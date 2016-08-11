
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

(ns ^:figwheel-always taxi.navigation

    (:require [taxi.taxi :as taxi]
              [taxi.grid-rendering :as grid]
              [taxi.controller :as controller]
              [taxi.passenger :as passenger]
              [om.core :as om :include-macros true]
              [om.dom :as dom :include-macros true]
              [om-bootstrap.button :as b]
              [om-bootstrap.nav :as n]
              [om-bootstrap.random :as r]
              [secretary.core :as sec :include-macros true]
              [goog.events :as events]
              [goog.history.EventType :as EventType])
    (:require-macros [cljs.core.async.macros :refer [go go-loop]])
    (:import goog.History))

; Secretary set up.
(sec/set-config! :prefix "#")

(let [history (History.)
      navigation EventType/NAVIGATE]
  (goog.events/listen history navigation #(-> % .-token sec/dispatch!))
  (doto history (.setEnabled true)))


(declare controller-page auction-page taxi-page passenger-page)

(defn- set-location [l]
  (-> js/document .-location (set! l)))

(defn navigation-view [data owner]
  (reify
    om/IInitState
    (init-state [_] {:active-key 1})

    om/IRenderState
    (render-state [_ state]
      (n/nav {:bs-style "tabs"
              :active-key (:active-key state)
              :on-select (fn [k y]
                           (set-location y)
                           (om/set-state! owner :active-key k)
                           )
              }
       (n/nav-item {:key 1 :href (controller-page)} "World view")
       (n/nav-item {:key 2 :href (auction-page)} "View auctions")
       (n/nav-item {:key 3 :href (taxi-page)} "Taxis")
       (n/nav-item {:key 4 :href (passenger-page)} "Add passenger")))))


(defn controller-view [data _]
  (reify
    om/IRender
    (render [_]
      (dom/div
       #js {:className "content"}
       (r/page-header {} "World view")
       (om/build grid/view data)))))

(defn auction-view [data _]
  (reify
    om/IRender
    (render [_]
      (dom/div
       #js {:className "content"}
       (r/page-header {} "View auctions")
       (om/build controller/auctions-view data)))))

(defn taxi-page-view [data _]
  (reify om/IRender
    (render [_]
      (dom/div
       #js {:className "content"}
       (r/page-header {} "Taxis")
       (om/build taxi/view data)
       ))))

(defn passenger-page-view [data _]
  (reify om/IRender
    (render [_]
      (dom/div
       #js {:className "content"}
       (r/page-header {} "Add passenger")
       (om/build passenger/view data)
       ))))


(defn render [state]

  (sec/defroute controller-page "/controller" []
    (om/root controller-view
             state
             {:target (. js/document (getElementById "app"))}))

  (sec/defroute auction-page "/auction" []
    (om/root auction-view
             state
             {:target (. js/document (getElementById "app"))}))

  (sec/defroute taxi-page "/taxi" []
    (om/root taxi-page-view
             state
             {:target (. js/document (getElementById "app"))}))

  (sec/defroute passenger-page "/passenger" []
    (om/root passenger-page-view
             state
             {:target (. js/document (getElementById "app"))}))

  (om/root navigation-view
           {}
           {:target (. js/document (getElementById "nav"))})

  (set-location (controller-page)))
