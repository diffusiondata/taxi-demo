
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

(ns server.core
  (:use compojure.core)
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.middleware.not-modified :as etag]
            [ring.util.response :as resp]))

;; This populates the js/endpoint.js. It sets targeted Reappt server based on
;; the environmental variables REAPPT_HOST, REAPPT_PORT and REAPPT_SECURE.
;; If defaults to localhost:8080
(def endpoint
  (str
    "window.reapptHost = '"
    (or (.get (System/getenv) "REAPPT_HOST") "localhost")
    "';\n"
    "window.reapptPort = '"
    (or (.get (System/getenv) "REAPPT_PORT") "8080")
    "';\n"
    "window.reapptSecure = "
    (or (.get (System/getenv) "REAPPT_SECURE") "false")
    ";\n"))

(defroutes app-routes
  (GET "/" [] (resp/redirect "/index.html"))
  (GET "/js/endpoint.js" [] endpoint)
  (route/resources "/")
  (route/not-found "Not Found"))

(def handler
  (etag/wrap-not-modified (handler/site app-routes)))

