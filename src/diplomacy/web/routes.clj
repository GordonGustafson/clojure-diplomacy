(ns diplomacy.web.routes
  (:require [diplomacy.web.views :as views]
            [compojure.core :refer [defroutes GET]]
            [compojure.route :as route]
            [ring.util.response :as response]
            [clojure.data.json :as json]))

(def index-page (ring.util.response/file-response "resources/public/demo.html"))
(defroutes app
  (GET "/" [] (response/redirect "/index.html"))
  ;; Serve static assets from "resources/public" directory in this project.
  (route/resources "/")

  (GET "/gamestate1" []
       {:status 200
        :headers {"Content-Type" "application/json"}
        :body
        (json/write-str
         {:unit-positions {:ven {:unit-type :army :country :italy}}
          :supply-center-ownership {:germany [:mun]}
          :game-time {:year 1901 :season :fall}})})
  (GET "/gamestate2" []
       {:status 200
        :headers {"Content-Type" "application/json"}
        :body
        (json/write-str
         {:unit-positions {:bre {:unit-type :fleet :country :france}}
          :supply-center-ownership {:france [:par]}
          :game-time {:year 1901 :season :fall}})}))
