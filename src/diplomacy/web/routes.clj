(ns diplomacy.web.routes
  (:require [diplomacy.web.views :as views]
            [compojure.core :refer [defroutes GET]]
            [compojure.route :as route]
            [ring.middleware.json :refer [wrap-json-response]]
            [ring.util.response :as response]
            [clojure.data.json :as json]))

(def index-page (ring.util.response/file-response "resources/public/demo.html"))
(defroutes app
  (GET "/" [] (response/redirect "/index.html"))
  (GET "/index.html" [] (views/index))
  ;; Serve static assets from "resources/public" directory in this project.
  (route/resources "/")
  ;; Serve DATC game states
  (GET "/DATC/:test-letter-number/orders-phase-test" [test-letter-number]
       ((wrap-json-response views/DATC-orders-phase-test) test-letter-number)))
