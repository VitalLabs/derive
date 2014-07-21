(ns derive.dfns
  (:require [clojure.core.reducers :as r]
            [purnam.native])
  (:refer-clojure :exclude [filter map mapcat count remove]))

(extend-protocol IReduce
  array
  (-reduce [native f]
    (areduce native i r (f) (f r (aget native i))))
  (-reduce [native f start]
    (areduce native i r (f start) (f r (aget native i)))))

(defn map
  ([f coll]
     (r/reduce -conj! #js [] (r/map f coll)))
  ([f]
     (r/map f)))

(defn mapcat
  ([f coll]
     (r/reduce -conj! #js [] (r/mapcat f coll)))
  ([f]
     (r/mapcat f)))

(defn filter
  ([f coll]
     (r/reduce -conj! #js [] (r/filter f coll)))
  ([f]
   (r/filter f)))

(defn remove
  ([f coll]
     (r/reduce -conj! #js [] (r/remove f coll)))
  ([f]
     (r/remove f)))

(defn count
  ([f coll]
     (reduce #(inc %1) 0 coll)))

(defn reduce->>
  [comb coll & forms]
  (r/reduce comb ((apply comp (reverse forms)) coll)))

(defn reducec->>
  [coll & forms]
  (r/reduce -conj! #js [] ((apply comp (reverse forms)) coll)))


;;
;; Non-reduce native helpers
;;

(defn sort-in-place [arry f]
  (.sort arry f))
