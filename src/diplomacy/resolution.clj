(ns diplomacy.resolution
  (:refer-clojure :exclude [==])
  (:use [clojure.core.logic])
  (:require [clojure.core.logic.pldb :as pldb]
            [diplomacy.datatypes :as dt]
            [diplomacy.maps :refer [classic-map]]
            [diplomacy.util :refer [defn-spec]]
            [clojure.set]
            [clojure.spec :as s]))

;; TODO: convoys, dislodging convoys

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                       core.logic Utilities ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO: can these be functions instead of macros?

(defmacro multi-pred
  "A version of `clojure.core.logic/pred` for predicates that take multiple
  arguments"
  [f & args]
  (let [list-name (gensym)]
    `(fresh [~list-name]
       (== ~list-name [~@args])
       (pred ~list-name #(apply ~f %)))))

;; Taken from Norman Richards' post here:
;; https://groups.google.com/forum/#!topic/minikanren/-py197HTgZA
(defmacro fail-if
  "fail if the given goal succeeds, use with extreme caution"
  [goal]
  `(conda [~goal fail]
          [succeed]))

;; Core.logic stores active databases in `(def ^:dynamic *logic-dbs* [])`,
;; which `pldb/with-db` sets using `binding`. This means that code only has
;; access to the db from `with-db` when it is **executed** 'inside' the
;; `with-db`. `run` generates a lazy sequence, so code inside `run` will not be
;; executed 'inside' the `with-db` block unless we force evaluation with
;; `doall` (https://cemerick.com/2009/11/03/be-mindful-of-clojures-binding/).
;; Thus, removing this `doall` causes nested runs to run with an *empty* fact
;; database!
;;
;; There are other solutions worth considering:
;; - pass the fact database all the way down to the site of the nested run
;; - set our own global var for the fact database (instead of piggybacking on
;;     core.logic's internal ^:dynamic var)
;; - eliminate the need for nested runs (I use them to count the possible values
;;     a variable can take).
;; - implement findall in core.logic http://dev.clojure.org/jira/browse/LOGIC-68
(defmacro run-db*-with-nested-runs [db bindings goals]
  "Like `run-db*`, except calls to `run` in `goals` also use `db` for their fact
  database."
  `(pldb/with-db ~db
     (doall
      (run* ~bindings ~goals))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                             Diplomacy Orders in core.logic ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; These relations help express diplomacy order in core.logic, and contain no
;; logic for *resolving* the orders.

;; Puts an order dictionary directly into the fact database.
(pldb/db-rel raw-order order-map)

(defmacro fresh-order
  "Like fresh, but all of the fresh variables are constrained with `raw-order`"
  [name-vector & body]
  (let [raw-order-constraints (map (fn [name] `(raw-order ~name))
                                   name-vector)]
    `(fresh ~name-vector
       ~@raw-order-constraints
       ~@body)))

(defn holdo
  "Relation where `order` attempts to hold at `location`"
  [order location]
  (all
   (raw-order order)
   (featurec order {:order-type :hold
                    :location location})))

(defn attacko
  "Relation where `order` attempts to attack from `from` to `to`"
  [order from to]
  (all
   (raw-order order)
   (featurec order {:order-type :attack
                    :location from
                    :destination to})))

(defn supported-order-matcheso
  "Relation where supporting `supported-order` would support `order`. This is
  more complex than whether they unify because supporting a hold can also
  indicate supporting a unit that's supporting or convoying."
  [supported-order order]
  (conde
   ;; pg 7: A unit ordered to move can only be supported by a support order that
   ;; matches the move the unit is trying to make..
   [(== supported-order order)]
   ;; pg 7: A unit not ordered to move can be supported by a support order that
   ;; only mentions its province.
   [(fresh [location]
      (featurec supported-order {:order-type :hold
                                 :location location})
      (conde
       [(featurec order {:order-type :support
                         :location location})]
       [(featurec order {:order-type :convoy
                         :location location})]))]))

(defn supporto
  "Relation where `order` attempts to remain at `location` while supporting
  `supported-order`"
  [order location supported-order]
  (fresh [actual-order-supported]
    (raw-order order)
    (raw-order supported-order)
    (featurec order {:order-type :support
                     :location location
                     :assisted-order actual-order-supported})
    (supported-order-matcheso actual-order-supported supported-order)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                 Resolving Diplomacy Orders ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 'bounced': attack failed due to conflict with another unit.
;; 'advanced': attack succeeded

(defn support-succeedso
  "Relation where `supporting-order` successfully supports `supported-order`"
  [supporting-order supported-order]
  (fresh [supporter-location
          supported-location]
    (supporto supporting-order supporter-location supported-order)
    ;; pg 10: Support is cut if the unit giving support is attacked from any
    ;; province except the one where support is being given
    ;;
    ;; TODO: pg 12: Support is cut if the unit giving support is dislodged (even
    ;; if it is dislodged from the province into which it's giving support).
    ;;
    ;; TODO: pg 15: A country can't support the dislodgement of one of its own
    ;; units.
    ;;
    ;; TODO: pg 16: An attack by a country on one of its own units doesn't cut
    ;; support.
    (fail-if
     (fresh [cutting-attack-from]
      (!= cutting-attack-from supported-location)
      (attacko (lvar 'cutting-attack)
               cutting-attack-from
               supporter-location)))))

(defn supporter-count
  "Number of units that successfully support `supported-order`. Non-relational."
  [supported-order]
  ;; TODO: can this `run*` return the same order multiple times? Shouldn't
  ;;       matter because of the call to `set` afterwards, but I'm curious.
  (when (empty? clojure.core.logic/*logic-dbs*)
    ;; This helps more than it hurts at the moment; it's hard to notice that
    ;; you're using an empty fact database.
    (throw (IllegalStateException.
            "nested run running with empty fact database!")))
  (count (set (run* [supporting-order]
                (support-succeedso supporting-order supported-order)))))

(defn has-fewer-or-equal-supporters
  "Whether `order-a` has fewer or equally many successful supporters as
  `order-b`. Non-relational."
  [order-a order-b]
  (<= (supporter-count order-a)
      (supporter-count order-b)))

(defn has-no-supporters
  "Whether `order` has no successful supporters. Non-relational."
  [order]
  (zero? (supporter-count order)))

(declare attack-bounce-rulingo-impl)

(defn attack-bounce-rulingo
  "Relation where:
  - `attack-order` is an attack whose outcome we would like to evaluate.
  - `interfering-order` is an order that *could potentially* bounce the attack
    due to a rule for conflicting orders.
  - `bounced-by-interfering-order?` is a boolean indicating whether the attack
    *actually was* bounced by `interfering-order`.
  - `rules-used` contains keywords representing the rules used to decide the
    value of `bounced-by-interfering-order?`.

  The following are 'input parameters' that must be ground when they are passed:
  - `attack-order`

  The following are 'output parameters' that must be fresh when they are passed,  and will be constrained by this relation:
  - `interfering-order`
  - `bounced-by-interfering-order?`
  - `rules-used`

  The resulting values of `interfering-order` and `rules-used` are only useful
  for explaining *why* `attack-order` bounced; `attack-order` bounced
  if-and-only-if this relation unifies with `bounced-by-interfering-order? ==
  true` for some values of `interfering-order` and `rules-used`. If
  `attack-order` bounced, it may also unify with `bounced-by-interfering-order?
  == false`, because an attack that is bounced by one bounce-candidate and
  bounced by another is still bounced.
  "
  [attack-order interfering-order bounced-by-interfering-order? rules-used]
  ;; Doesn't work when I tried passing an empty set instead of an empty vector,
  ;; but maybe I can change something to fix that?
  (attack-bounce-rulingo-impl attack-order interfering-order
                               bounced-by-interfering-order? rules-used []))

(defn ^:private attack-bounce-rulingo-impl
  "Backing function for `attack-bounce-rulingo`"
  ;; `attacks-assumed-successful` is necessary to allow three or more units to
  ;; 'rotate' in a cycle (all move to the next unit's position). Without it,
  ;; each `attack-bounce-rulingo-impl` goal in the cycle depends on whether the
  ;; attack leaving its destination bounces, causing an infinite loop.
  [attack-order interfering-order bounced-by-interfering-order? rules-used
   attacks-assumed-successful]
  (fresh [from to]
    (attacko attack-order from to)
    (conde
     ;; An attack tried to leave our destination but failed
     [(all
       (fresh [other-to new-attacks-assumed-successful]
         (!= other-to from)
         (attacko interfering-order to other-to)
         ;; Fail if we assumed `interfering-order` successfully vacated our
         ;; destination.
         (fail-if (membero interfering-order attacks-assumed-successful))
         ;; Assume this attack advanced
         (conso attack-order attacks-assumed-successful
                new-attacks-assumed-successful)
         ;; We bounce if the order vacating our destination bounced, so we pass
         ;; our own `bounced-by-interfering-order?` here. We don't care why the
         ;; vacating order bounced, so we pass lvars that are never used
         ;; anywhere else.
         (attack-bounce-rulingo-impl
          interfering-order
          bounced-by-interfering-order?
          (lvar 'rules-used-for-interfering-order)
          (lvar 'interfering-order-for-interfering-order)
          new-attacks-assumed-successful))
       ;; Since the attack out of our destination bounced, it's support doesn't
       ;; help it maintain its original position. We will only fail to dislodge
       ;; it if we have no support (1v1).
       (pred attack-order
             has-no-supporters))]

     [(all
       (conde
        [(holdo    interfering-order to)]        ; attack occupied location
        [(supporto interfering-order to (lvar 'supported-order))]
        [(attacko interfering-order to from)]    ; swap places
        [(fresh [other-from]                     ; different attack on same place
           (!= other-from from)  ; make sure we don't bounce ourselves because
                                 ; we're attacking the same place as ourselves.
           (attacko interfering-order other-from to)
           ;; pg 9: "A dislodged unit, even with support, has no effect on the
           ;; province that dislodged it" (see Diagram 13).
           ;;
           ;; from                    to                             other-from
           ;; ------------------------------------------------------------------
           ;; A_rus --attack-order--> B_rus ---vacating-to---1sup--> C_tur
           ;;                               <--interfering-order----
           ;;
           ;; B_rus (has 1 support) dislodges C_tur (has 0 support). The fact
           ;; that C_tur attacked where B_rus came from does not prevent A_rus
           ;; from moving into where B_rus came from.
           ;;
           ;; If we're evaluating this goal and there was an attack out of `to`,
           ;; the attack must have advanced, because if it bounced the previous
           ;; goal would have succeeded and we wouldn't be evaluating this goal.
           ;; If the attack from `to` to `other-from` advanced, then the attack
           ;; from `other-from` to `to` doesn't bounce `attack-order`.
           ;;
           ;; TODO: see if assuming that the last goal in this conde failed is
           ;; safe. Do we need to use conda or condu instead?
           (fail-if (attacko (lvar 'vacating-to) to other-from)))])
       (multi-pred has-fewer-or-equal-supporters
                   attack-order
                   interfering-order))])))

(defn-spec failed-attacks
  [(s/coll-of ::dt/order)]
  (s/map-of ::dt/order (s/coll-of ::dt/order)))
(defn failed-attacks
  "A map from each element of `orders` to the set of orders that conflicted with
  it (empty-set if the order succeeded)"
  [orders]
  (let [orders-db (->> orders
                       (map (fn [order] [raw-order order]))
                       (apply pldb/db))]
    (->>
     (run-db*-with-nested-runs
      orders-db
      [attack interfering bounced-by-interfering?]
      ;; TODO: incorporate `rules-used` in test cases.
      (fresh [rules-used]
        (attack-bounce-rulingo attack interfering bounced-by-interfering?
                               rules-used)))
     ;; TODO: add test cases for which rules were used for succeeding orders
     ;; instead of removing all succeeding orders.
     (filter #(nth % 2))
     (map (fn [[k v _]] {k #{v}}))
     (apply merge-with clojure.set/union))))

