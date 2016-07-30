
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

(defproject taxi "0.1.0-SNAPSHOT"
  :description "Taxi demo"
  :url "http://github.com/pushtechnology/"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/clojurescript "1.7.228"]
                 [figwheel "0.5.1"]
                 [org.clojure/core.async "0.2.374"]
                 [sablono "0.7.0"]
                 [secretary "1.2.3"]
                 [org.omcljs/om "1.0.0-alpha32"]
                 [racehub/om-bootstrap "0.6.1"]
                 [ring/ring-core "1.4.0"]
                 [compojure "1.5.0"]
                 [javax/javaee-api "7.0"]]

  :plugins [[lein-cljsbuild "1.1.3"]
            [lein-figwheel "0.5.1"]
            [lein-ring "0.9.7"]]

  :source-paths ["src/clj"]
  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]
  :aot :all

  :cljsbuild {
    :builds [{:id "dev"
              :source-paths ["src/cljs" "dev_src/cljs"]
              :compiler {:output-to "resources/public/js/compiled/taxi.js"
                         :output-dir "resources/public/js/compiled/dev"
                         :optimizations :none
                         :main taxi.dev
                         :asset-path "js/compiled/dev"
                         :source-map true
                         :source-map-timestamp true
                         :cache-analysis true
                         :foreign-libs [{:file "lib/diffusion.js"
                                         :provides ["diffusion"]}]
                         }}
             {:id "min"
              :source-paths ["src/cljs"]
              :compiler {:output-to "resources/public/js/compiled/taxi.js"
                         :output-dir "resources/public/js/compiled/release"
                         :main taxi.core
                         :optimizations :simple
                         :pretty-print false
                         :foreign-libs [{:file "lib/diffusion.js"
                                         :provides ["diffusion"]}]
                         }}]}

  :figwheel {:http-server-root "public" ;; default and assumes "resources"
             :server-port 3449 ;; default
             :css-dirs ["resources/public/css"] ;; watch and update CSS
             :ring-handler server.core/handler
             }

  :ring {:handler server.core/handler
         :war-exclusions [#"\.DS_Store$", #"public/js/compiled/dev.*$", #"public/js/compiled/release.*$"]}

  :aliases {"release-build" ["do" "clean" ["cljsbuild" "once" "min"] ["ring" "uberwar"]]
            "dev-build" ["do" "clean" ["cljsbuild" "once" "dev"]]})
