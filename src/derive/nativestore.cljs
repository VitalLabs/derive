(ns derive.nativestore
  (:use purnam.native.functions)
  (:require [purnam.native]
            [goog.structs.AvlTree :as avl]
            [goog.object :as obj]))
             

;;
;; Derive Protocols
;;


;;
;; Store protocols
;;

(defprotocol INativeStore
  (insert! [store obj])
  (update! [store obj])
  (delete! [store obj]))

(defprotocol IIndexedStore
  (add-index! [store name index])
  (rem-index! [store name])
  (get-index  [store name]))

(defprotocol ITransactionalStore
  (transact! [store fn & args]))

(defprotocol IIndex
  (index! [idx obj])
  (unindex! [idx obj])
  (key-fn [idx]))

(defprotocol IScannableIndex
  (comp-fn [idx])
  (-scan   [idx f] [idx f start] [idx f start end] [idx f start end dir]))

;;
;; Native object store
;;

(defn- update-log [store op value]
  (let [id (.-txn-id store)]
    (.push (.-txn-log store) #js {:op op :value (js-copy value) :date (Date.) :id id})
    (set! (.-txn-id store) (inc id))))

(defn flush-log [

(deftype NativeStore [root indices listeners txn-log ^:mutable txn-id]
  ILookup
  (-lookup [store id]
    (-lookup root id))
  INativeStore
  (insert! [store obj]
    (assert (not (contains? root obj)) "Does not exist")
    (index! root obj)
    (doseq [idx indices]
      (index! idx obj))
    (update-log store :insert (js-copy obj))
    store)
  (update! [store obj]
    (if (not (contains? root obj))
      (insert! store obj)
      (do
        (reindex! root obj)
        (doseq [idx indices]
          (reindex! idx obj))
        (update-log store :update obj)))
    store)
  (delete! [store obj]
    (assert (contains? root obj) "Exists")
    (unindex! root obj)
    (doseq [idx indices]
      (unindex! root obj))
    (update-log store :delete obj)
    store)
  ITransactionalStore
  (transact! [store f & args]
    ;; With mutation tracking enabled?
    (apply f store args)))

(defn store [root-idx]
  (NativeStore. root-idx #js {} #js {} #js [] 1))
  
;;
;; Indexes
;; 


(deftype HashIndex [kf htable]
  IIndex
  (index! [idx obj]
    (obj/add htable (kf obj) obj))
  (unindex! [idx obj]
    (obj/remove htable (kf obj)))
  (key-fn [idx] kf))

(defn hash-index [key-fn]
  (HashIndex. key-fn #js {}))

(defn- scanner
  ([f]
     (fn [val] (f val) nil))
  ([f comp-fn end]
     (fn [val]
       (if (comp-fn val end)
         (do (f val) nil)
         true))))

(deftype AvlIndex [kf cf avl]
  IIndex
  (index! [idx obj]
    (.add avl obj))
  (unindex! [idx obj]
    (.remove avl obj))
  (key-fn [idx] kf)
  IScannableIndex  
  (comp-fn [idx] cf)
  (-scan [idx f]
    (.inOrderTraversal avl (scanner f)))
  (-scan [idx f start]
    (.inOrderTraversal avl (scanner f) start)))
    
(defn avl-index [key-fn comp-fn]
  (avl/AvlIndex. key-fn comp-fn (avl/AvlTree (comp key-fn comp-fn))))
  
;;
;; Read-only interface with consistency semantics
;; and cross references
;;

(defprotocol IReference
  (resolve-ref [ref]))

(deftype Reference [store iname id]
  IReference
  (resolve-ref [_] (get-in-idx store iname id)))

(deftype Entity [db ^long txnid ^object obj])

(extend-type Entity
  ILookup
  (-lookup
    ([e k]
       (aget (.-obj e) (name k)))
    ([e k not-found]
       (let [s (name k)]
         (if (goog.object.containsKey (.-obj e) s)
           (aget (.-obj e) s)
           not-found))))
  IDeref
  (-deref [e] (.-obj e)))

(def test (Entity. nil 1 #js {:test 2}))

(pr-str (:test test))
(pr-str (meta test))
