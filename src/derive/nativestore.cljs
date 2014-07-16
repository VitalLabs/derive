(ns derive.nativestore
  (:require [goog.object :as obj]
            [purnam.native]
            [purnam.native.functions :refer [js-lookup js-assoc js-dissoc js-merge js-map
                                             js-copy]]
            [derive.protocols :as p :refer [index! unindex! key-fn comparator-fn scan
                                            insert! delete! add-index! rem-index! get-index]]))
             
;;
;; Native object store
;;

(defn upsert-merge
  ([o1 o2]
     (doseq [k (js-keys o2)]
       (if-not (nil? (aget o2 k))
         (aset o1 k (aget o2 k))
         (js-delete o1 k)))
     o1)
  ([o1 o2 & more]
     (apply upsert-merge (upsert-merge o1 o2) more)))


;; Hash KV Index, meant to be for a root store index (unique keys)
;; - Merging upsert against existing if keyfn output matches
;; - Nil values in provided object deletes keys
;; - Original object maintains identity
(deftype HashIndex [keyfn hashmap]
  ILookup
  (-lookup [idx val]
    (-lookup idx val nil))
  (-lookup [idx val not-found]
    (js-lookup hashmap val not-found))

  IFn
  (-invoke [idx k]
    (-lookup idx k))
  (-invoke [idx k not-found]
    (-lookup idx k not-found))

  ICounted
  (-count [idx] (alength (js-keys hashmap)))

  p/IIndex
  (key-fn [idx] keyfn)
  (index! [idx obj]
    (let [key (keyfn obj)
          old (js-lookup hashmap key)]
      (js-assoc hashmap key (if old (upsert-merge old obj) obj))))
  (unindex! [idx obj]
    (let [key (keyfn obj)]
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
    (let [index (goog.array.binarySearch arry val #(compfn %1 (keyfn %2)))]
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
  (key-fn [idx] keyfn)
  (index! [idx obj]
    (let [loc (goog.array.binarySearch arry obj #(compfn (keyfn %1) (keyfn %2)))]
      (if (>= loc 0)
        (goog.array.insertAt arry obj loc)
        (goog.array.insertAt arry obj (- (inc loc)))))
    idx)
  (unindex! [idx obj]
    (let [loc (goog.array.findIndex arry #(= obj %))]
      (assert (>= loc 0))
      (goog.array.removeAt arry loc))
    idx)

  p/IIndexedStore
  (comparator-fn [idx] compfn)

  p/IScannable
  (scan [idx f]
    (dotimes [i (alength arry)]
      (f (aget arry i))))
  (scan [idx f start]
    (let [head (goog.array.binarySearch arry start #(compfn %1 (keyfn %2)))
          head (if (>= head 0) head (- (inc head)))]
      (dotimes [i (- (alength arry) head)]
        (f (aget arry (+ i head))))))
  (scan [idx f start end]
    (let [head (goog.array.binarySearch arry start #(compfn %1 (keyfn %2)))
          head (if (>= head 0) head (- (inc head)))
          tail (goog.array.binarySearch arry end #(compfn %1 (keyfn %2)))
          tail (if (>= tail 0) tail (- (inc tail)))]
      (loop [i head]
        (when-not (>= i tail)
          (f (aget arry i))
          (recur (inc i)))))))

(defn ordered-index [keyfn compfn]
  (BinaryIndex. keyfn compfn #js []))


;; A native, indexed, mutable/transactional store
;; - Always performs a merging upsert
;; - Secondary index doesn't index objects for key-fn -> nil
(deftype NativeStore [root indices listeners]
  ILookup
  (-lookup [store val]
    (-lookup store val nil))
  (-lookup [store id not-found]
    (-lookup root id not-found))

  ICounted
  (-count [store] (-count root))

  IFn
  (-invoke [store k]
    (-lookup store k))
  (-invoke [store k not-found]
    (-lookup store k not-found))

  p/IStore
  (insert! [store obj]
    (let [key ((key-fn root) obj)
          names (js-keys indices)
          old (get root key)
          oldref (js-copy old)]
      (when old
        (doseq [name names]
          (let [idx (aget indices name)]
            (when ((key-fn idx) old)
              (unindex! idx old)))))
      (index! root obj)
      (let [new (get root key)]
        (doseq [name names]
          (let [idx (aget indices name)]
            (when ((key-fn idx) new)
              (index! idx new))))
        (-notify-watches store oldref new)))
    store)
  (delete! [store id]
    (assert (contains? root id) "Exists")
    (let [old (get root id)]
      (doseq [name (js-keys indices)]
        (let [idx (aget indices name)]
          (when ((key-fn idx) old)
            (unindex! idx old))))
      (unindex! root old))
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
    (js-lookup indices iname))

  IWatchable
  (-notify-watches [store oldval newval]
    (doseq [name (js-keys listeners)]
      (let [listener (get listeners name)]
        (listener oldval newval)))
    store)
  (-add-watch [store key f]
    (js-assoc listeners key f)
    store)
  (-remove-watch [store key]
    (js-dissoc listeners key)
    store))
  

    
;  ITransactionalStore
;  (transact! [store f & args]
;    ;; With mutation tracking enabled?
;    (apply f store args)))
(defn native-store [root-fn]
  (NativeStore. (root-index root-fn) #js {} #js {}))

(defn index-lookup [store index value]
  (-> (get-index store index) (get value)))

(comment
  (def store (native-store #(aget % "id")))
  (p/add-index! store :name (ordered-index :name compare))
  (insert! store #js {:id 1 :type "user" :name "Fred"})
  (insert! store #js {:id 2 :type "user" :name "Zoe"})
  (insert! store #js {:id 3 :type "user" :name "Apple"})
  (insert! store #js {:id 4 :type "user" :name "Flora"})
  (insert! store #js {:id 5 :type "user" :name "Flora"})
  (insert! store #js {:id 6 :type "tracker" :measure 2700})
  (println "Get by ID" (get store 1))
  (println "Get by index" (-> (get-index store :name) (get "Flora")))
  (let [a (array)]
    (scan (get-index store :name) #(.push a (:id %)))
    (println (js->clj a)))) ;; object #6 is not indexed!


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
