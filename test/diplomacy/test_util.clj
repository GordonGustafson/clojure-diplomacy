(ns diplomacy.test-util
  (:require [clojure.test :refer [is]]
            [diplomacy.datatypes :as dt]
            [diplomacy.maps]
            [diplomacy.util :refer [defn-spec fn-spec]]
            [clojure.spec :as s]))

(defn-spec run-test-cases
  [(fn-spec [::dt/order]
            ::dt/failure-reasons)
   (s/map-of ::dt/failure-reasons
             (s/coll-of ::dt/order))]
  any?)
(defn run-test-cases [func test-case-dict]
  (doseq [[expected-failure-reasons orders] test-case-dict]
    (doseq [order orders]
      (is (= expected-failure-reasons (func order))))))
