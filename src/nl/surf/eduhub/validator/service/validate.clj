;; SPDX-FileCopyrightText: 2024, 2025 SURF B.V.
;; SPDX-License-Identifier: AGPL-3.0-or-later
;; SPDX-FileContributor: Joost Diepenmaat
;; SPDX-FileContributor: Michiel de Mare
;; SPDX-FileContributor: Remco van 't Veer

(ns nl.surf.eduhub.validator.service.validate
  (:gen-class)
  (:require [babashka.http-client :as http]
            [clojure.tools.logging :as log]
            [nl.jomco.apie.main :as apie])
  (:import [clojure.lang ExceptionInfo]
           [java.io File]))

;; Validates whether the endpoint is working and reachable at all.
(defn check-endpoint
  "Performs a synchronous validation via the eduhub-validator"
  [endpoint-id {:keys [gateway-url gateway-basic-auth ooapi-version] :as _config}]
  {:pre [gateway-url]}
  (let [url      (str gateway-url (if (.endsWith gateway-url "/") "" "/") "courses")
        opts     {:headers    {"x-route"             (str "endpoint=" endpoint-id)
                               "accept"              (str "application/json; version=" ooapi-version)
                               "x-envelope-response" "true"}
                  :basic-auth gateway-basic-auth
                  :throw      false}
        response (http/get url opts)]
    (log/info (str (:status response) " :get " url opts))
    response))

;; Uses the ooapi validator to validate an endpoint.
;; Returns the generated HTML report.
(defn validate-endpoint
  "Returns the HTML validation report as a String."
  [endpoint-id {:keys [basic-auth ooapi-version max-total-requests base-url profile spider-timeout-millis] :as opts}]
  {:pre [endpoint-id basic-auth ooapi-version base-url profile]}
  (let [report-file       (File/createTempFile "report" ".html")
        report-path       (.getAbsolutePath report-file)
        observations-file (File/createTempFile "observations" ".edn")
        observations-path (.getAbsolutePath observations-file)
        defaults {:bearer-token nil,
                  :no-report? false,
                  :max-total-requests max-total-requests,
                  :report-path report-path,
                  :headers {:x-route (str "endpoint=" endpoint-id),
                            :accept (str "application/json; version=" ooapi-version),
                            :x-envelope-response "false"},
                  :no-spider? false,
                  :max-requests-per-operation ##Inf,
                  :spider-timeout-millis spider-timeout-millis,
                  :observations-path observations-path,
                  :profile profile}]
    (try
      (apie/main (merge defaults opts))
      (slurp report-path)
      (catch ExceptionInfo ex
        (when-let [dr (:during-request (ex-data ex))]
          (log/error ex (str "Timeout during request " (prn-str dr))))
        (throw ex))
      (finally
        (.delete observations-file)
        (.delete report-file)))))
