(ns diplomacy.test-expansion
  (:require [diplomacy.orders :refer [get-unit attack?]]
            [clojure.set :as set]
            [clojure.core.match :refer [match]]
            [diplomacy.datatypes :as dt]
            [diplomacy.util :refer [defn-spec]]
            [clojure.spec.alpha :as s]))

;;; Verbose test cases are difficult ot write, nad difficult to understand when
;;; they fail. This module reduces the verbosity of test cases by expanding
;;; abbreviated notation used in test cases, and filling in parts of the test
;;; that can be derived or automatically generated.

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                               specs for shorthand notation ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def ::order-abbr
  (s/cat :country    ::dt/country
         :unit-type  ::dt/unit-type
         :location   ::dt/location
         :order-type ::dt/order-type
         ;; `rest` could be empty, a destination, or the arguments for the
         ;; assisted hold or attack. I'm not going to bother making this
         ;; spec more specific, but unfortunately that means we can't use
         ;; `s/exercise-fn` with this spec.
         :rest (s/* any?)))

(s/def ::validation-result-abbr
  (s/or :valid (partial = :valid)
        :invalid (s/cat :failure-reasons ::dt/validation-failure-reasons
                        :order-used ::order-abbr)))
(s/def ::validation-results-abbr (s/map-of ::order-abbr ::validation-result-abbr))

(s/def ::conflict-situation-abbr
  (s/or :support-rule ::dt/support-conflict-rule
        :attack-rule ::dt/attack-conflict-rule
        :attack-abbr (s/tuple ::dt/attack-conflict-rule ::order-abbr)))
(s/def ::conflict-judgment-abbr
  (s/cat :interfered?   ::dt/interfered?
         :interferer    ::order-abbr
         :conflict-situation ::dt/conflict-situation
         :would-dislodge-own-unit?
         (s/? ::dt/would-dislodge-own-unit?)))
(s/def ::resolution-results-abbr (s/map-of
                                  ::order-abbr
                                  (s/coll-of ::conflict-judgment-abbr)))

(s/def ::unit-positions-before ::dt/unit-positions)
(s/def ::supply-center-ownership-before ::dt/supply-center-ownership)
(s/def ::game-time-before ::dt/game-time)

(s/def ::unit-positions-after ::dt/unit-positions)
(s/def ::supply-center-ownership-after ::dt/supply-center-ownership)
(s/def ::game-time-after ::dt/game-time)

;; These are one layer shallower than ::completed-orders-phase. Instead of
;; having an outermost map containing two game-state maps, this puts all the
;; keys from the two game-state maps directly in the outermost map.
(s/def ::orders-phase-test-options-abbr
  (s/keys :req-un [::resolution-results-abbr]
          :opt-un [;; ::game-state-before-orders fields
                   ::unit-positions-before
                   ::supply-center-ownership-before
                   ::game-time-before
                   ;; stand-alone fields
                   ::validation-results-abbr
                   ;; ::game-state-after-orders fields
                   ::unit-positions-after
                   ::supply-center-ownership-after
                   ::game-time-after
                   ::dt/pending-retreats]))

;; Same as ::orders-phase-test-options-abbr, but with the abbreviations
;; expanded.
(s/def ::orders-phase-test-options
  (s/keys :req-un [::resolution-results]
          :opt-un [;; ::game-state-before-orders fields
                   ::unit-positions-before
                   ::supply-center-ownership-before
                   ::game-time-before
                   ;; stand-alone fields
                   ::validation-results
                   ;; ::game-state-after-orders fields
                   ::unit-positions-after
                   ::supply-center-ownership-after
                   ::game-time-after
                   ::dt/pending-retreats]))

;; Orders phase test with as many optional fields filled in automatically as
;; possible.
(s/def ::orders-phase-test
  (s/keys :req-un [;; ::game-state-before-orders fields
                   ::unit-positions-before
                   ::supply-center-ownership-before
                   ::game-time-before
                   ;; stand-alone fields
                   ::validation-results
                   ::resolution-results
                   ;; ::game-state-after-orders fields
                   ::supply-center-ownership-after
                   ::game-time-after]
          ;; We have no way of filling in these fields in
          ;; ::game-state-after-orders, so they will only be present if they are
          ;; provided in the test definition.
          :opt-un [::unit-positions-after
                   ::dt/pending-retreats]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                               expanding shorthand notation ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn-spec expand-order ::order-abbr ::dt/order)
(defn expand-order
  "A shorthand for writing orders in Clojure. Intended for 'order literals' in
  source code rather than taking user input, so errors are handled with
  exceptions. Usage:

  (expand-order :england :army  :wal :hold)
  (expand-order :england :army  :lvp :support :england :army :wal :hold)
  (expand-order :france  :army  :bre :attack  :lon)
  (expand-order :france  :fleet :eng :convoy  :france  :army :bre :attack :lon)

  PRECONDITION: Constructed order must be valid in some diplomacy map.
  "
  [country unit-type location order-type & rest]
  (let [basic-order {:country  country  :unit-type  unit-type
                     :location location :order-type order-type}
        ;; `match` won't let multiple patterns map to the same expression, so we
        ;; put the expression in a thunk to avoid duplication.
        make-assisting-order
        (fn [] (assoc basic-order
                      :assisted-order (apply expand-order rest)))]
    (match [unit-type order-type rest]
      [_      :hold    nil]           basic-order
      [_      :attack  ([dest] :seq)]
      (assoc basic-order :destination dest)
      [_      :support ([_ _     _ :hold] :seq)]     (make-assisting-order)
      [_      :support ([_ _     _ :attack _] :seq)] (make-assisting-order)
      [:fleet :convoy  ([_ :army _ :attack _] :seq)] (make-assisting-order))))

(defn-spec order-to-abbr [::dt/order] ::order-abbr)
(defn order-to-abbr
  "Inverse of `expand-order`. Intended for generating concise error messages"
  [order]
  (let [base (map order [:country :unit-type :location :order-type])
        extra (case (:order-type order)
                :hold []
                :attack [(:destination order)]
                :support (order-to-abbr (:assisted-order order))
                :convoy (order-to-abbr (:assisted-order order)))]
    (vec (concat base extra))))

(defn-spec expand-validation-result
  [::validation-result-abbr] ::dt/validation-result)
(defn expand-validation-result
  [raw-validation-result]
  (if (= raw-validation-result :valid)
    :valid
    (let [[failure-reasons order-used] raw-validation-result]
      {:validation-failure-reasons failure-reasons
       :order-used (if (nil? order-used)
                     nil
                     (apply expand-order order-used))})))

(defn-spec expand-validation-results
  [::validation-results-abbr] ::dt/validation-results)
(defn expand-validation-results
  [order-abbr-to-raw-validation-result]
  (into {} (map (fn [[order-abbr raw-validation-result]]
                  [(apply expand-order order-abbr)
                   (expand-validation-result raw-validation-result)])
                order-abbr-to-raw-validation-result)))

(defn-spec expand-conflict-situation
  [::conflict-situation-abbr] ::dt/conflict-situation)
(defn expand-conflict-situation
  [raw-conflict-situation]
  (cond
    (s/valid? ::dt/support-conflict-rule raw-conflict-situation)
    raw-conflict-situation
    (s/valid? ::dt/attack-conflict-rule raw-conflict-situation)
    {:attack-conflict-rule raw-conflict-situation
     :beleaguered-garrison-changing-outcome nil}
    (vector? raw-conflict-situation)
    (let [[rule beleaguered-garrison-abbr] raw-conflict-situation]
      {:attack-conflict-rule rule
       :beleaguered-garrison-changing-outcome
       (apply expand-order beleaguered-garrison-abbr)})))

(defn-spec expand-resolution-results
  [::resolution-results-abbr] ::dt/resolution-results)
(defn expand-resolution-results
  "Judgment maps are verbose when written out in full (the keys are repeated
  many times). This function converts a form using more concise order
  abbreviations and judgment abbreviations into a judgments map."
  [orders]
  (into {}
        (for [[k v] orders]
          (let [expanded-order (apply expand-order k)]
            [expanded-order
             (set (map (fn [{interfered? 0
                             interferer 1
                             situation-abbr 2
                             would-dislodge-own-unit? 3
                             ;; would-dislodge-own-unit? is optional in
                             ;; ::resolution-results-abbr.
                             :or {would-dislodge-own-unit? false}}]
                         (let [res {:interferer (apply expand-order interferer)
                                    :conflict-situation
                                    (expand-conflict-situation situation-abbr)
                                    :interfered? interfered?}]
                           (if (attack? expanded-order)
                             (assoc res :would-dislodge-own-unit?
                                    would-dislodge-own-unit?)
                             res)))
                       v))]))))

(defn-spec expand-orders-phase-test-options
  [::orders-phase-test-options-abbr] ::orders-phase-test-options)
(defn expand-orders-phase-test-options
  "Expands all abbreviated components of an `::orders-phase-test-options-abbr`."
  [test-options-abbr]
  (let [validation-results-provided?
        (contains? test-options-abbr :validation-results-abbr)]
    (cond-> test-options-abbr
      true (update :resolution-results-abbr
                   expand-resolution-results)
      ;; calling `update` on a key that doesn't exist invokes the function with
      ;; `nil`, which isn't we want, so don't `update` unless the key is
      ;; present.
      validation-results-provided? (update :validation-results-abbr
                                           expand-validation-results)
      true (set/rename-keys {:resolution-results-abbr :resolution-results,
                             :validation-results-abbr :validation-results}))))

(defn-spec orders-to-unit-positions
  [::dt/orders] ::dt/unit-positions)
(defn orders-to-unit-positions
  "The `::dt/unit-positions` that `::dt/orders` occurs in, assuming
  `::dt/orders` contains an order for every unit that exists on the map, and no
  orders for units that don't exist."
  [orders]
  (into {} (map (juxt :location get-unit)
                orders)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                      filling in optional parts of the test ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ::resolution-results is required.
;;
;; :game-time-before and :game-time-after are filled in as
;; {:year 999 :season :fall} (game-time doesn't change during the orders phase).
;;
;; :supply-center-ownership-before and :supply-center-ownership-after are both
;; filled in as an empty dictionary. It is impossible for no supply centers to
;; be owned, but we allow it. (supply-center-ownership doesn't change during the
;; orders phase).
;;
;; :validation-results and :unit-positions-before are filled in by assuming
;; every unit was given a valid order.
;;
;; :unit-positions-after and :pending-retreats are not filled in, and will not
;; be tested unless they are directly specified in the test.

(defn-spec fill-in-game-times [(s/keys)]
  (s/keys :req-un [::game-time-before ::game-time-after]))
(defn fill-in-game-times
  "Adds default values for :game-time-after and/or :game-time-after if they are
  not present in `test-case`"
  [test-case]
  (merge {:game-time-before {:year 999 :season :fall}
          :game-time-after {:year 999 :season :fall}}
         test-case))

(defn-spec fill-in-supply-center-ownerships [(s/keys)]
  (s/keys :req-un [::supply-center-ownership-before
                   ::supply-center-ownership-after]))
(defn fill-in-supply-center-ownerships
  "Adds default values for :supply-center-ownership-before and/or
  :supply-center-ownership-after if they are not present in `test-case`"
  [test-case]
  (merge {:supply-center-ownership-before {}
          :supply-center-ownership-after {}}
         test-case))

(defn-spec fill-in-missing-valid-orders
  [(s/keys :req-un [::dt/resolution-results]
           :opt-un [::dt/validation-results])]
  (s/keys :req-un [::dt/resolution-results
                   ::dt/validation-results]))
(defn ^:private fill-in-missing-valid-orders
  "Adds each key (order) from `:resolution-results` as a valid order in
  `:validation-results` if there is no entry in `:validation-results` specifying
  how that order was obtained from the orders the player gave."
  [test-case]
  (let [raw-validation-results (get test-case :validation-results {})
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
        ;; :resolution-results.
        missing-resolved-orders
        (set/difference (set (-> test-case :resolution-results keys))
                        resolved-orders-accounted-for)
        filled-in-validation-results
        (merge raw-validation-results
               (zipmap missing-resolved-orders (repeat :valid)))]
    (assoc test-case :validation-results filled-in-validation-results)))

(defn-spec fill-in-unit-positions-before [(s/keys :req-un
                                                  [::dt/resolution-results])]
  (s/keys :req-un [::dt/resolution-results ::unit-positions-before]))
(defn fill-in-unit-positions-before
  "Adds a derived value for :unit-positions-before if it is not present in
  `test-case`. The value is derived by assuming that every unit was given a
  valid order."
  [{:keys [resolution-results] :as test-case}]
  (if (contains? test-case :unit-positions-before)
    test-case
    (assoc test-case :unit-positions-before
           (orders-to-unit-positions (keys resolution-results)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                     fully expanding a test ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn-spec expand-and-fill-in-orders-phase-test
  [::orders-phase-test-options-abbr] ::orders-phase-test)
(defn expand-and-fill-in-orders-phase-test
  "Fully expands and fills in all optional components of an
  `::orders-phase-test-options-abbr`."
  [test-options-abbr]
  (-> test-options-abbr
      expand-orders-phase-test-options
      fill-in-game-times
      fill-in-supply-center-ownerships
      fill-in-missing-valid-orders
      fill-in-unit-positions-before))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                     fully expanding a test ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn-spec judgments-equal-enough? [::dt/judgment :dt/judgment] boolean?)
(defn judgments-equal-enough?
  "To avoid making our tests too verbose, we ignore certain parts of
  `dt/judgment`s so we don't have to provide expected values for them in every
  single test. This function returns whether two judgments are equal if we
  ignore those parts."
  [expected actual]
  (or (= expected actual)
      (and (= (:interferer expected) (:interferer actual))
           (= (:interfered? expected) (:interfered? actual))
           (= (:would-dislodge-own-unit? expected)
              (:would-dislodge-own-unit? actual))
           (= (get-in expected [:conflict-situation ::attack-conflict-rule])
              (get-in actual [:conflict-situation ::attack-conflict-rule]))
           (= (get-in expected [:conflict-situation ::would-dislodge-own-unit?])
              (get-in actual [:conflict-situation ::would-dislodge-own-unit?])))))
