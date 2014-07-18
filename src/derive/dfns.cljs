(ns derive.dfns
  (:require [clojure.core.reducers :as r]
            [purnam.native]))



(extend-protocol IReduce
  array
  (-reduce [native f]
    (if (> (alength native) 0)
      (areduce native i r (aget native 0) (f r (aget native i)))
      native))
  (-reduce [native f start]
    (if (> (alength native) 0)
      (areduce native i r (f start (aget native 0)) (f r (aget native i)))
      native)))

(defn map
  ([f coll]
     (r/reduce -conj! #js [] (r/map f coll)))
  ([f]
     (r/map f)))

(defn filter
  ([f coll]
     (r/reduce -conj! #js [] (r/filter f coll)))
  ([f]
   (r/filter f)))

(defn reduce->>
  [coll & forms]
  (r/reduce -conj! #js [] ((apply comp (reverse forms)) coll)))
