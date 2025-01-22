(ns nl.surf.eduhub.validator.service.checker
  (:require [babashka.json :as json]
            [clojure.tools.logging :as log]
            [nl.jomco.http-status-codes :as http-status]
            [nl.surf.eduhub.validator.service.validate :as validate]))

(defn- handle-check-endpoint-response [status body endpoint-id]
  (if (= http-status/ok status)
    ;; If the gateway returns OK we assume we've gotten a json envelope response and check the response status
    ;; of the endpoint.
    (let [envelope        (json/read-str body)
          envelope-status (get-in envelope [:gateway :endpoints (keyword endpoint-id) :responseCode])]
      {:status http-status/ok
       :body   (if (= "200" (str envelope-status))
                 {:valid true}
                 ;; If the endpoint denied the gateway's request or otherwise returned a response outside the 200 range
                 ;; we return 200 ok and return the status of the endpoint and the message of the gateway in our response
                 {:valid false
                  :message (str "Endpoint validation failed with status: " envelope-status)})})

    ;; If the gateway returns something else than a 200, treat it similar to an error
    (let [error-msg (str "Unexpected response status received from gateway: " status)]
      (log/error error-msg)
      {:status http-status/internal-server-error
       :body   {}})))

;; The endpoint checker from phase 1. This connects to an endpoint via the gateway and checks if it receives
;; a valid response.
(defn check-endpoint [endpoint-id config]
  (try
    (let [{:keys [status body]} (validate/check-endpoint endpoint-id config)]
      ;; If the client credentials for the validator are incorrect, the wrap-authentication
      ;; middleware has already returned 401 forbidden and execution doesn't get here.
      (handle-check-endpoint-response status body endpoint-id))
    (catch Throwable e
      (log/error e "Internal error in validator-service")
      {:status http-status/internal-server-error
       :body   {:error "Internal server error"}})))
