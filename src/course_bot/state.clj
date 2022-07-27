(ns course-bot.state
  (:require [clojure.edn :as edn]))


(def state (atom nil))

(def bot-id (atom nil))

(def config (merge
             (edn/read-string (slurp "config.edn"))
             (edn/read-string (slurp "token.edn"))))

(def app-id (:application-id config))

(def guild-id "716997853121216613")

(def course-map (atom {}))

(def save-future (atom nil))

(defn save [{filename :save-filename}]
    (spit filename (pr-str @course-map)))
