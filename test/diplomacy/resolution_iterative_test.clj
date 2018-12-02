(ns diplomacy.resolution-iterative-test
  (:require [diplomacy.resolution-iterative :refer :all]
            [diplomacy.map-data :refer [classic-map]]
            [diplomacy.test-expansion :as te]
            [clojure.test :refer [deftest is]]))

;;; This file is for unit tests of individual functions,
;;; `compute-resolution-results` is tested in `diplomacy.order-pipeline-test`

(def austria-attack-tyr (te/expand-order :austria :army :vie :attack :tyr))
(def italy-attack-tyr (te/expand-order :italy :army :ven :attack :tyr))
(def germany-attack-tyr (te/expand-order :germany :army :mun :attack :tyr))

(def bul-ec-hold (te/expand-order :italy :fleet :bul-ec :hold))
(def spa-attack (te/expand-order :france :army :spa :attack :por))
(def stp-sc-attack (te/expand-order :russia :fleet :stp-sc :attack :lvn))

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

(deftest test-get-at-colocated-location
  (let [orders-map {:bul-ec bul-ec-hold
                    :spa spa-attack
                    :stp-sc stp-sc-attack
                    :vie austria-attack-tyr}
        cases [[:bul bul-ec-hold
                :bul-ec bul-ec-hold
                :bul-sc bul-ec-hold
                :spa spa-attack
                :spa-nc spa-attack
                :spa-sc spa-attack
                :stp stp-sc-attack
                :stp-nc stp-sc-attack
                :stp-sc stp-sc-attack
                :vie austria-attack-tyr
                :wes nil]]]
    (doall (map (fn [[loc order]]
                  (is (= (get-at-colocated-location classic-map
                                                    orders-map
                                                    loc)
                         order)))
                cases))))

(deftest test-get-all-potential-conflicts
  (is (= (get-all-potential-conflicts
          classic-map
          (make-location-to-order-map
           [(te/expand-order :austria :army :ven :hold)
            (te/expand-order :italy :fleet :rom :hold)
            (te/expand-order :italy :army :apu :attack :ven)])
          (te/expand-order :italy :army :apu :attack :ven))
         [[(te/expand-order :italy :army :apu :attack :ven)
           (te/expand-order :austria :army :ven :hold)
           :occupying-destination]])))
