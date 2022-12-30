(ns course-bot.bar
  (:require [clojure.string :as string]))

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

(defn score-graph [course-map]
  (if (empty? course-map)
    "No courses registered."
    (let [counts (->> (vals course-map)
                      (map :courses)
                      (apply merge)
                      (sort-by (comp :count val) >)
                      (map (fn [[k {c :count}]]
                             [k c])))
          max-count (apply max (map second counts))
          max-code-length (->> (vals course-map)
                               (map (comp keys :courses))
                               flatten
                               (map count)
                               (apply max))]
      (->> (map #(display 50 max-code-length max-count %) counts)
           (string/join "\n")
           (#(str % "\nTotal: " max-count))))))
