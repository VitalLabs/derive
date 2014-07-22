(ns derive.deps)

(defmacro with-tracked-dependencies
  "Capture the dependency tracking context into parent, result, and deps"
  [[parent result final-deps init-deps] body handler-expr]
  `(let [~parent derive.deps/*tracker*]
     (binding [derive.deps/*tracker* (if-let [ideps# ~init-deps]
                                       (derive.deps/default-tracker ideps#)
                                       (derive.deps/default-tracker))]
       #_(println (.-deps derive.deps/*tracker*) (type (.-deps derive.deps/*tracker*)))
       (let [~result (do ~body)
             ~final-deps (derive.deps/dependencies derive.deps/*tracker*)]
         ~handler-expr
         ~result))))

(defmacro defn-derived
  "Create a Derive function which manages the derive lifecycle for a
   set of function results that call out to other derive functions or
   source (store / databases).  Currently the first argument to a derive
   function must be a source"
  [fname args & body]
  ;; (if (exists? ~fname) <handle exists> <create new>)
  ;; handle exists means informing listeners then recreation method
  `(def ~fname
     (let [derive# (derive.deps/create-derive-fn ~fname)
           dfn# (fn ~(symbol (str (name fname) "-method"))
                 [self# ~@args]
                 (let [store# ~(first args) params# ~(vec (rest args))]
                   (derive.deps/ensure-subscription self# store#)
                   (if-let [[value# st# dep#] (derive.deps/derive-value self# params#)]
                     value#
                     (with-tracked-dependencies [parent# res# deps# (empty-deps store#)]
                       (do ~@body)
                       (derive.deps/update-derive self# params# res# store# deps# parent#)))))
           lfn# (fn ~(symbol (str (name fname) "-listener"))
                  [lstore# ldeps#]
                  (derive.deps/derive-listener derive# lstore# ldeps#))]
       (set! (.-dfn derive#) dfn#)
       (set! (.-lfn derive#) lfn#)
       derive#)))

;; TODO inform listeners of an invalidation and unsubscribe from stores???


