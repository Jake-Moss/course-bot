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
       "\t" popularity " / " max-count))

(defn score-graph [course-map]
  (if (empty? course-map)
    "No courses registered."
    (let [max-count (apply max (vals course-map))
          max-code-length (apply max (map count (keys course-map)))]
      (string/join "\n" (map (partial display 50 max-code-length max-count) (sort-by val > course-map))))))
