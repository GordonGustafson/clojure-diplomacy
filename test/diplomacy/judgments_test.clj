(ns diplomacy.judgments-test
  (:require [diplomacy.judgments :refer :all]
            [clojure.test :refer [deftest is]]))

(def french-army-bre-to-lon
  {:country :france  :unit-type :army  :location :bre :order-type :attack
   :destination :lon})

(deftest test-create-attack-judgment
  (is (create-attack-judgment
       :interfered? true
       :interferer french-army-bre-to-lon
       :attack-rule :attacked-same-destination))
  {:interfered? true
   :interferer french-army-bre-to-lon
   :conflict-situation {:attack-conflict-rule :attacked-same-destination
                        :beleaguered-garrison-changing-outcome nil}
   :would-dislodge-own-unit? false})
