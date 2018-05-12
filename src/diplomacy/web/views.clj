(ns diplomacy.web.views
  (:require [ring.util.response :as response]
            [selmer.parser]
            [diplomacy.DATC-cases :as DATC-cases]
            [diplomacy.web.json-serialization :as djson]
            [diplomacy.test-expansion :as test-expansion]))

(defn DATC-key-to-sort-key [DATC-key]
  "The keys for DATC cases are a letter followed by a number. This function
  returns a sort key that orders them [A1, A2, A10] instead of [A1, A10, A2]."
  [(first DATC-key) (Integer. (subs DATC-key 1))])

(defn index []
  (let [template-args {:DATC-button-data
                       (->> DATC-cases/all-DATC-cases
                            (map (fn [[key test]]
                                   {:DATC-key key
                                    :test-complete? (DATC-cases/test-complete?
                                                     test)}))
                            (sort-by (comp DATC-key-to-sort-key :DATC-key)))}]
    (-> (selmer.parser/render-file "public/index.html" template-args)
        (response/response))))

(defn DATC-orders-phase-test [test-letter-number]
  (if (contains? DATC-cases/all-DATC-cases test-letter-number)
    (response/response
     (-> (get DATC-cases/all-DATC-cases test-letter-number)
         (update :validation-results djson/jsonify-map-with-non-string-keys)
         (update :resolution-results djson/jsonify-map-with-non-string-keys)))
    (response/not-found (str "Could not find completed DATC test for '"
                             test-letter-number "'"))))
