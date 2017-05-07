(defproject diplomacy "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha14"]
                 [org.clojure/core.match "0.3.0-alpha4"]
                 [org.clojure/core.logic "0.8.11"]
                 ;; This isn't strictly necessary by why not
                 ;; [org.clojure/algo.generic "0.1.2"]
                 ;; TODO: make this a :dev or :test dependency
                 [org.clojure/test.check "0.9.0"]]
  :main ^:skip-aot diplomacy.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
