#!/usr/bin/env bb

(require '[babashka.http-client :as http]
         '[babashka.process :as process]
         '[clojure.string :as str]
         '[clojure.java.io :as io]
         '[clojure.edn :as edn]
         '[cheshire.core :as json])

(def spotify-api-base "https://api.spotify.com/v1")
(def spotify-auth-base "https://accounts.spotify.com")
(def spotify-dl-bin
  (if-let [bin (System/getenv "SPOTIFY_DL_BIN")]
    bin
    (if (System/getenv "NIX_BUILD_TOP")
      "spotify-dl"  ; In Nix, use from PATH
      (str (System/getenv "HOME") "/src/spotify-sync/spotify-dl/target/debug/spotify-dl"))))
(def downloaded-file "downloaded.edn")
(def songs-dir ".")
(def temp-download-dir "/tmp/spotify-sync-download")

(defn get-env-var [var-name]
  (or (System/getenv var-name)
      (throw (ex-info (str "Missing environment variable: " var-name)
                      {:var var-name}))))

(defn read-downloaded []
  "Read the set of already downloaded track URIs"
  (if (.exists (io/file downloaded-file))
    (set (edn/read-string (slurp downloaded-file)))
    #{}))

(defn write-downloaded [downloaded-uris]
  "Write the set of downloaded track URIs to file"
  (spit downloaded-file (pr-str downloaded-uris)))

(defn sanitize-filename [s]
  "Remove/replace invalid filesystem characters"
  (-> s
      (str/replace #"[/\\:*?\"<>|]" "_")
      (str/trim)))

(defn ensure-temp-dir []
  "Create and clean temporary download directory"
  (let [temp-dir (io/file temp-download-dir)]
    ;; Remove old temp directory if it exists
    (when (.exists temp-dir)
      (doseq [file (.listFiles temp-dir)]
        (.delete file))
      (.delete temp-dir))
    ;; Create fresh temp directory
    (.mkdir temp-dir)
    temp-dir))

(defn cleanup-temp-dir []
  "Remove temporary download directory"
  (try
    (let [temp-dir (io/file temp-download-dir)]
      (when (.exists temp-dir)
        (doseq [file (.listFiles temp-dir)]
          (try
            (.delete file)
            (catch Exception e
              (println (str "Warning: Could not delete temp file " (.getName file) ": " (.getMessage e))))))
        (.delete temp-dir)))
    (catch Exception e
      (println (str "Warning: Could not clean up temp directory: " (.getMessage e))))))

(defn get-access-token
  "Get access token using refresh token flow"
  [client-id client-secret refresh-token]
  (let [body-str (str "grant_type=refresh_token&refresh_token=" refresh-token)
        response (http/post (str spotify-auth-base "/api/token")
                            {:headers {"Content-Type" "application/x-www-form-urlencoded"}
                             :basic-auth [client-id client-secret]
                             :body body-str})
        body (json/parse-string (:body response) true)]
    (:access_token body)))

(defn fetch-liked-songs
  "Fetch liked songs from Spotify (most recent first).
   By default, only fetches the first page (50 tracks) for efficiency.
   Set fetch-all? to true to fetch entire library."
  ([access-token] (fetch-liked-songs access-token false))
  ([access-token fetch-all?]
   (loop [url (str spotify-api-base "/me/tracks?limit=50")
          all-tracks []]
     (let [response (http/get url
                             {:headers {"Authorization" (str "Bearer " access-token)}})
           body (json/parse-string (:body response) true)
           tracks (mapv (fn [item]
                         (let [album (get-in item [:track :album])
                               release-date (:release_date album)
                               year (when release-date (subs release-date 0 4))]
                           {:name (get-in item [:track :name])
                            :artist (str/join ", " (map :name (get-in item [:track :artists])))
                            :album (:name album)
                            :album-uri (:uri album)
                            :album-id (:id album)
                            :year year
                            :added-at (:added_at item)
                            :id (get-in item [:track :id])
                            :uri (get-in item [:track :uri])}))
                       (:items body))
           accumulated (into all-tracks tracks)
           next-url (:next body)]
       (if (and fetch-all? next-url)
         (recur next-url accumulated)
         accumulated)))))

(defn fetch-album-tracks
  "Fetch all tracks from an album"
  [access-token album-id]
  (loop [url (str spotify-api-base "/albums/" album-id "/tracks?limit=50")
         all-tracks []]
    (let [response (http/get url
                            {:headers {"Authorization" (str "Bearer " access-token)}})
          body (json/parse-string (:body response) true)
          tracks (mapv (fn [item]
                        {:name (:name item)
                         :artist (str/join ", " (map :name (:artists item)))
                         :uri (:uri item)
                         :id (:id item)})
                      (:items body))
          accumulated (into all-tracks tracks)]
      (if-let [next-url (:next body)]
        (recur next-url accumulated)
        accumulated))))

(defn find-track-file [track-id current-dir format]
  "Find a file by track ID (spotify-dl now names files by track ID)"
  (let [extension (str "." format)
        expected-file (io/file current-dir (str track-id extension))]
    (when (.exists expected-file)
      expected-file)))

(defn organize-file [track current-file format]
  "Move file to organized directory structure: <artist>/<album [year]>/<track>.<format>"
  (let [artist (sanitize-filename (:artist track))
        album-base (sanitize-filename (:album track))
        album-with-year (if-let [year (:year track)]
                         (str album-base " [" year "]")
                         album-base)
        track-name (sanitize-filename (:name track))
        dest-dir (io/file songs-dir artist album-with-year)
        extension (str "." format)
        dest-file (io/file dest-dir (str track-name extension))]

    ;; Create directory structure
    (.mkdirs dest-dir)

    ;; Move file
    (when (.exists current-file)
      (io/copy current-file dest-file)
      (.delete current-file)
      (println (str "  ✓ Organized: " artist "/" album-with-year "/" track-name extension))
      true)))

(defn process-album-downloads [albums-to-process access-token format]
  "Match downloaded files to album tracks and organize them"
  (println "\nOrganizing downloaded files...")
  (let [downloaded (read-downloaded)
        successful (atom downloaded)
        current-dir temp-download-dir]

    (doseq [{:keys [album-uri album-id album year artist]} albums-to-process]
      (println (str "\nProcessing album: " album " [" year "]..."))
      ;; Fetch all tracks in this album from Spotify
      (let [album-tracks (fetch-album-tracks access-token album-id)
            ;; Enrich album tracks with album metadata
            enriched-tracks (mapv #(assoc %
                                          :album album
                                          :year year
                                          ;; Use the artist from the album track, not the liked song
                                          :artist (:artist %))
                                 album-tracks)
            ;; Track how many files we successfully organize
            organized-count (atom 0)]

        ;; Try to match and organize each track by track ID
        (doseq [track enriched-tracks]
          (when-let [matched-file (find-track-file (:id track) current-dir format)]
            (println (str "    Found: " (.getName matched-file) " → " (:name track)))
            (when (organize-file track matched-file format)
              (swap! organized-count inc))))

        ;; If we found at least one track, consider the album successfully downloaded
        (when (pos? @organized-count)
          (swap! successful conj album-uri)
          (println (str "  ✓ Organized " @organized-count " tracks from album")))))

    ;; Save updated downloaded list
    (write-downloaded @successful)
    (println (str "\n✓ Organization complete! " (- (count @successful) (count downloaded)) " new albums added."))))

(defn download-tracks [tracks access-token]
  (let [already-downloaded (read-downloaded)
        ;; Filter to tracks whose albums aren't already downloaded
        tracks-to-download (filterv #(not (contains? already-downloaded (:album-uri %))) tracks)

        ;; Group by album to avoid downloading the same album multiple times
        albums-to-download (vals (group-by :album-uri tracks-to-download))
        unique-albums (mapv first albums-to-download)

        skipped-count (- (count tracks) (count tracks-to-download))
        format (or (System/getenv "SPOTIFY_DOWNLOAD_FORMAT") "mp3")]

    (println (str "\nFound " (count tracks) " liked songs"))
    (when (pos? skipped-count)
      (println (str "  " skipped-count " songs from already downloaded albums (skipping)")))
    (println (str "  " (count unique-albums) " unique albums to download\n"))

    (if (empty? unique-albums)
      (println "Nothing to download!")
      (do
        ;; Ensure clean temp directory for downloads
        (ensure-temp-dir)
        (println (str "Downloading albums as " (str/upper-case format) " to temporary directory...\n"))
        (let [album-uris (mapv :album-uri unique-albums)
              args (concat ["-a" access-token "-f" format] album-uris)
              _ (println "Running spotify-dl with" (count album-uris) "albums...")
              ;; Download to temp directory
              result (apply process/shell {:out :inherit :err :inherit :dir temp-download-dir}
                            spotify-dl-bin args)]
          (if (zero? (:exit result))
            (do
              (println "\n✓ Download complete!")
              (process-album-downloads unique-albums access-token format)
              ;; Clean up temp directory after successful organization
              (cleanup-temp-dir)
              (println "✓ Temporary files cleaned up"))
            (do
              (println "\n✗ Download failed with exit code:" (:exit result))
              (cleanup-temp-dir)
              (System/exit 1))))))))

(defn -main []
  (try
    (let [client-id (get-env-var "SPOTIFY_CLIENT_ID")
          client-secret (get-env-var "SPOTIFY_CLIENT_SECRET")
          refresh-token (get-env-var "SPOTIFY_REFRESH_TOKEN")
          fetch-all? (= "true" (System/getenv "SPOTIFY_FETCH_ALL"))
          _ (println "Authenticating with Spotify...")
          access-token (get-access-token client-id client-secret refresh-token)
          _ (if fetch-all?
              (println "Fetching all liked songs...")
              (println "Fetching recent liked songs (first 50)..."))
          tracks (fetch-liked-songs access-token fetch-all?)]
      (download-tracks tracks access-token))
    (catch Exception e
      (println "\nError:" (.getMessage e))
      (when-let [data (ex-data e)]
        (println "Details:" data))
      (when (and (ex-data e) (= 400 (:status (ex-data e))))
        (println "\nIf you're getting a 400 error, your refresh token may be invalid.")
        (println "Run ./get_refresh_token.clj to generate a new refresh token."))
      ;; Clean up temp directory on error
      (cleanup-temp-dir)
      (System/exit 1))))

(-main)
