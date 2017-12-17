;; (ns diplomacy.DATC-cases
;;   (:require [diplomacy.test-expansion]
;;             [diplomacy.util :refer [defn-spec]]
;;             [diplomacy.datatypes :as dt]))

;; (def my-cases-abbr
;;  {"Moving a unit that doesn't exist"
;;   :unit-positions-before {}
;;   :conflict-judgments-abbr {[:england :fleet :nth :hold] #{}}})

;; (def my-cases
;;   (->> DATC-cases-abbr
;;        (map (fn [[name abbreviated-adjudication]]
;;                 [name (diplomacy.test-expansion/expand-adjudication
;;                        abbreviated-adjudication)]))
;;        (into {})))
