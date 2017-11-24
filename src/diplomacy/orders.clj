(ns diplomacy.orders
  (:require [clojure.core.match :refer [match]]
            [clojure.set :as set]
            [clojure.spec.alpha :as s]
            [diplomacy.util :refer [defn-spec]]
            [diplomacy.datatypes :as dt]))

;;; Functions for creating and working with Diplomacy orders.

(defn-spec expand-order ::dt/order-vector ::dt/order)
(defn expand-order
  "A shorthand for writing orders in Clojure. Intended for 'order literals' in
  source code rather than taking user input, so errors are handled with
  exceptions. Usage:

  (expand-order :england :army  :wal :hold)
  (expand-order :england :army  :lvp :support :england :army :wal :hold)
  (expand-order :france  :army  :bre :attack  :lon)
  (expand-order :france  :fleet :eng :convoy  :france  :army :bre :attack :lon)

  PRECONDITION: Constructed order must be valid in some diplomacy map.
  "
  [country unit-type location order-type & rest]
  (let [basic-order {:country  country  :unit-type  unit-type
                     :location location :order-type order-type}
        ;; `match` won't let multiple patterns map to the same expression, so we
        ;; put the expression in a thunk to avoid duplication.
        make-assisting-order
        (fn [] (assoc basic-order
                      :assisted-order (apply expand-order rest)))]
    (match [unit-type order-type rest]
      [_      :hold    nil]           basic-order
      [_      :attack  ([dest] :seq)]
      (assoc basic-order :destination dest)
      [_      :support ([_ _     _ :hold] :seq)]     (make-assisting-order)
      [_      :support ([_ _     _ :attack _] :seq)] (make-assisting-order)
      [:fleet :convoy  ([_ :army _ :attack _] :seq)] (make-assisting-order))))


;; TODO: consider removing these trivial accessor functions
(defn-spec army?    [::dt/order] boolean?)
(defn-spec fleet?   [::dt/order] boolean?)
(defn-spec hold?    [::dt/order] boolean?)
(defn-spec attack?  [::dt/order] boolean?)
(defn-spec support? [::dt/order] boolean?)
(defn-spec convoy?  [::dt/order] boolean?)

(defn army?  [order] (= (:unit-type order) :army))
(defn fleet? [order] (= (:unit-type order) :fleet))
(defn hold?    [order] (= (:order-type order) :hold))
(defn attack?  [order] (= (:order-type order) :attack))
(defn support? [order] (= (:order-type order) :support))
(defn convoy?  [order] (= (:order-type order) :convoy))

(defn-spec next-intended-location [::dt/order] ::dt/location)
(defn next-intended-location
  [order]
  (if (attack? order)
    (:destination order)  ; attacks move, everything else stays put
    (:location order)))

(defn-spec locations-used-by-order [::dt/order] (s/coll-of ::dt/location))
(defn locations-used-by-order
  [order]
  "The set of all locations referenced by `order` and the order it assists."
  (set/union #{(:location order)}
             (case (:order-type order)
               :hold   #{}
               :attack #{(:destination order)}
               :support (locations-used-by-order (:assisted-order order))
               :convoy  (locations-used-by-order (:assisted-order order)))))

