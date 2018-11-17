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

;; A judgment that there is no conflict (a possibility not covered by
;; `::dt/judgment`).
(s/def ::no-conflict-judgment (s/tuple ::dt/conflict-rule
                                       (partial = :no-conflict)))
(s/def ::resolved-conflict-state (s/or :judgment-tag ::dt/judgment
                                       :no-conflict-tag ::no-conflict-judgment))
(s/def ::pending-conflict-state ::dt/conflict-rule)
(s/def ::conflict-state (s/or :resolved-tag ::resolved-conflict-state
                              :pending-tag ::pending-conflict-state))
;; At the moment `::judgment` also contains the interfering order, duplicating
;; information between the key and value of the nested map. Fixing this isn't
;; necessary.
;;
;; TBD: make this only contain resolved conflicts?
(s/def ::conflict-map (s/map-of ::dt/order
                                (s/map-of ::dt/order ::conflict-state)))
(s/def ::pending-conflict
  (s/tuple ::dt/order ::dt/order ::pending-conflict-state))
(s/def ::conflict-queue (s/and (s/coll-of ::pending-conflict) #_queue?))

(s/def ::location-to-order-map (s/map-of ::dt/location ::dt/order))
(s/def ::resolution-state
  (s/keys :req-un [::conflict-map
                   ::conflict-queue
                   ;; Fields that never change during resolution
                   ::location-to-order-map
                   ::dmap]))

(s/def ::conflict-state-update
  (s/tuple ::dt/order ::dt/order ::conflict-state))
(s/def ::conflict-state-updates (s/coll-of ::conflict-state-update))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                    Resolution Control Flow ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(declare conflict-rule-to-eval-func apply-conflict-state-updates)

(defn-spec resolution-complete? [::conflict-map] boolean?)
(defn resolution-complete? [conflict-map]
  (let [all-conflict-states
        (for [[order conflicting-orders-map] conflict-map
              [conflicting-order conflict-state] conflicting-orders-map]
          conflict-state)]
    (every? (partial s/valid? ::resolved-conflict-state)
            all-conflict-states)))

(defn-spec take-resolution-step [::resolution-state] ::resolution-state)
(defn take-resolution-step
  "Takes one step of the resolution algorithm.

  The return value will only be equal to the argument if resolution is
  complete."
  [{:keys [conflict-map conflict-queue
           location-to-order-map dmap]
    :as resolution-state}]

  (if (resolution-complete? (:conflict-map resolution-state))
    resolution-state
    (let [[order-a order-b conflict-rule] (peek conflict-queue)
          eval-func-for-rule (get conflict-rule-to-eval-func conflict-rule)
          conflict-state-updates
          (eval-func-for-rule resolution-state order-a order-b)]
      (-> resolution-state
          (update :conflict-queue move-front-to-back)
          (update :conflict-map #(apply-conflict-state-updates
                                  % conflict-state-updates))))))

(defn-spec apply-conflict-state-updates
  [::conflict-map ::conflict-state-updates] ::conflict-map)
(defn apply-conflict-state-updates
  "Applies the updates in `conflict-state-updates` to `conflict-map`,
  performing any necessary bookkeeping."
  [conflict-map conflict-state-updates]
  (reduce (fn [cmap [order conflicting-order conflict-state]]
            (assoc-in cmap [order conflicting-order] conflict-state))
          conflict-map
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
  (if-let [order (location-to-order-map location)]
    (if (contains? #{:hold :support :convoy} (:order-type order))
      [order]
      [])
    []))

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
  (if-let [order (location-to-order-map from)]
    (if (and (orders/attack? order)
             (= (:destination order) to))
      [order]
      [])
    []))

(defn-spec attacks-from [::location-to-order-map ::dt/location]
  ::dt/orders)
(defn attacks-from
  ""
  [location-to-order-map from]
  (if-let [order (location-to-order-map from)]
    (if (orders/attack? order)
      [order]
      [])
    []))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                            Diplomacy Rules ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn-spec evaluate-destination-occupied
  [::resolution-state ::dt/attack-order ::dt/order]
  ::conflict-state-updates)
(defn evaluate-destination-occupied
  ""
  [resolution-state attack remain]
  )

(defn-spec evaluate-attacked-same-destination
  [::resolution-state ::dt/attack-order ::dt/attack-order]
  ::conflict-state-updates)
(defn evaluate-attacked-same-destination
  ""
  [resolution-state attack-a attack-b]
  [[attack-a attack-b
    {:interferer attack-b
     :conflict-situation {:attack-conflict-rule :attacked-same-destination
                          :beleaguered-garrison-changing-outcome nil}
     :interfered? true}]
   [attack-b attack-a
    {:interferer attack-a
     :conflict-situation {:attack-conflict-rule :attacked-same-destination
                          :beleaguered-garrison-changing-outcome nil}
     :interfered? true}]])

(defn-spec evaluate-swapped-places-without-convoy
  [::resolution-state ::dt/attack-order ::dt/attack-order]
  ::conflict-state-updates)
(defn evaluate-swapped-places-without-convoy
  ""
  [resolution-state attack-a attack-b]
  )

(defn-spec evaluate-failed-to-leave-destination
  [::resolution-state ::dt/attack-order ::dt/attack-order]
  ::conflict-state-updates)
(defn evaluate-failed-to-leave-destination
  ""
  [resolution-state attack-a attack-b]
  )

(def conflict-rule-to-eval-func
  {:destination-occupied          evaluate-destination-occupied
   :attacked-same-destination     evaluate-attacked-same-destination
   :swapped-places-without-convoy evaluate-swapped-places-without-convoy
   :failed-to-leave-destination   evaluate-failed-to-leave-destination})

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
  (s/coll-of ::pending-conflict))
(defn ^:private get-all-potential-conflicts
  [location-to-order-map {:keys [location order-type] :as order}]
  (case order-type
    :hold []
    :convoy []
    :attack
    (let [destination (:destination order)]
      (concat
       (map #(-> [order % :destination-occupied])
            (remains-at location-to-order-map destination))
       (map #(-> [order % :attacked-same-destination])
            (attacks-to location-to-order-map destination))
       (map #(-> [order % :swapped-places-without-convoy])
            (attacks-from-to location-to-order-map destination location))
       (map #(-> [order % :failed-to-leave-destination])
            (attacks-from location-to-order-map destination))))
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
        all-conflicts (mapcat (partial get-all-potential-conflicts
                                       location-to-order-map)
                              orders)
        conflict-queue (into clojure.lang.PersistentQueue/EMPTY all-conflicts)
        conflict-map (apply-conflict-state-updates {} all-conflicts)
        initial-resolution-state
        {:conflict-map conflict-map
         :conflict-queue conflict-queue
         :location-to-order-map location-to-order-map
         :dmap diplomacy-map}
        final-resolution-state
        (fixpoint take-resolution-step initial-resolution-state)
        final-conflict-map (:conflict-map final-resolution-state)]
    (merge
     ;; There should only be finished judgments in `final-conflict-map`, and
     ;; those judgments already contain the conflicting order.
     (->> final-conflict-map
          (map (fn [[order conflicting-orders-map]]
                 [order (vals conflicting-orders-map)]))
          (into {}))
     ;; Add the holds to the result
     (->> orders
          (filter orders/hold?)
          (map #(-> [% #{}]))
          (into {})))))
