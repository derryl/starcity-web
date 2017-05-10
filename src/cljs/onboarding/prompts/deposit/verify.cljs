(ns onboarding.prompts.deposit.verify
  (:require [ant-ui.core :as a]
            [onboarding.prompts.content :as content]
            [re-frame.core :refer [dispatch subscribe]]))

(defn- on-change [keypath k]
  #(dispatch [:prompt/update keypath k %]))

(defmethod content/content :deposit.method/verify
  [{:keys [keypath data] :as item}]
  (let [{:keys [amount-1 amount-2]} data]
    [:div.content
     [:p "Once the two deposits have been reflected in your bank's transaction history, enter the amounts (in cents) below."]
     [:p "Remember, "
      [:strong "it may take up to two business days for this to take place. "]
      "If you do not yet see the transactions , it's probably because they have not yet been made!"]

     [a/card
      [:div.control.is-grouped
       {:style {:justify-content "center"}}
       [:div.control
        [:label.label "First Deposit"]
        [a/input-number {:min       1
                         :step      1
                         :max       100
                         :size      :large
                         :formatter #(str "¢ " %)
                         :value     (or amount-1 0)
                         :on-change (on-change keypath :amount-1)}]]
       [:div.control
        [:label.label "Second Deposit"]
        [a/input-number {:min       1
                         :step      1
                         :max       100
                         :size      :large
                         :formatter #(str "¢ " %)
                         :value     (or amount-2 0)
                         :on-change (on-change keypath :amount-2)}]]]]]))
