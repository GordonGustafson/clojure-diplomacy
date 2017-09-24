(ns diplomacy.order-pipeline-test
  (:require [clojure.test :as test]
            [diplomacy.order-pipeline]
            [diplomacy.datatypes :as dt]
            [diplomacy.map-data]
            [diplomacy.DATC-cases]
            [diplomacy.util :refer [defn-spec map-difference]]))

(defn-spec run-test-case [::dt/dmap ::dt/adjudication string?] any?)
(defn run-test-case [dmap expected-adjudication test-identifier]
  (let [actual-adjudication
        (diplomacy.order-pipeline/adjudicate-orders dmap
                                                    (-> expected-adjudication
                                                        (:validation-results)
                                                        (keys)))
        expected-val (:validation-results expected-adjudication)
        actual-val   (:validation-results actual-adjudication)
        expected-jud (:conflict-judgments expected-adjudication)
        actual-jud   (:conflict-judgments actual-adjudication)]
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

    (test/is (empty? (map-difference expected-jud actual-jud))
             (str test-identifier
                  " - resolution step SHOULD produce these CORRECT conflict judgments"))
    (test/is (empty? (map-difference actual-jud expected-jud))
             (str test-identifier
                  " - resolution step SHOULD NOT produce these INCORRECT conflict judgments"))))

(test/deftest test-DATC
  (doseq [[test-name expected-adjudication] diplomacy.DATC-cases/DATC-cases]
    (run-test-case diplomacy.map-data/classic-map
                   expected-adjudication
                   test-name)))
