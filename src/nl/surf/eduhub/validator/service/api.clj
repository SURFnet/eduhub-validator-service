;; SPDX-FileCopyrightText: 2024, 2025 SURF B.V.
;; SPDX-License-Identifier: AGPL-3.0-or-later
;; SPDX-FileContributor: Joost Diepenmaat
;; SPDX-FileContributor: Michiel de Mare
;; SPDX-FileContributor: Remco van 't Veer

(ns nl.surf.eduhub.validator.service.api
  (:require [clojure.string :as string]
            [compojure.core :refer [GET POST]]
            [compojure.route :as route]
            [clojure.tools.logging :as log]
            [nl.jomco.http-status-codes :as http-status]
            [nl.surf.eduhub.validator.service.authentication :as auth]
            [nl.surf.eduhub.validator.service.checker :as checker]
            [nl.surf.eduhub.validator.service.jobs.client :as jobs-client]
            [nl.surf.eduhub.validator.service.jobs.status :as status]
            [nl.surf.eduhub.validator.service.views.status :as views.status]
            [ring.middleware.defaults :refer [api-defaults wrap-defaults]]
            [ring.middleware.json :refer [wrap-json-response]]
            [ring.middleware.resource :refer [wrap-resource]]))

;; Turn the contents of a job status (stored in redis) into an http response.
(defn- job-status-handler [uuid {:keys [redis-conn] :as _config}]
  (let [job-status (status/load-status redis-conn uuid)]
    (if (empty? job-status)
      {:status http-status/not-found}
      {:status http-status/ok :body (dissoc job-status :html-report)})))

(defn- view-report-handler [uuid {:keys [redis-conn] :as _config} {:keys [download]}]
  (let [validation (status/load-status redis-conn uuid)]
    (if (= "finished" (:job-status validation))
      {:status http-status/ok :body (:html-report validation) :download download}
      {:status http-status/see-other :headers {"Location" (str "/view/status/" uuid)}})))

(defn- delete-report-handler [uuid {:keys [redis-conn] :as _config}]
  (status/delete-status redis-conn uuid)
  {:status http-status/see-other :headers {"Location" (str "/view/status/" uuid)}})

(defn- view-status-handler [uuid {:keys [redis-conn] :as _config}]
  (let [validation (status/load-status redis-conn uuid)]
    (if validation
      {:status http-status/ok :body (views.status/render (assoc validation :uuid uuid))}
      {:status http-status/not-found :body (views.status/render-not-found)})))

(defn wrap-html-response [app]
  (fn html-response [req]
    (let [resp (app req)]
      (cond
        (and (string? (:body resp))
             (:download resp))
        (update-in resp [:headers] merge
                   {"Content-Type" "text/html; charset=UTF-8"
                    "Content-Disposition" "attachment; filename=\"validation-report.html\""})

        (string? (:body resp))
        (assoc-in resp [:headers "Content-Type"] "text/html; charset=UTF-8")

        :else resp))))

(defn public-routes [config]
  (-> (compojure.core/routes
        (GET "/status/:uuid" [uuid]
          (job-status-handler uuid config))
        (GET "/view/report/:uuid" [uuid]
          (view-report-handler uuid config {:download false}))
        (GET "/download/report/:uuid" [uuid]
          (view-report-handler uuid config {:download true}))
        (GET "/view/status/:uuid" [uuid]
          (view-status-handler uuid config))
        (POST "/delete/report/:uuid" [uuid]
          (delete-report-handler uuid config)))
      (wrap-resource "public")
      (wrap-html-response)
      (wrap-json-response)
      (wrap-defaults api-defaults)))

(defn private-routes [{:keys [introspection-endpoint-url introspection-basic-auth
                              allowed-client-ids] :as config} auth-disabled]
  (let [allowed-client-id-set (set (string/split allowed-client-ids #","))
        auth-opts             {:auth-disabled (boolean auth-disabled)}]
    (-> (compojure.core/routes
         (GET "/configstatus/:endpoint-id" [endpoint-id]
            (checker/check-endpoint endpoint-id config))
         (POST "/jobs/paths/:endpoint-id" [endpoint-id profile]
            (jobs-client/enqueue-validation endpoint-id profile config)))
        (auth/wrap-authentication introspection-endpoint-url introspection-basic-auth allowed-client-id-set auth-opts)
        (wrap-json-response)
        (wrap-defaults api-defaults))))

(defn error-message-from-response
  [{:keys [status body]}]
  (when (or (http-status/client-error-status? status)
            (http-status/server-error-status? status))
    (or (:message body)
        (let [msg (string/replace (str body) #"\s+" " ")]
          (if (<= 300 (count msg))
            (subs msg 0 300)
            msg)))))

(defn wrap-log
  [handler]
  (fn [request]
    (try (let [{:keys [status] :as response} (handler request)]
           (if (http-status/server-error-status? status)
             (log/error (str (:status response) " " (:request-method request) " " (:uri request)))
             (log/info (str (:status response) " " (:request-method request) " " (:uri request))))
           (when-let [msg (error-message-from-response response)]
             (log/error msg))
           response)
         (catch Exception e
           (log/error e
                      (str "Exception handling "  (:request-method request) " " (:uri request) ": " (ex-message e)))
           (throw e)))))

;; Compose the app from the routes and the wrappers. Authentication can be disabled for testing purposes.
(defn compose-app [config auth-disabled]
  (-> (compojure.core/routes
       (public-routes config)
       (private-routes config auth-disabled)
       (route/not-found "Not Found"))
      (wrap-log)))
