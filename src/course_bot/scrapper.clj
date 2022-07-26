(ns course-bot.scrapper
  (:require [net.cgrand.enlive-html :as html])
  (:import java.net.URL))

(def base-url "https://my.uq.edu.au/programs-courses/course.html?course_code=")

(defn get-html
  "A memorised function to retreive html."
  [url]
   (html/html-resource (URL. url)))

(defn yoink-details
  "Scrap the details of all availiable course offerings from UQ"
  [course-code]
  (let [html (get-html (str base-url course-code))]
    {:name (first (html/select html [:#course-title html/content]))
     :desc (first (html/select html [:#course-summary html/content]))
     :offerings
     (for [current-offerings (html/select html [:table#course-current-offerings :tbody :tr])]
       {:sem (first (html/select current-offerings
                                 [:a.course-offering-year html/content]))

        :mode (first (html/select current-offerings
                                  [:td.course-offering-mode :a html/content]))

        :location (first (html/select current-offerings
                                      [:td.course-offering-location html/content]))

        :profile (:href (:attrs
                         (first (html/select current-offerings
                                             [:td.course-offering-profile :a]))))})}))
