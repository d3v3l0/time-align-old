(ns time-align.core
  (:require [reagent.core :as r]
            [cljsjs.material-ui]
            [cljs-react-material-ui.core :refer [get-mui-theme color]]
            [cljs-react-material-ui.reagent :as ui]
            [cljs-react-material-ui.icons :as ic]
            [re-frame.core :as rf]
            [secretary.core :as secretary]
            [goog.events :as events]
            [goog.history.EventType :as HistoryEventType]
            [markdown.core :refer [md->html]]
            [ajax.core :refer [GET POST]]
            [time-align.ajax :refer [load-interceptors!]]
            [time-align.handlers]
            [time-align.subscriptions]
            [clojure.string :as string]
            [time-align.utilities :as utils]
            [cljs.pprint :refer [pprint]])
  (:import goog.History))

(def svg-consts {:viewBox "0 0 100 100"
                 ;; :width "90" :height "90" :x "5" :y "5"
                 :cx "50" :cy "50" :r "40"
                 :inner-r "30"
                 :period-width "10"})

(def shadow-filter
  [:defs
   [:filter {:id "shadow-2dp"
             :x "-50%" :y "-100%"
             :width "200%" :height "300%"}
    [:feOffset {:in "SourceAlpha" :result "offA" :dy "2"}]
    [:feOffset {:in "SourceAlpha" :result "offB" :dy "1"}]
    [:feOffset {:in "SourceAlpha" :result "offC" :dy "3"}]
    [:feMorphology {:in "offC" :result "spreadC"
                    :operator "erode" :radius "2"}]
    [:feGaussianBlur {:in "offA" :result "blurA"
                      :stdDeviation "1"}]
    [:feGaussianBlur {:in "offB" :result "blurB"
                      :stdDeviation "2.5"}]
    [:feGaussianBlur {:in "spreadC" :result "blurC"
                      :stdDeviation "0.5"}]
    [:feFlood {:flood-opacity "0.14" :result "opA"}]
    [:feFlood {:flood-opacity "0.12" :result "opB"}]
    [:feFlood {:flood-opacity "0.20" :result "opC"}]
    [:feComposite {:in "opA" :in2 "blurA"
                   :result "shA" :operator "in"}]
    [:feComposite {:in "opB" :in2 "blurB"
                   :result "shB" :operator "in"}]
    [:feComposite {:in "opC" :in2 "blurC"
                   :result "shC" :operator "in"}]
    [:feMerge
     [:feMergeNode {:in "shA"}]
     [:feMergeNode {:in "shB"}]
     [:feMergeNode {:in "shC"}]
     [:feMergeNode {:in "SourceGraphic"}]]]])

(defn describe-arc [cx cy r start stop]
  (let [
        p-start (utils/polar-to-cartesian cx cy r start)
        p-stop  (utils/polar-to-cartesian cx cy r stop)

        large-arc-flag (if (<= (- stop start) 180) "0" "1")]

    (string/join " " ["M" (:x p-start) (:y p-start)
                      "A" r r 0 large-arc-flag 1 (:x p-stop) (:y p-stop)])))

(defn period [selected-period period]
  (let [id (:id period)
        start-date (:start period)
        start-ms (utils/get-ms start-date)
        start-angle (utils/ms-to-angle start-ms)

        stop-date (:stop period)
        stop-ms (utils/get-ms stop-date)
        stop-angle (utils/ms-to-angle stop-ms)

        type (:type period)
        color (cond
                ;; actual
                (and (or (nil? selected-period)
                         (= selected-period id))
                     (= :actual type))
                (:color period)
                ;; "#43a047"
                ;; planned
                (and (or (nil? selected-period)
                         (= selected-period id))
                     (= :planned type))
                (:color period)
                ;; "#63ccff"
                ;; something else selected
                :else (if (= :planned type)
                        "#aaaaaa"
                        "#a1a1a1"))
        period-width (js/parseInt (:period-width svg-consts))
        cx      (js/parseInt (:cx svg-consts))
        cy      (js/parseInt (:cy svg-consts))
        ;; radii need to be offset to account for path using
        ;; A (arc) command having radius as the center of path
        ;; instead of edge (like circle)
        r       (-> (js/parseInt (:r svg-consts))
                    (- (/ period-width 2)))
        inner-r (-> (js/parseInt (:inner-r svg-consts))
                    (- (/ period-width 2)))


        arc (describe-arc cx cy
                          (if (= :actual type) r inner-r)
                          start-angle stop-angle)]

    [:path
     {:key (str id)
      :d arc
      :stroke color
      :opacity (if (= :planned type) "0.6" "0.3")
      :stroke-width period-width
      :fill "transparent"
      :onClick (if (nil? selected-period)
                 (fn [e]
                   (.stopPropagation e)
                   (rf/dispatch
                    [:set-selected-period id])))}]))

(defn periods [col-of-col-of-periods selected-period]
  (->> col-of-col-of-periods
       (map (fn [periods]
              (->> periods
                   (map (partial period selected-period)))))))

(defn handle-period-move [id evt]
  (let [cx (js/parseInt (:cx svg-consts))
        cy (js/parseInt (:cy svg-consts))
        pos (utils/client-to-view-box id evt)
        pos-t (utils/point-to-centered-circle
               (merge pos {:cx cx :cy cy}))
        angle (utils/point-to-angle pos-t)
        time-ms (utils/angle-to-ms angle)]

    (rf/dispatch [:move-selected-period time-ms])))

(defn day [tasks selected-period day]
  (let [date-str (subs (.toISOString day) 0 10)
        col-of-col-of-periods (utils/filter-periods-for-day day tasks)]

    [:svg (merge {:key date-str
                  :id date-str
                  :style {:display "inline-box"}
                  :width "100%"
                  :height "100%"
                  :onMouseMove (if (not (nil? selected-period))
                                 (partial handle-period-move date-str))}
                 (select-keys svg-consts [:viewBox]))
     shadow-filter
     [:circle (merge {:fill "#e8e8e8" :filter "url(#shadow-2dp)"}
                     (select-keys svg-consts [:cx :cy :r]))]
     [:circle (merge {:fill "#f1f1f1" :r (:inner-r svg-consts)}
                     (select-keys svg-consts [:cx :cy]))]
     (periods col-of-col-of-periods selected-period)]))

(defn days [days tasks selected-period]
  (->> days
       (map (partial day tasks selected-period))))

(defn task-list [tasks]
  [:div.tasks-list {:style {:display "flex"}}
   [ui/paper
    [:div.task-list {:style {:overflow-y "scroll"}}
     [ui/list
      (->> tasks
           (map
            (fn [t]
              [ui/list-item
               {:key (:id t)
                :primaryText (:name t)
                :onTouchTap #(rf/dispatch
                              [:set-selected-task (:id t)])
                }
               ])))]]]])

(defn queue [tasks]
  (let [periods-no-stamps (utils/filter-periods-no-stamps tasks)]
    [:div.queue-container {:style {:display "flex" :align-self "center"}}
     [ui/paper
      [:div.queue {:style {:overflow-y "scroll"}}
       [ui/list
        (->> periods-no-stamps
             (map (fn [period]
                    [ui/list-item
                     {:key (:id period)
                      :primaryText (str "period-id " (:id period))
                      :onTouchTap #(rf/dispatch
                                    [:set-selected-task (:task-id period)])
                      }
                     ]
                    )))
        ]]]]
    )
  )

(defn home-page []
  (let [main-drawer-state @(rf/subscribe [:main-drawer-state])]

    [:div.app-container
     {:style {:display "flex"
              :flex-wrap "wrap"
              :justify-content "center"
              :align-content "space-between"
              :height "100%"
              ;; :border "yellow solid 0.1em"
              :box-sizing "border-box"}}

     [:div.app-bar-container
      {:style {:display "flex"
               :flex "1 0 100%"
               ;; :border "green solid 0.1em"
               :box-sizing "border-box"}}
      [ui/app-bar {:title "Time Align"
                   :onLeftIconButtonTouchTap (fn [e] (rf/dispatch [:toggle-main-drawer]))}]
      [ui/drawer {:docked false :open main-drawer-state
                  :onRequestChange (fn [new-state] (rf/dispatch [:set-main-drawer new-state]))}
       [ui/menu-item {:onTouchTap #(rf/dispatch [:set-main-drawer false])}
        [:i.material-icons "person"] "Account"]
       [ui/menu-item {:onTouchTap #(rf/dispatch [:set-main-drawer false])}
        [:i.material-icons "settings"] "Settings"]
       ;; [ui/menu-item {:onTouchTap #(rf/dispatch [:set-main-drawer false])}
       ;;  [ui/svg-icon {:marginRight "24"} [:p {:d "M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z"}]] "???"]
       ]]

     [:div.day-container
      {:style {:display "flex"
               :flex "1 0 100%"
               :max-height "60%"
               :border "red solid 0.1em"
               :box-sizing "border-box"}
       :onClick (fn [e] (.log js/console "I used to deselect things"))}
      "day display"
      ]

     [:div.queue-container
      {:style {:display "flex"
               :flex "1 0 50%"
               :border "blue solid 0.1em"
               :box-sizing "border-box"}}
      "queue display"
      ]

     [:div.action-container
      {:style {:display "flex"
               :flex "1 0 50%"
               :border "green solid 0.1em"
               :box-sizing "border-box"}}
      "action display"
      ]


     ]))

(def pages
  {:home #'home-page})

(defn page []
  [ui/mui-theme-provider
   [:div
    [(pages @(rf/subscribe [:page]))]]
   ]
  )

;; -------------------------
;; Routes
(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (rf/dispatch [:set-active-page :home]))

;; -------------------------
;; History
;; must be called after routes have been defined
(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
     HistoryEventType/NAVIGATE
     (fn [event]
       (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

;; -------------------------
;; Initialize app

(defn mount-components []
  (rf/clear-subscription-cache!)
  (r/render [#'page] (.getElementById js/document "app")))

(defn init! []
  (rf/dispatch-sync [:initialize-db])
  (load-interceptors!)
  (hook-browser-navigation!)
  (mount-components))
