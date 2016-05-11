
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

(ns taxi.world)

(def grid-width 8)

(def grid-height 6)

(def taxi-update-period-ms 250)

(def taxi-update-period (/ taxi-update-period-ms 1000))

(def frames-per-second 30)

(def frame-time (/ 1000 frames-per-second))

(def grid-ratio 0.7)

(def road-ratio 3)

(def taxi-ratio 1.1)

(def grid-border 1)

(defonce images {:gherkin "url('images/gherkin.png')"
                 :castle "url('images/castle.png')"
                 :tower "url('images/tower.png')"
                 :left "url('images/left.png')"
                 :right "url('images/right.png')"
                 :up "url('images/up.png')"
                 :down "url('images/down.png')"})

(def locations [{:name "The Gherkin"
                 :image :gherkin
                 :x 1.0
                 :y 1.0
                 :colour "rgb(200, 0 ,0)"
                 :ratio 1.0}
                {:name "Heron Tower"
                 :image :tower
                 :x 3.0
                 :y 2.0
                 :colour "rgb(200, 0 ,0)"
                 :ratio 1.0}
                {:name "The Tower"
                 :image :castle
                 :x 2.0
                 :y 4.0
                 :colour "rgb(200, 0 ,0)"
                 :ratio 1.0}])
