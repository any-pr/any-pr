//! Helpers shared by the endpoint modules. Where the PHP code relied on
//! loose semantics (intval, ??, urlencode) the functions here mirror them.

use std::collections::HashMap;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use axum::http::header;
use axum::response::{IntoResponse, Response};
use serde_json::Value;

/// PHP `intval($s)`: optional sign + leading digits, anything else is 0.
pub fn intval(s: &str) -> i64 {
    let t = s.trim_start();
    let (sign, t) = if let Some(r) = t.strip_prefix('-') {
        (-1i64, r)
    } else {
        (1i64, t.strip_prefix('+').unwrap_or(t))
    };
    let digits: String = t.chars().take_while(|c| c.is_ascii_digit()).collect();
    sign * digits.parse::<i64>().unwrap_or(0)
}

/// Query param as PHP intval, with a default when the param is absent.
pub fn param_i64(params: &HashMap<String, String>, key: &str, default: i64) -> i64 {
    params.get(key).map_or(default, |v| intval(v))
}

/// PHP `time()`.
pub fn now() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs()
}

/// PHP's mt_rand-based v4 UUID (generateReqId) -> uuid crate.
pub fn req_id() -> String {
    uuid::Uuid::new_v4().to_string()
}

/// PHP `urlencode` (RFC 1738): space -> `+`, unreserved `[A-Za-z0-9-_.]`,
/// everything else -> `%XX` uppercase.
pub fn urlencode(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    for &b in s.as_bytes() {
        match b {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' => {
                out.push(b as char)
            }
            b' ' => out.push('+'),
            _ => out.push_str(&format!("%{b:02X}")),
        }
    }
    out
}

/// PHP `http_build_query` over an ordered pair list.
pub fn build_query(pairs: &[(&str, String)]) -> String {
    pairs
        .iter()
        .map(|(k, v)| format!("{}={}", urlencode(k), urlencode(v)))
        .collect::<Vec<_>>()
        .join("&")
}

/// PHP `$v ?? $default` for JSON values (null/absent -> default).
pub fn or<'a>(v: &'a Value, default: &'a Value) -> &'a Value {
    if v.is_null() {
        default
    } else {
        v
    }
}

/// JSON value to i64 with PHP-style coercion of numeric strings.
pub fn v_i64(v: &Value) -> i64 {
    match v {
        Value::Number(n) => n
            .as_i64()
            .or_else(|| n.as_f64().map(|f| f as i64))
            .unwrap_or(0),
        Value::String(s) => intval(s),
        Value::Bool(b) => *b as i64,
        _ => 0,
    }
}

/// JSON value to f64 with PHP-style coercion of numeric strings.
pub fn v_f64(v: &Value) -> f64 {
    match v {
        Value::Number(n) => n.as_f64().unwrap_or(0.0),
        Value::String(s) => s.trim().parse().unwrap_or(0.0),
        Value::Bool(b) => *b as i64 as f64,
        _ => 0.0,
    }
}

/// All the PHP scripts disabled TLS verification; keep that behaviour.
pub fn http_client(connect_timeout_secs: u64, timeout_secs: u64) -> reqwest::Client {
    reqwest::Client::builder()
        .danger_accept_invalid_certs(true)
        .connect_timeout(Duration::from_secs(connect_timeout_secs))
        .timeout(Duration::from_secs(timeout_secs))
        .build()
        .expect("failed to build reqwest client")
}

/// PHP `json_encode(..., JSON_PRETTY_PRINT|UNESCAPED_UNICODE|UNESCAPED_SLASHES)`.
pub fn pretty_json(v: &Value) -> String {
    serde_json::to_string_pretty(v).unwrap_or_default()
}

/// PHP `json_encode(..., JSON_UNESCAPED_UNICODE)` (compact).
pub fn compact_json(v: &Value) -> String {
    serde_json::to_string(v).unwrap_or_default()
}

pub fn json_response(v: &Value) -> Response {
    (
        [(header::CONTENT_TYPE, "application/json; charset=utf-8")],
        pretty_json(v),
    )
        .into_response()
}

pub fn json_response_compact(v: &Value) -> Response {
    (
        [(header::CONTENT_TYPE, "application/json; charset=utf-8")],
        compact_json(v),
    )
        .into_response()
}
