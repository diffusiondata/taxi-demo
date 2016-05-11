
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

(ns ^:figwheel-always taxi.taxi
    "Taxi model and application."

    (:require [taxi.communication :as d]
              [taxi.world :as world]
              [taxi.util :as util]
              [om.core :as om :include-macros true]
              [om.dom :as dom :include-macros true]
              [om-bootstrap.panel :as p]
              [om-bootstrap.random :as r]
              [cljs.core.async :refer [put! chan <! timeout]])
    (:require-macros [cljs.core.async.macros :refer [go alt!]]))

;;
;; Taxi states: :roaming, :bidding, :collecting, :carrying
;;

(defn- message
  "Wrap a value in a message of a given type."
  [type value]
  {:type type :value value})

(defn- send-message
  "Send a message."
  [chan type value]

  (go (>! chan (message type value))))

(defn- random-destination
  "Generates a random destination with the bounds of the world."
  []

  [(rand-int world/grid-width)
   (rand-int world/grid-height)])

(defn- quantise [[x y]]
  (let [rnd js/Math.round]
    [(rnd x) (rnd y)]))

(defn- pick-direction
  "Pick a direction that heads towards the destination."
  [[x1 y1] [x2 y2]]

  (let [xd (- x2 x1)
        yd (- y2 y1)
        abs js/Math.abs
        axd (abs xd)
        ayd (abs yd)]
    (cond
      (and (< axd 1) (< ayd 1)) nil
      (< ayd 1) (if (> x2 x1) :right :left)
      (< axd 1) (if (> y2 y1) :up :down)
      :else (if (> (rand) 0.5)
              (if (> x2 x1) :right :left)
              (if (> y2 y1) :up :down)))))

(defn- step [[x1 y1 :as start] [x2 y2 :as end]]
  (condp = (pick-direction start end)
    :left  [start [(dec x1) y1] :left]
    :right [start [(inc x1) y1] :right]
    :up    [start [x1 (inc y1)] :up]
    :down  [start [x1 (dec y1)] :down]
    nil nil))

(defn- steps [from to]
  (lazy-seq
   (if-let [s (step from to)]
     (cons s (steps (s 1) to))
     [[from to nil]])))

(defn- interpolate [route speed]
  "Takes a route to a destination and a speed and returns a sequence of
  positions and directions. The returned sequence always includes the points
  of the route and may include points inbetween them."
  (for [[[x y] _e dir] route
        d (if dir (range 0 1 speed) [:last])]
    (condp = dir
      :up    [x (+ y d) :up]
      :down  [x (- y d) :down]
      :left  [(- x d) y :left]
      :right [(+ x d) y :right]
      nil [x y])))

(defn- steps-to-updates
  "Takes a route to a destination and a speed and returns a sequence of
  positions and directions mostly at regular intervals. The last value is the
  destination and might be reached at an irregular time."
  ([route speed] (steps-to-updates route speed speed))
  ([route speed distance]
   (let [[[sx sy] [ex ey] dir] (first route)]
     (condp = dir
       :up    (if (> ey (+ sy distance))
                (cons [sx (+ sy distance) :up]
                      (steps-to-updates
                        (cons [[sx (+ sy distance)] [ex ey] :up]
                              (next route)) speed))
                (steps-to-updates (next route) speed (- (+ sy distance) ey)))
       :down  (if (< ey (- sy distance))
                (cons [sx (- sy distance) :down]
                      (steps-to-updates
                        (cons [[sx (- sy distance)] [ex ey] :down]
                              (next route)) speed))
                (steps-to-updates (next route) speed (- distance (- sy ey))))
       :left  (if (< ex (- sx distance))
                (cons [(- sx distance) sy :left]
                      (steps-to-updates
                        (cons [[(- sx distance) sy] [ex ey] :left]
                              (next route)) speed))
                (steps-to-updates (next route) speed (- distance (- sx ex))))
       :right (if (> ex (+ sx distance))
                (cons [(+ sx distance) sy :right]
                      (steps-to-updates
                        (cons [[(+ sx distance) sy] [ex ey] :right]
                              (next route)) speed))
                (steps-to-updates (next route) speed (- (+ sx distance) ex)))
       nil [[ex ey]]))))

(defn- set-destination
  "Set the destination for a taxi and selects a route to it from the current position."
  [taxi-state position speed new-destination]

  (-> taxi-state
      (assoc :destination new-destination)
      (assoc :route (steps-to-updates (steps (quantise position) new-destination) speed))))

(defn- stop-taxi-moving
  "Stop the taxi from moving. Clear the route."
  [{:keys [position] :as taxi-state}]

  (-> taxi-state
        (assoc :position (quantise position))
        (dissoc :route)))

(defn- follow-route
  "Move the taxi along its route."
  [taxi-state route]

  (-> taxi-state
      (assoc :position (first route))
      (assoc :route (next route))))

(defn- move-taxi
  "Move the taxi to its next position."
  [message-chan app-state {:keys [position route speed state name] :as taxi-state}]

  (if (seq route)
    ; If there is a current route continue to follow it
    (follow-route taxi-state route)
    (condp = state
      ; If we are roaming pick a random destination
      :roaming (set-destination taxi-state position speed (random-destination))
      ; If we are collecting we have arrived near the passenger
      :collecting (do
                    (send-message message-chan :collection-arrival (:journey-id taxi-state))
                    (-> taxi-state
                      (set-destination position speed (:destination (:journey (get (:global-journeys app-state) (:journey-id taxi-state)))))
                      (assoc :state :carrying)))
      ; If we are carrying we have arrived near the passenger's destination
      :carrying (do
                  (send-message message-chan :passenger-drop-off (:journey-id taxi-state))
                  (-> taxi-state
                    (stop-taxi-moving)
                    (assoc :state :roaming)
                    (dissoc :journey-id)))
      ; If we are bidding pick a random destination
      :bidding (set-destination taxi-state position speed (random-destination))
      ; Otherwise stop moving the taxi
      (stop-taxi-moving taxi-state))))

(defn- next-taxi-name
  "Generate a new taxi name."
  [data]

  (let [{:keys [session next-taxi-id]} (deref (om/transact! data :next-taxi-id inc))]
    (str (.-sessionID session) "-" next-taxi-id)))

(defn- generate-new-bid [current-bidding taxi]
  (let [current (:bid current-bidding)]
    (assoc current-bidding
      :bidder (:name taxi)
      :bidder-display-name (:display-name taxi)
      :bid (if current
             (* current (+ 0.7 (rand 0.25)))
             (+ 10 (rand 20))))))

(defn- add-taxi
  "Create a new taxi"
  [data]

  (let [{:keys [error session taxi-topic-root]} data
        name (next-taxi-name data)
        topic-name (str taxi-topic-root "/" name)
        taxi {:name name :speed (+ 0.2 (rand 0.5)) :state :roaming :display-name (util/new-name)}]

    (om/transact! data :taxis #(conj % taxi))

    (go
      (when (<! (d/add-topic error session topic-name {:display-name (:display-name taxi) :position [0 0]}))
        (om/transact! data :taxis
                      #(for [t %] (if (= (:name t) name) (assoc t :topic topic-name)
                                      t)))))))

(defn- move-taxis [{:keys [error session taxis] :as app-state} message-chan]

  (let [moved (map (partial move-taxi message-chan app-state) taxis)]
    (doseq [t moved]
      (if-let [{:keys [topic position display-name]} t]
        (d/update-topic error session topic {:display-name display-name :position position})))

    (assoc app-state :taxis (vec moved))))

(defn- check-taxi-name [n t]
  (= (:name t) n))

(defn- modify-taxi [state taxi-name action]
  (let [new-taxis (map (fn [taxi]
           (if (check-taxi-name taxi-name taxi)
             (action taxi)
             taxi))
         (:taxis state))]
  (assoc
    state
    :taxis
    new-taxis)))

(defn- process-auction-winner [state message-chan taxi-name bid location destination journey-id auction-id]
  ;; Need a way to update the taxi state with a new destination
  (println "And the winner is" taxi-name "at" (util/money-to-string bid))
  (when
    (some (partial check-taxi-name taxi-name) (:taxis @state))
    (send-message message-chan :acknowledge-win {:auction-id auction-id})
    (swap!
      state
      modify-taxi
      taxi-name
      (fn [taxi]
        (-> taxi
          (set-destination (:position taxi) (:speed taxi) location)
          (assoc :state :collecting)
          (assoc :journey-id journey-id))))))

(defn- taxis-available-to-bid
  "Return a collection of taxis currently available to bid on auctions.
  Filters busy taxis and bidders leading an auction."
  [auctions taxis]

  (let [leading-bidders (filter #(not (nil? %)) (map #(:bidder %) (vals auctions)))
        free-taxis (filter #(= (:state %) :roaming) taxis)]
    (filter (fn [taxi]
              (not (some #(= % (:name taxi)) leading-bidders)))
            free-taxis)))

(defn- apply-auction-update
  "Update the auction and taxi state on an auction update."
  [state auction]

  (let [current-bidder (get-in state [:auctions (:id auction) :bidder])
        new-bidder (:bidder auction)]
    (if current-bidder
      (-> state
          (modify-taxi current-bidder #(assoc % :state :roaming))
          (modify-taxi new-bidder #(assoc % :state :bidding))
          (assoc-in [:auctions (:id auction)] auction))
      (-> state
          (modify-taxi new-bidder #(assoc % :state :bidding))
          (assoc-in [:auctions (:id auction)] auction)))))

(defn- process-auction-update [auction state message-chan]
  ;; Update local state with new value
  (let [new-state (swap! state apply-auction-update auction)]
    (condp = (:auction-state auction)
      :offered (process-auction-winner
                 state
                 message-chan
                 (:bidder auction)
                 (:bid auction)
                 (:location (:journey auction))
                 (:destination (:journey auction))
                 (:journey-id auction)
                 (:id auction))

      nil)))

(defn- process-auction-remove [topic-path state]
  (let [id (.parseInt js/window (get (re-matches #"controller/auctions/(.*)" topic-path) 1))]
    (when id
      (swap! state update-in [:auctions] dissoc id))))

(defn- bid-on-auctions
  "Bid on the open auctions with the available taxis."
  [state message-chan]

  (let [bidding-taxis (taxis-available-to-bid (:auctions state) (:taxis state))
        open-auctions (filter #(= :open (:auction-state %)) (vals (:auctions state)))]
    (if (and (not-empty bidding-taxis) (not-empty open-auctions))
      (let [taxi-to-bid (rand-nth bidding-taxis)
            auction-to-bid-on (rand-nth open-auctions)]
        (send-message message-chan :bid (generate-new-bid auction-to-bid-on taxi-to-bid))
        (modify-taxi state (:name taxi-to-bid) #(assoc % :state :bidding)))
      state)))

(defn init [app-state]
  "Initialise taxis. We don't use the Om component lifecycle because
   that's initialised every time the component is remounted.

   `app-state` is the application state atom.
   Returns a channel that will be sent an event when initialisation is complete, or closed."

  (let [{:keys [session error taxi-topic-root]}
        ; Update application state with our fields.
        (swap! app-state
               (fn [{:keys [session] :as old-state}]
                 (assoc old-state
                        :taxi-topic-root (str "taxi/" (.-sessionID session))
                        :next-taxi-id 0
                        :taxis [])))
        ; Listen for changes to auctions
        auctions (d/subscribe error session "?controller/auctions/")
        ; Create channel to send a message
        message-chan (chan)]

    ; Setup go routines to update the taxi positions and react to events
    (go
      (while (.isConnected session)
        (alt!
          auctions                     ([e] (condp = (:type e)
                                              :update (process-auction-update (:value e) app-state message-chan)
                                              :unsubscribed (process-auction-remove (:topic e) app-state)
                                              :subscribed nil))

          message-chan                 ([e] (d/send-message error session "controller" e)))))

    (go
      (while (.isConnected session)
        (<! (timeout world/update-speed))
        (swap! app-state move-taxis message-chan)))

    (go
      (while (.isConnected session)
        (<! (timeout (+ 1000 (rand-int 1000))))
        (swap! app-state bid-on-auctions message-chan)))

    ; Register a single session will.
    (d/remove-topics-with-session error session taxi-topic-root)))


(defn- speed-to-string [s]
  (if s (.toFixed s 2) "0"))

(defn- state-to-string [{:keys [state destination] :as taxi-state}]
  (condp = state
    :roaming  "Roaming"
    :collecting (str "Collecting from " (util/location-to-string destination))
    :carrying (str "Carrying to " (util/location-to-string destination))
    :bidding "Bidding"
    ""))

(defn- taxi-view [taxi _]
  (reify
    om/IRender
    (render [_]
      (let [{:keys [name position destination route speed state display-name]} taxi]
        (p/panel
         {:header (str "Taxi " display-name " " name)}

         (dom/table #js { :className "table" }
                    (dom/thead nil
                               (dom/th nil "Position")
                               (dom/th nil "Direction")
                               (dom/th nil "Speed")
                               (dom/th nil "Destination")
                               (dom/th nil "State")
                               )
                    (dom/tbody nil
                               (dom/tr nil
                                       (dom/td nil (util/location-to-string position))
                                       (dom/td nil (util/direction-to-string (get position 2)))
                                       (dom/td nil (speed-to-string speed))
                                       (dom/td nil (util/location-to-string destination))
                                       (dom/td nil (state-to-string taxi))
                                       ))))))))


(defn view [data _]
  (reify
    om/IRender
    (render [_]
      (dom/div nil
       (dom/button #js {:onClick #(add-taxi data)} "Add taxi")
       (apply p/panel {}
              (map #(om/build taxi-view %) (:taxis data)))))))
