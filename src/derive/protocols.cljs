(ns derive.protocols)

;;
;; Dependency Protocols
;;


;; AtomStore (tree structured, queries are lookups and such)
;; NativeStore
;; DatascriptStore


;; Store -> produces Dependencies as query/access methods are called
;;          by calling a Tracker
;;          Generates transactions for listeners

;; Tracker -> sits in a dynamic variable
;;            gets called with dependencies
;;         -> Listens to store transactions

;; Dependency -> 

;; Transaction ->



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
  (changes [this txn deps]))

;; Passed to dependency trackers during queries via record-dependency
(defprotocol IDependencySet
  (merge-dependencies [this deps])
  (dependency-match? [this changes]))



;;
;; Store protocols
;;

;; + ILookup
(defprotocol IStore
  (insert! [store obj])  ;; shallow merge upsert of native objects
  (delete! [store obj])) ;; delete, only need primary ID in submitted object

;; CompFn(KeyFn(obj)) -> value, obj
(defprotocol IIndex
  (key-fn [idx])
  (index! [idx obj])
  (unindex! [idx obj]))

(defprotocol ISortedIndex
  (comparator-fn [idx]))

(defprotocol IScannable
  (scan [idx f] [idx f start] [idx f start end]))

(defprotocol IIndexedStore
  (add-index! [store name index])
  (rem-index! [store name])
  (get-index  [store name]))


(defprotocol ITransactionalStore
  (transact! [store fn args]))

