(ns server.core
  (:use compojure.core)
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.middleware.not-modified :as etag]
            [ring.util.response :as resp]))

(def endpoint
  (str
    "window.reapptHost = '"
    (.get (System/getenv) "REAPPT_HOST")
    "';\n"
    "window.reapptPort = '"
    (.get (System/getenv) "REAPPT_PORT")
    "';\n"
    "window.reapptSecure = "
    (.get (System/getenv) "REAPPT_SECURE")
    ";\n"))

(defroutes app-routes
  (GET "/" [] (resp/redirect "/index.html"))
  (GET "/js/endpoint.js" [] endpoint)
  (route/resources "/")
  (route/not-found "Not Found"))

(def handler
  (etag/wrap-not-modified (handler/site app-routes)))

