(ns diplomacy.rulebook-sample-game
  (:require [diplomacy.datatypes :as dt :refer [create-order]]
            [diplomacy.maps]
            [diplomacy.util :refer [defn-spec]]
            [clojure.spec :as s]))

(defn-spec create-orders
  [(s/map-of (s/coll-of keyword?)
             (s/nilable (s/coll-of keyword?)))]
  (s/map-of ::dt/order
            (s/nilable ::dt/order)))
(defn ^:private create-orders [orders]
  (into {} (for [[k v] orders] [(apply create-order k)
                                (set (map (partial apply create-order) v))])))

;; this contains a good amount of supports, but only one convoy, and almost no
;; use of coasts. Be sure to add your own test cases to cover those!
(def rulebook-sample-game-cases
  {
   {:year 1901 :season :spring}
   (create-orders
    {[:austria :army :vie :attack :tri] #{}
     [:austria :army :bud :attack :gal] #{[:russia :army :war :attack :gal]}
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
     [:russia :army :war :attack :gal] #{[:austria :army :bud :attack :gal]}
     [:russia :fleet :stp-sc :attack :bot] #{}
     [:russia :fleet :sev :attack :bla] #{[:turkey :fleet :ank :attack :bla]}
     [:turkey :army :con :attack :bul] #{}
     [:turkey :army :smy :attack :con] #{}
     [:turkey :fleet :ank :attack :bla] #{[:russia :fleet :sev :attack :bla]}})
   {:year 1901 :season :fall}
   (create-orders
    {[:austria :army :tri :hold] #{}
     [:austria :army :bud :attack :ser] #{[:turkey :army :bul :attack :ser]}
     [:austria :fleet :alb :attack :gre] #{}
     ;; TODO(convoy): uncomment this
     ;; [:england :army :yor :attack :nwy]
     ;; [:england :fleet :nth :convoy :england :army :yor :attack :nwy]
     [:england :fleet :nrg :attack :bar] #{}
     [:france :army :bur :attack :mar] #{[:italy :army :pie :attack :mar]}
     [:france :army :spa :attack :por] #{}
     [:france :fleet :pic :attack :bel] #{[:germany :army :ruh :attack :bel]}
     [:germany :army :kie :attack :hol] #{}
     [:germany :army :ruh :attack :bel] #{[:france :fleet :pic :attack :bel]}
     [:germany :fleet :den :hold] #{}
     [:italy :army :ven :hold] #{}
     [:italy :army :pie :attack :mar] #{[:france :army :bur :attack :mar]}
     [:italy :fleet :ion :attack :tun] #{}
     [:russia :army :ukr :support :russia :fleet :sev :attack :rum] #{}
     [:russia :army :war :attack :gal] #{}
     [:russia :fleet :bot :attack :swe] #{}
     [:russia :fleet :sev :attack :rum] #{}
     [:turkey :army :bul :attack :ser] #{[:austria :army :bud :attack :ser]}
     ;; backed up
     [:turkey :army :con :attack :bul] #{[:turkey :army :bul :attack :ser]}
     [:turkey :fleet :ank :attack :bla] nil})
   {:year 1902 :season :spring}
   (create-orders
    {[:austria :army :tri :attack :bud] #{[:austria :army :vie :attack :bud]
                                          [:russia :army :gal :attack :bud]}
     [:austria :army :vie :attack :bud] #{[:austria :army :tri :attack :bud]
                                          [:russia :army :gal :attack :bud]}
     [:austria :army :bud :attack :ser] #{}
     [:austria :fleet :gre :hold] #{}
     [:england :army :nwy :attack :stp] #{[:russia :army :stp :attack :nwy]}
     ;; backed up
     [:england :fleet :nth :attack :nwy] #{[:england :army :nwy :attack :stp]
                                           ;; TODO: if the unit was backed up,
                                           ;; do we report any other interfering
                                           ;; orders?
                                           #_[:russia :army :stp :attack :nwy]}
     [:england :fleet :bar :support :england :army :nwy :attack :stp] #{}
     ;; backed up
     [:england :fleet :edi :attack :nth] #{[:england :fleet :nth :attack :nwy]}
     [:france :army :bur :support :france :fleet :pic :attack :bel] #{}
     [:france :army :por :attack :spa] #{}
     [:france :fleet :pic :attack :bel] #{[:germany :army :hol :attack :bel]}
     [:france :fleet :mar :hold] #{}
     [:germany :army :hol :attack :bel] #{}
     [:germany :army :ruh :support :germany :army :hol :attack :bel] #{}
     [:germany :army :mun :attack :bur] #{[:france :army :bur :support :france :fleet :pic :attack :bel]}
     [:germany :fleet :den :hold] #{}
     [:germany :fleet :kie :attack :hol] #{}
     [:italy :army :ven :hold] #{}
     [:italy :army :pie :attack :mar] #{[:france :fleet :mar :hold]}
     [:italy :fleet :tun :attack :wes] #{}
     [:italy :fleet :nap :attack :tyn] #{}
     [:russia :army :ukr :support :russia :fleet :rum :hold] #{}
     [:russia :army :gal :attack :bud] #{[:austria :army :vie :attack :bud]
                                         [:austria :army :tri :attack :bud]}
     [:russia :army :stp :attack :nwy] #{[:england :army :nwy :attack :stp]}
     [:russia :army :sev :support :russia :fleet :rum :hold] #{}
     [:russia :fleet :swe :support :russia :army :stp :attack :nwy] #{}
     [:russia :fleet :rum :hold] #{}
     [:turkey :army :bul :attack :rum] #{[:russia :fleet :rum :hold]}
     [:turkey :army :con :attack :bul] #{[:turkey :army :bul :attack :rum]}
     [:turkey :army :smy :attack :arm] #{}
     [:turkey :fleet :bla :support :turkey :army :bul :attack :rum] nil})
   {:year 1902 :season :fall}
   (create-orders
    {
     [:austria :army :vie :attack :gal] #{[:russia :army :gal :support :russia :fleet :rum :hold]}
     [:austria :army :tri :attack :bud] #{}
     [:austria :army :ser :support :turkey :army :bul :attack :rum] #{}
     [:austria :fleet :gre :hold] #{}
     [:england :army :nwy :attack :stp] #{}
     [:england :fleet :bar :support :england :army :nwy :attack :stp] #{}
     [:england :fleet :nth :attack :nwy] #{}
     [:england :fleet :edi :attack :nth] #{}
     [:france :army :bur :attack :bel] #{[:germany :army :bel :support :germany :army :ruh :attack :bur]}
     [:france :fleet :pic :support :france :army :bur :attack :bel] #{}
     [:france :army :spa :support :france :fleet :mar :hold] #{}
     [:france :fleet :mar :support :france :army :spa :hold] #{}
     [:germany :army :ruh :attack :bur] #{}
     [:germany :army :mun :support :germany :army :ruh :attack :bur] #{}
     [:germany :army :bel :support :germany :army :ruh :attack :bur] #{}
     [:germany :fleet :den :attack :swe] #{[:russia :fleet :swe :support :russia :army :stp :attack :nwy]}
     [:germany :fleet :hol :support :germany :army :bel :hold] #{}
     ;; backed up
     [:italy :army :ven :attack :pie] #{[:italy :army :pie :attack :mar]}
     [:italy :army :pie :attack :mar] #{[:france :fleet :mar :support :france :army :spa :hold]}
     [:italy :fleet :wes :attack :naf] #{}
     [:italy :fleet :tyn :attack :gol] #{}
     [:russia :army :stp :attack :nwy] #{[:england :army :nwy :attack :stp]
                                         [:england :fleet :nth :attack :nwy]}
     [:russia :fleet :swe :support :russia :army :stp :attack :nwy] #{}
     [:russia :fleet :rum :support :russia :army :sev :hold] #{}
     [:russia :army :sev :support :russia :fleet :rum :hold] #{}
     [:russia :army :gal :support :russia :fleet :rum :hold] #{}
     [:russia :army :ukr :support :russia :army :sev :hold] #{}
     [:turkey :army :bul :attack :rum] #{}
     [:turkey :army :con :attack :bul] #{}
     [:turkey :army :arm :attack :sev] #{[:russia :army :sev :support :russia :fleet :rum :hold]}
     [:turkey :fleet :bla :support :turkey :army :bul :attack :rum] nil
     })})



     ;; [:russia :fleet :swe :support :russia :army :stp :attack :nwy] #{[:germany :fleet :den :attack :swe]}
     ;; [:russia :fleet :rum :support :russia :army :sev :hold] #{[:turkey :army :bul :attack :rum]}
     ;; [:russia :army :sev :support :russia :fleet :rum :hold] #{[:turkey :army :arm :attack :sev]}
     ;; [:russia :army :gal :support :russia :fleet :rum :hold] #{[:austria :army :vie :attack :gal]}
