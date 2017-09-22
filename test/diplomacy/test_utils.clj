(ns diplomacy.test-utils
  (:require [clojure.set :as set]
            [diplomacy.datatypes :as dt]
            [diplomacy.orders :refer [create-order]]
            [diplomacy.util :refer [defn-spec]]
            [clojure.spec.alpha :as s]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                               expanding shorthand notation ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn-spec create-validation-result
  [(s/or :valid (partial = :valid)
         :invalid (s/cat :failure-reasons ::validation-failure-reasons
                         :order-used ::order-used))]
  ::dt/validation-result)
(defn create-validation-result
  [raw-validation-result]
  (if (= raw-validation-result :valid)
    :valid
    {:validation-failure-reasons (first raw-validation-result)
     :order-used (apply create-order
                        (second raw-validation-result))}))

(defn-spec create-validation-results
  [(s/map-of ::dt/order-vector
             (s/or :valid (partial = :valid)
                   :invalid (s/cat :validation-failure-reasons
                                   ::validation-failure-reasons
                                   :order-used ::order-used)))]
  ::dt/validation-results)
(defn create-validation-results
  [order-vector-to-raw-validation-result]
  (into {} (map (fn [[order-vector raw-validation-result]]
                  [(apply create-order order-vector)
                   (create-validation-result raw-validation-result)])
                order-vector-to-raw-validation-result)))

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                       inferring optional parts of the test ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn-spec fill-in-missing-valid-orders
  [(s/keys :req-un [::conflict-judgments]
           :opt-un [::validation-results])]
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

(defn expand-adjudication
  [raw-adjudication]
  (-> raw-adjudication
      (update :conflict-judgments
              diplomacy.test-utils/create-conflict-judgments)
      (update :validation-results
              diplomacy.test-utils/create-validation-results)
      (fill-in-missing-valid-orders)))
