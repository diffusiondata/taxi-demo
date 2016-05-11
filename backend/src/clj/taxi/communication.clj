(ns taxi.communication
  (:gen-class)
  (:use
   [clojure.core.async :only [>! <! <!! close! go go-loop chan timeout]])
  (:require
   [clojure.edn :as edn])
  (:import com.pushtechnology.diffusion.client.Diffusion
           com.pushtechnology.diffusion.client.session.Session
           com.pushtechnology.diffusion.client.session.Session$Listener
           com.pushtechnology.diffusion.client.session.Session$State
           com.pushtechnology.diffusion.client.features.control.topics.MessagingControl
           com.pushtechnology.diffusion.client.features.control.topics.MessagingControl$MessageHandler
           com.pushtechnology.diffusion.client.features.control.topics.MessagingControl$SendCallback
           com.pushtechnology.diffusion.client.features.control.topics.TopicControl
           com.pushtechnology.diffusion.client.features.control.topics.TopicControl$AddCallback
           com.pushtechnology.diffusion.client.features.control.topics.TopicControl$RemoveCallback
           com.pushtechnology.diffusion.client.features.Topics
           com.pushtechnology.diffusion.client.features.Topics$CompletionCallback
           com.pushtechnology.diffusion.client.features.control.topics.TopicUpdateControl
           com.pushtechnology.diffusion.client.features.control.topics.TopicUpdateControl$Updater$UpdateCallback
           com.pushtechnology.diffusion.client.features.Topics$TopicStream
           com.pushtechnology.diffusion.client.topics.details.TopicType
           com.pushtechnology.diffusion.client.callbacks.TopicTreeHandler

           java.lang.Thread
           java.security.KeyStore
           javax.net.ssl.SSLContext
           javax.net.ssl.TrustManagerFactory))


(defn- parse-content
  "Parse Diffusion Content as a single EDN value."
  [content]

  (edn/read-string (.asString content)))

(defn- to-content
  "Create a new Content containing the value passed in, serialised as an EDN value."
  [value]
  (.newContent (Diffusion/content) (pr-str value)))

(defn- resource-as-stream [resource]
  (.getResourceAsStream
   (.getContextClassLoader (Thread/currentThread))
   resource))

(defn- create-trust-store [stream]
  (let [new-store (KeyStore/getInstance (KeyStore/getDefaultType))]
    (.load new-store stream nil)
    new-store))

(defn- create-trust-manager-factory [trust-store]
  (let [factory (TrustManagerFactory/getInstance (TrustManagerFactory/getDefaultAlgorithm))]
    (.init factory trust-store)
    factory))

(defn- create-ssl-context [trust-manager-factory]
  (let [context (SSLContext/getInstance "SSL")]
    (.init context nil (.getTrustManagers trust-manager-factory), nil)
    context))

(defn- create-ssl-context-from-trust-store-resource
  "Returns an SSL context created from a trust store resource."
  [resource]
  (create-ssl-context
   (create-trust-manager-factory
    (create-trust-store
     (resource-as-stream resource)))))

(defn send-message
  "Send a message to a session. The message is sent as an EDN value. The result of the callback will be placed on the channel.
  These results will be identified by the :send-message keyword."
  [sending-session jackie recipient-session-id topic-path message]
    (println "Sending message" topic-path "with" message)
    (-> sending-session
      (.feature TopicControl)
      (.send
       recipient-session-id
       topic-path
       (to-content message)
       (reify MessagingControl$SendCallback
         (onComplete
          [this]
          (go (>! jackie {:send-message {:result :success :topic-path topic-path}}))
          nil)

         (onDiscard
          [this]
          (go (>! jackie {:send-message {:result :discarded}}))
          nil)
         ))))

(defn add-topic
  "Add a new stateful topic that publishes an EDN value. The result of the callback will be placed on the channel.
  These results will be identified by the :topic-add keyword"
  [session jackie topic-path initial-value]
  (println "Adding topic" topic-path "with" initial-value)
  (-> session
      (.feature TopicControl)
      (.addTopic
       topic-path
       TopicType/SINGLE_VALUE
       (to-content initial-value)
       (reify TopicControl$AddCallback
         (onTopicAdded
          [this topic-path]
          (go (>! jackie {:topic-add {:result :success :topic-path topic-path}}))
          nil)

         (onTopicAddFailed
          [this topic-path reason]
          (go (>! jackie {:topic-add {:result :failed :topic-path topic-path :reason reason}}))
          nil)

         (onDiscard
          [this]
          (go (>! jackie {:topic-add {:result :discarded :topic-path topic-path}}))
          nil)
         ))))

(defn update-topic
  "Update an existing topic with a new EDN value. The result of the callback will be placed on the channel.
  These results will be identified by the :topic-update keyword"
  [session jackie topic-path new-value]
  (println "Updating topic" topic-path "with" new-value)
  (-> session
      (.feature TopicUpdateControl)
      (.updater)
      (.update
       topic-path
       (to-content new-value)
       (reify TopicUpdateControl$Updater$UpdateCallback
         (onSuccess
          [this]
          (go (>! jackie {:topic-update {:result :success :topic-path topic-path}}))
          nil)

         (onError
          [this reason]
          (go (>! jackie {:topic-update {:result :error :topic-path topic-path :reason reason}}))
          nil)
         ))))

(defn remove-topics
  "Remove topics matching a selector from the server. The result of the callback will be placed on the channel.
  These results will be identified by the :topics-remove keyword."
  [session jackie topic-selector]
  (-> session
      (.feature TopicControl)
      (.removeTopics
       topic-selector
       (reify TopicControl$RemoveCallback
         (onTopicsRemoved
          [this]
          (go (>! jackie {:topics-remove {:result :success :topic-selector topic-selector}})))

         (onDiscard
          [this]
          (go (>! jackie {:topics-remove {:result :discarded :topic-selector topic-selector}})))
         ))))

(defn register-message-handler
  "Register a message handler that puts events onto the channel passed in. The result of registering the handler
  and the messages will be placed on the channel.
  The result of registering the handler will be identified by the :message-handler-state keyword.
  The messages received by the handler will be identified by the :message keyword."
  [session jackie topic-path]

  (-> session
      (.feature MessagingControl)
      (.addMessageHandler
       topic-path
       (reify MessagingControl$MessageHandler

         (onActive
          [this topic-path registration]
          (go (>! jackie {:message-handler-state {:handler-state :active :topic-path topic-path}}))
          nil)

         (onClose
          [this topic-path]
          (go (>! jackie {:message-handler-state {:handler-state :closed :topic-path topic-path}}))
          nil)

         (onMessage
          [this session-id topic-path content context]
          (go (>! jackie {:message {:session-id session-id :topic-path topic-path :content (parse-content content)}}))
          nil))

       (into-array String []))
  ))

(defn subscribe
  "Subscribe the session to a selector, place the result onto the channel passed in.
  The result will be identified by the :subscribe-request keyword."
  [session jackie topic-selector]

  (-> session
      (.feature Topics)
      (.subscribe
       topic-selector
       (reify Topics$CompletionCallback

         (onComplete [this]
                     (go (>! jackie {:subscribe-request {:result :completed}}))
                     nil)

         (onDiscard [this]
                    (go (>! jackie {:subscribe-request {:result :discarded}}))
                    nil)

         ))))

(defn- add-topics-stream
  "Add a topic stream to the session that puts events onto the channel passed in. The topic stream is registered as the
  fallback topic stream.
  The stream events be identified by the keywords :subscription, :update, :unsubscription,
  :topic-stream-close and :topic-stream-error."
  [session jackie]

  (-> session
      (.feature Topics)
      (.addFallbackTopicStream
       (reify Topics$TopicStream

         (onSubscription [this topic-path topic-details]
                         (go (>! jackie {:subscription {:topic-path topic-path}}))
                         nil)

         (onTopicUpdate [this topic-path content update-context]
                        (go (>! jackie {:update {:topic-path topic-path :content (parse-content content)}}))
                         nil)

         (onUnsubscription [this topic-path reason]
                           (go (>! jackie {:unsubscription {:topic-path topic-path :reason reason}}))
                           nil)

         (onClose [this]
                  (go (>! jackie {:topic-stream-close {}}))
                  nil)

         (onError [this reason]
                  (go (>! jackie {:topic-stream-error {:error reason}}))
                  nil)

         ))))

(defn remove-topics-with-session
  "Register topics to remove when the session closes. The result is placed on the channel passed in.
  The result is identified by the :remove-topics-with-session keyword."
  [session jackie topic-path]
  (-> session
      (.feature TopicControl)
      (.removeTopicsWithSession
       topic-path
       (reify TopicTreeHandler
         (onRegistered
          [this topic-path registration]
          (go (>! jackie {:remove-topics-with-session {:topic-path topic-path :registration registration}}))
          nil)

         (onError
          [this topic-path reason]
          (go (>! jackie {:remove-topics-with-session {:topic-path topic-path :error reason}}))
          nil)

         (onClose
          [this topic-path]
          (go (>! jackie {:remove-topics-with-session {:topic-path topic-path}}))
          nil)
         ))))

(defn create-session-factory
  "Return a session factory. The session factory created will create sessions with the same principal and password.
  The session that it creates will put state changes on the channel passed in. The state changes will be identified
  by the keyword :state-change. When the session becomes active it will add the fallback topics stream to receive
  any updates to topics it subscribes to."
  [jackie principal password]
  (let [listener (reify Session$Listener
                     (onSessionStateChanged
                      [this session old-state new-state]
                      (when (and
                             (= old-state
                                Session$State/CONNECTING)
                             (= new-state
                                Session$State/CONNECTED_ACTIVE))
                        (add-topics-stream session jackie))
                      (go (>! jackie {:state-change {:session session :old-state old-state :new-state new-state}}))
                      nil))]

      (-> (Diffusion/sessions)
          (.principal principal)
          (.password password)
          (.sslContext (create-ssl-context-from-trust-store-resource "reapptTruststore.jks"))
          (.listener listener))))
