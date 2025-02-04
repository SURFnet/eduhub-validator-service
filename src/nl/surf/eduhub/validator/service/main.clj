;; This file is part of eduhub-validator-service
;;
;; Copyright (C) 2024 SURFnet B.V.
;;
;; This program is free software: you can redistribute it and/or
;; modify it under the terms of the GNU Affero General Public License
;; as published by the Free Software Foundation, either version 3 of
;; the License, or (at your option) any later version.
;;
;; This program is distributed in the hope that it will be useful, but
;; WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
;; Affero General Public License for more details.
;;
;; You should have received a copy of the GNU Affero General Public
;; License along with this program.  If not, see
;; <https://www.gnu.org/licenses/>.

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
