(ns diplomacy.datatypes
  (:require [clojure.core.match :refer [match]]
            [clojure.set :as set]
            [clojure.spec :as s]
            [diplomacy.util :refer [defn-spec]]
            [diplomacy.maps]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                 GameState and DiplomacyMap ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def ::unit-type #{:army :fleet})
;; Make the generator for this spec return a country form the classic map.
(s/def ::country (s/with-gen keyword?
                   (fn [] (s/gen (-> diplomacy.maps/classic-map
                                     :home-supply-centers
                                     keys
                                     set)))))
(s/def ::unit (s/keys :req-un [::unit-type ::country]))

(s/def ::location (s/with-gen keyword?
                    (fn [] (s/gen (-> diplomacy.maps/classic-map
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
                (fn [] (s/gen #{diplomacy.maps/classic-map}))))

(defn-spec location-accessible-to? [::dmap ::location ::unit-type] boolean?)
(defn location-accessible-to?
  [diplomacy-map location unit-type]
  (let [location-accessibility (get-in diplomacy-map
                                       [:location-accessibility location])]
    (contains? location-accessibility unit-type)))

(defn-spec edge-accessible-to?
  [::dmap ::location ::location ::unit-type] boolean?)
(defn edge-accessible-to?
  [diplomacy-map from to unit-type]
  (let [edge-accessibility (get-in diplomacy-map [:edge-accessibility from to])]
    (contains? edge-accessibility unit-type)))

(defn-spec locations-colocated? [::dmap ::location ::location] boolean?)
(defn locations-colocated?
  [diplomacy-map location-1 location-2]
  (or (= location-1 location-2)
      (some #(set/subset? #{location-1 location-2} %)
            (:colocation-sets diplomacy-map))))

(defn-spec colocation-set-for-location [::dmap ::location] (s/coll-of ::location))
(defn colocation-set-for-location
  [{:keys [colocation-sets]} location]
  (let [colocation-sets-for-location (filter #(contains? % location)
                                             colocation-sets)]
    (assert (<= (count colocation-sets-for-location) 1))
    (if (empty? colocation-sets-for-location)
      #{location}
      (first colocation-sets-for-location))))

(defn-spec supply-center? [::dmap ::location] boolean?)
(defn supply-center?
  [diplomacy-map location]
  (contains? (:supply-centers diplomacy-map) location))

(defn-spec home-supply-centers [::dmap ::country] boolean?)
(defn home-supply-centers
  [diplomacy-map country]
  (get-in diplomacy-map [:home-supply-centers country]))

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

(s/def ::create-order-args
  (s/cat :country    ::country
         :unit-type  ::unit-type
         :location   ::location
         :order-type ::order-type
         ;; `rest` could be empty, a destination, or the arguments for the
         ;; assisted hold or attack. I'm not going to bother making this
         ;; spec more specific, but unfortunately that means we can't use
         ;; `s/exercise-fn` with this spec.
         ))

(defn-spec create-order ::create-order-args ::order)
(defn create-order
  "A shorthand for writing orders in Clojure. Intended for 'order literals' in
  source code rather than taking user input, so errors are handled with
  exceptions. Usage:

  (create-order :england :army  :wal :hold)
  (create-order :england :army  :lvp :support :england :army :wal :hold)
  (create-order :france  :army  :bre :attack  :lon)
  (create-order :france  :fleet :eng :convoy  :france  :army :bre :attack :lon)

  PRECONDITION: Constructed order must be valid in some diplomacy map.
  "
  [country unit-type location order-type & rest]
  (let [basic-order {:country  country  :unit-type  unit-type
                     :location location :order-type order-type}
        ;; `match` won't let multiple patterns map to the same expression, so we
        ;; put the expression in a thunk to avoid duplication.
        make-assisting-order
        (fn [] (assoc basic-order
                      :assisted-order (apply create-order rest)))]
    (match [unit-type order-type rest]
      [_      :hold    nil]           basic-order
      [_      :attack  ([(dest :guard (partial not= location))] :seq)]
      (assoc basic-order :destination dest)
      [_      :support ([_ _     _ :hold] :seq)]     (make-assisting-order)
      [_      :support ([_ _     _ :attack _] :seq)] (make-assisting-order)
      [:fleet :convoy  ([_ :army _ :attack _] :seq)] (make-assisting-order))))


;; TODO: consider removing these trivial accessor functions
(defn-spec army?    [::order] boolean?)
(defn-spec fleet?   [::order] boolean?)
(defn-spec hold?    [::order] boolean?)
(defn-spec attack?  [::order] boolean?)
(defn-spec support? [::order] boolean?)
(defn-spec convoy?  [::order] boolean?)

(defn army?  [order] (= (:unit-type order) :army))
(defn fleet? [order] (= (:unit-type order) :fleet))
(defn hold?    [order] (= (:order-type order) :hold))
(defn attack?  [order] (= (:order-type order) :attack))
(defn support? [order] (= (:order-type order) :support))
(defn convoy?  [order] (= (:order-type order) :convoy))

(defn-spec next-intended-location [::order] ::location)
(defn next-intended-location
  [order]
  (if (attack? order)
    (:destination order)  ; attacks move, everything else stays put
    (:location order)))

(defn-spec locations-used-by-order [::order] (s/coll-of ::location))
(defn locations-used-by-order
  [order]
  "The set of all locations referenced by `order` and the order it assists."
  (set/union #{(:location order)}
             (case (:order-type order)
               :hold   #{}
               :attack #{(:destination order)}
               :support (locations-used-by-order (:assisted-order order))
               :convoy  (locations-used-by-order (:assisted-order order)))))
