(ns onboarding.prompts.review
  (:require [ant-ui.core :as a]
            [clojure.string :as string]
            [onboarding.prompts.content :as content]
            [re-frame.core :refer [dispatch subscribe]]
            [reagent.core :as r]
            [toolbelt.core :as tb]))

(defn- column
  [key & {:keys [render title class style]}]
  (let [render (if render
                 (fn [a b]
                   (r/as-element
                    (render (js->clj a :keywordize-keys true)
                            (js->clj b :keywordize-keys true))))
                 identity)]
    {:title     (or title (string/capitalize key))
     :key       key
     :dataIndex key
     :render    render
     :className class}))

(def ^:private columns
  [(column "name"
           :title "Order"
           :class "width-75"
           :render (fn [name {:keys [desc rental]}]
                     [:div
                      [:span [:b {:dangerouslySetInnerHTML {:__html name}}]
                       (when rental [:i " (rental)"])]
                      [:p {:style                   {:word-break "break-word"}
                           :dangerouslySetInnerHTML {:__html desc}}]]))
   (column "quantity"
           :class "column-right no-break"
           :render (fn [q _]
                     [:span {:dangerouslySetInnerHTML {:__html (or q "&mdash;")}}]))
   (column "price"
           :class "column-right no-break"
           :render (fn [price {:keys [billed quantity]}]
                     (let [bill (if (= billed "monthly") "/mo" "")]
                       (if (nil? price) "Quote" (str "$" price bill)))))
   (column "action"
           :class "no-break column-center"
           :render (fn [_ {id :id}]
                     [:a {:on-click #(do (.preventDefault %)
                                         (dispatch [:order/delete id]))}
                      "Remove"]))])

;; =============================================================================
;; Components
;; =============================================================================

(defn- review [loading orders]
  [:div
   [:p {:dangerouslySetInnerHTML {:__html "Below are the services that you selected&mdash;please ensure that these are the services that you selected and that all information is correct."}}]
   [:p "When you press " [:b "Finish"] " below we will ask for your credit/debit card information; we " [:b "will not charge you until your move-in date or your service(s) are delivered"] ", whichever happens last."]
   [a/card {:class "table-card"}
    [a/table {:dataSource (clj->js orders)
              :loading    loading
              :size       :small
              :columns    columns
              :pagination false}]]])

(defn- fetch-error [keypath loading]
  [:div
   [:p "Something went wrong while trying to fetch your orders. Please check your internet connection and use the button below to try again."]
   [:p "If the problem persists, " [:b "please click my face above "] "to get help."]
   [:div.has-text-centered
    [a/button {:type      :ghost
               :size      :large
               :loading   loading
               :icon      :reload
               :html-type :button
               :on-click  #(dispatch [:orders/fetch keypath])}
     "Retry"]]])

(defn- handle-card-errors [container event]
  (if-let [error (.-error event)]
    (aset container "textContent" (.-message error))
    (aset container "textContent" "")))

(def card-style
  {:base    {:fontFamily "'Work Sans', Helvetica, sans-serif"}
   :invalid {:color "#ff3860" :iconColor "#ff3860"}})

(defn- cc-modal []
  (let [finishing (subscribe [:finish.review/finishing?])]
    (r/create-class
     {:component-did-mount
      (fn [this]
        (let [st         (js/Stripe (.-key js/stripe))
              elements   (.elements st)
              card       (.create elements "card" #js {:style (clj->js card-style)})
              errors     (.querySelector (r/dom-node this) "#card-errors")
              submit-btn (.querySelector (r/dom-node this) "#submit-btn")]
          (.mount card "#card-element")
          (.addEventListener card "change" (partial handle-card-errors errors))
          (->> (fn [_]
                 (let [p (.createToken st card)]
                   (.then p (fn [result]
                              (if-let [error (.-error result)]
                                (aset errors "textContent" (.-message error))
                                (dispatch [:finish.review.cc/submit! result]))))))
               (.addEventListener submit-btn "click"))))
      :reagent-render
      (fn []
        [:div.modal.is-active
         [:div.modal-background
          {:on-click #(dispatch [:finish.review.cc/toggle false])}]
         [:div.modal-content.box
          [:h4.title "Please enter your payment information"]
          [:p "We won't charge your account until your move-in date or your service(s) are delivered, whichever happens last."]
          [:div {:style {:background-color "#f7f8f9"
                         :padding          24
                         :border-radius    4
                         :border           "1px solid #eeeeee"}}
           [:label.label.is-small {:for "card-element"} "Credit or debit card"]
           [:div#card-element]
           [:p#card-errors.help.is-danger]]
          [:div {:style {:padding-top "1.25rem" :float "right"}}
           [a/button {:size      :large
                      :type      :primary
                      :html-type :button
                      :id        "submit-btn"
                      :loading   @finishing}
            "Submit"]]]
         [:button.modal-close {:type :button :on-click #(dispatch [:finish.review.cc/toggle false])}]])})))

;; =============================================================================
;; Entrypoint
;; =============================================================================

;; TODO: Button action when no orders are selected
(defmethod content/next-button :finish/review [prompt]
  (let [dirty     (subscribe [:prompt/dirty?])
        is-saving (subscribe [:prompt/saving?])]
    [a/button {:type      :primary
               :size      :large
               :loading   @is-saving
               :on-click  #(dispatch [:finish.review.cc/toggle true])
               :html-type :button}
     "Finish"]))

(defn- content* [{keypath :keypath}]
  (let [orders     (subscribe [:orders])
        error      (subscribe [:orders/error?])
        loading    (subscribe [:orders/loading?])
        show-modal (subscribe [:finish.review.cc/showing?])]
    (r/create-class
     {:component-will-mount
      (fn [_]
        (dispatch [:stripe/load-scripts "v3"]))
      :reagent-render
      (fn [{keypath :keypath}]
        [:div.content
         (when @show-modal [cc-modal])
         (cond
           @error                                (fetch-error keypath @loading)
           (and (empty? @orders) (not @loading)) [:p "TODO: copy when nothing is ordered"]
           :otherwise                            [review @loading @orders])])})))

(defmethod content/content :finish/review [prompt]
  [content* prompt])
