(ns diplomacy.web.views
  (:require [ring.util.response :as response]
            [selmer.parser]
            [diplomacy.DATC-cases :as DATC-cases]
            [diplomacy.web.json-serialization :as dip-json]
            [diplomacy.test-expansion :as test-expansion]))

(defn DATC-key-to-sort-key [DATC-key]
  "The keys for DATC cases are a letter followed by a number. This function
  returns a sort key that orders them [A1, A2, A10] instead of [A1, A10, A2]."
  [(first DATC-key) (Integer. (subs DATC-key 1))])

(defn index []
  (let [template-args {:DATC-keys (->> DATC-cases/all-DATC-cases
                                       (keys)
                                       (sort-by DATC-key-to-sort-key))}]
    (-> (selmer.parser/render-file "public/index.html" template-args)
        (response/response))))

(defn DATC-orders-phase-test [test-letter-number]
  (if (contains? DATC-cases/all-DATC-cases test-letter-number)
    (response/response (update (get DATC-cases/all-DATC-cases
                                    test-letter-number)
                               :resolution-results
                               dip-json/jsonify-resolution-results))
    (response/not-found (str "Could not find completed DATC test for '"
                             test-letter-number "'"))))
