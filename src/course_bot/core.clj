(ns course-bot.core
  (:require [clojure.core.async :as async]

            [discljord.messaging :as d-rest]
            [discljord.connections :as d-conn]
            [discljord.events :as d-event]

            [slash.core :refer [route-interaction]]
            [slash.gateway :refer [gateway-defaults wrap-response-return]]

            ;; [com.brunobonacci.mulog :as u]

            [course-bot.state :as state]
            [course-bot.paths :as path]
            [course-bot.commands :refer [commands]]))

;; (defonce logger (u/start-publisher! {:type :multi
;;                                      :publishers [{:type :console}
;;                                                   {:type :simple-file
;;                                                    :filename "./logs/mulog.log"}]}))


(defn start-bot! [token & intents]
  (let [event-channel (async/chan 100)
        gateway-connection (d-conn/connect-bot! token event-channel :intents #{})
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
  (reset! state/state (start-bot! (:token state/config) :guild-messages))
  (reset! state/bot-id (:id @(d-rest/get-current-user! (:rest @state/state))))
  (reset! state/course-map {})
  ;; (reset! state/save-future (future
  ;;                             (loop []
  ;;                               (state/save state/config)
  ;;                               (Thread/sleep (* (:save-period state/config) 1000))
  ;;                               (recur))))
  (try
    (d-rest/bulk-overwrite-guild-application-commands! (:rest @state/state) state/app-id state/guild-id commands)
    (d-event/message-pump! (:events @state/state)
                           (partial d-event/dispatch-handlers
                                    {:interaction-create [#((:handler @state/state) %2)]}))
    (finally
      (stop-bot! @state/state)
      (future-cancel @state/save-future))))
