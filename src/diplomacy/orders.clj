(ns diplomacy.orders
  (:require [clojure.set :as set]
            [clojure.spec.alpha :as s]
            [diplomacy.util :refer [defn-spec]]
            [diplomacy.datatypes :as dt]))

;;; Functions for working with Diplomacy orders.

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

(defn-spec get-unit [::dt/order] ::dt/unit)
(defn get-unit [order]
  (select-keys order #{:unit-type :country}))

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

