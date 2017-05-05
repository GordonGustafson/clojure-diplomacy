(ns diplomacy.resolution
  (:refer-clojure :exclude [==])
  (:use [clojure.core.logic])
  (:require [clojure.core.logic.pldb :as pldb]
            [diplomacy.datatypes :as dt]
            [diplomacy.maps :refer [classic-map]]
            [diplomacy.util :refer [defn-spec]]
            [clojure.spec :as s]))

;; TODO: convoys, dislodging convoys

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

(defmacro fresh-order
  "Like fresh, but all of the fresh variables are constrained with `raw-order` "
  [name-vector & body]
  (let [raw-order-constraints (map (fn [name] `(raw-order ~name))
                                   name-vector)]
    `(fresh ~name-vector
       ~@raw-order-constraints
       ~@body)))

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

(defn supporto
  "Relation where `order` attempts to remain at `location` while supporting
  `supported-order`"
  [order location supported-order]
  (all
   (raw-order order)
   (raw-order supported-order)
   (featurec order {:order-type :support
                    :location location
                    :assisted-order supported-order})))


(defn support-succeedso
  "Relation where `supporting-order` successfully supports `supported-order`"
  [supporting-order supported-order]
  (fresh [supporter-location]
    (supporto supporting-order supporter-location supported-order)
    (fail-if (attacko (lvar 'cutting-attack)
                      (lvar 'cutting-attack-from)
                      supporter-location))))

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

(defn attack-failso
  "Relation where `attack-order` failed due to `interfering-order`"
  [attack-order interfering-order]
  (fresh [from to]
    (attacko attack-order from to)
    (conde
     [(holdo    interfering-order to)]        ; attack occupied location
     [(supporto interfering-order to (lvar 'supported-order))]
     [(attacko interfering-order to from)]    ; swap places
     [(fresh [other-from]                     ; different attack on same place
        (!= other-from from)
        (attacko interfering-order other-from to))]
     ;; An attack tried to leave our destination but failed
     [(fresh [other-to]
        ;; This is handled in the 'swap places' case. Avoiding it in this case
        ;; may not be necessary???
        (!= other-to from)
        (attacko interfering-order to other-to)
        (attack-failso interfering-order
                       (lvar 'interfering-order-for-interfering-order)))])
    (multi-pred has-fewer-or-equal-supporters
                attack-order
                interfering-order)))

(defn-spec failed-attacks
  [(s/coll-of ::dt/order)] (s/map-of ::dt/order ::dt/order))
(defn failed-attacks
  "A map from each element of `orders` to its failure reason (the order that
  prevented it from succeeding or `nil` if it succeeded)"
  [orders]
  (let [orders-db (->> orders
                       (map (fn [order] [raw-order order]))
                       (apply pldb/db))]
    ;; TODO: will duplicate results cause problems here???
    (into {}
          (run-db*-with-nested-runs orders-db
                                    [attack interfering]
                                    (attack-failso attack interfering)))))
