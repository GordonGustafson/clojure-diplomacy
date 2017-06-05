(ns diplomacy.rulebook-diagrams
  (:require [diplomacy.test-utils]))

(def ^:private rulebook-diagrams-raw
  {4 {[:germany :army :ber :attack :sil] #{[:russia  :army :war :attack :sil]}
      [:russia  :army :war :attack :sil] #{[:germany :army :ber :attack :sil]}}
   5 {[:germany :fleet :kie :attack :ber] #{[:germany :army :ber :attack :pru]}
      [:germany :army :ber :attack :pru] #{[::russia :army :pru :hold]}
      [::russia :army :pru :hold] #{}}
   6 {[:germany :fleet :ber :attack :pru] #{[:russia :army :pru :attack :ber]}
      [:russia :army :pru :attack :ber] #{[:germany :fleet :ber :attack :pru]}}
   7 {[:england :army :hol :attack :bel] #{}
      [:england :fleet :bel :attack :nth] #{}
      [:france :fleet :nth :attack :hol] #{}}
   8 {[:france :army :mar :attack :bur] #{}
      [:france :army :gas :support :france :army :mar :attack :bur] #{}
      [:germany :army :bur :hold] #{}}
   9 {[:germany :army :sil :attack :pru] #{}
      [:germany :fleet :bal :support :germany :army :sil :attack :pru] #{}
      [:russia :army :pru :hold] #{}}
   10 {[:france :fleet :gol :attack :tyn] #{[:italy :fleet :nap :attack :tyn]}
       [:france :fleet :wes :support :france :fleet :gol :attack :tyn] #{}
       [:italy :fleet :nap :attack :tyn] #{[:france :fleet :gol :attack :tyn]}
       [:italy :fleet :rom :support :italy :fleet :nap :attack :tyn] #{}}
   11 {[:france :fleet :gol :attack :tyn] #{[:italy :fleet :tyn :hold]}
       [:france :fleet :wes :support :france :fleet :gol :attack :tyn] #{}
       [:italy :fleet :tyn :hold] #{}
       [:italy :fleet :rom :support :italy :fleet :tyn :hold] #{}}
   12 {[:austria :army :boh :attack :mun] #{}
       [:austria :army :tyr :support :austria :army :boh :attack :mun] #{}
       [:germany :army :mun :attack :sil] #{[:germany :army :mun :attack :sil]}
       [:germany :army :ber :support :germany :army :mun :attack :sil] #{}
       [:russia :army :war :attack :sil] #{[:germany :army :mun :attack :sil]}
       [:russia :army :pru :support :russia :army :war :attack :sil] #{}}
   13 {[:turkey :army :bul :attack :rum] #{[:russia :army :rum :attack :bul]}
       [:russia :army :rum :attack :bul] #{}
       [:russia :army :ser :support :russia :army :rum :attack :bul] #{}
       [:russia :army :sev :attack :rum] #{}}
   14 {[:turkey :army :bul :attack :rum] #{[:russia :army :rum :attack :bul]}
       [:turkey :fleet :bla :support :turkey :army :bul :attack :rum] #{}
       [:russia :army :rum :attack :bul] #{}
       [:russia :army :gre :support :russia :army :rum :attack :bul] #{}
       [:russia :army :ser :support :russia :army :rum :attack :bul] #{}
       [:russia :army :sev :attack :rum] #{}}
   15 {[:germany :army :pru :attack :war] #{[:russia :army :war :hold]}
       [:germany :army :sil :support :germany :army :pru :attack :war] #{}
       [:russia :army :war :hold] #{}
       [:russia :army :boh :attack :sil] #{[:germany :army :sil :support :germany :army :pru :attack :war]}}
   16 {[:germany :army :pru :attack :war] #{}
       [:germany :army :sil :support :germany :army :pru :attack :war] #{}
       [:russia :army :war :attack :sil] #{[:germany :army :sil :support :germany :army :pru :attack :war]}}
   17 {[:germany :fleet :ber :attack :pru] #{[:russia :fleet :bal :attack :pru]}
       [:germany :army :sil :support :germany :army :ber :attack :pru] #{}
       [:russia :army :pru :attack :sil] #{}
       [:russia :army :war :support :russia :army :pru :attack :sil] #{}
       [:russia :fleet :bal :attack :pru] #{[:germany :fleet :ber :attack :pru]}}
   18 {[:germany :army :ber :hold] #{}
       [:germany :army :mun :attack :sil] #{[:russia :army :sil :support :russia :army :pru :attack :ber]}
       [:russia :army :pru :attack :ber] #{[:germany :army :ber :hold]}
       [:russia :army :sil :support :russia :army :pru :attack :ber] #{}
       [:russia :army :boh :attack :mun] #{}
       [:russia :army :tyr :support :russia :army :boh :attack :mun] #{}}
   })

(def rulebook-diagrams
  {}
  #_(into {} (for [[k v] rulebook-diagrams-raw]
             [k (diplomacy.test-utils/create-orders v)])))

   ;; have checked up to this line

   ;; 19 {[:england :army :lon :attack :nwy] #{}
   ;;     [:england :fleet :nth :convoy :england :army :lon :attack :nwy] #{}}
   ;; 20 {[:england :army :lon :attack :tun] #{}
   ;;     [:england :fleet :eng :convoy :england :army :lon :attack :tun] #{}
   ;;     [:england :fleet :mid :convoy :england :army :lon :attack :tun] #{}
   ;;     [:france  :fleet :wes :convoy :england :army :lon :attack :tun] #{}}
   ;; 21 {[:france :army :spa :attack :nap] #{   }
   ;;     [:france :fleet :gol :convoy :france :army :spa :attack :nap] #{}
   ;;     [:france :fleet :tyn :convoy :france :army :spa :attack :nap] #{   }
   ;;     [:italy :fleet :ion :attack :tyn] #{}
   ;;     [:italy :fleet :tun :support :italy :fleet :ion :attack :tyn] #{}}
   ;; 22 {[:france :army :par :attack :bur] #{   }
   ;;     [:france :army :mar :support :france :army :par :attack :bur] #{}
   ;;     [:france :army :bur :hold] #{}}
   ;; 23 {[:france :army :par :attack :bur] #{   }
   ;;     [:france :army :bur :attack :mar] #{   }
   ;;     [:germany :army :ruh :support :france :army :par :attack :bur] #{}
   ;;     [:italy :army :mar :attack :bur] #{   }}
   ;; 24 {[:germany :army :ruh :attack :bur] #{   }
   ;;     [:germany :army :mun :hold] #{}
   ;;     [:france :army :par :support :germany :army :ruh :attack :bur] #{}
   ;;     [:france :army :bur :hold] #{}}
   ;; 25 {[:germany :army :mun :attack :tyr] #{   }
   ;;     [:germany :army :ruh :attack :mun] #{   }
   ;;     [:germany :army :sil :attack :mun] #{   }
   ;;     [:austria :army :tyr :attack :mun] #{   }
   ;;     [:austria :army :boh :support :germany :army :sil :attack :mun] #{}}
   ;; 26 {[:england :fleet :den :attack :kiel] #{   }
   ;;     [:england :fleet :nth :attack :den] #{   }
   ;;     [:england :fleet :hel :support :england :fleet :nth :attack :den] #{}
   ;;     [:russia :army :ber :attack :kiel] #{   }
   ;;     [:russia :fleet :ska :attack :den] #{   }
   ;;     [:russia :fleet :bal :support ::russia :fleet :ska :attack :den] #{}}
   ;; 27 {[:austria :army :ser :attack :bud] #{}
   ;;     [:austria :army :vie :attack :bud] #{   }
   ;;     [:russia :army :gal :support :austria :army :ser :attack :bud] #{}}
   ;; 28 {[:england :army :lon :attack :bel] #{}
   ;;     [:england :fleet :nth :convoy :england :army :lon :attack :bel] #{}
   ;;     [:france :army :bel :attack :lon] #{}
   ;;     [:france :fleet :eng :convoy :france :army :bel :attack :lon] #{}}
   ;; 29 {[:england :army :lon :attack :bel] #{}
   ;;     [:england :fleet :eng :convoy :england :army :lon :attack :bel] #{   }
   ;;     [:england :fleet :nth :convoy :england :army :lon :attack :bel] #{}
   ;;     [:france :fleet :bre :attack :eng] #{}
   ;;     [:france :fleet :iri :support :france :fleet :bre :attack :eng] #{}}
   ;; 30 {[:france :army :tun :attack :nap] #{   }
   ;;     [:france :fleet :tyn :convoy :france :army :tun :attack :nap] #{   }
   ;;     [:italy :fleet :ion :attack :tyn] #{}
   ;;     [:italy :fleet :nap :support :italy :fleet :ion :attack :tyn] #{}}
   ;; 31 {[:france :army :tun :attack :nap] #{   }
   ;;     [:france :fleet :tyn :convoy :france :army :tun :attack :nap] #{}
   ;;     [:france :fleet :ion :convoy :france :army :tun :attack :nap] #{}
   ;;     [:italy :fleet :rom :attack :tyn] #{   }
   ;;     [:italy :fleet :nap :support :italy :fleet :rom :attack :tyn] #{}}
   ;; 32 {[:france :army :tun :attack :nap] #{}
   ;;     [:france :fleet :tyn :convoy :france :army :tun :attack :nap] #{}
   ;;     [:france :fleet :ion :convoy :france :army :tun :attack :nap] #{}
   ;;     [:france :army :apu :support :france :army :tun :attack :nap] #{}
   ;;     [:italy :fleet :rom :attack :tyn] #{   }
   ;;     [:italy :fleet :nap :support :italy :fleet :rom :attack :tyn] #{}}})

