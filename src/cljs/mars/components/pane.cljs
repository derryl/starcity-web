(ns mars.components.pane)

(defn pane [header content]
  [:div header content])

(defn header [title & [subtitle]]
  [:header.pane-header
   [:div.pane-header__title
    [:h1.title.is-3 [:strong {:dangerouslySetInnerHTML {:__html title}}]]
    (when subtitle
      [:h3.subtitle.is-5 {:dangerouslySetInnerHTML {:__html subtitle}}])]])

(defn content [content]
  [:section.pane-content
   content])
