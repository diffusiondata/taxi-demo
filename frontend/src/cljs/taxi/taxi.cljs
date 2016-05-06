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
  [{:keys [position route speed state] :as taxi-state}]

  (if (seq route)
    ; If there is a current route continue to follow it
    (follow-route taxi-state route)
    (condp = state
      ; If we are roaming pick a random destination
      :roaming (set-destination taxi-state position speed (random-destination))
      ; If we are collecting we have arrived near the passenger
      :collecting (-> taxi-state
                      (stop-taxi-moving)
                      (assoc :state :carrying))
      ; If we are carrying we have arrived near the passenger's destination
      :carrying (-> taxi-state
                    (stop-taxi-moving)
                    (assoc :state :roaming))
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
      :bid (if current
             (* current (+ 0.7 (rand 0.25)))
             (+ 10 (rand 20))))))

(defn- add-taxi
  "Create a new taxi"
  [data]

  (let [{:keys [error session taxi-topic-root]} data
        name (next-taxi-name data)
        topic-name (str taxi-topic-root "/" name)
        taxi {:name name :speed (+ 0.2 (rand 0.5)) :state :roaming}]

    (om/transact! data :taxis #(conj % taxi))

    (go
      (when (<! (d/add-topic error session topic-name [0 0]))
        (om/transact! data :taxis
                      #(for [t %] (if (= (:name t) name) (assoc t :topic topic-name)
                                      t)))))

    ; Randomly select an open auction to bid on
    (let [open-auction (rand-nth (vals (:auctions data)))]
      (when open-auction
        (d/send-message error session "controller/auctions" {:type :bid :value (generate-new-bid open-auction taxi)})
    ))))

(defn- move-taxis [{:keys [error session taxis] :as app-state}]

  (let [moved (map move-taxi taxis)]
    (doseq [t moved]
      (if-let [{:keys [topic position]} t]
        (d/update-topic error session topic position)))

    (assoc app-state :taxis (vec moved))))

(defn- new-bid [e taxi bid-chan]
  (go
    (<! (timeout (+ 1000 (rand-int 4000))))
      (>! bid-chan (generate-new-bid e taxi))))

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

(defn- process-auction-winner [state taxi-name bid location destination]
  ;; Need a way to update the taxi state with a new destination
  (println "And the winner is" taxi-name "at" (util/money-to-string bid))
  (when
    (some (partial check-taxi-name taxi-name) (:taxis @state))
    (d/send-message (:error @state) (:session @state) "controller/auctions" {:type :accept-journey})
    (swap!
      state
      modify-taxi
      taxi-name
      (fn [taxi]
        (assoc
          (set-destination taxi (:position taxi) (:speed taxi) location)
          :state :collecting)))
    ))

(defn- process-auction-update [e state bid-chan]
  ;; Ignore the last bidder
  (swap! state assoc-in [:auctions (:id e)] e)
  (condp = (:auction-state e)
    ;; Really simple, stateless bidding
    ;; Pick a random taxi
    ;; Wait 1-5s
    ;; If no current bid, start at £10-30
    ;; Otherwise bid 70-95% of current value.
    :open (let [bidding-taxis (remove (partial check-taxi-name (:bidder e)) (:taxis @state))]
            (when (not-empty bidding-taxis)
              (new-bid e (rand-nth bidding-taxis) bid-chan)))

    :offered (process-auction-winner state (:bidder e) (:bid e) (:location (:journey e)) (:destination (:journey e)))

    (println "Ignoring " e)))

(defn- process-auction-remove [topic-path state]
  (let [id (.parseInt js/window (get (re-matches #"controller/auctions/(.*)" topic-path) 1))]
    (when id
      (swap! state update-in [:auctions] dissoc id))))


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
        ; Create channel to trigger bid placement
        bid-chan (chan)]

    ; Setup go routines to update the taxi positions and react to events
    (go
      (while (.isConnected session)
        (alt!
          auctions                     ([e] (condp = (:type e)
                                              :update (process-auction-update (:value e) app-state bid-chan)
                                              :unsubscribed (process-auction-remove (:topic e) app-state)
                                              :subscribed nil))

          bid-chan                     ([e] (d/send-message error session "controller/auctions" {:type :bid :value e}))

          (timeout world/update-speed) ([_] (swap! app-state move-taxis)))
        ))


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
      (let [{:keys [name position destination route speed state]} taxi]
        (p/panel
         {:header (str "Taxi " name) }

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
