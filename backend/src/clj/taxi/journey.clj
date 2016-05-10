(ns taxi.journey
  (:use
   [clojure.core.async :only [>! <! <!! close! go go-loop chan timeout]])
  (:require [taxi.communication :as diffusion]))

(def journey-keep-alive-ms 380000)


(defn start-journey
  "Start the journey."
  [journey-id jackie app-state]

  (let [new-state (swap! app-state assoc-in [:journeys journey-id :journey-state] :in-progress)
        new-journey (get-in new-state [:journeys journey-id])]

    (diffusion/update-topic (:session @app-state) jackie (str "controller/journey/" journey-id) new-journey)))

(defn- create-journey
  "Create a new journey from the provided state and request."
  [{:keys [last-journey-id] :as state} request]

  (let [journey-id (inc last-journey-id)
        journey {:journey-id journey-id :journey request :journey-state :pending}]
    (-> state
        (assoc-in [:journeys journey-id] journey)
        (assoc :last-journey-id journey-id))))

(defn new-journey
  "Create a new journey for display on the world view."
  [request jackie app-state]
  (let [new-state (swap! app-state create-journey request)
        journey-id (:last-journey-id new-state)
        journey (get-in new-state [:journeys journey-id])]

    (println "Creating new journey" journey-id)

    (diffusion/add-topic (:session @app-state) jackie (str "controller/journey/" journey-id) journey)

    (go
     (<! (timeout journey-keep-alive-ms))
     (diffusion/remove-topics (:session @app-state) jackie (str ">controller/journey/" journey-id)))

    journey))

(defn process-message
  "Process message events taken from the channel.
  Dispatches based on the type of message received."
  [app-state jackie session-id {:keys [type value] :as request}]

  (condp = type
    :collection-arrival (start-journey value jackie app-state)
    nil))
