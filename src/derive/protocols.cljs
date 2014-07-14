(ns derive.protocols)

;;
;; Store protocols
;;

(defprotocol INativeStore
  (insert! [store obj])
  (update! [store obj])
  (delete! [store obj]))

(defprotocol IIndexedStore
  (add-index! [store name index])
  (rem-index! [store name])
  (get-index  [store name]))

(defprotocol IQueryable
  (query [store query]))

(defprotocol ITransactionalStore
  (transact! [store fn & args]))

(defprotocol IIndex
  (index! [idx obj])
  (unindex! [idx obj])
  (key-fn [idx]))

(defprotocol IScannableIndex
  (comp-fn [idx])
  (-scan   [idx f] [idx f start] [idx f start end] [idx f start end dir]))
