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
       (DeriveFn. (fn ~(intern (str (name fname) "-method"))
                    [self# ~@args]
                    (let [store# ~(first args)
                          params# [~@(rest args)]]
                      (derive.deps/ensure-subscription self# store#)
                      (if-let [value (derive.core/derive-value self# params#)]
                        value
                        (with-tracked-dependencies [parent# res# deps#]
                          (do ~@body)
                          (derive.core/update-derive self# params# res# store# deps# res# parent#)))))
                  (fn ~(intern (str (name fname) "-listener"))
                    [store deps] (derive.core/derive-listener nil store deps))
                  #{}
                  (derive.deps/default-cache)
                  #{}))
     (add-watch ~name :derive-redef identity))) ;; TODO: inform listeners of an invalidation


