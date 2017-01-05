(defproject diplomacy "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha14"]
                 [org.clojure/core.logic "0.8.11"]
                 [org.clojure/core.match "0.3.0-alpha4"]
                 ;; TODO: make this a :dev or :test dependency
                 [org.clojure/test.check "0.9.0"]]
  ;; TODO: set main namespace
  :main ^:skip-aot diplomacy.resolution
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
