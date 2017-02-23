(ns apply.overview.views
  (:require [apply.prompts.views :as p]
            [apply.routes :refer [prompt-uri]]))

(def ^:private welcome-content
  [:div.content
     [:p "My name is Meg Bell, and I'm your community advisor.
     Congratulations! You've taken your first step towards joining a Starcity
     community. We're looking forward to getting to know you."]

     [:p "As your community advisor, I'm here to assist you in submitting your
     application, getting oriented with our communities, and helping you feel at
     home in our communal living spaces. " [:strong "If you have a question at
     any time, click on my picture to send me a message."]]

     [:p "Here's a quick overview of our applciation process to get you
     comfortable with what's ahead."]

     [:ul
      ;; Logistics
      [:li "Let us know which communities you'd like to join, youre preferred
      move-in date, and how long you'll be staying with us "
       [:span {:dangerouslySetInnerHTML {:__html "&mdash; "}}]
       "we'll handle the " [:strong "logistics"] "."]
      ;; Personal information
      [:li "Provide us with a few pieces of "
       [:strong "personal information"]
       " so that we can perform a background check on you "
       [:span {:dangerouslySetInnerHTML {:__html "&mdash; "}}]
       " this is an important part of what keeps Starcity communities safe."]
      ;; Community fitness
      [:li "Each of our communities is uniquely cultivated by our members. "
       [:strong "Community fitness "]
       "is determined by community members using the responses you provide."]
      ;; Final Steps
      [:li [:strong "Finish"] " your application by paying our application fee of " [:em "$25."]]]])

(defn welcome []
  (p/prompt
   (p/header "Hi. Welcome to Starcity!")
   (p/content welcome-content)))
