(ns diplomacy.resolution-iterative.compute-resolution
  (:require [diplomacy.resolution-iterative.datatypes :as r]
            [diplomacy.resolution-iterative.evaluate-conflict :as conflict]
            [diplomacy.resolution-iterative.init :as init]
            [diplomacy.resolution-iterative.evaluate-util :as eval-util]
            [diplomacy.resolution-iterative.evaluate-voyage :as voyage]
            [diplomacy.orders :as orders]
            [diplomacy.datatypes :as dt]
            [diplomacy.util :refer [defn-spec]]
            [clojure.spec.alpha :as s]))

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
  (nth (iterate voyage/take-voyage-resolution-step rs)
       (count voyage-queue)))

(defn-spec try-resolve-every-conflict [::r/resolution-state] ::r/resolution-state)
(defn try-resolve-every-conflict
  [{:keys [conflict-queue] :as rs}]
  (nth (iterate conflict/take-conflict-resolution-step rs)
       (count conflict-queue)))

(defn-spec all-orders-resolved? [::r/resolution-state] boolean?)
(defn all-orders-resolved? [{:keys [conflict-map voyage-map]}]
  (let [all-conflict-states
        (for [[order conflicting-orders-map] conflict-map
              [conflicting-order conflict-state] conflicting-orders-map]
          conflict-state)]
    (and (every? (partial s/valid? ::r/resolved-conflict-state)
                 all-conflict-states)
         (every? #{:succeeded :failed} (vals voyage-map)))))

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
    (->> (iterate conflict/take-conflict-resolution-step
                  stable-rs-with-failed-voyages)
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
          (filter (fn [attack-order] (= (eval-util/arrival-status final-resolution-state attack-order) :failed)))
          (map (fn [attack-order] [attack-order #{:no-successful-convoy}]))
          (into {})))))
