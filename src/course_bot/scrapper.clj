(ns course-bot.scrapper
  (:require [net.cgrand.enlive-html :refer :all])
  (:import java.net.URL))


(defn yoink-details [course-code]
  "Scrap the details of all availiable course offersings from UQ"
  (let [html (html-resource (URL. (str "https://my.uq.edu.au/programs-courses/course.html?course_code=" course-code)))]
    {:desc (first (select html [:#course-summary content]))
     :offerings
     (for [current-offerings (select html [:table#course-current-offerings :tbody :tr])]
       {:year (first (select current-offerings
                             [:a.course-offering-year content]))

        :mode (first (select current-offerings
                             [:td.course-offering-mode :a content]))

        :location (first (select current-offerings
                                 [:td.course-offering-location content]))

        :profile (:href (:attrs
                         (first (select current-offerings
                                        [:td.course-offering-profile :a]))))})}))
