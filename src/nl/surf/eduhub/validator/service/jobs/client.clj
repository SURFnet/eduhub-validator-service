(ns nl.surf.eduhub.validator.service.jobs.client
  (:require [clojure.tools.logging :as log]
            [goose.brokers.redis.broker :as broker]
            [goose.client :as c]
            [goose.retry :as retry]
            [nl.surf.eduhub.validator.service.jobs.status :as status]
            [nl.surf.eduhub.validator.service.jobs.worker :as worker])
  (:import [java.util UUID]))

(defn job-error-handler [_cfg _job ex]
  (log/error ex "Error in job"))

(def client-opts (assoc c/default-opts
                   :broker (broker/new-producer broker/default-opts)
                   :retry-opts (assoc retry/default-opts :error-handler-fn-sym `job-error-handler)))

;; Enqueue the validate-endpoint call in the worker queue.
(defn enqueue-validation
  [endpoint-id profile {:keys [redis-conn gateway-basic-auth gateway-url ooapi-version max-total-requests] :as _config}]
  (let [uuid (str (UUID/randomUUID))
        prof (or profile "rio")
        opts {:basic-auth         gateway-basic-auth
              :base-url           gateway-url
              :max-total-requests max-total-requests
              :ooapi-version      ooapi-version
              :profile            prof}]
    (status/set-status-fields redis-conn uuid "pending" {:endpoint-id endpoint-id, :profile prof} nil)
    (c/perform-async client-opts `worker/validate-endpoint endpoint-id uuid opts)
    {:status 200 :body {:job-status "pending" :uuid uuid, :web-url (str "http://localhost:3002/view/status/" uuid)}}))
