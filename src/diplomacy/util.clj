(ns diplomacy.util
  (:require [clojure.spec :as s]))

(defmacro def- [item value]
  `(def ^{:private true} ~item ~value))

(defn equal-by [func & values]
  "Whether `func` returns equal (`=`) values for every element of `values`"
  (apply = (map func values)))

;; TODO: see if we can make this a function instead of a macro
(defmacro fn-spec
  "Specs `func` as taking arguments that conform to the corresponding elements
  in `args-specs` and returning a value that conforms to `ret-spec`."
  [func arg-specs ret-spec]
  (if (seq? arg-specs)
    `(s/fdef ~func :args (s/tuple ~@arg-specs) :ret ~ret-spec)
    `(s/fdef ~func :args ~arg-specs            :ret ~ret-spec)))
