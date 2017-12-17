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
                 ;; Used to get better spec instrumentation than core.spec
                 ;; currently has (checking that return values conform to
                 ;; specs).
                 [orchestra "2017.08.13"]
                 ;; Web dependencies
                 [ring/ring-core "1.6.2"]
                 [ring/ring-devel "1.6.2"]
                 [compojure "1.6.0"]
                 ;; Converting Clojure data structures to and from JSON
                 [org.clojure/data.json "0.2.6"]
                 ;; TODO: make this a :dev or :test dependency
                 [org.clojure/test.check "0.9.0"]]
  :plugins [[lein-ring "0.9.7"]]
  :ring {:handler diplomacy.web.routes/app}
  :main ^:skip-aot diplomacy.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
