(ns derive.main
  (:require [figwheel.client :as fw :include-macros true]
            [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [clojure.core.reducers :as r]
            [derive.repl :as repl]
            [derive.tools :as tools]
            [derive.dfns :as d]
            [derive.nativestore :as store]
            [derive.debug-level :as debug-level]))

(enable-console-print!)

;;
;; State
;;

(def root-div (.getElementById js/document "app"))
(defonce state (atom {:current 0}))
(defonce db (store/native-store))

(defn load-db []
  (when (not (store/get-index db :text))
    (store/add-index! db :text (store/ordered-index (store/field-key :text) compare)))
  (when (not (store/get-index db :value-lt))
    (store/add-index! db :value-lt (store/ordered-index (store/field-key :int) compare)))
  (when (not (store/get-index db :value-gt))
    (store/add-index! db :value-gt (store/ordered-index (store/field-key :int) (comparator >))))
  (store/insert! db #js {:id 1 :text "Hi there." :int 10})
  (store/insert! db #js {:id 2 :text "I'm cycling..." :int 20})
  (store/insert! db #js {:id 3 :text "...through a series of messages." :int 30})
  (store/insert! db #js {:id 4 :text "And then I repeat!" :int 40})
  #_(time
     (do
       (dotimes [i 5000]
         (store/insert! db #js {:id (+ 10000 i) :text (str "entry-" (rand-int i))}))
       (dotimes [i 5000]
         (store/insert! db #js {:id (+ 20000 i) :int (rand-int i)})))))

;;
;; Derive renderable state
;;

(defn derive-count [db id]
  (println "Computing Count")
  (-> (store/cursor db :value-lt 0 (* id 10))
      (d/reduce->> + (d/map :int) (d/filter even?) (d/map inc))))

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


