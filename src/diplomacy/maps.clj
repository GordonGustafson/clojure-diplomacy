(ns diplomacy.maps
  (:require [diplomacy.datatypes :refer [map->DiplomacyMap
                                         map->GameState map->GameTime]])
  (:import (diplomacy.datatypes Unit)))

(def classic-map
  (let [land #{:army}
        sea #{:fleet}
        coast #{:army :fleet}
        location-accessibility
        {:adr    sea
         :aeg    sea
         :alb    coast
         :ank    coast
         :apu    coast
         :arm    coast
         :bal    sea
         :bar    sea
         :bel    coast
         :ber    coast
         :bla    sea
         :boh    land
         :bot    sea
         :bre    coast
         :bud    land
         :bul-ec sea
         :bul    land
         :bul-sc sea
         :bur    land
         :cly    coast
         :con    coast
         :den    coast
         :eas    sea
         :edi    coast
         :eng    sea
         :fin    coast
         :gal    land
         :gas    coast
         :gol    sea
         :gre    coast
         :hel    sea
         :hol    coast
         :ion    sea
         :iri    sea
         :kie    coast
         :lon    coast
         :lvn    coast
         :lvp    coast
         :mar    coast
         :mid    sea
         :mos    land
         :mun    land
         :naf    coast
         :nap    coast
         :nat    sea
         :nrg    sea
         :nth    sea
         :nwy    coast
         :par    land
         :pic    coast
         :pie    coast
         :por    coast
         :pru    coast
         :rom    coast
         :ruh    land
         :rum    coast
         :ser    land
         :sev    coast
         :sil    land
         :ska    sea
         :smy    coast
         :spa    land
         :spa-nc sea
         :spa-sc sea
         :stp    land
         :stp-nc sea
         :stp-sc sea
         :swe    coast
         :syr    coast
         :tri    coast
         :tun    coast
         :tus    coast
         :tyn    land
         :tys    sea
         :ukr    land
         :ven    coast
         :vie    land
         :wal    coast
         :war    land
         :wes    sea
         :yor    coast}
        edge-accessibility
        {:adr    {:ven sea, :tri sea, :alb sea, :ion sea, :apu sea}
         :aeg    {:eas sea, :ion sea, :gre sea, :bul-sc sea, :con sea, :smy sea}
         :alb    {:adr sea, :tri coast, :ser land, :gre coast, :ion sea}
         :ank    {:con coast, :bla sea, :arm coast, :smy land}
         :apu    {:rom land, :ven coast, :adr sea, :ion sea, :nap coast}
         :arm    {:ank coast, :bla sea, :sev coast, :syr land, :smy land}
         :bal    {:den sea, :swe sea, :bot sea, :lvn sea, :pru sea, :ber sea, :kie sea}
         :bar    {:nrg sea, :stp-nc sea, :nwy sea}
         :bel    {:pic coast, :eng sea, :nth sea, :hol coast, :ruh land, :bur land}
         :ber    {:kie coast, :bal sea, :pru coast, :sil land, :mun land}
         :bla    {:bul-ec sea, :rum sea, :sev sea, :arm sea, :ank sea, :con sea}
         :boh    {:mun land, :sil land, :gal land, :vie land, :tyr land}
         :bot    {:swe sea, :fin sea, :stp-sc sea, :lvn sea, :bal sea}
         :bre    {:mid sea, :eng sea, :pic coast, :par land, :gas coast}
         :bud    {:tri land, :vie land, :gal land, :rum land, :ser land}
         :bul-ec {:rum sea, :bla sea, :con sea}
         :bul-sc {:gre sea, :con sea, :aeg sea}
         :bul    {:ser land, :rum land, :con land, :gre land}
         :bur    {:par land, :pic land, :bel land, :ruh land, :mun land, :mar land, :gas land}
         :cly    {:nat sea, :nrg sea, :edi coast, :lvp coast}
         :con    {:bul-sc sea, :bul land, :bul-ec sea, :bla sea, :ank coast, :smy coast, :aeg sea}
         :den    {:hel sea, :nth sea, :ska sea, :swe coast, :bal sea, :kie coast}
         :eas    {:ion sea, :aeg sea, :smy sea, :syr sea}
         :edi    {:cly coast, :nrg sea, :nth sea, :yor coast, :lvp land}
         :eng    {:mid sea, :iri sea, :wal sea, :lon sea, :nth sea, :bel sea, :pic sea, :bre sea}
         :fin    {:nwy land, :bot sea, :swe coast, :stp land, :stp-sc sea}
         :gal    {:boh land, :sil land, :war land, :ukr land, :rum land, :bud land, :vie land}
         :gas    {:mid sea, :bre coast, :par land, :bur land, :mar land, :spa land, :spa-nc sea}
         :gol    {:spa-sc sea, :mar sea, :pie sea, :tus sea, :tys sea, :wes sea}
         :gre    {:ion sea, :alb coast, :ser land, :bul land, :bul-sc sea, :aeg sea}
         :hel    {:nth sea, :den sea, :kie sea, :hol sea}
         :hol    {:nth sea, :hel sea, :kie coast, :ruh land, :bel coast}
         :ion    {:apu sea, :adr sea, :tun sea, :tys sea, :nap sea, :alb sea, :gre sea, :aeg sea, :eas sea}
         :iri    {:nat sea, :lvp sea, :wal sea, :eng sea, :mid sea}
         :kie    {:hol coast, :hel sea, :den coast, :bal sea, :ber coast, :mun land, :ruh land}
         :lon    {:wal coast, :yor coast, :nth sea, :eng sea}
         :lvn    {:stp land, :bal sea, :bot sea, :stp-sc sea, :mos land, :war land, :pru coast}
         :lvp    {:iri sea, :nat sea, :cly coast, :edi land, :yor land, :wal coast}
         :mar    {:spa land, :gas land, :bur land, :pie coast, :gol sea, :spa-sc sea}
         :mid    {:wes sea, :nat sea, :iri sea, :eng sea, :bre sea, :gas sea, :spa-nc sea, :por sea, :spa-sc sea, :naf sea}
         :mos    {:stp land, :sev land, :ukr land, :war land, :lvn land}
         :mun    {:bur land, :ruh land, :kie land, :ber land, :sil land, :boh land, :tyr land}
         :naf    {:mid sea, :wes sea, :tun coast}
         :nap    {:tys sea, :rom coast, :apu coast, :ion sea}
         :nat    {:nrg sea, :cly sea, :lvp sea, :iri sea, :mid sea}
         :nrg    {:nat sea, :bar sea, :nwy sea, :nth sea, :edi sea, :cly sea}
         :nth    {:eng sea, :edi sea, :nrg sea, :nwy sea, :ska sea, :den sea, :hel sea, :hol sea, :bel sea, :lon sea, :yor sea}
         :nwy    {:nth sea, :nrg sea, :bar sea, :stp-nc sea, :stp land, :fin land, :swe coast, :ska sea}
         :par    {:bre land, :pic land, :bur land, :gas land}
         :pic    {:bre coast, :eng sea, :bel coast, :bur land, :par land}
         :pie    {:mar coast, :tyr land, :ven land, :tus coast, :gol sea}
         :por    {:mid sea, :spa-nc sea, :spa land, :spa-sc sea}
         :pru    {:ber coast, :bal sea, :lvn coast, :war land, :sil land}
         :rom    {:tys sea, :tus coast, :ven land, :apu land, :nap coast}
         :ruh    {:bel land, :hol land, :kie land, :mun land, :bur land}
         :rum    {:bla sea, :bud land, :gal land, :ukr land, :sev coast, :bul-ec sea, :bul land, :ser land}
         :ser    {:tri land, :bud land, :rum land, :bul land, :gre land, :alb land}
         :sev    {:ukr land, :mos land, :arm coast, :bla sea, :rum coast}
         :sil    {:mun land, :ber land, :pru land, :war land, :gal land, :boh land}
         :ska    {:nth sea, :nwy sea, :swe sea, :den sea}
         :smy    {:aeg sea, :con coast, :ank land, :arm land, :syr coast, :eas sea}
         :spa-nc {:por sea, :mid sea, :gas sea}
         :spa    {:por land, :gas land, :mar land}
         :spa-sc {:mid sea, :por sea, :mar sea, :gol sea, :wes sea}
         :stp    {:fin land, :nwy land, :mos land, :lvn land}
         :stp-nc {:bar sea, :nwy sea}
         :stp-sc {:bot sea, :fin sea, :lvn sea}
         :swe    {:ska sea, :nwy coast, :fin coast, :bot sea, :bal sea, :den coast}
         :syr    {:eas sea, :smy coast, :arm land}
         :tri    {:adr sea, :ven coast, :tyr land, :vie land, :bud land, :ser land, :alb coast}
         :tun    {:naf coast, :wes sea, :tys sea, :ion sea}
         :tus    {:gol sea, :pie coast, :ven land, :rom coast, :tys sea}
         :tyr    {:mun land, :boh land, :vie land, :tri land, :ven land, :pie land}
         :tys    {:wes sea, :gol sea, :tus sea, :rom sea, :nap sea, :ion sea, :tun sea}
         :ukr    {:war land, :mos land, :sev land, :rum land, :gal land}
         :ven    {:tus land, :pie land, :tyr land, :tri coast, :adr sea, :apu coast, :rom land}
         :vie    {:tyr land, :boh land, :gal land, :bud land, :tri land}
         :wal    {:iri sea, :lvp coast, :yor land, :lon coast, :eng sea}
         :war    {:sil land, :pru land, :lvn land, :mos land, :ukr land, :gal land}
         :wes    {:mid sea, :spa-sc sea, :gol sea, :tys sea, :tun sea, :naf sea}
         :yor    {:lvp land, :edi coast, :nth sea, :lon coast, :wal land}}
        colocated-locations
        #{#{:bud :bul-ec :bul-sc}
          #{:spa :spa-nc :spa-sc}
          #{:stp :stp-nc :stp-sc}}
        supply-centers
        #{:stp :mos :sev :tun :lvp :edi :nwy :war :ank :smy :gre :nap :spa :por
          :bre :swe :rum :bul :con :ser :rom :mar :par :lon :bel :hol :den :ber
          :mun :vie :bud :tri :ven :kie}
        home-supply-centers
        {:austria #{:vie :bud :tri}
         :england #{:lon :edi :lvp}
         :france  #{:par :mar :bre}
         :germany #{:ber :mun :kie}
         :italy   #{:rom :ven :nap}
         :russia  #{:mos :sev :war :stp}
         :turkey  #{:ank :con :smy}}
        starting-unit-positions
        {:vie (Unit. :army  :austria) :bud (Unit. :army  :austria) :tri (Unit. :fleet :austria)
         :lon (Unit. :fleet :england) :edi (Unit. :fleet :england) :lvp (Unit. :army  :england)
         :par (Unit. :army  :france ) :mar (Unit. :army  :france ) :bre (Unit. :fleet :france )
         :ber (Unit. :army  :german ) :mun (Unit. :army  :german ) :kie (Unit. :fleet :german )
         :rom (Unit. :army  :italy  ) :ven (Unit. :army  :italy  ) :nap (Unit. :fleet :italy  )
         :mos (Unit. :army  :russia ) :sev (Unit. :fleet :russia ) :war (Unit. :army  :russia ) :stp-sc (Unit. :fleet :russia)
         :ank (Unit. :fleet :turkey ) :con (Unit. :army  :turkey ) :smy (Unit. :army  :turkey )}]
     (map->DiplomacyMap
      {:location-accessibility location-accessibility
       :edge-accessibility     edge-accessibility
       :colocated-locations    colocated-locations
       :supply-centers         supply-centers
       :home-supply-centers    home-supply-centers
       :initial-game-state (map->GameState
                            {:unit-positions starting-unit-positions
                             :supply-center-ownership home-supply-centers
                             :game-time (map->GameTime {:year 1901
                                                        :season :spring})})})))
