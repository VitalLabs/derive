(ns derive.deps)

(defmacro with-tracked-dependencies
  "Capture the dependency tracking context into parent, result, and deps"
  [[parent result deps] body handler-expr]
  `(let [~parent derive.deps/*tracker*]
     (binding [derive.deps/*tracker* (derive.deps/default-tracker)]
       (let [~result (do ~body)
             ~deps (derive.deps/dependencies *tracker*)]
         ~handler-expr))))
                 
(defmacro defn-derived
  "Create a Derive function which manages the derive lifecycle for a
   set of function results that call out to other derive functions or
   source (store / databases).  Currently the first argument to a derive
   function must be a source"
  [fname args & body]
  `(def ~fname
     (derive.deps/create-derive-fn
      (fn ~(symbol (str (name fname) "-method"))
        [self# ~@args]
        (let [store# ~(first args) params# ~(vec (rest args))]
          (derive.deps/ensure-subscription self# store#)
          (if-let [value# (derive.deps/derive-value self# params#)]
            value#
            (with-tracked-dependencies [parent# res# deps#]
              (do ~@body)
              (derive.deps/update-derive self# params# res# store# deps# parent#)))))
      (fn ~(symbol (str (name fname) "-listener"))
        [lstore# ldeps#] (derive.deps/derive-listener nil lstore# ldeps#)))))

;; TODO inform listeners of an invalidation and unsubscribe from stores???

