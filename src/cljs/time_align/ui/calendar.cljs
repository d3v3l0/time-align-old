(ns time-align.ui.calendar
  (:require [re-frame.core :as rf]
            [cljs-react-material-ui.reagent :as ui]
            [time-align.ui.common :as uic]
            [time-align.history :as hist]
            [cljs-react-material-ui.icons :as ic]
            [time-align.client-utilities :as cutils]
            [time-align.utilities :as utils]))

(def cell-width (* (/ 100 7)))  ;; ~14

(def cell-height (* (/ 100 5))) ;; 20

(defn indices
  "From [stack overflow](https://stackoverflow.com/a/8642069/5040125)"
  [pred coll]
  (keep-indexed #(when (pred %2) %1) coll))

(defn get-day
  "A monday 1 based index where sunday is 7"
  [date]
  (let [date (.getDay date)]
    (if (= date 0) 7 date)))

(defn week-has-day [week {:keys [year month date]}]
  (not (empty? (indices (fn [day] (let [this-days-year  (.getFullYear day)
                                        this-days-month (.getMonth day)
                                        this-days-date  (.getDate day)]
                                    (and (= year this-days-year)
                                         (= month this-days-month)
                                         (= date this-days-date))))
                        week))))

(defn week-number [ts]
  (let [year                   (.getFullYear ts)
        month                  (.getMonth ts)
        date                   (.getDate ts)
        day                    (get-day ts)
        month-coll             (->> (range 1 32)
                                    (map #(new js/Date year month %))
                                    (filter #(and (= year (.getFullYear %))
                                                  (= month (.getMonth %)))))
        month-starts-monday    (= 1 (get-day (first month-coll)))
        partitioned-by-mondays (partition-by #(= (get-day %) 1) month-coll)
        fuser                  (fn [[monday rest-of-week]]
                                 (into rest-of-week monday))
        ;; help from this https://stackoverflow.com/a/12806697/5040125
        partitioned-by-weeks   (->> partitioned-by-mondays
                                    (#(if month-starts-monday
                                         %
                                         (rest %)))
                                    (partition-all 2)
                                    (map fuser)
                                    (#(if month-starts-monday
                                        %
                                        (cons
                                         (first partitioned-by-mondays)
                                         %))))]

    (first (indices
            #(week-has-day % {:year year :month month :date date})
            partitioned-by-weeks))))

(defn calendar-nav [year month dd-year dd-month]
  (let [honed-in (and (= year dd-year)
                      (= month dd-month))]
    [:div.navigation
     {:style {:display "flex" :justify-content "space-around"
              :flex-wrap "nowrap" :width "100%"}}

     [ui/icon-button
      {:onClick (fn [e] (rf/dispatch [:decrease-displayed-month]))}
      [ic/image-navigate-before {:color (:primary uic/app-theme)}]]

     [ui/icon-button
      {:onClick (fn [e] (rf/dispatch [:set-displayed-month [dd-year dd-month]]))}
      (if honed-in
        [ic/device-gps-fixed {:color (:primary uic/app-theme)}]
        [ic/device-gps-not-fixed {:color (:primary uic/app-theme)}])]

     [ui/icon-button
      {:onClick (fn [e] (rf/dispatch [:advance-displayed-month]))}
      [ic/image-navigate-next {:color (:primary uic/app-theme)}]]]))

(defn calendar []
  (let [displayed-day @(rf/subscribe [:displayed-day])
        dd-year (.getFullYear displayed-day)
        dd-month (.getMonth displayed-day)
        [year month] @(rf/subscribe [:displayed-month])
        days (->> (range 1 32)
                  (map #(new js/Date year month %))
                  (filter #(and (= year (.getFullYear %))
                                (= month (.getMonth %)))))
        periods @(rf/subscribe [:periods])]

    [:div
     {:style
      {:display "flex" :justify-content "center" :flex-wrap "wrap"
       :margin-top "0.5em"}}

     [:span {:style {:color (:primary uic/app-theme)}} (str year "/" (inc month))]

     (calendar-nav year month dd-year dd-month)

     [:svg {:key "calendar-svg"
            :id "calendar-svg"
            :xmlns "http://www.w3.org/2000/svg"
            :version  "1.1"
            :style       {:display      "inline-box"
                          ;; this stops scrolling for moving period
                          :touch-action "pinch-zoom"}
            :width       "100%"
            :height      "100%"
            :viewBox      "0 0 100 100"}

      (map-indexed

       (fn [i d]
         (let [this-day-date (.getDate d)
               displayed-day-date (.getDate displayed-day)
               this-day-is-displayed (and (= this-day-date displayed-day-date)
                                      (= dd-year (.getFullYear d))
                                      (= dd-month (.getMonth d)))
               this-day-is-today (and (= this-day-date (.getDate (new js/Date)))
                                      (= (.getFullYear d) (.getFullYear (new js/Date)))
                                      (= (.getMonth d) (.getMonth (new js/Date))))
               x (-> d (get-day) (- 1) (* cell-width))
               y (* cell-height (week-number d))]
           [:g {:transform (str "translate(" x " " y ")")
                :id  (.toDateString d)
                :key (.toDateString d)
                :onClick (fn [_]
                           (rf/dispatch [:set-displayed-day d])
                           (hist/nav! "/"))}
            [:rect {:x "0"
                    :y "0"
                    :width cell-width
                    :height cell-height
                    :fill "white"
                    :stroke (if this-day-is-today
                              (:secondary uic/app-theme) ;; TODO should be accent1
                              (if this-day-is-displayed (:primary uic/app-theme) "#bcbcbc"))
                    ;; TODO grey400 when global styles are in place
                    :stroke-width (if (or this-day-is-displayed this-day-is-today) "0.25" "0.10")}]

            (->> periods
                 (filter cutils/period-has-stamps)
                 (filter (fn [p]
                           (and
                            (or
                             (= this-day-date (.getDate (:start p)))
                             (= this-day-date (.getDate (:stop p))))
                            ;; shouldn't matter that we only check start
                            ;; TODO period max of 24 hours or better checks here
                            (= month (.getMonth (:start p)))
                            (= year (.getFullYear (:start p))))))
                 ((fn [periods]
                    [:g (->> periods
                             (map (fn [p]
                                    [:rect {:key (str (:id p))
                                            :x (if (:planned p) "0" (/ cell-width 2))
                                            :y (->>
                                                (#(if (< (.getDate (:start p))
                                                         this-day-date)
                                                    (merge p {:start (new js/Date year month this-day-date 0 0 1)})
                                                    p))
                                                ;; relative position
                                                (#(/ (utils/get-ms (:start %))
                                                     utils/ms-in-day))
                                                (* cell-height))

                                            :width (/ cell-width 2.1)
                                            :height (-> (#(if (> (.getDate (:stop p))
                                                                 this-day-date)
                                                            (merge p {:stop (new js/Date year month this-day-date 23 59 59)})
                                                            p)) ;; adjust stop if after today
                                                        (#(if (< (.getDate (:start p))
                                                                 this-day-date)
                                                            (merge p {:start (new js/Date year month this-day-date 0 0 1)})
                                                            p)) ;; adjust start if before today
                                                        (#(- (.valueOf (:stop %))
                                                             (.valueOf (:start %))))
                                                        ;; relative height
                                                        (/ utils/ms-in-day)
                                                        (* cell-height))

                                            :fill (:color p)}] )))])))

            [:circle {:cx 2 :cy 2.5 :r 1.5 :fill "white"}]
            [:text {:x 2 :y 3
                    :text-anchor "middle"
                    ;; :stroke "white" :stroke-width "0.1"
                    :font-weight "bold"
                    :fill "grey" :font-size "2"} (.getDate d)]
            ]))

       days)]]))


