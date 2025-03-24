;; SPDX-FileCopyrightText: 2024, 2025 SURF B.V.
;; SPDX-License-Identifier: AGPL-3.0-or-later
;; SPDX-FileContributor: Joost Diepenmaat
;; SPDX-FileContributor: Michiel de Mare
;; SPDX-FileContributor: Remco van 't Veer

(ns nl.surf.eduhub.validator.service.main
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [environ.core :refer [env]]
            [nl.jomco.resources :refer [mk-system Resource wait-until-interrupted with-resources]]
            [nl.surf.eduhub.validator.service.api :as api]
            [nl.surf.eduhub.validator.service.config :as config]
            [nl.surf.eduhub.validator.service.jobs.worker :as jobs.worker]
            [nl.surf.eduhub.validator.service.redis-monitor :refer [check-redis-connection run-redis-monitor]]
            [ring.adapter.jetty :refer [run-jetty]]))

;; Ensure jetty server is stopped when system is stopped
(extend-protocol Resource
  org.eclipse.jetty.server.Server
  (close [server]
    (.stop server)))

(defn run-system
  [{:keys [server-port] :as config}]
  (let [parent-thread (Thread/currentThread)]
    (mk-system [worker (jobs.worker/mk-worker config)
                web-app (api/compose-app config true)
                jetty (run-jetty web-app
                                 {:port  server-port
                                  :join? false})
                redis-monitor (run-redis-monitor config #(.interrupt parent-thread))]
      {:worker        worker
       :redis-monitor redis-monitor
       :web-app       web-app
       :jetty         jetty})))

(defn -main [& _]
  (let [config (config/validate-and-load-config env)]
    (try (check-redis-connection config)
         (catch Exception e
           (log/error e "Error checking Redis connection")
           (System/exit 1)))
    (with-resources [_ (run-system config)]
      (wait-until-interrupted))
    (shutdown-agents)))
