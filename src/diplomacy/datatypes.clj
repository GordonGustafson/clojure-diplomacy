(ns diplomacy.datatypes
  (:require [clojure.spec.alpha :as s]
            [clojure.set :as set]
            [diplomacy.map-data]))

;;; Specs for the datatypes used to represent Diplomacy concepts.

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                 GameState and DiplomacyMap ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def ::unit-type #{:army :fleet})
;; Make the generator for this spec return a country form the classic map.
(s/def ::country (s/with-gen keyword?
                   (fn [] (s/gen (-> diplomacy.map-data/classic-map
                                     :home-supply-centers
                                     keys
                                     set)))))
(s/def ::unit (s/keys :req-un [::unit-type ::country]))

(s/def ::location (s/with-gen keyword?
                    (fn [] (s/gen (-> diplomacy.map-data/classic-map
                                      :location-accessibility
                                      keys
                                      set)))))
(s/def ::unit-positions (s/map-of ::location ::unit))
(s/def ::supply-centers (s/coll-of ::location))
(s/def ::supply-center-ownership (s/map-of ::country
                                           ::supply-centers))

(s/def ::year integer?)
(s/def ::season #{:spring :fall})
(s/def ::game-time (s/keys :req-un [::year ::season]))

(s/def ::game-state (s/keys :req-un [::unit-positions
                                     ::supply-center-ownership
                                     ::game-time]))
(s/def ::initial-game-state ::game-state)

(s/def ::location-accessibility (s/map-of ::location (s/coll-of ::unit-type)))
(s/def ::edge-accessibility (s/map-of ::location
                                      (s/map-of ::location
                                                (s/coll-of ::unit-type))))
;; Coasts are colocated with their corresponding 'land' locations.
;; The DiplomacyMap section of `DESIGN.md` discusses this representation.
(s/def ::colocation-sets (s/coll-of (s/coll-of ::location)))
(s/def ::home-supply-centers ::supply-center-ownership)

(s/def ::dmap (s/with-gen
                (s/keys :req-un [::location-accessibility
                                 ::edge-accessibility
                                 ::colocation-sets
                                 ::supply-centers
                                 ::home-supply-centers
                                 ::initial-game-state])
                (fn [] (s/gen #{diplomacy.map-data/classic-map}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                     orders ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def ::destination ::location)
(s/def ::order-type #{:hold :attack :support :convoy})
(s/def ::order
  (s/and (s/keys :req-un #{::country  ::unit-type
                           ::location ::order-type}
                 ;; Required to check spec for these keys (would be checked
                 ;; automatically if they were namespaced keywords).
                 :opt-un [::destination ::assisted-order])
         (fn [order]
           (let [expected-keys (set/union #{:country  :unit-type
                                            :location :order-type}
                                          (case (:order-type order)
                                            :hold    #{}
                                            :attack  #{:destination}
                                            :support #{:assisted-order}
                                            :convoy  #{:assisted-order}))]
             (= (set (keys order)) expected-keys)))))
(s/def ::attack-order (s/and ::order #(= (:order-type %) :attack)))
(s/def ::support-order (s/and ::order #(= (:order-type %) :support)))
(s/def ::convoy-order (s/and ::order #(= (:order-type %) :convoy)))
(s/def ::assisted-order (s/and ::order
                               #(contains? #{:hold :attack} (:order-type %))))
(s/def ::orders (s/coll-of ::order))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                            validating and resolving orders ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Keyword representing which order validation predicate failed.
(s/def ::validation-failure-reason
  #{:attacks-current-location?
    :supports-wrong-order-type?
    :uses-nonexistent-location?
    :attacks-inaccessible-location?
    :fleet-attacks-via-inaccessible-edge?
    :supports-unsupportable-location?
    :convoys-from-coast?
    :ordered-unit-does-not-exist?})
(s/def ::validation-failure-reasons (s/coll-of ::validation-failure-reason))
(s/def ::order-given ::order)
;; `nil` if no order should be used to in place of the invalid order.
(s/def ::order-used (s/nilable ::order))
;; `:valid` if the order was valid, otherwise a list of reasons it was invalid,
;; and the order that should actually be fed to the resolution engine (what the
;; order was interpreted as).
;; Is this how I actually do this in core.spec???
(s/def ::validation-result
  (s/or :valid (partial = :valid)
        :invalid (s/keys :req-un [::validation-failure-reasons
                                  ::order-used])))
;; Map from *every* order given in a turn to its validation result.
(s/def ::validation-results (s/map-of ::order ::validation-result))

(s/def ::interferer ::order)

;; A keyword describing the conflict situation between two units. Does not
;; consider whether the conflict is between units of the same country (see
;; `::would-dislodge-own-unit?`).
(s/def ::attack-conflict-rule
  #{:destination-occupied
    :attacked-same-destination
    :swapped-places
    :failed-to-leave-destination
    ;; Only used in `diplomacy.resolution-core-logic`
    :no-effect-on-dislodgers-province})
;; Beleaguered garrison that changed the outcome of one of the attacks on its
;; location due to one or more supporting orders of that attack being unwilling
;; to dislodge the beleaguered garrison (since they're from the same country).
(s/def ::attack-supporters (s/coll-of ::support-order))
(s/def ::bouncer-supporters (s/coll-of ::support-order))
;; HISTORICAL NOTE: this was originally nilable because it's difficult to work
;; with optional keys in core.logic, so we made the key mandatory, but allowed
;; it to have a value of `nil`.
(s/def ::beleaguered-garrison-changing-outcome (s/nilable ::order))
(s/def ::attack-conflict-situation
  (s/and
   (s/keys :req-un [::attack-conflict-rule
                    ::beleaguered-garrison-changing-outcome]
           ;; Things not checked in the tests because they would make the tests
           ;; quite verbose.
           :opt-un [;; These are used by the `resolution-core-logic`
                    ;; implementation
                    ::attack-supporters
                    ::bouncer-supporters])
   #(if (nil? (:beleaguered-garrison-changing-outcome %))
      true
      (= (:attack-conflict-rule %) :attacked-same-destination))))

(s/def ::support-conflict-rule
  #{:attacked
    :attacked-from-supported-location  ; but not dislodged
    :dislodged
    :attack-failed-to-arrive
    :attacked-by-same-country
    :army-cant-cut-support-for-attack-on-its-own-convoy})
(s/def ::support-conflict-situation ::support-conflict-rule)

(s/def ::conflict-rule
  (s/or :attack-rule-tag ::attack-conflict-rule
        :support-rule-tag ::support-conflict-rule))
(s/def ::conflict-situation
  (s/or :attack-sit ::attack-conflict-situation
        :support-sit ::support-conflict-situation))

;; Whether the fact that the attack would have dislodge a unit of its own
;; country caused the it to fail when it would have otherwise succeeded.
(s/def ::would-dislodge-own-unit? boolean?)

;; Whether the bouncer bounces the attack.
(s/def ::interfered? boolean?)

(s/def ::failed-to-arrive-judgment #(= :no-successful-convoy %))
;; Map describing the conflict that some order had with `:interferer`.
;; `:interfered?` is the outcome of that conflict (whether `:interfered?`
;; counteracted the order), and `:conflict-situation` is the rule describing the
;; conflict situation.
(s/def ::conflict-judgment
  (s/keys :req-un [::interferer
                   ::conflict-situation
                   ::interfered?]
                   ;; TODO: separate attack and support judgments so this
                   ;; attack-specific field doesn't have to appear on all
                   ;; supports? They should be more decoupled?
          :opt-un [::would-dislodge-own-unit?]))
(s/def ::judgment
  (s/or :failed-to-arrive-judgment-tag ::failed-to-arrive-judgment
        :conflict-judgment-tag ::conflict-judgment))
(s/def ::judgments (s/coll-of ::judgment))

;; Map from *every* order that went into the resolution engine to the rules
;; governing how it was resolved.
(s/def ::resolution-results (s/map-of ::order
                                      ::judgments))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                        retreats, builds, and full pipeline ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def ::retreatable-locations (s/coll-of ::location))
(s/def ::pending-retreat (s/keys :req-un [::location
                                          ::unit
                                          ::retreatable-locations]))
(s/def ::pending-retreats (s/coll-of ::pending-retreat))

(s/def ::game-state-with-pending-retreats (s/and ::game-state
                                                 (s/keys :req-un
                                                         [::pending-retreats])))

(s/def ::game-state-before-orders ::game-state)
(s/def ::game-state-after-orders ::game-state-with-pending-retreats)

;; There's no ::orders here because ::validation-results already contains every
;; order.
(s/def ::completed-orders-phase (s/keys :req-un [::game-state-before-orders
                                                 ::validation-results
                                                 ::resolution-results
                                                 ::game-state-after-orders]))
