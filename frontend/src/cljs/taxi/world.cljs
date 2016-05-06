(ns taxi.world)

(def grid-width 8)

(def grid-height 6)

(def update-speed 1000)

(def taxi-speed 0.25)

(def frames-per-second 30)

(def frame-time (/ 1000 frames-per-second))

(def block-colour "rgb(120, 90 ,0)")

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
