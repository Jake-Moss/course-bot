(ns course-bot.scrapper
  (:require [net.cgrand.enlive-html :as html]
            [clojure.string :as str])
  (:import java.net.URL))

(def base-url "https://my.uq.edu.au/programs-courses/course.html?course_code=")

(defn get-html
  "A memorised function to retreive html."
  [url]
   (html/html-resource (URL. url)))

(defn now [] (java.util.Date.))

(defn yoink-details
  "Scrap the details of all availiable course offerings from UQ.
  Returns a discord embed object"
  [course-code]
  (let [url (str base-url course-code)
        html (get-html url)
        embed {:title (first (html/select html [:#course-title html/content]))
               :description (first (html/select html [:#course-summary html/content]))
               :url url
               :color 5814783
               :author {:name (->> (html/select html [:#course-coordinator html/content])
                                   (take-nth 2)
                                   (apply str)
                                   (#(str/replace %1 "\n" " ")))} ;; cc
               :footer {:text (str "Last updated - " (.toString (now)))} ;; date
               :fields ;; Offerings
               (for [current-offerings (html/select html [:table#course-current-offerings :tbody :tr])]
                 {;; mode
                  :name (str
                         (first (html/select current-offerings [:a.course-offering-year html/content]))

                         )
                  ;; profile url
                  :value (or (some-> (html/select current-offerings [:td.course-offering-profile :a])
                                 first
                                 :attrs
                                 :href
                                 (#(str (first (html/select current-offerings [:td.course-offering-mode :a html/content])) ": [Course Profile]" "(" % ")")))
                             "Profile unavailable")})}]
    (if (and (nil? (:title embed)) (nil? (:description embed)))
      {:title "No embed found" :description "Are you sure this is a real course? If it is, please ping @rogueportal"}
      embed)
    ))


(comment
  (yoink-details "MATH1071")

  (html/select (get-html (str base-url "MATH1071")) [:#course-offering-1-sem html/content])
  :sem (first (html/select current-offerings
                           [:a.course-offering-year html/content]))
  :location (first (html/select current-offerings
                                [:td.course-offering-location html/content]))

 )
