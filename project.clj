(defproject starcity "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}

  :jvm-opts ^:replace ["-XX:MaxPermSize=128m" "-Xms512m" "-Xmx512m" "-server"]

  :dependencies [[org.clojure/clojure "1.8.0"]
                 ;; cljs
                 [org.clojure/clojurescript "1.7.170"]
                 [reagent "0.5.1"]
                 [re-frame "0.7.0"]
                 [re-com "0.8.1"]
                 [secretary "1.2.3"]
                 ;; clj
                 [bidi "1.21.1"]
                 [cheshire "5.5.0"]
                 [ring/ring "1.4.0"]
                 [hiccup "1.0.5"]
                 [com.stuartsierra/component "0.3.1"]
                 [com.datomic/datomic-free "0.9.5350"
                  :exclusions [joda-time]]
                 [me.raynes/fs "1.4.6"]
                 [com.taoensso/timbre "4.3.1"]]

  :profiles
  {:dev        {:source-paths ["src/dev" "src/clj" "src/cljs"]
                :plugins      [[lein-figwheel "0.5.0-2"]
                               [org.clojars.strongh/lein-init-script "1.3.1"]]
                :dependencies [[figwheel-sidecar "0.5.0-2"]]}
   :production {:source-paths ["src/clj" "src/cljs"]
                :aot  [starcity.core]
                :lis-opts {:redirect-output-to "/var/log/starcity-init-script.log"
                           :jvm-opts ["-XX:MaxPermSize=128m"
                                      "-Xms256m"
                                      "-Xmx512m"
                                      "-server"]}}}

  :repl-options {:init-ns          user
                 :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

  :main starcity.core
  )
