(ns diplomacy.post-resolution
  (:require [diplomacy.map-functions :as map-func]
            [diplomacy.orders :as orders]
            [clojure.set :as set]
            [diplomacy.datatypes :as dt]
            [diplomacy.util :refer [defn-spec]]
            [clojure.spec.alpha :as s]))

;;; This namespace is for post-processing the results of the resolution engine.

(defn-spec get-successful-attacks [::dt/resolution-results]
  (s/coll-of (s/and ::dt/order orders/attack?)))
(defn get-successful-attacks
  "All attacks in `resolution-results` that succeeded."
  [resolution-results]
  (->> resolution-results
       (filter (fn [[order resolution-results-for-order]]
                 (and (orders/attack? order)
                      (not-any? :interfered? resolution-results-for-order))))
       (map first)))

(defn-spec get-pending-retreats [::dt/dmap ::dt/resolution-results]
  ::dt/pending-retreats)
(defn get-pending-retreats
  "All forced retreats occurring as a result of `resolution-results`."
  [diplomacy-map resolution-results]
  (let [all-orders (set (keys resolution-results))
        successful-attacks (set (get-successful-attacks resolution-results))
        invaded-locations (set (map :destination successful-attacks))
        ;; Any order that wasn't a successful attack that had a successful
        ;; attack move into its location is forced to retreat.
        evicted-orders (set (filter (fn [{:keys [location]}]
                                      (contains? invaded-locations location))
                                    (set/difference all-orders
                                                    successful-attacks)))
        occupied-locations
        (set/union (set (map :location
                             (set/difference all-orders successful-attacks)))
                   (map :destination successful-attacks))
        standoff-locations
        (->> all-orders
             (filter (fn [order]
                       (and (orders/attack? order)
                            (not (contains? successful-attacks order)))))
             (map :destination)
             (set))]
    (map
     (fn [{:keys [location unit-type] :as evicted-order}]
       (let [adjacent-accessible-locations
             (set (map-func/get-adjacent-accessible-locations
                   diplomacy-map location unit-type))
             attacked-from
             (->> successful-attacks
                  (filter #(= (:destination %) location))
                  first
                  :location)]
         {:location (:location evicted-order)
          :unit (orders/get-unit evicted-order)
          ;; From page 18 of the rulebook:
          ;; "A unit can't retreat to:
          ;; - a province that is occupied;
          ;; - the province from which the attacker came; or
          ;; - a province that was left vacant by a standoff during the same
          ;;   turn."
          :retreatable-locations
          (set/difference adjacent-accessible-locations
                          occupied-locations
                          #{attacked-from}
                          standoff-locations)}))
     evicted-orders)))

(defn-spec unit-positions-after-orders
  [::dt/resolution-results ::dt/unit-positions] ::dt/unit-positions)
(defn unit-positions-after-orders
  "The unit-positions after the successful orders from `resolution-results` have
  been carried out"
  [resolution-results unit-positions-before]
  (let [successful-attacks (get-successful-attacks resolution-results)
        locations-to-vacate (map :location successful-attacks)
        unit-positions-to-add
        (->> successful-attacks
             (map (juxt :destination orders/get-unit))
             (into {}))]
    (merge (apply dissoc unit-positions-before locations-to-vacate)
           unit-positions-to-add)))
