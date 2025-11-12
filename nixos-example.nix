# Example NixOS configuration for spotify-sync service
{ config, pkgs, ... }:

let
  # Define your secrets as constants here
  # WARNING: These will be in the Nix store which is world-readable!
  # For production, consider using agenix or sops-nix instead
  spotifySecrets = {
    clientId = "your_client_id_here";
    clientSecret = "your_client_secret_here";
    refreshToken = "your_refresh_token_here";
  };
in
{
  imports = [
    # Import the spotify-sync module from the flake
    # If using as a flake input:
    # inputs.spotify-sync.nixosModules.default
  ];

  # Enable and configure the service
  services.spotify-sync = {
    enable = true;

    # Spotify credentials (passed directly as values)
    clientId = spotifySecrets.clientId;
    clientSecret = spotifySecrets.clientSecret;
    refreshToken = spotifySecrets.refreshToken;

    # Where to download songs
    downloadPath = "/var/lib/spotify-sync";

    # How often to sync (systemd calendar format)
    # Examples:
    # - "hourly" - every hour
    # - "daily" - once per day at midnight
    # - "weekly" - once per week on Monday at midnight
    # - "*:0/15" - every 15 minutes
    # - "Mon,Wed,Fri 10:00" - Monday, Wednesday, Friday at 10 AM
    schedule = "daily";

    # Audio format: "mp3" (smaller) or "flac" (lossless, larger)
    format = "mp3";

    # Optional: If using a path in /home/, set user/group to your user
    # downloadPath = "/home/youruser/music";
    # user = "youruser";
    # group = "users";
  };

  # Alternative: Using agenix for secrets management (more secure)
  # age.secrets.spotify-client-secret.file = ./secrets/spotify-client-secret.age;
  # age.secrets.spotify-refresh-token.file = ./secrets/spotify-refresh-token.age;
  #
  # services.spotify-sync = {
  #   clientSecret = config.age.secrets.spotify-client-secret.value;
  #   refreshToken = config.age.secrets.spotify-refresh-token.value;
  # };

  # Alternative: Using sops-nix for secrets management (more secure)
  # sops.secrets.spotify-client-secret = {};
  # sops.secrets.spotify-refresh-token = {};
  #
  # services.spotify-sync = {
  #   clientSecret = config.sops.secrets.spotify-client-secret.value;
  #   refreshToken = config.sops.secrets.spotify-refresh-token.value;
  # };
}
