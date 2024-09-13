;; This file is part of eduhub-validator-service
;;
;; Copyright (C) 2022 SURFnet B.V.
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

(ns nl.surf.eduhub.validator.service.authentication
  "Authenticate incoming HTTP API requests using SURFconext.

  This uses the OAuth2 Client Credentials flow for authentication. From
  the perspective of the RIO Mapper HTTP API (a Resource Server in
  OAuth2 / OpenID Connect terminology), this means that:

  1. Calls to the API should contain an Authorization header with a
     Bearer token.

  2. The token is verified using the Token Introspection endpoint,
     provided by SURFconext.

  The Token Introspection endpoint is described in RFC 7662.

  The SURFconext service has extensive documentation. For our use
  case you can start here:
  https://wiki.surfnet.nl/display/surfconextdev/Documentation+for+Service+Providers

  The flow we use is documented at https://wiki.surfnet.nl/pages/viewpage.action?pageId=23794471 "
  (:require [babashka.http-client :as http]
            [cheshire.core :as json]
            [clojure.core.memoize :as memo]
            [clojure.tools.logging :as log]
            [nl.jomco.http-status-codes :as http-status]
            [ring.util.response :as response]))

(defn bearer-token
  [{{:strs [authorization]} :headers}]
  (some->> authorization
           (re-matches #"Bearer ([^\s]+)")
           second))

;; Take a authentication uri, basic auth credentials and a token extracted from the bearer token
;; and make a call to the authentication endpoint.
;; Returns the client id if authentication is successful, otherwise nil.
(defn authenticate-token [uri token auth]
  {:pre [(string? uri)
         (string? token)
         (map? auth)]}
  (try
    (let [opts {:basic-auth auth
                :form-params {:token token}
                :throw false
                :headers {"Accept" "application/json"}}
          {:keys [status] :as response} (http/post uri opts)]
      (when (= http-status/ok status)
        ;; See RFC 7662, section 2.2
        (let [json   (json/parse-string (:body response) true)
              active (:active json)]
          (when-not (boolean? active)
            (throw (ex-info "Invalid response for token introspection, active is not boolean."
                            {:body json})))
          (when active
            (:client_id json)))))
    (catch Exception ex
      (log/error ex "Error in token-authenticator")
      nil)))

(defn make-token-authenticator
  "Make a token authenticator that uses the OIDC `introspection-endpoint`.

  Returns a authenticator that tests the token using the given
  `instrospection-endpoint` and returns the token's client id if the
  token is valid.
  Returns nil unless the authentication service returns a response with a 200 code."
  [introspection-endpoint auth]
  {:pre [introspection-endpoint auth]}
  (fn [token]
    (authenticate-token introspection-endpoint
                        token
                        auth)))

(defn handle-request-with-token [request request-handler client-id]
  (if (nil? client-id)
    (response/status http-status/forbidden)
    ;; set client-id on request and response (for tracing)
    (-> request
        (assoc :client-id client-id)
        request-handler
        (assoc :client-id client-id))))

(defn wrap-authentication
  "Authenticate calls to ring handler `f` using `token-authenticator`.

  The token authenticator will be called with the Bearer token from
  the incoming http request. If the authenticator returns a client-id,
  the client-id gets added to the request as `:client-id` and the
  request is handled by `f`. If the authenticator returns `nil` or
  if the http status of the authenticator call is not successful, the
  request is forbidden.

  If no bearer token is provided, the request is executed without a client-id."
  ; auth looks like {:user client-id :pass client-secret}
  [f introspection-endpoint auth]
  (let [authenticator (memo/ttl (make-token-authenticator introspection-endpoint auth) :ttl/threshold 60000)] ; 1 minute
    (fn [request]
      (if-let [token (bearer-token request)]
        (handle-request-with-token request f (authenticator token))
        (f request)))))
