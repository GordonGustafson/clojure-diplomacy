(ns diplomacy.util
  (:require [clojure.spec.alpha :as s]
            [clojure.set]))

(defn ^:private cat-specs
  ([a]           (s/cat :arg-1 a))
  ([a b]         (s/cat :arg-1 a :arg-2 b))
  ([a b c]       (s/cat :arg-1 a :arg-2 b :arg-3 c))
  ([a b c d]     (s/cat :arg-1 a :arg-2 b :arg-3 c :arg-4 d))
  ([a b c d e]   (s/cat :arg-1 a :arg-2 b :arg-3 c :arg-4 d :arg-5 e))
  ([a b c d e f] (s/cat :arg-1 a :arg-2 b :arg-3 c :arg-4 d :arg-5 e :arg-6 f)))

(defn fn-spec
  "Return a function spec taking arguments that conform to the corresponding
  elements in `raw-arg-spec` and returning a value that conforms to `ret-spec`.
  If `raw-arg-spec` is not a sequence, it is interpreted as the spec for the
  entire argument list."
  [raw-arg-spec ret-spec]
  (s/fspec :args (if (sequential? raw-arg-spec)
                   (apply cat-specs raw-arg-spec)
                   raw-arg-spec)
           :ret ret-spec))

;; Emacs' clojure-mode will indent and highlight functions that start with
;; `defn` as if they were a `defn`, which is convenient.
(defmacro defn-spec
  "Defines `func` to have spec `(fn-spec arg-specs ret-spec)`"
  [func arg-specs ret-spec]
  `(s/def ~func (fn-spec ~arg-specs ~ret-spec)))

(defn-spec map-difference [map? map?] map?)
(defn map-difference [lhs rhs]
  "A map of all the key-value pairs that are in `lhs` but not `rhs`"
  (into {} (clojure.set/difference (set lhs) (set rhs))))
