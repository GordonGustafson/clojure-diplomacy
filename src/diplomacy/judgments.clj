(ns diplomacy.judgments
  (:require [diplomacy.util :refer [defn-spec]]
            [diplomacy.datatypes :as dt]
            [clojure.spec.alpha :as s]))

(s/def ::attack-rule ::dt/attack-conflict-rule)
(defn-spec create-attack-judgment
  (s/keys* :req-un [::dt/interferer ::dt/attack-rule ::dt/interfered?]
           :opt-un [::dt/beleaguered-garrison-changing-outcome
                    ::dt/would-dislodge-own-unit?])
  ::dt/conflict-judgment)
(defn create-attack-judgment
  [& {:keys [interfered? interferer attack-rule
             would-dislodge-own-unit? beleaguered-garrison-changing-outcome]
      :or {would-dislodge-own-unit? false
           beleaguered-garrison-changing-outcome nil}}]
  {:interfered? interfered?
   :interferer interferer
   :conflict-situation {:attack-conflict-rule attack-rule
                        :beleaguered-garrison-changing-outcome
                        beleaguered-garrison-changing-outcome}
   :would-dislodge-own-unit? would-dislodge-own-unit?})
