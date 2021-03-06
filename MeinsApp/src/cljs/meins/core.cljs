(ns meins.core
  (:require ["react-native" :refer [AppRegistry]]
            ["react-native-exception-handler" :refer [setJSExceptionHandler
                                                      setNativeExceptionHandler]]
            ["react-native-gesture-handler"]
            [matthiasn.systems-toolbox.scheduler :as sched]
            [matthiasn.systems-toolbox.switchboard :as sb]
            [meins.components.geolocation :as geo]
            [meins.components.healthkit :as hk]
            [meins.components.photos :as photos]
            [meins.components.store :as store]
            [meins.components.sync :as sync]
            [meins.crypto]
            [meins.helpers]
            [meins.ui :as ui]
            [taoensso.timbre :refer-macros [error info]]))

(enable-console-print!)
(defonce switchboard (sb/component :client/switchboard))
(def OBSERVER false)

(defn make-observable [components]
  (if OBSERVER
    (let [mapper #(assoc-in % [:opts :msgs-on-firehose] true)]
      (println "CORE: Attaching firehose")
      (set (mapv mapper components)))
    components))

(defn init []
  (let [components #{(hk/cmp-map :app/healthkit)
                     (store/cmp-map :app/store)
                     (photos/cmp-map :app/photos)
                     (sync/cmp-map :app/sync)
                     (geo/cmp-map :app/geo)
                     (sched/cmp-map :app/scheduler)
                     (ui/cmp-map :app/ui-cmp)}
        components (make-observable components)]
    (sb/send-mult-cmd
      switchboard
      [[:cmd/init-comp components]

       [:cmd/route {:from :app/ui-cmp
                    :to   #{:app/geo
                            :app/healthkit
                            :app/photos
                            :app/scheduler
                            :app/store
                            :app/sync}}]

       [:cmd/observe-state {:from :app/store
                            :to   :app/ui-cmp}]

       [:cmd/observe-state {:from :app/store
                            :to   :app/sync}]

       [:cmd/route {:from #{:app/healthkit
                            :app/geo}
                    :to   :app/store}]

       [:cmd/route {:from :app/store
                    :to   :app/sync}]

       [:cmd/route {:from :app/sync
                    :to   #{:app/store
                            :app/scheduler}}]

       [:cmd/route {:from :app/store
                    :to   :app/scheduler}]

       [:cmd/route {:from :app/scheduler
                    :to   #{:app/store
                            :app/sync
                            :app/healthkit}}]

       #_(when OBSERVER
           [:cmd/attach-to-firehose :app/sync])

       [:cmd/send {:to  :app/scheduler
                   :msg [:cmd/schedule-new {:timeout 10000
                                            :message [:sync/fetch]
                                            :repeat  true
                                            :initial false}]}]])
    (.registerComponent AppRegistry "meins" #(identity ui/app-container))
    (setJSExceptionHandler (fn [error _is-fatal] (error error)))
    (setNativeExceptionHandler #(error %) false)))
