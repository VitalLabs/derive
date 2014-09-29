(ns derive.core
  (:require [clojure.set :as s])
  (:refer-clojure :exclude [reset!]))


;; ============================
;; Dependency Tracking
;; ============================

(def ^{:doc "Dependency tracker that is informed of encountered dependencies"
       :dynamic true}
  *tracker* nil)

(def ^{:doc "Whether a dependency tracker should shadow lower level, used to
             implement stores"
       :dynamic true}
  *shadow* nil)

(defprotocol IDependencySet
  "An immutable set of dependencies. Passed to dependency trackers
  during queries via record-dependency."
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
  (add-value! [this params value dependency-map]
    "Informs store that a particular params yeilds value given current store + deps")
  (rem-value! [this params])
  (invalidate! [this store deps]))


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

(defn- matching-dep? [dmap store deps]
  (some (fn [[vstore vdeps]]
          (and (= store vstore)
               (or (nil? deps) (nil? vdeps)
                   (match-deps vdeps deps))))
        dmap))

(deftype DefaultCache [^:mutable cache]
  IDependencyCache
  (get-value [_ params] (get cache params))
  (add-value! [this params value dmap]
    (set! cache (assoc cache params [value dmap]))
    this)
  (rem-value! [this params]
    (set! cache (dissoc cache params)))
  (invalidate! [this store deps]
    (let [[c invalidated]
          (reduce (fn [[c i] [params [value dmap]]]
                    (if (matching-dep? dmap store deps)
                      [(dissoc! c params) (conj i params)]
                      [c i]))
                  [(transient cache) []]
                  cache)]
      (set! cache (persistent! c))
      (set invalidated)))
  (reset! [this] (set! cache {}) this))

(defn default-cache []
  (DefaultCache. {}))
    
(deftype DefaultTracker [^:mutable dmap]
  IDependencyTracker
  (depends! [this store new-deps]
    #_(println "depends!: " dmap "\n")
    (set! dmap (update-in dmap [store] (fnil merge-deps (empty-deps store)) new-deps)))

  (dependencies [this] dmap))

(defn default-tracker 
  ([] (DefaultTracker. {}))
  ([dmap] (DefaultTracker. dmap)))

;; Utilities for stores

(defn tracking? [] (not (nil? *tracker*)))

(defn inform-tracker
  ([store args]
     (when (tracking?)
       (inform-tracker *tracker* store args)))
  ([tracker store args]
     #_(println "Informing tracker: " args " t? " *tracker* "\n")
     (depends! tracker store (if (set? args) args #{args}))))


;;
;; Derive Function
;;

(deftype DeriveFn [fname dfn lfn ^:mutable subscriptions ^:mutable cache ^:mutable listeners]
  Fn
  IFn
  (-invoke [this]
    (dfn this))
  (-invoke [this a]
    (inform-tracker this [a])
    (dfn this a))
  (-invoke [this a b]
    (inform-tracker this [a b])
    (dfn this a b))
  (-invoke [this a b c]
    (inform-tracker this [a b c])
    (dfn this a b c))
  (-invoke [this a b c d]
    (inform-tracker this [a b c d])
    (dfn this a b c d))
  (-invoke [this a b c d e]
    (inform-tracker this [a b c d e])
    (dfn this a b c d e))
  (-invoke [this a b c d e f]
    (inform-tracker this [a b c d e f])
    (dfn this a b c d e f))
  (-invoke [this a b c d e f g]
    (inform-tracker this [a b c d e f g])
    (dfn this a b c d e f g))
  (-invoke [this a b c d e f g h]
    (inform-tracker this [a b c d e f g h])
    (dfn this a b c d e f g h))
  (-invoke [this a b c d e f g h i]
    (inform-tracker this [a b c d e f g h i])
    (dfn this a b c d e f g h i))
  (-invoke [this a b c d e f g h i j]
    (inform-tracker this [a b c d e f g h i j])
    (dfn this a b c d e f g h i j))
  (-invoke [this a b c d e f g h i j k]
    (inform-tracker this [a b c d e f g h i j k])
    (dfn this a b c d e f g h i j k))
  (-invoke [this a b c d e f g h i j k l]
    (inform-tracker this [a b c d e f g h i j k l])
    (dfn this a b c d e f g h i j k l))
  (-invoke [this a b c d e f g h i j k l m]
    (inform-tracker this [a b c d e f g h i j k l m])
    (dfn this a b c d e f g h i j k l m))
  (-invoke [this a b c d e f g h i j k l m n]
    (inform-tracker this [a b c d e f g h i j k l m n])
    (dfn this a b c d e f g h i j k l m n))
  (-invoke [this a b c d e f g h i j k l m n o]
    (inform-tracker this [a b c d e f g h i j k l m n o])
    (dfn this a b c d e f g h i j k l m n o))
  (-invoke [this a b c d e f g h i j k l m n o p]
    (inform-tracker this [a b c d e f g h i j k l m n o p])
    (dfn this a b c d e f g h i j k l m n o p))
  (-invoke [this a b c d e f g h i j k l m n o p q]
    (inform-tracker this [a b c d e f g h i j k l m n o p q])
    (dfn this a b c d e f g h i j k l m n o p q))
  (-invoke [this a b c d e f g h i j k l m n o p q r]
    (inform-tracker this [a b c d e f g h i j k l m n o p q r])
    (dfn this a b c d e f g h i j k l m n o p q r))
  (-invoke [this a b c d e f g h i j k l m n o p q r s]
    (inform-tracker this [a b c d e f g h i j k l m n o p q r s])
    (dfn this a b c d e f g h i j k l m n o p q r s))
  (-invoke [this a b c d e f g h i j k l m n o p q r s t]
    (inform-tracker this [a b c d e f g h i j k l m n o p q r s t])
    (dfn this a b c d e f g h i j k l m n o p q r s t))
  (-invoke [this a b c d e f g h i j k l m n o p q r s t rest]
    (inform-tracker this [a b c d e f g h i j k l m n o p q r s t rest])
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
             #{} (derive.core/default-cache) {}))

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
  (first (get-value (.-cache derive) params)))

(defn tracker-handler [dfn params]
  (fn [result dmap]
    #_(println result dmap)
    (doseq [[store deps] dmap]
      (ensure-subscription dfn store))
    (add-value! (.-cache dfn) params result dmap)))

(defn notify-listeners [store deps]
  (let [listeners (.-listeners store)]
    (->> (keys listeners)
         (filter #(or (nil? %) (match-deps % deps))) ;; cheap consolidation
         (map (fn [k] (doseq [l (get listeners k)] (l store deps))))
         doall)))

(defn derive-listener
  "Helper. Handle source listener events"
  [derive store deps]
  #_(println "Derive received: " (or (.-deps deps) deps))
  (let [cache (.-cache derive)
        param-set (set (invalidate! cache store deps))]
    (when-not (empty? param-set)
      (notify-listeners derive param-set))))
    
(defn invalidate-all-listeners
  "Helper. Inform upstream when we're redefined"
  [derive]
  (doall
   (map (fn [f] (f derive nil))
        (flatten (vals (.-listeners derive))))))

;;  
;; Om Support
;;

(defn clear-listener!
  "Call from will-unmount and when re-subscribing a component"
  [owner]
  (let [listener (aget owner "__derive_listener")
        dmap (aget owner "__derive_dmap")]
    (doseq [[store query-deps] dmap]
      (derive.core/unsubscribe! store listener query-deps))
    (aset owner "__derive_listener" nil)
    (aset owner "__derive_dmap" nil)
    owner))

(defn save-listener! [owner listener dmap]
  (aset owner "__derive_listener" listener)
  (aset owner "__derive_dmap" dmap)
  owner)
  
(defn- om-subscribe-handler
  "Call in on-changes"
  [owner]
  (fn [listener dmap]
    (-> owner
        (clear-listener!)
        (save-listener! listener dmap))))
          
          
