(ns diplomacy.resolution-iterative.datatypes
  (:require [diplomacy.datatypes :as dt]
            [diplomacy.util :refer [defn-spec]]
            [clojure.spec.alpha :as s]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                               Specs Internal to Resolution ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; An example use of `::no-conflict` is when there's a potential conflict of
;; army 1 not leaving army 2's destination. If army 1 ends up successfully
;; leaving army 2's destination, the result of the potential conflict between
;; army 1 and army 2 is `[:failed-to-leave-destination :no-conflict]`.
;;
;; `::no-conflict` resolutions are filtered out from the result of
;; `compute-resolution-results`, since `::dt/judgment` doesn't allow
;; `::no-conflict` resolutions.
(s/def ::no-conflict (s/tuple ::dt/conflict-rule
                              (partial = :no-conflict)))
(s/def ::resolved-conflict-state (s/or :judgment-tag ::dt/judgment
                                       :no-conflict-tag ::no-conflict))
(s/def ::pending-conflict-state ::dt/conflict-rule)
(s/def ::conflict-state (s/or :resolved-tag ::resolved-conflict-state
                              :pending-tag ::pending-conflict-state))
;; Whether an order is known to succeed, known to fail (due to being interefered
;; with), or not known yet.
(s/def ::order-status #{:succeeded :failed :pending})
(s/def ::arrival-status #{:succeeded :failed :pending})
;; At the moment `::judgment` also contains the interfering order, duplicating
;; information between the key and value of the nested map. Fixing this isn't
;; necessary.
;;
;; TBD: make this only contain resolved conflicts?
(s/def ::conflict-map (s/map-of ::dt/order
                                (s/map-of ::dt/order ::conflict-state)))
(s/def ::conflict-state-update
  (s/tuple ::dt/order ::dt/order ::conflict-state))
(s/def ::conflict-state-updates (s/coll-of ::conflict-state-update))
(s/def ::pending-conflict
  (s/tuple ::dt/order ::dt/order ::pending-conflict-state))
(s/def ::conflict-queue (s/and (s/coll-of ::pending-conflict) #_queue?))

(s/def ::dislodgment-status #{:dislodged :not-dislodged :pending})
(s/def ::voyage-status #{:succeeded :failed :pending})
(s/def ::voyage-map (s/map-of ::dt/attack-order ::voyage-status))
(s/def ::voyage-queue (s/and (s/coll-of ::dt/attack-order) #_queue?))

;; Map from orders to the orders attempting to support them.
(s/def ::support-map (s/map-of ::dt/order ::dt/orders))
;; Map from orders to the orders attempting to convoy them.
(s/def ::convoy-map (s/map-of ::dt/attack-order ::dt/orders))

;; Set of orders that do not need a convoy because they move between adjacent
;; locations (though they may still have a convoy).
(s/def ::direct-arrival-set (s/and (s/coll-of ::dt/attack-order) set?))
(s/def ::location-to-order-map (s/map-of ::dt/location ::dt/order))
(s/def ::resolution-state
  (s/keys :req-un [::conflict-map
                   ::conflict-queue
                   ::voyage-map
                   ::voyage-queue
                   ;; Fields that never change during resolution
                   ::support-map
                   ::convoy-map
                   ::direct-arrival-set
                   ::location-to-order-map
                   ::dt/dmap]))

;; more

(s/def ::assume-beleaguered-garrison-leaves boolean?)
(s/def ::battle-settings (s/keys :req-un [::assume-beleaguered-garrison-leaves]))

(s/def ::support-type #{:offense
                        :defense
                        :offense-assume-beleaguered-garrison-leaves})
(s/def ::willingness-to-support #{:yes
                                  :no
                                  :pending-beleaguered-garrison-leaving})
