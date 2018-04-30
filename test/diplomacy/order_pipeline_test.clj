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

    ;; Don't use `clojure.data/diff` here because "Maps are subdiffed where keys
    ;; match and values differ". We want to output exactly what orders failed,
    ;; and not do any sort of analysis on how the failing orders differed (the
    ;; user can do that much better themselves).
    (test/is (empty? (map-difference expected-val actual-val))
             (str test-identifier
                  " - validation step SHOULD produce these CORRECT validation results"))
    (test/is (empty? (map-difference actual-val expected-val))
             (str test-identifier
                  " - validation step SHOULD NOT produce these INCORRECT validation results"))

    (test/is (empty? (map-difference expected-res actual-res))
             (str test-identifier
                  " - resolution step SHOULD produce these CORRECT conflict judgments"))
    (test/is (empty? (map-difference actual-res expected-res))
             (str test-identifier
                  " - resolution step SHOULD NOT produce these INCORRECT conflict judgments"))

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

(test/deftest test-rulebook-sample-game
  (doseq [[game-time test]
          diplomacy.rulebook-sample-game/rulebook-sample-game-cases]
    (run-test-case diplomacy.map-data/classic-map
                   test
                   (str (:season game-time) " " (:year game-time)))))

(test/deftest test-rulebook-diagrams
  (doseq [[diagram-number test]
          diplomacy.rulebook-diagrams/rulebook-diagram-cases]
    (run-test-case diplomacy.map-data/classic-map
                   test
                   (str "Rulebook diagram " diagram-number))))
