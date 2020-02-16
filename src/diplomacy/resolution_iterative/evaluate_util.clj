(ns diplomacy.resolution-iterative.evaluate-util
  (:require [diplomacy.resolution-iterative.datatypes :as r]
            [diplomacy.orders :as orders]
            [diplomacy.datatypes :as dt]
            [diplomacy.util :refer [defn-spec]]
            [clojure.spec.alpha :as s]))

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
