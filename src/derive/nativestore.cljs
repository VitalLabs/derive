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
  (-transact! [store f args]))

;;
;; Native Dependencies
;; ============================

;; A dependency representation is:
;;
;; #js { _root: #js [<id_i> ...] 
;;       <idx_name>: #js [start end] }
;;
;; The root is a sorted list of object IDs (reference traversal or direct lookups)
;; The remaining index lookups maintain value ranges traversed
;; These become set intersection and range overlap calculations when testing
;; for the impact of a transaction
;;
;; The left side dependency is mutated and returned by all operations
;;

(defn- sorted-insert!
  "Mutates r1.  Keep list of merged IDs in sorted order"
  [r1 r2]
  (goog.array.forEach r2 (fn [v i a] (goog.array.binaryInsert r1 v))))

(defn- merge-range!
  "Mutates r1. The updated range becomes the union of the two ranges"
  [compfn range1 range2]
  (let [r1s (aget range1 0)
        r1e (aget range1 1)
        r2s (aget range2 0)
        r2e (aget range2 1)]
    (when (< (compfn r2s r1s) 0)
      (aset range1 0 r2s))
    (when (> (compfn r2e r1e) 0)
      (aset range1 1 r2e))))

(defn- merge-index!
  "Merge the index range or root set"
  [nset idx range1 range2]
  (if (nil? idx) ; root?
    (sorted-insert! range1 range2)
    (merge-range! (comparator-fn idx) range1 range2)))


(defn- intersect?
  "Do two sorted sets of integers intersect?"
  [this other]
  (let [tlen (.-length this)
        olen (.-length other)]
    (loop [i 0 j 0]
      (if (or (= i tlen) (= j olen))
        false
        (let [v1 (aget this i)
              v2 (aget other j)]
          (cond (= v1 v2) true
                (> (compare v1 v2) 0) (recur i (inc j))
                :default (recur (inc i) j)))))))

(defn- overlap?
  "Does the interval of other overlap this?"
  [compfn range1 range2]
  (let [r1s (aget range1 0)
        r1e (aget range1 1)
        r2s (aget range2 0)
        r2e (aget range2 1)]
    (not (or (> (compfn r2s r1e) 0)
             (< (compfn r2e r1s) 0)))))
  
(defn- match-index?
  [nset idx this-range other-range]
  (if (nil? idx) ; root?
    (intersect? this-range other-range)
    (overlap? (comparator-fn idx) this-range other-range)))

(deftype NativeDependencySet [store deps]
  IDependencySet
  (merge-deps [this other]
    (goog.object.forEach
     other (fn [v k] (merge-index! this (get-index store k) (aget deps k) v)))
    this)
  (match-deps [this other]
    (goog.object.some 
     other (fn [v k] (match-index? this (get-index store k) (aget deps k) v)))
    this))

(defn make-dependencies
  ([store] (NativeDependencySet. store #js {}))
  ([store init] (NativeDependencySet. store init)))
  
;;
;; Instance protocols
;; ============================

(def ^{:doc "Inside a transaction?"
       :dynamic true}
  *transaction* nil)

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

  IEquiv
  (-equiv [ref other]
    (and (= store (.-store other))
         (= id (.-id other))))
  
  IReference
  (resolve-ref [_] (get store id))
  (reference-id [_] id)
  (reference-db [_] store))

;; Mutation can only be done on Natives in
;; a transaction or on copies of Natives generated
;; via assoc, etc. or store/clone

(defprotocol IReadOnly
  (-read-only? [_]))

(deftype Native [^:mutable __ro]
  IReadOnly
  (-read-only? [_] __ro)

  IPrintWithWriter
  (-pr-writer [native writer opts]
    (-write writer "#native ")
    (print-map
     (->> (js-keys native)
          (keep (fn [k]
                  (let [v (aget native k)
                        k (keyword k)]
                    (when-not (or (fn? v)
                                  (#{:cljs$lang$protocol_mask$partition0$ :cljs$lang$protocol_mask$partition1$ :__ro :derive$nativestore$IReadOnly$} k))
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
    (when (and (-read-only? native) (not *transaction*))
      (throw (js/Error. "Cannot mutate store values outside transact!: ")))
    (aset native (if (keyword? k) (name k) k) v)
    native)

  ITransientCollection
  (-conj! [native [k v]]
    (when (and (-read-only? native) (not *transaction*))
      (throw (js/Error. "Cannot mutate store values outside transact!: ")))
    (aset native (if (keyword? k) (name k) k) v)
    native)
  
  ITransientMap
  (-dissoc! [native k]
    (when (and (-read-only? native) (not *transaction*))
      (throw (js/Error. "Cannot mutate store values outside transact!: ")))
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
  (let [native (Native. false)]
    (goog.object.forEach jsobj (fn [v k] (aset native k v)))
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
(deftype NativeStore [root indices tx-listeners ^:mutable listeners]
  ILookup
  (-lookup [store id]
    (-lookup store id nil))
  (-lookup [store id not-found]
    (deps/inform-tracker store #{id})
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
    (let [obj (if (= (type obj) Native)
                (do (set! (.-__ro obj) true) obj)
                (let [native (to-native obj)]
                  (set! (.-__ro native) true)
                  native))]
      (let [key ((key-fn root) obj)
            _ (assert key "Must have an ID field")
            names (js-keys indices)
            old (get root key)
            oldref (when old (js-copy old))]
        (when old
          (doseq [name names]
            (let [idx (aget indices name)]
              (when-not (nil? ((key-fn idx) old))
                (unindex! idx old)))))
        (index! root obj) ;; merging upsert
        (let [new (get root key)]
          (doseq [name names]
            (let [idx (aget indices name)]
              (when-not (nil? ((key-fn idx) new))
                (index! idx new))))
          (when *transaction*
            (.push *transaction* #js [:insert oldref new]))
          (deps/notify-listeners store #{key})
          (-notify-watches store oldref #js [:insert new]))))
    store)

  (delete! [store id]
    (assert (contains? root id) "Exists")
    (let [old (get root id)]
      (doseq [name (js-keys indices)]
        (let [idx (aget indices name)]
          (when ((key-fn idx) old)
            (unindex! idx old))))
      (unindex! root old)
      (when *transaction*
        (.push *transaction* #js [:delete old]))
      (-notify-watches store old #js [:delete])
      (deps/notify-listeners store #{((key-fn root) old)}))
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
    (doseq [name (js-keys tx-listeners)]
      (let [listener (get tx-listeners name)]
        (listener oldval newval)))
    store)
  (-add-watch [store key f]
    (js-assoc tx-listeners key f)
    store)
  (-remove-watch [store key]
    (js-dissoc tx-listeners key)
    store)
  
  ITransactionalStore
  (-transact! [store f args]
    (binding [*transaction* #js []]
      (apply f store args))
    store)

  deps/IDependencySource
  (subscribe! [this listener]
    (set! listeners (update-in listeners [nil] (fnil conj #{}) listener)))
  (subscribe! [this listener deps]
    (set! listeners (update-in listeners [deps] (fnil conj #{}) listener)))
  (unsubscribe! [this listener]
    (set! listeners (update-in listeners [nil] disj listener)))
  (unsubscribe! [this listener deps]
    (set! listeners (update-in listeners [deps] disj listener))))
    

(defn native-store []
  (NativeStore. (root-index) #js {} #js {} {}))

(defn transact! [store f & args]
  (-transact! store f args))

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

(defn ensure-index
  ([store iname key comp]
     (when-not (get-index store iname)
       (add-index! store iname (ordered-index (field-key key) comp))))
  ([store iname key-or-idx]
     (if (or (keyword? key-or-idx) (symbol? key-or-idx))
       (ensure-index store iname key-or-idx compare)
       (when-not (get-index store iname)
         (add-index! store iname key-or-idx))))) ;; key is an index

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
 


