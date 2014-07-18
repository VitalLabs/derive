(ns derive.dfns
  (:require [clojure.core.reducers :as r]
            [purnam.native]))



(defn wrap-reducible [native]
  (if (satisfies? clojure.core/IReduce native) native
      (reify
        clojure.core/IReduce
        (-reduce [_ f]
          (loop [x 1 ret (aget native 0) len (alength native)]
            (if (= x len) ret
                (recur (inc x) (f ret (aget native x)) len))))
        (-reduce [_ f start]
          (loop [x 1 ret (f start (aget native 0)) len (alength native)]
            (if (= x len) ret
                (recur (inc x) (f ret (aget native x)) len)))))))


(extend-protocol IReduce
  array
  (-reduce [native f]
    (loop [x 1 ret (aget native 0) len (alength native)]
      (if (= x len) ret
          (recur (inc x) (f ret (aget native x)) len))))
  (-reduce [native f start]
    (loop [x 1 ret (f start (aget native 0)) len (alength native)]
      (if (= x len) ret
          (recur (inc x) (f ret (aget native x)) len)))))



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
