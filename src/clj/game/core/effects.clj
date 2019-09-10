(ns game.core.effects
  (:require [game.core.card-defs :refer [card-def]]
            [game.core.eid :refer [make-eid]]
            [game.core.card :refer [get-card]]
            [game.utils :refer [same-card? to-keyword]]
            [clj-uuid :as uuid]))

(defn register-persistent-effects
  [state card]
  (when (:persistent-effects (card-def card))
    (let [persistent-effects (:persistent-effects (card-def card))
          abilities (for [ability persistent-effects]
                      (assoc
                        (select-keys ability [:type :req :effect])
                        :duration :persistent
                        :card card
                        :uuid (uuid/v1)))]
      (swap! state update :effects
             #(apply conj % abilities))
      abilities)))

(defn unregister-persistent-effects
  [state card]
  (when (:persistent-effects (card-def card))
    (swap! state assoc :effects
           (->> (:effects @state)
                (remove #(and (same-card? card (:card %))
                              (= :persistent (:duration %))))
                (into [])))))

(defn create-floating-effect
  [state card ability]
  (let [ability (assoc
                  (select-keys ability [:type :duration :req :effect])
                  :card card
                  :uuid (uuid/v1))]
    (swap! state update :effects conj ability)
    ability))

(defn remove-floating-effects
  [state duration]
  (swap! state assoc :effects
         (->> (:effects @state)
              (remove #(= duration (:duration %)))
              (into []))))

(defn- gather-effects
  [state side effect-type]
  (let [get-side #(-> % :card :side to-keyword)
        is-active-player #(= (:active-player @state) (get-side %))]
    (->> (:effects @state)
         (filter #(= effect-type (:type %)))
         (sort-by (complement is-active-player)))))

(defn get-effects
  "Filters and then 'executes' the effects of a given type."
  [state side card effect-type]
  (let [eid (make-eid state)]
    (->> (gather-effects state side effect-type)
         (filter #(if-not (:req %)
                    true
                    ((:req %) state side eid (get-card state (:card %)) [card])))
         (mapv #(if-not (fn? (:effect %))
                  (:effect %)
                  ((:effect %) state side eid (get-card state (:card %)) [card]))))))

(defn sum-effects
  "Sums the results from get-effects."
  [state side card effect-type]
  (reduce + (filter identity (get-effects state side card effect-type))))
