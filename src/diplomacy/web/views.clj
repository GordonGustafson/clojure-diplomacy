(ns diplomacy.web.views
  (:require [ring.util.response :as response]
            [diplomacy.DATC-cases :as DATC-cases]
            [diplomacy.test-expansion :as test-expansion]))

(defn DATC-gamestate-before [test-letter-number]
  (if (contains? DATC-cases/DATC-cases test-letter-number)
    (response/response
     (-> (get DATC-cases/DATC-cases test-letter-number)
         (test-expansion/get-gamestate-before)))
    (response/not-found (str "Could not find completed DATC test for '"
                             test-letter-number "'"))))
