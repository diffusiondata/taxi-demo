
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
            [ring.util.response :as resp]
            [clojure.data.json :as json]))

(def vcap-services
  (let [vcap-services-string (.get (System/getenv) "VCAP_SERVICES")]
    (if vcap-services-string
      (json/read-str vcap-services-string)
      nil)))

(def session-principal
  (or (get-in vcap-services ["push-reappt" 0 "credentials" "principal"]) "taxi"))

(def session-credential
  (or (get-in vcap-services ["push-reappt" 0 "credentials" "credentials"]) "taxi"))

(def diffusion-host
  (or (get-in vcap-services ["push-reappt" 0 "credentials" "host"]) (.get (System/getenv) "DIFFUSION_HOST") "localhost"))

(def diffusion-port
  (if vcap-services "443" (or (.get (System/getenv) "DIFFUSION_PORT") "8080")))

(def diffusion-secure
  (if vcap-services "true" (or (.get (System/getenv) "DIFFUSION_SECURE") "false")))

;; This populates the js/endpoint.js. It sets targeted Diffusion server based on
;; the environmental variables DIFFUSION_HOST, DIFFUSION_PORT and DIFFUSION_SECURE.
;; If defaults to localhost:8080
(def endpoint
  (str
    "window.diffusionHost = '"
    diffusion-host
    "';\n"
    "window.diffusionPort = '"
    diffusion-port
    "';\n"
    "window.diffusionSecure = "
    diffusion-secure
    ";\n"
    "window.diffusionPrincipal = '"
    session-principal
    "';\n"
    "window.diffusionCredential = '"
    session-credential
    "';\n"))

(defroutes app-routes
  (GET "/" [] (resp/redirect "/index.html"))
  (GET "/js/endpoint.js" [] endpoint)
  (route/resources "/")
  (route/not-found "Not Found"))

(def handler
  (etag/wrap-not-modified (handler/site app-routes)))
