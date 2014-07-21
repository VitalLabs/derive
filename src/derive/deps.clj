(ns derive.deps)

(defmacro with-tracked-dependencies [[parent result deps] body handler-expr]
  `(let [~parent derive.deps/*tracker*]
     (binding [derive.deps/*tracker* (derive.deps/default-tracker)]
       (let [~result ~body
             ~deps (derive.deps/dependencies *tracker*)]
         ~handler-expr))))
                 
(defmacro defn-derived [fname args & body]
  `(do
     (def ~fname
       (derive.deps/create-derive-fn
        (fn ~(symbol (str (name fname) "-method"))
          [self# ~@args]
          (let [store# ~(first args)
                params# ~(vec (rest args))]
            (derive.deps/ensure-subscription self# store#)
            (if-let [value# (derive.deps/derive-value self# params#)]
              value#
              (with-tracked-dependencies [parent# res# deps#]
                (do ~@body)
                (derive.deps/update-derive self# params# res# store# deps# parent#)))))
        (fn ~(symbol (str (name fname) "-listener"))
          [store deps] (derive.deps/derive-listener nil store deps))))
     ;; TODO: inform listeners of an invalidation and unsubscribe from stores
     (add-watch ~fname :derive-redef identity)))


