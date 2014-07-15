(ns derive.local
  (:require
   [om.core :as om :include-macros true]
   [sablono.core :as html :refer-macros [defhtml html]]
   [figwheel.client :as fw :include-macros true]))



(enable-console-print!)


(println "CONSOLE WORKS, !")


(defhtml render-page [owner state]
  [:div
   [:h3 (:banner-text state)]
   [:input {:type "text"
            :ref "banner-in"
            :value (:new-banner state)
            :on-change (fn [e]
                         (om/set-state! owner
                                        :new-banner
                                        (.. e -target -value)))}]
   [:button {:type "button"
             :on-click (fn [_]
                         (om/set-state! owner
                                        :banner-text (:new-banner state))
                         (om/set-state! owner
                                        :new-banner ""))}
    "Edit Banner"]])


(defn page [data owner]
  (reify
    om/IInitState
    (init-state [_]
      {:banner-text "Edit Me Below"
       :new-banner ""})
    om/IRenderState
    (render-state [_ state]
      (render-page owner state))))

(om/root page {} {:target (.getElementById js/document "main-area")})



(fw/watch-and-reload
   ;; :websocket-url "ws://localhost:3449/figwheel-ws" default
   :jsload-callback (fn [] (println "reloaded")))
