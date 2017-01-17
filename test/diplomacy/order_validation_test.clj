(ns diplomacy.order-validation-test
  (:require [clojure.test :refer [deftest]]
            [diplomacy.datatypes :refer [create-order]]
            [diplomacy.order-validation :refer [validation-failure-reasons]]
            [diplomacy.rulebook-sample-game :refer [rulebook-sample-game-turns]]
            [diplomacy.maps]
            [diplomacy.test-util :as test-util]))

(def validation-failure-reasons-in-classic-map
  (partial validation-failure-reasons diplomacy.maps/classic-map))

(deftest test-attacks-current-location?
  (test-util/run-test-cases validation-failure-reasons-in-classic-map
   {#{:attacks-current-location? :attacks-via-inaccessible-edge?}
    [{:country    :turkey
      :unit-type   :army
      :location    :con
      :order-type  :attack
      :destination :con}
     {:country    :italy
      :unit-type   :fleet
      :location    :rom
      :order-type  :attack
      :destination :rom}]}))

(deftest test-supports-wrong-order-type?
  (test-util/run-test-cases validation-failure-reasons-in-classic-map
   {#{:supports-wrong-order-type?}
    [{:country    :turkey
      :unit-type  :fleet
      :location   :ank
      :order-type :support
      :assisted-order (create-order :turkey :fleet :con :support
                                    :turkey :army :bul :attack :smy)}
     {:country    :turkey
      :unit-type  :fleet
      :location   :ank
      :order-type :support
      :assisted-order (create-order :turkey :fleet :con :convoy
                                    :turkey :army :bul :attack :smy)}]}))

(deftest test-uses-nonexistent-location?
  (test-util/run-test-cases validation-failure-reasons-in-classic-map
   {#{:uses-nonexistent-location?}
    [(create-order :turkey :army :cleveland :hold)]
    #{:uses-nonexistent-location? :attacks-via-inaccessible-edge?}
     [(create-order :italy :army :disneyland :attack :ven)]
    #{:uses-nonexistent-location? :attacks-inaccessible-location?
      :attacks-via-inaccessible-edge?}
    [(create-order :italy :army :ven :attack :disneyland)]
    #{:uses-nonexistent-location? :supports-unsupportable-location?}
    [(create-order :england :fleet :eng :support
                   :england :army :chipotle :hold)
     (create-order :france :fleet :par :support
                   :france :army :arbys :attack :bre)
     (create-order :france :fleet :par :support
                   :france :army :bre :attack :arbys)]}))

(deftest test-attacks-inaccessible-location?
  (test-util/run-test-cases validation-failure-reasons-in-classic-map
   { #{:attacks-inaccessible-location? :attacks-via-inaccessible-edge?}
    [(create-order :italy :fleet :ven :attack :tyr)
     (create-order :italy :army :ven :attack :adr)]}))

(deftest test-attacks-via-inaccessible-edge?
  (test-util/run-test-cases validation-failure-reasons-in-classic-map
   {#{:attacks-via-inaccessible-edge?}
    [(create-order :italy :fleet :ven :attack :lon)
     (create-order :italy :army :spa :attack :syr)
     (create-order :turkey :fleet :ank :attack :smy)]}))

(deftest test-supports-unsupportable-location?
  (test-util/run-test-cases validation-failure-reasons-in-classic-map
   {#{}
    [(create-order :france :army :por :support
                   :france :fleet :wes :attack :spa-sc)
     (create-order :france :army :por :support
                   :france :army :gas :attack :spa)]
    #{:supports-unsupportable-location?}
    [(create-order :france :army :por :support
                   :france :fleet :wes :attack :mid)]}))


(deftest test-rulebook-sample-game-validation
  ;; Use `(apply concat ...)` instead of `(flatten ...)` because `flatten`
  ;; creates one giant order by flattening *all* the layers!
  (let [rulebook-sample-game-orders
        (apply concat (vals rulebook-sample-game-turns))]
    (test-util/run-test-cases validation-failure-reasons-in-classic-map
                              {#{} rulebook-sample-game-orders})))
