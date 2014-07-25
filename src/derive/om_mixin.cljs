(ns derive.om-mixin
  (:require-macros [derive.deps :refer [defn-derived with-tracked-dependencies]])
  (:require [clojure.core.reducers :as r]
            [om.core :as om :include-macros true]
            [derive.deps :as deps :include-macros true]))
  

(defprotocol IRenderDerived
  (render-derived [this]))

(def DeriveMixin
  #js {:render
       (fn [this]
         (.log js/console this)
         (let [db (om/get-shared this :db)]
           (with-tracked-dependencies
             [parent result deps (deps/empty-deps db)]
             (render-derived this db)
             (deps/subscribe! db #(om/refresh! owner) deps))))
       :componentWillUnmount
       (fn []
         ;; unsubscribe to transactions in shared :conn
         )})

  ;; Use a ctor to override default render?
  (defn derive-ctor []
    (should-component-update [_ state]
                             (check m/annotated-observations cursor))
    (render [_ state]
            (let [card (annotated-observations (omd/conn) task-id)]
              (render-card card))))

  (omd/defcomponent
    (init-state [_])
    (render-state [_ state])
    (will-mount [_])
    (did-mount [_]))

;(updated? annotated-observations app task-id)
;(updated-entity? get-entity (:tracker task))
   
  (defn survey-component
    [task-id owner]
    (reify
      om/IRenderState
      (render-state [_ state]
        (with-om-dep-capture owner
          (let [card (survey-card (conn owner) task-id)
                ts (time-series (conn owner) (:tracker-id card))]
            (html
             [:h1 "My component"]
             [:p (:prompt card)]
             (om/build ts/component ts)))))))

)

  
