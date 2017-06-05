(ns diplomacy.test-utils
  (:require [diplomacy.datatypes :as dt :refer [create-order]]
            [diplomacy.util :refer [defn-spec]]
            [clojure.spec :as s]))

(defn-spec create-orders
  [(s/map-of (s/coll-of keyword?)
             (s/coll-of (s/tuple (s/coll-of keyword?) keyword?)))]
  (s/map-of ::dt/order
            (s/coll-of (s/tuple ::dt/order keyword?))))
(defn create-orders [orders]
  (into {} (for [[k v] orders]
             [(apply create-order k)
              (set (map (fn [[bounces? order rule]]
                          [bounces? (apply create-order order) rule])
                        v))])))

