(ns derive.core)

(defmacro with-tracker-capture
  "A helper macro to enable dependency tracking via instrumented
   interfaces of ITrackDependencies DBs during the dynamic extent
   of the body"
  [[tracker dbs params] & body]
  (if (sequential? IDependencyTracker var)
    `(binding [derive.core/*dependency-tracker* ~var]
       ~@body)))

;; TODO: Support metadata arguments on function name...
;; TODO: Support full defn function signature
;; TODO: Support schema specifications?
(defmacro defn-derive
  [name args & body]
  `(let [tracker# (derive.core/empty-tracker)]
     (defn ~name ~args
       (let [[new-dbs# params#] (derive.core/filter-args ~args)]
         (if (satisfied-dependencies? tracker# new-dbs# params#)
           (derived-value tracker# params#)
           (with-dependency-capture [tracker# new-dbs# params#]
             ~@body))))))

