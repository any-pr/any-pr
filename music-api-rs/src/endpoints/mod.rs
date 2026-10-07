//! HTTP handlers, one module per former PHP file. `router()` exposes them
//! under the original `.php` paths so the binary is a drop-in replacement.

pub mod kggc;

use axum::routing::get;
use axum::Router;

pub fn router() -> Router {
    Router::new().route("/kggc.php", get(kggc::get))
}
