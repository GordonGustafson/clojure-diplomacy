(ns diplomacy.map-functions-test
  (:require [clojure.test :refer [deftest is]]
            [diplomacy.map-functions :refer :all]
            [diplomacy.map-data :refer [classic-map]]))


(deftest test-location-accessible-to?
  (is (location-accessible-to? classic-map :par :army))
  (is (location-accessible-to? classic-map :ion :fleet))
  (is (location-accessible-to? classic-map :tun :army))
  (is (location-accessible-to? classic-map :tun :fleet))
  (is (not (location-accessible-to? classic-map :par :fleet)))
  (is (not (location-accessible-to? classic-map :ion :army)))
  (is (not (location-accessible-to? classic-map :foo :fleet)))
  (is (not (location-accessible-to? classic-map :foo :army))))

(deftest test-edge-accessible-to?
  (is (edge-accessible-to? classic-map :par :bur :army))
  (is (edge-accessible-to? classic-map :ion :adr :fleet))
  (is (edge-accessible-to? classic-map :tun :naf :army))
  (is (edge-accessible-to? classic-map :tun :naf :fleet))
  (is (not (edge-accessible-to? classic-map :par :bur :fleet)))
  (is (not (edge-accessible-to? classic-map :ion :adr :army)))
  (is (not (edge-accessible-to? classic-map :par :ank :fleet)))
  (is (not (edge-accessible-to? classic-map :par :ank :army)))
  (is (not (edge-accessible-to? classic-map :foo :ank :fleet)))
  (is (not (edge-accessible-to? classic-map :par :foo :army))))

(deftest test-locations-colocated?
  (is (locations-colocated? classic-map :spa    :spa-nc))
  (is (locations-colocated? classic-map :spa    :spa-sc))
  (is (locations-colocated? classic-map :spa-nc :spa-sc))
  (is (locations-colocated? classic-map :spa :spa))
  (is (locations-colocated? classic-map :spa-nc :spa-nc))
  (is (locations-colocated? classic-map :spa-sc :spa-sc))
  (is (locations-colocated? classic-map :par :par))
  (is (not (locations-colocated? classic-map :spa :par))))

(deftest test-supply-center?
  (is (supply-center? classic-map :mun))
  (is (not (supply-center? classic-map :naf))))

(deftest test-get-home-supply-centers
  (is (= (home-supply-centers classic-map :italy) #{:rom :ven :nap})))
