(ns time-align.ui.calendar)

(def days (->> (range 1 32)
               (map #(new js/Date 2018 0 %))))

(def data [])
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
                                    (map #(new js/Date year month %)))
        month-starts-sunday    (= 7 (get-day (first month-coll)))
        partitioned-by-sundays (partition-by #(= (get-day %) 7) month-coll)
        regular-fuser          (fn [[rest-of-week sunday]]
                                 (into rest-of-week sunday))
        sunday-first-fuser     (fn [[sunday rest-of-week]]
                                 (into rest-of-week sunday))
        fuser                  (if month-starts-sunday
                                 sunday-first-fuser regular-fuser)
        ;; help form this https://stackoverflow.com/a/12806697/5040125
        partitioned-by-weeks   (map fuser (partition-all 2 partitioned-by-sundays))]

    (first (indices
            #(week-has-day % {:year year :month month :date date})
            partitioned-by-weeks))))

(defn calendar [days data]
  [:svg {:key "calendar-svg"
         :id "calendar-svg"
         :xmlns "http://www.w3.org/2000/svg"
         :version  "1.1"
         :style       {:display      "inline-box"
                       :touch-action "pinch-zoom"
                       ;; this stops scrolling
                       ;; for moving period
}
         :width       "100%"
         :height      "100%"
         :viewBox      "0 0 100 100"}

   (map-indexed

    (fn [i d]
      (let [x (-> d (get-day) (- 1) (* cell-width))
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
         [:circle {:cx 3 :cy 3 :r 1 :fill "blue"}]]))

    days)])

(defn calendar-temp [] (calendar days data))

