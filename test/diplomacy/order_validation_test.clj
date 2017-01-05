(ns diplomacy.order-validation-test
  (:require [clojure.test :refer [deftest is]]
            [diplomacy.datatypes :refer [create-order] :as dt]
            [diplomacy.order-validation :refer :all]
            [diplomacy.rulebook-sample-game :refer [rulebook-sample-game-turns]]
            [diplomacy.maps]
            [diplomacy.util :refer [fn-spec]]
            [clojure.spec :as s]))

;; All tests use classic-map for now
(fn-spec run-test-case [::dt/failure-reasons ::dt/order] any?)
(defn run-test-case [expected-failure-reasons order]
  (is (= expected-failure-reasons
         (validation-failure-reasons diplomacy.maps/classic-map order))))

(fn-spec run-test-case
  [(s/map-of ::dt/failure-reasons (s/coll-of ::dt/order))] any?)
(defn run-test-cases [test-case-dict]
  (doseq [[expected-failure-reasons orders] test-case-dict]
    (doseq [order orders]
      (run-test-case expected-failure-reasons order))))


(deftest test-attacks-current-location?
  (run-test-cases
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
  (run-test-cases
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
  (run-test-cases
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
  (run-test-cases
   { #{:attacks-inaccessible-location? :attacks-via-inaccessible-edge?}
    [(create-order :italy :fleet :ven :attack :tyr)
     (create-order :italy :army :ven :attack :adr)]}))

(deftest test-attacks-via-inaccessible-edge?
  (run-test-cases
   {#{:attacks-via-inaccessible-edge?}
    [(create-order :italy :fleet :ven :attack :lon)
     (create-order :italy :army :spa :attack :syr)
     (create-order :turkey :fleet :ank :attack :smy)]}))

(deftest test-supports-unsupportable-location?
  (run-test-cases
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
    (run-test-cases {#{} rulebook-sample-game-orders})))





;; (def- remove-invalid-orders-fall-1901
;;   (partial remove-invalid-orders classic-map (:initial-game-state classic-map)))

;; (deftest test-supporting-support-is-invalid
;;   (let [italian-army-ven-hold (create-order :italy :army :ven :hold)
;;         italian-army-rom-support-ven-hold
;;         (create-order :italy :army :rom :support italian-army-ven-hold)]
;;     (is (remove-invalid-orders-fall-1901
;;          #{italian-army-ven-hold
;;            italian-army-rom-support-ven-hold
;;            ;; It's a good thing this is invalid because it's very hard to read:
;;            (create-order :italy :fleet :nap :support
;;                          italian-army-rom-support-ven-hold)}
;;          #{italian-army-ven-hold
;;            italian-army-rom-support-ven-hold}))
;;     (is (remove-invalid-orders-fall-1901
;;          #{italian-army-ven-hold
;;            italian-army-rom-support-ven-hold
;;            (create-order :italy :fleet :nap :support
;;                          (create-order )}
;;          #{italian-army-ven-hold
;;            italian-army-rom-support-ven-hold}))
