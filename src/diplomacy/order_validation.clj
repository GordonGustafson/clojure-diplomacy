(ns diplomacy.order-validation
  (:require [diplomacy.datatypes :as dt]
            [diplomacy.map-functions :as map]
            [diplomacy.orders :as ord]
            [diplomacy.util :refer [defn-spec]]
            [clojure.spec.alpha :as s]))

(defn-spec attacks-current-location?
  [::dt/dmap ::dt/unit-positions ::dt/order] boolean?)
(defn-spec supports-wrong-order-type?
  [::dt/dmap ::dt/unit-positions ::dt/order] boolean?)

(defn-spec uses-nonexistent-location?
  [::dt/dmap ::dt/unit-positions ::dt/order] boolean?)
(defn-spec attacks-inaccessible-location?
  [::dt/dmap ::dt/unit-positions ::dt/order] boolean?)
(defn-spec attacks-via-inaccessible-edge?
  [::dt/dmap ::dt/unit-positions ::dt/order] boolean?)
(defn-spec supports-unsupportable-location?
  [::dt/dmap ::dt/unit-positions ::dt/order] boolean?)

(defn-spec ordered-unit-does-not-exist?
  [::dt/dmap ::dt/unit-positions ::dt/order] boolean?)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                 orders invalid in ALL maps ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; These take unused `diplomacy-map` and `unit-positions` arguments for
;; uniformity's sake.

(defn attacks-current-location?
  [diplomacy-map unit-positions {:keys [location destination] :as order}]
  (and (ord/attack? order)
       (= location destination)))

(defn supports-wrong-order-type?
  [diplomacy-map unit-positions {:keys [assisted-order] :as order}]
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
;; These take an unused `unit-positions` argument for uniformity's sake.

(defn uses-nonexistent-location?
  [diplomacy-map unit-positions order]
  (let [locations-in-map (:location-accessibility diplomacy-map)]
    (not (every? (partial contains? locations-in-map)
                 (ord/locations-used-by-order order)))))

(defn attacks-inaccessible-location?
  [diplomacy-map unit-positions {:keys [unit-type destination] :as order}]
  (and (ord/attack? order)
       (not (map/location-accessible-to? diplomacy-map destination unit-type))))

;; TODO(convoy): rethink this, since army attacks can use complete convoys
(defn attacks-via-inaccessible-edge?
  [diplomacy-map unit-positions {:keys [unit-type location destination]
                                 :as order}]
  (and (ord/attack? order)
       (not (map/edge-accessible-to? diplomacy-map
                                     location
                                     destination
                                     unit-type))))

(defn supports-unsupportable-location?
  [diplomacy-map unit-positions {:keys [location assisted-order unit-type]
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
;;                      orders invalid in SOME game states (units on the map) ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ordered-unit-does-not-exist?
  [diplomacy-map unit-positions {:keys [location] :as order}]
  (or (not (contains? unit-positions location))
      ;; Invalid unless country and unit type also match
      (not= (unit-positions location)
            (ord/get-unit order))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                            aggregating validity predicates ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn-spec validation-failure-reasons
  [::dt/dmap ::dt/unit-positions ::dt/order] ::dt/validation-failure-reasons)
(defn validation-failure-reasons
  "The names (as a set of keywords) of the invalidation functions that declared
  `order` invalid in `diplomacy-map`."
  [diplomacy-map unit-positions order]
  (let [invalidator-vars [(var attacks-current-location?)
                          (var supports-wrong-order-type?)
                          (var uses-nonexistent-location?)
                          (var attacks-inaccessible-location?)
                          (var attacks-via-inaccessible-edge?)
                          (var supports-unsupportable-location?)
                          (var ordered-unit-does-not-exist?)]
        invalidators     (map var-get invalidator-vars)
        failure-keywords (map #(-> % meta :name keyword) invalidator-vars)]
    ;; curried-invalidators (map raw-invalidators #(partial % diplomacy-map))
    ;; curried-invalidator-keyword-pairs (zip curried-invalidators
    ;;                                      invalidator-keywords)]

    (set (mapcat (fn [invalidator failure-keyword]
                   (if (invalidator diplomacy-map unit-positions order)
                     [failure-keyword]
                     []))
                 invalidators
                 failure-keywords))))

(defn-spec get-order-used [::dt/order ::dt/validation-failure-reasons]
  ::dt/order-used)
(defn get-order-used
  "The order `invalid-order` should be replaced with, where `invalid-order` is
  invalid due to `failure-reasons`, or `nil` If `invalid-order` should be
  removed (instead of replaced)."
  [{:keys [country unit-type location] :as invalid-order} failure-reasons]
  (if (contains? failure-reasons :ordered-unit-does-not-exist?)
    nil
    {:country country
     :unit-type unit-type
     :location location
     :order-type :hold}))

(defn-spec validation-result
  [::dt/dmap ::dt/unit-positions ::dt/order] ::dt/validation-result)
(defn validation-result
  "The ::dt/validation-result for `order` in `diplomacy-map`"
  [diplomacy-map unit-positions {:keys [country unit-type location] :as order}]
  (let [failure-reasons (validation-failure-reasons diplomacy-map
                                                    unit-positions
                                                    order)]
    (if (empty? failure-reasons)
      :valid
      {:validation-failure-reasons failure-reasons
       :order-used (get-order-used order failure-reasons)})))

(defn-spec validation-results [::dt/dmap ::dt/unit-positions ::dt/orders]
  ::dt/validation-results)
(defn validation-results
  "The ::dt/validation-results for `orders` in `diplomacy-map` with units
  located at `unit-positions`."
  [diplomacy-map unit-positions orders]
  (->>
   orders
   (map (fn [order]
          [order (validation-result diplomacy-map unit-positions order)]))
   (into {})))
