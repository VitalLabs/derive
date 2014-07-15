(ns derive.datascript)

(comment
(defn query-deps [conn query sources]
  (-> (apply analyze-q query sources)
      (filter #(= @conn (first %)))
      (map (comp vec rest))
      (set)))

(defn q [query & sources]
  (let [deps (query-deps ds/analyze-q query sources)]
    (report-dependencies deps)
    (with-meta (apply ds/q query sources) {:ds-deps deps})))

(defn txn-diff [old new]
  (->> (:tx-data report) ;; get all txs between versions
       (map ds/datom->index-keys)
       (reduce set/union #{})))
)
