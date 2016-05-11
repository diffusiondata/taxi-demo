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
