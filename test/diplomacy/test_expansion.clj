(ns diplomacy.test-expansion
  (:require [clojure.set :as set]
            [diplomacy.datatypes :as dt]
            [diplomacy.orders :refer [expand-order]]
            [diplomacy.util :refer [defn-spec]]
            [clojure.spec.alpha :as s]))

;;; Verbose test cases are difficult ot write, nad difficult to understand when
;;; they fail. This module reduces the verbosity of test cases by expanding
;;; abbreviated notation used in test cases, and filling in parts of the test
;;; that can be derived or automatically generated.

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                               expanding shorthand notation ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn-spec expand-validation-result
  [::dt/validation-result-abbr] ::dt/validation-result)
(defn expand-validation-result
  [raw-validation-result]
  (if (= raw-validation-result :valid)
    :valid
    {:validation-failure-reasons (first raw-validation-result)
     :order-used (apply expand-order
                        (second raw-validation-result))}))

(defn-spec expand-validation-results
  [::dt/validation-results-abbr] ::dt/validation-results)
(defn expand-validation-results
  [order-abbr-to-raw-validation-result]
  (into {} (map (fn [[order-abbr raw-validation-result]]
                  [(apply expand-order order-abbr)
                   (expand-validation-result raw-validation-result)])
                order-abbr-to-raw-validation-result)))

(defn-spec expand-conflict-judgments
  [::dt/conflict-judgments-abbr] ::dt/conflict-judgments)
(defn expand-conflict-judgments [orders]
  "Judgment maps are verbose when written out in full (the keys are repeated
  many times). This function converts a form using more concise order
  abbreviations and judgment abbreviations into a judgments map."
  (into {} (for [[k v] orders]
             [(apply expand-order k)
              (set (map (fn [[interfered? interferer rule]]
                          {:interferer (apply expand-order interferer)
                           :conflict-rule rule
                           :interfered? interfered?})
                        v))])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                      filling in optional parts of the test ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn-spec fill-in-missing-valid-orders
  [(s/keys :req-un [::dt/conflict-judgments]
           :opt-un [::dt/validation-results])]
  ::dt/adjudication)
(defn ^:private fill-in-missing-valid-orders
  "Adds each key (order) from `:conflict-judgments` as a valid order in
  `:validation-results` if there is no entry in `:validation-results` specifying
  how that order was obtained from the orders the player gave."
  [adjudication]
  (let [raw-validation-results (get adjudication :validation-results {})
        resolved-orders-accounted-for
        (set (map (fn [[order-given validation-result]]
                    (if (= validation-result :valid)
                      order-given
                      (:order-used validation-result)))
                  raw-validation-results))
        ;; Orders that are to be fed to the resolution engine but aren't
        ;; mentioned in the validation results. To allow greater brevity in the
        ;; test cases, we add all of these to validation-results as valid
        ;; orders. This means you never need to specify valid orders in
        ;; :validation-results if you specify them as keys in
        ;; :conflict-judgments.
        missing-resolved-orders
        (set/difference (set (-> adjudication :conflict-judgments keys))
                        resolved-orders-accounted-for)
        filled-in-validation-results
        (merge raw-validation-results
               (zipmap missing-resolved-orders (repeat :valid)))]
    (assoc adjudication :validation-results filled-in-validation-results)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                     fully expanding a test ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn-spec expand-adjudication
  [::dt/adjudication-abbr] ::dt/adjudication)
(defn expand-adjudication
  [{:keys [conflict-judgments-abbr validation-results-abbr]
    :as abbreviated-adjudication}]
  (let [conflict-judgments (expand-conflict-judgments conflict-judgments-abbr)
        validation-results (expand-validation-results validation-results-abbr)]
    (-> abbreviated-adjudication
        (assoc :conflict-judgments conflict-judgments)
        (assoc :validation-results validation-results)
        (dissoc :conflict-judgments-abbr)
        (dissoc :validation-results-abbr)
        (fill-in-missing-valid-orders))))
