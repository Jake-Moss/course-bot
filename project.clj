(defproject course-bot "0.1.0-SNAPSHOT"
  :description "Discord bot to handle UQ courses"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :repositories [["jitpack" "https://jitpack.io"]]
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [org.clojure/tools.logging "1.2.4"]
                 [ch.qos.logback/logback-classic "1.4.5"]
                 ;; [com.github.discljord/discljord "1.3.1"]
                 [com.github.Jake-Moss/discljord "4067dcc0df"]
                 [enlive "1.1.6"]
                 [com.github.johnnyjayjay/slash "0.5.0-SNAPSHOT"]
                 [cljplot "0.0.2a-SNAPSHOT"]]
  :repl-options {:init-ns course-bot.core}
  :plugins [[lein-cljfmt "0.8.0"]]
  :jvm-opts ["-Dclojure.tools.logging.factory=clojure.tools.logging.impl/slf4j-factory"]
  :main course-bot.core
  :aot [course-bot.core])
