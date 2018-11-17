(ns diplomacy.resolution-iterative-test
  (:require [diplomacy.resolution-iterative :refer :all]
            [diplomacy.test-expansion :as te]
            [clojure.test :refer [deftest is]]))

;; This file is for unit tests of individual functions,
;; `compute-resolution-results` is tested in `diplomacy.order-pipeline-test`

(def austria-attack-tyr (te/expand-order :austria :army :vie :attack :tyr))
(def italy-attack-tyr (te/expand-order :italy :army :ven :attack :tyr))
(def germany-attack-tyr (te/expand-order :germany :army :mun :attack :tyr))

(deftest test-resolution-complete?
  (is (false?
       (resolution-complete?
        {austria-attack-tyr {italy-attack-tyr :attacked-same-destination}
         italy-attack-tyr {austria-attack-tyr :attacked-same-destination}})))
  (is (true?
       (resolution-complete?
        {austria-attack-tyr {italy-attack-tyr
                             {:interferer italy-attack-tyr
                              :conflict-situation {:attack-conflict-rule :attacked-same-destination
                                                   :beleaguered-garrison-changing-outcome nil}
                              :interfered? true}}
         italy-attack-tyr {austria-attack-tyr
                           {:interferer austria-attack-tyr
                            :conflict-situation {:attack-conflict-rule :attacked-same-destination
                                                 :beleaguered-garrison-changing-outcome nil}
                            :interfered? true}}}))))


(deftest test-apply-conflict-state-updates
  (is (= (apply-conflict-state-updates
          {}
          [[austria-attack-tyr
            italy-attack-tyr
            {:interferer
             italy-attack-tyr,
             :conflict-situation
             {:attack-conflict-rule :attacked-same-destination,
              :beleaguered-garrison-changing-outcome nil},
             :interfered? true}]
           [austria-attack-tyr
            germany-attack-tyr
            {:interferer
             germany-attack-tyr,
             :conflict-situation
             {:attack-conflict-rule :attacked-same-destination,
              :beleaguered-garrison-changing-outcome nil},
             :interfered? true}]])
         ;; expected result:
         {austria-attack-tyr
          {italy-attack-tyr
           {:interferer
            italy-attack-tyr,
            :conflict-situation
            {:attack-conflict-rule :attacked-same-destination,
             :beleaguered-garrison-changing-outcome nil},
            :interfered? true}
           germany-attack-tyr
           {:interferer
            germany-attack-tyr,
            :conflict-situation
            {:attack-conflict-rule :attacked-same-destination,
             :beleaguered-garrison-changing-outcome nil},
            :interfered? true}}})))
