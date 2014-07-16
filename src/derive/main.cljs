(ns derive.main
  (:require [figwheel.client :as fw :include-macros true]
            [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [derive.repl :as repl]
            [derive.tools :as tools]
            [derive.protocols :as p]
            [derive.nativestore :as store]
            [derive.debug-level :as debug-level]))

(enable-console-print!)

(def root-div (.getElementById js/document "app"))
(defonce state (atom {:current 0}))
(defonce db (store/native-store #(aget % "id")))

(defn load-db []
  (p/insert! db #js {:id 1 :text "Hi there."})
  (p/insert! db #js {:id 2 :text "I'm cycling..."})
  (p/insert! db #js {:id 3 :text "...through a series of messages."})
  (p/insert! db #js {:id 4 :text "And then I repeat!"}))

(defn derive-text [db id]
  (:text (db id)))

(defn inc-mod [modulus]
  (fn [old]
    (mod (inc old) modulus)))

(defn root-component
  [app owner]
  (reify
    om/IRender
    (render [_]
      (html
       [:div
        [:h2 "Om Application"]
        [:p (derive-text (om/get-shared owner :db) (inc (:current app)))]
        [:button {:on-click #(om/transact! app :current (inc-mod 4))} "Next"]]))))

(defn stop-app
  "Stop the application"
  []
  (om/detach-root! root-div))

(defn start-app 
  "Start the application"
  []
  (load-db)
  (om/root root-component state {:target root-div :shared {:db db}}))

(defn ^:export init [dev]
  (debug-level/set-level!)
  (tools/log "(init) dev:" dev)
  (repl/connect)
  (start-app))

(fw/watch-and-reload
 :jsload-callback (fn []
                    (stop-app)
                    (start-app)))


