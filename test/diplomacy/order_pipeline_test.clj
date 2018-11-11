(ns diplomacy.order-pipeline-test
  (:require [clojure.test :as test]
            [clojure.set :as set]
            [diplomacy.order-pipeline]
            [diplomacy.test-expansion :as te]
            [diplomacy.datatypes :as dt]
            [diplomacy.map-data]
            [diplomacy.DATC-cases]
            [diplomacy.rulebook-sample-game]
            [diplomacy.rulebook-diagrams]
            [diplomacy.util :refer [defn-spec map-difference]]))

(defn-spec run-test-case [::dt/dmap ::te/orders-phase-test string?] any?)
(defn run-test-case [dmap orders-phase-test test-identifier]
  (let [game-state-before
        {:unit-positions (:unit-positions-before orders-phase-test)
         :supply-center-ownership (:supply-center-ownership-before
                                   orders-phase-test)
         :game-time (:game-time-before orders-phase-test)}
        completed-orders-phase
        (diplomacy.order-pipeline/orders-phase dmap
                                               game-state-before
                                               (-> orders-phase-test
                                                   (:validation-results)
                                                   (keys)))
        expected-val (:validation-results orders-phase-test)
        actual-val   (:validation-results completed-orders-phase)
        expected-res (:resolution-results orders-phase-test)
        actual-res   (:resolution-results completed-orders-phase)]
    (test/is (= (:supply-center-ownership-after orders-phase-test)
                (get-in completed-orders-phase
                        [:game-state-after-orders :supply-center-ownership])))
    (test/is (= (:game-time-after orders-phase-test)
                (get-in completed-orders-phase
                        [:game-state-after-orders :game-time])))

    ;; Check that the correct orders are being tested. If this fails it's most
    ;; likely a severe problem with the testing setup, so we use `assert`
    ;; instead of `test/is`.
    (assert (= (set (keys expected-res))
               (set (keys actual-res)))
            (str "Assertion failure in " test-identifier "\n"
                 expected-res actual-res))

    (let [orders (keys expected-res)]
      (doall (map (fn [order]
                    (let [failure-message (str test-identifier
                                               " - "
                                               (te/order-to-abbr order))]
                      (test/is (=
                                (get expected-val order)
                                (get actual-val order))
                               (str failure-message " - validation"))
                      (test/is (=
                                (get expected-res order)
                                (set (map te/remove-unchecked-parts-of-actual-judgment
                                          (get actual-res order))))
                               (str failure-message " - resolution"))))
                  orders)))

    (when (contains? orders-phase-test :unit-positions-after)
      (test/is (= (:unit-positions-after orders-phase-test)
                  (get-in completed-orders-phase
                          [:game-state-after-orders :unit-positions]))))

    (when (contains? orders-phase-test :pending-retreats)
      (let [expected-retreats (set (:pending-retreats orders-phase-test))
            actual-retreats   (set (get-in completed-orders-phase
                                           [:game-state-after-orders
                                            :pending-retreats]))]
        (test/is (empty? (set/difference expected-retreats actual-retreats))
                 (str test-identifier
                      " - post-resolution step SHOULD produce these CORRECT retreats"))
        (test/is (empty? (set/difference actual-retreats expected-retreats))
                 (str test-identifier
                      " - post-resolution step SHOULD NOT produce these INCORRECT retreats"))))))

(test/deftest test-DATC
  (doseq [[short-name test] diplomacy.DATC-cases/finished-DATC-cases]
    (run-test-case diplomacy.map-data/classic-map test (:long-name test))))

#_(test/deftest test-rulebook-sample-game
  (doseq [[game-time test]
          diplomacy.rulebook-sample-game/rulebook-sample-game-cases]
    (run-test-case diplomacy.map-data/classic-map
                   test
                   (str (:season game-time) " " (:year game-time)))))

#_(test/deftest test-rulebook-diagrams
  (doseq [[diagram-number test]
          diplomacy.rulebook-diagrams/rulebook-diagram-cases]
    (run-test-case diplomacy.map-data/classic-map
                   test
                   (str "Rulebook diagram " diagram-number))))
