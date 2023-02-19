(ns course-bot.handlers
  (:require [clojure.string :as str]
            [clojure.edn :as edn]
            [clojure.pprint :as pp]
            [clojure.java.io :as io]

            [discljord.formatting :as d-format]
            [discljord.messaging :as d-rest]
            [discljord.permissions :as d-perms]

            [slash.command :as cmd]
            [slash.response :as rsp]

            [course-bot.state :as state]
            [course-bot.scrapper :as scrapper]
            [course-bot.bar :refer [score-graph]]))

;;; fun
(cmd/defhandler reverse-input
  ["reverse-input"]
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
  (boolean (re-matches @state/course-regex course)))

(defn already-registered? [course user-id]
  (contains? (get-in @state/course-map [(subs course 0 4) :courses course :users]) user-id))

(defn get-course-codes [course-map]
  (apply concat
         (for [{courses :courses} (vals course-map)]
           (keys courses))))

(defn get-course [course course-map]
  (get-in course-map [(subs course 0 4) :courses course]))

(defn get-course-embed [course]
  (let [{embed :embed} (get @state/course-embeds course)]
    (if embed
      embed
      (let [embed (scrapper/yoink-details course)]
        (swap! state/course-embeds assoc course {:embed embed})
        embed))))

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

(defn enroll! [course user-id guild-id]
  (let [role-id (or (get-in @state/course-map [(subs course 0 4) :courses course :role-id])
                    (:id @(create-role! course guild-id)))]
    (swap! state/course-map assoc-in [(subs course 0 4) :courses course :role-id] role-id)
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


(defn send-course-embed! [course channel-id]
  (let [{id :id channel-id :channel-id} @(d-rest/create-message! (:rest @state/state) channel-id :embed (get-course-embed course))]
    (d-rest/pin-message! (:rest @state/state) channel-id id)
    {course {:id id :channel-id channel-id}}))

(defn send-course-embeds! [course-map]
  (let [embeds @state/course-embeds]
      (apply merge (for [[course {channel-id :channel-id}] (apply merge (map :courses (vals course-map)))
                         :when (and channel-id (not (get-in embeds [course :id])))]
                     (send-course-embed! course channel-id)))))

(defn force-send-course-embeds! [course-map]
  (apply merge (for [[course {channel-id :channel-id}] (apply merge (map :courses (vals course-map)))
                     :when channel-id]
                 (send-course-embed! course channel-id))))

(defn update-course-embed [course]
  (let [{id :id channel-id :channel-id} (get @state/course-embeds course)
        embed (scrapper/yoink-details course)]
    (when (and id channel-id embed)
        (swap! state/course-embeds update course assoc :embed embed)
        (d-rest/edit-message! (:rest @state/state) channel-id id :embed embed))))

(defn update-course-embeds [course-map]
  (doseq [[_ {courses :courses}] course-map]
    (doseq [[course _] courses]
      (update-course-embed course))))

(defn deep-merge [a & maps]
  (if (map? a)
    (apply merge-with deep-merge a maps)
    (apply merge-with deep-merge maps)))

(defn register! [course user-id guild-id]
  (let [course (str/upper-case course)
        config @state/config]
    (cond
      (not (course? course)) (-> {:content (str course " is not a valid course code")}
                                rsp/channel-message
                                rsp/ephemeral)
      (already-registered? course user-id) (-> {:content (str "You are already registered for " course)}
                                              rsp/channel-message
                                              rsp/ephemeral)
      :else (do
              (state/register-course! course user-id)
              (when (:auto-enroll config)
                (enroll! course user-id guild-id))
              (let [course-map @state/course-map
                    c (:count (get-course course course-map))
                    threshold (:auto-channel-threshold config)
                    role-id (:role-id (get-course course course-map))
                    parent-id (:parent-id (get-in course-map [(subs course 0 4) :parent-id]))
                    parent-id (if (and (>= c threshold) (not parent-id))
                                (:id @(create-category! (subs course 0 4) guild-id))
                                parent-id)
                    channel-id (:channel-id (get-course course course-map))
                    channel-id (if (and (>= c threshold) (not channel-id))
                                 (let [channel-id (:id @(create-channel!
                                                         course
                                                         parent-id
                                                         (conj (:additional-roles config) role-id)
                                                         [guild-id]
                                                         guild-id))]
                                 (when (:auto-send-embed @state/config)
                                   (let [embed (send-course-embed! course channel-id)]
                                     (swap! state/course-embeds deep-merge embed)))
                                 channel-id)
                                 channel-id)
                    message (if channel-id
                              (str "Registered to " course ", join everyone else in " (d-format/mention-channel channel-id))
                              (str "Registered to " course ", currently not enough "
                                   "people have registered for this course. Need "
                                   (- threshold c) " more people. Invite your friends "
                                   "to have a channel created!"))
                    message (-> {:content message}
                                rsp/channel-message
                                rsp/ephemeral)]
                (swap! state/course-map assoc-in [(subs course 0 4) :courses course :channel-id] channel-id)
                (swap! state/course-map assoc-in [(subs course 0 4) :parent-id] parent-id)
                message)))))

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

(defn upload-to-free-hosting [channel file]
  (-> @(d-rest/create-message! (:rest @state/state) channel :file file)
      :attachments
      first
      :url))

(defn update-charts! []
  (score-graph @state/course-map)
  (let [{image-host-channel :image-host-channel
         embed-color :embed-color
         charts :charts} @state/config
         graph-url (upload-to-free-hosting image-host-channel (clojure.java.io/file "graph.png"))]
    (doseq [{channel-id :channel-id message-id :id} charts]
      @(d-rest/edit-message!
        (:rest @state/state)
        channel-id
        message-id
        :embed {:color embed-color :image {:url graph-url}}))))

(defn string->stream
  ([s] (string->stream s "UTF-8"))
  ([s encoding]
   (-> s
       (.getBytes encoding)
       (java.io.ByteArrayInputStream.))))

(def graph-debounced! (state/debounce #(update-charts!) (* 10 1000)))

;;; course
(cmd/defhandler register
  ["register"]
  {{{user-id :id username :username} :user} :member guild-id :guild-id}
  [input]
  (state/info (str "Registered " username " to " input))
  (let [message (register! input user-id guild-id)]
    (graph-debounced!)
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
  {{{user-id :id username :username} :user} :member guild-id :guild-id} ; Using the interaction binding to get the user who ran the command
    [input]
  (state/info (str "Degreisterd " username " from " input))
  (let [message (deregister! input user-id guild-id)]
    (graph-debounced!)
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
          (keep #(when (contains? (get (second %) :users #{}) user-id) (first %)))
          (take 24) ;; 25 is discord autocomplete limit
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
  _
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
    (graph-debounced!)
    message))

(cmd/defhandler force-deregister
  ["force-deregister"]
  {guild-id :guild-id}
  [user course]
  (let [message (deregister! course user guild-id)]
    (graph-debounced!)
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

(defn get-above-threshold
  "Given the course map, find the course-codes with >= n count"
  [course-map n]
  (apply merge
         (keep (fn [[prefix {courses :courses}]]
                 (let [above-threshold (keep (fn [[course {c :count}]] (when (>= c n) course)) courses)]
                   (when (seq above-threshold)
                     {prefix above-threshold}))) course-map)))

(defn create-categories!
  "Create categories for the provided courses
  courses is map from prefix to course-code"
  [courses guild-id]
  (apply merge
         (map (fn [[prefix _]]
                {prefix {:parent-id (:id @(create-category! prefix guild-id))}}) courses)))

(defn create-roles!
  "Create roles for the provided courses
  courses is map from prefix to course-code"
  [courses guild-id]
  (apply merge
         (map (fn [[prefix course-codes]]
                (->>
                 (for [course-code course-codes]
                   [course-code {:role-id (:id @(create-role! course-code guild-id))}])
                 (into {})
                 (hash-map :courses)
                 (hash-map prefix)))
              courses)))

(defn create-channels!
  "Create channels for the provided courses, allow additional roles and self, block @everyone
  courses is map from prefix to course-code"
  [courses course-map additional-roles guild-id]
  (apply merge
         (map (fn [[prefix course-codes]]
                (->>
                 (for [course-code course-codes
                      :let [role-id (get-in course-map [prefix :courses course-code :role-id])
                            parent-id (get-in course-map [prefix :parent-id])]
                       :when (and role-id parent-id)]
                  [course-code {:channel-id (:id @(create-channel!
                                                   course-code
                                                   parent-id
                                                   (conj additional-roles role-id)
                                                   [guild-id]
                                                   guild-id))}])
                 (into {})
                 (hash-map :courses)
                 (hash-map prefix)))
                courses)))

(defn filter-courses [courses course-map f k]
  (apply merge
         (keep (fn [[prefix courses]]
                 (let [filtered-courses
                       (for [course courses
                             :when (f course-map prefix course k)]
                         course)]
                   (when (seq filtered-courses)
                     {prefix filtered-courses}))) courses)))

(defn create-roles-and-channels! [course-map n guild-id embeds?]
  (let [courses (get-above-threshold course-map n)
        filter-fn (fn [course-map prefix course k]
                    (not (get-in course-map [prefix :courses course k])))
        
        categories (create-categories!
                    (filter-courses
                     courses course-map
                     (fn [course-map prefix _ k]
                       (not (get-in course-map [prefix k]))) :parent-id) guild-id)
        
        roles (create-roles! (filter-courses courses course-map filter-fn :role-id) guild-id)
        course-map (swap! state/course-map deep-merge course-map categories roles)
        channels (create-channels!
                  (filter-courses courses course-map filter-fn :channel-id)
                  course-map (:additional-roles @state/config) guild-id)
        course-map (swap! state/course-map deep-merge course-map channels)]
    (when (and embeds? (:auto-send-embed @state/config))
      (let [embeds (send-course-embeds! course-map)]
        (swap! state/course-embeds deep-merge embeds)))))

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
                                @(d-rest/delete-channel! (:rest @state/state) channel-id)
                                (swap! state/course-embeds update k dissoc :id :channel-id))
                              (when-let [role-id (:role-id v)]
                                @(d-rest/delete-guild-role! (:rest @state/state) guild-id role-id))
                              [k (dissoc v :role-id :channel-id)]))
                          (into {}))}]))
   (into {})))

(cmd/defhandler create-roles-and-channels
  ["create-roles-and-channels"]
    {{{username :username} :user} :member guild-id :guild-id}
    [threshold embeds]
  (state/info (str "Creating roles and channels, requested by: " username))
  (future
    (create-roles-and-channels! @state/course-map threshold guild-id embeds))
  (->> {:content "Creating roles and channels..."}
      rsp/channel-message))

(cmd/defhandler remove-roles-and-channels
  ["remove-roles-and-channels"]
  {{{username :username} :user} :member guild-id :guild-id}
  _
  (state/info (str "Removing roles and channels, requested by: " username))
  (future
    (let [course-map (remove-roles-and-channels! @state/course-map guild-id)]
       (reset! state/course-map course-map)))
  (->> {:content "Removing roles and channels"}
      rsp/channel-message))

(cmd/defhandler send-embed
  ["send-embed"]
  _
  [value]
  (if-let [channel-id (get-in @state/course-map [(subs value 0 4) :courses value :channel-id])]
    (do
      (future (let [embed (send-course-embed! value channel-id)]
                (swap! state/course-embeds merge embed)))
      (->> {:content (str "Sending the embed to channel: `" value "`.")}
       rsp/channel-message
       rsp/ephemeral))
    (rsp/channel-message {:content (str "Course `" value "` does not exist in the internal map. Could not find a channel-id.")})))

(cmd/defhandler send-all-embeds
  ["send-all-embeds"]
  _
  _
  (future (let [embeds (force-send-course-embeds! @state/course-map)]
            (swap! state/course-embeds deep-merge embeds)))
  (->> {:content (str "Sending the embeds...")}
       rsp/channel-message))

(cmd/defhandler update-embeds
  ["update-embeds"]
  _
  _
  (future (update-course-embeds @state/course-map))
  (rsp/channel-message {:content "Updating all course embeds..."}))

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

(cmd/defhandler image-host-channel
  ["image-host-channel"]
  _
  [value]
  (if value
    (do
      (swap! state/config assoc :image-host-channel value)
      (->> {:content (str "Set image hosting channel to " value)}
           rsp/channel-message))
    (->> {:content (str "Image hosting channel is " (d-format/code-block (:image-host-channel @state/config)))}
           rsp/channel-message)))

(cmd/defhandler embed-colour
  ["embed-colour"]
  _
  [value]
  (if value
    (do
      (swap! state/config assoc :embed-color value)
      (->> {:content (str "Embed colour is now " value)}
           rsp/channel-message))
    (->> {:content (str "Embed colour is " (d-format/code-block (:embed-color @state/config)))}
           rsp/channel-message)))

(defn dump-to-channel [string filename channel-id]
  (d-rest/create-message! (:rest @state/state) channel-id :stream {:filename filename :content (string->stream string)}))


(cmd/defhandler dump
  ["dump"]
  {channel-id :channel-id}
  _
  (dump-to-channel (with-out-str (pp/pprint @state/course-map)) "course-map.txt" channel-id)
  (rsp/channel-message {:content "Dumping course map to file..."}))

(cmd/defhandler send-chart
  ["send-chart"]
  {channel-id :channel-id}
  _
  (score-graph @state/course-map)
  (let [{image-host-channel :image-host-channel
         embed-color :embed-color} @state/config
        graph-url (upload-to-free-hosting image-host-channel (clojure.java.io/file "graph.png"))
        {id :id channel-id :channel-id}
               @(d-rest/create-message! (:rest @state/state)
                                        channel-id
                                        ;; :content (d-format/code-block message)
                                        :embed {:color embed-color :image {:url graph-url}})]
  (swap! state/config update :charts conj {:id id :channel-id channel-id})
  (->> {:content "Created the graph.\n\nYou can use `/sudo update-charts` to force an update of all charts"}
       rsp/channel-message
       rsp/ephemeral)))

(cmd/defhandler update-charts
  ["update-charts"]
  _
  _
  (future (update-charts!))
  (->> {:content "Forcibly updating all charts..."}
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

(cmd/defhandler auto-send-embed
  ["auto-send-embed"]
  _
  [value]
  (->> (if (nil? value)
         (str "auto-send-embed is currently " (:auto-send-embed @state/config))
         (do
           (swap! state/config assoc :auto-send-embed value)
           (str "Set auto-send-embed to " value)))
       (assoc {} :content)
       rsp/channel-message))

(cmd/defhandler auto-channel-threshold
  ["auto-channel-threshold"]
  _
  [value]
  (->> (if (nil? value)
         (str "auto-channel-threshold is currently " (d-format/code (:auto-channel-threshold @state/config)))
         (let [value (int value)]
           (swap! state/config assoc :auto-channel-threshold value)
           (str "Set auto-channel-threshold to " (d-format/code value))))
       (hash-map :content)
       rsp/channel-message))

(cmd/defhandler override
  ["override"]
  _
  [map]
  (let [old (d-format/code-block (pr-str @state/course-map))]
    (->> (if (nil? map)
           (str "Override the internal course map with the supplied one. Use clojure.edn format.\n"
                "Its currently:" old)
           (do
             (reset! state/course-map (edn/read-string map))
             (str "Overwrote the internal course map. Use `/sudo dump` to view it. It was: " old)))
         (assoc {} :content)
         rsp/channel-message)))

(cmd/defhandler save
  ["save"]
  _
  _
  (state/save!)
  (-> (rsp/channel-message {:content "Saved config and course-map"})
      rsp/ephemeral))

(cmd/defhandler config
  ["config"]
  {channel-id :channel-id}
  [value]
  (let [value (dissoc (edn/read-string value) :token :application-id)
        old (with-out-str (pp/pprint (dissoc @state/config :token :application-id)))]
    (if value
      (do (swap! state/config merge value)
          (dump-to-channel old "config.txt" channel-id)
          (rsp/channel-message {:content "Updated config, it was:"}))
      (do
        (dump-to-channel old "config.txt" channel-id)
        (rsp/channel-message {:content "Dumping config to file..."})))))

(cmd/defhandler course-regex
  ["course-regex"]
  _
  [value]
  (if value
    (do (reset! state/course-regex (re-pattern value))
        (swap! state/config assoc :course-regex value)
        (rsp/channel-message {:content (str "Updated course-regex to " (d-format/code-block value))}))
    (rsp/channel-message {:content (str "course-regex is " (d-format/code-block (:course-regex @state/config)))})))

(cmd/defhandler ping
  ["ping"]
  _
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


