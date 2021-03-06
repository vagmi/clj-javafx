(ns clj-javafx.wrapper
  (:import java.lang.Thread
           javafx.clojure.FXApplication 
           [javafx.application Application Platform]
           [javafx.scene Scene Group]))


(defonce ready? (promise))

(defonce _components (atom {:primary-stage nil
                           :primary-root nil
                           :primary-scene nil
                           :fxapp nil}))

(defonce _children (atom {}))

(defonce _parents (atom {}))


(defmacro with-javafx 
  "Runs a body inside the JavaFX thread."
  [& body]
  `(let [f# (fn [] ~@body)]
     (Platform/runLater f#)))

(defmacro with-javafx-let 
  "Runs a code inside the JavaFX thread. You can bind variables that are used inside the body."
  [bindings & body]
  `(let ~bindings
     (with-javafx ~@body)))



(defn wait-stopping
  "Checks if the application is stopped."
  []
  (FXApplication/isStopped))

(defn wait-launching
  "Tests if the JavaFX application is launched."
  []
  @ready?)

(defn start-fxapplication
  "Starts the JavaFX thread. This function should not be called directly.\n
   Don't forget you cann launch the JavaFX Application more one time."
  []

  (reset! _components {:primary-stage nil
                       :primary-root nil
                       :primary-scene nil
                       :fxapp nil})

  (reset! _children {})


  (swap! _components assoc :fxapp (future (Application/launch javafx.clojure.FXApplication (into-array String []))))
  (swap! _components assoc :primary-stage (javafx.clojure.FXApplication/getCurrentStage)))

(defn launch-app
  "Launches a basic JavaFX application. This function lays on the FXApplication to work.\n
   You can't call this function more one time. The second time, nothing will happen."
  ([]

     (start-fxapplication)


     (with-javafx
       (swap! _components assoc :primary-root (Group.))
       (swap! _components assoc :primary-scene (Scene. (:primary-root @_components)))
       (.setScene (:primary-stage @_components) (:primary-scene @_components))
       (deliver ready? true))

     (wait-launching))

  ([width height]
     (start-fxapplication)

     (let [w (if (nil? width) 0 width)
           h (if (nil? height) 0 height)]

       (with-javafx
         (swap! _components assoc :primary-root (Group.))
         (swap! _components assoc :primary-scene (Scene. (:primary-root @_components) (double w) (double h)))
         (.setScene (:primary-stage @_components) (:primary-scene @_components))
         (deliver ready? true)))

     (wait-launching))

  ([width height depth-buffer paint]
     (start-fxapplication)

     (let [db (not (nil? depth-buffer))
           w (if (nil? width) 0 width)
           h (if (nil? height) 0 height)]

       (with-javafx
         (swap! _components assoc :primary-root (Group.))
         (swap! _components assoc :primary-scene (Scene. (:primary-root @_components) (double w) (double h) (boolean db)))
         
         (if-not (nil? paint)
           (.setFill (:primary-scene @_components) paint))
         
         (.setScene (:primary-stage @_components) (:primary-scene @_components))
         (deliver ready? true))
       
       (wait-launching)))
  )

(defn launch
  "A facility function to launch an JavaFX application. In a call, you can give the original size of your main window (with arguments :width and :height), the depth-buffer value or a default fill color (with :paint)."
  [&  {:keys [width height depth-buffer paint]}]
  (cond 
   (not (nil? depth-buffer)) (launch-app width height depth-buffer paint)
   (not (nil? paint)) (launch-app width height depth-buffer paint)
   (and (nil? width) (nil? height) (nil? depth-buffer) (nil? paint)) (launch-app)
   :else (launch-app width height)))

(defn show
  "Displays on the screen the main window."
  []
  (with-javafx (.show (:primary-stage @_components))))


(defn component
  "Returns a component (a node) from all the known nodes in the current GUI."
  [c]
  (c @_components))

(defn update-children
  "Updates the children table. This table is a map where the key is the parent and the value is a children list."
  [parent-sym child-sym]
  (swap! _children assoc parent-sym (conj (parent-sym @_children) child-sym))
  (swap! _parents assoc child-sym parent-sym))

(defn add-child
  "Adds a component to a container (like group). The parent container is defined by its symbol.
The first known parent when launching is the primary-group (so defined by the symbol :primary-group).
The child component should be define by a symbol too. But it's not mandatory."
  ([parent-sym child-sym child]
     (when-let [parent (component parent-sym)]
       (-> (.getChildren parent) (.add child))
       (swap! _components assoc child-sym child)
       (update-children parent-sym child-sym)))

  ([parent-sym child]
     (when-let [parent (component parent-sym)]
       (-> (.getChildren parent) (.add child)))))


(defn children 
  "Returns a sequence of the children for the given node. The node is defined by its symbol."
  [parent-sym]
  (parent-sym @_children))

(defn children?
  "Tells if a node has at least one child"
  [node]
  (not (empty? (children node))))

(defn find-descendants
  "Finds the descendants of a node. It's an utility function for the function descendants."
  [node descendants]
  (if-not (children? node)
    descendants
    (let [children (children node)]
      (reduce #(find-descendants %2 %1) 
              (into descendants children)  children))))

(defn descendants
  "Returns a sequence with all the descendants of a node."
  [node]
  (find-descendants node '()))

(defn remove-from-children
  [parent-sym child-sym]
  (swap! _children assoc parent-sym (remove #(not= % child-sym) (get @_children parent-sym))))

(defn remove-node
  "Remove a child component from parent. The removed node is defined by its symbole."
  [node-sym]
  (let [child (component node-sym)
        parent (.getParent child)]
    
    ;; Removes the child from the JavaFX components tree
    (if-not (nil? parent)
      (-> parent (.getChildren) (.remove child)))

    ;; Manages the change in the wrapper
    (swap! _components dissoc node-sym)
    (doseq [d (descendants node-sym)]
      (remove-node d))

    (when-let [parent-sym (get @_parents node-sym)]
      (remove-from-children parent-sym node-sym))))

