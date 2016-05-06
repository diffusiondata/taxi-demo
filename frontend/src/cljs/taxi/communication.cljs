(ns ^:figwheel-always taxi.communication
    "Simple core/async bindings to Diffusion."
    (:require [diffusion]
              [cljs.core.async :refer [put! close! chan]]
              [cljs.reader :as edn])
    (:require-macros [cljs.core.async.macros :refer [go]]))


(defn- fail
  [ch error & s]
  (close! ch)
  (apply error s))


(defn- to-event [p]
  "We marshall cljs objects as EDN"

  ; Unfortunately "" -> "\"\"", which causes addTopic to choke.
  ; addTopic may not like other quoting too.

  (pr-str p))

(defn- from-event [d]
  "We marshall cljs objects as EDN"
  (cljs.reader/read-string (js/String. d)))

(defn connect
  "Create a new Diffusion session using the given options.

  On success, put the connection result to `result-chan`.
  On error, close `result-chan`, and call the fn `error`.
  Returns nil."
  [result-chan error options]

  (let [result (js/diffusion.connect options)
        local-chan (chan)]
    (.then result
             (fn [session]
               (set! (.-onbeforeunload js/window)
                     (fn []
                       (js/console.log "CLOSING" session)
                       (.-close session)
                       nil))
               (put! result-chan session)
               (.on session
                    #js {
                         :disconnect #(fail result-chan error (.-sessionID session) "disconnected")
                         :reconnect  #(fail result-chan error (.-sessionID session) "reconnected")
                         :error      #(fail result-chan error (.-sessionID session) "ERROR" %&)
                         :close      #(fail result-chan error (.-sessionID session) "closed")
                         }))
           (fn [error]
             (close! local-chan)
             (fail result-chan error "session" "failed")))
    nil))


(defn remove-topics-with-session
  "Use `session` to register that the topics below `topic-branch` should
  be removed on error.

  Return a new channel to which an event will be written on success,
  or closed on error."
  [error session topic-branch]

  (let [out (chan)]

    (-> session
        .-topics
        (.removeWithSession topic-branch)
        (.then #(put! out %1)
               #(fail out error ".removeWithSession failed" %1)))

    out))


(defn remove-topics
  "Use `session` to remove the topics below `topic-branch`.

  Return a new channel to which an event will be written on success,
  or closed on error."

  [error session topic-branch]

  (let [out (chan)]

    (-> session
        .-topics
        (.remove topic-branch)
        (.then #(put! out %1)
               #(fail out error ".remove failed" %1)))

    out))


(defn add-topic
  "Use `session` to add a topic `topic-name` with optional initial
  `value`.

  Return a new channel to which an event (map with keys :topic and
  :created) will be written on success, or closed on error."
  [error session topic-name value]

  (println "adding topic" topic-name value)

  (let [out (chan)]

    (-> session
        .-topics
        (.add topic-name (to-event value))
        (.then #(put! out {:topic (.-topic %1) :created (.-added %1)})
              #(fail out error "add topic failed" %1)))

    out))


(defn- to-event [p]
  (pr-str p))

(defn- from-event [d]
  (edn/read-string (.toString d)))

(defn update-topic
  "Use `session` to update topic `topic-name` with `value`.

  Return a new channel to which an event will be written on success,
  or closed on error."
  [error session topic-name value]

  (let [out (chan)]

    (-> session
        .-topics
        (.update topic-name (to-event value))
        (.then
         #(put! out %&)
         #(fail out error "update topic failed" %&)))

    out))

(defn send-message
  ""
  [error session path value]

  (println "sending message on" path)

  (let [out (chan)]

    (-> session
        .-messages
        (.send path (to-event value))
        (.then
         #(put! out %&)
         #(fail out error "sending message failed" %&)))

    out))

(defn add-message-handler
  ""
  [error session path]

  (println "adding handler for" path)

  (let [jackie (chan)]

    (-> session
        .-messages
        (.addHandler path #js {:onMessage (fn [message])
                               :onActive (fn [unregister])
                               :onClose (fn [error])})
        (.then
         #(put! jackie %&)
         #(fail jackie error "handler registation failed" %&)))

    jackie))

(defn add-message-listener
  ""
  [error session path]

  (println "adding listener for" path)

  (let [jackie (chan)]

    (-> session
        .-messages
        (.listen path)
        (.on "message" #(put! jackie {:type :message :value (from-event (.-content %1)) :topic (.-path %1)})))

    jackie))

(defn subscribe
  "Use `session` to subscribe to updates for `selector`.

  Return a new channel to which updates will be published."

  [error session selector]

  (println "subscribing to" selector)

  (let [jackie (chan)]

    (-> session
        (.subscribe selector)
        (.on "update"      #(put! jackie {:type :update :value (from-event %1) :topic %2}))
        (.on "subscribe"   #(put! jackie {:type :subscribed :value (from-event %1) :topic %2}))
        (.on "unsubscribe" #(put! jackie {:type :unsubscribed :message (from-event %1) :topic %2}))
        )
    jackie))
