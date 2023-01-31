(ns course-bot.state
  (:require [clojure.edn :as edn]
            [discljord.formatting :as d-format]
            [discljord.messaging :as d-rest]
            [course-bot.bar :refer [score-graph]])
  (:import (java.util Timer TimerTask)))


(def state (atom nil))

(def bot-id (atom nil))

(def config (atom
             (merge
              (edn/read-string (slurp "config.edn"))
              (edn/read-string (slurp "token.edn")))))

(def course-map (atom {}))

(defn update-charts! []
  (swap! config update :charts
         #(mapv
           (fn [{channel-id :channel-id
                 message-id :id}]
             @(d-rest/edit-message!
               (:rest @state)
               channel-id
               message-id
               :content (d-format/code-block
                         (score-graph @course-map)))) %)))

;; (defn make-roles! [course user-id]
;;   (when-not (get @state/course-map course)
;;     (d-rest/create-guild-role! (:rest @state/state) state/guild-id :name course)
;;     (d-rest/add-guild-member-role! (:rest @state/state) state/guild-id user-id "1001828872108642464")))

(defn register-course! [course user-id]
  (swap! course-map (fn [map]
                      (-> map
                          (update-in [(subs course 0 4) :courses course :count] (fnil inc 0))
                          (update-in [(subs course 0 4) :courses course :users] (fnil conj #{}) user-id)))))

(defn deregister-course! [course user-id]
  (swap! course-map (fn [map]
                      (-> map
                          (update-in [(subs course 0 4) :courses course :count] (fnil dec 1))
                          (update-in [(subs course 0 4) :courses course :users] (fnil disj #{}) user-id)))))

;; https://gist.github.com/oliyh/0c1da9beab43766ae2a6abc9507e732a
(defn debounce
  ([f] (debounce f 1000))
  ([f timeout]
    (let [timer (Timer.)
          task (atom nil)]
      (with-meta
        (fn [& args]
          (when-let [t ^TimerTask @task]
            (.cancel t))
          (let [new-task (proxy [TimerTask] []
                           (run []
                             (apply f args)
                             (reset! task nil)
                             (.purge timer)))]
            (reset! task new-task)
            (.schedule timer new-task timeout)))
        {:task-atom task}))))

;; example usage
;; (def say-hello (debounce #(println %)))
;; (say-hello "is it me you're looking for?")
;; (say-hello "Lionel")

(def course-map-debounced! (debounce #(spit (:save-filename @config) %) (* 10 1000)))

(def graph-debounced! (debounce #(update-charts!) (* 10 1000)))

(def config-debounced! (debounce #(spit "config.edn" (pr-str (dissoc % :token))) (* 60 1000)))
