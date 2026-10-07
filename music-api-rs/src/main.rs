//! Binary entry point: serve the music-api-rs endpoints.
//!
//! Routes keep the original `.php` paths so this can drop in behind the
//! same reverse-proxy rules the PHP files used to serve.

use tower_http::cors::CorsLayer;

#[tokio::main]
async fn main() {
    // The PHP scripts emitted `Access-Control-Allow-*` and answered OPTIONS.
    let app = music_api_rs::endpoints::router().layer(CorsLayer::permissive());

    let port: u16 = std::env::var("PORT")
        .ok()
        .and_then(|p| p.parse().ok())
        .unwrap_or(8000);
    let listener = tokio::net::TcpListener::bind(("0.0.0.0", port))
        .await
        .expect("failed to bind listener");
    println!("music-api-rs listening on 0.0.0.0:{port}");
    axum::serve(listener, app).await.expect("server error");
}
