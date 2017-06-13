(ns diplomacy.test-utils
  (:require [diplomacy.datatypes :as dt :refer [create-order]]
            [diplomacy.util :refer [defn-spec]]
            [clojure.spec :as s]))

(defn-spec create-judgments-map
  [(s/map-of ::dt/order-vector
             (s/coll-of (s/tuple ::bounced-by-bouncer?
                                 ::dt/order-vector
                                 ::rule)))]
  ::dt/judgments-map)
(defn create-judgments-map [orders]
  "Judgment maps are verbose when written out in full (the keys are repeated
  many times). This function converts a form using more concise order vectors
  and judgment vectors into a judgments map."
  (into {} (for [[k v] orders]
             [(apply create-order k)
              (set (map (fn [[bounced-by-bouncer? bouncer rule]]
                           {:bouncer (apply create-order bouncer)
                            :rule rule
                            :bounced-by-bouncer? bounced-by-bouncer?})
                        v))])))

