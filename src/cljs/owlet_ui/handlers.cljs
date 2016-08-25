(ns owlet-ui.handlers
  (:require [re-frame.core :as re]
            [owlet-ui.db :as db]
            [owlet-ui.config :as config]
            [ajax.core :refer [GET POST PUT]]))


(re/register-handler
  :initialize-db
  (fn [_ _]
    db/default-db))


(re/register-handler
  :set-active-view
  (fn [db [_ active-view]]
    (assoc db :active-view active-view)))


(re/register-handler
  :user-has-logged-in-out!
  (re/path [:user])
  (fn [db [_ val]]
    ;; reset user-bg-image on logout
    (when (false? val)
      (do
        (re/dispatch [:reset-user-bg-image! config/default-header-bg-image])
        (re/dispatch [:reset-user-db!])))
    (assoc db :logged-in? val)))


(re/register-handler
  :reset-user-db!
  (fn [_ [_ _]]
    db/default-db))


(re/register-handler
  :update-social-id!
  (re/path [:user])
  (fn [db [_ sid]]
    (GET (str config/server-url "/api/content/entries?social-id=" sid)
         {:response-format :json
          :keywords?       true
          :handler         #(re/dispatch [:process-fetch-entries-success! %1])
          :error-handler   #(prn %)})
    (assoc db :social-id sid)))


(re/register-handler
  :process-fetch-entries-success!
  (re/path [:user :content-entries])
  (fn [db [_ entries]]
    (re/dispatch [:set-user-background-image! entries])
    (conj db entries)))


(re/register-handler
  :set-user-background-image!
  (re/path [:user :background-image])
  (fn [db [_ coll]]
    (let [filter-user-bg-image (fn [c]
                                 (filterv #(= (get-in % [:sys :contentType :sys :id])
                                              "userBgImage") c))
          user-bg-image-entries (last (filter-user-bg-image coll))
          entry-id (get-in user-bg-image-entries [:sys :id])]
      ;; set :background-image-entry-id
      (re/dispatch [:set-backround-image-entry-id! entry-id])
      (get-in user-bg-image-entries [:fields :url :en-US]))))


(re/register-handler
  :set-backround-image-entry-id!
  (re/path [:user :background-image-entry-id])
  (fn [_ [_ id]]
    id))


(re/register-handler
  :update-user-background!
  (fn [db [_ url]]
    ;; if we have a url and an entry-id, aka existing entry for *userBgImage*
    ;; perform an update
    (let [entry-id (get-in db [:user :background-image-entry-id])]
      (if (and url entry-id)
        (PUT
          (str config/server-url "/api/content/entries")
          {:response-format :json
           :keywords?       true
           :params          {:content-type "userBgImage"
                             :fields       {:url      {"en-US" url}
                                            :socialid {"en-US" (get-in db [:user :social-id])}}
                             :entry-id     entry-id}
           :handler         #(re/dispatch [:update-user-background-after-successful-post! %1])
           :error-handler   #(prn %)})
        (POST
          (str config/server-url "/api/content/entries")
          {:response-format :json
           :keywords?       true
           :params          {:content-type  "userBgImage"
                             :fields        {:url      {"en-US" url}
                                             :socialid {"en-US" (get-in db [:user :social-id])}}
                             :auto-publish? true}
           :handler         #(re/dispatch [:update-user-background-after-successful-post! %1])
           :error-handler   #(prn %)})))
    db))


(re/register-handler
  :update-user-background-after-successful-post!
  (re/path [:user :background-image])
  (fn [_ [_ res]]
    (re/dispatch [:set-backround-image-entry-id! (get-in res [:sys :id])])
    (get-in res [:fields :url :en-US])))


(re/register-handler
  :reset-user-bg-image!
  (re/path [:user :background-image])
  (fn [_ [_ url]]
    url))


(re/register-handler
  :get-auth0-profile
  (fn [db [_ _]]
    (let [user-token (.getItem js/localStorage "userToken")]
      (.getProfile config/lock user-token
                   (fn [err profile]
                     (if (not (nil? err))
                       (prn err)
                       (do
                         (re/dispatch [:user-has-logged-in-out! true])
                         (re/dispatch [:update-social-id! (.-user_id profile)])))))
      db)))


(re/register-handler
  :activities-get-successful
  (fn [db [_ res]]
    ; Obtains the URL for each preview image, and adds a :url field next to
    ; its :id field in [:activities  :fields :preview :sys] map.

    (let [url-for-id  ; Maps preview image IDs to associated URLs.
                      (->> (get-in res [:includes :Asset])
                           (map (juxt (comp :id :sys)
                                      (comp :url :file :fields)))
                           (into {}))]

      (assoc db       ; Return new db, adding :url field to its [... :sys] map.
             :activities
             (for [item (:items res)]
               (update-in item
                          [:fields :preview :sys]
                          (fn [{id :id :as sys}]
                            (assoc sys :url (url-for-id id)))))))))
