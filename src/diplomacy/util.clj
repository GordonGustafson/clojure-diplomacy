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
  "Return a function spec that:
  1. takes arguments :arg-1, :arg-2, etc. that conform to the corresponding
     elements in `raw-arg-spec`.
  2. returns a value that conforms to `ret-spec`.
  3. optionally has post-conditions `fn-spec`.

  If `raw-arg-spec` is not a sequence, it is interpreted as the spec for the
  entire argument list.

  In clojure.spec, preconditions are supposed to be in the :args spec so they
  can be checked before executing the function. If you want to do that with this
  function you have to pass the spec for the whole argument list (including its
  preconditions) as `raw-arg-spec`."
  ([raw-arg-spec ret-spec]
   (s/fspec :args (if (sequential? raw-arg-spec)
                    (apply cat-specs raw-arg-spec)
                    raw-arg-spec)
            :ret ret-spec))
  ([raw-arg-spec ret-spec fn-spec]
   (s/fspec :args (if (sequential? raw-arg-spec)
                    (apply cat-specs raw-arg-spec)
                    raw-arg-spec)
            :ret ret-spec
            :fn fn-spec)))

;; Emacs' clojure-mode will indent and highlight functions that start with
;; `defn` as if they were a `defn`, which is convenient.
(defmacro defn-spec
  "Defines `func` to have spec `(fn-spec arg-specs ret-spec)` or `(fn-spec
  arg-specs ret-spec fn-spec)`"
  ([func arg-specs ret-spec]
   `(s/def ~func (fn-spec ~arg-specs ~ret-spec)))
  ([func arg-specs ret-spec fn-spec]
   `(s/def ~func (fn-spec ~arg-specs ~ret-spec ~fn-spec))))

(defn-spec map-difference [map? map?] map?)
(defn map-difference [lhs rhs]
  "A map of all the key-value pairs that are in `lhs` but not `rhs`"
  (into {} (clojure.set/difference (set lhs) (set rhs))))

;; Taken from `https://stackoverflow.com/a/14488425`
(defn dissoc-in
  "Dissociates an entry from a nested associative structure returning a new
  nested structure. keys is a sequence of keys. Any empty maps that result
  will not be present in the new structure."
  [m [k & ks :as keys]]
  (if ks
    (if-let [nextmap (get m k)]
      (let [newmap (dissoc-in nextmap ks)]
        (if (seq newmap)
          (assoc m k newmap)
          (dissoc m k)))
      m)
    (dissoc m k)))
