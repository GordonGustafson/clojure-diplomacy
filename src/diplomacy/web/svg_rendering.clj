(ns diplomacy.web.svg-rendering
  (:require [selmer.parser]
            [selmer.util]
            [clojure.string]
            [diplomacy.util :refer [defn-spec]]
            [diplomacy.datatypes :as dt]))

;;; TODO: Render all svg tags in the template instead of using functions that
;;; return svg strings.

(defn-spec unit-positions-to-svg [::dt/unit-positions] string?)
(defn ^:private unit-positions-to-svg
  [unit-positions]
  (clojure.string/join "\n"
                       (map (fn [[location {:keys [unit-type country]}]]
                              (format "<g><use xlink:href=\"#%s\" class=\"%s\" transform=\"translate(&%s;)\"/></g>"
                                      (name unit-type)
                                      (name country)
                                      (name location)))
                            unit-positions)))

(defn-spec supply-center-ownership-to-svg
  [::dt/supply-center-ownership] string?)
(defn ^:private supply-center-ownership-to-svg
  [supply-center-ownership]
  (clojure.string/join "\n"
                       (for [[country locations] supply-center-ownership
                             location locations]
                         (format "<g><use xlink:href=\"#sc\" class=\"%s\" transform=\"translate(&sc_%s;)\"/></g>"
                                 (name country)
                                 (name location)))))

(defn-spec render-to-svg [::dt/game-state] string?)
(defn render-to-svg
  [{:keys [unit-positions supply-center-ownership game-time]}]
  (let [template-args
        {:unit-positions (unit-positions-to-svg unit-positions)
         :supply-center-ownership
         (supply-center-ownership-to-svg supply-center-ownership)
         :turn-year (-> game-time :year str)
         :turn-season (-> game-time :season name)}]
    (selmer.util/without-escaping
     (selmer.parser/render-file "templates/classic-map-template.svg"
                                template-args))))
