(ns diplomacy.order-validation-test
  (:require [clojure.test :refer [deftest is]]
            [diplomacy.datatypes :as dt :refer [create-order]]
            [diplomacy.order-validation :refer [validation-failure-reasons]]
            [diplomacy.rulebook-diagrams]
            [diplomacy.rulebook-sample-game]
            [diplomacy.maps]
            [diplomacy.util :refer [defn-spec fn-spec]]
            [clojure.spec :as s]))

(defn-spec run-test-cases
  [(fn-spec [::dt/order]
            (s/coll-of ::dt/validation-failure-reason))
   (s/map-of (s/coll-of ::dt/validation-failure-reason)
             (s/coll-of ::dt/order))]
  any?)
(defn run-test-cases [func test-case-dict]
  "`test-case-dict` is a mapping of the expected outputs of `func` to a sequence
  of inputs to `func` that should all produce that same expected output."
  (doseq [[expected-failure-reasons orders] test-case-dict]
    (doseq [order orders]
      (is (= expected-failure-reasons (func order))))))

(def validation-failure-reasons-in-classic-map
  (partial validation-failure-reasons diplomacy.maps/classic-map))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                          Custom Test Cases ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest test-attacks-current-location?
  (run-test-cases validation-failure-reasons-in-classic-map
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
  (run-test-cases validation-failure-reasons-in-classic-map
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
  (run-test-cases validation-failure-reasons-in-classic-map
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
  (run-test-cases validation-failure-reasons-in-classic-map
   { #{:attacks-inaccessible-location? :attacks-via-inaccessible-edge?}
    [(create-order :italy :fleet :ven :attack :tyr)
     (create-order :italy :army :ven :attack :adr)]}))

(deftest test-attacks-via-inaccessible-edge?
  (run-test-cases validation-failure-reasons-in-classic-map
   {#{:attacks-via-inaccessible-edge?}
    [(create-order :italy :fleet :ven :attack :lon)
     (create-order :italy :army :spa :attack :syr)
     (create-order :turkey :fleet :ank :attack :smy)]}))

(deftest test-supports-unsupportable-location?
  (run-test-cases validation-failure-reasons-in-classic-map
   {#{}
    [(create-order :france :army :por :support
                   :france :fleet :wes :attack :spa-sc)
     (create-order :france :army :por :support
                   :france :army :gas :attack :spa)]
    #{:supports-unsupportable-location?}
    [(create-order :france :army :por :support
                   :france :fleet :wes :attack :mid)]}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                Rulebook Test Cases (orders that are valid) ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO: document what these mean
(defn orders-map-to-validation-test-cases
  [orders-map]
  ;; Use `(apply concat ...)` instead of `(flatten ...)` because `flatten`
  ;; creates one giant order by flattening *all* the layers!
  {#{} (->> orders-map
            (vals)      ; the keys are the turn times or diagram numbers
            (map keys)  ; the values are the failure-reason for the order
            (apply concat))})

(deftest test-rulebook-sample-game-validation
  (run-test-cases validation-failure-reasons-in-classic-map
                  (orders-map-to-validation-test-cases
                   diplomacy.rulebook-diagrams/rulebook-diagram-judgments)))

(deftest test-rulebook-diagrams-validation
  (run-test-cases validation-failure-reasons-in-classic-map
                  (orders-map-to-validation-test-cases
                   diplomacy.rulebook-sample-game/rulebook-sample-game-judgments
                   )))
