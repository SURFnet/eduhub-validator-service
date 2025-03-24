;; SPDX-FileCopyrightText: 2024, 2025 SURF B.V.
;; SPDX-License-Identifier: AGPL-3.0-or-later
;; SPDX-FileContributor: Joost Diepenmaat
;; SPDX-FileContributor: Michiel de Mare
;; SPDX-FileContributor: Remco van 't Veer

(ns nl.surf.eduhub.validator.service.validate-test
  (:require [babashka.http-client :as http]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [environ.core :refer [env]]
            [nl.surf.eduhub.validator.service.config :as config]
            [nl.surf.eduhub.validator.service.config-test :as config-test]
            [nl.surf.eduhub.validator.service.test-helper :as test-helper]
            [nl.surf.eduhub.validator.service.validate :as validate]))

(def test-config
  (first (config/load-config-from-env (merge config-test/default-env env))))

(deftest no-errors-test-config
  (let [[_opts errors] (config/load-config-from-env config-test/default-env)]
    (is (nil? errors))))

(deftest test-validate-correct
  (let [dirname "test/fixtures/validate_correct"
        http-handler http/request
        {:keys [gateway-basic-auth gateway-url max-total-requests]} test-config
        vcr (if (.exists (io/file dirname))
              (test-helper/make-playbacker dirname)
              (test-helper/make-recorder dirname http-handler))]
    (with-redefs [http/request (fn wrap-vcr [req] (vcr req))]
      (let [opts {:basic-auth         gateway-basic-auth
                  :base-url           gateway-url
                  :max-total-requests max-total-requests
                  :ooapi-version      5
                  :profile       "rio"}
            report (validate/validate-endpoint "demo04.test.surfeduhub.nl" opts)]
        (is (str/includes? report
                           "<dt>Number of requests</dt><dd>5</dd>"))
        (is (str/includes? report
                           "No issues found."))))))
