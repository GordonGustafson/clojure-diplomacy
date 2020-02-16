(ns diplomacy.resolution-iterative.all
  (:require [diplomacy.resolution-iterative.datatypes :as r]
            [diplomacy.resolution-iterative.map-util :as map-util]
            [diplomacy.resolution-iterative.init :as init]
            [diplomacy.judgments :as j]
            [diplomacy.orders :as orders]
            [diplomacy.map-functions :as maps]
            [diplomacy.settings]
            [diplomacy.datatypes :as dt]
            [diplomacy.util :refer [defn-spec]]
            [clojure.spec.alpha :as s]))
(require 'clojure.pprint)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                            Queue Functions ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn queue?
  [collection]
  (instance? clojure.lang.PersistentQueue collection))

(defn move-front-to-back
  [queue]
  (conj (pop queue) (peek queue)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                              Resolution Control Flow - Conflict Resolution ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(declare evaluate-conflict apply-conflict-state-updates remove-conflicts)

(defn-spec all-orders-resolved? [::r/resolution-state] boolean?)
(defn all-orders-resolved? [{:keys [conflict-map voyage-map]}]
  (let [all-conflict-states
        (for [[order conflicting-orders-map] conflict-map
              [conflicting-order conflict-state] conflicting-orders-map]
          conflict-state)]
    (and (every? (partial s/valid? ::r/resolved-conflict-state)
                 all-conflict-states)
         (every? #{:succeeded :failed} (vals voyage-map)))))

(defn-spec take-conflict-resolution-step [::r/resolution-state] ::r/resolution-state)
(defn take-conflict-resolution-step
  "Tries to evaluate the first conflict in the conflict queue, if there is one.

  If the first conflict in the queue can be evaluated, this function updates
  `conflict-map` with the results, and removes any resolved conflicts from
  `conflict-queue`. If the first conflict cannot be evaluated, it is moved to
  the back of the queue."
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
  [::r/conflict-map ::r/conflict-state-updates] ::r/conflict-map)
(defn apply-conflict-state-updates
  "Applies the updates in `conflict-state-updates` to `conflict-map`,
  performing any necessary bookkeeping."
  [conflict-map conflict-state-updates]
  (reduce (fn [cmap [order conflicting-order conflict-state]]
            (assoc-in cmap [order conflicting-order] conflict-state))
          conflict-map
          conflict-state-updates))

(defn-spec remove-conflict
  [::r/conflict-queue ::r/conflict-state-update] ::r/conflict-queue)
(defn remove-conflict
  "Removes the conflict in `conflict-state-update` from `conflict-queue`."
  [conflict-queue conflict-state-update]
  ;; Not necessary, but useful to check this invariant while we have it.
  (assert (or (s/valid? ::r/resolved-conflict-state (nth conflict-state-update 2))
              (s/valid? ::r/no-conflict (nth conflict-state-update 2)))
          (str conflict-state-update))
  (->> conflict-queue
       (filter (fn [queue-item]
                 (not= (take 2 queue-item)
                       (take 2 conflict-state-update))))
       (into clojure.lang.PersistentQueue/EMPTY)))

(defn-spec remove-conflicts
  [::r/conflict-queue ::r/conflict-state-updates] ::r/conflict-queue)
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
  [::r/resolution-state ::dt/attack-order ::r/voyage-status] ::r/resolution-state)
(defn apply-voyage-state-update
  [resolution-state pending-voyage voyage-status]
  (-> resolution-state
      (update :voyage-queue
              #(if (= voyage-status :pending)
                 (move-front-to-back %)
                 (pop %)))
      (update :voyage-map #(assoc % pending-voyage voyage-status))))

(defn-spec take-voyage-resolution-step
  [::r/resolution-state] ::r/resolution-state)
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
;;                                                  Determining Order Success ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn-spec interfering-state? [::r/resolved-conflict-state] boolean?)
(defn interfering-state?
  "Whether `rcs` is a judgment where the interferer successfully interferes."
  [rcs]
  (cond
    (s/valid? ::dt/judgment rcs) (:interfered? rcs)
    (s/valid? ::r/no-conflict rcs) false
    :else (assert false
                  (str "interfering-state? passed invalid resolved-conflict-state: "
                       rcs))))

(defn-spec pending-conflict-state? [::r/conflict-state] boolean?)
(defn pending-conflict-state?
  [conflict-state]
  (s/valid? ::r/pending-conflict-state conflict-state))

(defn-spec conflict-states-to-order-status [(s/coll-of ::r/conflict-state)]
  ::r/order-status)
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

(defn-spec get-conflict-states [::r/resolution-state ::dt/order]
  (s/coll-of ::r/conflict-state))
(defn get-conflict-states
  [{:keys [conflict-map]} order]
  (let [order-conflict-map (get conflict-map order {})]
    ;; Workaround for the fact that `(vals {})` is `nil`
    (if (empty? order-conflict-map)
      []
      (vals order-conflict-map))))

(defn-spec arrival-status [::r/resolution-state ::dt/attack-order]
  ::r/arrival-status)
(defn arrival-status
  [{:keys [direct-arrival-set voyage-map]} attack-order]
  (if (contains? direct-arrival-set attack-order)
    :succeeded
    (get voyage-map attack-order)))

(defn-spec order-status [::r/resolution-state ::dt/order]
  ::r/order-status)
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
;;                          Determining whether an arrival was made by convoy ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; All code outside this section is *only* concerned with whether a unit arrived
;; or not, ignoring whether it arrived by convoy or by land. This concept of
;; 'arrival method' could certainly be handled 'deeper' in the resolution engine
;; and included in `::r/resolution-state`, but that would be additional work for
;; little meaningful benefit.

(defn-spec arrives-via-convoy? [::r/resolution-state ::dt/attack-order] boolean?)
(defn arrives-via-convoy?
  "Whether `attack-order` is known to arrive using a convoy."
  [{:keys [dmap convoy-map] :as rs}
   {:keys [location destination] :as attack-order}]
  (and (orders/army? attack-order)
       ;; Call `evaluate-voyage` instead of checking `voyage-map` because
       ;; `voyage-map` does not include convoys to adjacent locations.
       (= (evaluate-voyage rs attack-order) :succeeded)
       (or
        (not (maps/edge-accessible-to? dmap location destination :army))
        ;; If this is a convoy to an adjacent location, require that some sort
        ;; of 'intent to convoy' was expressed.
        (some #(= (:country %) (:country attack-order))
              (get convoy-map attack-order [])))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                        Determining Support ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn-spec willingness-to-support
  [::r/resolution-state ::dt/support-order ::dt/order ::dt/order ::dt/conflict-rule ::r/support-type] ::r/willingness-to-support)
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

(defn-spec supporting-order-statuses [::r/resolution-state ::dt/order ::dt/order ::dt/conflict-rule ::r/support-type]
  (s/map-of (s/or :tag1 ::r/order-status :tag2 #{:unwilling}) integer?))
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

(defn-spec max-possible-support [::r/resolution-state ::dt/order ::dt/order ::dt/conflict-rule ::r/support-type]
  integer?)
(defn max-possible-support
  [resolution-state order interferer rule support-type]
  (let [support-counts (supporting-order-statuses resolution-state order interferer rule support-type)]
    (+ (:succeeded support-counts)
       (:pending support-counts))))

(defn-spec guaranteed-support [::r/resolution-state ::dt/order ::dt/order ::dt/conflict-rule ::r/support-type]
  integer?)
(defn guaranteed-support
  [resolution-state order interferer rule support-type]
  (let [support-counts (supporting-order-statuses resolution-state order interferer rule support-type)]
    (:succeeded support-counts)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                          Resolving Attacks ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn-spec evaluate-attack-battle
  [::r/resolution-state ::dt/attack-order ::dt/order ::dt/attack-conflict-rule
   ::r/battle-settings]
  ::r/conflict-state-updates)
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
  [::r/resolution-state (s/coll-of ::dt/attack-order)]
  (s/coll-of ::dt/attack-order)
  #(or (empty? (:ret %)) (> (count (:ret %)) 2)))
(defn find-failed-to-leave-cycle-helper
  [{:keys [location-to-order-map conflict-map] :as rs} attack-orders]
  (let [last-attack (last attack-orders)
        conflicts (get conflict-map last-attack)
        conflict-states (get-conflict-states rs last-attack)]
    (if (and (orders/attack? last-attack)
             (= (arrival-status rs last-attack) :succeeded)
             (= (order-status rs last-attack) :pending)
             (some (partial = :failed-to-leave-destination)
                   conflict-states)
             (every?
              (fn [[interferer conflict-state]]
                (or (= conflict-state :failed-to-leave-destination)
                    (and
                     (s/valid? ::r/resolved-conflict-state conflict-state)
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
                                    (s/valid? ::r/resolved-conflict-state hypothetical-conflict-state)
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
  [::r/resolution-state ::dt/attack-order]
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
  [::r/resolution-state ::dt/attack-order ::dt/attack-order]
  ::r/conflict-state-updates)
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

(defn-spec forbid-self-dislodgment [::r/conflict-state-update]
  ::r/conflict-state-update)
(defn forbid-self-dislodgment
  "If `order` dislodges `interferer` and they're from the same country, mark
  `order` as failed."
  [[order interferer conflict-state :as original-update]]
  (if (and (= (:country order) (:country interferer))
           (s/valid? ::dt/judgment conflict-state)
           (contains? #{:destination-occupied
                        :swapped-places
                        :failed-to-leave-destination}
                      ;; Don't forget the structure of
                      ;; `::dt/attack-conflict-situation`!!
                      (get-in conflict-state [:conflict-situation :attack-conflict-rule]))
           (not (:interfered? conflict-state)))
    [order interferer (assoc conflict-state
                             :interfered? true
                             :would-dislodge-own-unit? true)]
    original-update))

(defn-spec forbid-effect-on-dislodgers-province-helper
  [::r/resolution-state ::r/conflict-state-update] (s/nilable ::r/conflict-state-update))
(defn forbid-effect-on-dislodgers-province-helper
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

(defn-spec forbid-effects-on-dislodgers-provinces
  [::r/resolution-state ::r/conflict-state-updates] ::r/conflict-state-updates)
(defn forbid-effects-on-dislodgers-provinces
  "For each conflict state update, mark each `interferer` as not interfering if
  the `interferer` should have no effect on the `order` by the
  `no-effect-on-dislodgers-province` rule. If we don't have enough information
  to tell yet, remove the conflict state update from the list."
  [rs conflict-state-updates]
  (->> conflict-state-updates
       (map (partial forbid-effect-on-dislodgers-province-helper rs))
       (filter some?)))

(defn-spec evaluate-attack-conflict
  [::r/resolution-state ::dt/attack-order ::dt/order ::dt/attack-conflict-rule]
  ::r/conflict-state-updates)
(defn evaluate-attack-conflict
  [rs attack bouncer rule]
  (case (arrival-status rs attack)
    :failed [[attack bouncer [rule :no-conflict]]]
    :pending []
    :succeeded
    (cond
        ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; :failed-to-leave-destination

      ;; Try to evaluate `:failed-to-leave-destination` conflicts even if
      ;; `bouncer`'s arrival status is still pending. This is to allow orders
      ;; moving in a circle.
      (= rule :failed-to-leave-destination)
      (->> (evaluate-attack-failed-to-leave rs attack bouncer)
           (map forbid-self-dislodgment)
           (filter some?))

        ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; bouncer arrival pending

      ;; TODO: For `:swapped-places`, is there ever a situation where we have
      ;; to resolve this conflict before knowing whether `bouncer` arrives or
      ;; not?
      (and (contains? #{:attacked-same-destination :swapped-places} rule)
           (= (arrival-status rs bouncer) :pending))
      []

        ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; :no-conflict

      (and (= rule :attacked-same-destination)
           (= (arrival-status rs bouncer) :failed))
      [[attack bouncer [rule :no-conflict]]]

      (and (= rule :swapped-places)
           (= (arrival-status rs bouncer) :succeeded)
           (or (arrives-via-convoy? rs attack)
               (arrives-via-convoy? rs bouncer)))
      [[attack bouncer [rule :no-conflict]]]

        ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Battle!

      :else
      (->>
       (evaluate-attack-battle rs attack bouncer rule
                               {:assume-beleaguered-garrison-leaves false})
       (map forbid-self-dislodgment)
       (forbid-effects-on-dislodgers-provinces rs)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                         Resolving Supports ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(declare convoy-path-exists? dislodgment-status)

(defn-spec evaluate-support-conflict
  [::r/resolution-state ::dt/support-order ::dt/attack-order
   ::dt/support-conflict-rule]
  ::r/conflict-state-updates)
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

(defn-spec evaluate-conflict [::r/resolution-state ::r/pending-conflict]
  ::r/conflict-state-updates)
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

(defn-spec dislodgment-status [::r/resolution-state ::dt/convoy-order]
  ::r/dislodgment-status)
(defn dislodgment-status
  [{:keys [dmap location-to-order-map] :as resolution-state}
   {:keys [location] :as convoy-order}]
  (let [attacking-order-statuses
        (->> (map-util/attacks-to dmap location-to-order-map location)
             (map #(order-status resolution-state %)))]
    (cond
      (some #(= :succeeded %) attacking-order-statuses)
      :dislodged
      (some #(= :pending %) attacking-order-statuses)
      :pending
      (every? #(= :failed %) attacking-order-statuses)
      :not-dislodged)))

(defn-spec convoying-order-statuses [::r/resolution-state ::dt/attack-order]
  (s/map-of ::r/dislodgment-status (s/coll-of ::dt/convoy-order)))
(defn convoying-order-statuses
  [{:keys [convoy-map] :as rs}
   attack-order]
  (let [attempted-convoys (get convoy-map attack-order [])]
    (group-by #(dislodgment-status rs %) attempted-convoys)))

(defn-spec evaluate-voyage [::r/resolution-state ::dt/attack-order]
  ::r/voyage-status)
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

(defn-spec try-resolve-every-voyage [::r/resolution-state] ::r/resolution-state)
(defn try-resolve-every-voyage
  [{:keys [voyage-queue] :as rs}]
  (nth (iterate take-voyage-resolution-step rs)
       (count voyage-queue)))

(defn-spec try-resolve-every-conflict [::r/resolution-state] ::r/resolution-state)
(defn try-resolve-every-conflict
  [{:keys [conflict-queue] :as rs}]
  (nth (iterate take-conflict-resolution-step rs)
       (count conflict-queue)))

(defn-spec get-final-resolution-state [::r/resolution-state] ::r/resolution-state)
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

(defn-spec compute-resolution-results
  [::dt/orders ::dt/dmap]
  ::dt/resolution-results
  #(= (set (-> % :args :arg-1)) (set (-> % :ret (keys)))))
(defn compute-resolution-results
  "A map from each element of `orders` to the set of judgments that apply to it
  (the orders that may interfere with it, whether they successfully interfered,
  and the situation that determined that result)."
  [orders diplomacy-map]
  (let [initial-resolution-state
        (init/get-initial-resolution-state orders diplomacy-map)
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
                 (let [judgments (filter #(not (s/valid? ::r/no-conflict %))
                                         (vals conflicting-orders-map))]
                   [order judgments])))
          (into {}))
     ;; Add failed convoys
     (->> orders
          (filter orders/attack?)
          (filter (fn [attack-order] (= (arrival-status final-resolution-state attack-order) :failed)))
          (map (fn [attack-order] [attack-order #{:no-successful-convoy}]))
          (into {})))))
