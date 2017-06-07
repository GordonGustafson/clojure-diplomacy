(ns diplomacy.resolution
  (:refer-clojure :exclude [==])
  (:use [clojure.core.logic])
  (:require [clojure.core.logic.pldb :as pldb]
            [diplomacy.datatypes :as dt]
            [diplomacy.maps :refer [classic-map]]
            [diplomacy.util :refer [defn-spec]]
            [clojure.set]
            [clojure.spec :as s]))

;; TODO: convoys
;; TODO: dislodging convoys
;; TODO: can't dislodge own units
;; TODO: failure reasons for support orders
;; TODO: can't cut own support
;; TODO: integrate coasts (colocated locations) into resolution??

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

(defn has-no-supporters?
  "Whether `order` has no successful supporters. Non-relational."
  [order]
  (zero? (supporter-count order)))

(declare attack-advancedo)

(defn ^:private determining-rule-for-conflicto
  "Relation where:
  - `attack-order` is an attack whose outcome we would like to evaluate.
  - `interfering-order` is an order that satisfies one of these conditions:
    - attempts to remain in `attack-order`'s destination
    - attemps to move to the same destination as `attack-order`
    - attempts to switch places with `attack-order`
    - fails to leave `attack-order`'s destination
  - `rule` is the most specific rule (represented as a keyword) that applies to
    the conflict between `attack-order` and `interfering-order`; it dictates how
    the conflict will be resolved.
  - `attacks-assumed-successful` is a vector of attacks that will be assumed to
    successfully vacate their destinations for the purposes of identifying
    conflicts.
  "
  ;; `attacks-assumed-successful` is necessary to allow three or more units to
  ;; 'rotate' in a cycle (all move to the next unit's position). Without it,
  ;; each `determining-rule-for-conflicto` goal in the cycle depends on whether
  ;; the attack leaving its destination succeeds, causing an infinite loop.
  [attack-order interfering-order rule attacks-assumed-successful]
  (fresh [from to]
    (attacko attack-order from to)
     (conde
        [(holdo    interfering-order to)
         (== rule :destination-occupied)]
        [(supporto interfering-order to (lvar 'supported-order))
         (== rule :destination-occupied)]

        [(fresh [other-from]
           ;; make sure we don't bounce ourselves because we're attacking the
           ;; same place as ourselves.
           (!= other-from from)
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
           ;; from moving into where B_rus came from, because a dislodged unit
           ;; has no effect on the province that dislodged it.
           (conda
            [(fresh [vacating-to]
               (attacko vacating-to to other-from)
               ;; I don't think we should pass `attacks-assumed-successful` here
               ;; because this `attack-rulingo` can do its job without our help.
               (attack-advancedo vacating-to []))
             (== rule :no-effect-on-dislodgers-province)]
            ;; Otherwise, this is a normal conflict.
            [(== rule :attacked-same-destination)]))]

        [(attacko interfering-order to from)
         ;; TODO: use :swapped-places-with-convoy when appropriate.
         (== rule :swapped-places-without-convoy)]

        [(fresh [other-to new-attacks-assumed-successful]
           ;; Interfering orders that swap places with `attack-order` are
           ;; handled in a different case.
           (!= other-to from)
           (attacko interfering-order to other-to)
           ;; Assume this attack advanced
           (conso attack-order attacks-assumed-successful
                  new-attacks-assumed-successful)
           ;; Don't consider `interfering-order` to interfere if we were told to
           ;; assume it successfully vacated our destination, or if it did so
           ;; under the assumption that we successfully vacated our destination.
           ;; We could unify `(== rule :successfully-left-destination)` instead
           ;; of failing if we wanted to produce conflict-resolution rules for
           ;; all orders that *attempted* to leave our destination, but only
           ;; producing rules for those that *fail* to leave our destination
           ;; reduces noise (it's a no-conflict situation that's easy to
           ;; identify and resolve, so it's not worth having the rules engine
           ;; explain what happened).
           (fail-if (membero interfering-order attacks-assumed-successful))
           (fail-if (attack-advancedo interfering-order
                                      new-attacks-assumed-successful))
           ;; If the `fail-if` goals didn't fail, `interfering-order` must have
           ;; failed to leave our destination.
           (== rule :failed-to-leave-destination))])))

(defn attack-bounced-based-on-determining-rule?
  [attack-order interfering-order rule]
  (condp contains? rule
    #{:destination-occupied
      :attacked-same-destination
      :swapped-places-without-convoy}
    ;; In a direct conflict, `attack-order` is bounced if it has equal or fewer
    ;; supporters.
    (has-fewer-or-equal-supporters attack-order interfering-order)

    #{:failed-to-leave-destination}
    ;; Since the attack out of our destination failed to leave, it's support
    ;; doesn't help it maintain its original position. It will only bounce us if
    ;; we have no support (1v1).
    (has-no-supporters? attack-order)

    #{:no-effect-on-dislodgers-province}
    ;; If `interfering-order` was dislodged and `attack-order` moves to the
    ;; dislodger's province, `interfering-order` can't bounce `attack-order`.
    false

    (assert false (str "Unknown rule: " rule))))

;; TODO: should this be a regular function instead of a relation?
(defn attack-rulingo
  [attack-order interfering-order rule attacks-assumed-successful bounces?]
  (all
   (determining-rule-for-conflicto attack-order interfering-order rule
                                   attacks-assumed-successful)
   (conda
    [(multi-pred attack-bounced-based-on-determining-rule?
                 attack-order
                 interfering-order
                 rule)
     (== bounces? true)]
    [(== bounces? false)])))

;; TODO: think about `attacks-assumed-successful` parameter.
(defn ^:private attack-advancedo
  [attack-order attacks-assumed-successful]
  (fail-if (attack-rulingo attack-order
                           (lvar 'interfering-order)
                           (lvar 'rule)
                           attacks-assumed-successful
                           true)))

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
      [attack interfering rule bounced-by-interfering?]
        (attack-rulingo attack interfering rule [] bounced-by-interfering?))
     (map (fn [[attack interfere rule bounces?]]
            {attack #{[bounces? interfere rule]}}))
     (apply merge-with clojure.set/union))))

