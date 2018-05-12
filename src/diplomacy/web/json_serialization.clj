(ns diplomacy.web.json-serialization
  (:require [diplomacy.datatypes :as dt]
            [diplomacy.util :refer [defn-spec]]
            [clojure.spec.alpha :as s]))

;;; JSON doesn't allow maps with non-string keys, and Javascript doesn't support
;;; EDN that uses non-string keys. This module helps accomodate that.

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                specs for json data formats ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; This spec permits string keys, but is named to imply why we might need to do
;; any JSON-friendly conversion in the first place.
(s/def ::map-with-non-string-keys
  (s/map-of any? any?))
(s/def ::map-with-non-string-keys-for-json
  (s/coll-of (s/tuple any? any?)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                converting to and from json representations ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn-spec jsonify-map-with-non-string-keys [::map-with-non-string-keys]
  ::map-with-non-string-keys-for-json)
(defn jsonify-map-with-non-string-keys
  [map]
  (into #{} map))

(defn-spec unjsonify-map-with-non-string-keys [::map-with-non-string-keys-for-json]
  ::map-with-non-string-keys)
(defn unjsonify-map-with-non-string-keys
  [collection-of-pairs]
  (into {} collection-of-pairs))
