(ns diplomacy.orders-test
  (:require [clojure.test :refer [deftest is]]
            [diplomacy.test-expansion :refer [expand-order]]
            [diplomacy.orders :refer :all]))

(def english-army-wal-holds
  {:country :england :unit-type :army  :location :wal :order-type :hold})
(def english-army-lvp-supports-wal-hold
  {:country :england :unit-type :army  :location :lvp :order-type :support
   :assisted-order english-army-wal-holds})
(def french-army-bre-to-lon
  {:country :france  :unit-type :army  :location :bre :order-type :attack
   :destination :lon})
(def french-army-yor-supports-bre-to-lon
  {:country :france  :unit-type :army  :location :yor :order-type :support
   :assisted-order french-army-bre-to-lon})
(def french-fleet-eng-convoys-bre-to-lon
  {:country :france  :unit-type :fleet :location :eng :order-type :convoy
   :assisted-order french-army-bre-to-lon})

(deftest test-expand-order
  (is (expand-order :england :army :wal :hold) english-army-wal-holds)
  (is (expand-order :england :army :lvp :support :england :army :wal :hold)
      english-army-lvp-supports-wal-hold)
  (is (expand-order :france :army :bre :attack :lon) french-army-bre-to-lon)
  (is (expand-order :france :army :yor :support :france :army :bre :attack :lon)
      french-army-yor-supports-bre-to-lon)
  (is (expand-order :france :fleet :eng :convoy :france :army :bre :attack :lon)
      french-fleet-eng-convoys-bre-to-lon))



(deftest test-army?
  (is (army? english-army-wal-holds))
  (is (army? english-army-lvp-supports-wal-hold))
  (is (army? french-army-bre-to-lon))
  (is (army? french-army-yor-supports-bre-to-lon))
  (is (not (army? french-fleet-eng-convoys-bre-to-lon))))

(deftest test-fleet?
  (is (not (fleet? english-army-wal-holds)))
  (is (not (fleet? english-army-lvp-supports-wal-hold)))
  (is (not (fleet? french-army-bre-to-lon)))
  (is (not (fleet? french-army-yor-supports-bre-to-lon)))
  (is (fleet? french-fleet-eng-convoys-bre-to-lon)))

(deftest test-hold?
  (is (hold? english-army-wal-holds))
  (is (not (hold? english-army-lvp-supports-wal-hold)))
  (is (not (hold? french-army-bre-to-lon)))
  (is (not (hold? french-army-yor-supports-bre-to-lon)))
  (is (not (hold? french-fleet-eng-convoys-bre-to-lon))))

(deftest test-attack?
  (is (not (attack? english-army-wal-holds)))
  (is (not (attack? english-army-lvp-supports-wal-hold)))
  (is (attack? french-army-bre-to-lon))
  (is (not (attack? french-army-yor-supports-bre-to-lon)))
  (is (not (attack? french-fleet-eng-convoys-bre-to-lon))))

(deftest test-support?
  (is (not (support? english-army-wal-holds)))
  (is (support? english-army-lvp-supports-wal-hold))
  (is (not (support? french-army-bre-to-lon)))
  (is (support? french-army-yor-supports-bre-to-lon))
  (is (not (support? french-fleet-eng-convoys-bre-to-lon))))

(deftest test-convoy?
  (is (not (convoy? english-army-wal-holds)))
  (is (not (convoy? english-army-lvp-supports-wal-hold)))
  (is (not (convoy? french-army-bre-to-lon)))
  (is (not (convoy? french-army-yor-supports-bre-to-lon)))
  (is (convoy? french-fleet-eng-convoys-bre-to-lon)))

(deftest test-next-intended-location
  (is (= (next-intended-location english-army-wal-holds) :wal))
  (is (= (next-intended-location english-army-lvp-supports-wal-hold) :lvp))
  (is (= (next-intended-location french-army-bre-to-lon) :lon))
  (is (= (next-intended-location french-army-yor-supports-bre-to-lon) :yor))
  (is (= (next-intended-location french-fleet-eng-convoys-bre-to-lon) :eng)))



(deftest test-locations-used-by-order
  (is (set (locations-used-by-order english-army-wal-holds)) #{:wal})
  (is (set (locations-used-by-order english-army-lvp-supports-wal-hold))
      #{:lvp :wal})
  (is (set (locations-used-by-order french-army-bre-to-lon)) #{:bre :lon})
  (is (set (locations-used-by-order french-army-yor-supports-bre-to-lon))
      #{:yor :bre :lon})
  (is (set (locations-used-by-order french-fleet-eng-convoys-bre-to-lon))
      #{:eng :bre :lon}))
