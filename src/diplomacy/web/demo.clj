(ns diplomacy.web.demo
  (:require [ring.util.response]
            [clojure.data.json :as json]))

(defn handler [{:keys [uri] :as request}]
  (case uri
    "/gamestate"
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body
     (json/write-str
      {:unit-positions {:ven {:unit-type :army :country :italy}}
       :supply-center-ownership {:germany [:mun]}
       :game-time {:year 1901 :season :fall}})}
    "/diplomacy-classic-map.svg"
    (ring.util.response/file-response "resources/svg/diplomacy-classic-map.svg")
    "/render-gamestate.js"
    (ring.util.response/file-response "resources/js/render-gamestate.js")
    "/map-svg-positioning.js"
    (ring.util.response/file-response "resources/js/map-svg-positioning.js")
    "/"
    (ring.util.response/file-response "resources/demo.html")))
