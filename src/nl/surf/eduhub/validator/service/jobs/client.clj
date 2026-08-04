(ns nl.surf.eduhub.validator.service.jobs.client
  (:require [clojure.tools.logging :as log]
            [goose.client :as c]
            [nl.surf.eduhub.validator.service.jobs.status :as status]
            [nl.surf.eduhub.validator.service.jobs.worker :as worker])
  (:import [java.util UUID]))

(defn job-error-handler [_cfg _job ex]
  ;; Do not log the complete Goose job: its arguments include gateway credentials.
  (log/error ex "Goose failed while processing a queued validation job"))

;; Enqueue the validate-endpoint call in the worker queue.
(defn enqueue-validation
  [endpoint-id profile {:keys [redis-conn gateway-basic-auth gateway-url max-total-requests root-url goose-client-opts expiry-seconds] :as _config}]
  (let [uuid (str (UUID/randomUUID))
        prof (or profile "ooapi")
        opts {:basic-auth         gateway-basic-auth
              :base-url           gateway-url
              :max-total-requests max-total-requests
              :profile            prof}]
    (log/infof "Creating validation job uuid=%s endpoint-id=%s profile=%s" uuid endpoint-id prof)
    (status/set-status-fields redis-conn uuid "pending" {:endpoint-id endpoint-id, :profile prof} nil)
    (log/infof "Stored pending validation status uuid=%s; enqueueing job" uuid)
    (try
      (c/perform-async goose-client-opts `worker/validate-endpoint endpoint-id uuid opts)
      (log/infof "Enqueued validation job uuid=%s endpoint-id=%s profile=%s" uuid endpoint-id prof)
      {:status 200 :body {:job-status "pending" :uuid uuid, :web-url (str root-url "/view/status/" uuid)}}
      (catch Exception ex
        (log/error ex (format "Failed to enqueue validation job uuid=%s endpoint-id=%s profile=%s"
                              uuid endpoint-id prof))
        (status/set-status-fields redis-conn uuid "failed"
                                  {"error" "Failed to enqueue validation job"}
                                  expiry-seconds)
        (throw ex)))))
