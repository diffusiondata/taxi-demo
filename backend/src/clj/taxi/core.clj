
;; ******************************************************************************
;; Copyright (C) 2016 Push Technology Ltd.
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;; http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.
;; *******************************************************************************

(ns taxi.core
  (:gen-class)
  (:require [taxi.communication :as diffusion]
            [taxi.auction :as auction]
            [taxi.journey :as journey]
            [clojure.data.json :as json])
  (:use [clojure.core.async :only [>! <! <!! close! go go-loop chan timeout]])
  (:import com.pushtechnology.diffusion.client.session.Session$State))


;; This application is designed around a single event loop and a single channel.
;; Callbacks and notfications from Diffusion are placed onto the channel the
;; event loop consumes them.

;;
;; Auction states: :open, :offered, :accepted
;;
;; We publish all state transitions
;;
;; Multiple :open events will be published as taxis make lower bids
;;
;; After 20s, the controlller will change the state to :offered,
;; and add the winning bidder who must accept or reject
;;
;; If the bidder rejects (presumably because of accepting a better
;; offer), the auction will change back to open, the price will be
;; reset, and a new bidding round will start. If the bidder
;; accepts the auction will change to accepted


(def vcap-services
  (let [vcap-services-string (.get (System/getenv) "VCAP_SERVICES")]
    (if vcap-services-string
      (json/read-str vcap-services-string)
      nil)))

(def session-principal
  (if vcap-services
    (get-in vcap-services ["push-reappt" 0 "credentials" "principal"])
    "taxi-controller"))

(def session-credential
  (if vcap-services
    (get-in vcap-services ["push-reappt" 0 "credentials" "credentials"])
    "taxi"))

;; The connection URL, it sets targeted Reappt server based on the
;; environmental variables REAPPT_HOST, REAPPT_PORT and REAPPT_SECURE.
;; If defaults to localhost:8080
(def reappt-url
  (if vcap-services
    (str "wss://" (get-in vcap-services ["push-reappt" 0 "credentials" "host"]) ":443")
    (str
      (if (= (.get (System/getenv) "REAPPT_SECURE") "true") "wss://" "ws://")
      (or (.get (System/getenv) "REAPPT_HOST") "localhost")
      ":"
      (or (.get (System/getenv) "REAPPT_PORT") "8080"))))

(defn- create-session
  "Create a new session from the session factory."
  [session-factory]

  (.open session-factory reappt-url))

(defn- handle-session-state-change
  "Handle state change event.
  When a session become active it registers the handlers it needs and registers to clean up any auctions it creates."
  [{:keys [session old-state new-state] :as state-change} jackie app-state]
  (println "State change:" state-change)

  ; Successfully connect
  (when (and
         (= old-state
            Session$State/CONNECTING)
         (= new-state
            Session$State/CONNECTED_ACTIVE))
    (swap! app-state assoc :session session)
    (diffusion/register-message-handler session jackie "controller")
    (diffusion/remove-topics-with-session session jackie "controller/auctions")
    (diffusion/remove-topics-with-session session jackie "controller/journey"))

  ; Trigger connect again
  (when (= new-state
           Session$State/CLOSED_FAILED)
    (create-session)))

(defn -main [& args]
  (println "Starting controller")

  (let [jackie (chan)
        app-state (atom {:auctions {}
                         :last-auction-id 0
                         :journeys {}
                         :last-journey-id 0})
        session-factory (diffusion/create-session-factory jackie session-principal session-credential)]

    (create-session session-factory)

    ; Handle events received on the channel
    (<!! (go
          (while true
          (let [event (<! jackie)]

            (cond
              (:state-change event)  (handle-session-state-change (:state-change event) jackie app-state)
              (:message event)       (do
                                       (auction/process-message app-state jackie (:session-id (:message event)) (:content (:message event)))
                                       (journey/process-message app-state jackie (:session-id (:message event)) (:content (:message event))))
              (:topic-add event)     (let [detail (:topic-add event)]
                                       (when (= (:result detail) :failed) (println "Failed to add topic" (:topic-path detail) "because" (:reason detail)))
                                       (when (= (:result detail) :discarded) (println "Topic addition not confirmed for" (:topic-path detail))))
              (:topic-update event)  (let [detail (:topic-update event)] (when (= (:result detail) :error) (println "Failed to update topic" (:topic-path detail) "because" (:reason detail))))
              (:topics-remove event) (let [detail (:topic-update event)] (when (= (:result detail) :discarded) (println "Topic removal not confirmed for" (:topic-selector detail))))
              (:remove-topics-with-session event) "Failures not converted to EDN"
              (:message-handler-state event) (let [detail (:message-handler-state event)] (println "Message handler for" (:topic-path detail) "is" (:handler-state detail)))
              :else                  (println "Unknown event:" event))

            ))))

  (println "Shutting down")))
