(ns diplomacy.resolution
  (:refer-clojure :exclude [==])
  (:use [clojure.core.logic])
  (:require [clojure.core.logic.pldb :as pldb]
            [diplomacy.datatypes :as dt]
            [diplomacy.util :refer [defn-spec]]
            [clojure.set]
            [clojure.spec.alpha :as s]))

;; TODO: convoys
;; TODO: dislodging convoys
;; TODO: can't dislodge own units
;; TODO: can't cut own support
;; TODO: integrate coasts (colocated locations) into resolution??

(defn judgmento
  [judgment interferer rule interfered?]
  "Relation that destructures `judgment`"
  (== judgment {:interferer interferer
                :rule rule
                :interfered? interfered?}))

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
  "Relation where supporting `supported-order` would give support into
  `supported-location` for `order` . This requires some logic because supporting
  a hold can also indicate supporting a unit that's supporting or convoying."
  [supported-order order supported-location]
  (conde
   ;; pg 7: A unit ordered to move can only be supported by a support order that
   ;; matches the move the unit is trying to make.
   [(fresh [attack-from]
      (featurec supported-order {:order-type :attack
                                 :location attack-from
                                 :destination supported-location})
      (featurec order {:order-type :attack
                       :location attack-from
                       :destination supported-location}))]
   ;; pg 7: A unit not ordered to move can be supported by a support order that
   ;; only mentions its province.
   [(featurec supported-order {:order-type :hold
                               :location supported-location})
    (conde
     [(featurec order {:order-type :hold
                       :location supported-location})]
     [(featurec order {:order-type :support
                       :location supported-location})]
     [(featurec order {:order-type :convoy
                       :location supported-location})])]))

(defn supporto
  "Relation where `order` attempts to remain at `location` while supporting for
  `supported-order` into `supported-location`."
  [order location supported-order supported-location]
  (fresh [actual-order-supported]
    (raw-order order)
    (raw-order supported-order)
    (featurec order {:order-type :support
                     :location location
                     :assisted-order actual-order-supported})
    (supported-order-matcheso actual-order-supported
                              supported-order
                              supported-location)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                 Resolving Diplomacy Orders ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 'bounced': attack failed due to conflict with another unit.
;; 'advanced': attack succeeded

(declare attack-advancedo)

;; TODO: pg 15: A country can't support the dislodgement of one of its own
;; units.
;;
;; TODO: pg 16: An attack by a country on one of its own units doesn't cut
;; support.
(defn support-judgmento
  "Relation where `judgment` is the judgment for `support'"
  [support judgment]
  (fresh [supporter-location supported-location
          cutter rule support-cut?
          cutter-from]
    (supporto support
              supporter-location
              (lvar 'supported-order)
              supported-location)
    (judgmento judgment cutter rule support-cut?)
    (attacko cutter cutter-from supporter-location)

    (conde
     ;; pg 12: "Support is cut if the unit giving support is attacked from any
     ;; province except the one where support is being given."
     [(!= cutter-from supported-location)
      (== rule :attacked)
      (== support-cut? true)]

     [(== cutter-from supported-location)
      (conda
       ;; pg 12: "Support is cut if the unit giving support is dislodged."
       ;;
       ;; Only evaluate this goal when it's outcome determines `support-cut?`.
       ;; We could evaluate it first in this relation to get different results
       ;; for `rule`, but at the moment that causes a stackoverflow. If we want
       ;; different `rule` to be reported, such as `:dislodged` when the
       ;; supporter is dislodged from *anywhere*, that can be computed after
       ;; we're done figuring out the actual results.
       ;;
       ;; TODO: make sure this doesn't cause termination issues.
       [(attack-advancedo cutter [])
        (== rule :dislodged)
        (== support-cut? true)]

       [(== rule :attacked-from-supported-location-but-not-dislodged)
        (== support-cut? false)])])))

;; Convenience wrapper around `support-judgmento`.
(defn support-succeedso
  "Relation where `support` successfully supports its supported order."
  [support]
  (let [some-order-cut-us-goal
        (support-judgmento support
                           {:interferer (lvar 'cutter)
                            :rule (lvar 'rule)
                            :interfered? true})]
    ;; `support` succeeds if *there does not exist an order that cuts it*.
    ;; Changing `true` to `false` in the call to `support-judgmento` gives a
    ;; goal that succeeds if *there exists an order that potentially cut
    ;; `support` but did not successfully cut it* (that goal could still succeed
    ;; if some *other* order successfully cut `support`).
    (fail-if some-order-cut-us-goal)))

(defn supporter-count
  "Number of units that successfully support `supported`. Non-relational."
  [supported]
  (when (empty? clojure.core.logic/*logic-dbs*)
    ;; This helps more than it hurts at the moment; it's hard to notice that
    ;; you're using an empty fact database.
    (throw (IllegalStateException.
            "nested run running with empty fact database!")))
  ;; TODO: can this `run*` return the same order multiple times? Shouldn't
  ;;       matter because of the call to `set` afterwards, but I'm curious.
  (count (set (run* [support]
                (supporto support
                          (lvar 'location)
                          supported
                          (lvar 'supported-location))
                (support-succeedso support)))))

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

(defn ^:private determining-rule-for-conflicto
  "Relation where:
  - `attack` is an attack whose outcome we would like to evaluate.
  - `bouncer` is an order that satisfies one of these conditions:
    - attempts to remain in `attack`'s destination
    - attempts to move to the same destination as `attack`
    - attempts to switch places with `attack`
    - fails to leave `attack`'s destination
  - `rule` is the most specific rule (represented as a keyword) that applies to
    the conflict between `attack` and `bouncer`; it dictates how the conflict
    will be resolved.
  - `attacks-assumed-successful` is a vector of attacks that will be assumed to
    successfully vacate their destinations for the purposes of identifying
    conflicts.
  "
  ;; `attacks-assumed-successful` is necessary to allow three or more units to
  ;; 'rotate' in a cycle (all move to the next unit's position). Without it,
  ;; each `determining-rule-for-conflicto` goal in the cycle depends on whether
  ;; the attack leaving its destination succeeds, causing an infinite loop.
  [attack bouncer rule attacks-assumed-successful]
  (fresh [from to]
    (attacko attack from to)
    (conde
     [(holdo    bouncer to)
      (== rule :destination-occupied)]
     [(supporto bouncer to (lvar 'supported-order) (lvar 'supported-location))
      (== rule :destination-occupied)]

     [(fresh [other-from]
        ;; make sure we don't bounce ourselves because we're attacking the
        ;; same place as ourselves.
        (!= other-from from)
        (attacko bouncer other-from to)
        ;; pg 9: "A dislodged unit, even with support, has no effect on the
        ;; province that dislodged it" (see Diagram 13).
        ;;
        ;; from                    to                             other-from
        ;; ------------------------------------------------------------------
        ;; A_rus --attack--> B_rus ---vacating-to---1sup--> C_tur
        ;;                         <--bouncer-------0sup--
        ;;
        ;; B_rus (has 1 support) dislodges C_tur (has 0 support). The fact
        ;; that C_tur attacked where B_rus came from does not prevent A_rus
        ;; from moving into where B_rus came from, because a dislodged unit
        ;; has no effect on the province that dislodged it.
        (conda
         [(fresh [vacating-to]
            (attacko vacating-to to other-from)
            ;; I don't think we should pass `attacks-assumed-successful` here
            ;; because this `attack-advanced` can do its job without our help.
            (attack-advancedo vacating-to []))
          (== rule :no-effect-on-dislodgers-province)]
         ;; Otherwise, this is a normal conflict.
         [(== rule :attacked-same-destination)]))]

     [(attacko bouncer to from)
      ;; TODO: use :swapped-places-with-convoy when appropriate.
      (== rule :swapped-places-without-convoy)]

     [(fresh [other-to new-attacks-assumed-successful]
        ;; Orders that swap places with `attack` are handled in a different
        ;; case.
        (!= other-to from)
        (attacko bouncer to other-to)
        ;; Assume this attack advanced
        (conso attack attacks-assumed-successful
               new-attacks-assumed-successful)
        ;; Don't consider `bouncer` to be a potential bouncer if we were told
        ;; to assume it successfully vacated our destination, or if it did so
        ;; under the assumption that we successfully vacated our destination.
        ;; We could unify `(== rule :successfully-left-destination)` instead
        ;; of failing if we wanted to produce conflict-resolution rules for
        ;; all orders that *attempted* to leave our destination, but only
        ;; producing rules for those that *fail* to leave our destination
        ;; reduces noise (it's a no-conflict situation that's easy to
        ;; identify and resolve, so it's not worth having the rules engine
        ;; explain what happened).
        (fail-if (membero bouncer attacks-assumed-successful))
        (fail-if (attack-advancedo bouncer
                                   new-attacks-assumed-successful))
        ;; If the `fail-if` goals didn't fail, `bouncer` must have failed to
        ;; leave our destination.
        (== rule :failed-to-leave-destination))])))

(defn attack-bounced-based-on-determining-rule?
  "Function that returns whether `bouncer` bounced `attack` due to `rule`.
  Proving the wrong `rule` will give bogus results."
  [attack bouncer rule]
  (condp contains? rule
    #{:destination-occupied
      :attacked-same-destination
      :swapped-places-without-convoy}
    ;; In a direct conflict, `attack` is bounced if it has equal or fewer
    ;; supporters.
    (has-fewer-or-equal-supporters attack bouncer)

    #{:failed-to-leave-destination}
    ;; Since the attack out of our destination failed to leave, it's support
    ;; doesn't help it maintain its original position. It will only bounce us if
    ;; we have no support (1v1).
    (has-no-supporters? attack)

    #{:no-effect-on-dislodgers-province}
    ;; If `bouncer` was dislodged and `attack` moves to the dislodger's
    ;; province, `bouncer` can't bounce `attack`.
    false

    (assert false (str "Unknown rule: " rule))))

;; This relation links the relational code in `determining-rule-for-conflicto`
;; with the functional code in `attack-bounced-based-on-determining-rule?`.
(defn attack-judgmento
  "Relation where `judgment` is the judgment for `attack`, and
  `attacks-assumed-successful` is a vector of attacks that will be assumed to
  successfully vacate their destinations for the purposes of identifying
  conflicts."
  [attack judgment attacks-assumed-successful]
  (fresh [bouncer rule bounced-by-bouncer?]
    (judgmento judgment bouncer rule bounced-by-bouncer?)
    (determining-rule-for-conflicto attack bouncer rule
                                    attacks-assumed-successful)
    (conda
     [(multi-pred attack-bounced-based-on-determining-rule?
                  attack
                  bouncer
                  rule)
      (== bounced-by-bouncer? true)]
     [(== bounced-by-bouncer? false)])))

;; TODO: think about `attacks-assumed-successful` parameter.
;;
;; Convenience wrapper around `attack-judgmento`.
(defn ^:private attack-advancedo
  "Relation where `attack` succeeds"
  [attack attacks-assumed-successful]
  (let [some-order-bounced-us-goal
        (attack-judgmento attack
                          {:interferer (lvar 'bouncer)
                           :rule (lvar 'rule)
                           :interfered? true}
                          attacks-assumed-successful)]
    ;; `attack` advances if *there does not exist an order that bounces it*.
    ;; Changing `true` to `false` in the call to `attack-judgmento` gives a goal
    ;; that succeeds if *there exists an order that potentially bounced `attack`
    ;; but did not successfully bounce it* (that goal could still succeed if
    ;; some *other* order successfully bounced `attack`).
    (fail-if some-order-bounced-us-goal)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                      Public Interface for Order Resolution ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn-spec conflict-judgments
  [(s/coll-of ::dt/order)]
  ::dt/conflict-judgments)
(defn conflict-judgments
  "A map from each element of `orders` to the set of judgments that apply to it
  (the orders that may interfere with it, whether they successfully interfered,
  and the rule that determined that result)."
  [orders]
  (let [orders-db (->> orders
                       (map (fn [order] [raw-order order]))
                       (apply pldb/db))]
    (->>
     (run-db*-with-nested-runs
      orders-db
      [order judgment]
      (conde
       [(attack-judgmento order judgment [])]
       [(support-judgmento order judgment)]))
     (map (fn [[attack judgment]] {attack #{judgment}}))
     (apply merge-with clojure.set/union)
     (into {})
     ;; Make sure that orders that have no conflict-judgments are mapped to an
     ;; empty set of conflict-judgments in the output. Merge uses the values
     ;; from the right-most map when there are conflicts.
     (merge (zipmap orders (repeat #{}))))))
