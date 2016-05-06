(ns taxi.util)

(defn float-to-string [p f]
  (if f (str (.toFixed f p)) (str "0." (repeat p "0"))))

(defn location-to-string [l]
  (if l (str "[" (.toFixed (l 0) 1) " " (.toFixed (l 1) 1) "]") "-"))

(defn direction-to-string [d]
  (if d (name d) "-"))

(defn money-to-string [d]
  (when d (str "£" (.toFixed d 2))))
