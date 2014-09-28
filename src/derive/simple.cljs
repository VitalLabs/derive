(ns derive.simple
  "Trivial implementation of derive protocol, no performance optimization"
  (:refer-clojure :exclude [get-in get])
  (:require [derive.core :as d]
            [clojure.set :as set]))

(defprotocol ISimpleStore
  (-update! [store path fov args])
  (get [store key])
  (get-in [store path]))

(defn path->dep [path]
  #_(println path)
  (->> path 
       (map #(subvec path 0 %)
            (range 1 (inc (count path))))
       (set)))
     
(deftype SimpleStore [a l]
  ISimpleStore
  (-update! [store path fov args]
    (let [res (if (fn? fov)
                (if args
                  (swap! a update-in path #(apply fov %1 args))
                  (swap! a update-in path fov))
                (swap! a assoc-in path fov))
          dep (path->dep path)]
      (doseq [[listener deps] @l]
        #_(println deps dep)
        (when (or (nil? deps) (d/match-deps deps dep))
          (listener store dep)))
      res))
  
  (get [store key]
    (d/inform-tracker store (path->dep [key]))
    (cljs.core/get @a key))
  
  (get-in [store path]
    #_(println path)
    (d/inform-tracker store (path->dep path))
    (cljs.core/get-in @a path))

  IDeref
  (-deref [store] @a)
  
  d/IDependencySource
  (subscribe! [this listener]
    (swap! l assoc listener nil))
  (subscribe! [this listener deps]
    (swap! l assoc listener deps))
  (unsubscribe! [this listener]
    (swap! l dissoc listener))
  (unsubscribe! [this listener deps]
    (swap! l dissoc listener))
  (empty-deps [this] #{}))

(defn update! [store path fov & args]
  (-update! store path fov args))
  
(defn create
  ([] (create {}))
  ([init] (SimpleStore. (atom init) (atom {}))))

