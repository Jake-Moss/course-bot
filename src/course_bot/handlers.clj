(ns course-bot.handlers
  (:require [clojure.string :as str]
            [discljord.formatting :as d-format]
            [discljord.messaging :as d-rest]
            [slash.command :as cmd]
            [slash.command.structure :as scs]
            [slash.response :as rsp]
            [course-bot.state :as state]
            [course-bot.bar :refer [score-graph]]))

;;; fun
(cmd/defhandler reverse-input
  ["reverse"]
  _
  [input words]
  (rsp/channel-message
   {:content (if words
               (->> #"\s+" (str/split input) str/reverse (str/join " "))
               (str/reverse input))}))

(cmd/defhandler mock
  ["mock"]
  _
  [input]
  (rsp/channel-message
   {:content (->> input
                  (str/lower-case)
                  (map #(cond-> % (rand-nth [true false]) Character/toUpperCase))
                  str/join)}))

;;; course
(cmd/defhandler course
  ["register"]
  {{{user-id :id} :user} :member} ; Using the interaction binding to get the user who ran the comma
  [input]

  (defn update-or-assoc [map value]
    (update map value (fnil inc 0)))

  (let [input (str/upper-case input)]
    (if (re-matches #"[A-Z]{4}\d{4}" input)
      (do
        (when-not (get @state/course-map input)
          (d-rest/create-guild-role! (:rest @state/state) state/guild-id :name input)
          (d-rest/add-guild-member-role! (:rest @state/state) state/guild-id user-id "1001828872108642464"))
        (rsp/channel-message
         {:content (str "Registered " input " to map, now has count "
                        (get (swap! state/course-map update-or-assoc input) input))}))
      (-> (rsp/channel-message
           {:content (str input " is not a valid course code.")})
          rsp/ephemeral))))

(cmd/defhandler course-autocomplete
  ["register"]
  {{:keys [focused-option]} :data}
  [input]
  (let [input (str/upper-case input)]
    (rsp/autocomplete-result
     (->> (keys @state/course-map)
          (filter #(str/starts-with? % input))
          (#(conj %1 input)) ;; Add user input as first element
          (map #(array-map :name %1, :value %1))
          (take 25)
          (into [])))))

;;; sudo
(cmd/defhandler reset
  ["reset"]
  _
  _
  (reset! state/course-map {})
  (-> (rsp/channel-message
       {:content "Reset all course interests."})
      rsp/ephemeral))

(defn filter-vals
  [pred m]
  (into {} (filter
            (fn [[k v]] (pred v))
            m)))

(cmd/defhandler clean
  ["clean"]
  _
  [threshold]
  (swap! state/course-map (partial filter-vals #(not (< % threshold))))
  (-> (rsp/channel-message
       {:content (str "Removed all courses with less than " threshold " interested.")})
      rsp/ephemeral))

(cmd/defhandler set-interest
  ["set-interest"]
  _
  [course n]
  (swap! state/course-map (fn [old] (assoc old course n)))
  (-> (rsp/channel-message
       {:content (str "Updated interest of " course " to " n ".")})
      rsp/ephemeral))

(cmd/defhandler dump
  ["dump"]
  _
  _
  (-> (rsp/channel-message
       {:content (str @state/course-map)})
      rsp/ephemeral))

(cmd/defhandler chart
  ["chart"]
  _
  _
  (rsp/channel-message {:content (d-format/code-block (score-graph @state/course-map))}))

(cmd/defhandler ping
  ["ping"]
  something
  _
  (println something)
  (-> (rsp/channel-message {:content "pong"})
      rsp/ephemeral))


;;; unknown
(cmd/defhandler unknown
  [unknown] ; Placeholders can be used in paths too
  {{{user-id :id} :user} :member} ; Using the interaction binding to get the user who ran the command
  _ ; no options
  (-> (rsp/channel-message {:content (str "I don't know the command `" unknown "`, <@" user-id ">.")})
      rsp/ephemeral))
