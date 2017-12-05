
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

(defproject taxi-controller "0.1.0-SNAPSHOT"
  :description "Taxi demo controller"
  :url "http://github.com/pushtechnology/"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :repositories {"diffusion" "http://download.pushtechnology.com/maven/"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/core.async "0.2.374"]
                 [org.clojure/data.json "0.2.6"]
                 [com.pushtechnology.diffusion/diffusion-client "5.9.16"]]

  :source-paths ["src/clj"]
  :resource-paths ["src/resources"]

  :main taxi.core
  :aot :all

  :aliases {"release-build" ["do" "clean" ["uberjar"]]})
