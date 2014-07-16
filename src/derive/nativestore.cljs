(ns derive.nativestore
  (:require [goog.object :as obj]
            [purnam.native]
            [purnam.native.functions :refer [js-lookup js-assoc js-dissoc js-merge js-map]]
            [derive.protocols :as p :refer [index! unindex! key-fn comparator-fn scan
                                            insert! delete! add-index! rem-index! get-index]]))
             
;;
;; Native object store
;;

;; Hash KV Index, meant to be for a root store index (unique keys)
;; - Merging upsert against existing if keyfn output matches
(deftype HashIndex [keyfn hashmap]
  ILookup
  (-lookup [idx val]
    (-lookup idx val nil))
  (-lookup [idx val not-found]
    (js-lookup (.-hashmap idx) val not-found))

  IFn
  (-invoke [idx k]
    (-lookup idx k))
  (-invoke [idx k not-found]
    (-lookup idx k not-found))

  ICounted
  (-count [idx] (.-length (obj/getValues (.-hashmap idx))))

  p/IIndex
  (key-fn [idx] (.-keyfn idx))
  (index! [idx obj]
    (let [key ((.-keyfn idx) obj)
          hashmap (.-hashmap idx)
          old (js-lookup hashmap key)]
      (js-assoc hashmap key (if old (js-merge old obj) obj))))
  (unindex! [idx obj]
    (let [key ((.-keyfn idx) obj)
          hashmap (.-hashmap idx)]
      (js-dissoc hashmap key obj))))

(defn root-index [keyfn]
  (HashIndex. keyfn #js {}))

;; KV index using binary search/insert/remove on array
;; - Always inserts new objects in sorted order
;; - Matches on object identity for unindex!
(deftype BinaryIndex [keyfn compfn arry]
  ILookup
  (-lookup [idx val]
    (-lookup idx val nil))
  (-lookup [idx val not-found]
    (let [compfn (.-compfn idx)
          keyfn (.-keyfn idx)
          arry (.-arry idx)
          index (goog.array.binarySearch arry val #(compfn %1 (keyfn %2)))]
      (if (>= index 0)
        (loop [end index]
          (if (= (compfn val (keyfn (aget arry end))) 0)
            (recur (inc end))
            (goog.array.slice arry index end)))
        not-found)))

  IFn
  (-invoke [idx k]
    (-lookup idx k))
  (-invoke [idx k not-found]
    (-lookup idx k not-found))

  p/IIndex
  (key-fn [idx] (.-keyfn idx))
  (index! [idx obj]
    (let [compfn (.-compfn idx)
          keyfn (.-keyfn idx)
          arry (.-arry idx)
          loc (goog.array.binarySearch arry obj #(compfn (keyfn %1) (keyfn %2)))]
      (if (>= loc 0)
        (goog.array.insertAt arry obj loc)
        (goog.array.insertAt arry obj (- (inc loc)))))
    idx)
  (unindex! [idx obj]
    (let [compfn (.-compfn idx)
          keyfn (.-keyfn idx)
          arry (.-arry idx)
          loc (goog.array.findIndex arry #(= obj %))]
      (assert (>= loc 0))
      (goog.array.removeAt arry loc))
    idx)

  p/IIndexedStore
  (comparator-fn [idx] (.-compfn idx))

  p/IScannable
  (scan [idx f]
    (let [keyfn (.-keyfn idx)
          compfn (.-compfn idx)
          arry (.-arry idx)
          len (.-length arry)]
      (loop [i 0]
        (when-not (>= i len)
          (f (aget arry i))
          (recur (inc i))))))
  (scan [idx f start]
    (let [keyfn (.-keyfn idx)
          compfn (.-compfn idx)
          arry (.-arry idx)
          len (.-length arry)
          head (goog.array.binarySearch arry start #(compfn %1 (keyfn %2)))
          head (if (>= head 0) head (- (inc head)))]
      (loop [i head]
        (when-not (>= i len)
          (f (aget arry i))
          (recur (inc i))))))
  (scan [idx f start end]
    (let [keyfn (.-keyfn idx)
          compfn (.-compfn idx)
          arry (.-arry idx)
          head (goog.array.binarySearch arry start #(compfn %1 (keyfn %2)))
          head (if (>= head 0) head (- (inc head)))
          tail (goog.array.binarySearch arry end #(compfn %1 (keyfn %2)))
          tail (if (>= tail 0) tail (- (inc tail)))]
      (loop [i head]
        (when-not (>= i tail)
          (f (aget arry i))
          (recur (inc i)))))))

(defn ordered-index [keyfn compfn]
  (BinaryIndex. keyfn compfn #js []))

;; Placeholder for a native, indexed, mutable/transactional store
(deftype NativeStore [root indices listeners txn-log ^:mutable txn-id]
  ILookup
  (-lookup [store val]
    (-lookup store val nil))
  (-lookup [store id not-found]
    (-lookup (.-root store) id not-found))

  ICounted
  (-count [store] (-count (.-root store)))

  IFn
  (-invoke [store k]
    (-lookup store k))
  (-invoke [store k not-found]
    (-lookup store k not-found))

  p/IStore
  (insert! [store obj]
    (let [key ((key-fn root) obj)
          root (.-root store)
          indices (obj/getValues (.-indices store))
          ilen (.-length indices)
          old (get root key)]
      (when old
        (loop [i 0]
          (when-not (>= i ilen)
            (unindex! (aget indices i) old)
            (recur (inc i)))))
      (index! root obj)
      (let [new (get root key)]
        (loop [i 0]
          (when-not (>= i ilen)
            (index! (aget indices i) new)
            (recur (inc i))))))
    store)
  (delete! [store id]
    (assert (contains? root id) "Exists")
    (let [obj (get root id)]
      (unindex! root obj)
      (doseq [idx indices]
        (unindex! idx obj)))
    store)

  p/IIndexedStore
  (add-index! [store iname index]
    (assert (not (get-index store iname)))
    (js-assoc indices iname index)
    store)
  (rem-index! [store iname]
    (assert (get-index store iname))
    (js-dissoc indices iname)
    store)
  (get-index [store iname]
    (js-lookup indices iname)))
    
;  ITransactionalStore
;  (transact! [store f & args]
;    ;; With mutation tracking enabled?
;    (apply f store args)))

(defn native-store [root-fn]
  (NativeStore. (root-index root-fn) #js {} #js {} #js [] 1))

(comment
  (def store (native-store #(aget % "id")))
  (p/add-index! store :name (ordered-index :name compare))
  (insert! store #js {:id 1 :name "Fred"})
  (insert! store #js {:id 2 :name "Zoe"})
  (insert! store #js {:id 3 :name "Apple"})
  (insert! store #js {:id 4 :name "Flora"})
  (insert! store #js {:id 5 :name "Flora"})
  (println "Get by ID" (get store 1))
  (println "Get by index" (-> (get-index store :name) (get "Flora"))))


;;
;; Read-only interface with consistency semantics
;; and cross references
;;
(comment
  
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
)
