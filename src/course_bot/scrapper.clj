(ns course-bot.scrapper
  (:require [net.cgrand.enlive-html :as html])
  (:import java.net.URL))

(def base-url "https://my.uq.edu.au/programs-courses/course.html?course_code=")

(defn get-html
  "A memorised function to retreive html."
  [url]
   (html/html-resource (URL. url)))

(defn yoink-details
  "Scrap the details of all availiable course offerings from UQ.
  Returns a discord embed object"
  [course-code]
  (let [url (str base-url course-code)
        html (get-html url)]
    {:title (first (html/select html [:#course-title html/content]))
     :description (first (html/select html [:#course-summary html/content]))
     :url url
     :color 5814783
     :author {:name (first (html/select html [:#course-coordinator html/content]))} ;; cc
     :footer {:text (first (html/select html [:#course-offering-1-sem html/content]))} ;; date
     :fields ;; Offerings
     (for [current-offerings (html/select html [:table#course-current-offerings :tbody :tr])]
       {;; mode
        :name (first (html/select current-offerings
                                  [:td.course-offering-mode :a html/content]))
        ;; profile url
        :value (or (:href (:attrs
                           (first (html/select current-offerings
                                               [:td.course-offering-profile :a]))))
                   "Profile unavailable")})}))


(comment
  (yoink-details "MATH1071")

  (html/select (get-html (str base-url "MATH1071")) [:#course-offering-1-sem html/content])
  :sem (first (html/select current-offerings
                           [:a.course-offering-year html/content]))
  :location (first (html/select current-offerings
                                [:td.course-offering-location html/content]))

 )
