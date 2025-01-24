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
            [goose.worker :as w]
            [clojure.tools.logging :as log]
            [nl.jomco.resources :refer [mk-system closeable with-resources wait-until-interrupted]]
            [nl.surf.eduhub.validator.service.redis-check :refer [check-redis-connection]]
            [nl.surf.eduhub.validator.service.api :as api]
            [nl.surf.eduhub.validator.service.config :as config]
            [ring.adapter.jetty :refer [run-jetty]]))

(defn run-system
  [{:keys [server-port goose-worker-opts] :as config}]
  (mk-system [worker (-> (w/start goose-worker-opts)
                         (closeable w/stop))
              web-app (api/compose-app config true)
              jetty (run-jetty web-app
                               {:port  server-port
                                :join? false})]
    {:worker  worker
     :web-app web-app
     :jetty   jetty}))


(defn -main [& _]
  (let [config (config/validate-and-load-config env)]
    (try (check-redis-connection config)
         (catch Exception e
           (log/error e "Error checking Redis connection")
           (System/exit 1)))
    (with-resources [_ (run-system config)]
      (wait-until-interrupted))
    (shutdown-agents)))
