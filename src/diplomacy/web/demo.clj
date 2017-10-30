(ns diplomacy.web.demo
  (:require [diplomacy.web.svg-rendering]
            [ring.util.response]))

(defn handler [{:keys [uri] :as request}]
  (if (= uri "/diplomacy-orig.svg")
    {:status 200
     :headers {"Content-Type" "image/svg+xml"}
     :body
     (diplomacy.web.svg-rendering/render-to-svg
      {:unit-positions {:ven {:unit-type :army :country :italy}}
       :supply-center-ownership {:germany [:mun]}
       :game-time {:year 1901 :season :fall}})}
    (ring.util.response/file-response "resources/demo.html")))
