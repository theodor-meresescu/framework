(ns interceptors.load-user
  (:require
    [framework.db.sql :as db]
    [framework.session.core :as session]
    [honeysql.helpers :refer [select from where]]
    [xiana.core :as xiana])
  (:import
    (java.util
      UUID)))

(defn ->role
  [user]
  (let [role (cond
               (not (:users/is_active user)) :guest
               (:users/is_superuser user) :superuser
               (:users/is_staff user) :staff
               (:users/is_active user) :member
               :else :guest)]
    (assoc user :users/role role)))

(defn valid-user?
  [{:keys [request] :as state}]
  (let [user-id (UUID/fromString (get-in request [:headers :authorization]))
        datasource (get-in state [:deps :db :datasource])
        query (-> {}
                  (select :*)
                  (from :users)
                  (where [:= :id user-id]))]
    (-> (db/execute datasource query)
        first
        ->role)))

(def load-user!
  {:enter (fn [{:keys [request] :as state}]
            (let [session-backend (-> state :deps :session-backend)
                  guest-user {:users/role :guest
                              :users/id   (UUID/randomUUID)}
                  session-id (get-in request [:headers :session-id] (UUID/randomUUID))
                  user (try (let [valid-user (valid-user? state)]
                              (if (empty? valid-user)
                                guest-user
                                valid-user))
                            (catch Exception _ guest-user))]
              (session/add! session-backend session-id user)
              (xiana/ok (-> (assoc-in state [:session-data :user] user)
                            (assoc-in [:request :headers "session-id"] session-id)))))})
