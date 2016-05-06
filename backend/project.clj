(defproject taxi-controller "0.1.0-SNAPSHOT"
  :description "Taxi demo controller"
  :url "http://github.com/pushtechnology/"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :repositories {"diffusion" "http://download.pushtechnology.com/maven/"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/core.async "0.2.374"]
                 [com.pushtechnology.diffusion/diffusion-client "5.7.1"]]

  :source-paths ["src/clj"]
  :resource-paths ["src/resources"]

  :main taxi.core
  :aot :all

  :aliases {"release-build" ["do" "clean" ["uberjar"]]})
