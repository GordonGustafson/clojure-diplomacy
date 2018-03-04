(ns diplomacy.order-pipeline
  (:require [diplomacy.order-validation :as order-validation]
            [diplomacy.resolution :as resolution]
            [diplomacy.post-resolution :as post-resolution]
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

(defn-spec orders-phase [::dt/dmap ::dt/game-state ::dt/orders]
  ::dt/completed-orders-phase)
(defn orders-phase [dmap
                    {:keys [unit-positions
                            supply-center-ownership
                            game-time] :as game-state-before}
                    orders]
  (let [val-results (order-validation/validation-results dmap orders)
        orders-to-resolve (validation-results-to-orders-to-resolve val-results)
        resolution-results (resolution/compute-resolution-results
                            orders-to-resolve dmap)
        unit-positions-after (post-resolution/unit-positions-after-orders
                              resolution-results unit-positions)
        pending-retreats (post-resolution/get-pending-retreats
                          dmap resolution-results)]
    {:game-state-before-orders game-state-before
     :validation-results val-results
     :resolution-results resolution-results
     :game-state-after-orders {:unit-positions unit-positions-after
                               ;; The game season and supply-center-ownership
                               ;; change during the build phase, and stay the
                               ;; same during the orders and retreat phases.
                               :supply-center-ownership supply-center-ownership
                               :game-time game-time
                               :pending-retreats pending-retreats}}))
