(ns ^:figwheel-always taxi.passenger
    "Passenger model and application."

    (:require [taxi.communication :as d]
              [taxi.world :as world]
              [om.core :as om :include-macros true]
              [om.dom :as dom :include-macros true]
              [om-bootstrap.input :as i]
              [om-bootstrap.panel :as p]
              [om-bootstrap.random :as r]
              [om-bootstrap.button :as b]
              [cljs.reader :as edn]
              [cljs.core.async :refer [put! chan <! timeout]])
    (:require-macros [cljs.core.async.macros :refer [go go-loop]]))


(def next-id (atom 0))

(defn- new-journey
  "Create a new journey"
  [error session]

  (let [name (str (.-sessionID session) "-" (swap! next-id inc))]

    {
     :name name
     :position [0 0]
     }))

(defn- add-journey [{:keys [error session journeys] :as data}]
  (om/transact! data :journeys #(conj % (new-journey error session))))

(defn- process-event [v]
  (println "RECEIVED " v))

(defn init [app-state]
  "Initialise passenger.

   `app-state` is the application state atom.
   Returns a channel that will be sent an event when initialisation is complete, or closed."


  ;; Update application state with our fields.
  ;; Subscribe to passenger/session-id

  (let [{:keys [session error passenger]}
        (swap! app-state
               (fn [state]
                 (-> state
                     (assoc :journeys [])
                     (assoc :passenger (.-sessionID (:session state))))))
        topic-name (str "controller/passenger/" passenger)
        updates (d/subscribe error session topic-name)]

    (go-loop [e (<! updates)]
      (when (and e (.isConnected session))
        (when (= :update (:type e))
          process-event (:value e))
        recur (<! updates)))))

(defn- journey-view [journey _]
  (reify
    om/IRender
    (render [_]
      (let [{:keys [name position destination route]} journey
            padding #js {:style #js {:padding-right 10}}]
        (p/panel
         {:header (str "Journey " name) }

         (comment dom/table nil
                    (dom/thead nil
                               (dom/th padding "Position")
                               (dom/th padding "Direction")
                               (dom/th padding "Destination")
                               (dom/th padding "Route"))
                    (dom/tbody nil
                               (dom/tr nil
                                       (dom/td nil (location-to-string position))
                                       (dom/td nil (direction-to-string (get position 2)))
                                       (dom/td nil (location-to-string destination))
                                       (dom/td nil (str route))
))
                    ))))))

(defn- parse-location [location-str]
  (let [[_ x y] (re-matches #"\s*(\d+)\s+(\d+)\s*" location-str)]
    (when (and x y)
      [(js/parseInt x) (js/parseInt y)])))

(defn- journey-request [p location destination]
  {:passenger p
   :location location
   :destination destination})

(defn- new-journey [data owner]
  (let [start (-> (om/get-node owner "start") .-value parse-location)
        end (-> (om/get-node owner "end") .-value parse-location)]

    (when (and start end)
      (let [{:keys [error session passenger]} data
            request (journey-request (:passenger data) start end)]
        (d/send-message error session "controller/auctions" {:type :journey :value request})))))

(defn- new-journey-form-event [event data owner]
  (new-journey data owner)
  (.stopPropagation event)
  (.preventDefault event)
  false)

(defn view [data owner]
  (reify
    om/IInitState
    (init-state [_]
      {:start-valid false
       :end-valid false})
    om/IRender
    (render [_]
      (dom/div nil
         (dom/form #js {:className "form-vertical"}
                   (i/input {:type "text" :label "Where are you?" :ref "start"})
                   (i/input {:type "text" :label "Where do you want to go?" :ref "end"})
                   (dom/button #js {:onClick #(new-journey-form-event % data owner)} "New journey")
                   )

       (apply p/panel {}
              (map #(om/build journey-view %) (:journeys data)))))))
