;; SPDX-FileCopyrightText: 2024, 2025 SURF B.V.
;; SPDX-License-Identifier: AGPL-3.0-or-later
;; SPDX-FileContributor: Michiel de Mare
;; SPDX-FileContributor: Remco van 't Veer

(ns nl.surf.eduhub.validator.service.test-helper
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.test :refer [is]]
            [clojure.pprint :refer [pprint]])
  (:import [java.io PushbackReader]))

(defn validate-timestamp [m k]
  (let [ts (k m)]
    (is (string? ts) (str k " value must be set in " (prn-str m)))
    (when (string? ts)
      (is (re-matches #"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{3})?Z" ts)))) ; fractional seconds are omitted when zero
  (dissoc m k))

(defn make-playbacker [dir]
  (let [count-atom (atom 0)]
    (fn playbacker [_req]
      (let [i                (swap! count-atom inc)
            fname            (str dir "/" i ".edn")]
        (if (.exists (io/file fname))
          (:response (with-open [r (io/reader fname)]
                       (edn/read (PushbackReader. r))))
          (throw (ex-info (str "VCR playbacker cannot find file:" fname " - It's recommended to remove '" dir "' entirely and run the tests again") {})))))))

(defn make-recorder [dir http-handler]
  (let [mycounter (atom 0)]
    (fn recorder [request]
      (let [response  (-> request
                          http-handler
                          (select-keys [:status :body :headers]))
            counter   (swap! mycounter inc)
            file-name (str dir "/" counter ".edn")]
        (io/make-parents file-name)
        (with-open [writer (io/writer file-name)]
          (let [safe-headers (dissoc (:headers request) "Authorization" "Cookie")
                safe-request (-> request
                                 (select-keys [:method :url :body])
                                 (assoc :headers safe-headers))]
            (pprint {:request  safe-request :response response} writer)))
        response))))
