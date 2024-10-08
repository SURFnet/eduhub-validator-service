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
  (:require [environ.core :refer [env]]
            [goose.brokers.redis.broker :as broker]
            [goose.worker :as w]
            [nl.surf.eduhub.validator.service.api :as api]
            [nl.surf.eduhub.validator.service.config :as config]
            [ring.adapter.jetty :refer [run-jetty]]))

;; Starts a Jetty server on given port.
(defn start-server [routes {:keys [server-port] :as _config}]
  (let [server  (-> routes

                    (run-jetty {:port server-port :join? false}))
        handler ^Runnable (fn [] (.stop server))]
    ;; Add a shutdown hook to stop Jetty on JVM exit (Ctrl+C)
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. handler))
    server))

(defn -main [& _]
  (let [config (config/validate-and-load-config env)]
    ;; set config as global var (read-only) so that the workers can access it
    (w/start (assoc w/default-opts
               :broker (broker/new-consumer broker/default-opts)
               :middlewares (fn [app]
                             (fn [opts job]
                               ;; adds config to map at the last of the args in job
                               (let [job-args (assoc-in (vec (:args job)) [2 :config] config)
                                     new-job (assoc job :args job-args)]
                                 (app opts new-job))))))
    (start-server (api/compose-app config :auth-enabled) config)))
