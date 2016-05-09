(ns ^:figwheel-always taxi.controller
    "Controller model and application."

    (:require [taxi.communication :as d]
              [taxi.taxi :as taxi]
              [taxi.world :as world]
              [taxi.util :as util]
              [om.core :as om :include-macros true]
              [om.dom :as dom :include-macros true]
              [om-bootstrap.panel :as p]
              [om-bootstrap.random :as r]
              [cljs.core.async :refer [put! chan <! timeout close!]])
    (:require-macros [cljs.core.async.macros :refer [go alt!]]))


(defn- taxi-joined
  [id state]
    (println "Taxi" id "joined")
    (assoc-in state [:all-taxis id] {}))

(defn- calculate-speed
  [old-position new-position]
  (+ (js/Math.abs (- (nth new-position 0) (nth old-position 0)))
     (js/Math.abs (- (nth new-position 1) (nth old-position 1)))))

(defn- taxi-update
  [id new-position state]
  (if new-position
      (let [updated-taxi (get (:all-taxis state) id)
            current-time (.getTime (js/Date.))]
        (assoc-in state [:all-taxis id]
            (if (nil? (:known-position updated-taxi))
                ; Update a taxi for the first time
                (assoc updated-taxi
                  :known-position new-position
                  :position new-position
                  :timestamp current-time
                  :age 0)

                ; Update a taxi and estimate the speed
                (let [old-position (:known-position updated-taxi)
                      estimated-speed (calculate-speed old-position new-position)]
                  (assoc updated-taxi
                    :known-position new-position
                    :position new-position
                    :estimated-speed estimated-speed
                    :timestamp current-time
                    :age 0)))))
      state))

(defn- taxi-retired
  [id state]
    (println "Taxi" id "retired")
    (update-in state [:all-taxis] dissoc id))

(defn- process-taxi
  [state event]
  (let [taxi (:topic event)]
        (swap! state (condp = (:type event)
                       :subscribed   (partial taxi-joined taxi)
                       :update       (partial taxi-update taxi (:value event))
                       :unsubscribed (partial taxi-retired taxi)))))


(defn- auction-view [auction _]
  (reify
    om/IRender
    (render [_]
      (let [{:keys [id bid bidder auction-state]
             {:keys [location destination passenger]} :journey} @auction]
        (dom/tr nil
                (dom/td nil id)
                (dom/td nil (name auction-state))
                (dom/td nil passenger)
                (dom/td nil (util/location-to-string location))
                (dom/td nil (util/location-to-string destination))
                (dom/td nil (util/money-to-string bid))
                (dom/td nil bidder)
                )))))

(defn auctions-view [data _]
  (reify
    om/IRender
    (render [_]
      (dom/div nil
         (dom/table #js { :className "table" }
                    (dom/thead nil
                               (dom/th nil "Auction")
                               (dom/th nil "State")
                               (dom/th nil "Passenger")
                               (dom/th nil "From")
                               (dom/th nil "To")
                               (dom/th nil "Bid")
                               (dom/th nil "Bidder")
                               )

                    (apply dom/tbody nil
                           (map #(om/build auction-view %) (vals (:auctions data)))))))))


(defn- taxi-prediction
  "Predict where a taxi will be for the next rendered frame."
  [new-map [id {:keys [position estimated-speed] :as taxi}]]

  (if (nil? estimated-speed)
    (assoc new-map id taxi)
    (let [x (nth position 0)
          y (nth position 1)
          step (/ estimated-speed world/frames-per-second)
          age (- (.getTime (js/Date.)) (:timestamp taxi))]
      (if (not (= 3 (count position)))
        (assoc new-map id taxi)
        (case (nth position 2)
          :up (assoc new-map id (assoc taxi :position [x (+ y step) :up] :age age))
          :down (assoc new-map id (assoc taxi :position [x (- y step) :down] :age age))
          :left (assoc new-map id (assoc taxi :position [(- x step) y :left] :age age))
          :right (assoc new-map id (assoc taxi :position [(+ x step) y :right] :age age)))))))

(defn- taxi-predictions
  "Predict where all the taxis are going to be for the next rendered frame."
  [{:keys [all-taxis] :as state}]

  (assoc state :all-taxis (reduce taxi-prediction {} all-taxis)))

(defn- process-journey-event [app-state journey-event]
  (println "New journey notification " journey-event)
  (when (= (:type journey-event) :update)
    (let [journey-id (:journey-id (:value journey-event))]
      (swap! app-state assoc-in [:global-journeys journey-id] (:value journey-event))))

  (when (= (:type journey-event) :unsubscribed)
    (let [id (.parseInt js/window (get (re-matches #"controller/journey/(.*)" (:topic journey-event)) 1))]
      (when id
        (swap! state update-in [:global-journeys] dissoc id)))))

(defn init
  "Initialise controller. We don't use the Om component lifecycle because
   that's initialised every time the component is remounted.

   `app-state` is the application state atom.
   Returns a channel that will be sent an event when initialisation is complete, or closed."
  [app-state]


  (let [{:keys [session error]}
        ;; Update application state with our fields.
        (swap! app-state assoc
               :auctions {}
               :next-auction-id 0
               :global-journeys {})
        result (chan)]

    (go
     (let [taxi-locations (d/subscribe error session "?taxi/.*/.*")
           journeys (d/subscribe error session "?controller/journey/")]

       (>! result true)

       (while (.isConnected session)
         (alt!
          taxi-locations             ([e] (process-taxi app-state e))

          journeys ([e] (process-journey-event app-state e))

          ;; Regular updates
          (timeout world/frame-time) ([_] (swap! app-state taxi-predictions))))))

    result))
