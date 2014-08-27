(ns derive.deps
  (:require [clojure.set :as s]
            [clojure.core.reducers :as r])
  (:refer-clojure :exclude [reset!]))

;; Dependency Tracking
;; ============================

(def ^{:doc "Bound dependency trackers are informed of encountered dependencies"
       :dynamic true}
  *tracker* nil)


(defprotocol IDependencySet
  "An immutable set of dependencies. Passed to dependency trackers during queries via record-dependency"
  (merge-deps [this deps]
    "Merge two dependencies")
  (match-deps [this set]
    "Match the current set to an incoming set - intersection semantics"))

(defprotocol IDependencySource
  "This interface is implemented by databases and derive methods to allow callers to
   subscribe to subset of changes identified by the provided dependency set"
  (subscribe! [this listener] [this listener deps]
    "Call tracker method when deps match a change operation")
  (unsubscribe! [this listener] [this listener deps]
    "Call tracker method when deps match a change operation")
  (empty-deps [this]))

(defprotocol IDependencyTracker
  "Implemented by function and component caches"
  (depends! [this store deps]
    "Dependency sources call this method if a tracker is bound in the current
     context with dependencies that are encountered during query processing.")
  (dependencies [this]
    "The current dependencies encountered by this tracker"))

(defprotocol IDependencyCache
  "A utility API for tracking dependencies, allows us to provide more
   advanced options for assembling tracker policies"
  (reset! [this] "Clear cache")
  (get-value [this params]
    "Returns cached value if exists for params")
  (add-value! [this params value store deps]
    "Informs store that a particular params yeilds value given current store + deps")
  (rem-value! [this params])
  (invalidate! [this store deps]))

;; - IDependencyTracker
;; - IDependencyCache

;; - InvalidationListener is setup in current context, possibly with options
;; - Sources cache subscribers and call it with options, args on invalidation
;; - A call to a source that calls other sources creates an invalidation chain
;; - Updates can be suspended with 

;;
;; Default dependency set
;;

(extend-protocol IDependencySet
  PersistentHashSet
  (merge-deps [this deps]
    (s/union this deps))
  (match-deps [this deps]
    (or (nil? deps) (not (empty? (s/intersection this deps)))))

  PersistentTreeSet
  (merge-deps [this deps]
    (s/union this deps))
  (match-deps [this deps]
    (or (nil? deps) (not (empty? (s/intersection this deps))))))

;;
;; Simple tracker
;;

(deftype DefaultCache [^:mutable cache]
  IDependencyCache
  (get-value [_ params] (get cache params))
  (add-value! [this params value store deps]
    (set! cache (assoc cache params [value store deps]))
    this)
  (rem-value! [this params]
    (set! cache (dissoc cache params)))
  (invalidate! [this store deps]
    (->> (reduce (fn [c [params [value vstore vdeps]]]
                   (if (and (= vstore store)
                            (or (nil? deps) (nil? vdeps)
                                (match-deps vdeps deps)))
                     (dissoc! c params)
                     c))
                 (transient cache)
                 cache)
         persistent!
         (set! cache)))
  (reset! [this] (set! cache {}) this))

(defn default-cache []
  (DefaultCache. {}))
    
(deftype DefaultTracker [^:mutable deps]
  IDependencyTracker
  (depends! [this store new-deps]
    #_(println "depends!: " (.-deps deps) (or (.-deps new-deps) new-deps) "\n")
    (set! deps (merge-deps deps new-deps)))

  (dependencies [this] deps))

(defn default-tracker 
  ([] (DefaultTracker. #{}))
  ([deps] (DefaultTracker. deps)))


;; Utilities for stores

(defn tracking? [] (not (nil? *tracker*)))

(defn inform-tracker
  ([store deps]
     (when (tracking?)
       (inform-tracker *tracker* store deps)))
  ([tracker store deps]
     #_(println "Informing tracker: " (or (.-deps deps) deps) " t? " *tracker* "\n")
     (depends! tracker store deps)))


;;
;; Derive Function
;;

(deftype DeriveFn [fname dfn lfn ^:mutable subscriptions ^:mutable cache ^:mutable listeners]
  Fn
  IFn
  (-invoke [this]
    (dfn this))
  (-invoke [this a]
    (dfn this a))
  (-invoke [this a b]
    (dfn this a b))
  (-invoke [this a b c]
    (dfn this a b c))
  (-invoke [this a b c d]
    (dfn this a b c d))
  (-invoke [this a b c d e]
    (dfn this a b c d e))
  (-invoke [this a b c d e f]
    (dfn this a b c d e f))
  (-invoke [this a b c d e f g]
    (dfn this a b c d e f g))
  (-invoke [this a b c d e f g h]
    (dfn this a b c d e f g h))
  (-invoke [this a b c d e f g h i]
    (dfn this a b c d e f g h i))
  (-invoke [this a b c d e f g h i j]
    (dfn this a b c d e f g h i j))
  (-invoke [this a b c d e f g h i j k]
    (dfn this a b c d e f g h i j k))
  (-invoke [this a b c d e f g h i j k l]
    (dfn this a b c d e f g h i j k l))
  (-invoke [this a b c d e f g h i j k l m]
    (dfn this a b c d e f g h i j k l m))
  (-invoke [this a b c d e f g h i j k l m n]
    (dfn this a b c d e f g h i j k l m n))
  (-invoke [this a b c d e f g h i j k l m n o]
    (dfn this a b c d e f g h i j k l m n o))
  (-invoke [this a b c d e f g h i j k l m n o p]
    (dfn this a b c d e f g h i j k l m n o p))
  (-invoke [this a b c d e f g h i j k l m n o p q]
    (dfn this a b c d e f g h i j k l m n o p q))
  (-invoke [this a b c d e f g h i j k l m n o p q r]
    (dfn this a b c d e f g h i j k l m n o p q r))
  (-invoke [this a b c d e f g h i j k l m n o p q r s]
    (dfn this a b c d e f g h i j k l m n o p q r s))
  (-invoke [this a b c d e f g h i j k l m n o p q r s t]
    (dfn this a b c d e f g h i j k l m n o p q r s t))
  (-invoke [this a b c d e f g h i j k l m n o p q r s t rest]
    (apply dfn this a b c d e f g h i j k l m n o p q r s t rest))

  IDependencySource
  (subscribe! [this listener]
    (set! listeners (update-in listeners [nil] (fnil conj #{}) listener)))
  (subscribe! [this listener deps]
    (set! listeners (update-in listeners [deps] (fnil conj #{}) listener)))
  (unsubscribe! [this listener]
    (set! listeners (update-in listeners [nil] disj listener)))
  (unsubscribe! [this listener deps]
    (set! listeners (update-in listeners [deps] disj listener)))
  (empty-deps [this] #{}))
 

(defn empty-derive-fn [& args]
  (assert false "Uninitialized derive fn"))

(defn create-derive-fn [fname]
  (DeriveFn. fname empty-derive-fn empty-derive-fn
             #{} (derive.deps/default-cache) {}))
  

(defn ensure-subscription 
  "Ensure we're subscribed to stores we encounter"
  [derive store]
  (when-not ((.-subscriptions derive) store)
    (subscribe! store (.-lfn derive))
    (set! (.-subscriptions derive) (conj (.-subscriptions derive) store))))

(defn release-subscriptions [derive]
  (doall (map #(unsubscribe! % (.-lfn derive)) (.-subscriptions derive))))
    
(defn derive-value
  "Handle deps and cache values from normal calls"
  [derive params]
  (get-value (.-cache derive) params))

(defn update-derive
  [derive params value store deps parent-tracker]
  (add-value! (.-cache derive) params value store deps)
  (when parent-tracker (depends! parent-tracker store deps))
  value)

(defn notify-listeners [store deps]
  (let [listeners (.-listeners store)]
    (->> (keys listeners)
         (filter #(or (nil? %) (match-deps % deps))) ;; cheap consolidation
         (map (fn [k] (doseq [l (get listeners k)] (l store deps))))
         doall)))

(defn derive-listener
  "Helper. Handle source listener events"
  [derive store deps]
  #_(println "Derive received: " (.-deps deps))
  (let [cache (.-cache derive)]
    (invalidate! cache store deps)
    (notify-listeners derive deps)))
    
(defn invalidate-all-listeners
  "Helper. Inform upstream when we're redefined"
  [derive]
  (doall
   (map (fn [f] (f derive nil))
        (flatten (vals (.-listeners derive))))))


  
