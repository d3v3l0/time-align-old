(ns time-align.db
  (:require [clojure.spec :as s]
            [clojure.test.check.generators :as gen]
            [time-align.utilities :as utils]
            [clojure.string :as string]

            [cljs.pprint :refer [pprint]]
            ))

(s/def ::name (s/and string? #(> 256 (count %))))
(s/def ::description string?)
(s/def ::email string?)
(s/def ::id uuid?)
(s/def ::moment (s/with-gen inst?
                  #(s/gen utils/time-set)))
(s/def ::start ::moment)
(s/def ::stop ::moment)
(s/def ::priority int?)
(s/def ::period (s/with-gen (s/and
                             (s/keys :req-un [::id]
                                     :opt-un [::start ::stop ::description])
                             (fn [period]
                               (if (and
                                    (contains? period :start)
                                    (contains? period :stop))
                                 (> (.valueOf (:stop period))
                                    (.valueOf (:start period)))
                                 true
                                 ))
                             )

                  ;; generator uses a generated moment and adds a random amount of time to it
                  ;; < 2 hrs
                  #(gen/fmap (fn [moment]
                               (let [queue-chance (> 0.5 (rand))
                                     desc-chance (> 0.5 (rand))
                                     start (.valueOf moment)
                                     stop  (->> start
                                                (+ (rand-int (* 2 utils/hour-ms))))
                                     stamps (if queue-chance
                                              {}
                                              {:start (new js/Date start)
                                               :stop (new js/Date stop)})
                                     desc (if desc-chance
                                            {:description (gen/generate (s/gen ::description))}
                                            {})]

                                 (merge stamps desc {:id (random-uuid)})))
                             (s/gen ::moment))))
(s/def ::periods (s/coll-of ::period :gen-max 5))
(s/def ::hex-digit (s/with-gen (s/and string? #(contains? (set "0123456789abcdef") %))
                      #(s/gen (set "0123456789abcdef"))))
(s/def ::hex-str (s/with-gen (s/and string? (fn [s] (every? #(s/valid? ::hex-digit %) (seq s))))
                   #(gen/fmap string/join (gen/vector (s/gen ::hex-digit) 6))))
(s/def ::color (s/with-gen
                 (s/and #(= "#" (first %))
                        #(s/valid? ::hex-str (string/join (rest %)))
                        #(= 7 (count %)))
                 #(gen/fmap
                   (fn [hex-str] (string/join (cons "#" hex-str)))
                   (s/gen ::hex-str))))
;; (s/def ::dependency ::id) ;; TODO do tasks and periods have dependencies how to validate that they point correctly?
;; (s/def ::dependencies (s/coll-of ::dependency))
(s/def ::complete boolean?)
;; think about adding a condition that queue tasks (no periods) have to have planned true
;; (? and priority)
;; tasks that are not planned (:actual) cannot have periods in the future
;; adding date support is going to need some cljc trickery
(s/def ::actual-period (s/and ::period
                              (fn [period]
                                (and (contains? period :start)
                                     (contains? period :stop)))))
(s/def ::actual-periods (s/coll-of ::actual-period ::gen-max 2))
(s/def ::planned-periods ::periods)
(s/def ::task (s/keys :req-un [::id ::name ::description ::complete]
                      :opt-un [::actual-periods ::planned-periods]))
;; TODO complete check (all periods are planned/actual are passed)
(s/def ::tasks (s/coll-of ::task :gen-max 10))
(s/def ::user (s/keys :req-un [::name ::id ::email]))
(s/def ::category (s/keys :req-un [::id ::name ::color ::tasks]))
(s/def ::categories (s/coll-of ::category :gen-max 20))
(s/def ::page  #{:home})
(s/def ::type #{:category :task :period})
(s/def ::type-or-nil (s/with-gen
                       (s/or :is-type ::type
                             :is-nil nil?)
                       #(gen/return nil)))
(s/def ::id-or-nil (s/with-gen
                       (s/or :is-id ::id
                             :is-nil nil?)
                       #(gen/return nil)))
(s/def ::current-selection (s/and (s/keys :req-un [::type-or-nil ::id-or-nil])
                                  (fn [sel] (if (some? (:type-or-nil sel))
                                              (some? (:id-or-nil sel))
                                              false))))
(s/def ::previous-selection (s/and (s/keys :req-un [::type-or-nil ::id-or-nil])
                                   (fn [sel] (if (some? (:type-or-nil sel))
                                               (some? (:id-or-nil sel))
                                               false))))
(s/def ::selected (s/keys :req-un [::current-selection
                                   ::previous-selection]))
(s/def ::main-drawer (s/with-gen
                       boolean?
                       #(gen/return false)))
(s/def ::view (s/keys :req-un [::page
                               ::selected
                               ::main-drawer]))
(s/def ::db (s/keys :req-un [::user ::view ::categories]))
(def default-db (gen/generate (s/gen ::db)))

