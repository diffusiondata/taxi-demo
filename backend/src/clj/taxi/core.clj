(ns taxi.core
  (:gen-class)
  (:require [taxi.communication :as diffusion]
            [taxi.auction :as auction])
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

(def local-connection "ws://localhost:8080")
(def reappt-connection "wss://pushingstrikingAres.us.reappt.io:443")
(def env-connection
  (str
    (if (= (.get (System/getenv) "REAPPT_SECURE") "true") "wss://" "ws://")
    (.get (System/getenv) "REAPPT_HOST")
    ":"
    (.get (System/getenv) "REAPPT_PORT")))

(defn- create-session
  "Create a new session from the session factory."
  [session-factory]

  (.open session-factory env-connection))

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
    (diffusion/register-message-handler session jackie "controller/auctions")
    (diffusion/remove-topics-with-session session jackie "controller/auctions"))

  ; Trigger connect again
  (when (= new-state
           Session$State/CLOSED_FAILED)
    (create-session)))

(defn -main [& args]
  (println "Starting controller")

  (let [jackie (chan)
        app-state (atom {:auctions {}
                         :last-auction-id 0})
        session-factory (diffusion/create-session-factory jackie "taxi-controller" "taxi")]

    (create-session session-factory)

    ; Handle events received on the channel
    (<!! (go
          (while true
          (let [event (<! jackie)]

            (cond
             (:state-change event) (handle-session-state-change (:state-change event) jackie app-state)
             (:message event) (auction/process-message app-state jackie (:session-id (:message event)) (:content (:message event)))
             :else (println "Unknown event:" event))

            ))))

  (println "Shutting down")))
