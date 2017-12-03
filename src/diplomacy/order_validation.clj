(ns diplomacy.order-validation
  (:require [diplomacy.datatypes :as dt]
            [diplomacy.map-functions :as map]
            [diplomacy.orders :as ord]
            [diplomacy.util :refer [defn-spec]]
            [clojure.spec.alpha :as s]))

(defn-spec attacks-current-location?  [::dt/dmap ::dt/order] boolean?)
(defn-spec supports-wrong-order-type? [::dt/dmap ::dt/order] boolean?)

(defn-spec uses-nonexistent-location?       [::dt/dmap ::dt/order] boolean?)
(defn-spec attacks-inaccessible-location?   [::dt/dmap ::dt/order] boolean?)
(defn-spec attacks-via-inaccessible-edge?   [::dt/dmap ::dt/order] boolean?)
(defn-spec supports-unsupportable-location? [::dt/dmap ::dt/order] boolean?)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                 orders invalid in ALL maps ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; These take an unused `diplomacy-map` argument for uniformity's sake.

(defn attacks-current-location?
  [diplomacy-map {:keys [location destination] :as order}]
  (and (ord/attack? order)
       (= location destination)))

(defn supports-wrong-order-type?
  [diplomacy-map {:keys [assisted-order] :as order}]
  (and (ord/support? order)
       (not (or (ord/attack? assisted-order) (ord/hold? assisted-order)))))

;; (defn convoys-wrong-order-type? [{:keys [assisted-order] :as order}]
;;   (and (ord/convoy? order)
;;        (not (ord/attack? assisted-order))))

;; (defn convoy-with-wrong-unit-types? [{:keys [assisted-order] :as order}]
;;   (and (ord/convoy? order)
;;        (not (and (fleet? order) (army? assisted-order)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                orders invalid in SOME maps ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn uses-nonexistent-location?
  [diplomacy-map order]
  (let [locations-in-map (:location-accessibility diplomacy-map)]
    (not (every? (partial contains? locations-in-map)
                 (ord/locations-used-by-order order)))))

(defn attacks-inaccessible-location?
  [diplomacy-map {:keys [unit-type destination] :as order}]
  (and (ord/attack? order)
       (not (map/location-accessible-to? diplomacy-map destination unit-type))))

;; TODO(convoy): rethink this, since army attacks can use complete convoys
(defn attacks-via-inaccessible-edge?
  [diplomacy-map {:keys [unit-type location destination]
                  :as order}]
  (and (ord/attack? order)
       (not (map/edge-accessible-to? diplomacy-map
                                     location
                                     destination
                                     unit-type))))

(defn supports-unsupportable-location?
  [diplomacy-map {:keys [location assisted-order unit-type]
                  :as supporting-order}]
  (and (ord/support? supporting-order)
       (let [supported-location (ord/next-intended-location assisted-order)
             coloc-set (map/colocation-set-for-location diplomacy-map
                                                        supported-location)]
         (not-any? #(map/edge-accessible-to? diplomacy-map
                                             location
                                             %
                                             unit-type)
                   coloc-set))))

;; (defn convoys-from-coast? [diplomacy-map {:keys [location] :as order}]
;;   (and (ord/convoy? order)
;;        (map/location-accessible-to? diplomacy-map location :army)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                            aggregating validity predicates ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn-spec validation-failure-reasons
  [::dt/dmap ::dt/order] ::dt/validation-failure-reasons)
(defn validation-failure-reasons
  "The names (as a set of keywords) of the invalidation functions that declared
  `order` invalid in `diplomacy-map`."
  [diplomacy-map order]
  (let [invalidator-vars [(var attacks-current-location?)
                          (var supports-wrong-order-type?)
                          (var uses-nonexistent-location?)
                          (var attacks-inaccessible-location?)
                          (var attacks-via-inaccessible-edge?)
                          (var supports-unsupportable-location?)]
        invalidators     (map var-get invalidator-vars)
        failure-keywords (map #(-> % meta :name keyword) invalidator-vars)]
    ;; curried-invalidators (map raw-invalidators #(partial % diplomacy-map))
    ;; curried-invalidator-keyword-pairs (zip curried-invalidators
    ;;                                      invalidator-keywords)]

    (set (mapcat (fn [invalidator failure-keyword]
                   (if (invalidator diplomacy-map order)
                     [failure-keyword]
                     []))
                 invalidators
                 failure-keywords))))

(defn-spec validation-result
  [::dt/dmap ::dt/order] ::dt/validation-result)
(defn validation-result
  "The ::dt/validation-result for `order` in `diplomacy-map`"
  [diplomacy-map {:keys [country unit-type location] :as order}]
  (let [failure-reasons (validation-failure-reasons diplomacy-map order)]
    (if (empty? failure-reasons)
      :valid
      ;; TODO: For now any validation failure causes the unit to hold. Consider
      ;; whether we want to change this (low priority).
      {:validation-failure-reasons failure-reasons
       :order-used {:country country
                    :unit-type unit-type
                    :location location
                    :order-type :hold}})))

(defn-spec validation-results [::dt/dmap ::dt/orders] ::dt/validation-results)
(defn validation-results
  "The ::dt/validation-results for `orders` in `diplomacy-map`"
  [diplomacy-map orders]
  (->>
   orders
   (map (fn [order] [order (validation-result diplomacy-map order)]))
   (into {})))
