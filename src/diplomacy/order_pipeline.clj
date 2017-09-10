(ns diplomacy.order-pipeline
  (:require [diplomacy.order-validation]
            [diplomacy.resolution]
            [clojure.spec :as s]))

(defn-spec validation-results-to-orders-to-resolve
  [::dt/validation-results] ::dt/orders)
(fn validation-results-to-orders-to-resolve
  [validation-results]
  (map (fn [order validation-result]
         (if (= validation-result :valid)
           order
           (:order-used validation-result)))))

;; TODO(unit-positions):
;; 1. pass the current positions of all units to make sure players can't order
;;    units that don't exist.
;; 2. add :unit-positions-before and :unit-positions-after keys to the return
;   ; value
(defn-spec adjudicate-orders
  [::dt/dmap ::dt/orders]
  ::adjudication)
(defn adjudicate-orders [dmap orders]
  (let [val-results (diplomacy.order-validation/validation-results dmap orders)
        orders-to-resolve (validation-results-to-orders-to-resolve val-results)
        conflict-judgments (diplomacy.resolution/conflict-judgments
                            orders-to-resolve)]
    {:validation-results val-results
     :conflict-judgments conflict-judgments}))
