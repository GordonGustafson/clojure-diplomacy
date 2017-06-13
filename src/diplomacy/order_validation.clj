(ns diplomacy.order-validation
  (:require [diplomacy.datatypes :as dt]
            [diplomacy.util :refer [defn-spec]]
            [clojure.spec :as s]))

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
  (and (dt/attack? order)
       (= location destination)))

(defn supports-wrong-order-type?
  [diplomacy-map {:keys [assisted-order] :as order}]
  (and (dt/support? order)
       (not (or (dt/attack? assisted-order) (dt/hold? assisted-order)))))

;; (defn convoys-wrong-order-type? [{:keys [assisted-order] :as order}]
;;   (and (dt/convoy? order)
;;        (not (dt/attack? assisted-order))))

;; (defn convoy-with-wrong-unit-types? [{:keys [assisted-order] :as order}]
;;   (and (dt/convoy? order)
;;        (not (and (fleet? order) (army? assisted-order)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                orders invalid in SOME maps ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn uses-nonexistent-location?
  [diplomacy-map order]
  (let [locations-in-map (:location-accessibility diplomacy-map)]
    (not (every? (partial contains? locations-in-map)
                 (dt/locations-used-by-order order)))))

(defn attacks-inaccessible-location?
  [diplomacy-map {:keys [unit-type destination] :as order}]
  (and (dt/attack? order)
       (not (dt/location-accessible-to? diplomacy-map destination unit-type))))

;; TODO(convoy): rethink this, since army attacks can use complete convoys
(defn attacks-via-inaccessible-edge?
  [diplomacy-map {:keys [unit-type location destination]
                  :as order}]
  (and (dt/attack? order)
       (not (dt/edge-accessible-to? diplomacy-map
                                    location
                                    destination
                                    unit-type))))

(defn supports-unsupportable-location?
  [diplomacy-map {:keys [location assisted-order unit-type]
                  :as supporting-order}]
  (and (dt/support? supporting-order)
       (let [supported-location (dt/next-intended-location assisted-order)
             coloc-set (dt/colocation-set-for-location diplomacy-map
                                                       supported-location)]
         (not-any? #(dt/edge-accessible-to? diplomacy-map
                                            location
                                            %
                                            unit-type)
                   coloc-set))))

;; (defn convoys-from-coast? [diplomacy-map {:keys [location] :as order}]
;;   (and (dt/convoy? order)
;;        (dt/location-accessible-to? diplomacy-map location :army)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                            aggregating validity predicates ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn-spec validation-failure-reasons
  [::dt/dmap ::dt/order] (s/coll-of ::dt/validation-failure-reason))
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
