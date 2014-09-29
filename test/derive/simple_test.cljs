(ns derive.simple-test
  (:require-macros [cemerick.cljs.test
                    :refer [is deftest with-test testing test-var]]
                   [derive.core :refer [defnd with-tracked-dependencies on-changes]])
  (:require [cemerick.cljs.test :as t]
            [clojure.set :as set]
            [derive.core :as d]
            [derive.simple :as store]))

(deftest path-to-dep
  (is (= (store/path->dep [:a :b :c])
         #{[:a] [:a :b] [:a :b :c]})))

(deftest store-access
  (let [store (store/create {:a {:b {:c 1}}})]
    (is (= (store/get store :a) {:b {:c 1}}))
    (is (= (store/get-in store [:a :b :c]) 1))))

(deftest store-update
  (let [store (store/create)]
    (store/update! store [:a] 10)
    (is (= (store/get store :a) 10))
    
    (store/update! store [:b :c] (constantly 20))
    (is (= (store/get-in store [:b :c]) 20))
    
    (store/update! store [:b :d] (fn [old new] new) 30)
    (is (= (store/get-in store [:b :d]) 30))))

(defn dval [store path]
  (store/get-in store path))

(deftest store-tracking
  (let [store (store/create {:a 1 :b 2})]
    (is (= (dval store [:a]) 1))
    (store/update! store [:a] 3)
    (is (= (dval store [:a]) 3))
    (is (= (dval store [:b]) 2))))


(deftest set-deps
  (let [d1 #{:b :c}
        d2 (d/merge-deps #{:a} d1)]
    (is (= d2 #{:a :b :c}))
    (is (= (d/match-deps d1 d2)))
    (is (not (d/match-deps d1 #{:a})))))


(deftest cache
  (let [c (d/default-cache)]
    (d/add-value! c [1 2] :result {:store #{1 4 8}})
    (is (= (first (d/get-value c [1 2])) :result))
    (is (= (d/invalidate! c :store #{4}) #{[1 2]}))))

(deftest simple-store
  (let [store (store/create)
        res (atom nil)
        handler (fn [store deps]
                  (reset! res deps))]
    (d/subscribe! store handler #{[3 4]})
    (store/update! store [3 4] 10)
    (is (= (store/get-in store [3 4]) 10))
    (is (set/intersection @res #{[3 4]}))
    (store/update! store [1 2] 20)
    (is (set/intersection @res #{[3 4]}))))

(deftest simple-store-tracking1
  (let [store (store/create)
        query-deps (atom nil)
        tracker-handler (fn [store deps] (reset! query-deps deps))]
    (doto store
      (store/update! [1 2] 3)
      (store/update! [3 4] 5)
      (store/update! [1 3] 5))
    (is (= @store {1 {2 3 3 5} 3 {4 5}}))
    (with-tracked-dependencies [tracker-handler]
      (is (= (store/get-in store [1 2]) 3))
      (is (= (store/get-in store [1 3]) 5)))
    (is (= (first (vals @query-deps)) #{[1] [1 2] [1 3]}))))


(defn- derive-cache-value [df args]
  (first (d/get-value (.-cache df) args)))

(defnd dvald [store path]
  (store/get-in store path))

(deftest derive1
  (let [store (store/create {:a 1 :b 2})]
    ;; Can read
    (is (= (dvald store [:a]) 1))
    ;; Read is pulled from dvald cache
    (is (= (derive-cache-value dvald [store [:a]]) 1))
    (store/update! store [:a] 3)
    ;; Cache was invalidated
    (is (= (derive-cache-value dvald [store [:a]]) nil))
    (is (= (dvald store [:a]) 3))
    ;; Cache was restored
    (is (= (derive-cache-value dvald [store [:a]]) 3))
    (store/update! store [:b] 3)
    ;; Original cache value remains untouched
    (is (= (derive-cache-value dvald [store [:a]]) 3))
    (is (= (dvald store [:b]) 3))))


(defnd d1 [store path mul]
  (* mul (store/get-in store path)))

(defnd rootd [store val]
  (+ (d1 store [1 val] 3)
     (d1 store [2 val] 4)))
  
(deftest nested-derive
  (let [store (store/create {1 {1 2 2 20} 2 {1 4 2 40}})]
    (is (= (rootd store 1) (+ 6 16)))
    (is (= (derive-cache-value rootd [store 1]) (+ 6 16)))
    (is (= (derive-cache-value d1 [store [1 1] 3]) 6))
    (is (= (derive-cache-value d1 [store [2 1] 4]) 16))
    (store/update! store [1 1] 3)
    ;; Chain of deps is invalidated
    (is (= (derive-cache-value rootd [store 1]) nil))
    (is (= (derive-cache-value d1 [store [1 1] 3]) nil))
    (is (= (derive-cache-value d1 [store [2 1] 4]) 16))))

(deftest changes
  (let [store (store/create {1 :test1})
        id 1
        owner (js-obj)
        target (atom nil)
        render (fn [store id]
                 (reset! target (store/get store id)))]
    (on-changes [ (d/om-subscribe-handler owner)
                  #(render store id) ]
      (render store id))
    (is (= @target :test1))
    (store/update! store [1] :test2)
    (is (= @target :test2))
    (store/update! store [1] :test3)
    (is (= @target :test3))))
        

    

      
        
