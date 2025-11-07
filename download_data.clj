(require
 '[babashka.http-client :as http]
 '[babashka.fs :as fs]
 '[clojure.data.csv :as csv]
 '[clojure.java.io :as io]
 '[clojure.string :as str])

(def base-url "https://docs.google.com/spreadsheets/d/1DA0WkQjf5D3Vwewsgj9WlncnWsRJL4XmLL5Ak8Umh88/export?format=csv")
(def meetups-sheet-id "0")
(def bike-groups-sheet-id "822867825")
(def events-sheet-id "1618285110")

(defn url [sheet-id] (str base-url "&gid=" sheet-id))

(defn safe-get-body [u]
  (try
    (let [resp (http/get u)]
      (if (and resp (= 200 (:status resp)))
        (:body resp)
        (do (println "Warning: HTTP GET failed for" u "status:" (:status resp)) "")))
    (catch Exception e
      (println "Warning: HTTP GET exception for" u ":" (.getMessage e))
      "")))

(defn read-csv-file [file-path]
  (try
    (when (fs/exists? file-path)
      (with-open [reader (io/reader file-path)]
        (doall (csv/read-csv reader))))
    (catch Exception e
      (println "Warning: could not read CSV" file-path ":" (.getMessage e))
      [])))

(defn to-id [name]
  "Create a URL-friendly id from `name`. Returns nil when name is nil." 
  (some-> name
          str/trim
          (str/replace #"\s+" "-")
          (str/lower-case)))

; Setting up the dist directory
(when-not (fs/exists? "data")
  (fs/create-dirs "data"))

; Save the first meetup content
(def meetups-file "data/meetups.csv")
(spit meetups-file (safe-get-body (url meetups-sheet-id)))

;; Normalize header row into keywords. Be forgiving about casing/spacing.
;; If a header isn't recognized, convert it to a kebab-case keyword.
(defn meetup-header [header]
  (let [mappings {"biking group" :group
                  "day of the week" :day
                  "time" :time
                  "description" :description
                  "location" :location
                  "location url" :location-url}
        normalize (fn [h]
                    (let [s (some-> h str/trim str/lower-case)]
                      (if (contains? mappings s)
                        (mappings s)
                        ;; fallback: turn into a safe keyword
                        (-> s
                            (str/replace #"[^a-z0-9]+" "-")
                            (str/replace #"(^-|-$)" "")
                            (keyword)))))]
    (map normalize header)))

(defn parse-time [time-str]
  "Parse a time string into an integer like 930 for 9:30AM, or nil on invalid input." 
  (when-let [t (some-> time-str str/trim (not-empty))]
    (cond
      (= t "Morning") 600
      (= t "Afternoon") 1200
      (= t "Evening") 1800
      :else
      (let [m (re-matches #"(\d{1,2}):(\d{2}) ?(AM|PM)" t)]
        (when m
          (let [[_ hour minute period] m
                hour-int (Integer/parseInt hour)
                minute-int (Integer/parseInt minute)
                hour-24 (cond
                          (= period "AM") (if (= hour-int 12) 0 hour-int)
                          (= period "PM") (if (= hour-int 12) 12 (+ hour-int 12)))]
            (+ (* hour-24 100) minute-int)))))))

(defn get-meetups [file]
  (let [data (or (read-csv-file file) [])
        first-row (first data)
        header (when first-row (meetup-header first-row))
        rows (rest data)]
    (if (or (nil? header) (empty? rows))
      []
      (->> rows
           (map #(zipmap header %))
           (map (fn [m]
                  (let [loc (some-> (:location m) str/trim not-empty)
                        loc-url (some-> (:location-url m) str/trim not-empty)
                        location (if (seq loc-url)
                                   (format "<a href=\"%s\" target=\"_blank\" rel=\"noopener noreferrer\">%s</a>"
                                           loc-url (or loc loc-url))
                                   loc)]
                    (-> m
                        (assoc :location location)
                        (assoc :id (to-id (:group m)))
                        (assoc :parsed-time (parse-time (:time m)))
                        (assoc :day (when-let [d (some-> (:day m) str/trim not-empty str/lower-case)]
                                      (keyword d)))))))
           (sort-by #(or (:parsed-time %) 9999))
           vec))))

(def meetups
  (->> meetups-file
       (get-meetups)
       (group-by :day)))

(spit "data/meetups.edn" (pr-str meetups))

; Generating the bike-groups content
(def bike-groups-file "data/bike-groups.csv")
(spit bike-groups-file (safe-get-body (url bike-groups-sheet-id)))

(defn bike-group-header [header]
  (replace {"Name" :name
            "Description" :description
            "Instagram" :instagram
            "Twitter" :twitter
            "Strava" :strava
            "Website" :website
            "Facebook" :facebook} header))

(defn day-has [meetups day id]
  (some #(= id (:id %)) (get meetups day [])))

(defn add-days [group]
  (let [id (:id group)]
    (assoc group
           :monday (day-has meetups :monday id)
           :tuesday (day-has meetups :tuesday id)
           :wednesday (day-has meetups :wednesday id)
           :thursday (day-has meetups :thursday id)
           :friday (day-has meetups :friday id)
           :saturday (day-has meetups :saturday id)
           :sunday (day-has meetups :sunday id))))

(defn get-bike-groups [file]
  (let [data (read-csv-file file)
        header (bike-group-header (first data))
        bike-groups (rest data)]
    (->> bike-groups
         (map #(zipmap header %))
         (map #(assoc % :id (to-id (:name %))))
         (sort-by :id)
         (map add-days))))

(def bike-groups
  (->> bike-groups-file
       (get-bike-groups)))

(spit "data/bike_groups.edn" (pr-str bike-groups))

(spit "./data/events.csv" (safe-get-body (url events-sheet-id)))
(defn event-header [header]
  (replace {"Name" :name
            "Date" :date
            "Time" :time
            "Description" :description
            "Location" :location
            "Distances" :distances
            "Host" :host
            "Website" :website
            "Instagram" :instagram
            "Facebook" :facebook
            "Twitter" :twitter} header))

(defn parse-date [date]
  (let [formats ["M/dd/yy" "M/d/yy" "M/dd/yyyy" "M/d/yyyy" "MM/dd/yy" "MM/dd/yyyy"]
        formatters (map #(java.time.format.DateTimeFormatter/ofPattern %) formats)]
    (some #(try
             (java.time.LocalDate/parse date %)
             (catch Exception _ nil))
          formatters)))
(defn get-events [file]
  (let [data (read-csv-file file)
        header (event-header (first data))
        events (rest data)
        now (.toLocalDate (java.time.LocalDateTime/now))]
    (->> events
         (map #(zipmap header %))
         (map #(assoc % :date (parse-date (:date %))))
         (map #(assoc % :has-happened? (boolean (and (:date %) (.isAfter now (:date %)))))))))

(def events (get-events "./data/events.csv"))
(spit "data/events.edn" (pr-str events))

(println "DONE: downloading data")
