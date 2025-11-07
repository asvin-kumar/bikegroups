(ns build-content (:require [clojure.java.io :as io]
                            [clojure.edn :as edn]
                            [clojure.string :as str]
                            [babashka.fs :as fs]))

(def output-dir "dist")

(defn make-attrs [attrs]
  (->> attrs
       (map (fn [[k v]] (str " " (name k) "=\"" v "\"")))
       (apply str)))

(str "test" (apply str ["a" "b" "c"]))

(defn tag [tag-name attrs & content]
  (str "<" tag-name (make-attrs attrs) ">" (apply str content) "</" tag-name ">"))

(defn make-tag [tag-name]
  (fn [attrs & content]
    (apply tag tag-name attrs content)))

(def t-html (make-tag "html"))
(def t-meta (make-tag "meta"))
(def t-title (make-tag "title"))
(def t-div (make-tag "div"))
(def t-head (make-tag "head"))
(def t-body (make-tag "body"))
(def t-a (make-tag "a"))
(def t-h1 (make-tag "h1"))
(def t-h2 (make-tag "h2"))
(def t-h3 (make-tag "h3"))
(def t-h4 (make-tag "h4"))
(def t-p (make-tag "p"))
(def t-header (make-tag "header"))
(def t-nav (make-tag "nav"))
(def t-main (make-tag "main"))
(def t-link (make-tag "link"))
(def t-li (make-tag "li"))
(def t-ol (make-tag "ol"))
(def t-script (make-tag "script"))
(defn t-br [] "<br />")

(defn styled-a [attrs & content]
  (apply t-a (assoc attrs :class "underline decoration-green-400 hover:text-green-400 underline-offset-4 leading-8") content))
(defn styled-p [attrs & content]
  (apply t-p (assoc attrs :class "mb-2 leading-6") content))
(defn styled-h4 [attrs & content]
  (apply t-h4 (assoc attrs :class "text-xl mb-4 mt-4 align-bottom") content))
(defn styled-h3 [attrs & content]
  (apply t-h3 (assoc attrs :class "text-2xl mb-4 mt-4 align-bottom") content))
(defn styled-h2 [attrs & content]
  (apply t-h2 (assoc attrs :class "font-bold text-3xl mb-8 mt-8 align-bottom") content))
(defn styled-h1 [attrs & content]
  (apply t-h1 (assoc attrs :class "font-bold text-4xl mb-8 align-bottom") content))

(defn header []
  (t-header {:class "container max-w-3xl mx-auto px-4 pt-16 sm:flex justify-between"}
            (styled-h1 {} "Austin biking")
            (t-nav {:class "flex flex-row gap-4"}
                   (styled-a {:href "/" :ref "prefetch"} "Home")
                   (styled-a {:href "/groups" :ref "prefetch"} "Groups")
                   (styled-a {:href "/routes" :ref "prefetch"} "Routes")
                   (styled-a {:href "/about" :ref "prefetch"} "About"))))

(defn layout [description & content]
  (str "<!DOCTYPE html>"
       (t-html {:lang "en"}
               (t-head {}
                       (t-meta {:charset "UTF-8"})
                       (t-meta {:name "viewport" :content "width=device-width, initial-scale=1.0"})
                       (t-meta {:name "description" :content description})
                       (t-title {} "austin biking")
                       (t-link {:rel "icon" :type "image/svg+xml" :href "/bicycle.svg"})
                       (t-link {:rel "stylesheet" :href "/styles.css"}))
               (t-body {}
                       (header)
                       (apply t-main {:class "container max-w-3xl mx-auto px-4 pb-8"}
                              content)))))

(defn write-file [file data]
  (let [file (str output-dir "/" file)]
    (with-open [writer (io/writer file)]
      (.write writer data))))

; Writing the files
(when-not (fs/exists? output-dir)
  (fs/create-dirs output-dir))

(fs/copy-tree "public" output-dir {:replace-existing true})

(defn meetup [m]
  (let [loc-text (or (:location m) (get m "Location") "")
   loc-url  (or (:location-url m) (get m "Location URL") (get m "Location Url") "")]
    (t-li {}
     (t-div {:class "grid grid-cols-3 sm:grid-cols-6 gap-4 mb-2 items-center"}
       (t-div {:class "font-bold"} (:time m))
       (t-div {:class "text-right sm:text-left col-span-2 sm:col-span-5"}
         (styled-a {:href (str "/groups#" (:id m))} (:group m))))
     (t-div {:class "sm:grid sm:grid-cols-6 mb-4"}
       (t-div {:class "sm:col-start-2 sm:col-span-5"} (styled-p {} (:description m)))
                 (t-div {:class "sm:col-start-2 sm:col-span-5"}
                        (if (and (seq (str/trim loc-url)))
                          ;; show a clear clickable link: arrow before the link text, link underlined
                          (styled-p {}
                                    (str "Location: ")
                                    "↗ "
                                    (styled-a {:href loc-url :target "_blank" :rel "noopener noreferrer"}
                                              (or (some-> loc-text str/trim not-empty) loc-url)))
                          ;; fallback: plain text location
                          (styled-p {} (str "Location: " loc-text))))))))

(defn day [meetups day]
  (str
   (styled-h2 {} (str/capitalize (name day)))
   (t-ol {}
         (apply str (map meetup (get meetups day))))))

(defn day-block [active c]
  (let [cls "rounded block h-6 w-6 text-center text-white "
        cls (if active (str cls "bg-green-400") (str cls "bg-gray-400"))]
    (t-div {:class cls} c)))

(defn bike-group [g]
  (t-div {:class "mb-8 gap-4"}
         (styled-h3 {:id (:id g)} (:name g))
         (styled-p  {} (:description g))
         (t-div {:class "flex flex-row gap-4 mb-2"} (if (= "" (:instagram g))
                                                      ""
                                                      (styled-a {:href (:instagram g)} "Instagram"))
                (if (= "" (:facebook g))
                  ""
                  (styled-a {:href (:facebook g)} "Facebook"))
                (if (= "" (:twitter g))
                  ""
                  (styled-a {:href (:twitter g)} "Twitter"))
                (if (= "" (:website g))
                  ""
                  (styled-a {:href (:website g)} "Website"))
                (if (= "" (:strava g))
                  ""
                  (styled-a {:href (:strava g)} "Strava")))
         (t-div {:class "flex flex-row gap-2"}
                (day-block (:monday g) "M")
                (day-block (:tuesday g) "T")
                (day-block (:wednesday g) "W")
                (day-block (:thursday g) "T")
                (day-block (:friday g) "F")
                (day-block (:saturday g) "S")
                (day-block (:sunday g) "S"))))

(def meetups (edn/read-string (slurp "data/meetups.edn")))
(write-file "index.html"
            (layout "All of the weekly biking events in Austin, Texas"
                    (day meetups :monday)
                    (day meetups :tuesday)
                    (day meetups :wednesday)
                    (day meetups :thursday)
                    (day meetups :friday)
                    (day meetups :saturday)
                    (day meetups :sunday)))

(def bike-groups (edn/read-string (slurp "data/bike_groups.edn")))
(write-file "groups.html"
            (apply layout "All the biking groups in Austin, Texas"
                   (styled-h2 {} "Groups")
                   (map bike-group bike-groups)))

(def lists [{:name "Violet Crown List"
                 :link "https://www.violetcrown.org/routes"
                 :description "Violet Crown's recommended list of routes."}
                {:name "Austin City List"
                 :link "https://austin.maps.arcgis.com/apps/webappviewer/index.html?id=c7fecf32a2d946fabdf062285d58d40c&extent=3052120.7123%2C10036958.1486%2C3179054.0456%2C10097891.4819%2C102739"
                 :description "Austin City's arcgis list with bike routes."}])

(def tracks [{:name "Austin High School"
              :location "1715 W Cesar Chavez St, Austin, TX 78703"
              :description "The High School track is open to the public and is a great place to train."}
             {:name "Yellow Jacket Stadium"
              :location "3101-3189 Hargrave St, Austin, TX 78702"
              :description "A High School track in the East side of Austin."}])

(def trails [{:name "Greenbelt"
              :location "There are multiple entry points, parking at Barton Springs Pool and Barton Creek Greenbelt Trailhead are the easiest."
              :description "The greenbelt is a long trail that starts the the south west corner of Austin, it goes for around 10 miles out and back. It is very well maintained and highly trafficed on the weekends."}])

(write-file "routes.html"
            (layout "Top biking routes in Austin, Texas"
                    (styled-h2 {} "Long Rides")
                    (apply str (map (fn [r] (t-div {:class "mb-8"}
                                                   (styled-h3 {} (:name r))
                                                   (styled-p {} (:description r))
                                                   (styled-a {:href (:link r)} "Link"))) lists))))
                    ;; (styled-h2 {} "Tracks")
                    ;; (apply str (map (fn [r] (t-div {:class "mb-8"}
                    ;;                                (styled-h3 {} (:name r))
                    ;;                                (styled-p {} (:description r))
                    ;;                                (styled-p {} (str "Location: " (:location r))))) tracks))
                    ;; (styled-h2 {} "Trails")
                    ;; (apply str (map (fn [r] (t-div {:class "mb-8"}
                    ;;                                (styled-h3 {} (:name r))
                    ;;                                (styled-p {} (:description r))
                    ;;                                (styled-p {} (str "Location: " (:location r))))) trails))))

(write-file "about.html"
            (layout "About the austinbikegroups.com"
                    (styled-h2 {} "About")
                    (styled-p {} "This website was created by "
                              (styled-a {:href "https://www.strava.com/athletes/46171421"} "Asvin Kumar") 
                              " and is heavily inspired by the "
                              (styled-a {:href "https://austinrungroups.com"} "Austin Run Groups")
                              " website created by "
                              (styled-a {:href "https://www.strava.com/athletes/25975441"} "Kyle Henderson"))
                    (t-br)
                    (styled-h2 {} "How to Help?")
                    (styled-p {} "The main source of truth for this website is a "
                              (styled-a {:href "https://docs.google.com/spreadsheets/d/1DA0WkQjf5D3Vwewsgj9WlncnWsRJL4XmLL5Ak8Umh88/edit?usp=sharing"} "spreadsheet")
                              ". If you see anything off feel free to reach out to me through this "
                              (styled-a {:href "https://forms.gle/Jzt5PQdFoLYxGjXk9"} "google form")
                              ". If you REALLY want to help, I am open to giving out edit privileges.")
                    (styled-p {} "For people with some coding experience, the website is generated at this "
                              (styled-a {:href "https://github.com/asvin-kumar/bikegroups"} "repo")
                              ". The code is currently open and I will look through any pull requests, although I will not give any guarantees on a fast response time.")))

(println "DONE: building content")
