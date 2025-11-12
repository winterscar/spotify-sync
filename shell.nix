{ pkgs ? import <nixpkgs> {} }:

pkgs.mkShell {
  buildInputs = with pkgs; [
    # Babashka for running Clojure scripts
    babashka

    # Rust toolchain for building spotify-dl
    rustc
    cargo
    pkg-config

    # For getting refresh tokens
    curl
    jq
  ];

  shellHook = ''
    echo "Spotify Sync Development Environment"
    echo "====================================="
    echo ""
    echo "Available commands:"
    echo "  ./get_refresh_token.clj     - Get Spotify refresh token"
    echo "  ./fetch_liked_songs.clj     - Sync liked songs"
    echo "  cd spotify-dl && cargo build - Build spotify-dl"
    echo ""
    echo "Required environment variables:"
    echo "  SPOTIFY_CLIENT_ID"
    echo "  SPOTIFY_CLIENT_SECRET"
    echo "  SPOTIFY_REFRESH_TOKEN"
    echo ""
    echo "Optional environment variables:"
    echo "  SPOTIFY_DOWNLOAD_FORMAT    - mp3 (default) or flac"
    echo "  SPOTIFY_FETCH_ALL          - false (default, fetch recent 50) or true (fetch all)"
    echo ""
  '';
}
