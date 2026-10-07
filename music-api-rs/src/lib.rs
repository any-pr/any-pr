//! Rust rewrite of the former `music-api/` PHP endpoints (Kuwo music API
//! proxy). `endpoints::router()` builds the axum router; `util` mirrors
//! the PHP loose semantics the endpoints relied on (intval, urlencode,
//! `??`, ...).

pub mod endpoints;
pub mod util;
