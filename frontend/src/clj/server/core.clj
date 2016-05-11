
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

