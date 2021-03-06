(ns diplomacy.resolution-core-logic
  ;; Don't get zooped by this!
  (:refer-clojure :exclude [==])
  (:use [clojure.core.logic])
  (:require [clojure.core.logic.pldb :as pldb]
            [diplomacy.orders :as orders]
            [diplomacy.datatypes :as dt]
            [diplomacy.util :refer [defn-spec]]
            [clojure.set]
            [clojure.spec.alpha :as s]))

;; TODO: convoys
;; TODO: dislodging convoys

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                       core.logic Utilities ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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
  `(condu [~goal fail]
          [succeed]))

(defn findall
  "Unifies `result-set-var` with the list of values for `query-var` that satisfy
  `goal` in the current substitution. Unifies `result-set-var` with the empty
  list if `goal` fails."
  [query-var goal result-set-var]
  (fn [current-substitution]
    ((== result-set-var (solutions current-substitution query-var goal))
     current-substitution)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                             Diplomacy Orders in core.logic ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; These relations help express diplomacy order in core.logic, and contain no
;; logic for *resolving* the orders.

;; Puts an order dictionary directly into the fact database.
(pldb/db-rel raw-order order-map)
;; A vector of locations that are colocated, such as `[:spa :spa-sc :spa-nc]`.
(pldb/db-rel colocation-vec colocation-vector)
;; Indicates that there is a *directed* edge from `loc-a` to `loc-b`. If there
;; is also an edge from `loc-b` to `loc-a` there must be an additional fact
;; indicating that. This means there must be 2 facts for every pair of adjacent
;; locations in the classic map, since every edge in the classic map is
;; undirected. I don't know of any variants that have edges that can only be
;; traversed in one direction, but this makes the code simpler.
;;
;; All edges between adjacent land locations are accessible to armies, so we
;; don't need any edge-accessibility information in the resolution phase.
(pldb/db-rel adjacent loc-a loc-b)

;; For performance reasons it may be better to pre-compute the colocation-set of
;; every location used in every order, and then simply compare colocation-sets
;; instead of locations when a coast-insensitive comparison is desired.
(defn colocated
  "Relation where `location-a` and `location-b` are colocated."
  [location-a location-b]
  ;; This is `conde` instead of `condu` so this can 'return' all colocated
  ;; locations if one argument is ground and the other free (currently used by
  ;; `colocated-adjacent`).
  (conde
   [(== location-a location-b)]
   [(fresh [common-colocation-vec]
      (colocation-vec common-colocation-vec)
      (membero location-a common-colocation-vec)
      (membero location-b common-colocation-vec))]))

(defn colocated-adjacent
  "Relation where there exists a pair of adjacent locations colocated with `location-a` and
  `location-b`, respectively."
  [location-a location-b]
  (fresh [colocated-a colocated-b]
    (colocated location-a colocated-a)
    (colocated location-b colocated-b)
    (adjacent colocated-a colocated-b)))

(defn same-countryo
  "Relation where `order-a` and `order-b` are given by the same country."
  [order-a order-b]
  (fresh [country]
    (featurec order-a {:country country})
    (featurec order-b {:country country})))

(defn remainso
  "Relation where `order` attempts to hold, support, or convoy at `location`,"
  [order location]
  (fresh [order-type]
    (raw-order order)
    (featurec order {:order-type order-type
                     :location location})
    ;; `membero` doesn't seem to work with sets!
    (membero order-type [:hold :support :convoy])))

(defn attacko
  "Relation where `order` attempts to attack from `from` to `to`"
  [order from to]
  (all
   (raw-order order)
   (featurec order {:order-type :attack
                    :location from
                    :destination to})))

(defn supported-order-matcheso
  "Relation where supporting `supported-order` would give support for `order` .
  This requires some logic because supporting a hold can also indicate
  supporting a unit that's supporting or convoying."
  [supported-order order]
  (condu
   ;; pg 7: A unit ordered to move can only be supported by a support order that
   ;; matches the move the unit is trying to make.
   [(fresh [attack-from attack-to]
      (featurec supported-order {:order-type :attack
                                 :location attack-from
                                 :destination attack-to})
      (featurec order {:order-type :attack
                       :location attack-from
                       :destination attack-to}))]
   ;; pg 7: A unit not ordered to move can be supported by a support order that
   ;; only mentions its province.
   [(fresh [supported-location]
      (featurec supported-order {:order-type :hold
                                 :location supported-location})
      (condu
       [(featurec order {:order-type :hold
                         :location supported-location})]
       [(featurec order {:order-type :support
                         :location supported-location})]
       [(featurec order {:order-type :convoy
                         :location supported-location})]))]))

(defn supporto
  "Relation where `order` attempts to remain at `location` while supporting
  `supported-order`."
  [order location supported-order]
  (fresh [actual-order-supported]
    (raw-order order)
    ;; Don't require that `supported-order` was actually given. In our system
    ;; the act and outcome of supporting has nothing to do with whether the
    ;; supported order was given or not.
    (featurec order {:order-type :support
                     :location location
                     :assisted-order actual-order-supported})
    (supported-order-matcheso actual-order-supported
                              supported-order)))

(defn convoyo
  "Relation where `order` attempts to remain at `location` while convoying
  `convoyed-attack`."
  [order location convoyed-attack]
  (all
   (raw-order order)
   ;; Don't require that `convoyed-attack` was actually given.
   (featurec order {:order-type :convoy
                    :location location
                    :assisted-order convoyed-attack})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                 Resolving Diplomacy Orders ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 'bounced': attack failed due to conflict with another unit.
;; 'advanced': attack succeeded

(declare convoy-dislodged)

(defn convoy-chain-between-convoys
  "Relation where there are a chain of convoys convoying `attack` between the
  first convoy at `start` and the last convoy at `end`."
  [attack start end locations-used]
  (condu
   [(adjacent start end)]
   [(fresh [next-convoy next-location new-locations-used]
      (convoyo next-convoy next-location attack)
      (adjacent start next-location)
      (fail-if (membero next-location locations-used))
      (fail-if (convoy-dislodged next-convoy))
      (conso next-location locations-used new-locations-used)
      (convoy-chain-between-convoys attack next-location end
                                    new-locations-used))]))

(defn attack-arrives-by-convoyo
  ""
  [attack from to]
  (all
   (attacko attack from to)
    (fresh [first-convoy chain-start
            last-convoy chain-end]
      ;; Armies can 'get on' and 'get off' at `colocated-adjacent` areas, since
      ;; the land location for a location with two coasts isn't `adjacent` to any
      ;; bodies of water.
      (convoyo first-convoy chain-start attack)
      (colocated-adjacent chain-start from)
      (fail-if (convoy-dislodged first-convoy))

      (convoyo last-convoy chain-end attack)
      (colocated-adjacent chain-end to)
      (fail-if (convoy-dislodged last-convoy))
      (condu
       [(== chain-start chain-end)]
       [(convoy-chain-between-convoys attack
                                      chain-start
                                      chain-end
                                      [])]))))

(defn attack-arriveso
  ""
  [attack from to]
  (all
   (attacko attack from to)
   (condu
    [(adjacent from to)]
    [(attack-arrives-by-convoyo attack from to)])))

(declare attack-advancedo)

(defn support-judgmento
  "Relation where `judgment` is the judgment for `support`. This only takes into
  account whether the supporting unit was interfered with or not. Whether the
  support order supports the dislodgement of a unit from the same country must
  be handled elsewhere, since a support order would still take effect against
  other orders even if it was ignored for the purpose of dislodging a fellow
  unit."
  [support judgment]
  (fresh [supporter-location supported-order supported-attack-destination
          cutter situation support-cut?
          cutter-from cutter-to]
    (supporto support
              supporter-location
              supported-order)
    (condu
     ;; TODO: should this be `attack-arriveso`?
     [(attacko supported-order
               (lvar 'supported-attack-location)
               supported-attack-destination)]
     [(== supported-attack-destination :none)])
    (== judgment {:interferer cutter
                  :conflict-situation situation
                  :interfered? support-cut?})
    (attacko cutter cutter-from cutter-to)
    (colocated supporter-location cutter-to)
    ;; RESUME HERE!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    ;; Why this doesn't print anything is driving me nuts.
    (multi-pred (fn [a b c d e f g h] (do (println a b c d e f g h) true))
                supporter-location supported-order supported-attack-destination
                cutter situation support-cut?
                cutter-from cutter-to)
    (do (println "BEFORE attack-arriveso") succeed)
    (attack-arriveso cutter cutter-from cutter-to)
    (do (println "AFTER  attack-arriveso") succeed)

    (condu
     ;; pg 16: "An attack by a country one of its own units doesn't cut support."
     [(same-countryo support cutter)
      (== situation :attacked-by-same-country)
      (== support-cut? false)]
     ;; pg 12: "Support is cut if the unit giving support is attacked from any
     ;; province except the one where support is being given."
     [(!= cutter-from supported-attack-destination)
      (== situation :attacked)
      (== support-cut? true)]

     [(== cutter-from supported-attack-destination)
      (condu
       ;; pg 12: "Support is cut if the unit giving support is dislodged."
       ;;
       ;; Only evaluate this goal when it's outcome determines `support-cut?`.
       ;; We could evaluate it first in this relation to get different results
       ;; for `situation`, but at the moment that causes a stackoverflow. If we want
       ;; different `situation` to be reported, such as `:dislodged` when the
       ;; supporter is dislodged from *anywhere*, that can be computed after
       ;; we're done figuring out the actual results.
       ;;
       ;; TODO: make sure this doesn't cause termination issues.
       [(attack-advancedo cutter [])
        (== situation :dislodged)
        (== support-cut? true)]

       [(== situation :attacked-from-supported-location)
        (== support-cut? false)])])))

;; Convenience wrapper around `support-judgmento`.
(defn support-succeedso
  "Relation where `support` successfully supports its supported order."
  [support]
  (let [some-order-cut-us-goal
        (support-judgmento support
                           {:interferer (lvar 'cutter)
                            :conflict-situation (lvar 'situation)
                            :interfered? true})]
    ;; `support` succeeds if *there does not exist an order that cuts it*.
    ;; Changing `true` to `false` in the call to `support-judgmento` gives a
    ;; goal that succeeds if *there exists an order that potentially cut
    ;; `support` but did not successfully cut it* (that goal could still succeed
    ;; if some *other* order successfully cut `support`).
    (fail-if some-order-cut-us-goal)))

(defn successful-supporterso
  "Unifies `supporters` with the list of orders that successfully support
  `supported`. `supported` must be ground."
  [supported supporters]
  (fresh [support]
    (findall support
             (all
              (supporto support
                        (lvar 'location)
                        supported)
              (support-succeedso support))
             supporters)))

(declare depends-on-whether-beleaguered-garrison-leaves)

(defn ^:private conflict-situationo
  "Relation where:
  - `attack` is an attack whose outcome we would like to evaluate.
  - `bouncer` is an order that satisfies one of these conditions:
    - attempts to remain in `attack`'s destination
    - attempts to move to the same destination as `attack`
    - attempts to switch places with `attack`
    - fails to leave `attack`'s destination
  - `situation` is the most specific rule (represented as a keyword) that
    applies to the conflict between `attack` and `bouncer`; it dictates how
    the conflict will be resolved.
  - `attacks-assumed-successful` is a vector of attacks that will be assumed to
    successfully vacate their destinations for the purposes of identifying
    conflicts.
  "
  ;; `attacks-assumed-successful` is necessary to allow three or more units to
  ;; 'rotate' in a cycle (all move to the next unit's position). Without it,
  ;; each `conflict-situationo` goal in the cycle depends on whether the attack
  ;; leaving its destination succeeds, causing an infinite loop.
  [attack bouncer situation attacks-assumed-successful]
  (fresh [from to rule attack-supporters bouncer-supporters
          beleaguered-garrison]
    ;; TODO: should this be `attacko`?
    (attack-arriveso attack from to)
    (== situation {:attack-conflict-rule rule
                   :attack-supporters attack-supporters
                   :bouncer-supporters bouncer-supporters
                   :beleaguered-garrison-changing-outcome beleaguered-garrison})
    (conde
     [(fresh [bouncer-remain-loc]
        (remainso bouncer bouncer-remain-loc)
        (colocated to bouncer-remain-loc)
        (successful-supporterso attack attack-supporters)
        (successful-supporterso bouncer bouncer-supporters)
        (== rule :destination-occupied)
        (== beleaguered-garrison nil))]

     [(fresh [bouncer-from bouncer-to]
        ;; make sure we don't bounce ourselves because we're attacking the
        ;; same place as ourselves.
        (!= bouncer-from from)
        (attack-arriveso bouncer bouncer-from bouncer-to)
        (colocated to bouncer-to)
        (successful-supporterso attack attack-supporters)
        (successful-supporterso bouncer bouncer-supporters)
        ;; pg 9: "A dislodged unit, even with support, has no effect on the
        ;; province that dislodged it" (see Diagram 13).
        ;;
        ;; from                    to                           bouncer-from
        ;; ------------------------------------------------------------------
        ;; A_rus --attack--> B_rus ---dislodger---1sup--> C_tur
        ;;                         <--bouncer-----0sup--
        ;;
        ;; B_rus (has 1 support) dislodges C_tur (has 0 support). The fact
        ;; that C_tur attacked where B_rus came from does not prevent A_rus
        ;; from moving into where B_rus came from, because a dislodged unit
        ;; has no effect on the province that dislodged it.
        (condu
         [(fresh [dislodger dislodger-from dislodger-to]
            (attack-arriveso dislodger dislodger-from dislodger-to)
            (colocated dislodger-from bouncer-to)
            (colocated dislodger-to bouncer-from)
            ;; I don't think we should pass `attacks-assumed-successful` here
            ;; because this `attack-advanced` can do its job without our help.
            (attack-advancedo dislodger []))
          (== rule :no-effect-on-dislodgers-province)
          (== beleaguered-garrison nil)]
         ;; Otherwise, this is a normal conflict.
         [(== rule :attacked-same-destination)
          (condu
           [(condu
             [(remainso beleaguered-garrison to)
              (multi-pred depends-on-whether-beleaguered-garrison-leaves
                          attack bouncer attack-supporters bouncer-supporters
                          beleaguered-garrison)]
             [(attacko beleaguered-garrison to (lvar 'beleaguered-to))
              (multi-pred depends-on-whether-beleaguered-garrison-leaves
                          attack bouncer attack-supporters bouncer-supporters
                          beleaguered-garrison)
              ;; Don't call `attack-advancedo` unless it's guaranteed to
              ;; affect the outcome. This helps avoid non-termination issues.
              ;;
              ;; TODO: THINK HARD ABOUT THESE
              ;; (conso attack attacks-assumed-successful
              ;;        new-attacks-assumed-successful)
              ;; (fail-if (membero beleaguered-garrison
              ;;                   attacks-assumed-successful))
              (fail-if (attack-advancedo beleaguered-garrison
                                         attacks-assumed-successful))])]
           [(== beleaguered-garrison nil)])]))]

     [(fresh [bouncer-from bouncer-to]
        (attack-arriveso bouncer bouncer-from bouncer-to)
        (colocated bouncer-from to)
        (colocated bouncer-to from)
        (successful-supporterso attack attack-supporters)
        (successful-supporterso bouncer bouncer-supporters)
        ;; TODO: use :swapped-places-with-convoy when appropriate.
        (== rule :swapped-places)
        (== beleaguered-garrison nil))]

     [(fresh [bouncer-from bouncer-to new-attacks-assumed-successful]
        ;; TODO: should this be `attack-arriveso`?
        (attacko bouncer bouncer-from bouncer-to)
        (colocated bouncer-from to)
        ;; Orders that swap places with `attack` are handled in a different
        ;; case.
        (fail-if (colocated bouncer-to from))
        (successful-supporterso attack attack-supporters)
        (successful-supporterso bouncer bouncer-supporters)
        ;; Assume this attack advanced
        (conso attack attacks-assumed-successful
               new-attacks-assumed-successful)
        ;; Don't consider `bouncer` to be a potential bouncer if we were told
        ;; to assume it successfully vacated our destination, or if it did so
        ;; under the assumption that we successfully vacated our destination.
        ;; We could unify `(== situation :successfully-left-destination)` instead
        ;; of failing if we wanted to produce conflict-resolution situations for
        ;; all orders that *attempted* to leave our destination, but only
        ;; producing situations for those that *fail* to leave our destination
        ;; reduces noise (it's a no-conflict situation that's easy to
        ;; identify and resolve, so it's not worth having the rules engine
        ;; explain what happened).
        (fail-if (membero bouncer attacks-assumed-successful))
        (fail-if (attack-advancedo bouncer
                                   new-attacks-assumed-successful))
        ;; If the `fail-if` goals didn't fail, `bouncer` must have failed to
        ;; leave our destination.
        (== rule :failed-to-leave-destination)
        (== beleaguered-garrison nil))])))

(defn-spec num-willing-to-dislodge [::dt/orders ::dt/order] integer?
  #(every? orders/support? (-> % :args :arg-1)))
(defn num-willing-to-dislodge
  "Function that returns the number of supporters in `supporters` willing to
  dislodge `potentially-dislodged-order`.
  PRECONDITION: every order in `supporters` is a support order."
  [supporters potentially-dislodged-order]
  (count (filter #(not= (:country %)
                        (:country potentially-dislodged-order))
                 supporters)))

(defn bounced-by-strength-in-situation
  "Function that returns whether `bouncer` has enough support to bounce `attack`
  where the conflict between them was due to `situation`.

  *Does not* consider that `attack` will be unwilling to dislodge `bouncer` if
  the units are from the same country. This means that `bouncer` may still
  'bounce' `attack` (because `attack` is unwilling to dislodge) even if this
  function returns false.

  *Does* consider that supports are unwilling to cause units from the same
  country to be dislodged. In *each* conflict, a supporting unit will 'choose'
  to not provide the support it was ordered to give if by doing so it will
  prevent a unit from the same country from being dislodged. That decision is
  made *per conflict*, so a supporting unit may have its support counted in some
  conflicts but not in others.

  Providing the wrong `situation` will give bogus results."
  [attack bouncer situation]
  (let [{attack-conflict-rule :attack-conflict-rule
         attack-supporters  :attack-supporters
         bouncer-supporters :bouncer-supporters
         beleaguered-garrison :beleaguered-garrison-changing-outcome} situation]
    (condp contains? attack-conflict-rule
      #{:attacked-same-destination}
      (<= (if (nil? beleaguered-garrison)
            (count attack-supporters)
            (num-willing-to-dislodge attack-supporters beleaguered-garrison))
          (count bouncer-supporters))

      #{:swapped-places
        :destination-occupied}
      (<= (num-willing-to-dislodge attack-supporters bouncer)
          (count bouncer-supporters))

      #{:failed-to-leave-destination}
      ;; Since `bouncer` failed to leave `attack`s destination, `bouncer`'s
      ;; support doesn't help it maintain its original position. `bouncer` will
      ;; only bounce `attack` if `attack` draws no support willing to dislodge
      ;; `bouncer`.
      (zero? (num-willing-to-dislodge attack-supporters bouncer))

      #{:no-effect-on-dislodgers-province}
      ;; If `attack` is 'following' the attack that dislodged `bouncer`,
      ;; `bouncer` can't bounce `attack`.
      false

      (assert false (str "Unknown situation: " situation)))))

(defn-spec depends-on-whether-beleaguered-garrison-leaves
  [::dt/order ::dt/order ::dt/orders ::dt/orders ::dt/order] boolean?)
(defn depends-on-whether-beleaguered-garrison-leaves
  "Function that returns whether the outcome of `attack` in an
  `:attacked-same-destination` conflict with `bouncer` depends on whether
  `potential-beleaguered-garrison` advances."
  [attack bouncer attack-supporters bouncer-supporters potential-beleaguered-garrison]
  (let [situation {:attack-conflict-rule :attacked-same-destination
                   :attack-supporters attack-supporters
                   :bouncer-supporters bouncer-supporters}]
    (not= (bounced-by-strength-in-situation
           attack bouncer (assoc situation
                                 :beleaguered-garrison-changing-outcome
                                 potential-beleaguered-garrison))
          (bounced-by-strength-in-situation
           attack bouncer (assoc situation
                                 :beleaguered-garrison-changing-outcome
                                 nil)))))

(defn attack-failed-to-arrive-judgmento
  "Relation where `failed-to-arrive-judgment` is a
  `::dt/failed-to-arrive-judgment` indicating why `attack` failed to arrive at
  its destination."
  [attack failed-to-arrive-judgment]
  (fresh [from to]
    (attacko attack from to)
    (fail-if (attack-arriveso attack from to))
    ;; TODO: add more descriptive possibilities here?
    (== failed-to-arrive-judgment :no-successful-convoy)))

;; This relation links the relational code in `conflict-situationo` with the
;; functional code in `bounced-by-strength-in-situation`, and contains the logic
;; that disallows countries from dislodging their own units.
(defn attack-conflict-judgmento
  "Relation where `conflict-judgment` is a `::dt/conflict-judgment` for `attack`,
  and `attacks-assumed-successful` is a vector of attacks that will be assumed
  to successfully vacate their destinations for the purposes of identifying
  conflicts."
  [attack conflict-judgment attacks-assumed-successful]
  (fresh [bouncer situation bounced-by-bouncer? would-dislodge-own-unit?]
    (== conflict-judgment {:interferer bouncer
                           :conflict-situation situation
                           :interfered? bounced-by-bouncer?
                           :would-dislodge-own-unit? would-dislodge-own-unit?})
    (conflict-situationo attack bouncer situation
                         attacks-assumed-successful)
    (condu
     [(multi-pred bounced-by-strength-in-situation
                  attack
                  bouncer
                  situation)
      (== bounced-by-bouncer? true)
      (== would-dislodge-own-unit? false)]
     [(condu
       [(fresh [attack-to bouncer-location]
          (same-countryo attack bouncer)
          (featurec attack {:destination attack-to})
          (featurec bouncer {:location bouncer-location})
          (colocated attack-to bouncer-location)
          ;; If there's a conflict between `attack` and `bouncer`, `attack` is
          ;; strong enough to dislodge `bouncer`, `bouncer` is from the same
          ;; country, and `attack` is attacking `bouncer`'s location, then
          ;; `attack` cannot succeed because it would dislodge a unit of its own
          ;; country. `bouncer` could be an attack, but in that case it must be
          ;; either trying to switch places with `attack` or have failed to
          ;; leave `attack`'s destination in order for there to be a conflict
          ;; with `attack`.
          ;;
          ;; If `order` and `bouncer` are attacking the same destination, the
          ;; unit with more support with move successfully, as usual.
          (== bounced-by-bouncer? true)
          (== would-dislodge-own-unit? true))]
       [(all
         (== bounced-by-bouncer? false)
         (== would-dislodge-own-unit? false))])])))

(defn ^:private attack-advancedo
  "Relation where `attack` succeeds"
  [attack attacks-assumed-successful]
  (let [some-order-bounced-us-goal
        (attack-conflict-judgmento attack
                                   {:interferer (lvar 'bouncer)
                                    :conflict-situation (lvar 'situation)
                                    :interfered? true
                                    :would-dislodge-own-unit?
                                    (lvar 'would-dislodge-own-unit?)}
                                   attacks-assumed-successful)]
    (all
     (attack-arriveso attack (lvar 'from) (lvar 'to))
     ;; `attack` advances if *there does not exist an order that bounces it*.
     ;; Changing `true` to `false` in the call to `attack-conflict-judgmento`
     ;; gives a goal that succeeds if *there exists an order that potentially
     ;; bounced `attack` but did not successfully bounce it* (that goal could
     ;; still succeed if some *other* order successfully bounced `attack`).
     (fail-if some-order-bounced-us-goal))))

(defn ^:private convoy-dislodged
  "Relation where `convoy-order` is dislodged"
  [convoy-order]
  (fresh [dislodger location]
    (convoyo convoy-order location (lvar 'convoyed-attack))
    (attacko dislodger (lvar 'from) location)
    (attack-advancedo dislodger [])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                      Public Interface for Order Resolution ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn-spec compute-resolution-results
  [::dt/orders ::dt/dmap]
  ::dt/resolution-results
  #(= (set (-> % :args :arg-1)) (set (-> % :ret (keys)))))
(defn compute-resolution-results
  "A map from each element of `orders` to the set of judgments that apply to it
  (the orders that may interfere with it, whether they successfully interfered,
  and the situation that determined that result)."
  [orders diplomacy-map]
  (let [raw-order-vectors (map (fn [order] [raw-order order]) orders)
        colocation-vecs (->> diplomacy-map
                             :colocation-sets
                             (map (fn [co-set]
                                        ; convert to vector because `membero`
                                        ; doesn't work on sets!
                                    [colocation-vec (vec co-set)])))
        adjacency-pairs
        (->> diplomacy-map
             :edge-accessibility
             (mapcat (fn [[location edge-accessibility-map]]
                       (map #(-> [adjacent location %])
                            (keys edge-accessibility-map)))))
        database (apply pldb/db (concat raw-order-vectors
                                        colocation-vecs
                                        adjacency-pairs))]
    (->>
     (run-db*
      database
      [order judgment]
      (conde
       [(attack-failed-to-arrive-judgmento order judgment)]
       [(attack-conflict-judgmento order judgment [])]
       [(support-judgmento order judgment)]))
     (map (fn [[attack judgment]] {attack #{judgment}}))
     (apply merge-with clojure.set/union)
     (into {})
     ;; Make sure that orders that have no resolution-results are mapped to an
     ;; empty set of resolution-results in the output. Merge uses the values
     ;; from the right-most map when there are conflicts.
     (merge (zipmap orders (repeat #{}))))))
