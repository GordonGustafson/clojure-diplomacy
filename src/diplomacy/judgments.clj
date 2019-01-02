(ns diplomacy.judgments
  (:require [diplomacy.util :refer [defn-spec]]
            [diplomacy.datatypes :as dt]
            [clojure.spec.alpha :as s]))

;; These are just to change the name of the keyword arguments passed to these
;; functions.
;; TODO: rename the `::dt/attack-conflict-rule` and `::dt/support-conflict-rule`
;; to `::dt/attack-rule` and `::dt/support-rule` if you feel like putting in the
;; work.
(s/def ::attack-rule ::dt/attack-conflict-rule)
(s/def ::support-rule ::dt/support-conflict-rule)

(defn-spec create-attack-judgment
  (s/keys* :req-un [::dt/interferer ::attack-rule ::dt/interfered?]
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

;; This function is unnecessary, but provided for symmetry with
;; `create-attack-judgment`.
(defn-spec create-support-judgment
  (s/keys* :req-un [::dt/interferer ::support-rule
                    ::dt/interfered?])
  ::dt/conflict-judgment)
(defn create-support-judgment
  [& {:keys [interfered? interferer support-rule]}]
  {:interfered? interfered?
   :interferer interferer
   :conflict-situation support-rule})

