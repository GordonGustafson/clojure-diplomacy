(ns diplomacy.resolution-test
  (:require [clojure.test :as test]
            [diplomacy.datatypes :as dt]
            [diplomacy.resolution]
            [diplomacy.rulebook-sample-game]
            [diplomacy.rulebook-diagrams]
            [diplomacy.util :refer [defn-spec map-difference]]
            [clojure.spec.alpha :as s]))

(defn run-test-case [orders-map message]
  (let [actual-judgments (diplomacy.resolution/conflict-judgments
                          (keys orders-map))
        expected-judgments (into {}
                                 (filter (comp not empty? second) orders-map))
        ;; We *cannot* use `clojure.data/diff` here because "Maps are subdiffed
        ;; where keys match and values differ". We want to output exactly what
        ;; orders failed, and not do any sort of analysis on how the failing
        ;; orders differed (the user can do that much better themselves).
        incorrect-judgments (map-difference actual-judgments expected-judgments)
        missing-judgments   (map-difference expected-judgments actual-judgments)
        ]
    (test/is (empty? incorrect-judgments) message)
    (test/is (empty? missing-judgments) message)))

(test/deftest test-rulebook-sample-game
  (doseq [[time orders-map]
          diplomacy.rulebook-sample-game/rulebook-sample-game-judgments]
    (run-test-case orders-map (str "Rulebook sample game, "
                                   (pr-str time)))))

(test/deftest test-rulebook-diagrams
  (doseq [[diagram-number orders-map]
          diplomacy.rulebook-diagrams/rulebook-diagram-judgments]
    (run-test-case orders-map (str "Rulebook diagram "
                                   (pr-str diagram-number)))))

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
