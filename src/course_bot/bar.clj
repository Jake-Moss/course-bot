(ns course-bot.bar
  (:require [clojure.string :as string]
            [cljplot.render :as r]
            [cljplot.build :as b]
            [cljplot.common]
            [cljplot.core]
            [cljplot.config]
            [course-bot.state :as state]
            [clojure.string :as str]))

(def block-step 0.125)

(def whole "█")
(def blocks ["" "▏" "▎" "▍" "▌" "▋" "▊" "▉" "█"])

(def lsep "▏")
(def rsep "▕")

(defn make-bar [max-length percent]
  (assert (<= percent 1))
  (let [length (* percent max-length) ;; Proportion filled
        whole-tiles-count (Math/floor length)
        frac-part (- length whole-tiles-count)
        frac-index (int (/ frac-part block-step)) ;; Find index of partial block
        filled-bar (str (string/join (repeat whole-tiles-count whole)) (blocks frac-index))
        missing (- max-length (count filled-bar))
        empty-bar (string/join (repeat missing " "))]
    (str lsep filled-bar empty-bar rsep)))

(defn display [max-len max-code-length max-count [code popularity]]
  (str (format (str "%-" max-code-length "s") code)
       "\t" (make-bar max-len (/ popularity max-count))
       "\t" popularity))


;; Adjust the config because the library is really stupid
(reset! cljplot.config/configuration
        (-> @cljplot.config/configuration
            (assoc-in [:label :font-size] 12)
            (assoc-in [:axis-left :ticks :font-size] 12)
            (assoc-in [:axis-bottom :ticks :font-size] 12)))

;; (defn score-graph [course-map]
;;   (if (empty? course-map)
;;     "No courses registered."
;;     (let [counts (->> (vals course-map)
;;                       (map :courses)
;;                       (apply merge)
;;                       (sort-by (comp :count val) >)
;;                       (map (fn [[k {c :count}]]
;;                              [k c])))
;;           max-count (apply max (map second counts))
;;           total (reduce + (map second counts))
;;           max-code-length (->> (vals course-map)
;;                                (map (comp keys :courses))
;;                                flatten
;;                                (map count)
;;                                (apply max))]
;;       (->> (map #(display 50 max-code-length max-count %) counts)
;;            (string/join "\n")
;;            (#(str % "\nTotal: " total))))))

(defn score-graph [course-map]
  (let [data (->> (vals course-map)
                  (map :courses)
                  (apply merge)
                  (map (fn [[k {c :count}]]
                         [k c]))
                  (sort-by second >))
        message (str/join "\n" (map (fn [[name x]] (str name ": " x)) data))]

    (-> (b/series
         [:grid nil {:x nil}]
         [:stack-vertical [:bar data {:padding-out 0.1}]])
        (b/preprocess-series)
        (b/update-scale :x :fmt name)
        (b/update-scale :y :fmt int)
        (b/add-axes :bottom)
        (b/add-axes :left)
        (b/add-label :bottom "Course code")
        (b/add-label :left "Popularity")
        (r/render-lattice {:width (max (* 36 (count data)) 1024) :height 480})
        (cljplot.core/save "graph.png"))
    message))

(comment
(let [counts (->> (vals @state/course-map)
                      (map :courses)
                      (apply merge)
                      (sort-by (comp :count val) >)
                      (map (fn [[k {c :count}]]
                             [k c])))
          max-count (apply max (map second counts))
          total (reduce + (map second counts))
          max-code-length (->> (vals course-map)
                               (map (comp keys :courses))
                               flatten
                               (map count)
                               (apply max))]
      (->> (map #(display 50 max-code-length max-count %) counts)
           (string/join "\n")
           (#(str % "\nTotal: " total))))



(get-in @cljplot.config/configuration [:axis-left :ticks :font-size])



)
