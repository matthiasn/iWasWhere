(ns meo.jvm.core
  "In this namespace, the individual components are initialized and wired
  together to form the backend system."
  (:gen-class)
  (:require [matthiasn.systems-toolbox.switchboard :as sb]
            [matthiasn.systems-toolbox-sente.server-http-kit :as sente]
            [matthiasn.systems-toolbox.scheduler :as sched]
            [meo.jvm.index :as idx]
            [meo.common.specs]
            [clj-pid.core :as pid]
            [meo.jvm.log]
            [meo.jvm.store :as st]
            [meo.jvm.fulltext-search :as ft]
            [meo.jvm.upload :as up]
            [meo.jvm.backup :as bak]
            [meo.jvm.imports :as i]
            [taoensso.timbre :refer [info]]
            [meo.jvm.graphql :as gql]))

(defonce switchboard (sb/component :backend/switchboard))

(def cmp-maps
  #{(sente/cmp-map :backend/ws idx/sente-map)
    (sched/cmp-map :backend/scheduler)
    (i/cmp-map :backend/imports)
    (st/cmp-map :backend/store)
    (gql/cmp-map :backend/graphql)
    (bak/cmp-map :backend/backup)
    (up/cmp-map :backend/upload switchboard)
    (ft/cmp-map :backend/ft)})

(defn restart!
  "Starts or restarts system by asking switchboard to fire up the ws-cmp for
   serving the client side application and providing bi-directional
   communication with the client, plus the store and imports components.
   Then, routes messages to the store and imports components for which those
   have a handler function. Also route messages from imports to store component.
   Finally, sends all messages from store component to client via the ws
   component."
  [switchboard cmp-maps opts]
  (sb/send-mult-cmd
    switchboard
    [[:cmd/init-comp cmp-maps]

     [:cmd/route {:from :backend/ws
                  :to   #{:backend/store
                          :backend/graphql
                          :backend/export
                          :backend/upload
                          :backend/imports}}]

     [:cmd/route {:from :backend/imports
                  :to   :backend/store}]

     [:cmd/route {:from :backend/graphql
                  :to   :backend/ws}]

     [:cmd/route {:from :backend/upload
                  :to   #{:backend/store
                          :backend/scheduler
                          :backend/ws}}]

     [:cmd/route {:from :backend/store
                  :to   #{:backend/ws
                          :backend/graphql
                          :backend/ft}}]

     [:cmd/route {:from :backend/scheduler
                  :to   #{:backend/store
                          :backend/backup
                          :backend/imports
                          :backend/graphql
                          :backend/upload
                          :backend/ws}}]

     [:cmd/route {:from #{:backend/store
                          :backend/graphql
                          :backend/upload
                          :backend/backup
                          :backend/imports}
                  :to   :backend/scheduler}]

     (when (:inspect opts)
       [:cmd/attach-to-firehose :backend/kafka-firehose])

     (when (:read-logs opts)
       [:cmd/send {:to  :backend/store
                   :msg [:startup/read]}])

     [:cmd/send {:to  :backend/scheduler
                 :msg [:cmd/schedule-new {:timeout (* 5 60 1000)
                                          :message [:import/spotify]
                                          :repeat  true
                                          :initial false}]}]]))

(defn -main
  "Starts the application from command line, saves and logs process ID. The
   system that is fired up when restart! is called proceeds in core.async's
   thread pool. Since we don't want the application to exit when the current
   thread is out of work, we just put it to sleep."
  [& _args]
  (pid/save "meo.pid")
  (pid/delete-on-shutdown! "meo.pid")
  (info "meo started, PID" (pid/current))
  (restart! switchboard cmp-maps {:read-logs true})
  (Thread/sleep Long/MAX_VALUE))
