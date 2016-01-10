(ns diplomacy.datatypes
  (:require [diplomacy.util :refer [fail equal-by]]
            [clojure.core.match :refer [match]]
            [clojure.set :refer [union subset?]]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                  GameState and DiplomacyMap ;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def valid-unit-types #{:army :fleet})
(defrecord Unit
  [unit-type    ; an element of `valid-unit-types`
   nationality  ; a keyword
   ])

(def valid-seasons #{:spring :fall})
(defrecord GameTime
  [year    ; an integer
   season  ; an element of `valid-seasons`
   ])

(defrecord GameState
  [unit-positions           ; location => `Unit` occupying it ("=>" means map)
   supply-center-ownership  ; country  => set of supply centers it owns
   game-time                ; the current `GameTime` (Spring 1901)
   ])

;; Coasts are colocated with their corresponding 'land' locations.
;; The DiplomacyMap section of `DESIGN.md` discusses this representation.
(defrecord DiplomacyMap
  [location-accessibility  ; location => set of unit-types that can occupy it
   edge-accessibility      ; from-location => {to-location => set of unit-types
                           ;   that can move from from-location to to-location}.
                           ;   This is a nested map, *not* of a map of pairs.
   colocated-locations     ; set of sets of colocated locations
   supply-centers          ; set of locations that are supply centers
   home-supply-centers     ; country  => set of its home centers
   initial-game-state      ; the `GameState` before the first turn
   ])


(defn location-accessible-to? [diplomacy-map location unit-type]
  (let [location-accessibility (get-in diplomacy-map
                                       [:location-accessibility location])]
    (contains? location-accessibility unit-type)))

(defn edge-accessible-to? [diplomacy-map from to unit-type]
  (let [edge-accessibility (get-in diplomacy-map [:edge-accessibility from to])]
    (contains? edge-accessibility unit-type)))

(defn locations-colocated? [diplomacy-map location-1 location-2]
  (or (= location-1 location-2)
      (some #(subset? #{location-1 location-2} %)
            (:colocated-locations diplomacy-map))))

(defn supply-center? [diplomacy-map location]
  (contains? (:supply-centers diplomacy-map) location))

(defn home-supply-centers [diplomacy-map country]
  (get-in diplomacy-map [:home-supply-centers country]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                      orders ;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def valid-order-types #{:hold :attack :support :convoy})
;;; Representing orders:
;;;
;;; {:country ? :unit-type ? :location ? :order-type :hold
;;; {:country ? :unit-type ? :location ? :order-type :attack  :destination ?}
;;; {:country ? :unit-type ? :location ? :order-type :support :assisted-order ?}
;;; {:country ? :unit-type ? :location ? :order-type :convoy  :assisted-order ?}
;;;
;;; This documents what keys orders must have.
;;; See order_validation.clj for constraints on VALID orders.


;; A shorthand for writing orders in Clojure. Example usage:
;;
;;     (create-order :france :army :bre :attack :lon)
;;
;; See `datatype_test.clj` for more examples.
;;
;; PRECONDITION: Constructed order must be valid in some game state and map
;;               (only holds and attacks can be supported, armies can't convoy).
(defn create-order [country unit-type location order-type & assisted-order]
  ;; We could check that `unit-type` is valid in the match, but this is easier
  (assert (contains? valid-unit-types unit-type))
  (let [basic-order {:country  country  :unit-type  unit-type
                     :location location :order-type order-type}
        ;; `match` won't let multiple patterns map to the same expression, so we
        ;; put the expression in a thunk to avoid duplication.
        make-assisting-order
        (fn [] (assoc basic-order
                      :assisted-order (apply create-order assisted-order)))]
    (match [unit-type order-type assisted-order]
      [_      :hold    nil]           basic-order
      [_      :attack  ([dest] :seq)] (assoc basic-order :destination dest)
      [_      :support ([_ _     _ :hold] :seq)]     (make-assisting-order)
      [_      :support ([_ _     _ :attack _] :seq)] (make-assisting-order)
      [:fleet :convoy  ([_ :army _ :attack _] :seq)] (make-assisting-order))))


(defn army?  [order] (= (:unit-type order) :army))
(defn fleet? [order] (= (:unit-type order) :fleet))

(defn hold?    [order] (= (:order-type order) :hold))
(defn attack?  [order] (= (:order-type order) :attack))
(defn support? [order] (= (:order-type order) :support))
(defn convoy?  [order] (= (:order-type order) :convoy))

(defn next-intended-location [order]
  (if (attack? order)
    (:destination order)  ; attacks move, everything else stays put
    (:location order)))


(defn locations-used-by-order [order]
  "A set of all locations referenced by `order` and the order it assists."
  (assert (contains? valid-order-types (:order-type order)))
  (union #{(:location order)}
         (case (:order-type order)
           :hold   #{}
           :attack #{(:destination order)}
           :support (locations-used-by-order (:assisted-order order))
           :convoy  (locations-used-by-order (:assisted-order order)))))
