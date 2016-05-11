(ns taxi.journey
  (:use
   [clojure.core.async :only [>! <! <!! close! go go-loop chan timeout]])
  (:require [taxi.communication :as diffusion]))

(def ^:private journey-max-keep-alive-ms 380000)
(def ^:private journey-completed-keep-alive-ms 5000)

(defn- go-remove-journey-topic
  "Remove the journey topic after a peroid of time."
  [keep-alive journey-id jackie app-state]

  (go
    (<! (timeout keep-alive))
    ;; Remove local journey state and Diffusion topic
    (let [new-state (swap! app-state update-in [:journeys] dissoc journey-id)]
      (diffusion/remove-topics (:session new-state) jackie (str ">controller/journey/" journey-id)))))

(defn- update-journey-topic
  "Update the journey information held by a topic."
  [new-journey jackie app-state]

  (diffusion/update-topic (:session @app-state) jackie (str "controller/journey/" (:journey-id new-journey)) new-journey))

(defn- complete-journey
  "Compelete the journey on notification from client."
  [journey-id jackie app-state]

  (println "Journey complete" journey-id)
  (let [new-state (swap! app-state assoc-in [:journeys journey-id :journey-state] :complete)
        new-journey (get-in new-state [:journeys journey-id])]

    (update-journey-topic new-journey jackie app-state)
    (go-remove-journey-topic journey-completed-keep-alive-ms journey-id jackie app-state)))

(defn- start-journey
  "Start the journey on notification from client."
  [journey-id jackie app-state]

  (println "Starting journey" journey-id)
  (let [new-state (swap! app-state assoc-in [:journeys journey-id :journey-state] :in-progress)
        new-journey (get-in new-state [:journeys journey-id])]

    (update-journey-topic new-journey jackie app-state)))

(defn- create-journey
  "Create a new journey from the provided state and request."
  [{:keys [last-journey-id] :as state} request]

  (let [journey-id (inc last-journey-id)
        journey {:journey-id journey-id :display-name (:display-name request) :journey (:journey request) :journey-state :pending}]
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
    (go-remove-journey-topic journey-max-keep-alive-ms journey-id jackie app-state)

    journey))

(defn process-message
  "Process message events taken from the channel.
  Dispatches based on the type of message received."
  [app-state jackie session-id {:keys [type value] :as request}]

  (condp = type
    :collection-arrival (start-journey value jackie app-state)
    :passenger-drop-off (complete-journey value jackie app-state)
    nil))
