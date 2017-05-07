(ns diplomacy.resolution-test
  (:require [clojure.test :as test]
            [clojure.data]
            [diplomacy.datatypes :as dt :refer [create-order]]
            [diplomacy.resolution :refer [failed-attacks]]
            [diplomacy.rulebook-sample-game :refer [rulebook-sample-game-cases]]
            [diplomacy.util :refer [defn-spec map-difference]]
            [diplomacy.maps]))

;; Spring 1901 in the sample game has no supports or convoys, and only failures
;; due to both units moving to the same place.
(defn run-test-case [orders-map message]
  (let [actual-fails (failed-attacks (keys orders-map))
        expected-fails (into {} (filter (comp not empty? second) orders-map))
        ;; [incorrect-fails missing-fails _] (clojure.data/diff actual-fails
        ;;                                                      expected-fails)]
        ;; We *cannot* use `clojure.data/diff` here because "Maps are subdiffed
        ;; where keys match and values differ". We want to output exactly what
        ;; orders failed, and not do any sort of analysis on how the failing
        ;; orders differed (the user can do that much better themselves).
        incorrect-fails (map-difference actual-fails expected-fails)
        missing-fails   (map-difference expected-fails actual-fails)]
    (test/is (empty? incorrect-fails) message)
    (test/is (empty? missing-fails) message)))

(test/deftest test-rulebook-sample-game
  (doseq [[time orders-map] rulebook-sample-game-cases]
    (run-test-case orders-map (str "Rulebook sample game, "
                                   (pr-str time)))))


 ;; (def test-orders (map (partial apply dt/create-order)
 ;;                       [[:italy :army :ven :attack :tri]
 ;;                        ;; [:italy :army :pie :support
 ;;                        ;;  :italy :army :ven :attack :tri]
 ;;                        [:austria :army :tri :hold]
 ;;                        #_[:austria :army :alb :support
 ;;                         :austria :army :tri :hold]
 ;;                        #_[:turkey :army :gre :attack :alb]]))

 ;; (def- remove-invalid-orders-fall-1901
 ;;   (partial remove-invalid-orders classic-map (:initial-game-state classic-map)))

 ;; (deftest test-supporting-support-is-invalid
 ;;   (let [italian-army-ven-hold (create-order :italy :army :ven :hold)
 ;;         italian-army-rom-support-ven-hold
 ;;         (create-order :italy :army :rom :support italian-army-ven-hold)]
 ;;     (is (remove-invalid-orders-fall-1901
 ;;          #{italian-army-ven-hold
 ;;            italian-army-rom-support-ven-hold
 ;;            ;; It's a good thing this is invalid because it's very hard to read:
 ;;            (create-order :italy :fleet :nap :support
 ;;                          italian-army-rom-support-ven-hold)}
 ;;          #{italian-army-ven-hold
 ;;            italian-army-rom-support-ven-hold}))
 ;;     (is (remove-invalid-orders-fall-1901
 ;;          #{italian-army-ven-hold
 ;;            italian-army-rom-support-ven-hold
 ;;            (create-order :italy :fleet :nap :support
 ;;                          (create-order )}
 ;;          #{italian-army-ven-hold
 ;;            italian-army-rom-support-ven-hold}))
