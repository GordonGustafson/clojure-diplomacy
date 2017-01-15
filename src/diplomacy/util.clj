(ns diplomacy.util
  (:require [clojure.spec :as s]))

;; TODO: see if we can make this a function instead of a macro
(defmacro fn-spec
  "Return a function spec taking arguments that conform to the corresponding
  elements in `args-specs` and returning a value that conforms to `ret-spec`. If
  `args-specs` is not a sequence, it is interpreted as the spec for the entire
  argument list."
  [arg-specs ret-spec]
  (if (seq? arg-specs)
    `(s/fspec :args (s/tuple ~@arg-specs) :ret ~ret-spec)
    `(s/fspec :args ~arg-specs            :ret ~ret-spec)))

;; Emacs' clojure-mode will indent and highlight functions that start with
;; `defn` as if they were a `defn`, which is convenient.
(defmacro defn-spec
  "Defines `func` to have spec `(fn-spec arg-specs ret-spec)`"
  [func arg-specs ret-spec]
  `(s/def ~func (fn-spec ~arg-specs ~ret-spec)))
