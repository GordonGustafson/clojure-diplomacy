(ns diplomacy.rulebook-sample-game
  (:require [diplomacy.test-expansion :as test-expansion]))

;; this contains a good amount of supports, but only one convoy, and almost no
;; use of coasts. Be sure to add your own test cases to cover those!
(def ^:private rulebook-sample-game-judgments-raw
  {
   {:year 1901 :season :spring}
   {[:austria :army :vie :attack :tri] #{}
    [:austria :army :bud :attack :gal] #{[true [:russia :army :war :attack :gal] :attacked-same-destination]}
    [:austria :fleet :tri :attack :alb] #{}
    [:england :army :lvp :attack :yor] #{}
    [:england :fleet :lon :attack :nth] #{}
    [:england :fleet :edi :attack :nrg] #{}
    [:france :army :par :attack :bur] #{}
    [:france :army :mar :attack :spa] #{}
    [:france :fleet :bre :attack :pic] #{}
    [:germany :army :ber :attack :kie] #{}
    [:germany :army :mun :attack :ruh] #{}
    [:germany :fleet :kie :attack :den] #{}
    [:italy :army :ven :attack :pie] #{}
    [:italy :army :rom :attack :ven] #{}
    [:italy :fleet :nap :attack :ion] #{}
    [:russia :army :mos :attack :ukr] #{}
    [:russia :army :war :attack :gal] #{[true [:austria :army :bud :attack :gal] :attacked-same-destination]}
    [:russia :fleet :stp-sc :attack :bot] #{}
    [:russia :fleet :sev :attack :bla] #{[true [:turkey :fleet :ank :attack :bla] :attacked-same-destination]}
    [:turkey :army :con :attack :bul] #{}
    [:turkey :army :smy :attack :con] #{}
    [:turkey :fleet :ank :attack :bla] #{[true [:russia :fleet :sev :attack :bla] :attacked-same-destination]}}
   {:year 1901 :season :fall}
   {[:austria :army :tri :hold] #{}
    [:austria :army :bud :attack :ser] #{[true [:turkey :army :bul :attack :ser] :attacked-same-destination]}
    [:austria :fleet :alb :attack :gre] #{}
    ;; TODO(convoy): uncomment this
    ;; [:england :army :yor :attack :nwy]
    ;; [:england :fleet :nth :convoy :england :army :yor :attack :nwy]
    [:england :fleet :nrg :attack :bar] #{}
    [:france :army :bur :attack :mar] #{[true [:italy :army :pie :attack :mar] :attacked-same-destination]}
    [:france :army :spa :attack :por] #{}
    [:france :fleet :pic :attack :bel] #{[true [:germany :army :ruh :attack :bel] :attacked-same-destination]}
    [:germany :army :kie :attack :hol] #{}
    [:germany :army :ruh :attack :bel] #{[true [:france :fleet :pic :attack :bel] :attacked-same-destination]}
    [:germany :fleet :den :hold] #{}
    [:italy :army :ven :hold] #{}
    [:italy :army :pie :attack :mar] #{[true [:france :army :bur :attack :mar] :attacked-same-destination]}
    [:italy :fleet :ion :attack :tun] #{}
    [:russia :army :ukr :support :russia :fleet :sev :attack :rum] #{}
    [:russia :army :war :attack :gal] #{}
    [:russia :fleet :bot :attack :swe] #{}
    [:russia :fleet :sev :attack :rum] #{}
    [:turkey :army :bul :attack :ser] #{[true [:austria :army :bud :attack :ser] :attacked-same-destination]}
    ;; backed up
    [:turkey :army :con :attack :bul] #{[true [:turkey :army :bul :attack :ser] :failed-to-leave-destination]}
    [:turkey :fleet :ank :attack :bla] #{}}
   {:year 1902 :season :spring}
   {[:austria :army :tri :attack :bud] #{[true [:austria :army :vie :attack :bud] :attacked-same-destination]
                                         [true [:russia :army :gal :attack :bud] :attacked-same-destination]}
    [:austria :army :vie :attack :bud] #{[true [:austria :army :tri :attack :bud] :attacked-same-destination]
                                         [true [:russia :army :gal :attack :bud] :attacked-same-destination]}
    [:austria :army :bud :attack :ser] #{}
    [:austria :fleet :gre :hold] #{}
    [:england :army :nwy :attack :stp] #{[true [:russia :army :stp :attack :nwy] :swapped-places-without-convoy]}
    ;; backed up
    [:england :fleet :nth :attack :nwy] #{[true [:england :army :nwy :attack :stp] :failed-to-leave-destination]
                                          ;; TODO: if the unit was backed up,
                                          ;; do we report any other interfering
                                          ;; orders?
                                          [true [:russia :army :stp :attack :nwy] :attacked-same-destination]}
    [:england :fleet :bar :support :england :army :nwy :attack :stp] #{}
    ;; backed up
    [:england :fleet :edi :attack :nth] #{[true [:england :fleet :nth :attack :nwy] :failed-to-leave-destination]}
    [:france :army :bur :support :france :fleet :pic :attack :bel] #{[true [:germany :army :mun :attack :bur] :attacked]}
    [:france :army :por :attack :spa] #{}
    [:france :fleet :pic :attack :bel] #{[true [:germany :army :hol :attack :bel] :attacked-same-destination]}
    [:france :fleet :mar :hold] #{}
    [:germany :army :hol :attack :bel] #{[false [:france :fleet :pic :attack :bel] :attacked-same-destination]}
    [:germany :army :ruh :support :germany :army :hol :attack :bel] #{}
    [:germany :army :mun :attack :bur] #{[true [:france :army :bur :support :france :fleet :pic :attack :bel] :destination-occupied]}
    [:germany :fleet :den :hold] #{}
    [:germany :fleet :kie :attack :hol] #{}
    [:italy :army :ven :hold] #{}
    [:italy :army :pie :attack :mar] #{[true [:france :fleet :mar :hold] :destination-occupied]}
    [:italy :fleet :tun :attack :wes] #{}
    [:italy :fleet :nap :attack :tyn] #{}
    [:russia :army :ukr :support :russia :fleet :rum :hold] #{}
    [:russia :army :gal :attack :bud] #{[true [:austria :army :vie :attack :bud] :attacked-same-destination]
                                        [true [:austria :army :tri :attack :bud] :attacked-same-destination]}
    [:russia :army :stp :attack :nwy] #{[true [:england :army :nwy :attack :stp] :swapped-places-without-convoy]
                                        [false [:england :fleet :nth :attack :nwy] :attacked-same-destination]}
    [:russia :army :sev :support :russia :fleet :rum :hold] #{}
    [:russia :fleet :swe :support :russia :army :stp :attack :nwy] #{}
    [:russia :fleet :rum :hold] #{}
    [:turkey :army :bul :attack :rum] #{[true [:russia :fleet :rum :hold] :destination-occupied]}
    [:turkey :army :con :attack :bul] #{[true [:turkey :army :bul :attack :rum] :failed-to-leave-destination]}
    [:turkey :army :smy :attack :arm] #{}
    [:turkey :fleet :bla :support :turkey :army :bul :attack :rum] #{}}
   {:year 1902 :season :fall}
   {
    [:austria :army :vie :attack :gal] #{[true [:russia :army :gal :support :russia :fleet :rum :hold] :destination-occupied]}
    [:austria :army :tri :attack :bud] #{}
    [:austria :army :ser :support :turkey :army :bul :attack :rum] #{}
    [:austria :fleet :gre :hold] #{}
    [:england :army :nwy :attack :stp] #{[false [:russia :army :stp :attack :nwy] :swapped-places-without-convoy]}
    [:england :fleet :bar :support :england :army :nwy :attack :stp] #{}
    [:england :fleet :nth :attack :nwy] #{[false [:russia :army :stp :attack :nwy] :no-effect-on-dislodgers-province]}
    [:england :fleet :edi :attack :nth] #{}
    [:france :army :bur :attack :bel] #{[true [:germany :army :bel :support :germany :army :ruh :attack :bur] :destination-occupied]}
    [:france :fleet :pic :support :france :army :bur :attack :bel] #{}
    [:france :army :spa :support :france :fleet :mar :hold] #{}
    [:france :fleet :mar :support :france :army :spa :hold] #{[true [:italy :army :pie :attack :mar] :attacked]}
    [:germany :army :ruh :attack :bur] #{[false [:france :army :bur :attack :bel] :failed-to-leave-destination]}
    [:germany :army :mun :support :germany :army :ruh :attack :bur] #{}
    [:germany :army :bel :support :germany :army :ruh :attack :bur] #{[false [:france :army :bur :attack :bel] :attacked-from-supported-location-but-not-dislodged]}
    [:germany :fleet :den :attack :swe] #{[true [:russia :fleet :swe :support :russia :army :stp :attack :nwy] :destination-occupied]}
    [:germany :fleet :hol :support :germany :army :bel :hold] #{}
    ;; backed up
    [:italy :army :ven :attack :pie] #{[true [:italy :army :pie :attack :mar] :failed-to-leave-destination]}
    [:italy :army :pie :attack :mar] #{[true [:france :fleet :mar :support :france :army :spa :hold] :destination-occupied]}
    [:italy :fleet :wes :attack :naf] #{}
    [:italy :fleet :tyn :attack :gol] #{}
    [:russia :army :stp :attack :nwy] #{[true [:england :army :nwy :attack :stp] :swapped-places-without-convoy]
                                        [true [:england :fleet :nth :attack :nwy] :attacked-same-destination]}
    [:russia :fleet :swe :support :russia :army :stp :attack :nwy] #{[true [:germany :fleet :den :attack :swe] :attacked]}
    [:russia :fleet :rum :support :russia :army :sev :hold] #{[true [:turkey :army :bul :attack :rum] :attacked]}
    [:russia :army :sev :support :russia :fleet :rum :hold] #{[true [:turkey :army :arm :attack :sev] :attacked]}
    [:russia :army :gal :support :russia :fleet :rum :hold] #{[true [:austria :army :vie :attack :gal] :attacked]}
    [:russia :army :ukr :support :russia :army :sev :hold] #{}
    [:turkey :army :bul :attack :rum] #{[false [:russia :fleet :rum :support :russia :army :sev :hold] :destination-occupied]}
    [:turkey :army :con :attack :bul] #{}
    [:turkey :army :arm :attack :sev] #{[true [:russia :army :sev :support :russia :fleet :rum :hold] :destination-occupied]}
    [:turkey :fleet :bla :support :turkey :army :bul :attack :rum] #{}
    }})

(def rulebook-sample-game-cases
  (into {} (for [[k v] rulebook-sample-game-judgments-raw]
             [k (test-expansion/expand-and-fill-in-orders-phase-test
                 {:resolution-results-abbr v})])))
