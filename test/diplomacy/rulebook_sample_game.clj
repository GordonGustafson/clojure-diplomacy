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
                                (if (nil? v)
                                  nil
                                  (apply create-order v))])))

;; this contains a good amount of supports, but only one convoy, and almost no
;; use of coasts. Be sure to add your own test cases to cover those!
(def rulebook-sample-game-cases
  {
   {:year 1901 :season :spring}
   (create-orders
    {[:austria :army :vie :attack :tri] nil
     [:austria :army :bud :attack :gal] [:russia :army :war :attack :gal]
     [:austria :fleet :tri :attack :alb] nil
     [:england :army :lvp :attack :yor] nil
     [:england :fleet :lon :attack :nth] nil
     [:england :fleet :edi :attack :nrg] nil
     [:france :army :par :attack :bur] nil
     [:france :army :mar :attack :spa] nil
     [:france :fleet :bre :attack :pic] nil
     [:germany :army :ber :attack :kie] nil
     [:germany :army :mun :attack :ruh] nil
     [:germany :fleet :kie :attack :den] nil
     [:italy :army :ven :attack :pie] nil
     [:italy :army :rom :attack :ven] nil
     [:italy :fleet :nap :attack :ion] nil
     [:russia :army :mos :attack :ukr] nil
     [:russia :army :war :attack :gal] [:austria :army :bud :attack :gal]
     [:russia :fleet :stp-sc :attack :bot] nil
     [:russia :fleet :sev :attack :bla] [:turkey :fleet :ank :attack :bla]
     [:turkey :army :con :attack :bul] nil
     [:turkey :army :smy :attack :con] nil
     [:turkey :fleet :ank :attack :bla] [:russia :fleet :sev :attack :bla]})
   {:year 1901 :season :fall}
   (create-orders
    {[:austria :army :tri :hold] nil
     [:austria :army :bud :attack :ser] [:turkey :army :bul :attack :ser]
     [:austria :fleet :alb :attack :gre] nil
     ;; TODO(convoy): uncomment this
     ;; [:england :army :yor :attack :nwy]
     ;; [:england :fleet :nth :convoy :england :army :yor :attack :nwy]
     [:england :fleet :nrg :attack :bar] nil
     [:france :army :bur :attack :mar] [:italy :army :pie :attack :mar]
     [:france :army :spa :attack :por] nil
     [:france :fleet :pic :attack :bel] [:germany :army :ruh :attack :bel]
     [:germany :army :kie :attack :hol] nil
     [:germany :army :ruh :attack :bel] [:france :fleet :pic :attack :bel]
     [:germany :fleet :den :hold] nil
     [:italy :army :ven :hold] nil
     [:italy :army :pie :attack :mar] [:france :army :bur :attack :mar]
     [:italy :fleet :ion :attack :tun] nil
     [:russia :army :ukr :support :russia :fleet :sev :attack :rum] nil
     [:russia :army :war :attack :gal] nil
     [:russia :fleet :bot :attack :swe] nil
     [:russia :fleet :sev :attack :rum] nil
     [:turkey :army :bul :attack :ser] [:austria :army :bud :attack :ser]
     ;; backed up
     [:turkey :army :con :attack :bul] [:turkey :army :bul :attack :ser]
     [:turkey :fleet :ank :attack :bla] nil})
   {:year 1902 :season :spring}
   (create-orders
    {[:austria :army :tri :attack :bud] [:austria :army :vie :attack :bud]
     [:austria :army :vie :attack :bud] [:austria :army :tri :attack :bud]
     [:austria :army :bud :attack :ser] nil
     [:austria :fleet :gre :hold] nil
     [:england :army :nwy :attack :stp] [:russia :army :stp :attack :nwy]
     ;; backed up
     [:england :fleet :nth :attack :nwy] [:england :army :nwy :attack :stp]
     [:england :fleet :bar :support :england :army :nwy :attack :stp] nil
     [:england :fleet :edi :attack :nth] nil
     ;; TODO: do failed supports get assigned a failure reason?
     [:france :army :bur :support :france :fleet :pic :attack :bel] nil
     [:france :army :por :attack :spa] nil
     [:france :fleet :pic :attack :bel] [:germany :army :hol :attack :bel]
     [:france :fleet :mar :hold] nil
     [:germany :army :hol :attack :bel] nil
     ;; TODO: can a support order be the failure reason, or do we interpret it
     ;; as a hold order and specify that as the failure reason?
     [:germany :army :ruh :support :germany :army :hol :attack :bel] nil
     [:germany :army :mun :attack :bur] [:france :army :bur :support :france :fleet :pic :attack :bel]
     [:germany :fleet :den :hold] nil
     [:germany :fleet :kie :attack :hol] nil
     [:italy :army :ven :hold] nil
     [:italy :army :pie :attack :mar] [:france :fleet :mar :hold]
     [:italy :fleet :tun :attack :wes] nil
     [:italy :fleet :nap :attack :tyn] nil
     [:russia :army :ukr :support :russia :fleet :rum :hold] nil
     ;; TODO: what happens when there are two attacks that each could have
     ;; caused the attack to fail? (two of Austria's attacks to the same
     ;; location).
     [:russia :army :gal :attack :bud] [:austria :army :tri :attack :bud]
     [:russia :army :stp :attack :nwy] [:england :army :nwy :attack :stp]
     [:russia :army :sev :support :russia :fleet :rum :hold] nil
     [:russia :fleet :swe :support :russia :army :stp :attack :nwy] nil
     [:russia :fleet :rum :hold] nil
     [:turkey :army :bul :attack :rum] [:russia :fleet :rum :hold]
     [:turkey :army :con :attack :bul] [:turkey :army :bul :attack :rum]
     [:turkey :army :smy :attack :arm] nil
     [:turkey :fleet :bla :support :turkey :army :bul :attack :rum] nil})
   })
   ;; {:year 1902 :season :fall}
   ;; (create-orders
   ;;  ;; TODO: can a support order be the failure reason?
   ;;  {[:austria :army :vie :attack :gal] [:russia :army :gal :support :russia :fleet :rum :hold]
   ;;   [:austria :army :tri :attack :bud] nil
   ;;   [:austria :army :ser :support :turkey :army :bul :attack :rum] nil
   ;;   [:austria :fleet :gre :hold] nil
   ;;   [:england :army :nwy :attack :stp] nil
   ;;   [:england :fleet :bar :support :england :army :nwy :attack :stp] nil
   ;;   [:england :fleet :nth :attack :nwy] nil
   ;;   [:england :fleet :edi :attack :nth] nil
   ;;  ;; TODO: can a support order be the failure reason?
   ;;   [:france :army :bur :attack :bel] [:germany :army :bel :support :germany :army :ruh :attack :bur]
   ;;   [:france :fleet :pic :support :france :army :bur :attack :bel] nil
   ;;   [:france :army :spa :support :france :fleet :mar :hold] nil
   ;;   ;; TODO: do failed supports get assigned a failure reason?
   ;;   [:france :fleet :mar :support :france :army :spa :hold] [:italy :army :pie :attack :mar]
   ;;   [:germany :army :ruh :attack :bur] nil
   ;;   [:germany :army :mun :support :germany :army :ruh :attack :bur] nil
   ;;   [:germany :army :bel :support :germany :army :ruh :attack :bur] nil
   ;;  ;; TODO: can a support order be the failure reason?
   ;;   [:germany :fleet :den :attack :swe] [:russia :fleet :swe :support :russia :army :stp :attack :nwy]
   ;;   [:germany :fleet :hol :support :germany :army :bel :hold] nil
   ;;   ;; backed up
   ;;   [:italy :army :ven :attack :pie] [:italy :army :pie :attack :mar]
   ;;  ;; TODO: can a support order be the failure reason?
   ;;   [:italy :army :pie :attack :mar] [:france :army :spa :support :france :fleet :mar :hold]
   ;;   [:italy :fleet :wes :attack :naf] nil
   ;;   [:italy :fleet :tyn :attack :gol] nil
   ;;   ;; TODO: what happens when there's both an attack trying to switch places
   ;;   ;; and another into the attacked location? (both England's attacks).
   ;;   [:russia :army :stp :attack :nwy] [:england :army :nwy :attack :stp]
   ;;   ;; TODO: do failed supports get assigned a failure reason?
   ;;   [:russia :fleet :swe :support :russia :army :stp :attack :nwy] [:germany :fleet :den :attack :swe]
   ;;   ;; TODO: do failed supports get assigned a failure reason?
   ;;   [:russia :fleet :rum :support :russia :army :sev :hold] [:turkey :army :bul :attack :rum]
   ;;   ;; TODO: do failed supports get assigned a failure reason?
   ;;   [:russia :army :sev :support :russia :fleet :rum :hold] [:turkey :army :arm :attack :sev]
   ;;   ;; TODO: do failed supports get assigned a failure reason?
   ;;   [:russia :army :gal :support :russia :fleet :rum :hold] [:austria :army :vie :attack :gal]
   ;;   [:russia :army :ukr :support :russia :army :sev :hold] nil
   ;;   [:turkey :army :bul :attack :rum] nil
   ;;   [:turkey :army :con :attack :bul] nil
   ;;  ;; TODO: can a support order be the failure reason?
   ;;   [:turkey :army :arm :attack :sev] [:russia :army :sev :support :russia :fleet :rum :hold]
   ;;   [:turkey :fleet :bla :support :turkey :army :bul :attack :rum] nil})})
