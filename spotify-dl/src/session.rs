use anyhow::Result;
use librespot::core::cache::Cache;
use librespot::core::config::SessionConfig;
use librespot::core::session::Session;
use librespot::discovery::Credentials;

pub async fn create_session(access_token: String) -> Result<Session> {
    let credentials_store = dirs::home_dir().map(|p| p.join(".spotify-dl"));
    let cache = Cache::new(credentials_store, None, None, None)?;

    let session_config = SessionConfig::default();

    tracing::info!("Using provided access token");
    let credentials = Credentials::with_access_token(access_token);

    cache.save_credentials(&credentials);

    let session = Session::new(session_config, Some(cache));
    session.connect(credentials, true).await?;
    Ok(session)
}
