;; SPDX-FileCopyrightText: 2024, 2025 SURF B.V.
;; SPDX-License-Identifier: AGPL-3.0-or-later
;; SPDX-FileContributor: Michiel de Mare
;; SPDX-FileContributor: Remco van 't Veer

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
            [nl.jomco.http-status-codes :as http-status]))

(defn bearer-token
  [{{:strs [authorization]} :headers}]
  (some->> authorization
           (re-matches #"Bearer ([^\s]+)")
           second))

;; Take a authentication uri, basic auth credentials and a token extracted from the bearer token
;; and make a call to the authentication endpoint.
;; Returns the client id if authentication is successful, otherwise nil.
(defn- authenticate-token [uri token auth]
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

(defn- make-token-authenticator
  "Make a token authenticator that uses the OIDC `introspection-endpoint`.

  Returns a authenticator that tests the token using the given
  `instrospection-endpoint` and returns the token's client id if the
  token is valid.
  Returns nil unless the authentication service returns a response with a 200 code."
  [introspection-endpoint auth]
  {:pre [introspection-endpoint auth]}
  (fn token-authenticator [token]
    (authenticate-token introspection-endpoint
                        token
                        auth)))

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
  [app introspection-endpoint auth allowed-client-id-set {:keys [auth-disabled]}]
  (let [authenticator (memo/ttl (make-token-authenticator introspection-endpoint auth) :ttl/threshold 60000)] ; 1 minute
    (fn authentication [request]
      (let [client-id (some-> request bearer-token authenticator)]
        (if (or auth-disabled
                (allowed-client-id-set client-id))
          (app request)
          {:body   (if client-id "Unknown client id" "No client-id found")
           :status http-status/forbidden})))))
