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
  (log/info "Starting Goose validation worker")
  (-> goose-worker-opts
      (assoc :middlewares (wrap-worker-config config))
      goose.worker/start
      (closeable goose.worker/stop)))

;;;; Actual background job functions

;; Runs the validate-endpoint function
;; and updates the values in the job status.
;; opts should contain: basic-auth base-url profile

(defn validate-endpoint
  [endpoint-id uuid opts]
  (let [started-at (System/nanoTime)
        profile    (:profile opts)]
    (log/infof "Worker received validation job uuid=%s endpoint-id=%s profile=%s"
               uuid endpoint-id profile)
    (try
      (let [{:keys [redis-conn expiry-seconds]} *config*]
        (when-not redis-conn
          (throw (ex-info "Worker Redis connection is not configured" {})))
        (log/infof "Starting endpoint validation uuid=%s endpoint-id=%s profile=%s"
                   uuid endpoint-id profile)
        (let [html (validate/validate-endpoint endpoint-id opts)]
          ;; assuming everything went ok, save html in status, update status and set expiry to value configured in ENV
          (status/set-status-fields redis-conn uuid "finished" {"html-report" html} expiry-seconds)
          (log/infof "Finished validation job uuid=%s endpoint-id=%s profile=%s duration-ms=%d"
                     uuid endpoint-id profile
                     (long (/ (- (System/nanoTime) started-at) 1000000)))))
      (catch Exception ex
        (log/error ex (format "Validation job failed uuid=%s endpoint-id=%s profile=%s duration-ms=%d"
                              uuid endpoint-id profile
                              (long (/ (- (System/nanoTime) started-at) 1000000))))
        ;; Config/Redis failures cannot always be persisted, so protect this update and
        ;; retain the original exception in the logs.
        (when-let [redis-conn (:redis-conn *config*)]
          (try
            (status/set-status-fields redis-conn uuid "failed" {"error" (str ex)} (:expiry-seconds *config*))
            (catch Exception status-ex
              (log/error status-ex (format "Could not store failed status for validation job uuid=%s" uuid)))))))))
