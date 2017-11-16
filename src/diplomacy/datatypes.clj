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
;; This documents what keys orders must have. See `create-order` and
;; order_validation.clj for further constraints on VALID orders.
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
(s/def ::assisted-order (s/and ::order
                               #(contains? #{:hold :attack} (:order-type %))))
(s/def ::orders (s/coll-of ::order))

;; More concise way of writing an order in Clojure.
(s/def ::order-vector
  (s/cat :country    ::country
         :unit-type  ::unit-type
         :location   ::location
         :order-type ::order-type
         ;; `rest` could be empty, a destination, or the arguments for the
         ;; assisted hold or attack. I'm not going to bother making this
         ;; spec more specific, but unfortunately that means we can't use
         ;; `s/exercise-fn` with this spec.
         :rest (s/* any?)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                            validating and resolving orders ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Keyword representing which order validation predicate failed.
(s/def ::validation-failure-reason
  #{:attacks-current-location?
    :supports-wrong-order-type?
    :uses-nonexistent-location?
    :attacks-inaccessible-location?
    :attacks-via-inaccessible-edge?
    :supports-unsupportable-location?})
(s/def ::validation-failure-reasons (s/coll-of ::validation-failure-reason))
(s/def ::order-given ::order)
(s/def ::order-used ::order)
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

;; A rule describing how a conflict between an attack and another order was
;; resolved.
(s/def ::attack-rule
  #{:destination-occupied
    :attacked-same-destination
    :swapped-places-without-convoy
    :failed-to-leave-destination
    :no-effect-on-dislodgers-province})

(s/def ::support-rule
  #{:attacked
    :attacked-from-supported-location-but-not-dislodged
    :dislodged})

(s/def ::conflict-rule (s/or :attack-rule ::attack-rule
                             :support-rule ::support-rule))

;; Whether the bouncer bounces the attack.
(s/def ::interfered? boolean?)

;; Map describing the conflict that some order had with `:interferer`.
;; `:interfered?` is the outcome of that conflict (whether `:interfered?`
;; counteracted the order), and `:conflict-rule` is the rule by which the
;; outcome was determined.
(s/def ::conflict-judgment (s/keys :req-un [::interferer
                                            ::conflict-rule
                                            ::interfered?]))
;; Map from *every* order that went into the resolution engine to the rules
;; governing how it was resolved.
(s/def ::conflict-judgments (s/map-of ::order
                                      (s/coll-of ::conflict-judgment)))

(s/def ::unit-positions-before ::unit-positions)
(s/def ::unit-positions-after ::unit-positions)
(s/def ::adjudication (s/keys :req-un [::validation-results
                                       ::conflict-judgments]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                       shorthand notation for writing tests ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def ::validation-result-abbr
  (s/or :valid (partial = :valid)
        :invalid (s/cat :failure-reasons ::validation-failure-reasons
                        :order-used ::order)))
(s/def ::validation-results-abbr (s/map-of ::order ::validation-result-abbr))

(s/def ::conflict-judgment-abbr (s/tuple ::interfered?
                                         ::interferer
                                         ::conflict-rule))
(s/def ::conflict-judgments-abbr (s/map-of
                                  ::order
                                  (s/coll-of ::conflict-judgment-abbr)))

(s/def ::adjudication-abbr (s/keys :req-un [::validation-results-abbr
                                            ::conflict-judgments-abbr]))
