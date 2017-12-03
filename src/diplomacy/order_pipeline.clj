(ns diplomacy.order-pipeline
  (:require [diplomacy.order-validation]
            [diplomacy.resolution]
            [diplomacy.datatypes :as dt]
            [diplomacy.util :refer [defn-spec]]
            [clojure.spec.alpha :as s]))

(defn-spec validation-results-to-orders-to-resolve
  [::dt/validation-results] ::dt/orders)
(defn validation-results-to-orders-to-resolve
  [validation-results]
  (map (fn [[order validation-result]]
         (if (= validation-result :valid)
           order
           (:order-used validation-result)))
       validation-results))

;; TODO(unit-positions):
;; 1. pass the current positions of all units to make sure players can't order
;;    units that don't exist.
;; 2. add :unit-positions-before and :unit-positions-after keys to the return
;   ; value
(defn-spec adjudicate-orders [::dt/dmap ::dt/orders] ::dt/adjudication)
(defn adjudicate-orders [dmap orders]
  (let [val-results (diplomacy.order-validation/validation-results dmap orders)
        orders-to-resolve (validation-results-to-orders-to-resolve val-results)
        conflict-judgments (diplomacy.resolution/compute-conflict-judgments
                            orders-to-resolve)]
    {:validation-results val-results
     :conflict-judgments conflict-judgments}))
