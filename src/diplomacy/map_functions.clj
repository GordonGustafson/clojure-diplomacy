(ns diplomacy.map-functions
  (:require [clojure.set :as set]
            [clojure.spec.alpha :as s]
            [diplomacy.util :refer [defn-spec]]
            [diplomacy.datatypes :as dt]))

;;; Convenience functions for working with Diplomacy maps.

(defn-spec location-accessible-to? [::dt/dmap ::dt/location ::dt/unit-type]
  boolean?)
(defn location-accessible-to?
  [diplomacy-map location unit-type]
  (let [location-accessibility (get-in diplomacy-map
                                       [:location-accessibility location])]
    (contains? location-accessibility unit-type)))

(defn-spec edge-accessible-to?
  [::dt/dmap ::dt/location ::dt/location ::dt/unit-type] boolean?)
(defn edge-accessible-to?
  [diplomacy-map from to unit-type]
  (let [edge-accessibility (get-in diplomacy-map [:edge-accessibility from to])]
    (contains? edge-accessibility unit-type)))

(defn-spec locations-colocated? [::dt/dmap ::dt/location ::dt/location]
  boolean?)
(defn locations-colocated?
  [diplomacy-map location-1 location-2]
  (or (= location-1 location-2)
      (some #(set/subset? #{location-1 location-2} %)
            (:colocation-sets diplomacy-map))))

(defn-spec colocation-set-for-location [::dt/dmap ::dt/location]
  (s/coll-of ::dt/location))
(defn colocation-set-for-location
  [{:keys [colocation-sets]} location]
  (let [colocation-sets-for-location (filter #(contains? % location)
                                             colocation-sets)]
    (assert (<= (count colocation-sets-for-location) 1))
    (if (empty? colocation-sets-for-location)
      #{location}
      (first colocation-sets-for-location))))

(defn-spec supply-center? [::dt/dmap ::dt/location] boolean?)
(defn supply-center?
  [diplomacy-map location]
  (contains? (:supply-centers diplomacy-map) location))

(defn-spec home-supply-centers [::dt/dmap ::dt/country] boolean?)
(defn home-supply-centers
  [diplomacy-map country]
  (get-in diplomacy-map [:home-supply-centers country]))

