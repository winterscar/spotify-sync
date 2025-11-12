# Spotify Liked Songs Downloader

A Babashka script to fetch and download your liked songs from Spotify as MP3 or FLAC files. Automatically syncs your entire Spotify liked songs library to organized local files with configurable quality.

This project uses a modified version of [spotify-dl](https://github.com/GuillemCastro/spotify-dl) for downloading tracks.

## Features

- 🎵 **Album-based downloading**: Downloads entire albums when you like a song
- 📁 **Organized structure**: `songs/<artist>/<album [year]>/<track>.mp3`
- 🔄 **Incremental sync**: Only downloads new albums on subsequent runs
- 🎯 **ID-based matching**: Perfect file matching using Spotify track IDs
- 🎧 **Flexible formats**: Choose MP3 (smaller) or FLAC (lossless) quality
- 🐧 **NixOS integration**: Deploy as a systemd service with scheduled runs
- 🔒 **Secure**: Supports agenix/sops-nix for secret management

## Prerequisites

### For Manual Usage

- [Babashka](https://babashka.org/) installed
- [Rust](https://rustup.rs/) installed (for building spotify-dl)
- A Spotify account
- Spotify API credentials

### For NixOS

- NixOS with flakes enabled
- A Spotify account
- Spotify API credentials
- Secret management (agenix or sops-nix recommended)

## Setup

### 1. Create a Spotify App

1. Go to [Spotify Developer Dashboard](https://developer.spotify.com/dashboard)
2. Log in and click "Create app"
3. Fill in the details:
   - App name: "Liked Songs Fetcher" (or any name)
   - App description: "Personal script to fetch liked songs and download tracks"
   - Redirect URI: `http://127.0.0.1:8888/callback`
   - Which API/SDKs: Check **Web API**
4. Check the box to agree to terms and click "Save"
5. Note your **Client ID** and **Client Secret**

### 2. Get a Refresh Token

**Easy way** (using the helper script):

1. Set your client credentials:
   ```bash
   export SPOTIFY_CLIENT_ID="your_client_id"
   export SPOTIFY_CLIENT_SECRET="your_client_secret"
   ```

2. Run the helper script:
   ```bash
   ./get_refresh_token.clj
   ```

3. Open the URL it displays in your browser and authorize the app

4. Copy the `code` from the redirect URL and run:
   ```bash
   ./get_refresh_token.clj YOUR_CODE_HERE
   ```

5. The script will display your refresh token. Save it!

**Manual way** (using curl):

1. Generate an authorization URL (replace `YOUR_CLIENT_ID`):
   ```
   https://accounts.spotify.com/authorize?client_id=YOUR_CLIENT_ID&response_type=code&redirect_uri=http://127.0.0.1:8888/callback&scope=user-library-read%20streaming
   ```

2. Open this URL in your browser and authorize the app

3. You'll be redirected to `http://127.0.0.1:8888/callback?code=...`
   The page won't load (that's OK!). Copy the `code` parameter from the browser's address bar

4. Exchange the code for a refresh token (replace values):
   ```bash
   curl -X POST https://accounts.spotify.com/api/token \
     -H "Content-Type: application/x-www-form-urlencoded" \
     -u "YOUR_CLIENT_ID:YOUR_CLIENT_SECRET" \
     -d "grant_type=authorization_code&code=YOUR_CODE&redirect_uri=http://127.0.0.1:8888/callback"
   ```

5. Save the `refresh_token` from the response

### 3. Set Environment Variables

```bash
export SPOTIFY_CLIENT_ID="your_client_id"
export SPOTIFY_CLIENT_SECRET="your_client_secret"
export SPOTIFY_REFRESH_TOKEN="your_refresh_token"
export SPOTIFY_DOWNLOAD_FORMAT="mp3"  # or "flac" for lossless (optional, defaults to mp3)
```

Or create a `.env` file:
```bash
SPOTIFY_CLIENT_ID=your_client_id
SPOTIFY_CLIENT_SECRET=your_client_secret
SPOTIFY_REFRESH_TOKEN=your_refresh_token
SPOTIFY_DOWNLOAD_FORMAT=mp3  # or "flac" (optional, defaults to mp3)
```

And source it: `source .env`

### 4. Build spotify-dl

```bash
cd spotify-dl
cargo build --release
```

The binary will be at `spotify-dl/target/debug/spotify-dl` (or `spotify-dl/target/release/spotify-dl` if built with `--release`).

## Usage

Run the script to download all your liked songs:
```bash
./fetch_liked_songs.clj
```

Or:
```bash
bb fetch_liked_songs.clj
```

The script will:
1. Authenticate with Spotify using your refresh token
2. Fetch all your liked songs
3. Group songs by album
4. Skip albums that have already been downloaded (tracked in `downloaded.edn`)
5. **Download entire albums** (not just individual liked songs) as MP3 files
6. Query Spotify for all tracks in each album to ensure complete downloads
7. Organize downloaded files into `./songs/<artist>/<album [year]>/<track>.mp3`
8. Update `downloaded.edn` with successfully downloaded albums

**Why download full albums?**
- More efficient than downloading songs one-by-one
- Gives you the complete album even if you only liked one song
- Avoids duplicate downloads when you like multiple songs from the same album

**On subsequent runs**, the script will only download albums from newly liked songs.

## Manual spotify-dl Usage

You can also use spotify-dl directly with individual tracks, albums, or playlists:

```bash
# Download a single track
cd spotify-dl
./target/debug/spotify-dl -a "YOUR_ACCESS_TOKEN" -f mp3 spotify:track:xxxxx

# Download an album
./target/debug/spotify-dl -a "YOUR_ACCESS_TOKEN" -f mp3 spotify:album:xxxxx

# Download a playlist
./target/debug/spotify-dl -a "YOUR_ACCESS_TOKEN" -f mp3 spotify:playlist:xxxxx

# Download as FLAC (default)
./target/debug/spotify-dl -a "YOUR_ACCESS_TOKEN" spotify:track:xxxxx
```

## File Organization

Downloaded songs are organized into a clean directory structure:

```
songs/
├── Artist Name/
│   ├── Album Name [2024]/
│   │   ├── Track 01.mp3  (or .flac)
│   │   ├── Track 02.mp3
│   │   └── Track 03.mp3
│   └── Another Album [2023]/
│       └── Track.mp3
└── Another Artist/
    └── Album [2022]/
        └── Track.mp3
```

Album directories include the release year in square brackets for easy sorting and identification. Files will have `.mp3` or `.flac` extension depending on your configured format.

The `downloaded.edn` file tracks which **albums** have been successfully downloaded by their Spotify URI. This allows the script to:
- Skip already-downloaded albums on subsequent runs
- Only download new albums when you like new songs
- Automatically download complete albums (not just individual tracks)
- Resume if interrupted

To force re-download all albums, simply delete `downloaded.edn`.

## Notes

- The script fetches all liked songs (handles pagination automatically)
- **Album-based downloading**: When you like a song, the entire album is downloaded
  - If you like a second song from the same album, it won't be re-downloaded
  - You'll get the complete album even if you only liked one song
- **ID-based file matching**: Files are named by their Spotify track ID during download, then renamed to proper track names when organized
  - Ensures perfect matching regardless of special characters, multiple artists, or featured artists
  - Example: `4cOdK2wGLETKBW3PvgPWqT.mp3` → `songs/Artist/Album [2024]/Track Name.mp3`
- Requires the `user-library-read` and `streaming` scopes
  - `user-library-read`: Access your liked songs
  - `streaming`: Enables token to be used with spotify-dl and librespot
- The refresh token doesn't expire unless you revoke it
- Invalid filesystem characters in artist/album/track names are replaced with underscores

## Quick Start (NixOS)

1. **Get Spotify credentials** (see Setup section below)

2. **Get refresh token**:
   ```bash
   nix develop  # or use shell.nix
   export SPOTIFY_CLIENT_ID="your_client_id"
   export SPOTIFY_CLIENT_SECRET="your_client_secret"
   ./get_refresh_token.clj
   ```

3. **Add to your NixOS configuration** (see full example below)

4. **Deploy and enjoy**:
   ```bash
   nixos-rebuild switch
   systemctl status spotify-sync.timer
   ```

## NixOS Integration

### Using as a Flake

Add to your `flake.nix`:

```nix
{
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    spotify-sync = {
      url = "github:winterscar/spotify-sync";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs = { self, nixpkgs, spotify-sync, ... }: {
    nixosConfigurations.yourhostname = nixpkgs.lib.nixosSystem {
      system = "x86_64-linux";
      modules = [
        spotify-sync.nixosModules.default
        ./configuration.nix
      ];
    };
  };
}
```

Then in your `configuration.nix`:

```nix
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
  services.spotify-sync = {
    enable = true;
    clientId = spotifySecrets.clientId;
    clientSecret = spotifySecrets.clientSecret;
    refreshToken = spotifySecrets.refreshToken;
    downloadPath = "/var/lib/spotify-sync";
    schedule = "daily";  # or "hourly", "weekly", "*:0/15", etc.
    format = "mp3";      # or "flac" for lossless quality
  };
}
```

### Secret Management

**WARNING**: The basic configuration above stores secrets directly in the Nix store, which is world-readable. This is convenient but not secure for production use.

For better security, consider using a secrets management solution like agenix or sops-nix. The current module expects direct string values, so you would need to read the secret file contents. Here are some approaches:

**Option 1: Use secrets management with file reading**

Modify the module to support file-based secrets by adding options like `clientSecretFile` and using `LoadCredential` in the systemd service.

**Option 2: Keep secrets in a separate, restricted file**

Store your secrets in a separate `.nix` file with restricted permissions that's imported into your configuration:

```nix
# secrets.nix (chmod 600)
{
  spotifyClientSecret = "your_secret_here";
  spotifyRefreshToken = "your_token_here";
}

# configuration.nix
let
  secrets = import ./secrets.nix;
in {
  services.spotify-sync = {
    clientId = "your_client_id_here";
    clientSecret = secrets.spotifyClientSecret;
    refreshToken = secrets.spotifyRefreshToken;
  };
}
```

Note: This still puts secrets in the Nix store, but at least restricts access to the source file.

### Getting Your Refresh Token

Before enabling the service, you need to get a refresh token:

```bash
# Set your credentials
export SPOTIFY_CLIENT_ID="your_client_id"
export SPOTIFY_CLIENT_SECRET="your_client_secret"

# Run the helper script
./get_refresh_token.clj

# Follow the instructions to get your refresh token
```

Then store the refresh token in your secrets management system.

### Service Management

```bash
# Check service status
systemctl status spotify-sync.service

# View logs
journalctl -u spotify-sync.service -f

# Check timer status
systemctl status spotify-sync.timer

# List next scheduled runs
systemctl list-timers spotify-sync.timer

# Manually trigger a sync
systemctl start spotify-sync.service
```

### Configuration Options

**Format**

The `format` option controls the audio quality and file size of downloaded tracks:

- `"mp3"` (default) - Smaller file size (~5-10 MB per track), lossy compression, widely compatible
- `"flac"` - Larger file size (~30-50 MB per track), lossless quality, best audio fidelity

**Schedule**

The `schedule` option uses systemd calendar format:

- `"hourly"` - Every hour on the hour
- `"daily"` - Every day at midnight
- `"weekly"` - Every Monday at midnight
- `"*:0/15"` - Every 15 minutes
- `"Mon,Wed,Fri 10:00"` - Monday, Wednesday, Friday at 10 AM
- `"*-*-* 02:00:00"` - Every day at 2 AM

## Troubleshooting

**Redirect URI Issues**: If Spotify rejects `http://127.0.0.1:8888/callback`, you can:
1. Try using a public redirect URI like `http://example.com` (then just copy the code from your browser's address bar after being redirected)
2. Update the `redirect-uri` variable in the scripts to match whatever URI Spotify accepts
