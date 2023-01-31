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

(defn enroll! [course user-id guild-id]
  (when-let [role-id (get-in @state/course-map [(subs course 0 4) :courses course :role-id])]
    @(d-rest/add-guild-member-role! (:rest @state/state) guild-id user-id role-id)))

(defn unenroll! [course user-id guild-id]
  (when-let [role-id (get-in @state/course-map [(subs course 0 4) :courses course :role-id])]
    @(d-rest/remove-guild-member-role! (:rest @state/state) guild-id user-id role-id)))

(defn enroll-all! [course-map guild-id]
  (doseq [[_ {courses :courses}] course-map]
    (doseq [[course {users :users}] courses]
      (doseq [user-id users]
        (enroll! course user-id guild-id)))))

(defn unenroll-all! [course-map guild-id]
  (doseq [[_ {courses :courses}] course-map]
    (doseq [[course {users :users}] courses]
      (doseq [user-id users]
        (unenroll! course user-id guild-id)))))

(defn register! [course user-id guild-id]
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
              (when (:auto-enroll @state/config)
                (enroll! course user-id guild-id))
              (-> {:content (str "Registered to " course)}
                  rsp/channel-message
                  rsp/ephemeral)))))

(defn deregister! [course user-id guild-id]
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
          (when (:auto-enroll @state/config)
            (unenroll! course user-id guild-id))
          (-> (rsp/channel-message {:content (str "Deregistered from " course)})
               rsp/ephemeral)))))

;;; course
(cmd/defhandler register
  ["register"]
  {{{user-id :id} :user} :member guild-id :guild-id} ; Using the interaction binding to get the user who ran the command
  [input]
  (let [message (register! input user-id guild-id)]
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
  {{{user-id :id} :user} :member guild-id :guild-id} ; Using the interaction binding to get the user who ran the command
  [input]
  (let [message (deregister! input user-id guild-id)]
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
      rsp/channel-message))

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
      rsp/channel-message))

(cmd/defhandler set-interest
  ["set-interest"]
  a
  [course n]
  (let [n (int n)]
    (swap! state/course-map (fn [old] (if (= n 0)
                                        (update-in old [(subs course 0 4) :courses] dissoc course)
                                        (assoc-in old [(subs course 0 4) :courses course :count] n))))
    (-> {:content (if (= n 0)
                    (str "Removed " course)
                    (str "Updated interest of " course " to " n))}
        rsp/channel-message)))

(cmd/defhandler force-register
  ["force-register"]
  {guild-id :guild-id}
  [user course]
  (let [message (register! course user guild-id)]
    (state/graph-debounced!)
    message))

(cmd/defhandler force-deregister
  ["force-deregister"]
  {guild-id :guild-id}
  [user course]
  (let [message (deregister! course user guild-id)]
    (state/graph-debounced!)
    message))

(cmd/defhandler enroll-all
  ["enroll-all"]
  {guild-id :guild-id}
  _
  (future (enroll-all! @state/course-map guild-id))
  ;; (enroll-all! @state/course-map guild-id)
  (-> {:content "Enrolling all those registered"}
      rsp/channel-message))

(cmd/defhandler unenroll-all
  ["unenroll-all"]
  {guild-id :guild-id}
  _
  (future (unenroll-all! @state/course-map guild-id))
  (-> {:content "Unenrolling all those registered"}
      rsp/channel-message))



(defn create-category! [name guild-id]
  (d-rest/create-guild-channel! (:rest @state/state) guild-id name :type 4))

(defn create-role! [name guild-id]
  (d-rest/create-guild-role! (:rest @state/state) guild-id :name name))

(defn create-channel! [name parent-id viewable-by not-viewable-by guild-id]
  (d-rest/create-guild-channel!
   (:rest @state/state) guild-id
   name :type 0 :parent-id parent-id
   :permission-overwrites
   (into []
         (concat (for [role viewable-by]
                   {:id role :type :role
                    :allow (:view-channel d-perms/permissions-bit)})
                 (for [role not-viewable-by]
                   {:id role :type :role
                    :deny (:view-channel d-perms/permissions-bit)})
                 (list {:id @state/bot-id :type :member :allow (:view-channel d-perms/permissions-bit)})))))

(defn create-roles-and-channels! [course-map guild-id]
  (->>
   (for [[prefix {courses :courses parent-id :parent-id}] course-map
         :let [parent-id (or parent-id (:id @(create-category! prefix guild-id)))]]
     [prefix {:parent-id parent-id
              :courses (->>
                        (for [[k v] courses
                              :let [role-id (get v :role-id (:id @(create-role! k guild-id)))
                                    channel-id (get v :channel-id
                                                    (:id @(create-channel!
                                                           k parent-id
                                                           (conj (:additional-roles @state/config) role-id)
                                                           [guild-id]
                                                           guild-id)))]]
                          [k (assoc v :role-id role-id :channel-id channel-id)])
                        (into {}))}])
   (into {})))

(defn remove-roles-and-channels! [course-map guild-id]
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
                                @(d-rest/delete-guild-role! (:rest @state/state) guild-id role-id))
                              [k (dissoc v :role-id :channel-id)]))
                          (into {}))}]))
   (into {})))


(cmd/defhandler create-roles-and-channels
  ["create-roles-and-channels"]
  {guild-id :guild-id}
  _
  (future (swap! state/course-map create-roles-and-channels! guild-id))
  (->> {:content "Creating roles and channels"}
      rsp/channel-message))

(cmd/defhandler remove-roles-and-channels
  ["remove-roles-and-channels"]
  {guild-id :guild-id}
  _
  (future (swap! state/course-map remove-roles-and-channels! guild-id))
  (->> {:content "Removing roles and channels"}
      rsp/channel-message))

(cmd/defhandler additional-roles
  ["additional-roles"]
  _
  [role]
  (if role
    (do
      (swap! state/config update :additional-roles conj role)
      (->> {:content (str "Added " role " to the allowed list")}
           rsp/channel-message))
    (->> {:content (str "Allowed list: " (:additional-roles @state/config))}
           rsp/channel-message)))

(cmd/defhandler remove-additional-roles
    ["remove-additional-roles"]
    _
    [role]
  (swap! state/config update :additional-roles disj role)
  (->> {:content (str "Removed " role " from the allowed list")}
      rsp/channel-message))

(cmd/defhandler dump
  ["dump"]
  _
  _
  (->> @state/course-map
      pr-str
      d-format/code-block
      (assoc {} :content)
      rsp/channel-message))

(cmd/defhandler chart
  ["chart"]
  {channel-id :channel-id}
  _
  (swap! state/config update :charts conj
         (let [{id :id
                channel-id :channel-id}
               @(d-rest/create-message! (:rest @state/state)
                                        channel-id
                                        :content (d-format/code-block
                                                  (score-graph @state/course-map)))]
           {:id id :channel-id channel-id}))
  (->> {:content "Created the graph.\n\nYou can use `/sudo update-charts` to force an update of all charts"}
       rsp/channel-message
       rsp/ephemeral))

(cmd/defhandler update-charts
  ["update-charts"]
  _
  _
  (state/update-charts!)
  (->> {:content "Forcibly updated all charts"}
       rsp/channel-message))

(cmd/defhandler auto-enroll
  ["auto-enroll"]
  _
  [value]
  (->> (if (nil? value)
         (str "auto-enroll is currently " (:auto-enroll @state/config))
         (do
           (swap! state/config assoc :auto-enroll value)
           (str "Set auto-enroll to " value)))
       (assoc {} :content)
       rsp/channel-message))

(cmd/defhandler auto-save
  ["auto-save"]
  _
  [value]
  (->> (if (nil? value)
         (str "auto-save is currently " (:auto-save @state/config))
         (do
           (swap! state/config assoc :auto-save value)
           (str "Set auto-save to " value)))
       (assoc {} :content)
       rsp/channel-message))

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
           "Overwrote the internal course map. Use `/sudo dump` to view it"))
       (assoc {} :content)
       rsp/channel-message))

(cmd/defhandler ping
  ["ping"]
  something
  _
  (-> (rsp/channel-message {:content "pong"})
      rsp/ephemeral))

;;; unknown
(cmd/defhandler unknown
  [unknown] ; Placeholders can be used in paths too
  {{{user-id :id} :user} :member} ; Using the interaction binding to get the user who ran the command
  _ ; no options
  (-> (rsp/channel-message {:content (str "I don't know the command `" unknown "`, <@" user-id ">")})
      rsp/ephemeral))


