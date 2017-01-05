(ns diplomacy.core-logic-examples
  (:require [clojure.core.logic :refer [== all appendo conde conso
                                        defne firsto fresh lvar membero
                                        run*]]
            [clojure.core.logic.pldb :as pldb]
            [clojure.tools.macro :as macro])
  (:refer-clojure :exclude [==]))

;; From https://github.com/swannodette/logic-tutorial

(pldb/db-rel likes x y)
(pldb/db-rel woman x)
(pldb/db-rel fun x)
(pldb/db-rel man x)

(def men (pldb/db [man 'Bob] [man 'John]))
(pldb/with-db men (run* [q] (man q)))


(def fun-people (pldb/db [fun 'Bob]))
(pldb/with-dbs [men fun-people] (run* [q] (man q) (fun q)))


(def facts (-> (pldb/db [woman 'Lucy] [woman 'Mary])
               (pldb/db-fact likes 'Bob 'Mary)
               (pldb/db-fact likes 'John 'Lucy)))

(pldb/with-dbs [men facts] (run* [q] (likes 'Bob q)))
(pldb/with-dbs [men facts] (run* [q] (likes 'Mary q)))

(def facts (pldb/db-fact facts likes 'Mary 'Bob))

(pldb/with-dbs [men facts] (run* [q] (fresh [x y] (likes x y) (== q [x y]))))
(pldb/with-dbs [men facts] (run* [q] (fresh [x y]
                                       (likes x y) (likes y x) (== q [x y]))))


(pldb/db-rel parent x y)
(pldb/db-rel male x)
(pldb/db-rel female x)

(defn child [x y]
  (parent y x))

(defn son [x y]
  (all
   (child x y)
   (male x)))

(defn daughter [x y]
  (all
   (child x y)
   (female x)))

(defn grandparent [x y]
  (fresh [z]
    (parent x z)
    (parent z y)))

(defn granddaughter [x y]
  (fresh [z]
    (daughter x z)
    (child z y)))


(def genealogy
  (pldb/db
   [parent 'John 'Bobby]
   [male 'Bobby]))

(pldb/with-db genealogy
  (run* [q]
    (son q 'John)))

(def genealogy
  (-> genealogy
      (pldb/db-fact parent 'George 'John)))

(pldb/with-db genealogy (run* [q] (grandparent q 'Bobby)))

(run* [q] (fresh [x y] (== x y) (== q [x y])))
(run* [q] (fresh [x y] (== x y) (== y 1) (== q [x y])))


(pldb/with-dbs [facts fun-people]
  (run* [q]
    (conde
     ((fun q))
     ((likes q 'Mary)))))

(defn my-appendo [l1 l2 result]
  (conde
   ((== l1 ()) (== l2 result))
   ((fresh [head l1-tail result-tail]
      (conso head l1-tail l1)
      (conso head result-tail result)
      (appendo l1-tail l2 result-tail)))))



;; . is a 'rest' operator, like & (see lcons-p? and the surrounding code in
;; logic.clj in core.logic. Each _ creates a new lvar (see p->term in
;; logic.clj).
(defne righto [x y l]
  ([_ _ [x y . ?r]])
  ([_ _ [_ . ?r]] (righto x y ?r)))

(defn nexto [x y l]
  (conde
    ((righto x y l))
    ((righto y x l))))

(defn zebrao [hs]
  (macro/symbol-macrolet [_ (lvar)]
    (all
     (== [_ _ [_ _ 'milk _ _] _ _] hs)
     (firsto hs ['norwegian _ _ _ _])
     (nexto ['norwegian _ _ _ _] [_ _ _ _ 'blue] hs)
     (righto [_ _ _ _ 'ivory] [_ _ _ _ 'green] hs)
     (membero ['englishman _ _ _ 'red] hs)
     (membero [_ 'kools _ _ 'yellow] hs)
     (membero ['spaniard _ _ 'dog _] hs)
     (membero [_ _ 'coffee _ 'green] hs)
     (membero ['ukrainian _ 'tea _ _] hs)
     (membero [_ 'lucky-strikes 'oj _ _] hs)
     (membero ['japanese 'parliaments _ _ _] hs)
     (membero [_ 'oldgolds _ 'snails _] hs)
     (nexto [_ _ _ 'horse _] [_ 'kools _ _ _] hs)
     (nexto [_ _ _ 'fox _] [_ 'chesterfields _ _ _] hs))))



(defn make-house [& key-value-pairs]
  (let [partial-house (apply hash-map key-value-pairs)]
    (merge {:race (lvar) :smokes (lvar) :drinks (lvar)
            :pet (lvar) :color (lvar)}
           partial-house)))

(defn zebra-dicto [hs]
  (macro/symbol-macrolet [_ (lvar)]
    (all
     (== [_ _ (make-house :drinks :milk) _ _] hs)
     (firsto hs (make-house :race :norwegian))
     (nexto (make-house :race :norwegian) (make-house :color :blue) hs)
     (righto (make-house :color :ivory) (make-house :color :green) hs)
     (membero (make-house :race :englishman :color :red) hs)
     (membero (make-house :smokes :kools :color :yellow) hs)
     (membero (make-house :race :spaniard :pet :dog) hs)
     (membero (make-house :drinks :coffee :color :green) hs)
     (membero (make-house :race :ukrainian :drinks :tea) hs)
     (membero (make-house :smokes :lucky-strikes :drinks :orange-juice) hs)
     (membero (make-house :race :japanese :smokes :parliaments) hs)
     (membero (make-house :smokes :oldgolds :pet :snails) hs)
     (nexto (make-house :pet :horse) (make-house :smokes :kools) hs)
     (nexto (make-house :pet :fox) (make-house :smokes :chesterfields) hs))))



;; There are five houses.
;; Milk is drunk in the middle house.
;; The Norwegian lives in the first house.
;; The Norwegian lives next to the blue house.
;; The green house is immediately to the right of the ivory house.
;; The Englishman lives in the red house.
;; Kools are smoked in the yellow house.
;; The Spaniard owns the dog.
;; Coffee is drunk in the green house.
;; The Ukrainian drinks tea.
;; The Lucky Strike smoker drinks orange juice.
;; The Japanese smokes Parliaments.
;; The Old Gold smoker owns snails.
;; Kools are smoked in the house next to the house where the horse is kept.
;; The man who smokes Chesterfields lives in the house next to the man with the fox.

