(ns ^:figwheel-always taxi.core
    (:require [taxi.communication :as d]
              [taxi.controller :as controller]
              [taxi.passenger :as passenger]
              [taxi.taxi :as taxi]
              [taxi.grid-rendering :as rendering]
              [taxi.navigation :as navigation]
              [clojure.string :refer (join)]
              [cljs.core.async :refer [put! chan <!]])
    (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(enable-console-print!)
(js/diffusion.log "debug")

(def reappt-connection
  #js {
       :host "pushingStrikingAres.us.reappt.io"
       :port 443
       :secure true
       :reconnect false
       :principal "taxi"
       :credentials "taxi"
       }
  )

(def local-connection
  #js {
       :host "localhost"
       :port 8080
       :secure false
       :reconnect false
       :principal "control"
       :credentials "password"
       }
  )

(def env-connection
    #js {
          :host (.-reapptHost js/window)
          :port (.-reapptPort js/window)
          :secure (.-reapptSecure js/window)
          :reconnect false
          :principal "taxi"
          :credentials "taxi"
        }
  )

(def connection-options env-connection)

(defonce app-state (atom {}))

(defn- new-state
  [old-state error new-session]

  (if-let [old-session (:session old-state)]
    (.close old-session))

  ;; I'd like to pass this context around using dynamic bindings, but
  ;; cljs doesn't (yet?) allow that
  ;; https://groups.google.com/forum/#!topic/clojure/xtxRVuSOLg4
  {:session new-session
   :error error
   })

(defn restart []

  ; Use a single session per client. We replace it on page load.
  (let [errors (chan)
        connection (chan)
        error (fn [& s] (put! errors (join \space s)))]
    (d/connect connection error connection-options)

    (go-loop [e (<! errors)]
      (when e
        (js/console.error e)
        recur (<! errors)))

    (go
     (swap! app-state new-state error (<! connection))
     (and
      (<! (controller/init app-state))
      (<! (taxi/init app-state))
      (passenger/init app-state)

      (navigation/render app-state)

       ))))

(restart)
