(ns diplomacy.resolution-iterative-test
  (:require [diplomacy.resolution-iterative :refer :all]
            [diplomacy.map-data :refer [classic-map]]
            [diplomacy.judgments :as j]
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
        {austria-attack-tyr {italy-attack-tyr :attacking-same-destination}
         italy-attack-tyr {austria-attack-tyr :attacking-same-destination}})))
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                        Resolving Conflicts ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def no-conflict-ex [:leaving-destination :no-conflict])
(def interfered-judgment-ex
  (j/create-attack-judgment
   :interferer (te/expand-order :italy :army :apu :attack :ven)
   :attack-rule :destination-occupied
   :interfered? true))
(def not-interfered-judgment-ex
  (j/create-attack-judgment
   :interferer (te/expand-order :italy :army :apu :attack :ven)
   :attack-rule :destination-occupied
   :interfered? false))
(def pending-conflict-ex :leaving-destination)

(deftest test-interfering-state?
  (is (= (interfering-state? no-conflict-ex) false))
  (is (= (interfering-state? interfered-judgment-ex) true))
  (is (= (interfering-state? not-interfered-judgment-ex) false)))

(deftest test-pending-conflict-state?
  (is (= (pending-conflict-state? no-conflict-ex) false))
  (is (= (pending-conflict-state? interfered-judgment-ex) false))
  (is (= (pending-conflict-state? not-interfered-judgment-ex) false))
  (is (= (pending-conflict-state? pending-conflict-ex) true)))

(deftest test-conflict-states-to-order-status
  (is (= (conflict-states-to-order-status []) :succeeded))
  (is (= (conflict-states-to-order-status [no-conflict-ex]) :succeeded))
  (is (= (conflict-states-to-order-status [interfered-judgment-ex]) :failed))
  (is (= (conflict-states-to-order-status [not-interfered-judgment-ex]) :succeeded))
  (is (= (conflict-states-to-order-status [pending-conflict-ex]) :pending))
  (is (= (conflict-states-to-order-status [no-conflict-ex no-conflict-ex]) :succeeded))
  (is (= (conflict-states-to-order-status [no-conflict-ex interfered-judgment-ex]) :failed))
  (is (= (conflict-states-to-order-status [no-conflict-ex not-interfered-judgment-ex]) :succeeded))
  (is (= (conflict-states-to-order-status [no-conflict-ex pending-conflict-ex]) :pending))
  (is (= (conflict-states-to-order-status [interfered-judgment-ex interfered-judgment-ex]) :failed))
  (is (= (conflict-states-to-order-status [interfered-judgment-ex not-interfered-judgment-ex]) :failed))
  (is (= (conflict-states-to-order-status [interfered-judgment-ex pending-conflict-ex]) :failed))
  (is (= (conflict-states-to-order-status [not-interfered-judgment-ex not-interfered-judgment-ex]) :succeeded))
  (is (= (conflict-states-to-order-status [not-interfered-judgment-ex pending-conflict-ex]) :pending))
  (is (= (conflict-states-to-order-status [pending-conflict-ex pending-conflict-ex]) :pending)))

(deftest test-supported-order-matches?
  (let [true-cases  [[[:austria :army :vie :attack :tyr] [:austria :army :vie :attack :tyr]]
                     [[:austria :army :vie :hold] [:austria :army :vie :hold]]
                     [[:austria :army :vie :hold] [:austria :army :vie :support :austria :army :bud :attack :gal]]
                     [[:austria :fleet :adr :hold] [:austria :fleet :adr :convoy :austria :army :tri :attack :apu]]]
        false-cases [[[:austria :army :vie :attack :tyr] [:austria :army :vie :attack :mun]]
                     [[:austria :army :vie :attack :tyr] [:austria :army :boh :attack :tyr]]
                     [[:austria :army :vie :attack :tyr] [:austria :army :boh :hold]]
                     [[:austria :army :vie :hold] [:austria :army :vie :attack :mun]]
                     [[:austria :army :vie :hold] [:austria :army :boh :support :austria :army :gal :hold]]]]
    (doall
     (map (fn [case]
            (is (= true
                   (apply supported-order-matches?
                          (map (partial apply te/expand-order)
                               case)))))
          true-cases))
    (doall
     (map (fn [case]
            (is (= false
                   (apply supported-order-matches?
                          (map (partial apply te/expand-order)
                               case)))))
          false-cases))))

(deftest test-make-support-map
  (let [cases {
               [] {}
               [[:austria :army :vie :hold]]
               {}

               [[:austria :army :vie :hold]
                [:austria :army :boh :support :austria :army :gal :hold]]
               {}

               [[:austria :army :vie :hold]
                [:austria :army :boh :support :austria :army :vie :hold]]
               {[:austria :army :vie :hold]
                [[:austria :army :boh :support :austria :army :vie :hold]]}

               [[:austria :army :vie :hold]
                [:austria :army :boh :support :austria :army :vie :hold]
                [:austria :army :gal :support :austria :army :vie :hold]]
               {[:austria :army :vie :hold]
                [[:austria :army :boh :support :austria :army :vie :hold]
                 [:austria :army :gal :support :austria :army :vie :hold]]}

               [[:austria :army :vie :support :austria :army :boh :hold]
                [:austria :army :boh :support :austria :army :vie :hold]]
               {[:austria :army :vie :support :austria :army :boh :hold]
                [[:austria :army :boh :support :austria :army :vie :hold]]
                [:austria :army :boh :support :austria :army :vie :hold]
                [[:austria :army :vie :support :austria :army :boh :hold]]}
               }
        ]
    (doall
     (map (fn [[input expected-output]]
            (is (= (->> input
                        (map (partial apply te/expand-order))
                        (make-location-to-order-map)
                        (make-support-map))
                   (->> expected-output
                        (map (fn [[k vs]]
                               [(apply te/expand-order k)
                                (map (partial apply te/expand-order) vs)]))
                        (into {})))))
          cases))))

(deftest surely-bounced-by-strength?-helper-test
  (let [cases [
               [[] [] true]
               [[] [:succeeded] true]
               [[:succeeded] [] false]
               [[:succeeded] [:succeeded] true]
               [[:failed] [] true]
               [[:succeeded] [:failed] false]
               [[:succeeded] [:failed :failed] false]
               [[:succeeded] [:succeeded :succeeded] true]
               [[:succeeded :succeeded] [:succeeded :succeeded] true]
               [[:succeeded :succeeded] [:succeeded] false]

               [[:pending] [] false]
               [[:pending] [:pending] false]
               [[:pending] [:succeeded] true]
               [[:pending :pending] [] false]
               [[:pending :pending] [:succeeded] false]
               [[:pending :succeeded] [:succeeded :pending :pending] false]
               [[:pending :succeeded] [:succeeded :succeeded] true]
               [[:pending :succeeded :failed :failed] [:succeeded :succeeded] true]
               [[:succeeded :succeeded :pending :failed :failed] [:succeeded :succeeded :failed] false]
               ]]
    (doall
     (map (fn [[attack-support-statuses
                bouncer-support-statuses
                expected-output]]
            (is (= (surely-bounced-by-strength?-helper attack-support-statuses
                                                       bouncer-support-statuses)
                   expected-output)
                (str attack-support-statuses "\n" bouncer-support-statuses)))
          cases))))
