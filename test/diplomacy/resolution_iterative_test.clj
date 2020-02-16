(ns diplomacy.resolution-iterative-test
  (:require [diplomacy.resolution-iterative.all :refer :all]
            [diplomacy.resolution-iterative.map-util :refer :all]
            [diplomacy.resolution-iterative.init :refer :all]
            [diplomacy.resolution-iterative.evaluate-util :refer :all]
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

;; Commented out because I'm lazy and don't want to type out an empty resolution
;; state
#_(deftest test-resolution-complete?
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
           :destination-occupied]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                        Resolving Conflicts ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def no-conflict-ex [:failed-to-leave-destination :no-conflict])
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
(def pending-conflict-ex :failed-to-leave-destination)

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

(deftest test-make-convoy-map
  (let [cases {
               [] {}
               [[:austria :army :vie :hold]]
               {}

               [[:austria :army :vie :hold]
                [:austria :army :boh :support :austria :army :gal :hold]]
               {}

               [[:england :army :lon :attack :pic]
                [:england :fleet :eng :convoy :england :army :lon :attack :pic]]
               {[:england :army :lon :attack :pic]
                [[:england :fleet :eng :convoy :england :army :lon :attack :pic]]}

               ;; Require destination matches
               [[:england :army :lon :attack :pic]
                [:england :fleet :eng :convoy :england :army :lon :attack :bre]]
               {}

               ;; Require nationalities match.
               [[:england :army :lon :attack :pic]
                [:england :fleet :eng :convoy :france :army :lon :attack :pic]]
               {}

               [[:england :army :lon :attack :spa]
                [:england :fleet :eng :convoy :england :army :lon :attack :spa]
                [:france :fleet :mid :convoy :england :army :lon :attack :spa]]
               {[:england :army :lon :attack :spa]
                [[:england :fleet :eng :convoy :england :army :lon :attack :spa]
                 [:france :fleet :mid :convoy :england :army :lon :attack :spa]]}

               [[:italy :army :spa :attack :nap]
                [:italy :fleet :gol :convoy :italy :army :spa :attack :nap]
                [:italy :fleet :wes :convoy :italy :army :spa :attack :nap]
                [:italy :fleet :tyn :convoy :italy :army :spa :attack :nap]

                [:england :army :edi :attack :nwy]
                [:england :fleet :bla :convoy :england :army :edi :attack :nwy]]
               {
                [:italy :army :spa :attack :nap]
                [[:italy :fleet :gol :convoy :italy :army :spa :attack :nap]
                 [:italy :fleet :wes :convoy :italy :army :spa :attack :nap]
                 [:italy :fleet :tyn :convoy :italy :army :spa :attack :nap]]

                [:england :army :edi :attack :nwy]
                [[:england :fleet :bla :convoy :england :army :edi :attack :nwy]]}
               }
        ]
    (doall
     (map (fn [[input expected-output]]
            (is (= (->> input
                        (map (partial apply te/expand-order))
                        (make-location-to-order-map)
                        (make-convoy-map))
                   (->> expected-output
                        (map (fn [[k vs]]
                               [(apply te/expand-order k)
                                (map (partial apply te/expand-order) vs)]))
                        (into {})))))
          cases))))

(def italy-ven-tyr (te/expand-order :italy :army :ven :attack :tyr))

(def italy-tyr-hold (te/expand-order :italy :army :tyr :hold))
(def russia-tyr-hold (te/expand-order :russia :army :tyr :hold))

(def italy-tyr-ven (te/expand-order :italy :army :tyr :attack :ven))
(def austria-tyr-ven (te/expand-order :austria :army :tyr :attack :ven))

(def italy-tyr-boh (te/expand-order :italy :army :tyr :attack :boh))
(def turkey-tyr-boh (te/expand-order :turkey :army :tyr :attack :boh))

(def italy-mun-tyr (te/expand-order :italy :army :mun :attack :tyr))
(def germany-mun-tyr (te/expand-order :germany :army :mun :attack :tyr))

(deftest test-forbid-self-dislodgment
  (let [self-dislodgment-cases [
                                [italy-ven-tyr italy-tyr-hold :destination-occupied]
                                [italy-ven-tyr italy-tyr-ven :swapped-places]
                                [italy-ven-tyr italy-tyr-boh :failed-to-leave-destination]
                                ]
        no-self-dislodgment-cases [
                                   [italy-ven-tyr russia-tyr-hold :destination-occupied]
                                   [italy-ven-tyr austria-tyr-ven :swapped-places]
                                   [italy-ven-tyr turkey-tyr-boh :failed-to-leave-destination]
                                   [italy-ven-tyr italy-mun-tyr :attacked-same-destination]
                                   [italy-ven-tyr germany-mun-tyr :attacked-same-destination]
                                   ]]
    (doall
     (map (fn [[order interferer attack-rule]]
            (let [judgment (j/create-attack-judgment :interferer interferer
                                                     :attack-rule attack-rule
                                                     :interfered? false)
                  conflict-state-update [order interferer judgment]]
              (is (= (forbid-self-dislodgment conflict-state-update)
                     (update conflict-state-update 2
                             #(assoc %
                                     :interfered? true
                                     :would-dislodge-own-unit? true))))))
          self-dislodgment-cases))
    (doall
     (map (fn [[order interferer attack-rule]]
            (let [judgment (j/create-attack-judgment :interferer interferer
                                                     :attack-rule attack-rule
                                                     :interfered? false)
                  conflict-state-update [order interferer judgment]]
              (is (= (forbid-self-dislodgment conflict-state-update)
                     conflict-state-update))))
          no-self-dislodgment-cases))))

