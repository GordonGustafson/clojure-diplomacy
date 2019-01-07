(ns diplomacy.resolution-iterative
  (:require [diplomacy.judgments :as j]
            [diplomacy.orders :as orders]
            [diplomacy.map-functions :as maps]
            [diplomacy.datatypes :as dt]
            [diplomacy.util :refer [defn-spec]]
            [clojure.spec.alpha :as s]))
(def debug false)
(require 'clojure.pprint)

(defn queue?
  [collection]
  (instance? clojure.lang.PersistentQueue collection))

(defn move-front-to-back
  [queue]
  (conj (pop queue) (peek queue)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                               Specs Internal to Resolution ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; The case that there is no conflict (a possibility not covered by
;; `::dt/judgment`).
(s/def ::no-conflict (s/tuple ::dt/conflict-rule
                              (partial = :no-conflict)))
(s/def ::resolved-conflict-state (s/or :judgment-tag ::dt/judgment
                                       :no-conflict-tag ::no-conflict))
(s/def ::pending-conflict-state ::dt/conflict-rule)
(s/def ::conflict-state (s/or :resolved-tag ::resolved-conflict-state
                              :pending-tag ::pending-conflict-state))
;; Whether an order is known to succeed, known to fail (due to being interefered
;; with), or not known yet.
(s/def ::order-status #{:succeeded :failed :pending})
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
;; Map from orders to the orders attempting to support them.
(s/def ::support-map (s/map-of ::dt/order ::dt/orders))

(s/def ::location-to-order-map (s/map-of ::dt/location ::dt/order))
(s/def ::resolution-state
  (s/keys :req-un [::conflict-map
                   ::conflict-queue
                   ;; Fields that never change during resolution
                   ::support-map
                   ::location-to-order-map
                   ::dt/dmap]))

(s/def ::conflict-state-update
  (s/tuple ::dt/order ::dt/order ::conflict-state))
(s/def ::conflict-state-updates (s/coll-of ::conflict-state-update))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                    Resolution Control Flow ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(declare evaluate-conflict apply-conflict-state-updates remove-conflicts)

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

  PRECONDITION: `(not (resolution-complete? resolution-state))`
  "
  [{:keys [conflict-map conflict-queue
           location-to-order-map dmap]
    :as resolution-state}]
  (when debug
    (print "conflict-queue: ")
    (clojure.pprint/pprint conflict-queue)
    (print "conflict-map: ")
    (clojure.pprint/pprint conflict-map))

  (let [pending-conflict (peek conflict-queue)
        conflict-state-updates (evaluate-conflict resolution-state
                                                  pending-conflict)]
    (when debug
      (print "conflict-state-updates: ")
      (clojure.pprint/pprint conflict-state-updates))
    (-> resolution-state
        (update :conflict-queue
                #(-> %
                     (move-front-to-back)
                     (remove-conflicts conflict-state-updates)))
        (update :conflict-map #(apply-conflict-state-updates
                                % conflict-state-updates)))))

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

(defn-spec remove-conflict
  [::conflict-queue ::conflict-state-update] ::conflict-queue)
(defn remove-conflict
  "Removes the conflict in `conflict-state-update` from `conflict-queue`."
  [conflict-queue conflict-state-update]
  ;; Not necessary, but useful to check this invariant while we have it.
  (assert (s/valid? ::resolved-conflict-state (nth conflict-state-update 2))
          (str conflict-state-update))
  (->> conflict-queue
       (filter (fn [queue-item]
                 (not= (take 2 queue-item)
                       (take 2 conflict-state-update))))
       (into clojure.lang.PersistentQueue/EMPTY)))

(defn-spec remove-conflicts
  [::conflict-queue ::conflict-state-updates] ::conflict-queue)
(defn remove-conflicts
  "Removes all conflicts in `conflict-state-update` from `conflict-queue`."
  [conflict-queue conflict-state-updates]
  "Removes all conflicts in `conflict-state-updates` from `conflict-queue`."
  ;; This is inefficient, but we can optimize later
  (reduce remove-conflict conflict-queue conflict-state-updates))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                              Map Utilities ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn-spec get-at-colocated-location
  [::dt/dmap ::location-to-order-map ::dt/location]
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

(defn-spec remains-at [::dt/dmap ::location-to-order-map ::dt/location]
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

(defn-spec attacks-to [::dt/dmap ::location-to-order-map ::dt/location]
  ::dt/orders)
(defn attacks-to
  ""
  [dmap location-to-order-map to]
  (->> location-to-order-map
       (vals)
       (filter #(and (orders/attack? %)
                     (maps/locations-colocated? dmap (:destination %) to)))))

(defn-spec attacks-from-to
  [::dt/dmap ::location-to-order-map ::dt/location ::dt/location]
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

(defn-spec attacks-from [::dt/dmap ::location-to-order-map ::dt/location]
  ::dt/orders)
(defn attacks-from
  ""
  [dmap location-to-order-map from]
  (if-let [order (get-at-colocated-location dmap location-to-order-map from)]
    (if (orders/attack? order)
      [order]
      [])
    []))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                  Determining Order Success ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn-spec interfering-state? [::resolved-conflict-state] boolean?)
(defn interfering-state?
  "Whether `rcs` is a judgment where the interferer successfully interferes."
  [rcs]
  (cond
    (s/valid? ::dt/judgment rcs) (:interfered? rcs)
    (s/valid? ::no-conflict rcs) false
    :else (assert false
                  (str "interfering-state? passed invalid resolved-conflict-state: "
                       rcs))))

(defn-spec pending-conflict-state? [::conflict-state] boolean?)
(defn pending-conflict-state?
  [conflict-state]
  (s/valid? ::pending-conflict-state conflict-state))

(defn-spec conflict-states-to-order-status [(s/coll-of ::conflict-state)]
  ::order-status)
(defn conflict-states-to-order-status
  [conflict-states]
  (cond
    (some #(and (not (pending-conflict-state? %))
                (interfering-state? %))
          conflict-states)
    :failed
    (some pending-conflict-state? conflict-states)
    :pending
    (every? (complement interfering-state?) conflict-states)
    :succeeded
    :else (assert false "This code is unreachable")))

(defn-spec get-conflict-states [::resolution-state ::dt/order]
  (s/coll-of ::conflict-state))
(defn get-conflict-states
  [{:keys [conflict-map]} order]
  (let [order-conflict-map (get conflict-map order {})]
    ;; Workaround for the fact that `(vals {})` is `nil`
    (if (empty? order-conflict-map)
      []
      (vals order-conflict-map))))

(defn-spec order-status [::resolution-state ::dt/order]
  ::order-status)
(defn order-status
  "Whether `order` is known to succeed, known to fail, or doesn't have a known
  outcome in `resolution-state`."
  [resolution-state order]
  (->> order
       (get-conflict-states resolution-state)
       conflict-states-to-order-status))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                        Determining Support ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn-spec supporting-order-statuses [::resolution-state ::dt/order]
  (s/map-of ::order-status integer?))
(defn supporting-order-statuses
  [{:keys [support-map] :as resolution-state} order]
  (let [supporting-orders (get support-map order [])
        support-counts (->> supporting-orders
                            (map (partial order-status resolution-state))
                            (frequencies))]
    (merge {:succeeded 0 :pending 0 :failed 0}
           support-counts)))

(defn-spec max-possible-support [::resolution-state ::dt/order] integer?)
(defn max-possible-support
  [resolution-state order]
  (let [support-counts (supporting-order-statuses resolution-state order)]
    (+ (:succeeded support-counts)
       (:pending support-counts))))

(defn-spec guaranteed-support [::resolution-state ::dt/order] integer?)
(defn guaranteed-support
  [resolution-state order]
  (let [support-counts (supporting-order-statuses resolution-state order)]
    (:succeeded support-counts)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                          Resolving Attacks ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn-spec evaluate-attack-battle
  [::resolution-state ::dt/attack-order ::dt/order ::dt/attack-conflict-rule]
  ::conflict-state-updates)
(defn evaluate-attack-battle
  [rs attack bouncer rule]
  (cond
    (> (guaranteed-support rs attack)
       (max-possible-support rs bouncer))
    [[attack bouncer (j/create-attack-judgment :interferer bouncer
                                               :attack-rule rule
                                               :interfered? false)]]
    (>= (guaranteed-support rs bouncer)
        (max-possible-support rs attack))
    [[attack bouncer (j/create-attack-judgment :interferer bouncer
                                               :attack-rule rule
                                               :interfered? true)]]
    ;; If we're not sure, don't make any conflict state updates.
    :else
    []))

(defn-spec find-failed-to-leave-cycle-helper
  [::resolution-state (s/coll-of ::dt/attack-order)]
  (s/coll-of ::dt/attack-order)
  #(or (empty? (:ret %)) (> (count (:ret %)) 2)))
(defn find-failed-to-leave-cycle-helper
  [{:keys [location-to-order-map] :as rs} attack-orders]
  (let [last-attack (last attack-orders)
        next-attack (location-to-order-map (:destination last-attack))]
    (if (and (not (nil? next-attack))
             (orders/attack? next-attack)
             (= (order-status rs next-attack) :pending)
             (->> next-attack
                  (get-conflict-states rs)
                  (filter (partial s/valid? ::pending-conflict-state))
                  (vec)
                  (= [:failed-to-leave-destination])))
      ;; Make sure the required conditions match apply to the first attack in
      ;; `attack-orders` as well.
      (if (= next-attack
             (first attack-orders))
        attack-orders
        (recur rs (conj attack-orders next-attack)))
      [])))

(defn-spec find-failed-to-leave-cycle
  [::resolution-state ::dt/attack-order] (s/coll-of ::dt/attack-order)
  #(or (empty? (:ret %)) (> (count (:ret %)) 2)))
(defn find-failed-to-leave-cycle
  "The set of orders containing `attack-order` (as a vector), such that they:
  1. all move in a circle
  2. all are in :pending state
  3. all have a :failed-to-leave-destination conflict with the next order in the
     result as their *only* pending conflict.
  Returns an empty vector if no set of orders meets the requirements at this
  time."
  [resolution-state attack-order]
  (find-failed-to-leave-cycle-helper resolution-state [attack-order]))

(defn-spec evaluate-attack-failed-to-leave
  [::resolution-state ::dt/attack-order ::dt/attack-order]
  ::conflict-state-updates)
(defn evaluate-attack-failed-to-leave
  [rs attack bouncer]
  (case (order-status rs bouncer)
    :succeeded [[attack bouncer [:failed-to-leave-destination :no-conflict]]]
    ;; TODO(optimization): should we take steps to avoid looking for a cycle
    ;; unless absolutely necessary?
    :pending (let [failed-to-leave-cycle (find-failed-to-leave-cycle rs attack)]
               (if (empty? failed-to-leave-cycle)
                 []
                 ;; Resolve the *entire* cycle
                 (->> (conj failed-to-leave-cycle (first failed-to-leave-cycle))
                      (partition 2 1)
                      (map (fn [[o1 o2]]
                             [o1 o2
                              [:failed-to-leave-destination :no-conflict]])))))
    :failed
    (cond
      (pos? (guaranteed-support rs attack))
      [[attack bouncer (j/create-attack-judgment :interferer bouncer
                                                 :attack-rule :failed-to-leave-destination
                                                 :interfered? false)]]
      (zero? (max-possible-support rs attack))
      [[attack bouncer (j/create-attack-judgment :interferer bouncer
                                                 :attack-rule :failed-to-leave-destination
                                                 :interfered? true)]]
      :else [])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                         Resolving Supports ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn-spec evaluate-support-conflict
  [::resolution-state ::dt/support-order ::dt/attack-order
   ::dt/support-conflict-rule]
  ::conflict-state-updates)
(defn evaluate-support-conflict
  [resolution-state support attacker rule]
  ;; TODO: handle cutting support
  (case rule
    :attacked
    [[support attacker
      (j/create-support-judgment :interferer attacker
                                 :support-rule rule
                                 :interfered? true)]]
    :attacked-from-supported-location
    [[support attacker
      (j/create-support-judgment :interferer attacker
                                 :support-rule rule
                                 :interfered? false)]]
    (assert false (str "unknown support conflict rule: " rule))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                           Resolution Utils ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn-spec evaluate-conflict [::resolution-state ::pending-conflict]
  ::conflict-state-updates)
(defn evaluate-conflict
  [{:keys [conflict-map] :as resolution-state}
   [order-a order-b rule]]
  (assert (= rule (get-in conflict-map [order-a order-b]))
          (str "(= " rule " "
               (get-in conflict-map [order-a order-b]) ")"))
  (cond
    (orders/attack? order-a)
    (if (= rule :failed-to-leave-destination)
      (evaluate-attack-failed-to-leave resolution-state order-a order-b)
      (evaluate-attack-battle resolution-state order-a order-b rule))
    (orders/support? order-a)
    (evaluate-support-conflict resolution-state order-a order-b rule)
    :else
    (assert false (str "Non-attack non-support conflict: " order-a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                             Utilities for Public Interface ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn-spec get-all-potential-conflicts
  [::dt/dmap ::location-to-order-map ::dt/order]
  (s/coll-of ::pending-conflict))
(defn get-all-potential-conflicts
  [dmap location-to-order-map {:keys [location order-type] :as order}]
  (case order-type
    :hold []
    :convoy []
    :attack
    (let [destination (:destination order)]
      (concat
       (map #(-> [order % :destination-occupied])
            (remains-at dmap location-to-order-map destination))
       (->> (attacks-to dmap location-to-order-map destination)
            (filter #(not= order %))
            (map #(-> [order % :attacked-same-destination])))
       (map #(-> [order % :swapped-places-without-convoy])
            (attacks-from-to dmap location-to-order-map destination location))
       (->> (attacks-from dmap location-to-order-map destination)
            ;; Exclude `:swapped-places-without-convoy` conflicts
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
         (attacks-to dmap location-to-order-map location))))

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
(defn-spec make-support-map [::location-to-order-map] ::support-map)
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

(defn-spec make-location-to-order-map [::dt/orders] ::location-to-order-map)
(defn make-location-to-order-map
  [orders]
  (->> orders
       (map (juxt :location identity))
       (into {})))

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
  (let [location-to-order-map (make-location-to-order-map orders)
        all-conflicts (mapcat (partial get-all-potential-conflicts
                                       diplomacy-map location-to-order-map)
                              orders)
        conflict-queue (into clojure.lang.PersistentQueue/EMPTY all-conflicts)
        conflict-map (apply-conflict-state-updates {} all-conflicts)
        initial-resolution-state
        {:conflict-map conflict-map
         :conflict-queue conflict-queue
         :support-map (make-support-map location-to-order-map)
         :location-to-order-map location-to-order-map
         :dmap diplomacy-map}
        final-resolution-state
        (->> (iterate take-resolution-step initial-resolution-state)
             (filter #(resolution-complete? (:conflict-map %)))
             (first))
        final-conflict-map (:conflict-map final-resolution-state)]
    (merge
     ;; Assume everything is uncontested, then add the results of the conflicts.
     (->> orders
          (map #(-> [% #{}]))
          (into {}))
     ;; There should only be finished judgments in `final-conflict-map`, and
     ;; those judgments already contain the conflicting order.
     (->> final-conflict-map
          (map (fn [[order conflicting-orders-map]]
                 (let [judgments (filter #(not (s/valid? ::no-conflict %))
                                         (vals conflicting-orders-map))]
                   [order judgments])))
          (into {})))))
