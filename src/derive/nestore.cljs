(ns derive.nestore
  (:require [derive.protocols :as p]))

(def NEDB js/Datastore)
(def +empty+ #js {})

(deftype NeDB [db tx-handler listeners]
  p/INativeStore
  (insert! [_ obj]
    (assert (.-_id obj))
    (.insert db tx-handler))
  (update! [_ obj]
    (assert (.-_id obj))
    (.update db #js {"_id" (.-_id obj)} obj +empty+ tx-handler))
  (delete! [_ obj]
    (.remove db obj))

  p/IIndexedStore
  (add-index! [_ name index]
    (.ensureIndex db #js {:fieldName name}))
  (rem-index! [_ name]
    (.removeIndex name))
  (get-index! [_ name]
    (assert false "Invalid operation"))

  p/ITransactionalStore
  (transact! [_ 
  
  p/IQueryable
  (query [_ query]
    

(defn create []
  (let [db (NEDB.)
        listeners #js []
        tx-handler (fn [err updated]
                     (when (not err)
                       (doseq [listener listeners]
                         (listener updated))))]
    (NeDB. db tx-handler listeners)))
  
