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

(ns nl.surf.eduhub.validator.service.config
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [nl.jomco.envopts :as envopts]
            [goose.brokers.redis.broker :as redis.broker]
            [goose.worker]
            [goose.client]
            [goose.retry]))


(def opt-specs
  {:gateway-url                        ["URL of gateway" :str
                                        :in [:gateway-url]]
   :gateway-basic-auth-user            ["Basic auth username of gateway" :str
                                        :in [:gateway-basic-auth :user]]
   :gateway-basic-auth-pass            ["Basic auth password of gateway" :str
                                        :in [:gateway-basic-auth :pass]]
   :allowed-client-ids                 ["Comma separated list of allowed SurfCONEXT client ids." :str
                                        :in [:allowed-client-ids]]
   :max-total-requests                 ["Maximum number of requests that validator is allowed to make before raising an error" :int
                                        :default 10000
                                        :in [:max-total-requests]]
   :surf-conext-client-id              ["SurfCONEXT client id for validation service" :str
                                        :in [:introspection-basic-auth :user]]
   :surf-conext-client-secret          ["SurfCONEXT client secret for validation service" :str
                                        :in [:introspection-basic-auth :pass]]
   :surf-conext-introspection-endpoint ["SurfCONEXT introspection endpoint" :str
                                        :in [:introspection-endpoint-url]]
   :redis-uri                          ["URI to redis" :str :in [:redis-conn :spec :uri]]
   :server-port                        ["Starts the app server on this port" :int]
   :job-status-expiry-seconds          ["Number of seconds before job status in Redis expires" :int
                                        :default (* 3600 24 14)
                                        :in [:expiry-seconds]]
   :validator-service-root-url         ["Root url for the web view; does not include path" :str
                                        :in [:root-url]]
   :ooapi-version                      ["Ooapi version to pass through to gateway" :str
                                        :in [:ooapi-version]]
   :spider-timeout-millis              ["Maximum number of milliseconds before spider timeout." :int
                                        :default 3600000
                                        :in [:spider-timeout-millis]]})

(defn- file-secret-loader-reducer [env-map value-key]
  (let [file-key (keyword (str (name value-key) "-file"))
        path     (file-key env-map)]
    (cond
      (nil? path)
      env-map

      (not (.exists (io/file path)))
      (throw (ex-info (str "ENV var contains filename that does not exist: " path)
                      {:filename path, :env-path file-key}))

      (value-key env-map)
      (do (log/warn "ENV var contains secret both as file and as value" value-key)
          env-map)

      :else
      (-> env-map
          (assoc value-key (str/trim (slurp path)))
          (dissoc file-key)))))

(defn add-goose-config
  [[config errs]]
  (if errs
    [config errs]
    (let [goose-conn-opts {:url (get-in config [:redis-conn :spec :uri])}]
      [(assoc config
              :goose-worker-opts (-> goose.worker/default-opts
                                     (assoc :broker (redis.broker/new-consumer goose-conn-opts)))
              :goose-client-opts (-> goose.client/default-opts
                                     (assoc :retry-opts (assoc goose.retry/default-opts
                                                               :error-handler-fn-sym 'nl.surf.eduhub.validator.service.jobs.client/job-error-handler)
                                            :broker (redis.broker/new-producer goose-conn-opts))))])))

(defn load-config-from-env [env-map]
  (-> (reduce file-secret-loader-reducer env-map (keys opt-specs))
      (envopts/opts opt-specs)
      (add-goose-config)))

(defn validate-and-load-config [env]
  (let [[config errs] (load-config-from-env env)]
    (when errs
      (.println *err* "Error in environment configuration")
      (.println *err* (envopts/errs-description errs))
      (.println *err* "Available environment vars:")
      (.println *err* (envopts/specs-description opt-specs))
      (System/exit 1))
    config))
