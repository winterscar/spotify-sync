{
  description = "Spotify liked songs sync service";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = nixpkgs.legacyPackages.${system};
      in
      {
        packages = {
          spotify-dl = pkgs.rustPlatform.buildRustPackage {
            pname = "spotify-dl";
            version = "0.9.2";

            src = ./spotify-dl;

            cargoLock = {
              lockFile = ./spotify-dl/Cargo.lock;
            };

            nativeBuildInputs = with pkgs; [
              pkg-config
            ];

            buildInputs = with pkgs; [
              openssl
            ] ++ pkgs.lib.optionals pkgs.stdenv.isLinux [
              alsa-lib
            ] ++ pkgs.lib.optionals pkgs.stdenv.isDarwin [
              darwin.apple_sdk.frameworks.Security
              darwin.apple_sdk.frameworks.CoreFoundation
            ];

            meta = with pkgs.lib; {
              description = "Download Spotify tracks using librespot";
              homepage = "https://github.com/winterscar/spotify-sync";
              license = licenses.mit;
            };
          };

          spotify-sync = pkgs.writeScriptBin "spotify-sync" ''
            #!${pkgs.bash}/bin/bash
            set -euo pipefail

            # Check required environment variables
            : "''${SPOTIFY_CLIENT_ID:?SPOTIFY_CLIENT_ID not set}"
            : "''${SPOTIFY_CLIENT_SECRET:?SPOTIFY_CLIENT_SECRET not set}"
            : "''${SPOTIFY_REFRESH_TOKEN:?SPOTIFY_REFRESH_TOKEN not set}"

            # Set spotify-dl binary path
            export SPOTIFY_DL_BIN="${self.packages.${system}.spotify-dl}/bin/spotify-dl"

            # Run the sync script
            ${pkgs.babashka}/bin/bb ${./fetch_liked_songs.clj}
          '';

          default = self.packages.${system}.spotify-sync;
        };

        devShells.default = pkgs.mkShell {
          buildInputs = with pkgs; [
            babashka
            rustc
            cargo
            pkg-config
          ];
        };
      }
    ) // {
      nixosModules.default = { config, lib, pkgs, ... }:
        with lib;
        let
          cfg = config.services.spotify-sync;
          system = pkgs.stdenv.hostPlatform.system;
        in
        {
          options.services.spotify-sync = {
            enable = mkEnableOption "Spotify liked songs sync service";

            clientId = mkOption {
              type = types.str;
              description = "Spotify application client ID";
            };

            clientSecret = mkOption {
              type = types.str;
              description = ''
                Spotify client secret.
                WARNING: This will be stored in the Nix store, which is world-readable.
                Consider using agenix or sops-nix for better security.
              '';
            };

            refreshToken = mkOption {
              type = types.str;
              description = ''
                Spotify refresh token.
                WARNING: This will be stored in the Nix store, which is world-readable.
                Consider using agenix or sops-nix for better security.
              '';
            };

            downloadPath = mkOption {
              type = types.path;
              default = "/var/lib/spotify-sync";
              description = "Directory where songs will be downloaded";
            };

            schedule = mkOption {
              type = types.str;
              default = "daily";
              description = ''
                When to run the sync (systemd timer format).
                Examples: "hourly", "daily", "weekly", "*:0/15" (every 15 minutes)
              '';
              example = "daily";
            };

            format = mkOption {
              type = types.enum [ "mp3" "flac" ];
              default = "mp3";
              description = ''
                Audio format for downloaded tracks.
                - mp3: Smaller file size, lossy compression
                - flac: Larger file size, lossless quality
              '';
              example = "mp3";
            };

            fetchAll = mkOption {
              type = types.bool;
              default = false;
              description = ''
                Whether to fetch all liked songs or just the most recent 50.
                The Spotify API returns tracks in reverse chronological order (newest first).
                Set to false (default) for faster syncs if you don't add many songs between runs.
                Set to true to ensure all historical liked songs are synced.
              '';
            };

            user = mkOption {
              type = types.str;
              default = "spotify-sync";
              description = "User account under which spotify-sync runs";
            };

            group = mkOption {
              type = types.str;
              default = "spotify-sync";
              description = "Group under which spotify-sync runs";
            };
          };

          config = mkIf cfg.enable (mkMerge [
            # Only create system user and group if using defaults
            (mkIf (cfg.user == "spotify-sync" && cfg.group == "spotify-sync") {
              users.users.spotify-sync = {
                isSystemUser = true;
                group = "spotify-sync";
                home = cfg.downloadPath;
                createHome = true;
                description = "Spotify sync service user";
              };

              users.groups.spotify-sync = {};
            })

            # Service configuration (always applied when enabled)
            {

            systemd.services.spotify-sync = {
              description = "Sync Spotify liked songs";
              after = [ "network-online.target" ];
              wants = [ "network-online.target" ];

              serviceConfig = {
                Type = "oneshot";
                User = cfg.user;
                Group = cfg.group;
                WorkingDirectory = cfg.downloadPath;

                # Set environment variables directly
                Environment = [
                  "SPOTIFY_CLIENT_ID=${cfg.clientId}"
                  "SPOTIFY_CLIENT_SECRET=${cfg.clientSecret}"
                  "SPOTIFY_REFRESH_TOKEN=${cfg.refreshToken}"
                  "SPOTIFY_DOWNLOAD_FORMAT=${cfg.format}"
                  "SPOTIFY_FETCH_ALL=${if cfg.fetchAll then "true" else "false"}"
                ];

                ExecStart = "${self.packages.${system}.spotify-sync}/bin/spotify-sync";

                # Security hardening
                PrivateTmp = true;
                ProtectSystem = "strict";
                # Disable ProtectHome if download path is in /home
                ProtectHome = mkIf (!(lib.hasPrefix "/home/" cfg.downloadPath)) true;
                ReadWritePaths = [ cfg.downloadPath ];
                NoNewPrivileges = true;
                ProtectKernelTunables = true;
                ProtectKernelModules = true;
                ProtectControlGroups = true;
                RestrictAddressFamilies = [ "AF_INET" "AF_INET6" ];
                RestrictNamespaces = true;
                LockPersonality = true;
                RestrictRealtime = true;
                RestrictSUIDSGID = true;
                PrivateMounts = true;
              };
            };

            systemd.timers.spotify-sync = {
              description = "Timer for Spotify sync service";
              wantedBy = [ "timers.target" ];

              timerConfig = {
                OnCalendar = cfg.schedule;
                Persistent = true;
                RandomizedDelaySec = "5m";
              };
            };
            }
          ]);
        };
    };
}
