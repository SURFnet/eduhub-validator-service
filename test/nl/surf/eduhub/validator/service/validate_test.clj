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
                  :profile       "rio"}]
        (is (str/includes? (validate/validate-endpoint "demo04.test.surfeduhub.nl" opts)
                           "5 observations have no issues"))))))
