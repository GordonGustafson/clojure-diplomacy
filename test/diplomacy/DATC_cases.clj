(ns diplomacy.DATC-cases
  (:require [diplomacy.test-utils]
            [diplomacy.util :refer [defn-spec]]
            [diplomacy.datatypes :as dt]))

(def ^:private DATC-cases-raw
 {"6.A.1 MOVING TO AN AREA THAT IS NOT A NEIGHBOUR"
  {:summary "Check if an illegal move (without convoy) will fail."
   :validation-results {[:england :fleet :nth :attack :pic] [#{:attacks-via-inaccessible-edge?}
                                                             [:england :fleet :nth :hold]]}
   :conflict-judgments {[:england :fleet :nth :hold] #{}}
   :explanation "Order should fail."}
  "6.A.2. MOVE ARMY TO SEA"
  {:summary "Check if an army could not be moved to open sea."
   :validation-results {[:england :army :lvp :attack :iri] [#{:attacks-inaccessible-location? :attacks-via-inaccessible-edge?}
                                                             [:england :army :lvp :hold]]}
   :conflict-judgments {[:england :army :lvp :hold] #{}}
   :explanation "Order should fail."}
  "6.A.3. MOVE FLEET TO LAND"
  {:summary "Check whether a fleet can not move to land."
   :validation-results {[:germany :fleet :kie :attack :mun] [#{:attacks-inaccessible-location? :attacks-via-inaccessible-edge?}
                                                             [:germany :fleet :kie :hold]]}
   :conflict-judgments {[:germany :fleet :kie :hold] #{}}
   :explanation "Order should fail."}
  "6.A.4. MOVE TO OWN SECTOR"
  {:summary "Moving to the same sector is an illegal move (2000 rulebook, page 4, \"An Army can be ordered to move into an adjacent inland or coastal province.\")."
   :validation-results {[:germany :fleet :kie :attack :kie] [#{:attacks-current-location? :attacks-via-inaccessible-edge?}
                                                             [:germany :fleet :kie :hold]]}
   :conflict-judgments {[:germany :fleet :kie :hold] #{}}
   :explanation "Program should not crash."}
   ;; commented out because it uses a convoy
  #_"6.A.5. MOVE TO OWN SECTOR WITH CONVOY"
  #_{:summary "Moving to the same sector is still illegal with convoy (2000 rulebook, page 4, \"Note: An Army can move across water provinces from one coastal province to another...\")."
   :conflict-judgments {[:england :fleet :nth :convoy :england :army :yor :attack :yor] #{[:interfered? :interferer :rule]}
                        [:england :army :yor :attack :yor] #{[:interfered? :interferer :rule]}
                        [:england :army :lvp :support :england :army :yor :attack :yor] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :lon :attack :yor] #{[:interfered? :interferer :rule]}
                        [:germany :army :wal :support :germany :fleet :lon :attack :yor] #{[:interfered? :interferer :rule]}}
   :explanation "The move of the army in Yorkshire is illegal. This makes the support of Liverpool also illegal and without the support, the Germans have a stronger force. The army in London dislodges the army in Yorkshire."}
  #_"6.A.6. ORDERING A UNIT OF ANOTHER COUNTRY"
  #_{:summary "Check whether someone can not order a unit that is not his own unit. England has a fleet in London."
   :conflict-judgments {[:germany :fleet :lon :attack :nth] #{[:interfered? :interferer :rule]}}
   :explanation "Order should fail."}
   ;; commented out because it uses a convoy
  #_"6.A.7. ONLY ARMIES CAN BE CONVOYED"
  #_{:summary "A fleet can not be convoyed."
   :conflict-judgments {[:england :fleet :lon :attack :bel] #{[:interfered? :interferer :rule]}
                        [:england :fleet :nth :convoy :england :army :lon :attack :bel] #{[:interfered? :interferer :rule]}}
   :explanation "Move from London to Belgium should fail."}
  "6.A.8. SUPPORT TO HOLD YOURSELF IS NOT POSSIBLE"
  {:summary "An army can not get an additional hold power by supporting itself."
   :conflict-judgments {[:italy :army :ven :attack :tri] #{[:interfered? :interferer :rule]}
                        [:italy :army :tyr :support :italy :army :ven :attack :tri] #{[:interfered? :interferer :rule]}
                        [:austria :fleet :tri :support :austria :fleet :tri :hold] #{[:interfered? :interferer :rule]}}
   :explanation "The army in Trieste should be dislodged."}
  "6.A.9. FLEETS MUST FOLLOW COAST IF NOT ON SEA"
  {:summary "If two places are adjacent, that does not mean that a fleet can move between those two places. An implementation that only holds one list of adjacent places for each place, is incorrect."
   :conflict-judgments {[:italy :fleet :rom :attack :ven] #{[:interfered? :interferer :rule]}}
   :explanation "Move fails. An army can go from Rome to Venice, but a fleet can not."}
  "6.A.10. SUPPORT ON UNREACHABLE DESTINATION NOT POSSIBLE"
  {:summary "The destination of the move that is supported must be reachable by the supporting unit."
   :conflict-judgments {[:austria :army :ven :hold] #{}
                        [:italy :fleet :rom :support :italy :army :apu :attack :ven] #{[:interfered? :interferer :rule]}
                        [:italy :army :apu :attack :ven] #{[:interfered? :interferer :rule]}}
   :explanation "The support of Rome is illegal, because Venice can not be reached from Rome by a fleet. Venice is not dislodged."}
  "6.A.11. SIMPLE BOUNCE"
  {:summary "Two armies bouncing on each other."
   :conflict-judgments {[:austria :army :vie :attack :tyr] #{[:interfered? :interferer :rule]}
                        [:italy :army :ven :attack :tyr] #{[:interfered? :interferer :rule]}}
   :explanation "The two units bounce."}
  "6.A.12. BOUNCE OF THREE UNITS"
  {:summary "If three units move to the same place, the adjudicator should not bounce the first two units and then let the third unit go to the now open place."
   :conflict-judgments {[:austria :army :vie :attack :tyr] #{[:interfered? :interferer :rule]}
                        [:germany :army :mun :attack :tyr] #{[:interfered? :interferer :rule]}
                        [:italy :army :ven :attack :tyr] #{[:interfered? :interferer :rule]}}
   :explanation "The three units bounce."}
 "6.B.1. MOVING WITH UNSPECIFIED COAST WHEN COAST IS NECESSARY"
  {:summary "Coast is significant in this case:"
   :conflict-judgments {[:france :fleet :por :attack :spa] #{[:interfered? :interferer :rule]}}
   :explanation "Some adjudicators take a default coast (see issue 4.B.1). <i>I prefer that the move fails.</i>"}
  "6.B.2. MOVING WITH UNSPECIFIED COAST WHEN COAST IS NOT NECESSARY"
  {:summary "There is only one coast possible in this case:"
   :conflict-judgments {[:france :fleet :gas :attack :spa] #{[:interfered? :interferer :rule]}}
   :explanation "Since the North Coast is the only coast that can be reached, it seems logical that the a move is attempted to the north coast of Spain. Some adjudicators require that a coast is also specified in this case and will decide that the move fails or take a default coast (see issue 4.B.2). <i>I prefer that an attempt is made to the only possible coast, the north coast of Spain.</i>"}
  ;; comment out because it uses a coast
  #_"6.B.3. MOVING WITH WRONG COAST WHEN COAST IS NOT NECESSARY"
  #_{:summary "If only one coast is possible, but the wrong coast can be specified."
   :conflict-judgments {[:france :fleet :gas :attack :spa-sc] #{[:interfered? :interferer :rule]}}
   :explanation "If the rules are played very clemently, a move will be attempted to the north coast of Spain. However, since this order is very clear and precise, it is more common that the move fails (see issue 4.B.3). <i>I prefer that the move fails.</i>"}
  ;; comment out because it uses a coast
  #_"6.B.4. SUPPORT TO UNREACHABLE COAST ALLOWED"
  #_{:summary "A fleet can give support to a coast where it can not go."
   :conflict-judgments {[:france :fleet :gas :attack :spa-nc] #{[:interfered? :interferer :rule]}
                        [:france :fleet :mar :support :france :fleet :gas :attack :spa-nc] #{[:interfered? :interferer :rule]}
                        [:italy :fleet :wes :attack :spa-sc] #{[:interfered? :interferer :rule]}}
   :explanation "Although the fleet in Marseilles can not go to the north coast it can still support targeting the north coast. So, the support is successful, the move of the fleet in Gasgony succeeds and the move of the Italian fleet fails."}
  ;; comment out because it uses a coast
  #_"6.B.5. SUPPORT FROM UNREACHABLE COAST NOT ALLOWED"
  #_{:summary "A fleet can not give support to an area that can not be reached from the current coast of the fleet."
   :conflict-judgments {[:france :fleet :mar :attack :gol] #{[:interfered? :interferer :rule]}
                        [:france :fleet :spa-nc :support :france :fleet :mar :attack :gol] #{[:interfered? :interferer :rule]}
                        [:italy :fleet :gol :hold] #{}}
   :explanation "The Gulf of Lyon can not be reached from the North Coast of Spain. Therefore, the support of Spain is invalid and the fleet in the Gulf of Lyon is not dislodged."}
  ;; comment out because it uses a coast
  #_"6.B.6. SUPPORT CAN BE CUT WITH OTHER COAST"
  #_{:summary "Support can be cut from the other coast."
   :conflict-judgments {[:england :fleet :iri :support :england :fleet :nat :attack :mid] #{[:interfered? :interferer :rule]}
                        [:england :fleet :nat :attack :mid] #{[:interfered? :interferer :rule]}
                        [:france :fleet :spa-nc :support :france :fleet :mid :hold] #{[:interfered? :interferer :rule]}
                        [:france :fleet :mid :hold] #{}
                        [:italy :fleet :gol :attack :spa-sc] #{[:interfered? :interferer :rule]}}
   :explanation "The Italian fleet in the Gulf of Lyon will cut the support in Spain. That means that the French fleet in the Mid Atlantic Ocean will be dislodged by the English fleet in the North Atlantic Ocean."}
  ;; comment out because it uses a coast
  #_"6.B.7. SUPPORTING WITH UNSPECIFIED COAST"
  #_{:summary "Most house rules accept support orders without coast specification."
   :conflict-judgments {[:france :fleet :por :support :france :fleet :mid :attack :spa] #{[:interfered? :interferer :rule]}
                        [:france :fleet :mid :attack :spa-nc] #{[:interfered? :interferer :rule]}
                        [:italy :fleet :gol :support :italy :fleet :wes :attack :spa-sc] #{[:interfered? :interferer :rule]}
                        [:italy :fleet :wes :attack :spa-sc] #{[:interfered? :interferer :rule]}}
   :explanation "See issue 4.B.4. If coasts are not required in support orders, then the support of Portugal is successful. This means that the Italian fleet in the Western Mediterranean bounces. Some adjudicators may not accept a support order without coast (the support will fail or a default coast is taken). In that case the support order of Portugal fails (in case of a default coast the coast will probably the south coast) and the Italian fleet in the Western Mediterranean will successfully move. <i>I prefer that the support succeeds and the Italian fleet in the Western Mediterranean bounces.</i>"}
  ;; comment out because it uses a coast
  #_"6.B.8. SUPPORTING WITH UNSPECIFIED COAST WHEN ONLY ONE COAST IS POSSIBLE"
  #_{:summary "Some hardliners require a coast in a support order even when only  one coast is possible."
   :conflict-judgments {[:france :fleet :por :support :france :fleet :gas :attack :spa] #{[:interfered? :interferer :rule]}
                        [:france :fleet :gas :attack :spa-nc] #{[:interfered? :interferer :rule]}
                        [:italy :fleet :gol :support :italy :fleet :wes :attack :spa-sc] #{[:interfered? :interferer :rule]}
                        [:italy :fleet :wes :attack :spa-sc] #{[:interfered? :interferer :rule]}}
   :explanation "See issue 4.B.4. If coasts are not required in support orders, then the support of Portugal is successful. This means that the Italian fleet in the Western Mediterranean bounces. Some adjudicators may not accept a support order without coast (the support will fail or a default coast is taken). In that case the support order of Portugal fails (in case of a default coast the coast will probably the south coast) and the Italian fleet in the Western Mediterranean will successfully move. <i>I prefer that supporting without coasts should be allowed. So I prefer that the support of Portugal is successful and that the Italian fleet in the Western Mediterranean bounces.</i>"}
  ;; comment out because it uses a coast
  #_"6.B.9. SUPPORTING WITH WRONG COAST"
  #_{:summary "Coasts can be specified in a support, but the result depends on the house rules."
   :conflict-judgments {[:france :fleet :por :support :france :fleet :mid :attack :spa-nc] #{[:interfered? :interferer :rule]}
                        [:france :fleet :mid :attack :spa-sc] #{[:interfered? :interferer :rule]}
                        [:italy :fleet :gol :support :italy :fleet :wes :attack :spa-sc] #{[:interfered? :interferer :rule]}
                        [:italy :fleet :wes :attack :spa-sc] #{[:interfered? :interferer :rule]}}
   :explanation "See issue 4.B.4. If it is required that the coast matches, then the support of the French fleet in the Mid-Atlantic Ocean fails and that the Italian fleet in the Western Mediterranean moves successfully. Some adjudicators ignores the coasts in support orders. In that case, the move of the Italian fleet bounces. <i>I prefer that the support fails and that the Italian fleet in the  Western Mediterranean moves successfully.</i>"}
  ;; comment out because it uses a coast
  #_"6.B.10. UNIT ORDERED WITH WRONG COAST"
  #_{:summary "A player might specify the wrong coast for the ordered unit. France has a fleet on the south coast of Spain and orders:"
   :conflict-judgments {[:france :fleet :spa-nc :attack :gol] #{[:interfered? :interferer :rule]}}
   :explanation "If only perfect orders are accepted, then the move will fail, but since the coast for the ordered unit has no purpose, it might also be ignored (see issue 4.B.5). <i>I prefer that a move will be attempted.</i>"}
  ;; comment out because it uses a coast
  #_"6.B.11. COAST CAN NOT BE ORDERED TO CHANGE"
  #_{:summary "The coast can not change by just ordering the other coast. France has a fleet on the north coast of Spain and orders:"
   :conflict-judgments {[:france :fleet :spa-sc :attack :gol] #{[:interfered? :interferer :rule]}}
   :explanation "The move fails."}
  ;; comment out because it uses a coast
  #_"6.B.12. ARMY MOVEMENT WITH COASTAL SPECIFICATION"
  #_{:summary "For armies the coasts are irrelevant:"
   :conflict-judgments {[:france :army :gas :attack :spa-nc] #{[:interfered? :interferer :rule]}}
   :explanation "If only perfect orders are accepted, then the move will fail. But it is also possible that coasts are ignored in this case and a move will be attempted (see issue 4.B.6). <i>I prefer that a move will be attempted.</i>"}
  ;; comment out because it uses a coast
  #_"6.B.13. COASTAL CRAWL NOT ALLOWED"
  #_{:summary "If a fleet is leaving a sector from a certain coast while in the opposite direction another fleet is moving to another coast of the sector, it is still a head to head battle. This has been decided in the great revision of the 1961 rules that resulted in the 1971 rules."
   :conflict-judgments {[:turkey :fleet :bul-sc :attack :con] #{[:interfered? :interferer :rule]}
                        [:turkey :fleet :con :attack :bul-ec] #{[:interfered? :interferer :rule]}}
   :explanation "Both moves fail."}
 "6.C.1. THREE ARMY CIRCULAR MOVEMENT"
  {:summary "Three units can change place, even in spring 1901."
   :conflict-judgments {[:turkey :fleet :ank :attack :con] #{[:interfered? :interferer :rule]}
                        [:turkey :army :con :attack :smy] #{[:interfered? :interferer :rule]}
                        [:turkey :army :smy :attack :ank] #{[:interfered? :interferer :rule]}}
   :explanation "All three units will move."}
  "6.C.2. THREE ARMY CIRCULAR MOVEMENT WITH SUPPORT"
  {:summary "Three units can change place, even when one gets support."
   :conflict-judgments {[:turkey :fleet :ank :attack :con] #{[:interfered? :interferer :rule]}
                        [:turkey :army :con :attack :smy] #{[:interfered? :interferer :rule]}
                        [:turkey :army :smy :attack :ank] #{[:interfered? :interferer :rule]}
                        [:turkey :army :bul :support :turkey :fleet :ank :attack :con] #{[:interfered? :interferer :rule]}}
   :explanation "Of course the three units will move, but knowing how programs are written, this can confuse the adjudicator."}
  "6.C.3. A DISRUPTED THREE ARMY CIRCULAR MOVEMENT"
  {:summary "When one of the units bounces, the whole circular movement will hold."
   :conflict-judgments {[:turkey :fleet :ank :attack :con] #{[:interfered? :interferer :rule]}
                        [:turkey :army :con :attack :smy] #{[:interfered? :interferer :rule]}
                        [:turkey :army :smy :attack :ank] #{[:interfered? :interferer :rule]}
                        [:turkey :army :bul :attack :con] #{[:interfered? :interferer :rule]}}
   :explanation "Every unit will keep its place."}
  ;; commented out because it uses a convoy
  #_"6.C.4. A CIRCULAR MOVEMENT WITH ATTACKED CONVOY"
  #_{:summary "When the circular movement contains an attacked convoy, the circular movement succeeds. The adjudication algorithm should handle attack of convoys before calculating circular movement."
   :conflict-judgments {[:austria :army :tri :attack :ser] #{[:interfered? :interferer :rule]}
                        [:austria :army :ser :attack :bul] #{[:interfered? :interferer :rule]}
                        [:turkey :army :bul :attack :tri] #{[:interfered? :interferer :rule]}
                        [:turkey :fleet :aeg :convoy :turkey :army :bul :attack :tri] #{[:interfered? :interferer :rule]}
                        [:turkey :fleet :ion :convoy :turkey :army :bul :attack :tri] #{[:interfered? :interferer :rule]}
                        [:turkey :fleet :adr :convoy :turkey :army :bul :attack :tri] #{[:interfered? :interferer :rule]}
                        [:italy :fleet :nap :attack :ion] #{[:interfered? :interferer :rule]}}
   :explanation "The fleet in the Ionian Sea is attacked but not dislodged. The circular movement succeeds. The Austrian and Turkish armies will advance."}
  ;; commented out because it uses a convoy
  #_"6.C.5. A DISRUPTED CIRCULAR MOVEMENT DUE TO DISLODGED CONVOY"
  #_{:summary "When the circular movement contains a convoy, the circular movement is disrupted when the convoying fleet is dislodged. The adjudication algorithm should disrupt convoys before calculating circular movement."
   :conflict-judgments {[:austria :army :tri :attack :ser] #{[:interfered? :interferer :rule]}
                        [:austria :army :ser :attack :bul] #{[:interfered? :interferer :rule]}
                        [:turkey :army :bul :attack :tri] #{[:interfered? :interferer :rule]}
                        [:turkey :fleet :aeg :convoy :turkey :army :bul :attack :tri] #{[:interfered? :interferer :rule]}
                        [:turkey :fleet :ion :convoy :turkey :army :bul :attack :tri] #{[:interfered? :interferer :rule]}
                        [:turkey :fleet :adr :convoy :turkey :army :bul :attack :tri] #{[:interfered? :interferer :rule]}
                        [:italy :fleet :nap :attack :ion] #{[:interfered? :interferer :rule]}
                        [:italy :fleet :tun :support :italy :fleet :nap :attack :ion] #{[:interfered? :interferer :rule]}}
   :explanation "Due to the dislodged convoying fleet, all Austrian and Turkish armies will not move."}
  ;; commented out because it uses a convoy
  #_"6.C.6. TWO ARMIES WITH TWO CONVOYS"
  #_{:summary "Two armies can swap places even when they are not adjacent."
   :conflict-judgments {[:england :fleet :nth :convoy :england :army :lon :attack :bel] #{[:interfered? :interferer :rule]}
                        [:england :army :lon :attack :bel] #{[:interfered? :interferer :rule]}
                        [:france :fleet :eng :convoy :france :army :bel :attack :lon] #{[:interfered? :interferer :rule]}
                        [:france :army :bel :attack :lon] #{[:interfered? :interferer :rule]}}
   :explanation "Both convoys should succeed."}
  ;; commented out because it uses a convoy
  #_"6.C.7. DISRUPTED UNIT SWAP"
  #_{:summary "If in a swap one of the unit bounces, then the swap fails."
   :conflict-judgments {[:england :fleet :nth :convoy :england :army :lon :attack :bel] #{[:interfered? :interferer :rule]}
                        [:england :army :lon :attack :bel] #{[:interfered? :interferer :rule]}
                        [:france :fleet :eng :convoy :france :army :bel :attack :lon] #{[:interfered? :interferer :rule]}
                        [:france :army :bel :attack :lon] #{[:interfered? :interferer :rule]}
                        [:france :army :bur :attack :bel] #{[:interfered? :interferer :rule]}}
   :explanation "None of the units will succeed to move."}
 "6.D.1. SUPPORTED HOLD CAN PREVENT DISLODGEMENT"
  {:summary "The most simple support to hold order."
   :conflict-judgments {[:austria :fleet :adr :support :austria :army :tri :attack :ven] #{[:interfered? :interferer :rule]}
                        [:austria :army :tri :attack :ven] #{[:interfered? :interferer :rule]}
                        [:italy :army :ven :hold] #{}
                        [:italy :army :tyr :support :italy :army :ven :hold] #{[:interfered? :interferer :rule]}}
   :explanation "The support of Tyrolia prevents that the army in Venice is dislodged. The army in Trieste will not move."}
  "6.D.2. A MOVE CUTS SUPPORT ON HOLD"
  {:summary "The most simple support on hold cut."
   :conflict-judgments {[:austria :fleet :adr :support :austria :army :tri :attack :ven] #{[:interfered? :interferer :rule]}
                        [:austria :army :tri :attack :ven] #{[:interfered? :interferer :rule]}
                        [:austria :army :vie :attack :tyr] #{[:interfered? :interferer :rule]}
                        [:italy :army :ven :hold] #{}
                        [:italy :army :tyr :support :italy :army :ven :hold] #{[:interfered? :interferer :rule]}}
   :explanation "The support of Tyrolia is cut by the army in Vienna. That means that the army in Venice is dislodged by the army from Trieste."}
  "6.D.3. A MOVE CUTS SUPPORT ON MOVE"
  {:summary "The most simple support on move cut."
   :conflict-judgments {[:austria :fleet :adr :support :austria :army :tri :attack :ven] #{[:interfered? :interferer :rule]}
                        [:austria :army :tri :attack :ven] #{[:interfered? :interferer :rule]}
                        [:italy :army :ven :hold] #{}
                        [:italy :fleet :ion :attack :adr] #{[:interfered? :interferer :rule]}}
   :explanation "The support of the fleet in the Adriatic Sea is cut. That means that the army in Venice will not be dislodged and the army in Trieste stays in Trieste."}
  "6.D.4. SUPPORT TO HOLD ON UNIT SUPPORTING A HOLD ALLOWED"
  {:summary "A unit that is supporting a hold, can receive a hold support."
   :conflict-judgments {[:germany :army :ber :support :germany :fleet :kie :hold] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :kie :support :germany :army :ber :hold] #{[:interfered? :interferer :rule]}
                        [:russia :fleet :bal :support :russia :army :pru :attack :ber] #{[:interfered? :interferer :rule]}
                        [:russia :army :pru :attack :ber] #{[:interfered? :interferer :rule]}}
   :explanation "The Russian move from Prussia to Berlin fails."}
  "6.D.5. SUPPORT TO HOLD ON UNIT SUPPORTING A MOVE ALLOWED"
  {:summary "A unit that is supporting a move, can receive a hold support."
   :conflict-judgments {[:germany :army :ber :support :germany :army :mun :attack :sil] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :kie :support :germany :army :ber :hold] #{[:interfered? :interferer :rule]}
                        [:germany :army :mun :attack :sil] #{[:interfered? :interferer :rule]}
                        [:russia :fleet :bal :support :russia :army :pru :attack :ber] #{[:interfered? :interferer :rule]}
                        [:russia :army :pru :attack :ber] #{[:interfered? :interferer :rule]}}
   :explanation "The Russian move from Prussia to Berlin fails."}
  ;; commented out because it uses a convoy
  #_"6.D.6. SUPPORT TO HOLD ON CONVOYING UNIT ALLOWED"
  #_{:summary "A unit that is convoying, can receive a hold support."
   :conflict-judgments {[:germany :army :ber :attack :swe] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :bal :convoy :germany :army :ber :attack :swe] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :pru :support :germany :fleet :bal :hold] #{[:interfered? :interferer :rule]}
                        [:russia :fleet :lvn :attack :bal] #{[:interfered? :interferer :rule]}
                        [:russia :fleet :bot :support :russia :fleet :lvn :attack :bal] #{[:interfered? :interferer :rule]}}
   :explanation "The Russian move from Livonia to the Baltic Sea fails. The convoy from Berlin to Sweden succeeds."}
  "6.D.7. SUPPORT TO HOLD ON MOVING UNIT NOT ALLOWED"
  {:summary "A unit that is moving, can not receive a hold support for the situation that the move fails."
   :conflict-judgments {[:germany :fleet :bal :attack :swe] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :pru :support :germany :fleet :bal :hold] #{[:interfered? :interferer :rule]}
                        [:russia :fleet :lvn :attack :bal] #{[:interfered? :interferer :rule]}
                        [:russia :fleet :bot :support :russia :fleet :lvn :attack :bal] #{[:interfered? :interferer :rule]}
                        [:russia :army :fin :attack :swe] #{[:interfered? :interferer :rule]}}
   :explanation "The support of the fleet in Prussia fails. The fleet in Baltic Sea will bounce on the Russian army in Finland and will be dislodged by the Russian fleet from Livonia when it returns to the Baltic Sea."}
  "6.D.8. FAILED CONVOY CAN NOT RECEIVE HOLD SUPPORT"
  {:summary "If a convoy fails because of disruption of the convoy or when the right convoy orders are not given, then the army to be convoyed can not receive support in  hold, since it still tried to move."
   :conflict-judgments {[:austria :fleet :ion :hold] #{}
                        [:austria :army :ser :support :austria :army :alb :attack :gre] #{[:interfered? :interferer :rule]}
                        [:austria :army :alb :attack :gre] #{[:interfered? :interferer :rule]}
                        [:turkey :army :gre :attack :nap] #{[:interfered? :interferer :rule]}
                        [:turkey :army :bul :support :turkey :army :gre :hold] #{[:interfered? :interferer :rule]}}
   :explanation "There was a possible convoy from Greece to Naples, before the orders were made public (via the Ionian Sea). This means that the order of Greece to Naples should never be treated as illegal order and be changed in a hold order able to receive hold support (see also issue VI.A). Therefore, the support in Bulgaria fails and the army in Greece is dislodged by the army in Albania."}
  "6.D.9. SUPPORT TO MOVE ON HOLDING UNIT NOT ALLOWED"
  {:summary "A unit that is holding can not receive a support in moving."
   :conflict-judgments {[:italy :army :ven :attack :tri] #{[:interfered? :interferer :rule]}
                        [:italy :army :tyr :support :italy :army :ven :attack :tri] #{[:interfered? :interferer :rule]}
                        [:austria :army :alb :support :austria :army :tri :attack :ser] #{[:interfered? :interferer :rule]}
                        [:austria :army :tri :hold] #{}}
   :explanation "The support of the army in Albania fails and the army in Trieste is dislodged by the army from Venice."}
  "6.D.10. SELF DISLODGMENT PROHIBITED"
  {:summary "A unit may not dislodge a unit of the same great power."
   :conflict-judgments {[:germany :army :ber :hold] #{}
                        [:germany :fleet :kie :attack :ber] #{[:interfered? :interferer :rule]}
                        [:germany :army :mun :support :germany :fleet :kie :attack :ber] #{[:interfered? :interferer :rule]}}
   :explanation "Move to Berlin fails."}
  "6.D.11. NO SELF DISLODGMENT OF RETURNING UNIT"
  {:summary "Idem."
   :conflict-judgments {[:germany :army :ber :attack :pru] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :kie :attack :ber] #{[:interfered? :interferer :rule]}
                        [:germany :army :mun :support :germany :fleet :kie :attack :ber] #{[:interfered? :interferer :rule]}
                        [:russia :army :war :attack :pru] #{[:interfered? :interferer :rule]}}
   :explanation "Army in Berlin bounces, but is not dislodged by own unit."}
  "6.D.12. SUPPORTING A FOREIGN UNIT TO DISLODGE OWN UNIT PROHIBITED"
  {:summary "You may not help another power in dislodging your own unit."
   :conflict-judgments {[:austria :fleet :tri :hold] #{}
                        [:austria :army :vie :support :italy :army :ven :attack :tri] #{[:interfered? :interferer :rule]}
                        [:italy :army :ven :attack :tri] #{[:interfered? :interferer :rule]}}
   :explanation "No dislodgment of fleet in Trieste."}
  "6.D.13. SUPPORTING A FOREIGN UNIT TO DISLODGE A RETURNING OWN UNIT PROHIBITED"
  {:summary "Idem."
   :conflict-judgments {[:austria :fleet :tri :attack :adr] #{[:interfered? :interferer :rule]}
                        [:austria :army :vie :support :italy :army :ven :attack :tri] #{[:interfered? :interferer :rule]}
                        [:italy :army :ven :attack :tri] #{[:interfered? :interferer :rule]}
                        [:italy :fleet :apu :attack :adr] #{[:interfered? :interferer :rule]}}
   :explanation "No dislodgment of fleet in Trieste."}
  "6.D.14. SUPPORTING A FOREIGN UNIT IS NOT ENOUGH TO PREVENT DISLODGEMENT"
  {:summary "If a foreign unit has enough support to dislodge your unit, you may not prevent that dislodgement by supporting the attack."
   :conflict-judgments {[:austria :fleet :tri :hold] #{}
                        [:austria :army :vie :support :italy :army :ven :attack :tri] #{[:interfered? :interferer :rule]}
                        [:italy :army :ven :attack :tri] #{[:interfered? :interferer :rule]}
                        [:italy :army :tyr :support :italy :army :ven :attack :tri] #{[:interfered? :interferer :rule]}
                        [:italy :fleet :adr :support :italy :army :ven :attack :tri] #{[:interfered? :interferer :rule]}}
   :explanation "The fleet in Trieste is dislodged."}
  "6.D.15. DEFENDER CAN NOT CUT SUPPORT FOR ATTACK ON ITSELF"
  {:summary "A unit that is attacked by a supported unit can not prevent dislodgement by guessing which of the units will do the support."
   :conflict-judgments {[:russia :fleet :con :support :russia :fleet :bla :attack :ank] #{[:interfered? :interferer :rule]}
                        [:russia :fleet :bla :attack :ank] #{[:interfered? :interferer :rule]}
                        [:turkey :fleet :ank :attack :con] #{[:interfered? :interferer :rule]}}
   :explanation "The support of Constantinople is not cut and the fleet in Ankara is dislodged by the fleet in the Black Sea."}
  ;; commented out because it uses a convoy
  #_"6.D.16. CONVOYING A UNIT DISLODGING A UNIT OF SAME POWER IS ALLOWED"
  #_{:summary "It is allowed to convoy a foreign unit that dislodges your own unit is allowed."
   :conflict-judgments {[:england :army :lon :hold] #{}
                        [:england :fleet :nth :convoy :france :army :bel :attack :lon] #{[:interfered? :interferer :rule]}
                        [:france :fleet :eng :support :france :army :bel :attack :lon] #{[:interfered? :interferer :rule]}
                        [:france :army :bel :attack :lon] #{[:interfered? :interferer :rule]}}
   :explanation "The English army in London is dislodged by the French army coming from Belgium."}
  "6.D.17. DISLODGEMENT CUTS SUPPORTS"
  {:summary "The famous dislodge rule."
   :conflict-judgments {[:russia :fleet :con :support :russia :fleet :bla :attack :ank] #{[:interfered? :interferer :rule]}
                        [:russia :fleet :bla :attack :ank] #{[:interfered? :interferer :rule]}
                        [:turkey :fleet :ank :attack :con] #{[:interfered? :interferer :rule]}
                        [:turkey :army :smy :support :turkey :fleet :ank :attack :con] #{[:interfered? :interferer :rule]}
                        [:turkey :army :arm :attack :ank] #{[:interfered? :interferer :rule]}}
   :explanation "The Russian fleet in Constantinople is dislodged. This cuts the support to from Black Sea to Ankara. Black Sea will bounce with the army from Armenia."}
  "6.D.18. A SURVIVING UNIT WILL SUSTAIN SUPPORT"
  {:summary "Idem. But now with an additional hold that prevents dislodgement."
   :conflict-judgments {[:russia :fleet :con :support :russia :fleet :bla :attack :ank] #{[:interfered? :interferer :rule]}
                        [:russia :fleet :bla :attack :ank] #{[:interfered? :interferer :rule]}
                        [:russia :army :bul :support :russia :fleet :con :hold] #{[:interfered? :interferer :rule]}
                        [:turkey :fleet :ank :attack :con] #{[:interfered? :interferer :rule]}
                        [:turkey :army :smy :support :turkey :fleet :ank :attack :con] #{[:interfered? :interferer :rule]}
                        [:turkey :army :arm :attack :ank] #{[:interfered? :interferer :rule]}}
   :explanation "The Russian fleet in the Black Sea will dislodge the Turkish fleet in Ankara."}
  "6.D.19. EVEN WHEN SURVIVING IS IN ALTERNATIVE WAY"
  {:summary "Now, the dislodgement is prevented because the supports comes from a Russian army:"
   :conflict-judgments {[:russia :fleet :con :support :russia :fleet :bla :attack :ank] #{[:interfered? :interferer :rule]}
                        [:russia :fleet :bla :attack :ank] #{[:interfered? :interferer :rule]}
                        [:russia :army :smy :support :turkey :fleet :ank :attack :con] #{[:interfered? :interferer :rule]}
                        [:turkey :fleet :ank :attack :con] #{[:interfered? :interferer :rule]}}
   :explanation "The Russian fleet in Constantinople is not dislodged, because one of the support is of Russian origin. The support from Black Sea to Ankara will sustain and the fleet in Ankara will be dislodged."}
  "6.D.20. UNIT CAN NOT CUT SUPPORT OF ITS OWN COUNTRY"
  {:summary "Although this is not mentioned in all rulebooks, it is generally accepted that when a unit attacks another unit of the same Great Power, it will not cut support."
   :conflict-judgments {[:england :fleet :lon :support :england :fleet :nth :attack :eng] #{[:interfered? :interferer :rule]}
                        [:england :fleet :nth :attack :eng] #{[:interfered? :interferer :rule]}
                        [:england :army :yor :attack :lon] #{[:interfered? :interferer :rule]}
                        [:france :fleet :eng :hold] #{}}
   :explanation "The army in York does not cut support. This means that the fleet in the English Channel is dislodged by the fleet in the North Sea."}
  "6.D.21. DISLODGING DOES NOT CANCEL A SUPPORT CUT"
  {:summary "Sometimes there is the question whether a dislodged moving unit does not cut support (similar to the dislodge rule). This is not the case."
   :conflict-judgments {[:austria :fleet :tri :hold] #{}
                        [:italy :army :ven :attack :tri] #{[:interfered? :interferer :rule]}
                        [:italy :army :tyr :support :italy :army :ven :attack :tri] #{[:interfered? :interferer :rule]}
                        [:germany :army :mun :attack :tyr] #{[:interfered? :interferer :rule]}
                        [:russia :army :sil :attack :mun] #{[:interfered? :interferer :rule]}
                        [:russia :army :ber :support :russia :army :sil :attack :mun] #{[:interfered? :interferer :rule]}}
   :explanation "Although the German army is dislodged, it still cuts the Italian support. That means that the Austrian Fleet is not dislodged."}
  "6.D.22. IMPOSSIBLE FLEET MOVE CAN NOT BE SUPPORTED"
  {:summary "If a fleet tries moves to a land area it seems pointless to support the fleet, since the move will fail anyway. However, in such case, the support is also invalid for defense purposes."
   :conflict-judgments {[:germany :fleet :kie :attack :mun] #{[:interfered? :interferer :rule]}
                        [:germany :army :bur :support :germany :fleet :kie :attack :mun] #{[:interfered? :interferer :rule]}
                        [:russia :army :mun :attack :kie] #{[:interfered? :interferer :rule]}
                        [:russia :army :ber :support :russia :army :mun :attack :kie] #{[:interfered? :interferer :rule]}}
   :explanation "The German move from Kiel to Munich is illegal (fleets can not go to Munich). Therefore, the support from Burgundy fails and the Russian army in Munich will dislodge the fleet in Kiel. Note that the failing of the support is not explicitly mentioned in the rulebooks (the DPTG is more clear about this point). If you take the rulebooks very literally, you might conclude that the fleet in Munich is not dislodged, but this is an incorrect interpretation."}
  ;; comment out because it uses a coast
  #_"6.D.23. IMPOSSIBLE COAST MOVE CAN NOT BE SUPPORTED"
  #_{:summary "Comparable with the previous test case, but now the fleet move is impossible for coastal reasons."
   :conflict-judgments {[:italy :fleet :gol :attack :spa-sc] #{[:interfered? :interferer :rule]}
                        [:italy :fleet :wes :support :italy :fleet :gol :attack :spa-sc] #{[:interfered? :interferer :rule]}
                        [:france :fleet :spa-nc :attack :gol] #{[:interfered? :interferer :rule]}
                        [:france :fleet :mar :support :france :fleet :spa-nc :attack :gol] #{[:interfered? :interferer :rule]}}
   :explanation "The French move from Spain North Coast to Gulf of Lyon is illegal (wrong coast). Therefore, the support from Marseilles fails and the fleet in Spain is dislodged."}
  ;; comment out because it uses a coast
  #_"6.D.24. IMPOSSIBLE ARMY MOVE CAN NOT BE SUPPORTED"
  #_{:summary "Comparable with the previous test case, but now an army tries to move into sea and the support is used in a beleaguered garrison."
   :conflict-judgments {[:france :army :mar :attack :gol] #{[:interfered? :interferer :rule]}
                        [:france :fleet :spa-sc :support :france :army :mar :attack :gol] #{[:interfered? :interferer :rule]}
                        [:italy :fleet :gol :hold] #{}
                        [:turkey :fleet :tyn :support :turkey :fleet :wes :attack :gol] #{[:interfered? :interferer :rule]}
                        [:turkey :fleet :wes :attack :gol] #{[:interfered? :interferer :rule]}}
   :explanation "The French move from Marseilles to Gulf of Lyon is illegal (an army can not go to sea). Therefore, the support from Spain fails and there is no beleaguered garrison. The fleet in the Gulf of Lyon is dislodged by the Turkish fleet in the Western Mediterranean."}
  "6.D.25. FAILING HOLD SUPPORT CAN BE SUPPORTED"
  {:summary "If an adjudicator fails on one of the previous three test cases, then the bug should be removed with care. A failing move can not be supported, but a failing hold support, because of some preconditions (unmatching order) can still be supported."
   :conflict-judgments {[:germany :army :ber :support :russia :army :pru :hold] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :kie :support :germany :army :ber :hold] #{[:interfered? :interferer :rule]}
                        [:russia :fleet :bal :support :russia :army :pru :attack :ber] #{[:interfered? :interferer :rule]}
                        [:russia :army :pru :attack :ber] #{[:interfered? :interferer :rule]}}
   :explanation "Although the support of Berlin on Prussia fails (because of unmatching orders), the support of Kiel on Berlin is still valid. So, Berlin will not be dislodged."}
  "6.D.26. FAILING MOVE SUPPORT CAN BE SUPPORTED"
  {:summary "Similar as the previous test case, but now with an unmatched support to move."
   :conflict-judgments {[:germany :army :ber :support :russia :army :pru :attack :sil] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :kie :support :germany :army :ber :hold] #{[:interfered? :interferer :rule]}
                        [:russia :fleet :bal :support :russia :army :pru :attack :ber] #{[:interfered? :interferer :rule]}
                        [:russia :army :pru :attack :ber] #{[:interfered? :interferer :rule]}}
   :explanation "Again, Berlin will not be dislodged."}
  ;; commented out because it uses a convoy
  #_"6.D.27. FAILING CONVOY CAN BE SUPPORTED"
  #_{:summary "Similar as the previous test case, but now with an unmatched convoy."
   :conflict-judgments {[:england :fleet :swe :attack :bal] #{[:interfered? :interferer :rule]}
                        [:england :fleet :den :support :england :fleet :swe :attack :bal] #{[:interfered? :interferer :rule]}
                        [:germany :army :ber :hold] #{}
                        [:russia :fleet :bal :convoy :germany :army :ber :attack :lvn] #{[:interfered? :interferer :rule]}
                        [:russia :fleet :pru :support :russia :fleet :bal :hold] #{[:interfered? :interferer :rule]}}
   :explanation "The convoy order in the Baltic Sea is unmatched and fails. However, the support of Prussia on the Baltic Sea is still valid and the fleet in the Baltic Sea is not dislodged."}
  "6.D.28. IMPOSSIBLE MOVE AND SUPPORT"
  {:summary "If a move is impossible then it can be treated as \"illegal\", which makes a hold support possible."
   :conflict-judgments {[:austria :army :bud :support :russia :fleet :rum :hold] #{[:interfered? :interferer :rule]}
                        [:russia :fleet :rum :attack :hol] #{[:interfered? :interferer :rule]}
                        [:turkey :fleet :bla :attack :rum] #{[:interfered? :interferer :rule]}
                        [:turkey :army :bul :support :turkey :fleet :bla :attack :rum] #{[:interfered? :interferer :rule]}}
   :explanation "The move of the Russian fleet is impossible. But the question is,  whether it is \"illegal\" (see issue 4.E.1). If the move is \"illegal\" it must be ignored and that makes the hold support of the army in Budapest valid and the fleet in Rumania will not be dislodged. <i>I prefer that the move is \"illegal\", which means that the fleet in the Black Sea does not dislodge the supported Russian fleet.</i>"}
  ;; comment out because it uses a coast
  #_"6.D.29. MOVE TO IMPOSSIBLE COAST AND SUPPORT"
  #_{:summary "Similar to the previous test case, but now the move can be \"illegal\" because of the wrong coast."
   :conflict-judgments {[:austria :army :bud :support :russia :fleet :rum :hold] #{[:interfered? :interferer :rule]}
                        [:russia :fleet :rum :attack :bul-sc] #{[:interfered? :interferer :rule]}
                        [:turkey :fleet :bla :attack :rum] #{[:interfered? :interferer :rule]}
                        [:turkey :army :bul :support :turkey :fleet :bla :attack :rum] #{[:interfered? :interferer :rule]}}
   :explanation "Again the move of the Russian fleet is impossible. However, some people might correct the coast (see issue 4.B.3).  If the coast is not corrected, again the question is  whether it is \"illegal\" (see issue 4.E.1). If the move is \"illegal\" it must be ignored and that makes the hold support of the army in Budapest valid and the fleet in Rumania will not be dislodged. <i>I prefer that unambiguous orders are not changed and that the move is \"illegal\". That means that the fleet in the Black Sea does not dislodge the supported Russian fleet.</i>"}
  "6.D.30. MOVE WITHOUT COAST AND SUPPORT"
  {:summary "Similar to the previous test case, but now the move can be \"illegal\" because of missing coast."
   :conflict-judgments {[:italy :fleet :aeg :support :russia :fleet :con :hold] #{[:interfered? :interferer :rule]}
                        [:russia :fleet :con :attack :bul] #{[:interfered? :interferer :rule]}
                        [:turkey :fleet :bla :attack :con] #{[:interfered? :interferer :rule]}
                        [:turkey :army :bul :support :turkey :fleet :bla :attack :con] #{[:interfered? :interferer :rule]}}
   :explanation "Again the order to the Russian fleet is with problems, because it does not specify the coast, while both coasts of Bulgaria are possible. If no default coast is taken (see issue 4.B.1), then also here it must be decided whether the order is \"illegal\" (see issue 4.E.1). If the move is \"illegal\" it must be ignored and that makes the hold support of the fleet in the Aegean Sea valid and the Russian fleet will not be dislodged. <i>I don't like default coasts and I prefer that the move is \"illegal\". That means that the fleet in the Black Sea does not dislodge the supported Russian fleet.</i>"}
  "6.D.31. A TRICKY IMPOSSIBLE SUPPORT"
  {:summary "A support order can be impossible for complex reasons."
   :conflict-judgments {[:austria :army :rum :attack :arm] #{[:interfered? :interferer :rule]}
                        [:turkey :fleet :bla :support :austria :army :rum :attack :arm] #{[:interfered? :interferer :rule]}}
   :explanation "Although the army in Rumania can move to Armenia and the fleet in the Black Sea can also go to Armenia, the support is still not possible. The reason is that the only possible convoy is through the Black Sea and a fleet can not convoy and support at the same time. This is relevant for computer programs that show only the possible orders. In the list of possible orders, the support as given to the fleet in the Black Sea, should not be listed. Furthermore, if the fleet in the Black Sea gets a second order, then this may fail, because of double orders (although it can also be ruled differently, see issue 4.D.3). However, when the support order is considered \"illegal\" (see issue 4.E.1), then this impossible support must be ignored and the second order must be carried out. <i>I prefer that impossible orders are \"illegal\" and ignored. If there would be a second order for the fleet in the Black Sea, that order should be carried out.</i>"}
  "6.D.32. A MISSING FLEET"
  {:summary "The previous test cases contained an order that was impossible even when some other pieces on the board where changed. In this  test case, the order is impossible, but only for that situation."
   :conflict-judgments {[:england :fleet :edi :support :england :army :lvp :attack :yor] #{[:interfered? :interferer :rule]}
                        [:england :army :lvp :attack :yor] #{[:interfered? :interferer :rule]}
                        [:france :fleet :lon :support :germany :army :yor :hold] #{[:interfered? :interferer :rule]}
                        [:germany :army :yor :attack :hol] #{[:interfered? :interferer :rule]}}
   :explanation "The German order to Yorkshire can not be executed, because there is no fleet in the North Sea. In other situations (where there is a fleet in the North Sea), the exact same order would be possible. It should be determined whether this is \"illegal\"  (see issue 4.E.1) or not. If it is illegal, then the order should be ignored and the support of the French fleet in London succeeds. This means that the army in Yorkshire is not dislodged. <i>I prefer that impossible orders, even if it is only impossible for the current situation, are \"illegal\" and ignored. The army in Yorkshire is not dislodged.</i>"}
  "6.D.33. UNWANTED SUPPORT ALLOWED"
  {:summary "A self stand-off can be broken by an unwanted support."
   :conflict-judgments {[:austria :army :ser :attack :bud] #{[:interfered? :interferer :rule]}
                        [:austria :army :vie :attack :bud] #{[:interfered? :interferer :rule]}
                        [:russia :army :gal :support :austria :army :ser :attack :bud] #{[:interfered? :interferer :rule]}
                        [:turkey :army :bul :attack :ser] #{[:interfered? :interferer :rule]}}
   :explanation "Due to the Russian support, the army in Serbia advances to Budapest. This enables Turkey to capture Serbia with the army in Bulgaria."}
  "6.D.34. SUPPORT TARGETING OWN AREA NOT ALLOWED"
  {:summary "Support targeting the area where the supporting unit is standing, is illegal."
   :conflict-judgments {[:germany :army :ber :attack :pru] #{[:interfered? :interferer :rule]}
                        [:germany :army :sil :support :germany :army :ber :attack :pru] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :bal :support :germany :army :ber :attack :pru] #{[:interfered? :interferer :rule]}
                        [:italy :army :pru :support :russia :army :lvn :attack :pru] #{[:interfered? :interferer :rule]}
                        [:russia :army :war :support :russia :army :lvn :attack :pru] #{[:interfered? :interferer :rule]}
                        [:russia :army :lvn :attack :pru] #{[:interfered? :interferer :rule]}}
   :explanation "Russia and Italy wanted to get rid of the Italian army in Prussia (to build an Italian fleet somewhere else). However, they didn't want a possible German attack on Prussia to succeed. They invented this odd order of Italy. It was intended that the attack of the army in Livonia would have strength three, so it would be capable to prevent the possible German attack to succeed. However, the order of Italy is illegal, because a unit may only support to an area where the unit can go by itself. A unit can't go to the area it is already standing, so the Italian order is illegal and the German move from Berlin succeeds. Even if it would be legal, the German move from Berlin would still succeed, because the support of Prussia is cut by Livonia and Berlin."}
 "6.E.1. DISLODGED UNIT HAS NO EFFECT ON ATTACKERS AREA"
  {:summary "An army can follow."
   :conflict-judgments {[:germany :army :ber :attack :pru] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :kie :attack :ber] #{[:interfered? :interferer :rule]}
                        [:germany :army :sil :support :germany :army :ber :attack :pru] #{[:interfered? :interferer :rule]}
                        [:russia :army :pru :attack :ber] #{[:interfered? :interferer :rule]}}
   :explanation "The army in Kiel will move to Berlin."}
  "6.E.2. NO SELF DISLODGEMENT IN HEAD TO HEAD BATTLE"
  {:summary "Self dislodgement is not allowed. This also counts for head to head battles."
   :conflict-judgments {[:germany :army :ber :attack :kie] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :kie :attack :ber] #{[:interfered? :interferer :rule]}
                        [:germany :army :mun :support :germany :army :ber :attack :kie] #{[:interfered? :interferer :rule]}}
   :explanation "No unit will move."}
  "6.E.3. NO HELP IN DISLODGING OWN UNIT"
  {:summary "To help a foreign power to dislodge own unit in head to head battle is not possible."
   :conflict-judgments {[:germany :army :ber :attack :kie] #{[:interfered? :interferer :rule]}
                        [:germany :army :mun :support :england :fleet :kie :attack :ber] #{[:interfered? :interferer :rule]}
                        [:england :fleet :kie :attack :ber] #{[:interfered? :interferer :rule]}}
   :explanation "No unit will move."}
  "6.E.4. NON-DISLODGED LOSER HAS STILL EFFECT"
  {:summary "If in an unbalanced head to head battle the loser is not dislodged, it has still effect on the area of the attacker."
   :conflict-judgments {[:germany :fleet :hol :attack :nth] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :hel :support :germany :fleet :hol :attack :nth] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :ska :support :germany :fleet :hol :attack :nth] #{[:interfered? :interferer :rule]}
                        [:france :fleet :nth :attack :hol] #{[:interfered? :interferer :rule]}
                        [:france :fleet :bel :support :france :fleet :nth :attack :hol] #{[:interfered? :interferer :rule]}
                        [:england :fleet :edi :support :england :fleet :nrg :attack :nth] #{[:interfered? :interferer :rule]}
                        [:england :fleet :yor :support :england :fleet :nrg :attack :nth] #{[:interfered? :interferer :rule]}
                        [:england :fleet :nrg :attack :nth] #{[:interfered? :interferer :rule]}
                        [:austria :army :kie :support :austria :army :ruh :attack :hol] #{[:interfered? :interferer :rule]}
                        [:austria :army :ruh :attack :hol] #{[:interfered? :interferer :rule]}}
   :explanation "The French fleet in the North Sea is not dislodged due to the beleaguered garrison. Therefore, the Austrian army in Ruhr will not move to Holland."}
  "6.E.5. LOSER DISLODGED BY ANOTHER ARMY HAS STILL EFFECT"
  {:summary "If in an unbalanced head to head battle the loser is dislodged by a unit not part of the head to head battle, the loser has still effect on the place of the winner of the head to head battle."
   :conflict-judgments {[:germany :fleet :hol :attack :nth] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :hel :support :germany :fleet :hol :attack :nth] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :ska :support :germany :fleet :hol :attack :nth] #{[:interfered? :interferer :rule]}
                        [:france :fleet :nth :attack :hol] #{[:interfered? :interferer :rule]}
                        [:france :fleet :bel :support :france :fleet :nth :attack :hol] #{[:interfered? :interferer :rule]}
                        [:england :fleet :edi :support :england :fleet :nrg :attack :nth] #{[:interfered? :interferer :rule]}
                        [:england :fleet :yor :support :england :fleet :nrg :attack :nth] #{[:interfered? :interferer :rule]}
                        [:england :fleet :nrg :attack :nth] #{[:interfered? :interferer :rule]}
                        [:england :fleet :lon :support :england :fleet :nrg :attack :nth] #{[:interfered? :interferer :rule]}
                        [:austria :army :kie :support :austria :army :ruh :attack :hol] #{[:interfered? :interferer :rule]}
                        [:austria :army :ruh :attack :hol] #{[:interfered? :interferer :rule]}}
   :explanation "The French fleet in the North Sea is dislodged but not by the German fleet in Holland. Therefore, the French fleet can still prevent that the Austrian army in Ruhr will move to Holland. So, the Austrian move in Ruhr fails and the German fleet in Holland is not dislodged."}
  "6.E.6. NOT DISLODGE BECAUSE OF OWN SUPPORT HAS STILL EFFECT"
  {:summary "If in an unbalanced head to head battle the loser is not dislodged because the winner had help of a unit of the loser, the loser has still effect on the area of the winner."
   :conflict-judgments {[:germany :fleet :hol :attack :nth] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :hel :support :germany :fleet :hol :attack :nth] #{[:interfered? :interferer :rule]}
                        [:france :fleet :nth :attack :hol] #{[:interfered? :interferer :rule]}
                        [:france :fleet :bel :support :france :fleet :nth :attack :hol] #{[:interfered? :interferer :rule]}
                        [:france :fleet :eng :support :germany :fleet :hol :attack :nth] #{[:interfered? :interferer :rule]}
                        [:austria :army :kie :support :austria :army :ruh :attack :hol] #{[:interfered? :interferer :rule]}
                        [:austria :army :ruh :attack :hol] #{[:interfered? :interferer :rule]}}
   :explanation "Although the German force from Holland to North Sea is one larger than the French force from North Sea to Holland, the French fleet in the North Sea is not dislodged, because one of the supports on the German movement is French. Therefore, the Austrian army in Ruhr will not move to Holland."}
  "6.E.7. NO SELF DISLODGEMENT WITH BELEAGUERED GARRISON"
  {:summary "An attempt to self dislodgement can be combined with a beleaguered garrison. Such self dislodgment is still not possible."
   :conflict-judgments {[:england :fleet :nth :hold] #{}
                        [:england :fleet :yor :support :russia :fleet :nwy :attack :nth] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :hol :support :germany :fleet :hel :attack :nth] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :hel :attack :nth] #{[:interfered? :interferer :rule]}
                        [:russia :fleet :ska :support :russia :fleet :nwy :attack :nth] #{[:interfered? :interferer :rule]}
                        [:russia :fleet :nwy :attack :nth] #{[:interfered? :interferer :rule]}}
   :explanation "Although the Russians beat the German attack (with the support of Yorkshire) and the two Russian fleets are enough to dislodge the fleet in the North Sea, the fleet in the North Sea is not dislodged, since it would not be dislodged if the English fleet in Yorkshire would not give support. According to the DPTG the fleet in the North Sea would be dislodged. The DPTG is incorrect in this case."}
  "6.E.8. NO SELF DISLODGEMENT WITH BELEAGUERED GARRISON AND HEAD TO HEAD BATTLE"
  {:summary "Similar to the previous test case, but now the beleaguered fleet is also engaged in a head to head battle."
   :conflict-judgments {[:england :fleet :nth :attack :nwy] #{[:interfered? :interferer :rule]}
                        [:england :fleet :yor :support :russia :fleet :nwy :attack :nth] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :hol :support :germany :fleet :hel :attack :nth] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :hel :attack :nth] #{[:interfered? :interferer :rule]}
                        [:russia :fleet :ska :support :russia :fleet :nwy :attack :nth] #{[:interfered? :interferer :rule]}
                        [:russia :fleet :nwy :attack :nth] #{[:interfered? :interferer :rule]}}
   :explanation "Again, none of the fleets move."}
  "6.E.9. ALMOST SELF DISLODGEMENT WITH BELEAGUERED GARRISON"
  {:summary "Similar to the previous test case, but now the beleaguered fleet is moving away."
   :conflict-judgments {[:england :fleet :nth :attack :nrg] #{[:interfered? :interferer :rule]}
                        [:england :fleet :yor :support :russia :fleet :nwy :attack :nth] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :hol :support :germany :fleet :hel :attack :nth] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :hel :attack :nth] #{[:interfered? :interferer :rule]}
                        [:russia :fleet :ska :support :russia :fleet :nwy :attack :nth] #{[:interfered? :interferer :rule]}
                        [:russia :fleet :nwy :attack :nth] #{[:interfered? :interferer :rule]}}
   :explanation "Both the fleet in the North Sea and the fleet in Norway move."}
  "6.E.10. ALMOST CIRCULAR MOVEMENT WITH NO SELF DISLODGEMENT WITH BELEAGUERED GARRISON"
  {:summary "Similar to the previous test case, but now the beleaguered fleet is in circular movement with the weaker attacker. So, the circular movement fails."
   :conflict-judgments {[:england :fleet :nth :attack :den] #{[:interfered? :interferer :rule]}
                        [:england :fleet :yor :support :russia :fleet :nwy :attack :nth] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :hol :support :germany :fleet :hel :attack :nth] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :hel :attack :nth] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :den :attack :hel] #{[:interfered? :interferer :rule]}
                        [:russia :fleet :ska :support :russia :fleet :nwy :attack :nth] #{[:interfered? :interferer :rule]}
                        [:russia :fleet :nwy :attack :nth] #{[:interfered? :interferer :rule]}}
   :explanation "There is no movement of fleets."}
  ;; commented out because it uses a convoy
  #_"6.E.11. NO SELF DISLODGEMENT WITH BELEAGUERED GARRISON, UNIT SWAP WITH ADJACENT CONVOYING AND TWO COASTS"
  #_{:summary "Similar to the previous test case, but now the beleaguered fleet is in a unit swap with the stronger attacker. So, the unit swap succeeds. To make the situation more complex, the swap is on an area with two coasts."
   :conflict-judgments {[:france :army :spa :attack :por] #{[:interfered? :interferer :rule]}
                        [:france :fleet :mid :convoy :france :army :spa :attack :por] #{[:interfered? :interferer :rule]}
                        [:france :fleet :gol :support :italy :fleet :por :attack :spa-nc] #{[:interfered? :interferer :rule]}
                        [:germany :army :mar :support :germany :army :gas :attack :spa] #{[:interfered? :interferer :rule]}
                        [:germany :army :gas :attack :spa] #{[:interfered? :interferer :rule]}
                        [:italy :fleet :por :attack :spa-nc] #{[:interfered? :interferer :rule]}
                        [:italy :fleet :wes :support :italy :fleet :por :attack :spa-nc] #{[:interfered? :interferer :rule]}}
   :explanation "The unit swap succeeds. Note that due to the success of the swap, there is no beleaguered garrison anymore."}
  "6.E.12. SUPPORT ON ATTACK ON OWN UNIT CAN BE USED FOR OTHER MEANS"
  {:summary "A support on an attack on your own unit has still effect. It can prevent that another army will dislodge the unit."
   :conflict-judgments {[:austria :army :bud :attack :rum] #{[:interfered? :interferer :rule]}
                        [:austria :army :ser :support :italy :army :vie :attack :bud] #{[:interfered? :interferer :rule]}
                        [:italy :army :vie :attack :bud] #{[:interfered? :interferer :rule]}
                        [:russia :army :gal :attack :bud] #{[:interfered? :interferer :rule]}
                        [:russia :army :rum :support :russia :army :gal :attack :bud] #{[:interfered? :interferer :rule]}}
   :explanation "The support of Serbia on the Italian army prevents that the Russian army in Galicia will advance. No army will move."}
  "6.E.13. THREE WAY BELEAGUERED GARRISON"
  {:summary "In a beleaguered garrison from three sides, the adjudicator may not  let two attacks fail and then let the third succeed."
   :conflict-judgments {[:england :fleet :edi :support :england :fleet :yor :attack :nth] #{[:interfered? :interferer :rule]}
                        [:england :fleet :yor :attack :nth] #{[:interfered? :interferer :rule]}
                        [:france :fleet :bel :attack :nth] #{[:interfered? :interferer :rule]}
                        [:france :fleet :eng :support :france :fleet :bel :attack :nth] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :nth :hold] #{}
                        [:russia :fleet :nrg :attack :nth] #{[:interfered? :interferer :rule]}
                        [:russia :fleet :nwy :support :russia :fleet :nrg :attack :nth] #{[:interfered? :interferer :rule]}}
   :explanation "None of the fleets move. The German fleet in the North Sea is not dislodged."}
  "6.E.14. ILLEGAL HEAD TO HEAD BATTLE CAN STILL DEFEND"
  {:summary "If in a head to head battle, one of the units makes an illegal move, than that unit has still the possibility to defend against attacks with strength of one."
   :conflict-judgments {[:england :army :lvp :attack :edi] #{[:interfered? :interferer :rule]}
                        [:russia :fleet :edi :attack :lvp] #{[:interfered? :interferer :rule]}}
   :explanation "The move of the Russian fleet is illegal, but can still prevent the English army to enter Edinburgh. So, none of the units move."}
  "6.E.15. THE FRIENDLY HEAD TO HEAD BATTLE"
  {:summary "In this case both units in the head to head battle prevent that the other one is dislodged."
   :conflict-judgments {[:england :fleet :hol :support :england :army :ruh :attack :kie] #{[:interfered? :interferer :rule]}
                        [:england :army :ruh :attack :kie] #{[:interfered? :interferer :rule]}
                        [:france :army :kie :attack :ber] #{[:interfered? :interferer :rule]}
                        [:france :army :mun :support :france :army :kie :attack :ber] #{[:interfered? :interferer :rule]}
                        [:france :army :sil :support :france :army :kie :attack :ber] #{[:interfered? :interferer :rule]}
                        [:germany :army :ber :attack :kie] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :den :support :germany :army :ber :attack :kie] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :hel :support :germany :army :ber :attack :kie] #{[:interfered? :interferer :rule]}
                        [:russia :fleet :bal :support :russia :army :pru :attack :ber] #{[:interfered? :interferer :rule]}
                        [:russia :army :pru :attack :ber] #{[:interfered? :interferer :rule]}}
   :explanation "None of the moves succeeds. This case is especially difficult for  sequence based adjudicators. They will start adjudicating the head to head battle and continue to adjudicate the attack on one of the units part of the head to head battle. In this process, one of the sides of the head to head battle might be cancelled out. This happens in the DPTG. If this is adjudicated according to the DPTG, the unit in Ruhr or in Prussia will advance (depending on the order the units are adjudicated). This is clearly a bug in the DPTG."}
  ;; commented out because it uses a convoy
 #_"6.F.1. NO CONVOY IN COASTAL AREAS"
  #_{:summary "A fleet in a coastal area may not convoy."
   :conflict-judgments {[:turkey :army :gre :attack :sev] #{[:interfered? :interferer :rule]}
                        [:turkey :fleet :aeg :convoy :turkey :army :gre :attack :sev] #{[:interfered? :interferer :rule]}
                        [:turkey :fleet :con :convoy :turkey :army :gre :attack :sev] #{[:interfered? :interferer :rule]}
                        [:turkey :fleet :bla :convoy :turkey :army :gre :attack :sev] #{[:interfered? :interferer :rule]}}
   :explanation "The convoy in Constantinople is not possible. So, the army in Greece will not move to Sevastopol."}
  ;; commented out because it uses a convoy
  #_"6.F.2. AN ARMY BEING CONVOYED CAN BOUNCE AS NORMAL"
  #_{:summary "Armies being convoyed bounce on other units just as armies that are not being convoyed."
   :conflict-judgments {[:england :fleet :eng :convoy :england :army :lon :attack :bre] #{[:interfered? :interferer :rule]}
                        [:england :army :lon :attack :bre] #{[:interfered? :interferer :rule]}
                        [:france :army :par :attack :bre] #{[:interfered? :interferer :rule]}}
   :explanation "The English army in London bounces on the French army in Paris. Both units do not move."}
  ;; commented out because it uses a convoy
  #_"6.F.3. AN ARMY BEING CONVOYED CAN RECEIVE SUPPORT"
  #_{:summary "Armies being convoyed can receive support as in any other move."
   :conflict-judgments {[:england :fleet :eng :convoy :england :army :lon :attack :bre] #{[:interfered? :interferer :rule]}
                        [:england :army :lon :attack :bre] #{[:interfered? :interferer :rule]}
                        [:england :fleet :mid :support :england :army :lon :attack :bre] #{[:interfered? :interferer :rule]}
                        [:france :army :par :attack :bre] #{[:interfered? :interferer :rule]}}
   :explanation "The army in London receives support and beats the army in Paris. This means that the army London will end in Brest and the French army in Paris stays in Paris."}
  ;; commented out because it uses a convoy
  #_"6.F.4. AN ATTACKED CONVOY IS NOT DISRUPTED"
  #_{:summary "A convoy can only be disrupted by dislodging the fleets. Attacking is not sufficient."
   :conflict-judgments {[:england :fleet :nth :convoy :england :army :lon :attack :hol] #{[:interfered? :interferer :rule]}
                        [:england :army :lon :attack :hol] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :ska :attack :nth] #{[:interfered? :interferer :rule]}}
   :explanation "The army in London will successfully convoy and end in Holland."}
  ;; commented out because it uses a convoy
  #_"6.F.5. A BELEAGUERED CONVOY IS NOT DISRUPTED"
  #_{:summary "Even when a convoy is in a beleaguered garrison it is not disrupted."
   :conflict-judgments {[:england :fleet :nth :convoy :england :army :lon :attack :hol] #{[:interfered? :interferer :rule]}
                        [:england :army :lon :attack :hol] #{[:interfered? :interferer :rule]}
                        [:france :fleet :eng :attack :nth] #{[:interfered? :interferer :rule]}
                        [:france :fleet :bel :support :france :fleet :eng :attack :nth] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :ska :attack :nth] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :den :support :germany :fleet :ska :attack :nth] #{[:interfered? :interferer :rule]}}
   :explanation "The army in London will successfully convoy and end in Holland."}
  ;; commented out because it uses a convoy
  #_"6.F.6. DISLODGED CONVOY DOES NOT CUT SUPPORT"
  #_{:summary "When a fleet of a convoy is dislodged, the convoy is completely cancelled. So, no support is cut."
   :conflict-judgments {[:england :fleet :nth :convoy :england :army :lon :attack :hol] #{[:interfered? :interferer :rule]}
                        [:england :army :lon :attack :hol] #{[:interfered? :interferer :rule]}
                        [:germany :army :hol :support :germany :army :bel :hold] #{[:interfered? :interferer :rule]}
                        [:germany :army :bel :support :germany :army :hol :hold] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :hel :support :germany :fleet :ska :attack :nth] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :ska :attack :nth] #{[:interfered? :interferer :rule]}
                        [:france :army :pic :attack :bel] #{[:interfered? :interferer :rule]}
                        [:france :army :bur :support :france :army :pic :attack :bel] #{[:interfered? :interferer :rule]}}
   :explanation "The hold order of Holland on Belgium will sustain and Belgium will not be dislodged by the French in Picardy."}
  ;; commented out because it uses a convoy
  #_"6.F.7. DISLODGED CONVOY DOES NOT CAUSE CONTESTED AREA"
  #_{:summary "When a fleet of a convoy is dislodged, the landing area is not contested, so other units can retreat to that area."
   :conflict-judgments {[:england :fleet :nth :convoy :england :army :lon :attack :hol] #{[:interfered? :interferer :rule]}
                        [:england :army :lon :attack :hol] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :hel :support :germany :fleet :ska :attack :nth] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :ska :attack :nth] #{[:interfered? :interferer :rule]}}
   :explanation "The dislodged English fleet can retreat to Holland."}
  ;; commented out because it uses a convoy
  #_"6.F.8. DISLODGED CONVOY DOES NOT CAUSE A BOUNCE"
  #_{:summary "When a fleet of a convoy is dislodged, then there will be no bounce in the landing area."
   :conflict-judgments {[:england :fleet :nth :convoy :england :army :lon :attack :hol] #{[:interfered? :interferer :rule]}
                        [:england :army :lon :attack :hol] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :hel :support :germany :fleet :ska :attack :nth] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :ska :attack :nth] #{[:interfered? :interferer :rule]}
                        [:germany :army :bel :attack :hol] #{[:interfered? :interferer :rule]}}
   :explanation "The army in Belgium will not bounce and move to Holland."}
  ;; commented out because it uses a convoy
  #_"6.F.9. DISLODGE OF MULTI-ROUTE CONVOY"
  #_{:summary "When a fleet of a convoy with multiple routes is dislodged, the result depends on the rulebook that is used."
   :conflict-judgments {[:england :fleet :eng :convoy :england :army :lon :attack :bel] #{[:interfered? :interferer :rule]}
                        [:england :fleet :nth :convoy :england :army :lon :attack :bel] #{[:interfered? :interferer :rule]}
                        [:england :army :lon :attack :bel] #{[:interfered? :interferer :rule]}
                        [:france :fleet :bre :support :france :fleet :mid :attack :eng] #{[:interfered? :interferer :rule]}
                        [:france :fleet :mid :attack :eng] #{[:interfered? :interferer :rule]}}
   :explanation "The French fleet in Mid Atlantic Ocean will dislodge the convoying fleet in the English Channel. If the 1971 rules are used (see issue 4.A.1), this will disrupt the convoy and the army will stay in London. When the 1982 or 2000 rulebook is used (<i>which I prefer</i>) the army can still go via the North Sea and the convoy succeeds and the London army will end in Belgium."}
  ;; commented out because it uses a convoy
  #_"6.F.10. DISLODGE OF MULTI-ROUTE CONVOY WITH FOREIGN FLEET"
  #_{:summary "When the 1971 rulebook is used \"unwanted\" multi-route convoys are possible."
   :conflict-judgments {[:england :fleet :nth :convoy :england :army :lon :attack :bel] #{[:interfered? :interferer :rule]}
                        [:england :army :lon :attack :bel] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :eng :convoy :england :army :lon :attack :bel] #{[:interfered? :interferer :rule]}
                        [:france :fleet :bre :support :france :fleet :mid :attack :eng] #{[:interfered? :interferer :rule]}
                        [:france :fleet :mid :attack :eng] #{[:interfered? :interferer :rule]}}
   :explanation "If the 1982 or 2000 rulebook is used (<i>which I prefer</i>), it makes no difference that the convoying fleet in the English Channel is German. It will take the convoy via the North Sea anyway and the army in London will end in Belgium. However, when the 1971 rules are used, the German convoy is \"unwanted\". According to the DPTG the German fleet should be ignored in the English convoy, since there is a convoy path with only English fleets. That means that the convoy is not disrupted and the English army in London will end in Belgium. See also issue 4.A.1."}
  ;; commented out because it uses a convoy
  #_"6.F.11. DISLODGE OF MULTI-ROUTE CONVOY WITH ONLY FOREIGN FLEETS"
  #_{:summary "When the 1971 rulebook is used, \"unwanted\" convoys can not be ignored in all cases."
   :conflict-judgments {[:england :army :lon :attack :bel] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :eng :convoy :england :army :lon :attack :bel] #{[:interfered? :interferer :rule]}
                        [:russia :fleet :nth :convoy :england :army :lon :attack :bel] #{[:interfered? :interferer :rule]}
                        [:france :fleet :bre :support :france :fleet :mid :attack :eng] #{[:interfered? :interferer :rule]}
                        [:france :fleet :mid :attack :eng] #{[:interfered? :interferer :rule]}}
   :explanation "If the 1982 or 2000 rulebook is used (<i>which I prefer</i>), it makes no difference that the convoying fleets are not English. It will take the convoy via the North Sea anyway and the army in London will end in Belgium. However, when the 1971 rules are used, the situation is  different. Since both the fleet in the English Channel as the fleet in North Sea are not English, it can not be concluded that the German fleet is \"unwanted\". Therefore, one of the routes of the convoy is disrupted and that means that the complete convoy is disrupted. The army in London will stay in London. See also issue 4.A.1."}
  ;; commented out because it uses a convoy
  #_"6.F.12. DISLODGED CONVOYING FLEET NOT ON ROUTE"
  #_{:summary "When the rule is used that convoys are disrupted when one of the routes is disrupted (see issue 4.A.1), the convoy is not necessarily disrupted when one of the fleets ordered to convoy is dislodged."
   :conflict-judgments {[:england :fleet :eng :convoy :england :army :lon :attack :bel] #{[:interfered? :interferer :rule]}
                        [:england :army :lon :attack :bel] #{[:interfered? :interferer :rule]}
                        [:england :fleet :iri :convoy :england :army :lon :attack :bel] #{[:interfered? :interferer :rule]}
                        [:france :fleet :nat :support :france :fleet :mid :attack :iri] #{[:interfered? :interferer :rule]}
                        [:france :fleet :mid :attack :iri] #{[:interfered? :interferer :rule]}}
   :explanation "Even when convoys are disrupted when one of the routes is disrupted (see issue 4.A.1), the convoy from London to Belgium will still succeed, since the dislodged fleet in the Irish Sea is not part of any route, although it can be reached from the starting point London."}
  ;; commented out because it uses a convoy
  #_"6.F.13. THE UNWANTED ALTERNATIVE"
  #_{:summary "This situation is not difficult to adjudicate, but it shows that even if someone wants to convoy, the player might not want an alternative route for the convoy."
   :conflict-judgments {[:england :army :lon :attack :bel] #{[:interfered? :interferer :rule]}
                        [:england :fleet :nth :convoy :england :army :lon :attack :bel] #{[:interfered? :interferer :rule]}
                        [:france :fleet :eng :convoy :england :army :lon :attack :bel] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :hol :support :germany :fleet :den :attack :nth] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :den :attack :nth] #{[:interfered? :interferer :rule]}}
   :explanation "If France and German are allies, England want to keep its army in London, to defend the island. An army in Belgium could easily be destroyed by an alliance of France and Germany. England tries to be friends with Germany, however France and Germany trick England. The convoy of the army in London succeeds and the fleet in Denmark dislodges the fleet in the North Sea."}
  ;; commented out because it uses a convoy
  #_"6.F.14. SIMPLE CONVOY PARADOX"
  #_{:summary "The most common paradox is when the attacked unit supports an attack on one of the convoying fleets."
   :conflict-judgments {[:england :fleet :lon :support :england :fleet :wal :attack :eng] #{[:interfered? :interferer :rule]}
                        [:england :fleet :wal :attack :eng] #{[:interfered? :interferer :rule]}
                        [:france :army :bre :attack :lon] #{[:interfered? :interferer :rule]}
                        [:france :fleet :eng :convoy :france :army :bre :attack :lon] #{[:interfered? :interferer :rule]}}
   :explanation "This situation depends on how paradoxes are handled (see issue (4.A.2). In case of the 'All Hold' rule (fully applied, not just as \"backup\" rule), both the movement of the English fleet in Wales as the France convoy in Brest are part of the paradox and fail. In all other rules of paradoxical convoys (<i>including the Szykman rule which I prefer</i>), the support of London is not cut. That means that the fleet in the English Channel is dislodged."}
  ;; commented out because it uses a convoy
  #_"6.F.15. SIMPLE CONVOY PARADOX WITH ADDITIONAL CONVOY"
  #_{:summary "Paradox rules only apply on the paradox core."
   :conflict-judgments {[:england :fleet :lon :support :england :fleet :wal :attack :eng] #{[:interfered? :interferer :rule]}
                        [:england :fleet :wal :attack :eng] #{[:interfered? :interferer :rule]}
                        [:france :army :bre :attack :lon] #{[:interfered? :interferer :rule]}
                        [:france :fleet :eng :convoy :france :army :bre :attack :lon] #{[:interfered? :interferer :rule]}
                        [:italy :fleet :iri :convoy :italy :army :naf :attack :wal] #{[:interfered? :interferer :rule]}
                        [:italy :fleet :mid :convoy :italy :army :naf :attack :wal] #{[:interfered? :interferer :rule]}
                        [:italy :army :naf :attack :wal] #{[:interfered? :interferer :rule]}}
   :explanation "The Italian convoy is not part of the paradox core and should  therefore succeed when the move of the fleet in Wales is successful. This is the case except when the 'All Hold' paradox rule is used (fully applied, not just as \"backup\" rule, see issue 4.A.2). <i>I prefer the Szykman rule, so I prefer that both the fleet in Wales as the army in North Africa succeed in moving.</i>"}
  ;; commented out because it uses a convoy
  #_"6.F.16. PANDIN'S PARADOX"
  #_{:summary "In Pandin's paradox, the attacked unit protects the convoying fleet by a beleaguered garrison."
   :conflict-judgments {[:england :fleet :lon :support :england :fleet :wal :attack :eng] #{[:interfered? :interferer :rule]}
                        [:england :fleet :wal :attack :eng] #{[:interfered? :interferer :rule]}
                        [:france :army :bre :attack :lon] #{[:interfered? :interferer :rule]}
                        [:france :fleet :eng :convoy :france :army :bre :attack :lon] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :nth :support :germany :fleet :bel :attack :eng] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :bel :attack :eng] #{[:interfered? :interferer :rule]}}
   :explanation "In all the different rules for resolving convoy disruption paradoxes (see issue 4.A.2), the support of London is not cut. That means that the fleet in the English Channel is not dislodged and none of the units succeed to move."}
  ;; commented out because it uses a convoy
  #_"6.F.17. PANDIN'S EXTENDED PARADOX"
  #_{:summary "In Pandin's extended paradox, the attacked unit protects the convoying fleet by a beleaguered garrison and the attacked unit can dislodge the unit that gives the protection."
   :conflict-judgments {[:england :fleet :lon :support :england :fleet :wal :attack :eng] #{[:interfered? :interferer :rule]}
                        [:england :fleet :wal :attack :eng] #{[:interfered? :interferer :rule]}
                        [:france :army :bre :attack :lon] #{[:interfered? :interferer :rule]}
                        [:france :fleet :eng :convoy :france :army :bre :attack :lon] #{[:interfered? :interferer :rule]}
                        [:france :fleet :yor :support :france :army :bre :attack :lon] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :nth :support :germany :fleet :bel :attack :eng] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :bel :attack :eng] #{[:interfered? :interferer :rule]}}
   :explanation "When the 1971, 1982 or 2000 rule is used (see issue 4.A.2), the support of London is not cut. That means that the fleet in the English Channel is not dislodged. The convoy will succeed and dislodge the fleet in London. You may argue that this violates the dislodge rule, but the common interpretation is that the paradox convoy rules take precedence over the dislodge rule. If the Simon Szykman alternative is used (<i>which I prefer</i>), the convoy fails and the fleet in London and the English Channel are not dislodged. When the 'All Hold' (fully applied, not just as \"backup\" rule) or the DPTG rule is used, the result is the same as the Simon Szykman alternative. The involved moves (the move of the German fleet in Belgium and the convoying army in Brest) fail."}
  ;; commented out because it uses a convoy
  #_"6.F.18. BETRAYAL PARADOX"
  #_{:summary "The betrayal paradox is comparable to Pandin's paradox, but now the attacked unit direct supports the convoying fleet. Of course, this will only happen when the player of the attacked unit is betrayed."
   :conflict-judgments {[:england :fleet :nth :convoy :england :army :lon :attack :bel] #{[:interfered? :interferer :rule]}
                        [:england :army :lon :attack :bel] #{[:interfered? :interferer :rule]}
                        [:england :fleet :eng :support :england :army :lon :attack :bel] #{[:interfered? :interferer :rule]}
                        [:france :fleet :bel :support :england :fleet :nth :hold] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :hel :support :germany :fleet :ska :attack :nth] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :ska :attack :nth] #{[:interfered? :interferer :rule]}}
   :explanation "If the English convoy from London to Belgium is successful, then it cuts the France support necessary to hold the fleet in the North Sea (see issue 4.A.2). The 1971 and 2000 ruling do not give an answer on this. According to the 1982 ruling the French support on the North Sea will not be cut. So, the fleet in the North Sea will not be dislodged by the Germans and the army in London will dislodge the French army in Belgium. If the Szykman rule is followed (<i>which I prefer</i>), the move of the army in London will fail and will not cut support. That means that the fleet in the North Sea will not be dislodged. The 'All Hold' rule has the same result as the Szykman rule, but with a different reason. The move of the army in London and the move of the German fleet in Skagerrak will fail. Since a failing convoy does not result in a consistent  resolution, the DPTG gives the same result as the 'All Hold' rule."}
  ;; commented out because it uses a convoy
  #_"6.F.19. MULTI-ROUTE CONVOY DISRUPTION PARADOX"
  #_{:summary "The situation becomes more complex when the convoy has alternative routes."
   :conflict-judgments {[:france :army :tun :attack :nap] #{[:interfered? :interferer :rule]}
                        [:france :fleet :tyn :convoy :france :army :tun :attack :nap] #{[:interfered? :interferer :rule]}
                        [:france :fleet :ion :convoy :france :army :tun :attack :nap] #{[:interfered? :interferer :rule]}
                        [:italy :fleet :nap :support :italy :fleet :rom :attack :tyn] #{[:interfered? :interferer :rule]}
                        [:italy :fleet :rom :attack :tyn] #{[:interfered? :interferer :rule]}}
   :explanation "Now, two issues play a role. The ruling about disruption of convoys (issue 4.A.1) and the issue how paradoxes are resolved (issue 4.A.2). If the 1971 rule is used about multi-route convoys (when one of the routes is disrupted, the convoy fails), this test case is just a simple paradox. For the 1971, 1982, 2000 and Szykman paradox rule, the support of  the fleet in Naples is not cut and the fleet in Rome dislodges the fleet in the Tyrrhenian Sea. When the 'All Hold' rule is used, both the convoy of the army in Tunis as the move of the fleet in Rome will fail. When the 1982 rule is used about multi-route convoy disruption, then convoys are disrupted when all routes are disrupted (<i>this is the rule I prefer</i>). With this rule, the situation becomes paradoxical. According to the 1971 and 1982 paradox rules, the support given by the fleet in Naples is not cut, that means that the fleet in the Tyrrhenian Sea is dislodged. According to the 2000 ruling the fleet in the Tyrrhenian Sea is not \"necessary\" for the convoy and the support of Naples is cut and the fleet in the Tyrrhenian Sea is not dislodged. If the Szykman rule is used (<i>which I prefer</i>), the 'All Hold' rule or the DPTG, then there is no paradoxical situation. The support of Naples is cut and the fleet in the Tyrrhenian Sea is not dislodged."}
  ;; commented out because it uses a convoy
  #_"6.F.20. UNWANTED MULTI-ROUTE CONVOY PARADOX"
  #_{:summary "The 1982 paradox rule allows some creative defense."
   :conflict-judgments {[:france :army :tun :attack :nap] #{[:interfered? :interferer :rule]}
                        [:france :fleet :tyn :convoy :france :army :tun :attack :nap] #{[:interfered? :interferer :rule]}
                        [:italy :fleet :nap :support :italy :fleet :ion :hold] #{[:interfered? :interferer :rule]}
                        [:italy :fleet :ion :convoy :france :army :tun :attack :nap] #{[:interfered? :interferer :rule]}
                        [:turkey :fleet :aeg :support :turkey :fleet :eas :attack :ion] #{[:interfered? :interferer :rule]}
                        [:turkey :fleet :eas :attack :ion] #{[:interfered? :interferer :rule]}}
   :explanation "Again, two issues play a role. The ruling about disruption of multi-route convoys (issue 4.A.1) and the issue how paradoxes are resolved (issue 4.A.2). If the 1971 rule is used about multi-route convoys (when one of the routes is disrupted, the convoy fails), the Italian convoy order in the Ionian Sea is not part of the convoy, because it is a foreign unit (according to the DPTG). That means that the fleet in the Ionian Sea is not a 'convoying' fleet. In all rulings the support of Naples on the Ionian Sea is cut and the fleet in the Ionian Sea is dislodged by the Turkish fleet in the Eastern Mediterranean. When the 1982 rule is used about multi-route convoy disruption, then convoys are disrupted when all routes are disrupted (<i>this is the rule I prefer</i>). With this rule, the situation becomes paradoxical. According to the 1971 and 1982 paradox rules, the support given by the fleet in Naples is not cut, that means that the fleet in the Ionian Sea is not dislodged. According to the 2000 ruling the fleet in the Ionian Sea is not \"necessary\" and the support of Naples is cut and the fleet in the Ionian Sea is dislodged by the Turkish fleet in the Eastern Mediterranean. If the Szykman rule, the 'All Hold' rule or DPTG is used, then there is no paradoxical situation. The support of Naples is cut and the fleet in the Ionian Sea is dislodged by the Turkish fleet in the Eastern Mediterranean. <i>As you can see, the 1982 rules allows the Italian player to save its fleet in the Ionian Sea with a trick. I do not consider this trick as normal tactical play. I prefer the Szykman rule as one of the rules that does not allow this trick. According to this rule the fleet in the Ionian Sea is dislodged.</i>"}
  ;; commented out because it uses a convoy
  #_"6.F.21. DAD'S ARMY CONVOY"
  #_{:summary "The 1982 paradox rule has as side effect that convoying armies do not cut support in some situations that are not paradoxical."
   :conflict-judgments {[:russia :army :edi :support :russia :army :nwy :attack :cly] #{[:interfered? :interferer :rule]}
                        [:russia :fleet :nrg :convoy :russia :army :nwy :attack :cly] #{[:interfered? :interferer :rule]}
                        [:russia :army :nwy :attack :cly] #{[:interfered? :interferer :rule]}
                        [:france :fleet :iri :support :france :fleet :mid :attack :nat] #{[:interfered? :interferer :rule]}
                        [:france :fleet :mid :attack :nat] #{[:interfered? :interferer :rule]}
                        [:england :army :lvp :attack :cly] #{[:interfered? :interferer :rule]}
                        [:england :fleet :nat :convoy :england :army :lvp :attack :cly] #{[:interfered? :interferer :rule]}
                        [:england :fleet :cly :support :england :fleet :nat :hold] #{[:interfered? :interferer :rule]}}
   :explanation "In all rulings, except the 1982 paradox ruling, the support of the fleet in Clyde on the North Atlantic Ocean is cut and the French fleet in the Mid-Atlantic Ocean will dislodge the fleet in the North Atlantic Ocean. This is the preferred way. However, in the 1982 paradox rule (see issue 4.A.2), the support of the fleet in Clyde is not cut. That means that the English fleet in the North Atlantic Ocean is not dislodged. <i>As you can see, the 1982 rule allows England to save its fleet in the North Atlantic Ocean in a very strange way. Just the support of Clyde is insufficient (if there is no convoy, the support is cut). Only the convoy to the area occupied by own unit, can do the trick in this situation. The embarking of troops in the fleet deceives the enemy so much that it works as a magic cloak. The enemy is not able to dislodge the fleet in the North Atlantic Ocean any more. Of course, this will only work in comedies. I prefer the Szykman rule as one of the rules that does not allow this trick.  According to this rule (and all other paradox rules), the fleet in the North Atlantic is just dislodged.</i>"}
  ;; commented out because it uses a convoy
  #_"6.F.22. SECOND ORDER PARADOX WITH TWO RESOLUTIONS"
  #_{:summary "Two convoys are involved in a second order paradox."
   :conflict-judgments {[:england :fleet :edi :attack :nth] #{[:interfered? :interferer :rule]}
                        [:england :fleet :lon :support :england :fleet :edi :attack :nth] #{[:interfered? :interferer :rule]}
                        [:france :army :bre :attack :lon] #{[:interfered? :interferer :rule]}
                        [:france :fleet :eng :convoy :france :army :bre :attack :lon] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :bel :support :germany :fleet :pic :attack :eng] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :pic :attack :eng] #{[:interfered? :interferer :rule]}
                        [:russia :army :nwy :attack :bel] #{[:interfered? :interferer :rule]}
                        [:russia :fleet :nth :convoy :russia :army :nwy :attack :bel] #{[:interfered? :interferer :rule]}}
   :explanation "Without any paradox rule, there are two consistent resolutions. The supports of the English fleet in London and the German fleet in Picardy are not cut. That means that the French fleet in the English Channel and the Russian fleet in the North Sea are  dislodged, which makes it impossible to cut the support. The other resolution is that the supports of the English fleet in London the German fleet in Picardy are cut. In that case the French fleet in the English Channel and the Russian fleet in the North Sea will survive and will not be dislodged. This gives the possibility to cut the support. The 1971 paradox rule and the 2000 rule (see issue 4.A.2) do not have an answer on this. According to the 1982 rule, the supports are not cut which means that the French fleet in the English Channel and the Russian fleet in the North Sea are dislodged. The Szykman (<i>which I prefer</i>), has the same result as the 1982 rule. The supports are not cut, the convoying armies fail to move, the fleet in Picardy dislodges the fleet in English Channel and the fleet in Edinburgh dislodges the fleet in the North Sea. The DPTG rule has in this case the same result as the Szykman rule, because the failing of all convoys is a consistent resolution. So, the armies in Brest and Norway fail to move, while the fleets in Edinburgh and Picardy succeed to move. When the 'All Hold' rule is used, the movement of the armies in  Brest and Norway as the fleets in Edinburgh and Picardy will fail."}
  ;; commented out because it uses a convoy
  #_"6.F.23. SECOND ORDER PARADOX WITH TWO EXCLUSIVE CONVOYS"
  #_{:summary "In this paradox there are two consistent resolutions, but where the two convoys do not fail or succeed at the same time. This fact is important for the DPTG resolution."
   :conflict-judgments {[:england :fleet :edi :attack :nth] #{[:interfered? :interferer :rule]}
                        [:england :fleet :yor :support :england :fleet :edi :attack :nth] #{[:interfered? :interferer :rule]}
                        [:france :army :bre :attack :lon] #{[:interfered? :interferer :rule]}
                        [:france :fleet :eng :convoy :france :army :bre :attack :lon] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :bel :support :france :fleet :eng :hold] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :lon :support :russia :fleet :nth :hold] #{[:interfered? :interferer :rule]}
                        [:italy :fleet :mid :attack :eng] #{[:interfered? :interferer :rule]}
                        [:italy :fleet :iri :support :italy :fleet :mid :attack :eng] #{[:interfered? :interferer :rule]}
                        [:russia :army :nwy :attack :bel] #{[:interfered? :interferer :rule]}
                        [:russia :fleet :nth :convoy :russia :army :nwy :attack :bel] #{[:interfered? :interferer :rule]}}
   :explanation "Without any paradox rule, there are two consistent resolutions. In one resolution, the convoy in the English Channel is dislodged by the fleet in the Mid-Atlantic Ocean, while the convoy in the North Sea succeeds. In the other resolution, it is the other way around. The convoy in the North Sea is dislodged by the fleet in  Edinburgh, while the convoy in the English Channel succeeds. The 1971 paradox rule and the 2000 rule (see issue 4.A.2) do not have an answer on this. According to the 1982 rule, the supports are not cut which means that the none of the units move. The Szykman (<i>which I prefer</i>), has the same result as the 1982 rule. The convoying armies fail to move and the supports are not cut. Because of the failure to cut the support, no fleet succeeds to move. When the 'All Hold' rule is used, the movement of the armies and the fleets all fail. Since there is no consistent resolution where all convoys fail, the DPTG rule has the same result as the 'All Hold' rule. That means the movement of all units fail."}
  ;; commented out because it uses a convoy
  #_"6.F.24. SECOND ORDER PARADOX WITH NO RESOLUTION"
  #_{:summary "As first order paradoxes, second order paradoxes come in two flavors, with two resolutions or no resolution."
   :conflict-judgments {[:england :fleet :edi :attack :nth] #{[:interfered? :interferer :rule]}
                        [:england :fleet :lon :support :england :fleet :edi :attack :nth] #{[:interfered? :interferer :rule]}
                        [:england :fleet :iri :attack :eng] #{[:interfered? :interferer :rule]}
                        [:england :fleet :mid :support :england :fleet :iri :attack :eng] #{[:interfered? :interferer :rule]}
                        [:france :army :bre :attack :lon] #{[:interfered? :interferer :rule]}
                        [:france :fleet :eng :convoy :france :army :bre :attack :lon] #{[:interfered? :interferer :rule]}
                        [:france :fleet :bel :support :france :fleet :eng :hold] #{[:interfered? :interferer :rule]}
                        [:russia :army :nwy :attack :bel] #{[:interfered? :interferer :rule]}
                        [:russia :fleet :nth :convoy :russia :army :nwy :attack :bel] #{[:interfered? :interferer :rule]}}
   :explanation "When no paradox rule is used, there is no consistent resolution. If the French support in Belgium is cut, the French fleet in the English Channel will be dislodged. That means that the support of London will not be cut and the fleet in Edinburgh will dislodge the Russian fleet in the North Sea. In this way the support in Belgium is not cut! But if the support in Belgium is not cut, the Russian fleet in the North Sea will not be dislodged and the army in Norway can cut the support in Belgium. The 1971 paradox rule and the 2000 rule (see issue 4.A.2) do not have an answer on this. According to the 1982 rule, the supports are not cut which means that the French fleet in the English Channel will survive and but the Russian fleet in the North Sea is dislodged. If the Szykman alternative is used (<i>which I prefer</i>), the supports are not cut and the convoying armies fail to move, which has the same result as the 1982 rule in this case. When the 'All Hold' rule is used, the movement of the armies in  Brest and Norway as the fleets in Edinburgh and the Irish Sea will fail. Since there is no consistent resolution where all convoys fail, the DPTG has in this case the same result as the 'All Hold' rule."}
  ;; commented out because it uses a convoy
 #_"6.G.1. TWO UNITS CAN SWAP PLACES BY CONVOY"
  #_{:summary "The only way to swap two units, is by convoy."
   :conflict-judgments {[:england :army :nwy :attack :swe] #{[:interfered? :interferer :rule]}
                        [:england :fleet :ska :convoy :england :army :nwy :attack :swe] #{[:interfered? :interferer :rule]}
                        [:russia :army :swe :attack :nwy] #{[:interfered? :interferer :rule]}}
   :explanation "In most interpretation of the rules, the units in Norway and Sweden will be swapped. However, if explicit adjacent convoying is used (see issue 4.A.3), then it is just a head to head battle. <i>I prefer the 2000 rules, so the units are swapped.</i>"}
  ;; commented out because it uses a convoy
  #_"6.G.2. KIDNAPPING AN ARMY"
  #_{:summary "Germany promised England to support to dislodge the Russian fleet in Sweden and it promised Russia to support to dislodge the English army in Norway. Instead, the joking German orders a convoy."
   :conflict-judgments {[:england :army :nwy :attack :swe] #{[:interfered? :interferer :rule]}
                        [:russia :fleet :swe :attack :nwy] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :ska :convoy :england :army :nwy :attack :swe] #{[:interfered? :interferer :rule]}}
   :explanation "See issue 4.A.3.  When the 1982/2000 rulebook is used (<i>which I prefer</i>), England has no intent to swap and it is just a head to head battle were both units will fail to move. When explicit adjacent convoying is used (DPTG), the English move is not a convoy and again it just a head to head battle were both units will fail to move. In all other interpretations, the army in Norway will be convoyed and swap its place with the fleet in Sweden."}
  ;; commented out because it uses a convoy
  #_"6.G.3. KIDNAPPING WITH A DISRUPTED CONVOY"
  #_{:summary "When kidnapping of armies is allowed, a move can be sabotaged by a fleet that is almost certainly dislodged."
   :conflict-judgments {[:france :fleet :bre :attack :eng] #{[:interfered? :interferer :rule]}
                        [:france :army :pic :attack :bel] #{[:interfered? :interferer :rule]}
                        [:france :army :bur :support :france :army :pic :attack :bel] #{[:interfered? :interferer :rule]}
                        [:france :fleet :mid :support :france :fleet :bre :attack :eng] #{[:interfered? :interferer :rule]}
                        [:england :fleet :eng :convoy :france :army :pic :attack :bel] #{[:interfered? :interferer :rule]}}
   :explanation "See issue 4.A.3. If a convoy always takes precedence over a land route (choice a), the move from Picardy to Belgium fails. It tries to convoy and the convoy is disrupted. For choice b and c, there is no unit moving in opposite direction for the move of the army in Picardy. For this reason, the move for the army in Picardy is not by convoy and succeeds over land. When the 1982 or 2000 rules are used (choice d), then it is not the \"intent\" of the French army in Picardy to convoy. The move from Picardy to Belgium is just a successful move over land. When explicit adjacent convoying is used (DPTG, choice e), the order of the French army in Picardy is not a convoy order. So, it just ordered over land, and that move succeeds. <i>This is an excellent example why the convoy route should not automatically have priority over the land route. It would just be annoying for the attacker and this situation is without fun. I prefer the 1982 rule with the 2000 clarification. According to these rules the move from Picardy succeeds.</i>"}
  ;; commented out because it uses a convoy
  #_"6.G.4. KIDNAPPING WITH A DISRUPTED CONVOY AND OPPOSITE MOVE"
  #_{:summary "In the situation of the previous test case it was rather clear that the army didn't want to take the convoy. But what if there is an army moving in opposite direction?"
   :conflict-judgments {[:france :fleet :bre :attack :eng] #{[:interfered? :interferer :rule]}
                        [:france :army :pic :attack :bel] #{[:interfered? :interferer :rule]}
                        [:france :army :bur :support :france :army :pic :attack :bel] #{[:interfered? :interferer :rule]}
                        [:france :fleet :mid :support :france :fleet :bre :attack :eng] #{[:interfered? :interferer :rule]}
                        [:england :fleet :eng :convoy :france :army :pic :attack :bel] #{[:interfered? :interferer :rule]}
                        [:england :army :bel :attack :pic] #{[:interfered? :interferer :rule]}}
   :explanation "See issue 4.A.3. If a convoy always takes precedence over a land route (choice a), the move from Picardy to Belgium fails. It tries to convoy and the convoy is disrupted. For choice b the convoy is also taken, because there is a unit in Belgium moving in opposite direction. This means that the convoy is disrupted and the move from Picardy to Belgium fails. For choice c the convoy is not taken. Although, the unit in Belgium is moving in opposite direction, the army will not take a disrupted convoy. So, the move from Picardy to Belgium succeeds. When the 1982 or 2000 rules are used (choice d), then it is not the \"intent\" of the French army in Picardy to convoy. The move from Picardy to Belgium is just a successful move over land. When explicit adjacent convoying is used (DPTG, choice e), the order of the French army in Picardy is not a convoy order. So, it just ordered over land, and that move succeeds. <i>Again an excellent example why the convoy route should not automatically have priority over the land route. It would just be annoying for the attacker and this situation is without fun. I prefer the 1982 rule with the 2000 clarification. According to these rules the move from Picardy succeeds.</i>"}
  ;; commented out because it uses a convoy
  #_"6.G.5. SWAPPING WITH INTENT"
  #_{:summary "When one of the convoying fleets is of the same nationality of the convoyed army, the \"intent\" is to convoy."
   :conflict-judgments {[:italy :army :rom :attack :apu] #{[:interfered? :interferer :rule]}
                        [:italy :fleet :tyn :convoy :turkey :army :apu :attack :rom] #{[:interfered? :interferer :rule]}
                        [:turkey :army :apu :attack :rom] #{[:interfered? :interferer :rule]}
                        [:turkey :fleet :ion :convoy :turkey :army :apu :attack :rom] #{[:interfered? :interferer :rule]}}
   :explanation "See issue 4.A.3. When the 1982/2000 rulebook is used (<i>which I prefer</i>), the convoy depends on the \"intent\". Since there is an own fleet in the  convoy, the intent is to convoy and the armies in Rome and  Apulia swap places.  For choices a, b and c of the issue there is also a convoy and the same swap takes place. When explicit adjacent convoying is used (DPTG, choice e), then the Turkish army did not receive an order to move by convoy. So, it is just a head to head battle and both the army in Rome and Apulia will not move."}
  ;; commented out because it uses a convoy
  #_"6.G.6. SWAPPING WITH UNINTENDED INTENT"
  #_{:summary "The intent is questionable."
   :conflict-judgments {[:england :army :lvp :attack :edi] #{[:interfered? :interferer :rule]}
                        [:england :fleet :eng :convoy :england :army :lvp :attack :edi] #{[:interfered? :interferer :rule]}
                        [:germany :army :edi :attack :lvp] #{[:interfered? :interferer :rule]}
                        [:france :fleet :iri :hold] #{}
                        [:france :fleet :nth :hold] #{}
                        [:russia :fleet :nrg :convoy :england :army :lvp :attack :edi] #{[:interfered? :interferer :rule]}
                        [:russia :fleet :nat :convoy :england :army :lvp :attack :edi] #{[:interfered? :interferer :rule]}}
   :explanation "See issue 4.A.3. For choice a, b and c the English army in Liverpool will move by convoy and consequentially the two armies are swapped. For choice d, the 1982/2000 rulebook (<i>which I prefer</i>), the convoy depends on the \"intent\". England intended to convoy via the French fleets in the Irish Sea and the North Sea. However, the French did not order the convoy. The alternative route with the Russian fleets was unintended.  The English fleet in the English Channel (with the convoy order) is not part of this alternative route with the Russian fleets. Since England still \"intent\" to convoy, the move from Liverpool to Edinburgh should be via convoy and the two armies are swapped. Although, you could argue that this is not really according to the clarification of the 2000 rulebook. When explicit adjacent convoying is used (DPTG, choice e), then the English army did not receive an order to move by convoy. So, it is just a head to head battle and both the army in Edinburgh and Liverpool will not move."}
  ;; commented out because it uses a convoy
  #_"6.G.7. SWAPPING WITH ILLEGAL INTENT"
  #_{:summary "Can the intent made clear with an impossible order?"
   :conflict-judgments {[:england :fleet :ska :convoy :russia :army :swe :attack :nwy] #{[:interfered? :interferer :rule]}
                        [:england :fleet :nwy :attack :swe] #{[:interfered? :interferer :rule]}
                        [:russia :army :swe :attack :nwy] #{[:interfered? :interferer :rule]}
                        [:russia :fleet :bot :convoy :russia :army :swe :attack :nwy] #{[:interfered? :interferer :rule]}}
   :explanation "See issue 4.A.3 and 4.E.1. If for issue 4.A.3 choice a, b or c has been taken, then the army in Sweden moves by convoy and swaps places with the fleet in Norway. However, if for issue 4.A.3 the 1982/2000 has been chosen (choice d), then the \"intent\" is important. The question is whether the fleet in the Gulf of Bothnia can express the intent. If the order for this fleet is considered illegal (see issue 4.E.1), then this order must be ignored and there is no intent to swap. In that case none of the units move. If explicit convoying is used (DPTG, choice e of issue 4.A.3) then the army in Sweden will take the land route and none of the units move. <i>I prefer the 1982/2000 rule and that any orders that can't be valid are illegal. So, the order of the fleet in the Gulf of Bothnia is ignored and can not show the intent. There is no convoy, so no unit will move.</i>"}
  "6.G.8. EXPLICIT CONVOY THAT ISN'T THERE"
  {:summary "What to do when a unit is explicitly ordered to move via convoy and the convoy is not there?"
   :conflict-judgments {[:france :army :bel :attack :hol] #{[:interfered? :interferer :rule]}
                        [:england :fleet :nth :attack :hel] #{[:interfered? :interferer :rule]}
                        [:england :army :hol :attack :kie] #{[:interfered? :interferer :rule]}}
   :explanation "The French army in Belgium intended to move convoyed with the English fleet in the North Sea. But the English changed their plans. See issue 4.A.3. If choice a, b or c has been taken, then the 'via Convoy' directive has no meaning and the army in Belgium will move to Holland. If the 1982/2000 rulebook is used (choice d, <i>which I prefer</i>), the \"via Convoy\" has meaning, but only when there is both a land route and a convoy route. Since there is no convoy the \"via Convoy\" directive should be ignored. And the move from Belgium to Holland succeeds. If explicit adjacent convoying is used (DPTG, choice e),  then the unit can only go by convoy. Since there is no convoy, the move from Belgium to Holland fails."}
  ;; commented out because it uses a convoy
  #_"6.G.9. SWAPPED OR DISLODGED?"
  #_{:summary "The 1982 rulebook says that whether the move is over land or via convoy depends on the \"intent\" as shown by the totality of the orders written by the player governing the army (see issue 4.A.3). In this test case the English army in Norway will end in all cases in Sweden. But whether it is convoyed or not has effect on the Russian army. In case of convoy the Russian army ends in Norway and in case of a land route the Russian army is dislodged."
   :conflict-judgments {[:england :army :nwy :attack :swe] #{[:interfered? :interferer :rule]}
                        [:england :fleet :ska :convoy :england :army :nwy :attack :swe] #{[:interfered? :interferer :rule]}
                        [:england :fleet :fin :support :england :army :nwy :attack :swe] #{[:interfered? :interferer :rule]}
                        [:russia :army :swe :attack :nwy] #{[:interfered? :interferer :rule]}}
   :explanation "See issue 4.A.3. For choice a, b and c the move of the army in Norway is by convoy and the armies in Norway and Sweden are swapped. If the 1982 rulebook is used with the clarification of the  2000 rulebook (choice d, <i>which I prefer</i>), the intent of the English player is to convoy, since it ordered the fleet in Skagerrak to convoy. Therefore, the armies in Norway and Sweden are swapped. When explicit adjacent convoying is used (DTPG, choice e), then the unit in Norway did not receive an order to move by convoy and the land route should be considered. The Russian army in Sweden is dislodged."}
  ;; commented out because it uses a convoy
  #_"6.G.10. SWAPPED OR AN HEAD TO HEAD BATTLE?"
  #_{:summary "Can a dislodged unit have effect on the attackers area, when the attacker moved by convoy?"
   :conflict-judgments {[:england :army :nwy :attack :swe] #{[:interfered? :interferer :rule]}
                        [:england :fleet :den :support :england :army :nwy :attack :swe] #{[:interfered? :interferer :rule]}
                        [:england :fleet :fin :support :england :army :nwy :attack :swe] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :ska :convoy :england :army :nwy :attack :swe] #{[:interfered? :interferer :rule]}
                        [:russia :army :swe :attack :nwy] #{[:interfered? :interferer :rule]}
                        [:russia :fleet :bar :support :russia :army :swe :attack :nwy] #{[:interfered? :interferer :rule]}
                        [:france :fleet :nrg :attack :nwy] #{[:interfered? :interferer :rule]}
                        [:france :fleet :nth :support :france :fleet :nrg :attack :nwy] #{[:interfered? :interferer :rule]}}
   :explanation "Since England ordered the army in Norway to move explicitly via convoy and the army in Sweden is moving in opposite direction, only the convoyed route should be considered regardless of the rulebook used. It is clear that the army in Norway will dislodge the Russian army in Sweden. Since  the strength of three is in all cases the strongest force. The army in Sweden will not advance to Norway, because it can not beat the force in the Norwegian Sea. It will be dislodged by the army from Norway. The more interesting question is whether French fleet in the Norwegian Sea is bounced by the Russian army from Sweden.  This depends on the interpretation of issue 4.A.7. If the rulebook is taken literally (choice a), then a dislodged unit can not bounce a unit in the area where the attacker came from. This would mean that the move of the fleet in the Norwegian Sea succeeds However, if choice b is taken (<i>which I prefer</i>), then a bounce is still possible, when there is no head to head battle. So, the  fleet in the Norwegian Sea will fail to move."}
  ;; commented out because it uses a convoy
  #_"6.G.11. A CONVOY TO AN ADJACENT PLACE WITH A PARADOX"
  #_{:summary "In this case the convoy route is available when the land route is chosen and the convoy route is not available when the convoy route is chosen."
   :conflict-judgments {[:england :fleet :nwy :support :england :fleet :nth :attack :ska] #{[:interfered? :interferer :rule]}
                        [:england :fleet :nth :attack :ska] #{[:interfered? :interferer :rule]}
                        [:russia :army :swe :attack :nwy] #{[:interfered? :interferer :rule]}
                        [:russia :fleet :ska :convoy :russia :army :swe :attack :nwy] #{[:interfered? :interferer :rule]}
                        [:russia :fleet :bar :support :russia :army :swe :attack :nwy] #{[:interfered? :interferer :rule]}}
   :explanation "See issue 4.A.2 and 4.A.3. If for issue 4.A.3, choice b, c or e has been taken, then the move from Sweden to Norway is not a convoy and the English fleet in Norway is dislodged and the fleet in Skagerrak will not be dislodged. If choice a or d (1982/2000 rule) has been taken for issue 4.A.3, then the move from Sweden to Norway must be treated as a convoy. At that moment the situation becomes paradoxical. When the 'All Hold' rule is used, both the army in Sweden as the fleet in the North Sea will not advance. In all other paradox rules the English fleet in the North Sea will dislodge the Russian fleet in Skagerrak and the army in Sweden will not advance. <i>I prefer the 1982 rule with the 2000 rulebook clarification concerning the convoy to adjacent places and I prefer the Szykman rule for paradox resolving. That means that according to these preferences the fleet in the North Sea will dislodge the Russian fleet in Skagerrak and the army in Sweden will not advance.</i>"}
  ;; commented out because it uses a convoy
  #_"6.G.12. SWAPPING TWO UNITS WITH TWO CONVOYS"
  #_{:summary "Of course, two armies can also swap by when they are both convoyed."
   :conflict-judgments {[:england :army :lvp :attack :edi] #{[:interfered? :interferer :rule]}
                        [:england :fleet :nat :convoy :england :army :lvp :attack :edi] #{[:interfered? :interferer :rule]}
                        [:england :fleet :nrg :convoy :england :army :lvp :attack :edi] #{[:interfered? :interferer :rule]}
                        [:germany :army :edi :attack :lvp] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :nth :convoy :germany :army :edi :attack :lvp] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :eng :convoy :germany :army :edi :attack :lvp] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :iri :convoy :germany :army :edi :attack :lvp] #{[:interfered? :interferer :rule]}}
   :explanation "The armies in Liverpool and Edinburgh are swapped."}
  ;; commented out because it uses a convoy
  #_"6.G.13. SUPPORT CUT ON ATTACK ON ITSELF VIA CONVOY"
  #_{:summary "If a unit is attacked by a supported unit, it is not possible to prevent  dislodgement by trying to cut the support. But what, if a move is attempted via a convoy?"
   :conflict-judgments {[:austria :fleet :adr :convoy :austria :army :tri :attack :ven] #{[:interfered? :interferer :rule]}
                        [:austria :army :tri :attack :ven] #{[:interfered? :interferer :rule]}
                        [:italy :army :ven :support :italy :fleet :alb :attack :tri] #{[:interfered? :interferer :rule]}
                        [:italy :fleet :alb :attack :tri] #{[:interfered? :interferer :rule]}}
   :explanation "First it should be mentioned that if for issue 4.A.3 choice b or c is taken, then the move from Trieste to Venice is just a move over land, because the army in Venice is not moving in opposite direction. In that case, the support of Venice will not be cut as normal. In any other choice for issue 4.A.3, it should be decided whether the Austrian attack is considered to be coming from Trieste or from the Adriatic Sea. If it comes from Trieste, the support in Venice is not cut and the army in Trieste is dislodged by the fleet in Albania. If the Austrian attack is considered to be coming from the Adriatic Sea, then the support is cut and the army in Trieste will not be dislodged. See also issue 4.A.4. <i>First of all, I prefer the 1982/2000 rules for adjacent convoying. This means that I prefer the move from Trieste uses the convoy. Furthermore, I think that the two Italian units are still stronger than the army in Trieste. Therefore, I prefer that the support in Venice is not cut and that the army in Trieste is dislodged by the fleet in Albania.</i>"}
  ;; commented out because it uses a convoy
  #_"6.G.14. BOUNCE BY CONVOY TO ADJACENT PLACE"
  #_{:summary "Similar to test case 6.G.10, but now the other unit is taking the convoy."
   :conflict-judgments {[:england :army :nwy :attack :swe] #{[:interfered? :interferer :rule]}
                        [:england :fleet :den :support :england :army :nwy :attack :swe] #{[:interfered? :interferer :rule]}
                        [:england :fleet :fin :support :england :army :nwy :attack :swe] #{[:interfered? :interferer :rule]}
                        [:france :fleet :nrg :attack :nwy] #{[:interfered? :interferer :rule]}
                        [:france :fleet :nth :support :france :fleet :nrg :attack :nwy] #{[:interfered? :interferer :rule]}
                        [:germany :fleet :ska :convoy :russia :army :swe :attack :nwy] #{[:interfered? :interferer :rule]}
                        [:russia :army :swe :attack :nwy] #{[:interfered? :interferer :rule]}
                        [:russia :fleet :bar :support :russia :army :swe :attack :nwy] #{[:interfered? :interferer :rule]}}
   :explanation "Again the army in Sweden is bounced by the fleet in the Norwegian Sea. The army in Norway will move to Sweden and dislodge the Russian army. The final destination of the fleet in the Norwegian Sea depends on how issue 4.A.7 is resolved. If choice a is taken, then the fleet advances to Norway, but if choice b is taken (<i>which I prefer</i>) the fleet bounces and stays in the Norwegian Sea."}
  ;; commented out because it uses a convoy
  #_"6.G.15. BOUNCE AND DISLODGE WITH DOUBLE CONVOY"
  #_{:summary "Similar to test case 6.G.10, but now both units use a convoy and without some support."
   :conflict-judgments {[:england :fleet :nth :convoy :england :army :lon :attack :bel] #{[:interfered? :interferer :rule]}
                        [:england :army :hol :support :england :army :lon :attack :bel] #{[:interfered? :interferer :rule]}
                        [:england :army :yor :attack :lon] #{[:interfered? :interferer :rule]}
                        [:england :army :lon :attack :bel] #{[:interfered? :interferer :rule]}
                        [:france :fleet :eng :convoy :france :army :bel :attack :lon] #{[:interfered? :interferer :rule]}
                        [:france :army :bel :attack :lon] #{[:interfered? :interferer :rule]}}
   :explanation "The French army in Belgium is bounced by the army from Yorkshire. The army in London move to Belgium, dislodging the unit there. The final destination of the army in the Yorkshire depends on how issue 4.A.7 is resolved. If choice a is taken, then the army advances to London, but if choice b is taken (<i>which I prefer</i>) the army bounces and stays in Yorkshire."}
  ;; commented out because it uses a convoy
  #_"6.G.16. THE TWO UNIT IN ONE AREA BUG, MOVING BY CONVOY"
  #_{:summary "If the adjudicator is not correctly implemented, this may lead to  a resolution where two units end up in the same area."
   :conflict-judgments {[:england :army :nwy :attack :swe] #{[:interfered? :interferer :rule]}
                        [:england :army :den :support :england :army :nwy :attack :swe] #{[:interfered? :interferer :rule]}
                        [:england :fleet :bal :support :england :army :nwy :attack :swe] #{[:interfered? :interferer :rule]}
                        [:england :fleet :nth :attack :nwy] #{[:interfered? :interferer :rule]}
                        [:russia :army :swe :attack :nwy] #{[:interfered? :interferer :rule]}
                        [:russia :fleet :ska :convoy :russia :army :swe :attack :nwy] #{[:interfered? :interferer :rule]}
                        [:russia :fleet :nrg :support :russia :army :swe :attack :nwy] #{[:interfered? :interferer :rule]}}
   :explanation "See decision details 5.B.6. If the 'PREVENT STRENGTH' is incorrectly implemented, due to the fact that it does not take into account that the 'PREVENT STRENGTH' is only zero when the unit is engaged in a head to head battle, then this goes wrong in this test case. The 'PREVENT STRENGTH' of Sweden would be zero,  because the opposing unit in Norway successfully moves. Since, this strength would be zero, the fleet in the North Sea would move to Norway. However, although the 'PREVENT STRENGTH' is zero, the army in Sweden would also move to Norway. So, the final result would contain two units that successfully moved to Norway. Of course, this is incorrect. Norway will indeed successfully move to Sweden while the army in Sweden ends in Norway, because it is stronger then the fleet in the North Sea. This fleet will stay in the North Sea."}
  ;; commented out because it uses a convoy
  #_"6.G.17. THE TWO UNIT IN ONE AREA BUG, MOVING OVER LAND"
  #_{:summary "Similar to the previous test case, but now the other unit moves by convoy."
   :conflict-judgments {[:england :army :nwy :attack :swe] #{[:interfered? :interferer :rule]}
                        [:england :army :den :support :england :army :nwy :attack :swe] #{[:interfered? :interferer :rule]}
                        [:england :fleet :bal :support :england :army :nwy :attack :swe] #{[:interfered? :interferer :rule]}
                        [:england :fleet :ska :convoy :england :army :nwy :attack :swe] #{[:interfered? :interferer :rule]}
                        [:england :fleet :nth :attack :nwy] #{[:interfered? :interferer :rule]}
                        [:russia :army :swe :attack :nwy] #{[:interfered? :interferer :rule]}
                        [:russia :fleet :nrg :support :russia :army :swe :attack :nwy] #{[:interfered? :interferer :rule]}}
   :explanation "Sweden and Norway are swapped, while the fleet in the North Sea will bounce."}
  ;; commented out because it uses a convoy
  #_"6.G.18. THE TWO UNIT IN ONE AREA BUG, WITH DOUBLE CONVOY"
  #_{:summary "Similar to the previous test case, but now both units move by convoy."
   :conflict-judgments {[:england :fleet :nth :convoy :england :army :lon :attack :bel] #{[:interfered? :interferer :rule]}
                        [:england :army :hol :support :england :army :lon :attack :bel] #{[:interfered? :interferer :rule]}
                        [:england :army :yor :attack :lon] #{[:interfered? :interferer :rule]}
                        [:england :army :lon :attack :bel] #{[:interfered? :interferer :rule]}
                        [:england :army :ruh :support :england :army :lon :attack :bel] #{[:interfered? :interferer :rule]}
                        [:france :fleet :eng :convoy :france :army :bel :attack :lon] #{[:interfered? :interferer :rule]}
                        [:france :army :bel :attack :lon] #{[:interfered? :interferer :rule]}
                        [:france :army :wal :support :france :army :bel :attack :lon] #{[:interfered? :interferer :rule]}}
   :explanation "Belgium and London are swapped, while the army in Yorkshire fails to move to London."}})

(defn-spec test-dict-incomplete? [::dt/test-dict] boolean?)
(defn ^:private test-dict-incomplete?
  "Returns whether the argument has any placeholder conflict judgments."
  [{:keys [conflict-judgments]}]
  (not-any? (partial = #{[:interfered? :interferer :rule]})
            (vals conflict-judgments)))

(def DATC-cases
  (->> DATC-cases-raw
       ;; Don't export test cases that don't have conflict judgments assigned.
       ;; Make sure that DATC-cases actually contains the tests you want to run!
       (filter (comp test-dict-incomplete? second))
       (map (fn [[name raw-test-dict]]
                [name (diplomacy.test-utils/expand-test raw-test-dict)]))
       (into {})))
