(ns derive.main
  (:require [figwheel.client :as fw :include-macros true]
            [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [clojure.core.reducers :as r]
            [derive.repl :as repl]
            [derive.tools :as tools]
            [derive.protocols :as p]
            [derive.nativestore :as store]
            [derive.debug-level :as debug-level]))

(enable-console-print!)

;;
;; State
;;

(def root-div (.getElementById js/document "app"))
(defonce state (atom {:current 0}))
(defonce db (store/native-store :id))

(defn load-db []
  (when (not (p/get-index db :text))
    (p/add-index! db :text (store/ordered-index (store/field-key :text) compare)))
  (when (not (p/get-index db :value))
    (p/add-index! db :value-lt (store/ordered-index (store/field-key :int) compare)))
  (when (not (p/get-index db :value2))
    (p/add-index! db :value-gt (store/ordered-index (store/field-key :int) (comparator >))))
  (p/insert! db #js {:id 1 :text "Hi there." :int 10})
  (p/insert! db #js {:id 2 :text "I'm cycling..." :int 20})
  (p/insert! db #js {:id 3 :text "...through a series of messages." :int 30})
  (p/insert! db #js {:id 4 :text "And then I repeat!" :int 40})
  #_(time
     (do
       (dotimes [i 5000]
         (p/insert! db #js {:id (+ 10000 i) :text (str "entry-" (rand-int i))}))
       (dotimes [i 5000]
         (p/insert! db #js {:id (+ 20000 i) :int (rand-int i)})))))

;;
;; Derive renderable state
;;

(defn derive-count [db id]
  (println "Computing Count")
  (let [c (atom 0)]
    (p/scan (p/get-index db :value-lt) #(swap! c inc) 0 (* id 10))
    @c))

(defn derive-text [db id]
;  (let [tracker nil #_(default-tracker)]
;    (fn [db id]
;      (binding [*tracker* nil #_(tracker db [id] *tracker*)]
        (println "Computing Text")
        (str (:text (db id))  " [" (derive-count db id) "]"))

(defn inc-mod [modulus]
  (fn [old]
    (mod (inc old) modulus)))

(defn root-component
  [app owner]
  (reify
    om/IRender
    (render [_]
      (let [db (om/get-shared owner :db)]
        (html
         [:div
          [:h3 "Om Application"]
          [:p (derive-text db (inc (:current app)))]
          [:button {:on-click #(om/transact! app :current (inc-mod 4))} "Next"]])))))

;;
;; Setup and Lifecycle Management
;;

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


