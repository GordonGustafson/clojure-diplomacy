(ns diplomacy.web.views
  (:require [ring.util.response :as response]
            [diplomacy.DATC-cases :as DATC-cases]
            [diplomacy.web.json-serialization :as dip-json]
            [diplomacy.test-expansion :as test-expansion]))

(defn DATC-orders-phase-test [test-letter-number]
  (if (contains? DATC-cases/DATC-cases test-letter-number)
    (response/response (update (get DATC-cases/DATC-cases test-letter-number)
                               :resolution-results
                               dip-json/jsonify-resolution-results))
    (response/not-found (str "Could not find completed DATC test for '"
                             test-letter-number "'"))))
