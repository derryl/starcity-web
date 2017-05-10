(ns onboarding.prompts.deposit.bank
  (:require [ant-ui.core :as a]
            [onboarding.prompts.content :as content]
            [re-frame.core :refer [dispatch subscribe]]))

(defn on-change [keypath k]
  #(dispatch [:prompt/update keypath k (.. % -target -value)]))

(defmethod content/content :deposit.method/bank
  [{:keys [keypath data] :as item}]
  (let [{:keys [name account-number routing-number]} data]
    [:div.content
     [:p "After entering your account information, we'll make "
      [:b "two small deposits"]
      " (under $1) to your account some time in the next couple of business days."]
     [:p "In the next step, you'll verify ownership by correctly submitting the amounts of the deposits that were made."]
     [:p [:em [:b "Note: "] "At this time we can only accept payment from US-based bank accounts."]]

     [a/card
      [:div.control.is-grouped
       [:div.control.is-expanded
        [:label.label "Account Holder's Name"]
        [a/input {:type      :text
                  :required  true
                  :value     name
                  :on-change (on-change keypath :name)}]]
       [:div.control.is-expanded
        [:label.label "Routing Number"]
        [a/input {:type      :text
                  :required  true
                  :value     account-number
                  :on-change (on-change keypath :account-number)}]]
       [:div.control.is-expanded
        [:label.label "Account Number"]
        [a/input {:type      :text
                  :required  true
                  :value     routing-number
                  :on-change (on-change keypath :routing-number)}]]]]]))
