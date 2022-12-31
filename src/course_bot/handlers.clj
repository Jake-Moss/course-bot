(ns course-bot.handlers
  (:require [clojure.string :as str]
            [clojure.edn :as edn]

            [discljord.formatting :as d-format]
            [discljord.messaging :as d-rest]
            [discljord.permissions :as d-perms]

            [slash.command :as cmd]
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

(defn course? [course]
  (boolean (re-matches #"[A-Z]{4}\d{4}" course)))

(defn already-registered? [course user-id]
  (contains? (get-in @state/course-map [(subs course 0 4) :courses course :users]) user-id))

(defn register! [course user-id]
  (let [course (str/upper-case course)]
    (cond
      (not (course? course)) (-> {:content (str course " is not a valid course code")}
                                rsp/channel-message
                                rsp/ephemeral)
      (already-registered? course user-id) (-> {:content (str "You are already registered for " course)}
                                              rsp/channel-message
                                              rsp/ephemeral)
      :else (do
              (state/register-course! course user-id)
              (when @state/auto-save
                (state/save-debounced!))
              (-> {:content (str "Registered to " course)}
                  rsp/channel-message
                  rsp/ephemeral)))))

(defn deregister! [course user-id]
  (let [course (str/upper-case course)]
    (cond
      (not (course? course)) (-> {:content (str course " is not a valid course code")}
                                rsp/channel-message
                                rsp/ephemeral)
      (not (already-registered? course user-id)) (-> {:content (str "You are not registered for " course)}
                                                    rsp/channel-message
                                                    rsp/ephemeral)
      :else
        (do
          (state/deregister-course! course user-id)
          (when @state/auto-save
            (state/save-debounced!))
          (-> (rsp/channel-message {:content (str "Deregistered from " course)})
               rsp/ephemeral)))))

;;; course
(cmd/defhandler register
  ["register"]
  {{{user-id :id} :user} :member} ; Using the interaction binding to get the user who ran the command
  [input]
  (let [message (register! input user-id)]
    (state/graph-debounced!)
    message))


(cmd/defhandler register-autocomplete
  ["register"]
  _
  [input]
  (let [input (str/upper-case input)]
    (rsp/autocomplete-result
     (->> @state/course-map
          (#(map :courses (vals %)))
          (apply merge)
          (take 24) ;; 25 is discord autocomplete limit
          (sort-by (comp :count val) >)
          keys
          (filter #(str/starts-with? % input))
          (#(if (empty? input) % (conj % input))) ;; Add user input as first element
          (map #(array-map :name %1, :value %1))
          (into [])))))

(cmd/defhandler deregister
  ["deregister"]
  {{{user-id :id} :user} :member} ; Using the interaction binding to get the user who ran the command
  [input]
  (let [message (deregister! input user-id)]
    (state/graph-debounced!)
    message))

(cmd/defhandler deregister-autocomplete
  ["deregister"]
  {{{user-id :id} :user} :member}
  [input]
  (let [input (str/upper-case input)]
    (rsp/autocomplete-result
     (->> @state/course-map
          (#(map :courses (vals %)))
          (apply merge)
          (filter #(str/starts-with? (first %) input))
          (take 24) ;; 25 is discord autocomplete limit
          (keep #(when (contains? (get (second %) :users #{}) user-id) (first %)))
          (#(if (empty? input) % (conj % input))) ;; Add user input as first element
          (map #(array-map :name %1, :value %1))
          (into [])))))


;;; sudo
(cmd/defhandler reset
  ["reset"]
  _
  _
  (reset! state/course-map {})
  (-> {:content "Reset all course interests"}
      rsp/channel-message
      rsp/ephemeral))

(defn remove-below-threshold
  [nm threshold]
  (into {}
        (for [[k v] nm
                 :let [new (for [[course {c :count :as m}] (:courses v)
                                 :when (>= c threshold)]
                             [course m])]
                 :when (seq new)]
             (->> new
                  (into {})
                  (vector k)))))

(cmd/defhandler clean
  ["clean"]
  _
  [threshold]
  (swap! state/course-map remove-below-threshold threshold)
  (-> {:content (str "Removed all courses with less than " threshold " interested")}
      rsp/channel-message
      rsp/ephemeral))

(cmd/defhandler set-interest
  ["set-interest"]
  _
  [course n]
  (swap! state/course-map (fn [old] (assoc-in old [(subs course 0 4) :courses course :count] (int n))))
  (-> (rsp/channel-message
       {:content (str "Updated interest of " course " to " (int n))})
      rsp/ephemeral))

(cmd/defhandler force-register
  ["force-register"]
  _
  [user course]
  (let [message (register! course user)]
    (state/graph-debounced!)
    message))

(cmd/defhandler force-deregister
  ["force-deregister"]
  _
  [user course]
  (let [message (deregister! course user)]
    (state/graph-debounced!)
    message))

(defn create-category! [name]
  (d-rest/create-guild-channel! (:rest @state/state) state/guild-id name :type 4))

(defn create-role! [name]
  (d-rest/create-guild-role! (:rest @state/state) state/guild-id :name name))

(defn create-channel! [name parent-id viewable-by not-viewable-by]
  (d-rest/create-guild-channel!
   (:rest @state/state) state/guild-id
   name :type 0 :parent-id parent-id
   :permission-overwrites
   (into []
         (concat (for [role viewable-by]
                   {:id role :type :role
                    :allow (:view-channel d-perms/permissions-bit)})
                 (for [role not-viewable-by]
                   {:id role :type :role
                    :deny (:view-channel d-perms/permissions-bit)})))))

(defn create-roles-and-channels! [course-map]
  (->>
   (for [[prefix {courses :courses parent-id :parent-id}] course-map
         :let [parent-id (or parent-id (:id @(create-category! prefix)))]]
     [prefix {:parent-id parent-id
              :courses (->>
                        (for [[k v] courses
                              :let [role-id (get v :role-id (:id @(create-role! k)))
                                    channel-id (get v :channel-id
                                                    (:id @(create-channel!
                                                           k parent-id
                                                           (conj @state/additional-roles role-id)
                                                           [state/guild-id])))]]
                          [k (assoc v :role-id role-id :channel-id channel-id)])
                        (into {}))}])
   (into {})))

(defn remove-roles-and-channels! [course-map]
  (->>
   (for [[prefix {courses :courses parent-id :parent-id}] course-map]
     (do
       (when parent-id
         @(d-rest/delete-channel! (:rest @state/state) parent-id))
       [prefix {:courses (->>
                          (for [[k v] courses]
                            (do
                              (when-let [channel-id (:channel-id v)]
                                @(d-rest/delete-channel! (:rest @state/state) channel-id))
                              (when-let [role-id (:role-id v)]
                                @(d-rest/delete-guild-role! (:rest @state/state) state/guild-id role-id))
                              [k (dissoc v :role-id :channel-id)]))
                          (into {}))}]))
   (into {})))

;; (create-roles-and-channels! @state/course-map)
;; (remove-roles-and-channels! @state/course-map)

(cmd/defhandler create-roles-and-channels
  ["create-roles-and-channels"]
  _
  _
  (future (swap! state/course-map create-roles-and-channels!))
  (->> {:content "Creating roles and channels"}
      rsp/channel-message
      rsp/ephemeral))

(cmd/defhandler remove-roles-and-channels
  ["remove-roles-and-channels"]
  _
  _
  (future (swap! state/course-map remove-roles-and-channels!))
  (->> {:content "Removing roles and channels"}
      rsp/channel-message
      rsp/ephemeral))

(cmd/defhandler additional-roles
    ["additional-roles"]
    _
    [role]
  (swap! state/additional-roles conj role)
  (->> {:content (str "Added " role " to the allowed list")}
      rsp/channel-message
      rsp/ephemeral))

(cmd/defhandler remove-additional-roles
    ["remove-additional-roles"]
    _
    [role]
  (swap! state/additional-roles disj role)
  (->> {:content (str "Removed " role " from the allowed list")}
      rsp/channel-message
      rsp/ephemeral))

(cmd/defhandler dump
  ["dump"]
  _
  _
  (->> @state/course-map
      pr-str
      d-format/code-block
      (assoc {} :content)
      rsp/channel-message
      rsp/ephemeral))

(cmd/defhandler chart
  ["chart"]
  {channel-id :channel-id}
  _
  (swap! state/charts conj
         (let [{id :id
                channel-id :channel-id}
               @(d-rest/create-message! (:rest @state/state)
                                        channel-id
                                        :content (d-format/code-block
                                                  (score-graph @state/course-map)))]
           {:id id :channel-id channel-id}))
  (spit "charts.edn" (pr-str @state/charts))
  (->> {:content "Created the graph.\n\nYou can use `/sudo update` to force an update of all charts"}
       rsp/channel-message
       rsp/ephemeral))

(cmd/defhandler update-charts
  ["update-charts"]
  _
  _
  (state/update-charts!)
  (->> {:content "Forcibly updated all charts"}
       rsp/channel-message
       rsp/ephemeral))

(cmd/defhandler auto-enroll
  ["auto-enroll"]
  _
  [value]
  (->> (if (nil? value)
         (str "auto-enroll is currently " @state/auto-enroll)
         (do
           (reset! state/auto-enroll value)
           (str "Set auto-enroll to " value)))
       (assoc {} :content)
       rsp/channel-message
       rsp/ephemeral))

(cmd/defhandler auto-save
  ["auto-save"]
  _
  [value]
  (->> (if (nil? value)
         (str "auto-save is currently " @state/auto-save)
         (do
           (reset! state/auto-save value)
           (str "Set auto-save to " value)))
       (assoc {} :content)
       rsp/channel-message
       rsp/ephemeral))

(cmd/defhandler override
  ["override"]
  _
  [map]
  (->> (if (nil? map)
         (str "Override the internal course map with the supplied one. Use clojure.edn format.\n"
              "Example:\n"
              (d-format/code-block
               (str "{\"CSSE\" {:courses {\"CSSE1001\" {:count 23}}}, \"MATH\" {:courses {\"MATH1071\""
                    " {:count 35 :users #{\"938362352544411668\"}}}")))
         (do
           (reset! state/course-map (edn/read-string map))
           (when @state/auto-save
             (state/save-debounced!))
           "Overwrote the internal course map. Use `/sudo dump` to view it"))
       (assoc {} :content)
       rsp/channel-message
       rsp/ephemeral))

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
  (-> (rsp/channel-message {:content (str "I don't know the command `" unknown "`, <@" user-id ">")})
      rsp/ephemeral))


