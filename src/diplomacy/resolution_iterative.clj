(ns diplomacy.resolution-iterative
  (:require [diplomacy.judgments :as j]
            [diplomacy.orders :as orders]
            [diplomacy.map-functions :as maps]
            [diplomacy.settings]
            [diplomacy.datatypes :as dt]
            [diplomacy.util :refer [defn-spec]]
            [clojure.spec.alpha :as s]))
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
(s/def ::arrival-status #{:succeeded :failed :pending})
;; At the moment `::judgment` also contains the interfering order, duplicating
;; information between the key and value of the nested map. Fixing this isn't
;; necessary.
;;
;; TBD: make this only contain resolved conflicts?
(s/def ::conflict-map (s/map-of ::dt/order
                                (s/map-of ::dt/order ::conflict-state)))
(s/def ::conflict-state-update
  (s/tuple ::dt/order ::dt/order ::conflict-state))
(s/def ::conflict-state-updates (s/coll-of ::conflict-state-update))
(s/def ::pending-conflict
  (s/tuple ::dt/order ::dt/order ::pending-conflict-state))
(s/def ::conflict-queue (s/and (s/coll-of ::pending-conflict) #_queue?))

(s/def ::dislodgment-status #{:dislodged :not-dislodged :pending})
(s/def ::voyage-status #{:succeeded :failed :pending})
(s/def ::voyage-map (s/map-of ::dt/attack-order ::voyage-status))
(s/def ::voyage-queue (s/and (s/coll-of ::dt/attack-order) #_queue?))

;; Map from orders to the orders attempting to support them.
(s/def ::support-map (s/map-of ::dt/order ::dt/orders))
;; Map from orders to the orders attempting to convoy them.
(s/def ::convoy-map (s/map-of ::dt/attack-order ::dt/orders))

;; Set of orders that do not need a convoy because they moving between adjacent
;; locations (though they may still have a convoy).
(s/def ::direct-arrival-set (s/and (s/coll-of ::dt/attack-order) set?))
(s/def ::location-to-order-map (s/map-of ::dt/location ::dt/order))
(s/def ::resolution-state
  (s/keys :req-un [::conflict-map
                   ::conflict-queue
                   ::voyage-map
                   ::voyage-queue
                   ;; Fields that never change during resolution
                   ::support-map
                   ::convoy-map
                   ::direct-arrival-set
                   ::location-to-order-map
                   ::dt/dmap]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                              Resolution Control Flow - Conflict Resolution ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(declare evaluate-conflict apply-conflict-state-updates remove-conflicts)

(defn-spec all-orders-resolved? [::resolution-state] boolean?)
(defn all-orders-resolved? [{:keys [conflict-map voyage-map]}]
  (let [all-conflict-states
        (for [[order conflicting-orders-map] conflict-map
              [conflicting-order conflict-state] conflicting-orders-map]
          conflict-state)]
    (and (every? (partial s/valid? ::resolved-conflict-state)
                 all-conflict-states)
         (every? #{:succeeded :failed} (vals voyage-map)))))

(defn-spec take-conflict-resolution-step [::resolution-state] ::resolution-state)
(defn take-conflict-resolution-step
  "Tries to resolve the next conflict in the conflict queue, if there is one."
  [{:keys [conflict-map conflict-queue
           location-to-order-map dmap]
    :as resolution-state}]
  (when diplomacy.settings/debug
    (print "conflict-queue: ")
    (clojure.pprint/pprint conflict-queue)
    (print "conflict-map: ")
    (clojure.pprint/pprint conflict-map))

  (if (empty? conflict-queue)
    resolution-state
    (let [pending-conflict (peek conflict-queue)
          conflict-state-updates (evaluate-conflict resolution-state
                                                    pending-conflict)]
      (when diplomacy.settings/debug
        (print "conflict-state-updates: ")
        (clojure.pprint/pprint conflict-state-updates))
      (-> resolution-state
          (update :conflict-queue
                  #(-> %
                       (move-front-to-back)
                       (remove-conflicts conflict-state-updates)))
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

(defn-spec remove-conflict
  [::conflict-queue ::conflict-state-update] ::conflict-queue)
(defn remove-conflict
  "Removes the conflict in `conflict-state-update` from `conflict-queue`."
  [conflict-queue conflict-state-update]
  ;; Not necessary, but useful to check this invariant while we have it.
  (assert (or (s/valid? ::resolved-conflict-state (nth conflict-state-update 2))
              (s/valid? ::no-conflict (nth conflict-state-update 2)))
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
  ;; This is inefficient, but we can optimize later
  (reduce remove-conflict conflict-queue conflict-state-updates))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                Resolution Control Flow - Voyage Resolution ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(declare evaluate-voyage)

;; NOTE: signature is very different from `apply-conflict-state-updates`
(defn-spec apply-voyage-state-update
  [::resolution-state ::dt/attack-order ::voyage-status] ::resolution-state)
(defn apply-voyage-state-update
  [resolution-state pending-voyage voyage-status]
  (-> resolution-state
      (update :voyage-queue
              #(if (= voyage-status :pending)
                 (move-front-to-back %)
                 (pop %)))
      (update :voyage-map #(assoc % pending-voyage voyage-status))))

(defn-spec take-voyage-resolution-step
  [::resolution-state] ::resolution-state)
(defn take-voyage-resolution-step
  "Tries to resolve the next voyage in the voyage queue."
  [{:keys [voyage-map voyage-queue] :as resolution-state}]
  (when diplomacy.settings/debug
    (print "voyage-queue: ")
    (clojure.pprint/pprint voyage-queue)
    (print "voyage-map: ")
    (clojure.pprint/pprint voyage-map))

  (if (empty? voyage-queue)
    resolution-state
    (let [pending-voyage (peek voyage-queue)
          voyage-status-update
          (evaluate-voyage resolution-state pending-voyage)]
      (when diplomacy.settings/debug
        (print "voyage-update: ")
        (clojure.pprint/pprint [pending-voyage voyage-status-update]))
      (apply-voyage-state-update
       resolution-state pending-voyage voyage-status-update))))

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

(defn-spec arrival-status [::resolution-state ::dt/attack-order]
  ::arrival-status)
(defn arrival-status
  [{:keys [direct-arrival-set voyage-map]} attack-order]
  (if (contains? direct-arrival-set attack-order)
    :succeeded
    (get voyage-map attack-order)))

(defn-spec order-status [::resolution-state ::dt/order]
  ::order-status)
(defn order-status
  "Whether `order` is known to succeed, known to fail, or doesn't have a known
  outcome in `resolution-state`."
  [rs order]
  (if (or (orders/support? order)
          (and (orders/attack? order)
               (= (arrival-status rs order) :succeeded)))
    (->> order
         (get-conflict-states rs)
         conflict-states-to-order-status)
    ;; :pending or :failed
    (arrival-status rs order)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                        Determining Support ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def ::support-type #{:offense
                        :defense
                        :offense-assume-beleaguered-garrison-leaves})
(s/def ::willingness-to-support #{:yes
                                  :no
                                  :pending-beleaguered-garrison-leaving})

(defn-spec willingness-to-support
  [::resolution-state ::dt/support-order ::dt/order ::dt/order ::dt/conflict-rule ::support-type] ::willingness-to-support)
(defn willingness-to-support
  [{:keys [location-to-order-map] :as rs}
   supporting-order supported-order combatant rule support-type]
  (if (= support-type :defense)
    :yes
    (let [attempting-to-dislodge-combatant? (not= rule :attacked-same-destination)
          combatant-friendly? (= (:country supporting-order) (:country combatant))]
      (if attempting-to-dislodge-combatant?
        (if combatant-friendly? :no :yes)
        ;; The `:attacked-same-destination` case
        (let [beleaguered-garrison (location-to-order-map (:destination supported-order))
              beleaguered-garrison-friendly? (= (:country supporting-order)
                                                (:country beleaguered-garrison))]
          (if (or (nil? beleaguered-garrison)
                  (not beleaguered-garrison-friendly?))
            :yes
            ;; The friendly beleaguered garrison case
            (if (orders/attack? beleaguered-garrison)
              (case (order-status rs beleaguered-garrison)
                :succeeded :yes
                :failed :no
                :pending
                (if (= support-type :offense-assume-beleaguered-garrison-leaves)
                  :yes
                  :pending-beleaguered-garrison-leaving))
              :no)))))))

(defn-spec supporting-order-statuses [::resolution-state ::dt/order ::dt/order ::dt/conflict-rule ::support-type]
  (s/map-of (s/or :tag1 ::order-status :tag2 #{:unwilling}) integer?))
(defn supporting-order-statuses
  [{:keys [support-map] :as rs} supported-order combatant rule support-type]
  (let [supporting-orders (get support-map supported-order [])
        support-statuses
        (map (fn [supporting-order]
               (case (willingness-to-support rs supporting-order supported-order combatant rule support-type)
                 :yes (order-status rs supporting-order)
                 :pending-beleaguered-garrison-leaving :pending
                 :no :unwilling))
             supporting-orders)
        support-counts (frequencies support-statuses)]
    (merge {:succeeded 0 :pending 0 :failed 0 :unwilling 0}
           support-counts)))

(defn-spec max-possible-support [::resolution-state ::dt/order ::dt/order ::dt/conflict-rule ::support-type]
  integer?)
(defn max-possible-support
  [resolution-state order interferer rule support-type]
  (let [support-counts (supporting-order-statuses resolution-state order interferer rule support-type)]
    (+ (:succeeded support-counts)
       (:pending support-counts))))

(defn-spec guaranteed-support [::resolution-state ::dt/order ::dt/order ::dt/conflict-rule ::support-type]
  integer?)
(defn guaranteed-support
  [resolution-state order interferer rule support-type]
  (let [support-counts (supporting-order-statuses resolution-state order interferer rule support-type)]
    (:succeeded support-counts)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                          Resolving Attacks ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def ::assume-beleaguered-garrison-leaves boolean?)
(s/def ::battle-settings (s/keys :req-un [::assume-beleaguered-garrison-leaves]))

(defn-spec evaluate-attack-battle
  [::resolution-state ::dt/attack-order ::dt/order ::dt/attack-conflict-rule
   ::battle-settings]
  ::conflict-state-updates)
(defn evaluate-attack-battle
  "Does not account for dislodging a unit from the same country."
  [rs attack bouncer rule {:keys [assume-beleaguered-garrison-leaves]}]
  (let [offensive-support-type (if assume-beleaguered-garrison-leaves
                                 :offense-assume-beleaguered-garrison-leaves
                                 :offense)]
  (cond
    (> (guaranteed-support rs attack bouncer rule offensive-support-type)
       (max-possible-support rs bouncer attack rule :defense))
    [[attack bouncer (j/create-attack-judgment :interferer bouncer
                                               :attack-rule rule
                                               :interfered? false)]]
    (>= (guaranteed-support rs bouncer attack rule :defense)
        (max-possible-support rs attack bouncer rule offensive-support-type))
    [[attack bouncer (j/create-attack-judgment :interferer bouncer
                                               :attack-rule rule
                                               :interfered? true)]]
    ;; If we're not sure, don't make any conflict state updates.
    :else
    [])))

(defn-spec find-failed-to-leave-cycle-helper
  [::resolution-state (s/coll-of ::dt/attack-order)]
  (s/coll-of ::dt/attack-order)
  #(or (empty? (:ret %)) (> (count (:ret %)) 2)))
(defn find-failed-to-leave-cycle-helper
  [{:keys [location-to-order-map conflict-map] :as rs} attack-orders]
  (let [last-attack (last attack-orders)
        conflicts (get conflict-map last-attack)
        conflict-states (get-conflict-states rs last-attack)]
    (if (and (orders/attack? last-attack)
             (= (order-status rs last-attack) :pending)
             (some (partial = :failed-to-leave-destination)
                   conflict-states)
             (every?
              (fn [[interferer conflict-state]]
                (or (= conflict-state :failed-to-leave-destination)
                    (and
                     (s/valid? ::resolved-conflict-state conflict-state)
                     (not (:interfered? conflict-state)))
                    ;; Make sure resolution doesn't get into an infinite loop
                    ;; where this function is waiting on an attack that may
                    ;; break the cycle to resolve, but resolving that attack is
                    ;; waiting on whether an order in the cycle fails to move
                    ;; and becomes a beleaguered garrison. See case Z3 in
                    ;; `datc_cases.clj`.
                    (and
                     (= conflict-state :attacked-same-destination)
                     (let [hypothetical-updates
                           (evaluate-attack-battle rs last-attack interferer :attacked-same-destination
                                                   {:assume-beleaguered-garrison-leaves true})]
                       (some (fn [[o1 o2 hypothetical-conflict-state]]
                               (and (= o1 last-attack)
                                    (= o2 interferer)
                                    (s/valid? ::resolved-conflict-state hypothetical-conflict-state)
                                    (not (:interfered? hypothetical-conflict-state))))
                             hypothetical-updates)))))
                    conflicts))
      ;; Smallest possible cycle involves 3 distinct orders, and the return
      ;; value must **start and end** with the same order.
      (if (and (>= (count attack-orders) 4)
               (= last-attack
                  (first attack-orders)))
        attack-orders
        (let [next-attack (location-to-order-map (:destination last-attack))]
          (recur rs (conj attack-orders next-attack))))
      [])))

(defn-spec find-failed-to-leave-cycle
  [::resolution-state ::dt/attack-order]
  (s/and (s/coll-of ::dt/attack-order)
         #(or (empty? %)
              (and (>= (count %) 4)
                   (= (first %) (last %))))))
(defn find-failed-to-leave-cycle
  "The sequence of orders **start and ending** with `attack-order` such that
  they all successfully move in a circle. Returns an empty sequence if no such
  sequence is known at this time."
  [resolution-state attack-order]
  (find-failed-to-leave-cycle-helper resolution-state [attack-order]))

(defn-spec evaluate-attack-failed-to-leave
  [::resolution-state ::dt/attack-order ::dt/attack-order]
  ::conflict-state-updates)
(defn evaluate-attack-failed-to-leave
  "Does not account for dislodging a unit from the same country."
  [{:keys [conflict-map] :as rs} attack bouncer]
  (case (order-status rs bouncer)
    :succeeded [[attack bouncer [:failed-to-leave-destination :no-conflict]]]
    ;; TODO(optimization): should we take steps to avoid looking for a cycle
    ;; unless absolutely necessary?
    :pending (let [failed-to-leave-cycle (find-failed-to-leave-cycle rs attack)]
               (if (empty? failed-to-leave-cycle)
                 []
                 ;; Indicate that the cycle moves successfully. Much of this is
                 ;; necessary due to test case Z13.
                 (let [failed-to-leave-cycle-set (set failed-to-leave-cycle)]
                   (apply concat
                          (for [[order conflicting-orders-map] conflict-map
                                [conflicting-order conflict-state] conflicting-orders-map
                                :when (and (contains? failed-to-leave-cycle-set conflicting-order)
                                           (contains? #{:failed-to-leave-destination :attacked-same-destination}
                                                      conflict-state))]
                            (case conflict-state
                              :failed-to-leave-destination
                              [[order conflicting-order [:failed-to-leave-destination :no-conflict]]]
                              :attacked-same-destination
                              [[order conflicting-order (j/create-attack-judgment :interferer conflicting-order
                                                                                  :attack-rule :attacked-same-destination
                                                                                  :interfered? true)]
                               [conflicting-order order (j/create-attack-judgment :interferer order
                                                                                  :attack-rule :attacked-same-destination
                                                                                  :interfered? false)]]))))))
    :failed
    (cond
      (pos? (guaranteed-support rs attack bouncer :failed-to-leave-destination :offense))
      [[attack bouncer (j/create-attack-judgment :interferer bouncer
                                                 :attack-rule :failed-to-leave-destination
                                                 :interfered? false)]]
      (zero? (max-possible-support rs attack bouncer :failed-to-leave-destination :offense))
      [[attack bouncer (j/create-attack-judgment :interferer bouncer
                                                 :attack-rule :failed-to-leave-destination
                                                 :interfered? true)]]
      :else [])))

(defn-spec forbid-self-dislodgment [::conflict-state-update]
  ::conflict-state-update)
(defn forbid-self-dislodgment
  "If `order` dislodges `interferer` and they're from the same country, mark
  `order` as failed."
  [[order interferer conflict-state :as original-update]]
  (if (and (= (:country order) (:country interferer))
           (s/valid? ::dt/judgment conflict-state)
           (contains? #{:destination-occupied
                        :swapped-places-without-convoy
                        :failed-to-leave-destination}
                      ;; Don't forget the structure of
                      ;; `::dt/attack-conflict-situation`!!
                      (get-in conflict-state [:conflict-situation :attack-conflict-rule]))
           (not (:interfered? conflict-state)))
    [order interferer (assoc conflict-state
                             :interfered? true
                             :would-dislodge-own-unit? true)]
    original-update))

(defn-spec forbid-effect-on-dislodgers-province
  [::resolution-state ::conflict-state-update] (s/nilable ::conflict-state-update))
(defn forbid-effect-on-dislodgers-province
  "Mark `interferer` as not interfering if `interferer` should have no effect on
  `order` by the `no-effect-on-dislodgers-province` rule. If we don't have
  enough information to tell yet, return `nil`."
  [{:keys [location-to-order-map] :as resolution-state}
   [order interferer conflict-state :as original-update]]
  (assert (s/valid? ::dt/attack-order order))
  (let [potential-dislodger (location-to-order-map (:destination order))]
    (if (and (some? potential-dislodger)
             (s/valid? ::dt/judgment conflict-state)
             (= (get-in conflict-state
                        [:conflict-situation :attack-conflict-rule])
                :attacked-same-destination)
             (:interfered? conflict-state)
             (orders/attack? potential-dislodger)
             (= (:destination potential-dislodger) (:location interferer)))
      (case (order-status resolution-state potential-dislodger)
        :succeeded
        ;; I decided not to add any indication that the
        ;; `:no-effect-on-dislodgers-province` rule is being used in order to
        ;; proceed more quickly.
        (assoc-in original-update [2 :interfered?] false)
        :failed
        original-update
        :pending
        nil)
      original-update)))

(defn-spec evaluate-attack-conflict
  [::resolution-state ::dt/attack-order ::dt/order ::dt/attack-conflict-rule]
  ::conflict-state-updates)
(defn evaluate-attack-conflict
  [rs attack bouncer rule]
  (case (arrival-status rs attack)
    :failed [[attack bouncer [rule :no-conflict]]]
    :pending []
    :succeeded
    (cond
      (= rule :failed-to-leave-destination)
      (->> (evaluate-attack-failed-to-leave rs attack bouncer)
           (map forbid-self-dislodgment)
           (map (partial forbid-effect-on-dislodgers-province rs))
           (filter some?))

      (or (contains? #{:swapped-places-without-convoy :destination-occupied} rule)
          (and (= rule :attacked-same-destination)
               (= (arrival-status rs bouncer) :succeeded)))
      (->> (evaluate-attack-battle rs attack bouncer rule
                                   {:assume-beleaguered-garrison-leaves false})
           (map forbid-self-dislodgment)
           (map (partial forbid-effect-on-dislodgers-province rs))
           (filter some?))

      (and (= rule :attacked-same-destination)
           (= (arrival-status rs bouncer) :failed))
      [[attack bouncer [rule :no-conflict]]]

      (and (= rule :attacked-same-destination)
           (= (arrival-status rs bouncer) :pending))
      [])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                         Resolving Supports ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(declare convoy-path-exists? dislodgment-status)

(defn-spec evaluate-support-conflict
  [::resolution-state ::dt/support-order ::dt/attack-order
   ::dt/support-conflict-rule]
  ::conflict-state-updates)
(defn evaluate-support-conflict
  [{:keys [location-to-order-map dmap convoy-map] :as rs}
   {:keys [assisted-order] :as support}
   attacker rule]
  (let [attack-arrival-status (arrival-status rs attacker)]
    (cond
      (= attack-arrival-status :pending)
      []
      (= attack-arrival-status :failed)
      [[support attacker [rule :no-conflict]]]

      (and (= rule :attacked) (= (:country support) (:country attacker)))
      [[support attacker
        (j/create-support-judgment :interferer attacker
                                   :support-rule :attacked-by-same-country
                                   :interfered? false)]]
      (and (= rule :attacked) (not= (:country support) (:country attacker)))
      [[support attacker
        (j/create-support-judgment :interferer attacker
                                   :support-rule :attacked
                                   :interfered? true)]]

      (= rule :attacked-from-supported-location)
      (case (order-status rs attacker)
        :succeeded
        [[support attacker
          (j/create-support-judgment :interferer attacker
                                     :support-rule :dislodged
                                     :interfered? true)]]
        :pending
        []
        :failed
        [[support attacker
          (j/create-support-judgment :interferer attacker
                                     :support-rule :attacked-from-supported-location
                                     :interfered? false)]]))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                     Resolving any conflict ;;
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
    (evaluate-attack-conflict resolution-state order-a order-b rule)
    (orders/support? order-a)
    (evaluate-support-conflict resolution-state order-a order-b rule)
    :else
    (assert false (str "Non-attack non-support conflict: " order-a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                Resolving Voyages (Convoys) ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn-spec convoy-path-exists?-helper [::dt/dmap ::dt/location ::dt/location
                                       (s/and (s/coll-of ::dt/location) set?)
                                       boolean?]
  boolean?)
(defn convoy-path-exists?-helper
  [dmap start-loc end-loc convoy-locations path-length-so-far-is-zero?]
  (cond
    (and (maps/colocated-edge-accessible-to? dmap start-loc end-loc :fleet)
         (not path-length-so-far-is-zero?))
    true
    (empty? convoy-locations)
    false
    :else
    ;; TODO this is very inefficient on large inputs
    (->> convoy-locations
         (filter #(maps/colocated-edge-accessible-to? dmap start-loc % :fleet))
         (some (fn [next-convoy]
                 (convoy-path-exists?-helper
                  dmap
                  next-convoy
                  end-loc
                  (disj convoy-locations next-convoy)
                  false)))
         boolean)))

(defn-spec convoy-path-exists? [::dt/dmap ::dt/location ::dt/location ::dt/orders]
  boolean?)
(defn convoy-path-exists?
  [dmap start-loc end-loc convoy-orders]
  (let [convoy-locations (->> convoy-orders
                              (map :location)
                              (into #{}))]
    (convoy-path-exists?-helper dmap start-loc end-loc convoy-locations true)))

(defn-spec dislodgment-status [::resolution-state ::dt/convoy-order]
  ::dislodgment-status)
(defn dislodgment-status
  [{:keys [dmap location-to-order-map] :as resolution-state}
   {:keys [location] :as convoy-order}]
  (let [attacking-order-statuses
        (->> (attacks-to dmap location-to-order-map location)
             (map #(order-status resolution-state %)))]
    (cond
      (some #(= :succeeded %) attacking-order-statuses)
      :dislodged
      (some #(= :pending %) attacking-order-statuses)
      :pending
      (every? #(= :failed %) attacking-order-statuses)
      :not-dislodged)))

(defn-spec convoying-order-statuses [::resolution-state ::dt/attack-order]
  (s/map-of ::dislodgment-status (s/coll-of ::dt/convoy-order)))
(defn convoying-order-statuses
  [{:keys [convoy-map] :as rs}
   attack-order]
  (let [attempted-convoys (get convoy-map attack-order [])]
    (group-by #(dislodgment-status rs %) attempted-convoys)))

(defn-spec evaluate-voyage [::resolution-state ::dt/attack-order]
  ::voyage-status)
(defn evaluate-voyage
  [{:keys [dmap convoy-map] :as rs}
   {:keys [location destination] :as attack-order}]
  (let [convoys-by-status (convoying-order-statuses rs attack-order)
        successful-convoys (get convoys-by-status :not-dislodged [])
        pending-convoys (get convoys-by-status :pending [])]
    (cond
      (convoy-path-exists? dmap location destination successful-convoys)
      :succeeded
      (convoy-path-exists? dmap location destination
                           (concat successful-convoys pending-convoys))
      :pending
      :else
      :failed)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                          Getting to final resolution state ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn fixpoint
  "Apply `f` to `initial`, then apply `f` again to the result, repeating until
  applying `f` yields a result equal to the input to `f`. Return that
  result (which is a fixpoint of `f`)."
  [f initial]
  (let [f-results (iterate f initial)]
    (reduce #(if (= %1 %2) (reduced %2) %2) f-results)))

(defn-spec try-resolve-every-voyage [::resolution-state] ::resolution-state)
(defn try-resolve-every-voyage
  [{:keys [voyage-queue] :as rs}]
  (nth (iterate take-voyage-resolution-step rs)
       (count voyage-queue)))

(defn-spec try-resolve-every-conflict [::resolution-state] ::resolution-state)
(defn try-resolve-every-conflict
  [{:keys [conflict-queue] :as rs}]
  (nth (iterate take-conflict-resolution-step rs)
       (count conflict-queue)))

(defn-spec get-final-resolution-state [::resolution-state] ::resolution-state)
(defn get-final-resolution-state
  [resolution-state]
  (let [stable-rs
        (fixpoint (comp try-resolve-every-conflict
                        try-resolve-every-voyage)
                  resolution-state)
        stable-rs-with-failed-voyages
        ;; TODO: report 'this voyage failed because a paradox was found' in the
        ;; result. `before-simple-convoy-paradox-simplification` branch had
        ;; separate code to find
        ;; `:army-cant-cut-support-for-attack-on-its-own-convoy`, which could be
        ;; brought back if desired, but I removed it to simplify things.
        (-> stable-rs
            (update :voyage-map
                    #(into {} (map (fn [[attack-order voyage-status]]
                                     [attack-order (if (= voyage-status :pending) :failed voyage-status)])
                                   %)))
            (assoc :voyage-queue clojure.lang.PersistentQueue/EMPTY))]
    (->> (iterate take-conflict-resolution-step stable-rs-with-failed-voyages)
         (filter all-orders-resolved?)
         (first))))

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

;; TODO: reduce duplication between `make-support-map` and `make-convoy-map`
;; if desired.
(defn-spec make-convoy-map [::location-to-order-map] ::convoy-map)
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

(defn-spec make-direct-arrival-set [::dt/dmap ::dt/orders] ::direct-arrival-set)
(defn make-direct-arrival-set
  [dmap orders]
  (->> orders
       (filter orders/attack?)
       (filter
        (fn [{:keys [location destination unit-type] :as order}]
          (maps/edge-accessible-to? dmap location destination unit-type)))
       (into #{})))

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
        convoy-map (make-convoy-map location-to-order-map)
        direct-arrival-set (make-direct-arrival-set diplomacy-map orders)
        voyage-queue (into clojure.lang.PersistentQueue/EMPTY
                           (concat
                            (keys convoy-map)
                            (filter #(and (orders/attack? %)
                                          (not (contains? direct-arrival-set %)))
                                    orders)))
        initial-resolution-state
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
         :dmap diplomacy-map}
        final-resolution-state
        (get-final-resolution-state initial-resolution-state)
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
          (into {}))
     ;; Add failed convoys
     (->> orders
          (filter orders/attack?)
          (filter (fn [attack-order] (= (arrival-status final-resolution-state attack-order) :failed)))
          (map (fn [attack-order] [attack-order #{:no-successful-convoy}]))
          (into {})))))
