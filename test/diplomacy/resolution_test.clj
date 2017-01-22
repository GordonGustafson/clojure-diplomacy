(ns diplomacy.resolution-test
  (:require [clojure.test :refer [deftest]]
            [diplomacy.datatypes :refer [create-order]]
            [diplomacy.resolution :refer [failed-attacks]]
            [diplomacy.rulebook-sample-game :refer [rulebook-sample-game-turns]]
            [diplomacy.maps]
            [diplomacy.test-util :refer [run-test-cases]]))

(deftest test-attacks-current-location?
  (test-util/run-test-cases failed-attacks
   {
    []}))

(deftest test-rulebook-sample-game-validation
  ;; Use `(apply concat ...)` instead of `(flatten ...)` because `flatten`
  ;; creates one giant order by flattening *all* the layers!
  (let [rulebook-sample-game-orders
        (apply concat (vals rulebook-sample-game-turns))]
    (test-util/run-test-cases validation-failure-reasons-in-classic-map
                              {#{} rulebook-sample-game-orders})))





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
