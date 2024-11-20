(ns nl.surf.eduhub.validator.service.views.status
  (:require [hiccup2.core :as h2]))

(defn render-not-found []
  (->
    [:html
     {:lang "en"}
     [:head
      [:meta {:charset "UTF-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
      [:title "Status Report Not Found"]
      [:link {:href "/stylesheets/all.css" :rel "stylesheet"}]
     [:body
      [:div.profile-container
       [:h1 "Validation Report Not Found"]]]]]
    h2/html
    str))

(defn render [{:keys [endpoint-id job-status profile uuid]}]
  {:pre [job-status profile uuid endpoint-id]}
  (let [display (if (= "finished" job-status) "block" "none")]
    (->
      [:html
       {:lang "en"}
       [:head
        [:meta {:charset "UTF-8"}]
        [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
        [:title "Status Report"]
        [:link {:href "/stylesheets/all.css" :rel "stylesheet"}]
        [:script (h2/raw (str "var validationUuid = '" uuid "';"))]
        [:script {:src "/javascript/status.js"}]
        ;; Start polling for the status to change to "finished" or "failed"
        [:script (when (= job-status "pending") (h2/raw (str "const polling = setInterval(pollJobStatus, pollInterval);")))]]
       [:body
        [:div.profile-container
         [:h1 endpoint-id]
         [:p.status {:id "job-status" :class job-status} (str "Status: " job-status)]
         [:p (str "Profile Name: " profile)]
         [:div {:style (str "margin-left: 100px; display: " display)}
          [:a.button.report-button {:href (str "/view/report/" uuid)} "View Report"]
          [:a.button.report-button {:href (str "/download/report/" uuid)} "Download Report"]
          [:form {:action (str "/delete/report/" uuid) :method "POST"}
           [:button.delete-button.button {:type "submit"} "Delete Report"]]]]]]
      h2/html
      str)))
