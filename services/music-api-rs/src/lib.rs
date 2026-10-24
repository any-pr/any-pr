//! Rust rewrite of the former `music-api/` PHP endpoints (Kuwo music API
//! proxy). `endpoints::router()` builds the axum router; `kuwo` holds the
//! upstream API client details; `util` mirrors the PHP loose semantics the
//! endpoints relied on (intval, urlencode, `??`, ...).

pub mod endpoints;
pub mod kuwo;
pub mod util;
