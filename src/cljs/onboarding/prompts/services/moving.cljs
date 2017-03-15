(ns onboarding.prompts.services.moving
  (:require [ant-ui.core :as a]
            [onboarding.prompts.content :as content]
            [re-frame.core :refer [dispatch subscribe]]
            [cljsjs.moment]
            [reagent.core :as r]))

(def time-picker (r/adapt-react-class (.-TimePicker js/window.antd)))

(defn- form
  [keypath commencement {:keys [needed date time]}]
  [a/card
   [:div.control
    [:label.label "Do you need help moving in?"]
    [a/radio-group
     {:on-change #(dispatch [:prompt/update keypath :needed (= (.. % -target -value) "yes")])
      :value     (cond (true? needed) "yes" (false? needed) "no" :otherwise nil)}
     [a/radio {:value "yes"} "Yes"]
     [a/radio {:value "no"} "No"]]]
   (when needed
     [:div
      [:div.control
       [:label.label "What date will you be moving in on?"]
       [a/date-picker
        {:value         date
         :on-change     #(dispatch [:prompt/update keypath :date %])
         :disabled-date #(.isBefore % commencement)
         :allow-clear   false
         :format        "MM-DD-YYYY"}]]
      [:div.control
       [:label.label "At what time will you be moving in?"]
       [time-picker
        {:value                 time
         :on-change             #(dispatch [:prompt/update keypath :time %])
         :format                "HH:mm"
         :disabled-hours        #(concat (range 0 9) (range 20 24))
         :disabled-minutes      (fn [] (remove #(= (mod % 30) 0) (range 0 61)))
         :disabled-seconds      #(range 0 61)
         :hide-disabled-options true}]]])])

(defmethod content/content :services/moving
  [{:keys [keypath commencement data] :as item}]
  [:div.content
   [:p "Starcity provides moving services to assist you with the lugging and lifting at Starcity on move-in day. " [:strong "Moving services are $50 per hour with a 2 hour minimum."]]
   [form keypath commencement data]])