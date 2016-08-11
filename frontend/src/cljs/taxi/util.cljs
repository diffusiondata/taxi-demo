
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

(ns taxi.util)

(defn float-to-string [p f]
  (if f (str (.toFixed f p)) (str "0." (repeat p "0"))))

(defn location-to-string [l]
  (if l (str "[" (.toFixed (l 0) 1) " " (.toFixed (l 1) 1) "]") "-"))

(defn direction-to-string [d]
  (if d (name d) "-"))

(defn money-to-string [d]
  (when d (str "£" (.toFixed d 2))))

(def ^:private names ["matt", "phil", "lee", "Tom", "Matt", "Phil", "Lee", "CaptainAwesome"])

(defn- generate-suffix []
  (.toLowerCase (.toString (js/Math.round (* 1000 (js/Math.random))) 16)))

(defn new-name
  "Randomly geneate a new human readable name."
  []

  (str (rand-nth names) (generate-suffix)))
