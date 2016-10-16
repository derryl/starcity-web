(ns starcity.views.communities
  (:require [starcity.views.page :as p]
            [starcity.views.communities.mission]
            [potemkin :refer [import-vars]]
            [clojure.spec :as s]
            [starcity.views.components
             [layout :as l]
             [image :as i]]))

;; =============================================================================
;; Internal
;; =============================================================================

(def ^:private content
  (l/section
   {:class "is-fullheight"}
   (l/container
    {:style "min-width: 70%;"}
    [:h1.title.is-1 "Explore our <b>communities</b>"]
    [:p.subtitle.is-4 "Our communities are all located in different
    neighborhoods of <strong>San Francisco</strong>, and are each uniquely
    designed with their <strong>neighborhood's character</strong> in mind."]
    (l/columns
     (l/column
      {:class "is-half"}
      [:div.card.is-fullwidth
       [:div.card-image
        [:a {:href "/communities/soma"}
         (i/image {:class "is-4by3"} "/assets/img/soma/card-banner.jpg")]]
       [:div.card-content
        [:p.title.is-4 "West SoMa"]
        [:p.subtitle.is-6 "Available <b>November 15, 2016</b>"]]
       [:div.card-footer
        [:a.card-footer-item
         {:href "/communities/soma"}
         "Learn More"]]])
     (l/column
      {:class "is-half"}
      [:div.card.is-fullwidth
       [:div.card-image
        [:a {:href "/communities/mission"}
         (i/image {:class "is-4by3"} "/assets/img/mission/card-banner.jpg")]]
       [:div.card-content
        [:p.title.is-4 "The Mission"]
        [:p.subtitle.is-6 "Available <b>January 1, 2017</b>"]]
       [:div.card-footer
        [:a.card-footer-item
         {:href "/communities/mission"}
         "Learn More"]]]))

    [:div.has-text-centered
     [:a.button.is-primary.is-large {:href "/signup"} "Apply Now"]])))

;; =============================================================================
;; API
;; =============================================================================

(def communities
  (p/page
   (p/title "Communities")
   (p/content
    p/navbar
    content)))

(import-vars
 [starcity.views.communities.mission mission])
