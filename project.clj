(defproject diplomacy "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha17"]
                 ;; Used extensively in the resolution engine
                 [org.clojure/core.logic "0.8.11"]
                 ;; Used occasionally for convenience
                 [org.clojure/core.match "0.3.0-alpha4"]
                 ;; Web dependencies
                 [ring/ring-core "1.6.2"]
                 [ring/ring-devel "1.6.2"]
                 ;; Used for templating, seems to pull in a LOT of transitive
                 ;; dependencies.
                 [selmer "1.11.1"]
                 ;; TODO: make this a :dev or :test dependency
                 [org.clojure/test.check "0.9.0"]]
  :plugins [[lein-ring "0.9.7"]]
  :ring {:handler diplomacy.web.demo/handler}
  :main ^:skip-aot diplomacy.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
