(ns diplomacy.resolution-iterative.map-util
  (:require [diplomacy.resolution-iterative.datatypes :as r]
            [diplomacy.orders :as orders]
            [diplomacy.map-functions :as maps]
            [diplomacy.datatypes :as dt]
            [diplomacy.util :refer [defn-spec]]
            [clojure.spec.alpha :as s]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                              Map Utilities ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn-spec get-at-colocated-location
  [::dt/dmap ::r/location-to-order-map ::dt/location]
  (s/nilable ::dt/order))
(defn get-at-colocated-location
  [dmap location-to-order-map location]
  (let [colocated-locations (maps/colocation-set-for-location dmap location)
        orders-at-colocated-locations
        ;; RESUME HERE: check whether `(get _ 0 _)` is doing the right thing
        ;; if this is a set then it may always be returning nil
        (->> colocated-locations
             (map location-to-order-map)
             (filter (complement nil?)))
        ;; Technically this shouldn't be an assert since it indicates a problem
        ;; with the game state being resolved rather than the resolution code
        ;; itself, but I feel safer keeping it until we no longer want the
        ;; invariant on game states.
        _ (assert (<= (count orders-at-colocated-locations) 1))]
    ;; Returns `nil` if `orders-at-colocated-locations` is empty.
    (first orders-at-colocated-locations)))

(defn-spec remains-at [::dt/dmap ::r/location-to-order-map ::dt/location]
  ::dt/orders)
(defn remains-at
  "A sequence of the orders that attempt to hold, support, or convoy at
  `location`. The sequence will have 0 or 1 elements."
  [dmap location-to-order-map location]
  (if-let [order (get-at-colocated-location
                  dmap location-to-order-map location)]
    (if (contains? #{:hold :support :convoy} (:order-type order))
      [order]
      [])
    []))

(defn-spec attacks-to [::dt/dmap ::r/location-to-order-map ::dt/location]
  ::dt/orders)
(defn attacks-to
  ""
  [dmap location-to-order-map to]
  (->> location-to-order-map
       (vals)
       (filter #(and (orders/attack? %)
                     (maps/locations-colocated? dmap (:destination %) to)))))

(defn-spec attacks-from-to
  [::dt/dmap ::r/location-to-order-map ::dt/location ::dt/location]
  ::dt/orders)
(defn attacks-from-to
  ""
  [dmap location-to-order-map from to]
  (if-let [order (get-at-colocated-location dmap location-to-order-map from)]
    (if (and (orders/attack? order)
             (maps/locations-colocated? dmap (:destination order) to))
      [order]
      [])
    []))

(defn-spec attacks-from [::dt/dmap ::r/location-to-order-map ::dt/location]
  ::dt/orders)
(defn attacks-from
  ""
  [dmap location-to-order-map from]
  (if-let [order (get-at-colocated-location dmap location-to-order-map from)]
    (if (orders/attack? order)
      [order]
      [])
    []))
