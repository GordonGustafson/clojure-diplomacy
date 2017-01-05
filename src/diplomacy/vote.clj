(ns diplomacy.vote
  (:refer-clojure :exclude [==])
  (:use [clojure.core.logic])
  (:require [clojure.core.logic.pldb :as pldb]))

;; `voter-name` voted for `candidate`
(pldb/db-rel vote voter-name candidate)

(def voter-db (pldb/db
               [vote "Peter" :blue]
               [vote "Sarah" :blue]
               [vote "Larry" :red]
               [vote "Steve" :red]
               [vote "Amy" :yellow]))

(defn candidate-received-no-votes
  "Relation where candidate `candidate` received no votes"
  [candidate]
  (fresh [voter candidate-with-votes]
    (conda
     [(vote voter candidate)
      (!= candidate candidate-with-votes)]
     ;; No candidate received a vote, so `candidate` received no votes
     [succeed])))
