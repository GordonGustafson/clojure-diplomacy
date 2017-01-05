(ns diplomacy.resolution
  (:refer-clojure :exclude [==])
  (:use [clojure.core.logic])
  (:require [clojure.core.logic.pldb :as pldb])
  (:require [diplomacy.datatypes :as dt])
  (:require [diplomacy.maps :refer [classic-map]]))

;; TODO: support, cutting support, convoys, dislodging convoys

;; (pldb/db-rel holds location)
;; (pldb/db-rel attacks from to)
;; (pldb/db-rel supports location from to)
;; (pldb/db-rel convoys  location from to)

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

;; Puts an order dictionary directly into the fact database.
(pldb/db-rel raw-order order-map)

(def orders-db (pldb/db
                [raw-order (dt/create-order :italy :army :ven :attack :tri)]
                ;; [raw-order (dt/create-order :italy :army :pie :support
                ;;                             :italy :army :ven :attack :tri)]
                ;; [raw-order (dt/create-order :italy :army :rom :support
                ;;                             :italy :army :ven :attack :tri)]
                [raw-order (dt/create-order :austria :army :tri :hold)]
                ;; [raw-order (dt/create-order :austria :army :alb :support
                ;;                             :austria :army :tri :hold)]
                ))

;; (defn orderedo [order-map]
;;   "Relation where `order-map` is a subset of the key-value pairs of some order"
;;   (fresh [order]
;;     (raw-order order)
;;     (featurec order order-map)))

(defn holdo
  "Relation where `order` holds at `location`"
  [order location]
  (featurec order {:order-type :hold
                   :location location}))

(defn attacko
  "Relation where `order` attacks from `from` to `to`"
  [order from to]
  (featurec order {:order-type :attack
                   :location from
                   :destination to}))

(defn support-attacko
  "Relation where `order` is for a unit at `supporter-location` supporting an
  attack from `supported-from` to `supported-to` whose support was not cut."
  [order supporter-location supported-from supported-to]
  (fresh-order [assisted-order attack-on-support]
    (featurec order {:order-type :support
                     :location supporter-location
                     :assisted-order assisted-order})
    (featurec assisted-order {:location supported-from
                              :destination supported-to})
    (fail-if (featurec attack-on-support {:order-type :attack
                                 :destination attacker-destination}))))



    ;; This cannot be `condu` because we need to search *all* attacks to see if
    ;; any of them cut the support.
    #_(fresh [attacker-destination]
      (conda
       [(featurec attack-on-support {:order-type :attack
                                     :destination attacker-destination})
        (!= attacker-destination supporter-location)]
       ;; If there is
       [succeed]
       ))

(defn attack-failso
  "Relation where `attack-order` is a failed attack"
  [attack-order interfering-order]
  (fresh [from to]
    (raw-order interfering-order)
    (featurec attack-order {:location from
                            :destination to})
    (conde
     [(holdo interfering-order to)]           ; attack occupied location
     [(attacko interfering-order to from)]    ; swap places
     [(fresh [other-from]               ; different attack on same place
        (!= other-from from)
        (attacko interfering-order other-from to))])
    (multi-pred has-fewer-or-equal-supporters attack-order interfering-order)))

(defn supporter-count
  "Number of units that successfully support `supported-order`. Non-relational."
  [supported-order]
  ;; TODO: make sure this only counts successful supports
  ;; TODO: can this `run*` return the same order multiple times? Shouldn't
  ;;       matter because of the call to `set` afterwards, but I'm curious.
  (let [supporting-orders
        ;; WITHOUT THIS orders-db WON'T BE USED!
        (pldb/with-db orders-db
          (run* [supporting-location]
            (fresh-order [supporting-order]
              (support-attacko supporting-order
                               supporting-location
                               (:location supported-order)
                               (:destination supported-order)))))]
        (count (set supporting-orders))))

(defn has-fewer-or-equal-supporters
  "Whether `order-a` has fewer or equally many successful supporters as
  `order-b`. Non-relational."
  [order-a order-b]
  (<= (supporter-count order-a)
      (supporter-count order-b)))




;; (defn supporter-counto [supported-order num-supporters]
;;   "Relation where `num-supporters` is the number of units that successfully
;;   support `supported-order`"
;;   (fresh [supporters]


;; (defn voterso [all-voters voters-so-far candidate]
;;   (distincto all-voters)
;;   (distincto voters-so-far)
;;   (fresh [voter rest-of-voters]
;;     (votes-for voter candidate)
;;     (conso voter rest-of-voters voters)
;;     (voterso rest-of-voters candidate)))


;; (defn failso [from to]
;;   (fresh [other-from other-to]
;;     (conde
;;      ((holds to))              ; can't attack a space where someone's holding
;;      ((attacks to from))       ; two units can't swap places
;;      ((attacks other-from to)) ; two units can't attack into the same place
;;      ((supports to other-from other-to)) ; can't attack a space where someone's supporting
