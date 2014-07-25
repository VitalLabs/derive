(ns derive.main
  (:require-macros [derive.deps :refer [defn-derived with-tracked-dependencies]])
  (:require [clojure.core.reducers :as r]
            [figwheel.client :as fw :include-macros true]
            [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [derive.repl :as repl]
            [derive.om-mixin :as omix]
            [derive.debug-level :as debug-level]
            [derive.deps :as deps :include-macros true]
            [derive.nativestore :as store :refer [insert! delete! cursor transact!]]
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
  
  (insert! db #js {:id 1 :text "Hi there." :int 10
                         :next (store/NativeReference. db 2)})
  (insert! db #js {:id 2 :text "I'm cycling..." :int 20
                         :next (store/NativeReference. db 3)})
  (insert! db #js {:id 3 :text "...through a series of messages." :int 30
                                    :next (store/NativeReference. db 4)})
  (insert! db #js {:id 4 :text "And then I repeat!" :int 40
                                    :next (store/NativeReference. db 1)})
  (transact! db
   (fn [db]
     (time
      (do
        (dotimes [i 1000]
          (insert! db #js {:id (+ 10000 i) :text (str "entry-" (rand-int i))}))
        (dotimes [i 1000]
          (insert! db #js {:id (+ 20000 i) :int (rand-int i)}))
        nil)))))

;;
;; Derive renderable state
;;

(defn-derived derive-count [db id]
  #_(println "Computing simple count")
  (:int (db id)))

;; TODO: Still having build errors
(defn-derived derive-count2
;  "Somewhat artificial example of a processing pipeline with a sort step"
  [db id]
  #_(println "Computing derived count")
  (->> (cursor db :value-lt 0 (* id 10))
       (r/map :int)
       (r/filter even?)
       (r/map inc)
       (d/reducec->>)
       (d/sort (comparator >))
       (reduce + 0)))

;; TODO: Still having build errors
(defn derive-count3
;  "Somewhat artificial example of a processing pipeline with a sort step"
  [db id]
  #_(println "Computing underived count")
  (->> (cursor db :value-lt 0 (* id 10))
       (r/map :int)
       (r/filter even?)
       (r/map inc)
       (d/reducec->>)
       (d/sort (comparator >))
       (reduce + 0)))

(defn-derived derive-text
  [db id]
;  (let [tracker nil #_(default-tracker)]
;    (fn [db id]
;      (binding [*tracker* nil #_(tracker db [id] *tracker*)]
  (println "Computing Text")
  (str (:text (db id))  " [" (derive-count db (inc id)) "]"))

(defn inc-mod [modulus]
  (fn [old]
    (mod (inc old) modulus)))

(def RootComponent
  (let [obj (om/specify-state-methods! (clj->js om/pure-methods))]
    (aset obj "mixins" #js [omix/DeriveMixin])
    (aset obj "renderDerived"
          (fn [this db]
            (html
             [:div
              [:h3 "Om Application"]
              [:p (derive-text db (inc (:current app)))]
              [:button {:on-click #(om/transact! app :current (inc-mod 4))} "Next"]])))
    (js/React.createClass obj)))

(defn root-component
  [app owner]
  (RootComponent. nil))
      

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


