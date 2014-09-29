(ns derive.core)

(defmacro with-tracked-dependencies
  "Evaluate the body, tracking dependencies, and call the handler
   with a map of {src dep} and result"
  [[handler & [shadow?]] & body]
  `(binding [derive.core/*tracker* (if derive.core/*shadow*
                                     derive.core/*tracker*
                                     (derive.core/default-tracker))
             derive.core/*shadow* (or derive.core/*shadow* ~shadow?)]
     (let [result# (do ~@body)]
       (when-not derive.core/*shadow*
         (let [dmap# (derive.core/dependencies derive.core/*tracker*)]
           (~handler result# dmap#)))
       result#)))

(defmacro on-changes
  "Useful in contexts like an om render loop where we simply
  want to refresh the UI when a change is detected. e.g.
  (render [_]
    (derive/on-changes #(om/refresh owner)
      (html  "
  [[subscribe-fn update-fn] & body]
  `(with-tracked-dependencies
     [ (fn [result# dependency-map#]
         (let [cb# (fn [& args#] (~update-fn))]
           (~subscribe-fn cb# dependency-map#)
           (doseq [[store# query-deps#] dependency-map#]
             (derive.core/subscribe! store# cb# query-deps#))))
     ]
     ~@body))

(defmacro defnd
  "Create a Derive function which manages the derive lifecycle for a
   set of function results that call out to other derive functions or
   source (store / databases).  Currently the first argument to a derive
   function must be a source"
  [fname args & body]
  ;; TODO:
  ;; (if (exists? ~fname) <handle exists> <create new>)
  ;; handle exists means informing listeners then recreation method
  `(def ~fname
     (let [derive# (derive.core/create-derive-fn ~fname)
           dfn# (fn ~(symbol (str (name fname) "-method"))
                 [self# ~@args]
                 (let [params# ~(vec args)]
                   (if-let [value# (derive.core/derive-value self# params#)]
                     value#
                     (with-tracked-dependencies
                       [(derive.core/tracker-handler self# params#)]
                       ~@body))))
           lfn# (fn ~(symbol (str (name fname) "-listener"))
                  [lstore# ldeps#]
                  (derive.core/derive-listener derive# lstore# ldeps#))]
       (set! (.-dfn derive#) dfn#)
       (set! (.-lfn derive#) lfn#)
       derive#)))




