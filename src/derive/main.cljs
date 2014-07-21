(ns derive.main
  (:require-macros [derive.deps :refer [defn-derived]])
  (:require [clojure.core.reducers :as r]
            [figwheel.client :as fw :include-macros true]
            [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [derive.repl :as repl]
            [derive.debug-level :as debug-level]
            [derive.deps :as deps :include-macros true]
            [derive.nativestore :as store]
            [derive.dfns :as d]))

(enable-console-print!)

;;
;; State
;;

(def root-div (.getElementById js/document "app"))
(defonce state (atom {:current 0}))
(defonce db (store/native-store))

(defn load-db []
  (store/ensure-index db :text :text)
  (store/ensure-index db :value-lt :int)
  (store/ensure-index db :value-gt :int (comparator >))
  
  (store/insert! db #js {:id 1 :text "Hi there." :int 10
                         :next (store/NativeReference. db 2)})
  (store/insert! db #js {:id 2 :text "I'm cycling..." :int 20
                         :next (store/NativeReference. db 3)})
  (store/insert! db #js {:id 3 :text "...through a series of messages." :int 30
                                    :next (store/NativeReference. db 4)})
  (store/insert! db #js {:id 4 :text "And then I repeat!" :int 40
                                    :next (store/NativeReference. db 1)})
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
  id)

;; TODO: Still having build errors
(defn-derived derive-count2
;  "Somewhat artificial example of a processing pipeline with a sort step"
  [db id]
  (println "Computing Count")
  (->> (store/cursor db :value-lt 0 (* id 10))
       (r/map :int)
       (r/filter even?)
       (r/map inc)
       (d/reducec->>)
       (d/sort (comparator >))
       (reduce + 0)))

(defn derive-text
  [db id]
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
  #_(tools/log "(init) dev:" dev)
  (repl/connect)
  (start-app))

(fw/watch-and-reload
 :jsload-callback (fn []
                    (stop-app)
                    (start-app)))


