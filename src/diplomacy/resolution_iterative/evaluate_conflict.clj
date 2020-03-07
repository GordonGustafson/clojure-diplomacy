(ns diplomacy.resolution-iterative.evaluate-conflict
  (:require [diplomacy.resolution-iterative.datatypes :as r]
            [diplomacy.resolution-iterative.evaluate-util :as eval-util]
            [diplomacy.resolution-iterative.evaluate-voyage :as voyage]
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
       (= (voyage/evaluate-voyage rs attack-order) :succeeded)
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
              (case (eval-util/order-status rs beleaguered-garrison)
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
                 :yes (eval-util/order-status rs supporting-order)
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
        conflict-states (eval-util/get-conflict-states rs last-attack)]
    (if (and (orders/attack? last-attack)
             (= (eval-util/arrival-status rs last-attack) :succeeded)
             (= (eval-util/order-status rs last-attack) :pending)
             (some (partial = :failed-to-leave-destination)
                   conflict-states)
             (every?
              (fn [[interferer conflict-state]]
                (or (= conflict-state :failed-to-leave-destination)
                    (eval-util/non-interfering-conflict-state? conflict-state)
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
                                    (eval-util/non-interfering-conflict-state? hypothetical-conflict-state)))
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
  (case (eval-util/order-status rs bouncer)
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
      (case (eval-util/order-status resolution-state potential-dislodger)
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
  (case (eval-util/arrival-status rs attack)
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
           (= (eval-util/arrival-status rs bouncer) :pending))
      []

        ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; :no-conflict

      (and (= rule :attacked-same-destination)
           (= (eval-util/arrival-status rs bouncer) :failed))
      [[attack bouncer [rule :no-conflict]]]

      (and (= rule :swapped-places)
           (= (eval-util/arrival-status rs bouncer) :succeeded)
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

(defn-spec evaluate-support-conflict
  [::r/resolution-state ::dt/support-order ::dt/attack-order
   ::dt/support-conflict-rule]
  ::r/conflict-state-updates)
(defn evaluate-support-conflict
  [{:keys [location-to-order-map dmap convoy-map] :as rs}
   {:keys [assisted-order] :as support}
   attacker rule]
  (let [attack-arrival-status (eval-util/arrival-status rs attacker)]
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
      (case (eval-util/order-status rs attacker)
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


