(ns derive.todo
  (:require-macros [secretary.macros :refer [defroute]])
  (:require [goog.events :as events]
            [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [secretary.core :as secretary]
            [clojure.string :as string]
            [derive.nativestore :as store :refer [insert! delete! cursor transact!]]
            [derive.dfns :as d])
  (:import [goog History]
           [goog.history EventType]))

(enable-console-print!)

(def ENTER_KEY 13)

(defonce db (store/native-store))
(defonce app-state (atom [db]))

;;
;; Main view state
;;

(defn view [db]
  (db "root"))

;; Actions
(defn filter-list! [db filter]
  (insert! db #js {:id "root" :filter (keyword filter)}))

(defn list-filter [db]
  (:filter (db "root")))

;;
;; Todos
;;

(defn toggle-all! [db checked]
  (->> (fn [db]
         (d/reducec->>
          (cursor db :type :todo :todo)
          (r/map #(insert! db #js {:id (:id %) :completed checked}))))
       (transact! db)))

(defn destroy-todo! [db todo-id]
  (delete! db id))

(defn create-todo! [db ]

(defn handle-events [db type val]
  (case type
    :destroy (destroy-todo! db val)))

  
;;
;; Routes
;;


(defroute "/" [] (filter-list! :all)

(defroute "/:filter" [filter] (filter-list! filter))

;;
;; Init
;;

(defn load-db []
  (ensure-index :type)
  (filter-list! :all))


(declare toggle-all)

(defn visible? [todo filter]
  (case filter
    :all true
    :active (not (:completed todo))
    :completed (:completed todo)))


(defn-derived main-state [db]
  (let [todo (cursor db :type :todo)]
    
  [todos showing editing] :as app}
  

(defhtml main [[todo showing editing] comm]
  (let [[todos showing editing] (main-state db)]
  [:section #js {:id "main" :style (hidden (empty? todos))}
    (dom/input
      #js {:id "toggle-all" :type "checkbox"
           :onChange #(toggle-all % app)
           :checked (every? :completed todos)})
    (apply dom/ul #js {:id "todo-list"}
      (om/build-all item/todo-item todos
        {:init-state {:comm comm}
         :key :id
         :fn (fn [todo]
               (cond-> todo
                 (= (:id todo) editing) (assoc :editing true)
                 (not (visible? todo showing)) (assoc :hidden true)))}))))

(defn make-clear-button [completed comm]
  (when (pos? completed)
    (dom/button
      #js {:id "clear-completed"
           :onClick #(put! comm [:clear (now)])}
      (str "Clear completed (" completed ")"))))

(defn footer [app count completed comm]
  (let [clear-button (make-clear-button completed comm)
        sel (-> (zipmap [:all :active :completed] (repeat ""))
                (assoc (:showing app) "selected"))]
    (dom/footer #js {:id "footer" :style (hidden (empty? (:todos app)))}
      (dom/span #js {:id "todo-count"}
        (dom/strong nil count)
        (str " " (pluralize count "item") " left"))
      (dom/ul #js {:id "filters"}
        (dom/li nil (dom/a #js {:href "#/" :className (sel :all)} "All"))
        (dom/li nil (dom/a #js {:href "#/active" :className (sel :active)} "Active"))
        (dom/li nil (dom/a #js {:href "#/completed" :className (sel :completed)} "Completed")))
      clear-button)))




                
            
