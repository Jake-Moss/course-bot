(ns course-bot.state
  (:require [clojure.edn :as edn]
            [discljord.formatting :as d-format]
            [discljord.messaging :as d-rest])
  (:import (java.util Timer TimerTask)
           [ch.qos.logback.classic Level]
           [org.slf4j LoggerFactory]))


(def state (atom nil))

(def bot-id (atom nil))

(def config (atom
             (merge
              (edn/read-string (slurp "config.edn"))
              (edn/read-string (slurp "token.edn")))))

(def course-map (atom {}))

(def course-regex (atom (re-pattern (:course-regex @config))))

(def course-embeds (atom {}))

;; https://github.com/vaughnd/clojure-example-logback-integration/blob/master/src/clojure_example_logback_integration/log.clj
(def logger ^ch.qos.logback.classic.Logger (LoggerFactory/getLogger "course-bot"))

(defn set-log-level!
  "Pass keyword :error :info :debug"
  [level]
  (case level
    :debug (.setLevel logger Level/DEBUG)
    :info (.setLevel logger Level/INFO)
    :error (.setLevel logger Level/ERROR)))

(defmacro debug [& msg]
  `(.debug logger (print-str ~@msg)))

(defmacro info [& msg]
  `(.info logger (print-str ~@msg)))

(defmacro error [throwable & msg]
  `(if (instance? Throwable ~throwable)
    (.error logger (print-str ~@msg) ~throwable)
    (.error logger (print-str ~throwable ~@msg))))

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

(def config-debounced! (debounce #(spit "config.edn" (pr-str (dissoc % :token :application-id))) (* 60 1000)))

(def embed-debounced! (debounce #(spit "embeds.edn" (pr-str %)) (* 60 1000)))

(defn save! []
  (spit "config.edn" (pr-str (dissoc @config :token :application-id)))
  (spit "embeds.edn" (pr-str @course-embeds))
  (spit (:save-filename @config) @course-map))
