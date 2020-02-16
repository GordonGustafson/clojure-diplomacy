(ns diplomacy.resolution-iterative.init
  (:require [diplomacy.resolution-iterative.datatypes :as r]
            [diplomacy.resolution-iterative.map-util :as map-util]
            [diplomacy.orders :as orders]
            [diplomacy.map-functions :as maps]
            [diplomacy.datatypes :as dt]
            [diplomacy.util :refer [defn-spec]]
            [clojure.spec.alpha :as s]))

(defn-spec get-all-potential-conflicts
  [::dt/dmap ::r/location-to-order-map ::dt/order]
  (s/coll-of ::r/pending-conflict))
(defn get-all-potential-conflicts
  [dmap location-to-order-map {:keys [location order-type] :as order}]
  (case order-type
    :hold []
    :convoy []
    :attack
    (let [destination (:destination order)]
      (concat
       (map #(-> [order % :destination-occupied])
            (map-util/remains-at dmap location-to-order-map destination))
       (->> (map-util/attacks-to dmap location-to-order-map destination)
            (filter #(not= order %))
            (map #(-> [order % :attacked-same-destination])))
       (map #(-> [order % :swapped-places])
            (map-util/attacks-from-to dmap location-to-order-map destination location))
       (->> (map-util/attacks-from dmap location-to-order-map destination)
            ;; Exclude `:swapped-places` conflicts
            (filter #(not (maps/locations-colocated?
                           dmap (:destination %) location)))
            (map #(-> [order % :failed-to-leave-destination])))))
    :support
    (map (fn [interferer]
           (let [assisted-order (:assisted-order order)]
             (if (and (orders/attack? assisted-order)
                      (= (:destination assisted-order)
                         (:location interferer)))
               [order interferer :attacked-from-supported-location]
               [order interferer :attacked])))
         (map-util/attacks-to dmap location-to-order-map location))))

(defn-spec supported-order-matches? [::dt/order ::dt/order] boolean?)
(defn supported-order-matches?
  "Whether supporting `assisted-order` would give support for `order-given`.
  This requires some logic because supporting a hold can also indicate
  supporting a unit that's supporting or convoying."
  [assisted-order order-given]
  (if (orders/attack? order-given)
    (= assisted-order order-given)
    (and (orders/hold? assisted-order)
         (= (:location assisted-order)
            (:location order-given))
         (= (orders/get-unit assisted-order)
            (orders/get-unit order-given)))))

;; This does not take a diplomacy map because we currently require locations in
;; support orders to match exactly (a support order using the wrong colocated
;; location does not give support).
(defn-spec make-support-map [::r/location-to-order-map] ::r/support-map)
(defn make-support-map
  [location-to-order-map]
  (let [orders (vals location-to-order-map)
        support-orders (filter orders/support? orders)
        support-pairs
        (mapcat
         (fn [{:keys [assisted-order] :as support-order}]
           (let [order-at-assisted-location (location-to-order-map
                                             (:location assisted-order))]
             (if (and (not (nil? order-at-assisted-location))
                      (supported-order-matches? assisted-order
                                                order-at-assisted-location))
               [[order-at-assisted-location support-order]]
               [])))
         support-orders)]
    (reduce (fn [support-map [supported-order supporting-order]]
              (if (contains? support-map supported-order)
                (update support-map supported-order #(conj % supporting-order))
                (assoc support-map supported-order [supporting-order])))
            {}
            support-pairs)))

;; TODO: reduce duplication between `make-support-map` and `make-convoy-map`
;; if desired.
(defn-spec make-convoy-map [::r/location-to-order-map] ::r/convoy-map)
(defn make-convoy-map
  [location-to-order-map]
  (let [orders (vals location-to-order-map)
        convoy-orders (filter orders/convoy? orders)
        convoy-pairs
        (mapcat
         (fn [{:keys [assisted-order] :as convoy-order}]
           (let [order-at-assisted-location (location-to-order-map
                                             (:location assisted-order))]
             (if (and (not (nil? order-at-assisted-location))
                      (= assisted-order order-at-assisted-location))
               [[order-at-assisted-location convoy-order]]
               [])))
         convoy-orders)]
    (reduce (fn [convoy-map [convoyed-order convoying-order]]
              (if (contains? convoy-map convoyed-order)
                (update convoy-map convoyed-order #(conj % convoying-order))
                (assoc convoy-map convoyed-order [convoying-order])))
            {}
            convoy-pairs)))

(defn-spec make-direct-arrival-set [::dt/dmap ::dt/orders] ::r/direct-arrival-set)
(defn make-direct-arrival-set
  [dmap orders]
  (->> orders
       (filter orders/attack?)
       (filter
        (fn [{:keys [location destination unit-type] :as order}]
          (maps/edge-accessible-to? dmap location destination unit-type)))
       (into #{})))

(defn-spec make-location-to-order-map [::dt/orders] ::r/location-to-order-map)
(defn make-location-to-order-map
  [orders]
  (->> orders
       (map (juxt :location identity))
       (into {})))

(defn-spec get-initial-resolution-state
  [::dt/orders ::dt/dmap]
  ::r/resolution-state)
(defn get-initial-resolution-state
  [orders diplomacy-map]
  (let [location-to-order-map (make-location-to-order-map orders)
        all-conflicts (mapcat (partial get-all-potential-conflicts
                                       diplomacy-map location-to-order-map)
                              orders)
        conflict-queue (into clojure.lang.PersistentQueue/EMPTY all-conflicts)

        conflict-map (reduce (fn [cmap [order conflicting-order conflict-state]]
                               (assoc-in cmap [order conflicting-order] conflict-state))
                             {}
                             all-conflicts)

        convoy-map (make-convoy-map location-to-order-map)
        direct-arrival-set (make-direct-arrival-set diplomacy-map orders)
        voyage-queue (into clojure.lang.PersistentQueue/EMPTY
                           (concat
                            (keys convoy-map)
                            (filter #(and (orders/attack? %)
                                          (not (contains? direct-arrival-set %)))
                                    orders)))]
    {:conflict-map conflict-map
     :conflict-queue conflict-queue
     :voyage-map (->> voyage-queue
                      (map (fn [convoyed-order] [convoyed-order :pending]))
                      (into {}))
     :voyage-queue voyage-queue
     :support-map (make-support-map location-to-order-map)
     :convoy-map convoy-map
     :direct-arrival-set direct-arrival-set
     :location-to-order-map location-to-order-map
     :dmap diplomacy-map}))
