;; This file is part of eduhub-validator-service
;;
;; Copyright (C) 2025 SURFnet B.V.
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

(ns nl.surf.eduhub.validator.service.logging
  "Safe logging utility function that redacts sensitive information."
  (:require [clojure.string :as str]))

(def ^:private redact-key-re
  "Regex matching keys for entries that should be redacted from logs."
  #"(?i:pass|secret|client.id|proxy.options|auth)") ;; proxy options can contain authentication info

(defn- sensitive-key?
  "Returns true if the given key should be redacted based on case-insensitive matching."
  [k]
  (when k
    (let [k-str (-> k name str/lower-case)]
      (re-find redact-key-re k-str))))

(def redact-placeholder
  "String that will replace a redacted entry.

  Also see [[redact-key-re]]."
  "XXX-REDACTED")

(defn redact-sensitive
  "Recursively redacts sensitive information from data structures.
  Replaces values for keys that match sensitive patterns with \"REDACTED\".
  Works with nested maps, vectors, lists, and other collections."
  [data]
  (cond
    (map? data)
    (into {}
          (map (fn [[k v]]
                 [k (if (and (sensitive-key? k) (string? v))
                      redact-placeholder
                      (redact-sensitive v))]))
          data)

    (vector? data)
    (mapv redact-sensitive data)

    (seq? data)
    (map redact-sensitive data)

    (set? data)
    (set (map redact-sensitive data))

    :else
    data))
