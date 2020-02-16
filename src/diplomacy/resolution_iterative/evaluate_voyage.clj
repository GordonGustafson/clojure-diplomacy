(ns diplomacy.resolution-iterative.evaluate-voyage
  (:require [diplomacy.resolution-iterative.datatypes :as r]
            [diplomacy.resolution-iterative.map-util :as map-util]
            [diplomacy.resolution-iterative.evaluate-util :as eval-util]
            [diplomacy.map-functions :as maps]
            [diplomacy.settings]
            [diplomacy.datatypes :as dt]
            [diplomacy.util :refer [defn-spec]]
            [clojure.spec.alpha :as s]))
(require 'clojure.pprint)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                Resolving Voyages (Convoys) ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn-spec convoy-path-exists?-helper [::dt/dmap ::dt/location ::dt/location
                                       (s/and (s/coll-of ::dt/location) set?)
                                       boolean?]
  boolean?)
(defn convoy-path-exists?-helper
  [dmap start-loc end-loc convoy-locations path-length-so-far-is-zero?]
  (cond
    (and (maps/colocated-edge-accessible-to? dmap start-loc end-loc :fleet)
         (not path-length-so-far-is-zero?))
    true
    (empty? convoy-locations)
    false
    :else
    ;; TODO this is very inefficient on large inputs
    (->> convoy-locations
         (filter #(maps/colocated-edge-accessible-to? dmap start-loc % :fleet))
         (some (fn [next-convoy]
                 (convoy-path-exists?-helper
                  dmap
                  next-convoy
                  end-loc
                  (disj convoy-locations next-convoy)
                  false)))
         boolean)))

(defn-spec convoy-path-exists? [::dt/dmap ::dt/location ::dt/location ::dt/orders]
  boolean?)
(defn convoy-path-exists?
  [dmap start-loc end-loc convoy-orders]
  (let [convoy-locations (->> convoy-orders
                              (map :location)
                              (into #{}))]
    (convoy-path-exists?-helper dmap start-loc end-loc convoy-locations true)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                Resolution Control Flow - Voyage Resolution ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn-spec dislodgment-status [::r/resolution-state ::dt/convoy-order]
  ::r/dislodgment-status)
(defn dislodgment-status
  [{:keys [dmap location-to-order-map] :as resolution-state}
   {:keys [location] :as convoy-order}]
  (let [attacking-order-statuses
        (->> (map-util/attacks-to dmap location-to-order-map location)
             (map #(eval-util/order-status resolution-state %)))]
    (cond
      (some #(= :succeeded %) attacking-order-statuses)
      :dislodged
      (some #(= :pending %) attacking-order-statuses)
      :pending
      (every? #(= :failed %) attacking-order-statuses)
      :not-dislodged)))

(defn-spec convoying-order-statuses [::r/resolution-state ::dt/attack-order]
  (s/map-of ::r/dislodgment-status (s/coll-of ::dt/convoy-order)))
(defn convoying-order-statuses
  [{:keys [convoy-map] :as rs}
   attack-order]
  (let [attempted-convoys (get convoy-map attack-order [])]
    (group-by #(dislodgment-status rs %) attempted-convoys)))

(defn-spec evaluate-voyage [::r/resolution-state ::dt/attack-order]
  ::r/voyage-status)
(defn evaluate-voyage
  [{:keys [dmap convoy-map] :as rs}
   {:keys [location destination] :as attack-order}]
  (let [convoys-by-status (convoying-order-statuses rs attack-order)
        successful-convoys (get convoys-by-status :not-dislodged [])
        pending-convoys (get convoys-by-status :pending [])]
    (cond
      (convoy-path-exists? dmap location destination successful-convoys)
      :succeeded
      (convoy-path-exists? dmap location destination
                           (concat successful-convoys pending-convoys))
      :pending
      :else
      :failed)))

;; NOTE: signature is very different from `apply-conflict-state-updates`
(defn-spec apply-voyage-state-update
  [::r/resolution-state ::dt/attack-order ::r/voyage-status] ::r/resolution-state)
(defn apply-voyage-state-update
  [resolution-state pending-voyage voyage-status]
  (-> resolution-state
      (update :voyage-queue
              #(if (= voyage-status :pending)
                 (conj (pop %) (peek %))
                 (pop %)))
      (update :voyage-map #(assoc % pending-voyage voyage-status))))

(defn-spec take-voyage-resolution-step
  [::r/resolution-state] ::r/resolution-state)
(defn take-voyage-resolution-step
  "Tries to resolve the next voyage in the voyage queue."
  [{:keys [voyage-map voyage-queue] :as resolution-state}]
  (when diplomacy.settings/debug
    (print "voyage-queue: ")
    (clojure.pprint/pprint voyage-queue)
    (print "voyage-map: ")
    (clojure.pprint/pprint voyage-map))

  (if (empty? voyage-queue)
    resolution-state
    (let [pending-voyage (peek voyage-queue)
          voyage-status-update
          (evaluate-voyage resolution-state pending-voyage)]
      (when diplomacy.settings/debug
        (print "voyage-update: ")
        (clojure.pprint/pprint [pending-voyage voyage-status-update]))
      (apply-voyage-state-update
       resolution-state pending-voyage voyage-status-update))))
