(ns taxi.dev
    (:require
     [taxi.core :as taxi]
     [figwheel.client :as fw]))

(fw/start {
  :on-jsload (fn []
               (taxi/restart)
               )})
