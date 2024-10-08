(ns nl.surf.eduhub.validator.service.views.status
  (:require [hiccup2.core :as h2]))

(defn render [{:keys [endpoint-id job-status profile uuid]} {:keys [root-url] :as _config}]
  {:pre [job-status profile uuid endpoint-id]}
  (let [display (if (= "finished" job-status) "inline" "none")]
    (->
      [:html
       {:lang "en"}
       [:head
        [:meta {:charset "UTF-8"}]
        [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
        [:title "Status Report"]
        [:link {:href "/stylesheets/all.css" :rel "stylesheet"}]
        [:script (h2/raw (str "var rootUrl = '" root-url "'; var validationUuid = '" uuid "';"))]
        [:script {:src "/javascript/status.js"}]]
       [:body
        [:div.profile-container
         [:h1 endpoint-id]
         [:p.status {:id "job-status" :class job-status} (str "Status: " job-status)]
         [:p (str "Profile Name: " profile)]
         [:form {:action (str root-url "/delete/report/" uuid) :method "POST"}
          [:button.delete-button {:type "submit"} "Delete Status"]]
         [:br]
         [:a.external-link {:style (str "display: " display) :href (str root-url "/view/report/" uuid) :id "show-report" :target "_blank"} "View Report"]]]]
      h2/html
      str)))
