;; SPDX-FileCopyrightText: 2024, 2025 SURF B.V.
;; SPDX-License-Identifier: AGPL-3.0-or-later
;; SPDX-FileContributor: Joost Diepenmaat
;; SPDX-FileContributor: Michiel de Mare
;; SPDX-FileContributor: Remco van 't Veer

(ns nl.surf.eduhub.validator.service.jobs.worker
  "Functions called called by a worker thread running in the background."
  (:require [clojure.tools.logging :as log]
            [nl.surf.eduhub.validator.service.jobs.status :as status]
            [nl.surf.eduhub.validator.service.validate :as validate]
            [nl.jomco.resources :refer [closeable]]
            [goose.worker]))

;;;; middleware / config code

(def ^:dynamic *config*
  "The current system configuration as set by `wrap-worker-config`."
  nil)

(defn wrap-worker-config
  [config]
  (fn [next]
    (fn [opts job]
      (binding [*config* config]
        (next opts job)))))

(defn mk-worker
  "Configure and start a goose worker resource. This will start
  multiple background threads and can be stopped by calling
  `nl.jomco.resources/close`

  Ensures that worker functions are called with `*config*` bound to
  the system configuration that was used to start the worker
  resource."
  [{:keys [goose-worker-opts] :as config}]
  (-> goose-worker-opts
      (assoc :middlewares (wrap-worker-config config))
      goose.worker/start
      (closeable goose.worker/stop)))

;;;; Actual background job functions

;; Runs the validate-endpoint function
;; and updates the values in the job status.
;; opts should contain: basic-auth ooapi-version base-url profile

(defn validate-endpoint
  [endpoint-id uuid opts]
  (let [{:keys [redis-conn expiry-seconds]} *config*]
    (assert redis-conn)
    (try
      (let [html (validate/validate-endpoint endpoint-id opts)]
        ;; assuming everything went ok, save html in status, update status and set expiry to value configured in ENV
        (status/set-status-fields redis-conn uuid "finished" {"html-report" html} expiry-seconds))
      (catch Exception ex
        ;; otherwise set status to error, include error message and also set expiry
        (log/error ex "Validate endpoint threw an exception")
        (status/set-status-fields redis-conn uuid "failed" {"error" (str ex)} expiry-seconds)))))
