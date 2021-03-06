(ns owlet.components.activity.inspiration
  (:require [owlet.helpers :refer [showdown]]))

(defn activity-inspiration [inspiration]
  [:div.activity-inspiration-wrap.box-shadow
   [:span.h3 "Inspiration"]
   [:div {"dangerouslySetInnerHTML"
          #js{:__html (.makeHtml showdown inspiration)}}]])
