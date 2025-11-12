# Spotify Liked Songs Downloader

A Babashka script to fetch and download your liked songs from Spotify as MP3 or FLAC files. Automatically syncs your entire Spotify liked songs library to organized local files with configurable quality.

This project uses a modified version of [spotify-dl](https://github.com/GuillemCastro/spotify-dl) for downloading tracks.

## Features

- 🎵 **Album-based downloading**: Downloads entire albums when you like a song
- 📁 **Organized structure**: `<downloadPath>/<artist>/<album [year]>/<track>.mp3`
- 🔄 **Incremental sync**: Only downloads new albums on subsequent runs
- 🎯 **ID-based matching**: Perfect file matching using Spotify track IDs
- 🎧 **Flexible formats**: Choose MP3 (smaller) or FLAC (lossless) quality
- ⚡ **Smart fetching**: By default only checks 50 most recent likes for efficiency
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
export SPOTIFY_DOWNLOAD_FORMAT="mp3"   # or "flac" (optional, defaults to mp3)
export SPOTIFY_FETCH_ALL="false"       # or "true" to fetch all songs (optional, defaults to false)
```

Or create a `.env` file:
```bash
SPOTIFY_CLIENT_ID=your_client_id
SPOTIFY_CLIENT_SECRET=your_client_secret
SPOTIFY_REFRESH_TOKEN=your_refresh_token
SPOTIFY_DOWNLOAD_FORMAT=mp3   # or "flac" (optional, defaults to mp3)
SPOTIFY_FETCH_ALL=false        # or "true" to fetch all songs (optional, defaults to false)
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
7. Organize downloaded files into `./<artist>/<album [year]>/<track>.mp3`
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

Downloaded songs are organized into a clean directory structure within your configured `downloadPath`:

```
<downloadPath>/
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

For example, with `downloadPath = "/var/lib/spotify-sync"`, tracks will be at:
- `/var/lib/spotify-sync/Artist Name/Album Name [2024]/Track 01.mp3`

Album directories include the release year in square brackets for easy sorting and identification. Files will have `.mp3` or `.flac` extension depending on your configured format.

The `downloaded.edn` file tracks which **albums** have been successfully downloaded by their Spotify URI. This allows the script to:
- Skip already-downloaded albums on subsequent runs
- Only download new albums when you like new songs
- Automatically download complete albums (not just individual tracks)
- Resume if interrupted

To force re-download all albums, simply delete `downloaded.edn`.

## Notes

- **Smart fetching**: By default, the script only fetches the 50 most recently added liked songs (configurable with `fetchAll` option or `SPOTIFY_FETCH_ALL` environment variable)
  - Spotify's API returns tracks in reverse chronological order (newest first)
  - This makes subsequent syncs much faster if you don't add many songs between runs
  - For initial sync or if you have many new likes, set `fetchAll = true`
- **Album-based downloading**: When you like a song, the entire album is downloaded
  - If you like a second song from the same album, it won't be re-downloaded
  - You'll get the complete album even if you only liked one song
- **ID-based file matching**: Files are named by their Spotify track ID during download, then renamed to proper track names when organized
  - Ensures perfect matching regardless of special characters, multiple artists, or featured artists
  - Example: `4cOdK2wGLETKBW3PvgPWqT.mp3` → `Artist/Album [2024]/Track Name.mp3`
- Requires the `user-library-read` and `streaming` scopes
  - `user-library-read`: Access your liked songs
  - `streaming`: Enables token to be used with spotify-dl and librespot
- The refresh token doesn't expire unless you revoke it
- Invalid filesystem characters in artist/album/track names are replaced with underscores

## Quick Start (NixOS)

### Prerequisites

Before using on NixOS, you need to obtain your Spotify credentials and refresh token.

#### 1. Create a Spotify App

1. Go to [Spotify Developer Dashboard](https://developer.spotify.com/dashboard)
2. Log in and click "Create app"
3. Fill in the details:
   - App name: "Liked Songs Fetcher" (or any name)
   - Redirect URI: `http://127.0.0.1:8888/callback`
   - Check **Web API**
4. Note your **Client ID** and **Client Secret**

#### 2. Get a Refresh Token

You need to run the helper script once to get a refresh token:

```bash
# Clone the repo temporarily or use nix-shell
git clone https://github.com/winterscar/spotify-sync.git
cd spotify-sync
nix-shell  # or: nix develop

# Set your credentials
export SPOTIFY_CLIENT_ID="your_client_id"
export SPOTIFY_CLIENT_SECRET="your_client_secret"

# Run the helper script
./get_refresh_token.clj

# Follow the instructions:
# 1. Open the URL it displays in your browser
# 2. Authorize the app
# 3. Copy the code from the redirect URL
# 4. Run: ./get_refresh_token.clj YOUR_CODE_HERE
# 5. Save the refresh token it displays
```

### Integration Methods

Choose the method that matches your NixOS setup:

#### Method 1: Flakes-based Configuration

If your system uses flakes, create or edit your `flake.nix`:

```nix
{
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    spotify-sync = {
      url = "github:winterscar/spotify-sync?submodules=1";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs = { self, nixpkgs, spotify-sync }: {
    nixosConfigurations.yourhostname = nixpkgs.lib.nixosSystem {
      system = "x86_64-linux";
      modules = [
        ./configuration.nix
        spotify-sync.nixosModules.default
      ];
    };
  };
}
```

Then add to your `configuration.nix`:

```nix
{ config, pkgs, ... }:

let
  spotifySecrets = {
    clientId = "your_client_id";
    clientSecret = "your_client_secret";
    refreshToken = "your_refresh_token";
  };
in
{
  services.spotify-sync = {
    enable = true;
    clientId = spotifySecrets.clientId;
    clientSecret = spotifySecrets.clientSecret;
    refreshToken = spotifySecrets.refreshToken;
    downloadPath = "/var/lib/spotify-sync";
    schedule = "daily";
    format = "mp3";

    # Optional: If using a path in /home/, set user/group
    # downloadPath = "/home/youruser/music";
    # user = "youruser";
    # group = "users";
  };
}
```

#### Method 2: Traditional Configuration (without flakes)

If you don't use flakes, you can import the module directly in your `configuration.nix`.

**Option A: Add to your existing configuration.nix**

If you already have a `let ... in` block, just add these definitions to it:

```nix
{ config, pkgs, ... }:

let
  # Your existing let bindings...

  # Add these:
  spotify-sync = builtins.fetchGit {
    url = "https://github.com/winterscar/spotify-sync.git";
    ref = "main";  # or a specific tag/commit
    submodules = true;  # Important: fetch git submodules
  };

  spotifySecrets = {
    clientId = "your_client_id";
    clientSecret = "your_client_secret";
    refreshToken = "your_refresh_token";
  };
in
{
  imports = [
    # Your other imports...
    "${spotify-sync}/nixos-module.nix"
  ];

  # Your other configuration...

  services.spotify-sync = {
    enable = true;
    clientId = spotifySecrets.clientId;
    clientSecret = spotifySecrets.clientSecret;
    refreshToken = spotifySecrets.refreshToken;
    downloadPath = "/var/lib/spotify-sync";
    schedule = "daily";
    format = "mp3";
  };
}
```

**Option B: If you don't have a let block**

Wrap your entire configuration:

```nix
{ config, pkgs, ... }:

let
  spotify-sync = builtins.fetchGit {
    url = "https://github.com/winterscar/spotify-sync.git";
    ref = "main";
    submodules = true;  # Important: fetch git submodules
  };

  spotifySecrets = {
    clientId = "your_client_id";
    clientSecret = "your_client_secret";
    refreshToken = "your_refresh_token";
  };
in
{
  imports = [
    # Your existing imports...
    "${spotify-sync}/nixos-module.nix"
  ];

  # All your existing configuration options go here...
  # boot.loader...
  # networking...
  # users...
  # etc.

  services.spotify-sync = {
    enable = true;
    clientId = spotifySecrets.clientId;
    clientSecret = spotifySecrets.clientSecret;
    refreshToken = spotifySecrets.refreshToken;
    downloadPath = "/var/lib/spotify-sync";
    schedule = "daily";
    format = "mp3";
  };
}
```

#### Method 3: Local Clone

If you've cloned the repo locally:

```nix
{ config, pkgs, ... }:

let
  spotifySecrets = {
    clientId = "your_client_id";
    clientSecret = "your_client_secret";
    refreshToken = "your_refresh_token";
  };
in
{
  imports = [
    # Your other imports...
    /path/to/spotify-sync/nixos-module.nix
  ];

  services.spotify-sync = {
    enable = true;
    clientId = spotifySecrets.clientId;
    clientSecret = spotifySecrets.clientSecret;
    refreshToken = spotifySecrets.refreshToken;
    downloadPath = "/var/lib/spotify-sync";
    schedule = "daily";
    format = "mp3";
  };
}
```

### Secret Management

**⚠️ WARNING**: The examples above store secrets directly in your Nix configuration, which ends up in the world-readable Nix store. This is convenient for personal use but not secure for production.

For better security, consider:

**Option 1: Separate restricted file**

```nix
# secrets.nix (chmod 600 secrets.nix)
{
  spotifyClientSecret = "your_secret_here";
  spotifyRefreshToken = "your_token_here";
}

# configuration.nix
let
  secrets = import ./secrets.nix;
in {
  services.spotify-sync = {
    clientId = "your_client_id_here";  # Not sensitive
    clientSecret = secrets.spotifyClientSecret;
    refreshToken = secrets.spotifyRefreshToken;
  };
}
```

**Option 2: Use agenix or sops-nix**

If you need proper secrets management, consider modifying the module to support file-based secrets with systemd's `LoadCredential`.

### Deploy

After configuring, rebuild your system:

```bash
sudo nixos-rebuild switch
```

Check that the service and timer are running:

```bash
systemctl status spotify-sync.timer
systemctl list-timers spotify-sync.timer
```

### Service Commands

Once deployed, you can manage the service with these commands:

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

All available options for `services.spotify-sync`:

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `enable` | boolean | `false` | Enable the spotify-sync service |
| `clientId` | string | - | Spotify application client ID (required) |
| `clientSecret` | string | - | Spotify client secret (required, stored in Nix store) |
| `refreshToken` | string | - | Spotify refresh token (required, stored in Nix store) |
| `downloadPath` | path | `/var/lib/spotify-sync` | Directory where songs will be downloaded |
| `schedule` | string | `"daily"` | When to run sync (systemd timer format) |
| `format` | enum | `"mp3"` | Audio format: `"mp3"` or `"flac"` |
| `fetchAll` | boolean | `false` | Fetch all liked songs (true) or just most recent 50 (false) |
| `user` | string | `"spotify-sync"` | User account for the service (set to your username for `/home` paths) |
| `group` | string | `"spotify-sync"` | Group for the service (set to your group for `/home` paths) |

**Format Options**

- `"mp3"` (default) - Smaller file size (~5-10 MB per track), lossy compression, widely compatible
- `"flac"` - Larger file size (~30-50 MB per track), lossless quality, best audio fidelity

**Fetch Mode**

The `fetchAll` option controls how many liked songs are fetched from Spotify:

- `false` (default) - Only fetches the 50 most recently added liked songs
  - ✅ Much faster API calls
  - ✅ Ideal if you don't add many songs between syncs
  - ✅ Spotify returns tracks in reverse chronological order (newest first)
  - ⚠️ On first run, only syncs your 50 most recent likes

- `true` - Fetches your entire liked songs library
  - ✅ Ensures all historical songs are synced
  - ✅ Use for initial sync or if you have a large backlog
  - ⚠️ Slower due to pagination through entire library

**Recommendation**: Use `fetchAll = false` (default) for regular scheduled syncs after doing an initial full sync with `fetchAll = true`.

**Schedule Examples** (systemd calendar format)

- `"hourly"` - Every hour on the hour
- `"daily"` - Every day at midnight
- `"weekly"` - Every Monday at midnight
- `"*:0/15"` - Every 15 minutes
- `"Mon,Wed,Fri 10:00"` - Monday, Wednesday, Friday at 10 AM
- `"*-*-* 02:00:00"` - Every day at 2 AM

## Troubleshooting

**NixOS Build Errors**

**Error: librespot-metadata failed to build**

If you see errors like `error: 1 dependencies of derivation '/nix/store/...-librespot-metadata-0.8.0.drv' failed to build`:

This happens when git submodules aren't fetched. Make sure you have `submodules = true` in your `builtins.fetchGit` call:

```nix
spotify-sync = builtins.fetchGit {
  url = "https://github.com/winterscar/spotify-sync.git";
  ref = "main";
  submodules = true;  # This is required!
};
```

**Error: Could not find openssl via pkg-config**

If you see errors about OpenSSL not being found, the repository might have an outdated version of the module. Pull the latest changes which include OpenSSL as a build dependency.

**Error: The system library `alsa` required by crate `alsa-sys` was not found**

ALSA (Advanced Linux Sound Architecture) is required on Linux for audio support. The latest version of the module includes `alsa-lib` as a dependency. Make sure you've pulled the latest commits or refresh your git fetch:

```bash
sudo nixos-rebuild switch --refresh
```

If you're using a local clone, make sure you've pulled the latest commits that include both the OpenSSL and ALSA fixes.

**Error: Cannot run program ".../spotify-dl/target/debug/spotify-dl": No such file or directory**

This error occurs when the script tries to use a hardcoded development path instead of the Nix-built binary. The latest version sets `SPOTIFY_DL_BIN` explicitly in the wrapper script. Make sure you've pulled the latest changes and refresh your build:

```bash
sudo nixos-rebuild switch --refresh
```

**Error: Failed at step CHDIR spawning ... Permission denied**

This happens when the service can't access the `downloadPath` directory. Common causes:

1. **Using a path in `/home/` with the default user**: The `spotify-sync` user doesn't have permission to access your home directory. **Solution**: Set the service to run as your user:

   ```nix
   services.spotify-sync = {
     # ... other settings ...
     downloadPath = "/home/admin/media/music";
     user = "admin";           # Your username
     group = "users";          # Your primary group (run: groups admin)
   };
   ```

2. **ProtectHome is enabled**: The module automatically disables `ProtectHome` for paths in `/home/`, but make sure you've pulled the latest changes.

3. **Directory doesn't exist**: The directory will be created automatically, but parent directories must exist and be accessible by the configured user.

**Error: Exactly one of users.users.X.isSystemUser and users.users.X.isNormalUser must be set**

This means you have a user defined in your NixOS configuration without the required `isNormalUser` or `isSystemUser` attribute. Make sure your existing user definition has one of these set:

```nix
users.users.admin = {
  isNormalUser = true;  # Required
  extraGroups = [ "wheel" "networkmanager" ];
  # ... other settings ...
};
```

Note: The module will NOT create or modify your user if you specify it - it only creates the `spotify-sync` system user when using the defaults.

**Redirect URI Issues**

If Spotify rejects `http://127.0.0.1:8888/callback`, you can:
1. Try using a public redirect URI like `http://example.com` (then just copy the code from your browser's address bar after being redirected)
2. Update the `redirect-uri` variable in the scripts to match whatever URI Spotify accepts
