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

(defn-spec adjudicate-orders
  [::dt/dmap ::dt/unit-positions ::dt/orders]
  ::adjudication)
(defn adjudicate-orders [dmap unit-positions orders]
  ;; TODO: use unit-positions to make sure players can't order units that don't
  ;; exist.
  (let [val-results (diplomacy.order-validation/validation-results dmap orders)
        orders-to-resolve (validation-results-to-orders-to-resolve val-results)
        conflict-judgments (diplomacy.resolution/conflict-judgments
                            orders-to-resolve)]
    {:unit-positions-before unit-positions
     :validation-results val-results
     :conflict-judgments conflict-judgments
     ;; TODO: compute unit-positions-after from conflict-judgments
     :unit-positions-after []}))
