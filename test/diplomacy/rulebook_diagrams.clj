(ns diplomacy.rulebook-sample-game
  (:require [diplomacy.datatypes :refer [create-order]]))

(defn- create-orders [& orders]
  (map #(apply create-order %) orders))

(def rulebook-diagrams
  {4 {[:germany :army :ber :attack :sil] false
      [:russia  :army :war :attack :sil] false}
   5 {[:germany :fleet :kie :attack :ber] false
      [:germany :army :ber :attack :pru] false
      [::russia :army :pru :hold] true}
   6 {[:germany :fleet :ber :attack :pru] false
      [:russia :army :pru :attack :ber] false}
   7 {[:england :army :hol :attack :bel] true
      [:england :fleet :bel :attack :nth] true
      [:france :fleet :nth :attack :hol] true}
   8 {[:france :army :mar :attack :bur] true
      [:france :army :gas :support :france :army :mar :attack :bur] true
      [:germany :army :bur :hold] false}
   9 {[:germany :army :sil :attack :pru] true
      [:germany :fleet :bal :support :germany :army :sil :attack :pru] true
      [:russia :army :pru :hold] false}
   10 {[:france :fleet :gol :attack :tyn] false
       [:france :fleet :wes :support :france :fleet :gol :attack :tyn] true
       [:italy :fleet :nap :attack :tyn] false
       [:italy :fleet :rom :support :italy :fleet :nap :attack :tyn] true}
   11 {[:france :fleet :gol :attack :tyn] false
       [:france :fleet :wes :support :france :fleet :gol :attack :tyn] true
       [:italy :fleet :tyn :hold] true
       [:italy :fleet :rom :support :italy :fleet :tyn :hold] true}
   12 {[:austria :army :boh :attack :mun] true
       [:austria :army :tyr :support :austria :army :boh :attack :mun] true
       [:germany :army :mun :attack :sil] false
       [:germany :army :ber :support :germany :army :mun :attack :sil] true
       [:russia :army :war :attack :sil] false
       [:russia :army :pru :support :russia :army :war :attack :sil] true}
   13 {[:turkey :army :bul :attack :rum] false
       [:russia :army :rum :attack :bul] true
       [:russia :army :ser :support :russia :army :rum :attack :bul] true
       [:russia :army :sev :attack :rum] true}
   14 {[:turkey :army :bul :attack :rum] false
       [:turkey :fleet :bla :support :turkey :army :bul :attack :rum] true
       [:russia :army :rum :attack :bul] true
       [:russia :army :gre :support :russia :army :rum :attack :bul] true
       [:russia :army :ser :support :russia :army :rum :attack :bul] true
       [:russia :army :sev :attack :rum] true}
   ;; have checked up to this line
   15 {[:germany :army :pru :attack :war] false
       [:germany :army :sil :support :germany :army :pru :attack :war] false
       [:russia :army :war :hold] true
       [:russia :army :boh :attack :sil] false}
   16 {[:germany :army :pru :attack :war] true
       [:germany :army :sil :support :germany :army :pru :attack :war] true
       [:russia :army :war :attack :sil] false}
   17 {[:germany :fleet :ber :attack :pru] false
       [:geramny :army :sil :support :germany :army :ber :attack :pru] false
       [:russia :army :pru :attack :sil] true
       [:russia :army :war :support :russia :army :pru :attack :sil] true
       [:russia :fleet :bal :attack :pru] false}
   18 {[:germany :army :ber :hold] true
       [:germany :army :mun :attack :sil] false
       [:russia :army :pru :attack :ber] false
       [:russia :army :sil :support :russia :army :pru :attack :ber] false
       [:russia :army :boh :attack :mun] true
       [:russia :army :tyr :support :russia :army :boh :attack :mun] true}

   19 {[:england :army :lon :attack :nwy] true
       [:england :fleet :nth :convoy :england :army :lon :attack :nwy] true}
   20 {[:england :army :lon :attack :tun] true
       [:england :fleet :eng :convoy :england :army :lon :attack :tun] true
       [:england :fleet :mid :convoy :england :army :lon :attack :tun] true
       [:france  :fleet :wes :convoy :england :army :lon :attack :tun] true}
   21 {[:france :army :spa :attack :nap] false
       [:france :fleet :gol :convoy :france :army :spa :attack :nap] true
       [:france :fleet :tyn :convoy :france :army :spa :attack :nap] false
       [:italy :fleet :ion :attack :tyn] true
       [:italy :fleet :tun :support :italy :fleet :ion :attack :tyn] true}
   22 {[:france :army :par :attack :bur] false
       [:france :army :mar :support :france :army :par :attack :bur] true
       [:france :army :bur :hold] true}
   23 {[:france :army :par :attack :bur] false
       [:france :army :bur :attack :mar] false
       [:germany :army :ruh :support :france :army :par :attack :bur] true
       [:italy :army :mar :attack :bur] false}
   24 {[:germany :army :ruh :attack :bur] false
       [:germany :army :mun :hold] true
       [:france :army :par :support :germany :army :ruh :attack :bur] true
       [:france :army :bur :hold] true}
   25 {[:germany :army :mun :attack :tyr] false
       [:germany :army :ruh :attack :mun] false
       [:germany :army :sil :attack :mun] false
       [:austria :army :tyr :attack :mun] false
       [:austria :army :boh :support :germany :army :sil :attack :mun] true}
   26 {[:england :fleet :den :attack :kiel] false
       [:england :fleet :nth :attack :den] false
       [:england :fleet :hel :support :england :fleet :nth :attack :den] true
       [:russia :army :ber :attack :kiel] false
       [:russia :fleet :ska :attack :den] false
       [:russia :fleet :bal :support ::russia :fleet :ska :attack :den] true}
   27 {[:austria :army :ser :attack :bud] true
       [:austria :army :vie :attack :bud] false
       [:russia :army :gal :support :austria :army :ser :attack :bud] true}
   28 {[:england :army :lon :attack :bel] true
       [:england :fleet :nth :convoy :england :army :lon :attack :bel] true
       [:france :army :bel :attack :lon] true
       [:france :fleet :eng :convoy :france :army :bel :attack :lon] true}
   29 {[:england :army :lon :attack :bel] true
       [:england :fleet :eng :convoy :england :army :lon :attack :bel] false
       [:england :fleet :nth :convoy :england :army :lon :attack :bel] true
       [:france :fleet :bre :attack :eng] true
       [:france :fleet :iri :support :france :fleet :bre :attack :eng] true}
   30 {[:france :army :tun :attack :nap] false
       [:france :fleet :tyn :convoy :france :army :tun :attack :nap] false
       [:italy :fleet :ion :attack :tyn] true
       [:italy :fleet :nap :support :italy :fleet :ion :attack :tyn] true}
   31 {[:france :army :tun :attack :nap] false
       [:france :fleet :tyn :convoy :france :army :tun :attack :nap] true
       [:france :fleet :ion :convoy :france :army :tun :attack :nap] true
       [:italy :fleet :rom :attack :tyn] false
       [:italy :fleet :nap :support :italy :fleet :rom :attack :tyn] false}
   32 {[:france :army :tun :attack :nap] true
       [:france :fleet :tyn :convoy :france :army :tun :attack :nap] true
       [:france :fleet :ion :convoy :france :army :tun :attack :nap] true
       [:france :army :apu :support :france :army :tun :attack :nap] true
       [:italy :fleet :rom :attack :tyn] false
       [:italy :fleet :nap :support :italy :fleet :rom :attack :tyn] false}})

