(ns diplomacy.web.json-serialization
  (:require [diplomacy.datatypes :as dt]
            [diplomacy.util :refer [defn-spec]]
            [clojure.spec.alpha :as s]))

;;; JSON doesn't allow maps with non-string keys, and Javascript doesn't support
;;; EDN that uses non-string keys. This module helps accomodate that.

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                specs for json data formats ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def ::resolution-results-for-json
  (s/coll-of (s/tuple ::dt/order
                      ::dt/conflict-judgments)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                converting to and from json representations ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn-spec jsonify-resolution-results [::dt/resolution-results]
  ::resolution-results-for-json)
(defn jsonify-resolution-results
  [resolution-results]
  (into #{} resolution-results))

(defn-spec unjsonify-resolution-results [::resolution-results-for-json]
  ::dt/resolution-results)
(defn unjsonify-resolution-results
  [resolution-results-for-json]
  (into {} resolution-results-for-json))
