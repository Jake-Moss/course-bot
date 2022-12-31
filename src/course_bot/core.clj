(ns course-bot.core
  (:require [clojure.core.async :as async]

            [discljord.messaging :as d-rest]
            [discljord.connections :as d-conn]
            [discljord.events :as d-event]
            [discljord.permissions :as d-perms]

            [slash.core :refer [route-interaction]]
            [slash.gateway :refer [gateway-defaults wrap-response-return]]

            [course-bot.state :as state]
            [course-bot.paths :as path]
            [course-bot.commands :refer [commands]]
            [clojure.edn :as edn]))


(defn start-bot! [token intents]
  (let [event-channel (async/chan 100)
        gateway-connection (d-conn/connect-bot! token event-channel :intents intents)
        rest-connection (d-rest/start-connection! token)
        event-handler (-> route-interaction
                          (partial (assoc gateway-defaults
                                          :application-command path/command-paths
                                          :application-command-autocomplete path/autocomplete-paths))
                          (wrap-response-return (fn [id token {:keys [type data]}]
                                                  (d-rest/create-interaction-response!
                                                   rest-connection id token type :data data))))]
    {:events  event-channel
     :gateway gateway-connection
     :rest    rest-connection
     :handler event-handler}))

(defn stop-bot! [{:keys [rest gateway events] :as _state}]
  (d-rest/stop-connection! rest)
  (d-conn/disconnect-bot! gateway)
  (async/close! events))


(defn -main [& args]
  (reset! state/state (start-bot! (:token state/config) #{}))
  (reset! state/bot-id (:id @(d-rest/get-current-user! (:rest @state/state))))
  (reset! state/course-map (edn/read-string (slurp (:save-filename state/config))))
  (reset! state/charts (edn/read-string (slurp "charts.edn")))
  (try
    (d-rest/bulk-overwrite-guild-application-commands! (:rest @state/state) state/app-id state/guild-id commands)
    (d-event/message-pump! (:events @state/state)
                           (partial d-event/dispatch-handlers
                                    {:interaction-create [#((:handler @state/state) %2)]}))
    (finally
      (stop-bot! @state/state))))

(comment
 @state/course-map
 @state/state

 @(d-rest/create-message! (:rest @state/state) "938313279250530394" :content "sheesh")
 @(d-rest/create-guild-role! (:rest @state/state) state/guild-id :name "a-cool-role-for-cool-people")
 @(d-rest/create-guild-channel! (:rest @state/state) state/guild-id "neat-things" :type 4)
 @(d-rest/create-guild-channel! (:rest @state/state) state/guild-id "neat-text-chat" :type 0 :parent-id "1001819269400625184"
                                :permission-overwrites
                                [{:id "1001828872108642464" :type :role :allow (:view-channel d-perms/permissions-bit)}
                                 {:id state/guild-id :type :role :deny (:view-channel d-perms/permissions-bit)}])
 @(d-rest/add-guild-member-role! (:rest @state/state) state/guild-id "312446652570927106" "1001828872108642464")

 (def bot (future (-main)))
 (future-cancel bot)
 @(d-rest/bulk-overwrite-guild-application-commands! (:rest @state/state) state/app-id state/guild-id commands)
 )
