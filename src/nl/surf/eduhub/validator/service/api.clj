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

(ns nl.surf.eduhub.validator.service.api
  (:require [clojure.string :as str]
            [compojure.core :refer [GET POST]]
            [compojure.route :as route]
            [nl.jomco.http-status-codes :as http-status]
            [nl.surf.eduhub.validator.service.authentication :as auth]
            [nl.surf.eduhub.validator.service.checker :as checker]
            [nl.surf.eduhub.validator.service.jobs.client :as jobs-client]
            [nl.surf.eduhub.validator.service.jobs.status :as status]
            [nl.surf.eduhub.validator.service.views.root :as views.root]
            [nl.surf.eduhub.validator.service.views.status :as views.status]
            [ring.middleware.defaults :refer [api-defaults wrap-defaults]]
            [ring.middleware.json :refer [wrap-json-response]]
            [ring.middleware.resource :refer [wrap-resource]]))

;; Many response handlers have the same structure - with this function they can be written inline.
;; `activate-handler?` is a function that takes a request and returns a boolean which determines if
;; the current handler should be activated (or skipped).
;; `response-handler` takes an intermediate response and processes it into the next step.
(defn wrap-response-handler [app action response-handler]
  (fn [req]
    (let [resp (app req)]
      (if (= action (:action resp))
        (response-handler (dissoc resp :action))
        resp))))

;; Turn the contents of a job status (stored in redis) into an http response.
(defn- job-status-handler [{:keys [redis-conn] :as _config}]
  (fn handle-job-status [resp]
    (let [job-status (status/load-status redis-conn (:uuid resp))]
      (if (empty? job-status)
        {:status http-status/not-found}
        {:status http-status/ok :body (dissoc job-status :html-report)}))))

(defn- view-report-handler [{:keys [redis-conn] :as _config}]
  (fn view-status [{:keys [uuid] :as _resp}]
    (let [validation (status/load-status redis-conn uuid)]
      (if (= "finished" (:job-status validation))
        {:status http-status/ok :body (:html-report validation) :headers {"Content-Type" "text/html; charset=UTF-8"}}
        {:status http-status/not-found :body ""}))))

(defn- delete-report-handler [{:keys [redis-conn] :as _config}]
  (fn delete-report [{:keys [uuid] :as _resp}]
    (status/delete-status redis-conn uuid)
    {:status http-status/see-other :headers {"Location" "/" "Content-Type" "text/html; charset=UTF-8"}}))

(defn- view-root-handler [config]
  (fn view-status [{:keys [not-found] :as _resp}]
    {:status http-status/ok :body (views.root/render not-found config) :headers {"Content-Type" "text/html; charset=UTF-8"}}))

(defn- view-status-handler [{:keys [redis-conn] :as config}]
  (fn view-status [{:keys [uuid] :as _resp}]
    (let [validation (status/load-status redis-conn uuid)]
      (if validation
        {:status http-status/ok :body (views.status/render (assoc validation :uuid uuid) config) :headers {"Content-Type" "text/html; charset=UTF-8"}}
        {:status http-status/see-other :headers {"Location" "/?not-found=true" "Content-Type" "text/html; charset=UTF-8"}}))))

(defn public-routes [config]
  (-> (compojure.core/routes
        (GET "/" [not-found]
          {:action :view-root, :public true, :not-found (= "true" not-found)})
        (GET "/status/:uuid" [uuid]
          {:action :load-status, :uuid uuid})
        (GET "/view/report/:uuid" [uuid]
          {:action :view-report, :public true :uuid uuid})
        (GET "/view/status/:uuid" [uuid]
          {:action :view-status, :public true :uuid uuid})
        (POST "/delete/report/:uuid" [uuid]
          {:action :delete-report, :public true :uuid uuid}))
      (wrap-resource "public")
      (wrap-response-handler :load-status   (job-status-handler config))
      (wrap-response-handler :view-report   (view-report-handler config))
      (wrap-response-handler :delete-report (delete-report-handler config))
      (wrap-response-handler :view-status   (view-status-handler config))
      (wrap-response-handler :view-root     (view-root-handler config))
      (wrap-json-response)
      (wrap-defaults api-defaults)))

(defn private-routes [{:keys [introspection-endpoint-url introspection-basic-auth allowed-client-ids] :as config} auth-enabled]
  (let [allowed-client-id-set (set (str/split allowed-client-ids #","))
        auth-opts             {:auth-enabled (boolean auth-enabled)}]
    (-> (compojure.core/routes
          (POST "/endpoints/:endpoint-id/config" [endpoint-id]
            {:action :checker, :endpoint-id endpoint-id})
          (POST "/endpoints/:endpoint-id/paths" [endpoint-id profile]
            {:action :validator, :endpoint-id endpoint-id :profile profile}))
        (auth/wrap-authentication introspection-endpoint-url introspection-basic-auth auth-opts)
        (auth/wrap-allowed-clients-checker allowed-client-id-set auth-opts)
        (wrap-response-handler :checker #(checker/check-endpoint (:endpoint-id %) config))
        (wrap-response-handler :validator #(jobs-client/enqueue-validation (:endpoint-id %) (:profile %) config))
        (wrap-json-response)
        (wrap-defaults api-defaults))))

;; Compose the app from the routes and the wrappers. Authentication can be disabled for testing purposes.
(defn compose-app [config auth-enabled]
  (compojure.core/routes
    (public-routes config)
    (private-routes config auth-enabled)
    (route/not-found "Not Found")))
