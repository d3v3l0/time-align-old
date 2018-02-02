(ns time-align.ui.calendar
  (:require [re-frame.core :as rf]
            [cljs-react-material-ui.reagent :as ui]
            [cljs-react-material-ui.icons :as ic]
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

(defn calendar-nav [year month]
  [:div.navigation
   {:style {:display "flex" :justify-content "space-around"
            :flex-wrap "nowrap" :width "100%"}}
   [ui/icon-button
    {:onClick (fn [e] (rf/dispatch [:decrease-displayed-month]))}
    [ic/image-navigate-before]]

   [:span (str year "/" (inc month))]

   [ui/icon-button
    {:onClick (fn [e] (rf/dispatch [:advance-displayed-month]))}
    [ic/image-navigate-next]]])

(defn calendar [data]
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
      {:display "flex" :justify-content "center" :flex-wrap "wrap"}}

     (calendar-nav year month)

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
               this-day-is-today (and (= this-day-date displayed-day-date)
                                      (= dd-year (.getFullYear d))
                                      (= dd-month (.getMonth d)))
               x (-> d (get-day) (- 1) (* cell-width))
               y (* cell-height (week-number d))]
           [:g {:transform (str "translate(" x " " y ")")
                :id  (.toDateString d)
                :key (.toDateString d)}
            [:rect {:x "0"
                    :y "0"
                    :width cell-width
                    :height cell-height
                    :fill "white"
                    :stroke "#bcbcbc" ;; TODO grey400 when global styles are in place
                    :stroke-width "0.10"}]

            [:text {:x 1 :y 2.5
                    ;; :text-anchor "middle"
                    :stroke "white" :stroke-width "0.1"
                    :fill "grey" :font-size "3"} (.getDate d)]

            (->> periods
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
                                    [:rect {:x "0"
                                            :y (->>
                                                ;; relative position
                                                (/ (utils/get-ms (:start p))
                                                   utils/ms-in-day)
                                                (* cell-height))

                                            :width cell-width
                                            :height (-> (.valueOf (:stop p))
                                                        (- (.valueOf (:start p)))
                                                        ;; relative height
                                                        (/ utils/ms-in-day)
                                                        (* cell-height))

                                            :fill "blue"
                                            :stroke "#bcbcbc"
                                            :stroke-width "0.10"}] )))])))]))

       days)]]))


