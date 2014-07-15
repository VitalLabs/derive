(ns derive.examples
  (:require [derive.core :as core :import-macros true]))

(comment

(defn gen-schema [records]
  (into {} (map (fn [[attr & tags]]
                  (let [tags (set tags)]
                    [attr (cond-> {}
                                  (tags :many)
                                  (assoc :db/cardinality :db.cardinality/many)
                                  (tags :ref)
                                  (assoc :db/valueType :db.type/ref))]))
                records)))

(def schema
  (gen-schema
   [[:member :ref :many]
    [:contains     :ref :many "Resource <member> Share, Account <member> Group"]
    [:canRead      :ref :many "Account <canRead>  Share resources"]
    [:canWrite     :ref :many "Account <canWrite> Share resources"]
    [:canTrack     :ref :many "Account <canTrack> Subject and will see them on mobile"]
    [:self         :ref       "Account <self> Subject where Account == Subject"]
    ;; Subject
    [:subject/share :ref :many]
    ;; Measure
    [:channel      :many]
    ;; Tracker
    [:account      :ref]
    [:subject      :ref]
    [:measure      :ref]
    ;; Tasks
    [:parent       :ref]
    [:reference    :ref]
    ;; Notes
    [:reply-to     :ref]]))


;; TODO: Inject Test Data

(defn-derived notes-by-task
  [ds task-id]
  (d/q [:find ?n :in $ ?tid :where
        [?n :reference ?tid]]
       (:ds app) task-id))

(d/defn-derived notes-by-tracker
  [ds track-id]
  (d/q [:find ?n :in $ ?tid :where
        [?n :reference ?tid]]
       (:ds app) track-id))

(d/defn-derived observations
  [ds tracker-id]
  (d/query [:find ?obs :in $ ?tid :where
            [?tid :parent ?track]
            [?ts :type :time-series]
            [?ts :tracker ?track]
            [?ts :observations ?obs]]))

(d/defn-derived annotated-observations
  [conn tracker-id]
  (let [obs (observations ds tracker-id)
        notes (notes-by-tracker ds tracker-id)]
    (group-by :reference notes)))

(d/defn-derived card
  [conn task-id]
  {:pre [(not (nil? task-id))]}
  (let [db (:ds app)
        task (d/by-id (:ds app) task-id)
        tracker (d/entity db (:tracker task))
        measure (d/entity db (:measure tracker))
        notes ]
    {:track-id (:id tracker)
     :prompt (:prompt measure)
     :notes notes}))

;; TODO: Define make some calls
;; TODO: Demo memoization by inspection
