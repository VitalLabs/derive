(ns derive.om)

(comment

  (defprotocol IRenderDerived [_ state])

  (def DeriveMixin
    #js {:componentWillMount
         (fn []
           ;; subscribe to transactions in shared :conn
           ;; setup initial dependencies on component (empty)
           ;; txn listener calls refresh! on owner
           )
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

  
