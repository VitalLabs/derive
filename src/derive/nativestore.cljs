(ns derive.nativestore
  (:require [goog.object :as obj]
            [derive.deps :as deps]
            [purnam.native.functions :refer [js-lookup js-assoc js-dissoc
                                             js-merge js-map js-copy]]))

;;
;; Store protocols
;; ============================

;; + ILookup
(defprotocol IStore
  (insert! [store obj])  ;; shallow merge upsert of native objects
  (delete! [store id])) ;; delete, only need primary ID in submitted object

;; CompFn(KeyFn(obj)) -> value, obj
(defprotocol IIndex
  (key-fn [idx])
  (index! [idx obj])
  (unindex! [idx obj]))

(defprotocol ISortedIndex
  (comparator-fn [idx]))

(defprotocol IScannable
  (-get-cursor [idx] [idx start] [idx start end]))

(defprotocol IIndexedStore
  (add-index! [store name index])
  (rem-index! [store name])
  (get-index  [store name]))

(defprotocol ITransactionalStore
  (transact! [store fn args]))

;;
;; Instance protocols
;; ============================

(def ^{:doc "Inside a transaction?"}
  *transaction* nil)

(def ^{:doc "The dependency tracker context to notify of object operations"}
  *tracker* nil)

;; A reference wraps a lookup into a store
;; Objects implementing ILookup can test for a
;; IReference and dereference it.

(defprotocol IReference
  (resolve-ref [ref])
  (reference-id [ref])
  (reference-db [ref]))

(deftype NativeReference [store id]
  IPrintWithWriter
  (-pr-writer [native writer opts]
    (-write writer (str "#ref [" id "]")))

  IReference
  (resolve-ref [_] (store id))
  (reference-id [_] id)
  (reference-db [_] store))

;; Mutation can only be done on Natives in
;; a transaction or on copies of Natives generated
;; via assoc, etc. or store/clone

(deftype Native []
  IPrintWithWriter
  (-pr-writer [native writer opts]
    (-write writer "#native ")
    (print-map
     (->> (js-keys native)
          (keep (fn [k]
                  (let [v (aget native k)
                        k (keyword k)]
                    (when-not (or (fn? v)
                                  (#{:cljs$lang$protocol_mask$partition0$ :cljs$lang$protocol_mask$partition1$} k))
                      [k v])))))
     pr-writer writer opts))

  ICounted
  (-count [native]
    (.length (js-keys native)))

  ILookup
  (-lookup [native k]
    (let [key (if (keyword? k) (name k) k)
          res (aget native key)]
      (if (satisfies? IReference res)
        (resolve-ref res)
        res)))
  (-lookup [native k not-found]
    (let [key (if (keyword? k) (name k) k)
          res (aget native key)]
      (cond (nil? res) not-found
            (satisfies? IReference res) (resolve-ref res)
            :default res)))

  ITransientAssociative
  (-assoc! [native k v]
    (aset native (if (keyword? k) (name k) k) v)
    native)

  ITransientCollection
  (-conj! [native [k v]]
    (aset native (if (keyword? k) (name k) k) v)
    native)
  
  ITransientMap
  (-dissoc! [native k]
    (cljs.core/js-delete native (if (keyword? k) (name k) k))
    native)

  IAssociative
  (-assoc [native k v]
    (let [new (goog.object.clone native)]
      (aset new (if (keyword? k) (name k) k) v)
      new))
  
  IMap
  (-dissoc [native k]
    (let [new (goog.object.clone native)]
      (cljs.core/js-delete new (if (keyword? k) (name k) k))
      new))
    
  ICollection
  (-conj [native [k v]]
    (let [new (goog.object.clone native)]
      (aset new (if (keyword? k) (name k) k) v)
      new)))

(defn to-native
  "Copying version of to-native"
  [jsobj]
  (let [native (Native.)]
    (goog.object.forEach jsobj (fn [v k] (assoc! native k v)))
    native))

(comment
  (defn fast-to-native
    "Promotion version of to-native, doesn't work yet"
    [jsobj]
    (set! (.-constructor jsobj) Native)
    ;; TODO: Copy impls?
    ))

;;
;; Native object store
;; ============================

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

  IIndex
  (key-fn [idx] keyfn)
  (index! [idx obj]
    (let [key (keyfn obj)
          old (js-lookup hashmap key)]
      (js-assoc hashmap key (if old (upsert-merge old obj) obj))))
  (unindex! [idx obj]
    (let [key (keyfn obj)]
      (js-dissoc hashmap key obj))))

(defn root-index []
  (HashIndex. #(aget % "id") #js {}))

;; Return a cursor for walking a range of the index
(deftype Cursor [idx start end ^:mutable valid?]
  IReduce
  (-reduce [this f]
    (-reduce this f (f)))
  (-reduce [this f init]
    (let [a (.-arry idx)]
      (loop [i start ret init]
        (if (< i end)
          (recur (inc i) (f ret (aget a i)))
          ret)))))

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

  IIndex
  (key-fn [idx] keyfn)
  (index! [idx obj]
    (let [loc (goog.array.binarySearch arry obj #(compfn (keyfn %1) (keyfn %2)))]
      (if (>= loc 0)
        (goog.array.insertAt arry obj loc)
        (goog.array.insertAt arry obj (- (inc loc)))))
    idx)
  (unindex! [idx obj]
    (let [loc (goog.array.findIndex arry #(= obj %))]
      (when (>= loc 0)
        (goog.array.removeAt arry loc)))
    idx)

  IIndexedStore
  (comparator-fn [idx] compfn)

  IScannable
  (-get-cursor [idx]
    (Cursor. idx 0 (alength (.-arry idx)) true))
  (-get-cursor [idx start]
    (let [head (goog.array.binarySearch arry start #(compfn %1 (keyfn %2)))
          head (if (>= head 0) head (- (inc head)))]
      (Cursor. idx start (alength (.-arry idx)) true)))
  (-get-cursor [idx start end]
    (let [head (goog.array.binarySearch arry start #(compfn %1 (keyfn %2)))
          head (if (>= head 0) head (- (inc head)))
          tail (goog.array.binarySearch arry end #(compfn %1 (keyfn %2)))
          tail (if (>= tail 0) tail (- (inc tail)))]
      (Cursor. idx head tail true))))

(defn ordered-index [keyfn compfn]
  (BinaryIndex. keyfn compfn #js []))

;; A native, indexed, mutable/transactional store
;; - Always performs a merging upsert
;; - Secondary index doesn't index objects for key-fn -> nil
(deftype NativeStore [root indices listeners]
  ILookup
  (-lookup [store id]
    (-lookup store id nil))
  (-lookup [store id not-found]
    (-lookup root id not-found))

  ICounted
  (-count [store] (-count root))

  IFn
  (-invoke [store k]
    (-lookup store k nil))
  (-invoke [store k not-found]
    (-lookup store k not-found))

  IStore
  (insert! [store obj]
    (let [key ((key-fn root) obj)
          names (js-keys indices)
          old (get root key)
          oldref (js-copy old)]
      (when old
        (doseq [name names]
          (let [idx (aget indices name)]
            (when-not (nil? ((key-fn idx) old))
              (unindex! idx old)))))
      (index! root obj)
      (let [new (get root key)]
        (doseq [name names]
          (let [idx (aget indices name)]
            (when-not (nil? ((key-fn idx) new))
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

  IIndexedStore
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
(defn native-store []
  (NativeStore. (root-index) #js {} #js {}))

;;
;; External interface
;;

(defn fetch 
  ([store key]
     (get store key))
  ([store index key]
     (-> (get-index store index) (get key))))

(defn cursor
  "Walk the entire store, or an index"
  ([store]
     (apply -get-cursor store))
  ([store index & args]
     (apply -get-cursor (get-index store index) args)))

(defn field-key [field]
  (let [f (name field)]
    (fn [obj]
      (aget obj f))))

(defn type-field-key [type field]
  (let [t (name type)
        f (name field)]
    (fn [obj]
      (when (= t (aget obj "type"))
        (aget obj f)))))

(comment
  (def store (native-store))
  (add-index! store :name (ordered-index (field-key :name) compare))
  (insert! store #js {:id 1 :type "user" :name "Fred"})
  (insert! store #js {:id 2 :type "user" :name "Zoe"})
  (insert! store #js {:id 3 :type "user" :name "Apple"})
  (insert! store #js {:id 4 :type "user" :name "Flora"})
  (insert! store #js {:id 5 :type "user" :name "Flora"})
  (insert! store #js {:id 6 :type "tracker" :measure 2700})
  (println "Get by ID" (get store 1))
  (println "Get by index" (-> (get-index store :name) (get "Flora")))
  (println (js->clj (r/reduce (cursor store :name) (d/map :id))))) ;; object #6 is not indexed!
 


