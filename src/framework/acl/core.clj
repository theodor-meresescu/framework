(ns framework.acl.core
  (:require
    [clojure.string :as str]
    [framework.acl.core-functions :refer [has-access]]
    [xiana.core :as xiana]))

(def action-mapping
  {:get    :read
   :post   :update
   :put    :create
   :delete :delete})

(defn- ->resource
  ([uri prefix]
   (->> (if prefix (str/replace uri (re-pattern prefix) "") uri)
        (re-find #"\w+")))
  ([uri]
   (re-find #"\w+" uri)))

(defn is-allowed
  "Checks if the user is able to do an action on a resource.
  Returns xiana/ok when it is, and extends [:response-data :acl] with the :over of ownership check.
  When the user has no access, returns xiana/error or executes ((:or-else access) state) if it's provided.
  If any key is missing from 'access' it's resolved like:
  - role from user
  - resource from URI (/users/ -> \"users\")
  - and privilege from request method:

  |req:    | action: |
  |------- |---------|
  |:get    | :read   |
  |:post   | :update |
  |:put    | :create |
  |:delete | :delete |"
  ([{{user :user}             :session-data
     roles                    :acl/roles
     {method :request-method} :request
     :as                      state}
    {:keys [role privilege resource prefix] :as access}]
   (let [pr (or privilege (action-mapping method))
         res (name (or resource (->resource (get-in state [:request :uri]) prefix)))
         role (keyword (or role (:role user) (:users/role user)))
         result (has-access roles user {:resource  res
                                        :role      role
                                        :privilege pr})]
     (cond result (xiana/ok (-> (assoc-in state [:response-data :acl] result)
                                (assoc-in [:response-data :acl-resource] (keyword res))))
           (:or-else access) ((:or-else access) state)
           :else (xiana/error (assoc state :response {:status 401 :body "Authorization error"})))))
  ([{{user :user} :session-data http-request :request :as state}]
   (let [resource (->resource (:uri http-request))
         privilege (action-mapping (:request-method http-request))]
     (is-allowed state {:resource resource :privilege privilege :role (:role user)}))))
