(ns diplomacy.util)

(defmacro fail [message]
  `(assert false ~message))

(defmacro def- [item value]
  `(def ^{:private true} ~item ~value))

(defn equal-by [func & values]
  "Whether `func` returns equal (`=`) values for every element of `values`"
  (apply = (map func values)))
