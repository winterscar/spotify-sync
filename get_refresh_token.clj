#!/usr/bin/env bb

(require '[babashka.http-client :as http]
         '[cheshire.core :as json]
         '[clojure.string :as str])

(defn get-env-var [var-name]
  (or (System/getenv var-name)
      (throw (ex-info (str "Missing environment variable: " var-name)
                      {:var var-name}))))

(defn exchange-code-for-token [client-id client-secret code redirect-uri]
  (try
    (let [body-str (str "grant_type=authorization_code"
                       "&code=" code
                       "&redirect_uri=" (java.net.URLEncoder/encode redirect-uri "UTF-8"))
          response (http/post "https://accounts.spotify.com/api/token"
                              {:headers {"Content-Type" "application/x-www-form-urlencoded"}
                               :basic-auth [client-id client-secret]
                               :body body-str})
          body (json/parse-string (:body response) true)]
      body)
    (catch Exception e
      (let [response (ex-data e)
            body (try (json/parse-string (:body response) true)
                      (catch Exception _ nil))]
        (throw (ex-info (str "Failed to exchange code for token. "
                            (if body
                              (str "Error: " (:error body) " - " (:error_description body))
                              "The authorization code may have expired or been used already."))
                       {:response-body body
                        :status (:status response)}))))))

(defn -main [& args]
  (try
    (let [client-id (get-env-var "SPOTIFY_CLIENT_ID")
          client-secret (get-env-var "SPOTIFY_CLIENT_SECRET")
          redirect-uri (or (System/getenv "SPOTIFY_REDIRECT_URI")
                          "http://127.0.0.1:8888/callback")]

      (if (= (count args) 0)
        ;; Show authorization URL
        (do
          (println "\n=== Spotify Authorization ===\n")
          (println "Using redirect URI:" redirect-uri)
          (println "IMPORTANT: This MUST match exactly what you entered in your Spotify app settings!")
          (println "\nIf different, set SPOTIFY_REDIRECT_URI environment variable or update your Spotify app.\n")
          (println "Step 1: Open this URL in your browser:\n")
          (println (str "https://accounts.spotify.com/authorize"
                       "?client_id=" client-id
                       "&response_type=code"
                       "&redirect_uri=" redirect-uri
                       "&scope=" (java.net.URLEncoder/encode "user-library-read streaming" "UTF-8")))
          (println "\nStep 2: After authorizing, you'll be redirected to:")
          (println (str redirect-uri "?code=..."))
          (println "The page won't load - that's OK! Just copy the full URL from your browser.")
          (println "\nStep 3: Copy the 'code' parameter from the URL and run:")
          (println (str "  bb " *file* " YOUR_CODE_HERE"))
          (println "\nIMPORTANT: Authorization codes are single-use and expire in ~10 minutes.")
          (println "If you get an error, generate a fresh code by opening the URL again.\n"))

        ;; Exchange code for tokens
        (let [code (str/trim (first args))]
          (println "\n=== Debug Info ===")
          (println "Redirect URI:" redirect-uri)
          (println "Code length:" (count code))
          (println "Code (first 20 chars):" (subs code 0 (min 20 (count code))))
          (println "Code (last 20 chars):" (subs code (max 0 (- (count code) 20))))
          (println "\nExchanging code for tokens...\n")
          (let [result (exchange-code-for-token client-id client-secret code redirect-uri)]
            (println "\n=== Success! ===\n")
            (println "Add this to your environment variables:\n")
            (println (str "export SPOTIFY_REFRESH_TOKEN=\"" (:refresh_token result) "\""))
            (println "\nYour access token (valid for 1 hour):")
            (println (:access_token result))
            (println "\nRefresh token (save this!):")
            (println (:refresh_token result))))))

    (catch Exception e
      (println "Error:" (.getMessage e))
      (when-let [data (ex-data e)]
        (println "Details:" data))
      (System/exit 1))))

(apply -main *command-line-args*)
