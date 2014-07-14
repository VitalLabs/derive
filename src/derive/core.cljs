(ns derive.core
  (:require-macros [derive.core])) ; :refer [defn-derive]]))

(def ^{:dynamic true :private false} *dependency-tracker* nil)

;; Implemented by function and component caches
(defprotocol IDependencyTracker
  (reset-dependencies [this])
  (record-dependencies [this params db dep])
  (satisfied-dependencies? [this params new-dbs])
  (record-value [this params value])
  (derived-value [this params]))

;; Implemented by a DB reference
(defprotocol ITrackDependencies
  (dependencies [this query params])
  (changes [this new-db]))

;; Passed to dependency trackers during queries via record-dependency
(defprotocol IDependencySet
  (merge-dependencies [this deps])
  (dependency-match? [this changes]))


;;
;; Simple tracker
;;

(defrecord DefaultTracker [cache]
  IDependencyTracker
  (reset-dependencies [_]
    (reset! cache {}))

  (record-dependency [_ params db deps]
    (swap! cache update-in [params :dbs db]
           merge-with #(if (nil? %1) %2 (merge-dependencies %1 %2)))
    value)

  (record-value [_ params value]
    (swap! cache assoc-in [params :value] value)
    value)

  (satisfied-dependencies? [this new-dbs params]
    (if-let [{:keys [dbs deps]} (get @cache params)]
      (every? (fn [dep old-db new-db]
                (dependency-match? dep (changes old-db new-db)))
              deps dbs new-dbs)
      false))
  
  (derived-value [this params]
    (get @cache params)))

(defn empty-tracker []
  (DefaultTracker. (atom {})))



;; Examples

(comment

  (defn card [db task-id]
    (let [{:keys [db deps]} (get-memo task-id)
          change-set (txn-diff db new)]
      (if (set/intersection deps change-set)
        (with-dependency-capture deps
          (let [res (card* app task-id)]
            ;; save res, deps -> db + params
            res))
        (:res state))))

  (defmacro defn-derived [card [new-db & args] & body]
    `(let [cache# (atom {})]
       (defn ~card ~(vec (cons new-db args))
         (let [{:keys [db deps]} ((deref cache#) args)
               changes (change-set conn)]
           (if-not (empty? (set/intersection changes deps))
             (with-dependency-capture deps
               (let [res (do ~@body)]
                 (card* app task-id)))))))))






  


