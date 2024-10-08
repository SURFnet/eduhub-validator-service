(ns nl.surf.eduhub.validator.service.views.root
  (:require [hiccup2.core :as h2]))

(defn render [not-found {:keys [root-url] :as _config}]
  (->
    [:html
     {:lang "en"}
     [:head
      [:meta {:charset "UTF-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
      [:title "Status Report"]
      [:link {:href "/stylesheets/all.css" :rel "stylesheet"}]
      [:script (h2/raw (str "var rootUrl = '" root-url "';"))]
      [:script {:src "/javascript/root.js"}]]
     [:body
      [:div.profile-container
       [:p.status (if not-found "Could not find status with that uuid" "")]
       [:h1 "Validator Service"]
       [:label {:for "uuid-input"} "UUID:"]
       [:input#uuid-input {:type "text" :placeholder "Enter UUID"}]
       [:button#check-status-btn {:disabled "true"} "Check Status"]]]]
    h2/html
    str))
