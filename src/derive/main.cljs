(ns derive.main
  (:require [figwheel.client :as fw :include-macros true]
            [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [derive.repl :as repl]
            [derive.tools :as tools]
            [derive.debug-level :as debug-level]))

(enable-console-print!)

(def root-div (.getElementById js/document "app"))
(defonce state (atom {:text "Hello world, how are ya!" :count 0}))

(defn root-component
  [app owner]
  (reify
    om/IRender
    (render [_]
      (html
       [:div
        [:h2 "Om Application"]
        [:p (:text app) " " (:count app)]
        [:button {:on-click #(om/transact! app :count inc)} "Increment"]]))))

(defn stop-app []
  (om/detach-root! root-div))

(defn start-app []
  (om/root root-component state {:target root-div :shared {}}))

(defn ^:export init [dev]
  (debug-level/set-level!)
  (tools/log "(init) dev:" dev)
  (repl/connect)
  (start-app))

(fw/watch-and-reload
 :jsload-callback (fn []
                    (stop-app)
                    (start-app)))


