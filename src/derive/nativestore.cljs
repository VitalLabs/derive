(ns derive.nativestore
  (:require [purnam.native]
            [purnam.native.functions :refer [js-lookup js-assoc js-dissoc js-merge]]
            [derive.protocols :as p]
            [goog.object :as obj]))
             
;;
;; Native object store
;;

(deftype HashIndex [keyfn hashmap]
  ILookup
  (-lookup [_ val]
    (js-lookup hashmap val))
  p/IIndex
  (key-fn [_] keyfn)
  (index! [_ obj]
    (let [old (js-lookup hashmap (keyfn obj))]
      (js-assoc hashmap (keyfn obj) (if old (js-merge old obj) obj))))
  (unindex! [_ obj]
    (js-dissoc hashmap (keyfn obj))))

(defn root-index [keyfn]
  (HashIndex. keyfn #js {}))

(deftype BadSortedIndex [keyfn compfn hashmap]
  ILookup
  (-lookup [_ val]
    (js-lookup hashmap val))
  p/IIndex
  (key-fn [_] keyfn)
  (index! [_ obj]
    (let [old-set (js-lookup hashmap (keyfn obj))]
      (if old-set
        (.push old-set obj)
        (js-assoc hashmap (keyfn obj) #js [obj]))))
  (unindex! [_ obj]
    (let [set (js-lookup hashmap (keyfn obj))]
      (goog.array.remove set obj)))
  p/IIndexedStore
  (comparator-fn [idx] compfn)
  p/IScannable
  (scan [idx f]
    (sort-by keyfn compfn (obj/getValues hashmap)))
  (scan [idx f start]
    (->> (obj/getValues hashmap)
         (remove #(not (compfn start (keyfn %))))
         (sort-by keyfn compfn)
         (map f)
         dorun))
  (scan [idx f start end]
    (->> (obj/getValues hashmap)
         (remove #(not (compfn start (keyfn %))))
         (filter #(not (compfn (keyfn %) end)))
         (sort-by keyfn compfn)
         (map f)
         dorun))
  (scan [idx f start end dir]
    (->> (obj/getValues hashmap)
         (remove #(not (compfn start (keyfn %))))
         (filter #(not (compfn (keyfn %) end)))
         (sort-by keyfn compfn)
         (map f)
         dorun)))

(defn sorted-index [keyfn compfn]
  (BadSortedIndex. keyfn compfn #js {}))
(comment  
(deftype NativeStore [root indices listeners txn-log ^:mutable txn-id]
  ILookup
  (-lookup [store id]
    (-lookup root id))
  IStore
  (insert! [store obj]
    (let [old (get root ((key-fn root) obj))]
      (doseq [idx indices]
        (unindex! idx old)))
    (index! root obj)
    (let [new (get root ((key-fn root) obj))]
      
    (doseq [idx indices]
      (index! idx obj))
    store)
  (delete! [store obj]
    (assert (contains? root ((key-fn root) obj)) "Exists")
    (let [ref (get root ((key-fn root) obj))]
      (unindex! root ref)
      (doseq [idx indices]
        (unindex! idx ref)))
    store))
  IIndexedStore
  (add-index! [store iname obj index]
    (assert (not (get-index store iname)))
    (js-assoc indices iname index)
    store)
  (rem-index! [store iname]
    (assert (get-index store iname))
    (js-dissoc indices iname))
  (get-index [store iname]
    (js-lookup indices iname)))
    
;  ITransactionalStore
;  (transact! [store f & args]
;    ;; With mutation tracking enabled?
;    (apply f store args)))

(defn native-store [root-fn]
  (NativeStore. (root-index root-fn) #js {} #js {} #js [] 1))

(comment
  (def test-store (native-store #(aget % "id")))
  (p/add-index! test-store))

;;
;; Indexes
;; 

(comment
    
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
))
