(ns time-align.ui.svg-day-view
  (:require [time-align.utilities :as utils]
            [time-align.client-utilities :as cutils]
            [re-frame.core :as rf]
            [time-align.history :as hist]
            [time-align.ui.common :as uic]
            [time-align.js-interop :as jsi]
            [clojure.string :as string]
            [stylefy.core :as stylefy]))

(def shadow-filter
  [:defs
   [:filter {:id    "shadow-2dp"
             :x     "-50%" :y "-100%"
             :width "200%" :height "300%"}
    [:feOffset {:in "SourceAlpha" :result "offA" :dy "2"}]
    [:feOffset {:in "SourceAlpha" :result "offB" :dy "1"}]
    [:feOffset {:in "SourceAlpha" :result "offC" :dy "3"}]
    [:feMorphology {:in       "offC" :result "spreadC"
                    :operator "erode" :radius "2"}]
    [:feGaussianBlur {:in           "offA" :result "blurA"
                      :stdDeviation "1"}]
    [:feGaussianBlur {:in           "offB" :result "blurB"
                      :stdDeviation "2.5"}]
    [:feGaussianBlur {:in           "spreadC" :result "blurC"
                      :stdDeviation "0.5"}]
    [:feFlood {:flood-opacity "0.14" :result "opA"}]
    [:feFlood {:flood-opacity "0.12" :result "opB"}]
    [:feFlood {:flood-opacity "0.20" :result "opC"}]
    [:feComposite {:in     "opA" :in2 "blurA"
                   :result "shA" :operator "in"}]
    [:feComposite {:in     "opB" :in2 "blurB"
                   :result "shB" :operator "in"}]
    [:feComposite {:in     "opC" :in2 "blurC"
                   :result "shC" :operator "in"}]
    [:feMerge
     [:feMergeNode {:in "shA"}]
     [:feMergeNode {:in "shB"}]
     [:feMergeNode {:in "shC"}]
     [:feMergeNode {:in "SourceGraphic"}]]]])

(defn handle-period-move [id type evt]
  (let [cx                (js/parseInt (:cx uic/svg-consts))
        cy                (js/parseInt (:cy uic/svg-consts))
        pos               (cutils/client-to-view-box id evt type)
        pos-t             (cutils/point-to-centered-circle
                            (merge pos {:cx cx :cy cy}))
        angle             (cutils/point-to-angle pos-t)
        mid-point-time-ms (cutils/angle-to-ms angle)]

    (rf/dispatch [:move-selected-period mid-point-time-ms])))

(defn period [selected curr-time is-moving-period type period displayed-day]
  (let [id                         (:id period)
        start-date                 (:start period)
        starts-yesterday (utils/is-this-day-before-that-day?
                          start-date displayed-day)
        start-ms                   (utils/get-ms start-date)
        start-angle                (if starts-yesterday
                                     0.5
                                     (cutils/ms-to-angle start-ms))

        stop-date  (:stop period)
        stops-tomorrow (utils/is-this-day-after-that-day?
                        stop-date displayed-day)
        stop-ms    (utils/get-ms stop-date)
        stop-angle (if stops-tomorrow
                     359.5
                                     (cutils/ms-to-angle stop-ms))

        straddles-now              (utils/straddles-now? start-date stop-date)
        now-ms                     (utils/get-ms (new js/Date))
        broken-stop-before-angle   (cutils/ms-to-angle now-ms)
        broken-start-after-angle   (cutils/ms-to-angle now-ms)

        curr-time-ms               (jsi/value-of curr-time)
        start-abs-ms               (jsi/value-of start-date)
        stop-abs-ms                (jsi/value-of stop-date)

        is-period-selected         (= :period
                                      (get-in
                                        selected
                                        [:current-selection :type-or-nil]))
        selected-period            (if is-period-selected
                                     (get-in
                                       selected
                                       [:current-selection :id-or-nil])
                                     nil)
        this-period-selected       (= selected-period id)

        opacity-minor              "0.66"
        opacity-major              "0.99"
        is-planned                 (= type :planned)
        ;; actual is boldest in the past (before now)
        ;; planned is boldest in the future (after now)
        ;; opacity-before/after is used for task straddling now
        opacity-before        opacity-major
                              ;; (if is-planned
                              ;;   opacity-minor
                              ;;   opacity-major)
        opacity-after         opacity-major
                              ;; (if is-planned
                              ;;   opacity-major
                              ;;   opacity-minor)
        opacity               opacity-major
                                   ;; (cond
                                   ;;   this-period-selected opacity-major

                                   ;;   ;; planned after now
                                   ;;   (and is-planned (< curr-time-ms stop-abs-ms))
                                   ;;   opacity-major

                                   ;;   ;; actual before now
                                   ;;   (and (not is-planned) (> curr-time-ms stop-abs-ms))
                                   ;;   opacity-major

                                   ;;   :else opacity-minor)

        color                      (:color period)
        period-width               (js/parseInt (:period-width uic/svg-consts))
        cx                         (js/parseInt (:cx uic/svg-consts))
        cy                         (js/parseInt (:cy uic/svg-consts))
        ;; radii need to be offset to account for path using
        ;; A (arc) command having radius as the center of path
        ;; instead of edge (like circle)
        r                          (-> (case type
                                         :actual (:r uic/svg-consts)
                                         :planned (:inner-r uic/svg-consts)
                                         (* 0.5 (:inner-r uic/svg-consts)))
                                       (js/parseInt)
                                       (- (/ period-width 2)))

        arc                        (uic/describe-arc cx cy r start-angle stop-angle)
        broken-arc-before          (uic/describe-arc cx cy r
                                                 start-angle
                                                 broken-stop-before-angle)
        broken-arc-after           (uic/describe-arc cx cy r
                                                 broken-start-after-angle
                                                 stop-angle)
        future-handle-arc          (uic/describe-arc cx cy r
                                                     (+ stop-angle
                                                        (-> 5 ;; minutes
                                                            (* 60) ;; seconds
                                                            (* 1000) ;; milleseconds
                                                            (cutils/ms-to-angle)))
                                                     (+ stop-angle
                                                        (-> 25 ;; minutes
                                                            (* 60) ;; seconds
                                                            (* 1000) ;; milleseconds
                                                            (cutils/ms-to-angle))))

        touch-click-handler        (if (not is-period-selected)
                                     (fn [e]
                                       (jsi/stop-propagation e)
                                       (jsi/prevent-default e)
                                       (rf/dispatch
                                        [:set-selected-period id])))
        movement-trigger-handler   (if (and is-period-selected
                                            (= selected-period id))
                                     (fn [e]
                                       (jsi/stop-propagation e)
                                       (rf/dispatch
                                         [:set-moving-period true])))

        yesterday-arrow-point      (cutils/polar-to-cartesian cx cy r 1)
        yesterday-arrow-point-bt   (cutils/polar-to-cartesian
                                     cx cy (+ r (* 0.7 (/ period-width 2))) 3)
        yesterday-arrow-point-bb   (cutils/polar-to-cartesian
                                     cx cy (- r (* 0.7 (/ period-width 2))) 3)

        yesterday-2-arrow-point    (cutils/polar-to-cartesian cx cy r 3)
        yesterday-2-arrow-point-bt (cutils/polar-to-cartesian
                                     cx cy (+ r (* 0.7 (/ period-width 2))) 5)
        yesterday-2-arrow-point-bb (cutils/polar-to-cartesian
                                     cx cy (- r (* 0.7 (/ period-width 2))) 5)

        tomorrow-arrow-point       (cutils/polar-to-cartesian cx cy r 359)
        tomorrow-arrow-point-bt    (cutils/polar-to-cartesian
                                     cx cy (+ r (* 0.7 (/ period-width 2))) 357)
        tomorrow-arrow-point-bb    (cutils/polar-to-cartesian
                                     cx cy (- r (* 0.7 (/ period-width 2))) 357)

        tomorrow-2-arrow-point     (cutils/polar-to-cartesian cx cy r 357)
        tomorrow-2-arrow-point-bt  (cutils/polar-to-cartesian
                                     cx cy (+ r (* 0.7 (/ period-width 2))) 355)
        tomorrow-2-arrow-point-bb  (cutils/polar-to-cartesian
                                    cx cy (- r (* 0.7 (/ period-width 2))) 355)
        prev-next-stroke "0.15"
        selected-dash-array "0.5 0.4"]


    [:g {:key (str id)}
     (if (and straddles-now ;; ticker splitting should only happen when displaying today
              (= (utils/zero-in-day displayed-day)
                 (utils/zero-in-day (new js/Date)))
              (not is-period-selected))
       ;; broken arc
       [:g
        [:path
         {:d            broken-arc-before
          :stroke       color
          :opacity      opacity-before
          :stroke-width period-width
          :fill         "transparent"
          :onClick      touch-click-handler
          :onTouchStart movement-trigger-handler
          :onMouseDown  movement-trigger-handler}]
        [:path
         {:d            broken-arc-after
          :stroke       color
          :opacity      opacity-after
          :stroke-width period-width
          :fill         "transparent"
          :onClick      touch-click-handler
          :onTouchStart movement-trigger-handler
          :onMouseDown  movement-trigger-handler}]
        (when  (= selected-period id)
          [:g
           [:path
            {:d            broken-arc-before
             :stroke       (:text-color uic/app-theme)
             :stroke-dasharray selected-dash-array
             :opacity      opacity
             :stroke-width (* 1.1 period-width)
             :fill         "transparent"}]
           [:path
            {:d            broken-arc-after
             :stroke       (:text-color uic/app-theme)
             :stroke-dasharray selected-dash-array
             :opacity      opacity
             :stroke-width (* 1.1 period-width)
             :fill         "transparent"}]])]

       ;; solid arc
       [:g
        [:path
         {:d            arc
          :stroke       color
          :opacity      opacity
          :stroke-width period-width
          :fill         "transparent"
          :onClick      touch-click-handler
          :onTouchStart movement-trigger-handler
          :onMouseDown  movement-trigger-handler}]
        (when  (= selected-period id)
          [:g
           [:path
            {:d            arc
             :stroke       (:text-color uic/app-theme)
             :stroke-dasharray selected-dash-array
             :opacity      "0.7"
             :stroke-width  period-width
             :fill         "transparent"}]])])

     ;; yesterday arrows TODO change all yesterdays and tomorrows to next and previous days
     (if starts-yesterday
       [:g
        [:polyline {:fill           "transparent"
                    :stroke         "white"
                    :stroke-width   prev-next-stroke
                    :stroke-linecap "round"
                    :points         (str
                                     (:x yesterday-arrow-point-bt) ","
                                     (:y yesterday-arrow-point-bt) " "
                                     (:x yesterday-arrow-point) ","
                                     (:y yesterday-arrow-point) " "
                                     (:x yesterday-arrow-point-bb) ","
                                     (:y yesterday-arrow-point-bb) " ")}]

        [:polyline {:fill           "transparent"
                    :stroke         "white"
                    :stroke-width   prev-next-stroke
                    :stroke-linecap "round"
                    :points         (str
                                     (:x yesterday-2-arrow-point-bt) ","
                                     (:y yesterday-2-arrow-point-bt) " "
                                     (:x yesterday-2-arrow-point) ","
                                     (:y yesterday-2-arrow-point) " "
                                     (:x yesterday-2-arrow-point-bb) ","
                                     (:y yesterday-2-arrow-point-bb) " ")}]])

     ;; tomorrow arrows
     (if stops-tomorrow
       [:g
        [:polyline {:fill           "transparent"
                    :stroke         "white"
                    :stroke-width   prev-next-stroke
                    :stroke-linecap "round"
                    :points         (str
                                     (:x tomorrow-arrow-point-bt) ","
                                      (:y tomorrow-arrow-point-bt) " "
                                      (:x tomorrow-arrow-point) ","
                                      (:y tomorrow-arrow-point) " "
                                      (:x tomorrow-arrow-point-bb) ","
                                      (:y tomorrow-arrow-point-bb) " ")}]

        [:polyline {:fill           "transparent"
                    :stroke         "white"
                    :stroke-width   prev-next-stroke
                    :stroke-linecap "round"
                    :points         (str
                                      (:x tomorrow-2-arrow-point-bt) ","
                                      (:y tomorrow-2-arrow-point-bt) " "
                                      (:x tomorrow-2-arrow-point) ","
                                      (:y tomorrow-2-arrow-point) " "
                                      (:x tomorrow-2-arrow-point-bb) ","
                                      (:y tomorrow-2-arrow-point-bb) " ")}]])]))

(defn periods [periods selected is-moving-period curr-time displayed-day]
  (let [
        ;; whole song and dance for putting the selected period on _top_
        sel-id (get-in selected [:current-selection :id-or-nil])
        selected-period (some #(if (= sel-id (:id %)) %) periods)
        no-sel-periods (filter #(not= (:id %) sel-id) periods)
        sel-last-periods (filter cutils/period-has-stamps
                                 (if (some? sel-id)
                                   (reverse (cons selected-period no-sel-periods))
                                   periods))
        ;; dance done
        actual (filter #(not (:planned %)) sel-last-periods)
        planned (filter #(:planned %) sel-last-periods)
        selected-planned (:planned selected-period)] ;; TODO fuse these and have them use the :planned flag in periods
    [:g
     [:g
      (if (some? actual)
        (->> actual
             (map (fn [actual-period] (period selected
                                              curr-time
                                              is-moving-period
                                              :actual
                                              actual-period
                                              displayed-day)))))]
     [:g
      (if (some? planned)
        (->> planned
             (map (fn [planned-period] (period selected
                                               curr-time
                                               is-moving-period
                                               :planned
                                               planned-period
                                               displayed-day))))
        )]]))



(defn day [tasks selected day]
  (let [date-str                 (subs (jsi/->iso-string day) 0 10)
        curr-time                (:time @uic/clock-state)
        period-in-play           @(rf/subscribe [:period-in-play])
        display-ticker           (= (jsi/value-of (utils/zero-in-day day))
                                    (jsi/value-of (utils/zero-in-day curr-time)))

        ticker-ms                (utils/get-ms curr-time)
        ticker-angle             (cutils/ms-to-angle ticker-ms)
        ticker-pos               (cutils/polar-to-cartesian
                                   (:cx uic/svg-consts)
                                   (:cy uic/svg-consts)
                                   (if (some? period-in-play)
                                     (+ (js/parseFloat
                                         (:border-r uic/svg-consts))
                                        (js/parseFloat
                                         (:circle-stroke uic/svg-consts)))
                                     (+ (js/parseFloat
                                         (:inner-border-r uic/svg-consts))
                                        (js/parseFloat
                                         (:circle-stroke uic/svg-consts))))
                                   ticker-angle)
        filtered-periods         (cutils/filter-periods-for-day day tasks)
        selected-period          (if (= :period
                                        (get-in
                                          selected
                                          [:current-selection :type-or-nil]))
                                   (get-in selected
                                           [:current-selection :id-or-nil])
                                   nil)
        is-moving-period         @(rf/subscribe [:is-moving-period])
        period-in-play-color     @(rf/subscribe [:period-in-play-color])
        long-press-state         @(rf/subscribe [:inline-period-long-press])
        indicator-delay          2000 ;; slightly longer than standard touch (125ms)

        ;; this is all for figuring out how long to set the animation
        ;; relative to where the user indicated they want start for this period
        indicator-start          (:indicator-start long-press-state)
        indicator-relative-ms    (if (some? indicator-start)
                                   (utils/get-ms indicator-start))
        indicator-angle          (if (some? indicator-start)
                                   (cutils/ms-to-angle indicator-relative-ms))
        indicator-max-duration   60000
        indicator-arc-angle      (- 360 indicator-angle)
        indicator-duration       (* indicator-max-duration
                                    (/ indicator-arc-angle  360))

        mobile-user-agent         (re-seq
                                   #"(?i)Android|webOS|iPhone|iPad|iPod|BlackBerry"
                                   (jsi/user-agent))
        start-touch-click-handler (if (and (not is-moving-period)
                                           (not (:press-on long-press-state)))
                                    (fn [elem-id ui-type e]
                                      (let [
                                            ;; figure out what time the user initially touched
                                            svg-coords    (cutils/client-to-view-box elem-id e ui-type)
                                            circle-coords (cutils/point-to-centered-circle
                                                           (merge (select-keys uic/svg-consts [:cx :cy])
                                                                  svg-coords))
                                            angle         (cutils/point-to-angle circle-coords)
                                            relative-time (Math/floor (cutils/angle-to-ms angle))
                                            absolute-time (+ relative-time
                                                             (jsi/value-of (utils/zero-in-day day)))
                                            time-date-obj (new js/Date absolute-time)
                                            ;; set a function to go off after standard touch time
                                            ;; to start the animation and timer
                                            id (.setTimeout
                                                js/window
                                                (fn [_]
                                                  (println "KICKOFF!")
                                                  (rf/dispatch
                                                   [:set-inline-period-long-press
                                                    {:press-on true}]))
                                                indicator-delay)]

                                        ;; (jsi/stop-propagation e)
                                        ;; (jsi/prevent-default e)

                                        ;; set the id to cancel the animation
                                        ;; set the time indicated initially
                                        (println (str "maybe starting..." id))
                                        (rf/dispatch [:set-inline-period-long-press
                                                      {:timeout-id id
                                                       :indicator-start time-date-obj}]))))
        stop-touch-click-handler  (if is-moving-period
                                   (fn [e]
                                     (jsi/prevent-default e)
                                     (rf/dispatch
                                      [:set-moving-period false]))

                                   ;; not moving period and...
                                   (if (and (some? (:timeout-id long-press-state))
                                            (not (:press-on long-press-state)))
                                     (fn [e]
                                       (.clearTimeout js/window (:timeout-id long-press-state))
                                       (rf/dispatch [:set-inline-period-long-press
                                                     {:indicator-start nil
                                                      :stop-time nil
                                                      :timeout-id nil
                                                      :press-on false}])
                                       (println
                                        (str "cancelling inline add..."
                                             (:timeout-id long-press-state))))

                                     ;; either no timeout id or press-on true
                                     (if (:press-on long-press-state)
                                       (fn [_]
                                         (println "This is where we would add")
                                         (rf/dispatch [:set-inline-period-long-press
                                                       {:indicator-start nil
                                                        :stop-time nil
                                                        :timeout-id nil
                                                        :press-on false}])
                                         ;; get now
                                         ;; using the start time of long press state derive the intended duration of the period
                                         ;; use the indicated starte time and derived stop to ...
                                         ;; TODO eventually nav to edit period with start and stop query params
                                         ))))

        deselect                  (if (not is-moving-period)
                                   (fn [e]
                                     (jsi/prevent-default e)
                                     (rf/dispatch
                                      [:set-selected-period nil])))
        zoom @(rf/subscribe [:zoom])]

    [:div {:style {:height "100%" :width "100%"}}
     [:svg (merge {:key         date-str
                   :id          date-str
                   :xmlns "http://www.w3.org/2000/svg"
                   :version  "1.1"
                   :style       {
                                 :display      "inline-box"
                                 ;; this stops scrolling
                                 :touch-action "pinch-zoom"
                                 ;; for moving period
                                 }
                   :width       "100%"
                   :height      "100%"
                   :onClick     deselect}

                  ;; start gets triggered twice on mobile unless we use when statements
                  (when (some? mobile-user-agent)
                    {:onTouchStart (if (some? start-touch-click-handler)
                                     (partial start-touch-click-handler
                                              date-str :touch))
                     :onTouchEnd  stop-touch-click-handler
                     :onTouchMove (if is-moving-period
                                    (partial handle-period-move
                                             date-str :touch))})

                  (when (nil? mobile-user-agent)
                    {:onMouseDown (if (some? start-touch-click-handler)
                                    ;; catch the case when handler is nil otherwise partial freaks out when called
                                    ;; TODO this should be moved up into the let
                                    (partial start-touch-click-handler
                                             date-str :mouse))
                     :onMouseUp   stop-touch-click-handler
                     :onMouseMove (if is-moving-period
                                    (partial handle-period-move
                                             date-str :mouse))})

                  (case zoom
                    :q1 {:viewBox "40 0 55 100"}
                    :q2 {:viewBox "0 0 60 100"}
                    :q3 {:viewBox "0 40 60 60"}
                    :q4 {:viewBox "40 40 60 60"}
                    (select-keys uic/svg-consts [:viewBox])))

      [:circle (merge {:fill (:canvas-color uic/app-theme)}
                      (select-keys uic/svg-consts [:cx :cy :r]))]
      [:circle (merge {:fill "transparent"
                       :stroke (:border-color uic/app-theme)
                       :stroke-width (:circle-stroke uic/svg-consts)
                       :r  (:border-r uic/svg-consts)}
                      (select-keys uic/svg-consts [:cx :cy]))]
      [:circle (merge {:fill "transparent"
                       :stroke (:border-color uic/app-theme)
                       :stroke-width (:circle-stroke uic/svg-consts)
                       :r  (:inner-border-r uic/svg-consts)}
                      (select-keys uic/svg-consts [:cx :cy]))]

      (periods filtered-periods selected is-moving-period curr-time day)

      (when display-ticker
        [:g
         [:circle {:cx (:cx uic/svg-consts) :cy (:cy uic/svg-consts)
                   :r ".7"
                   :fill (if (some? period-in-play)
                           period-in-play-color
                           (:text-color uic/app-theme))
                   :stroke "transparent"}]
         [:line {:fill         "transparent"
                 :stroke-width "1.4"
                 :stroke       (if (some? period-in-play)
                                 period-in-play-color
                                 "white")
                 :stroke-linecap "butt"
                 :opacity      "1"
                 ;; :filter       "url(#shadow-2dp)"
                 ;; filter breaks bounding box and results in zero width or height
                 ;; on vertical and horizontal lines (6, 9, 12, 0)
                 :x1           (:cx uic/svg-consts)
                 :y1           (:cy uic/svg-consts)
                 :x2           (:x ticker-pos)
                 :y2           (:y ticker-pos)}]])

      (when (and (:press-on long-press-state)
                 (nil? selected-period))
        (let [cx            (js/parseInt (:cx uic/svg-consts))
              cy            (js/parseInt (:cy uic/svg-consts))
              r             (js/parseInt (:inner-r uic/svg-consts)) ;; TODO jsi int cast integration and replace all these
              indicator-r   (/ (-> (:period-width uic/svg-consts)
                                   (js/parseInt))
                               3)
              circumference (* (* 2 (jsi/pi)) r)
              arc-length    (* circumference (/ indicator-arc-angle 360))
              point         (cutils/polar-to-cartesian cx cy r indicator-angle)
              indicator-cx  (:x point)
              period-width  (js/parseInt (:period-width uic/svg-consts))
              arc           (uic/describe-arc cx cy r indicator-angle 359)
              indicator-cy  (:y point)]

          (stylefy/keyframes "grow-indicator"
                             [:from {:stroke-dasharray (str "0, " arc-length)}]
                             [:to   {:stroke-dasharray (str arc-length " , 0")}])

          [:g

           [:path
            {:d            arc
             :stroke       (:text-color uic/app-theme)
             :opacity      "0.1"
             :stroke-width (* 1.5 period-width)
             :fill         "transparent"}]

           [:path
            (merge (stylefy/use-style
                    {:animation-duration (str (/ indicator-duration 1000) "s")
                     :animation-timing-function "linear"
                     :animation-name "grow-indicator"})
                   {:d            arc
                    :stroke       (:text-color uic/app-theme)
                    :opacity      "0.5"
                    :stroke-width (* 1.5 period-width)
                    ;; guessed and checked the dasharray
                    ;; the length of the whole circle is a little over 210
                    :stroke-dasharray (str "0 " arc-length)
                    :fill         "transparent"})]]))]]))

(defn days [days tasks selected-period]
  (->> days
       (map (partial day tasks selected-period))))
