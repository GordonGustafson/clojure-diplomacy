(ns diplomacy.test-utils
  (:require [diplomacy.datatypes :as dt]
            [diplomacy.orders :refer [create-order]]
            [diplomacy.util :refer [defn-spec]]
            [clojure.spec :as s]))

(defn-spec create-conflict-judgments
  [(s/map-of ::dt/order-vector
             (s/coll-of (s/tuple ::interfered?
                                 ::dt/order-vector
                                 ::rule)))]
  ::dt/conflict-judgments)
(defn create-conflict-judgments [orders]
  "Judgment maps are verbose when written out in full (the keys are repeated
  many times). This function converts a form using more concise order vectors
  and judgment vectors into a judgments map."
  (into {} (for [[k v] orders]
             [(apply create-order k)
              (set (map (fn [[interfered? interferer rule]]
                          {:interferer (apply create-order interferer)
                           :rule rule
                           :interfered? interfered?})
                        v))])))

