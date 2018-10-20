(ns diplomacy.resolution-iterative
  (:require [diplomacy.orders :as orders]
            [diplomacy.datatypes :as dt]
            [diplomacy.util :refer [defn-spec]]
            [clojure.spec.alpha :as s]))

(defn queue?
  [collection]
  (instance? clojure.lang.PersistentQueue collection))

(defn move-front-to-back
  [queue]
  (conj queue (pop queue)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                               Specs Internal to Resolution ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def ::conflict-state (s/or :resolved-tag ::dt/judgment
                              :no-conflict-tag (partial = :no-conflict)
                              :pending-tag ::dt/conflict-rule))
;; At the moment `::judgment` also contains the interfering order, occasionally
;; duplicating information between the key and value of the nested map. Fixing
;; this isn't necessary.
(s/def ::conflict-states (s/map-of ::order
                                   (s/map-of ::order ::conflict-state)))
(s/def ::order-resolution-queue (s/and ::dt/orders queue?))
(s/def ::location-to-order-map (s/map-of ::dt/location ::dt/order))

(s/def ::resolution-state
  (s/keys :req-un [::conflict-states
                   ::order-resolution-queue
                   ;; Fields that never change during resolution
                   ::location-to-order-map
                   ::dmap]))

(s/def ::conflict-state-update (s/tuple ::dt/order ::dt/order ::conflict-state))
(s/def ::conflict-state-updates (s/coll-of ::conflict-state-update))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                    Resolution Control Flow ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(declare evaluate-attack evaluate-support evaluate-convoy
         apply-conflict-state-updates)

(defn-spec resolution-complete? [::resolution-state] boolean?)
(defn resolution-complete? [{:keys [conflict-states]}]
  (let [all-conflict-states
        (for [[order conflicting-orders-map] conflict-states
              [conflicting-order conflict-state] conflicting-orders-map]
          conflict-state)]
    (every? #(or (s/valid? ::dt/judgment %)
                 (partial = :no-conflict))
            all-conflict-states)))

(defn-spec take-resolution-step [::resolution-state] ::resolution-state)
(defn take-resolution-step
  "Takes one step of the resolution algorithm.

  The return value will only be equal to the argument if resolution is
  complete."
  [{:keys [conflict-states order-resolution-queue
           location-to-order-map dmap]
    :as resolution-state}]

  (if (resolution-complete? resolution-state)
    resolution-state
    (let [order (peek order-resolution-queue)
          evaluation-func-for-order (case (:order-type order)
                                      :attack evaluate-attack
                                      :support evaluate-support
                                      :convoy evaluate-convoy)
          conflict-state-updates
          (evaluation-func-for-order resolution-state order)]
      (-> resolution-state
          (update :order-resolution-queue move-front-to-back)
          (update :conflict-states #(apply-conflict-state-updates
                                     % conflict-state-updates))))))

(defn-spec apply-conflict-state-updates
  [::resolution-state ::conflict-state-updates] ::resolution-state)
(defn apply-conflict-state-updates
  "Applies the updates in `conflict-state-updates` to `resolution-state`,
  performing any necessary bookkeeping."
  [conflict-states conflict-state-updates]
  (reduce (fn [states [order conflicting-order conflict-state]]
            (assoc-in states [order conflicting-order] conflict-state))
          conflict-states
          conflict-state-updates))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                   Utilities for Expressing Diplomacy Rules ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn-spec remains-at [::location-to-order-map ::dt/location]
  ::dt/orders)
(defn remains-at
  "A sequence of the orders that attempt to hold, support, or convoy at
  `location`. The sequence will have 0 or 1 elements."
  [location-to-order-map location]
  (let [order (location-to-order-map location)]
    (when (contains? #{:hold :support :convoy} (:order-type order))
      [order]
      [])))

(defn-spec attacks-to [::location-to-order-map ::dt/location]
  ::dt/orders)
(defn attacks-to
  ""
  [location-to-order-map to]
  (->> location-to-order-map
       (vals)
       (filter #(and (orders/attack? %)
                     (= (:destination %) to)))))

(defn-spec attacks-from-to [::location-to-order-map ::dt/location ::dt/location]
  ::dt/orders)
(defn attacks-from-to
  ""
  [location-to-order-map from to]
  (let [order (location-to-order-map from)]
    (if (and (orders/attack? order)
             (= (:destination order) to))
      [order]
      [])))

(defn-spec attacks-from [::location-to-order-map ::dt/location]
  ::dt/orders)
(defn attacks-from
  ""
  [location-to-order-map from]
  (let [order (location-to-order-map from)]
    (if (orders/attack? order)
      [order]
      [])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                            Diplomacy Rules ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn-spec evaluate-attack [::resolution-state ::dt/attack-order]
  ::conflict-state-updates)
(defn evaluate-attack
  ""
  [{:keys [conflict-states] :as resolution-state}
   order]
  )

(defn-spec evaluate-support [::resolution-state ::dt/support-order]
  ::conflict-state-updates)
(defn evaluate-support
  ""
  [resolution-state order]
  )

(defn-spec evaluate-convoy [::resolution-state ::dt/convoy-order]
  ::conflict-state-updates)
(defn evaluate-convoy
  ""
  [resolution-state order]
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                             Utilities for Public Interface ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ^:private fixpoint
  "Apply `f` to `initial`, then apply `f` again to the result, repeating until
  applying `f` yields a result equal to the input to `f`. Return that
  result (which is a fixpoint of `f`)."
  [f initial]
  (let [f-results (iterate f initial)]
    (reduce #(if (= %1 %2) (reduced %2) %2) f-results)))

(defn-spec get-all-potential-conflicts [::location-to-order-map ::dt/order]
  (s/map-of ::order ::conflict-state))
(defn ^:private get-all-potential-conflicts
  [location-to-order-map {:keys [location order-type] :as order}]
  (case order-type
    :hold []
    :convoy []
    :attack
    (let [destination (:destination order)
          order-conflict-rule-pairs
          (concat
           (map #(-> [% :destination-occupied])
                (remains-at location-to-order-map destination))
           (map #(-> [% :attacked-same-destination])
                (attacks-to location-to-order-map destination))
           (map #(-> [% :swapped-places-without-convoy])
                (attacks-from-to location-to-order-map destination location))
           (map #(-> [% :failed-to-leave-destination])
                (attacks-from location-to-order-map destination)))]
      (into {} order-conflict-rule-pairs))
    ;; TODO: support logic
    :support []))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                      Public Interface for Order Resolution ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn-spec compute-resolution-results
  [::dt/orders ::dt/dmap]
  ::dt/resolution-results
  #(= (set (-> % :args :arg-1)) (set (-> % :ret (keys)))))
(defn compute-resolution-results
  "A map from each element of `orders` to the set of judgments that apply to it
  (the orders that may interfere with it, whether they successfully interfered,
  and the situation that determined that result)."
  [orders diplomacy-map]
  (let [location-to-order-map (->> orders
                                   (map (juxt :location identity))
                                   (into {}))
        conflict-states
        (->> orders
             (map (juxt identity (partial get-all-potential-conflicts
                                          location-to-order-map)))
             (into {}))
        order-resolution-queue (->> orders
                                    (filter (complement orders/hold?))
                                    (into clojure.lang.PersistentQueue/EMPTY))
        initial-resolution-state
        {:conflict-states conflict-states
         :order-resolution-queue order-resolution-queue
         :location-to-order-map location-to-order-map
         :dmap diplomacy-map}
        final-resolution-state
        (fixpoint take-resolution-step initial-resolution-state)
        final-conflict-states (:conflict-states final-resolution-state)]
    (merge
     ;; There should only be finished judgments in `final-conflict-states`, and
     ;; those judgments already contain the conflicting order.
     (->> final-conflict-states
          (map (fn [[order conflicting-orders-map]]
                 [order (vals conflicting-orders-map)]))
          (into {}))
     ;; Add the holds to the result
     (->> orders
          (filter orders/hold?)
          (map #(-> [% #{}]))
          (into {})))))
