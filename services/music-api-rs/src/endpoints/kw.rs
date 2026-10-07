//! Port of `music-api/kw.php`: resolve Kuwo play URLs at several quality
//! levels. The PHP version fanned out with curl_multi; here the five
//! quality requests run concurrently via join_all, as do the HEAD requests
//! used to report file sizes. Upstream calls live in [`crate::kuwo`].

use std::collections::HashMap;

use axum::extract::{Form, Query};
use axum::response::Response;
use serde_json::{json, Value};

use crate::{kuwo, util};

struct Quality {
    key: &'static str,
    ts: &'static str,
    br: &'static str,
    ctype: &'static str,
    fallback_br: Option<&'static str>,
    fallback_type: Option<&'static str>,
}

// Same order as $qualityConfig in kw.php; output preserves it.
const QUALITIES: &[Quality] = &[
    Quality {
        key: "4000kflac",
        ts: "His无损音质",
        br: "4000kflac",
        ctype: "flac",
        fallback_br: None,
        fallback_type: None,
    },
    Quality {
        key: "2000kflac",
        ts: "SQ超品音质",
        br: "2000",
        ctype: "flac",
        fallback_br: None,
        fallback_type: None,
    },
    Quality {
        key: "320kmp3",
        ts: "HQ高品音质",
        br: "320kmp3",
        ctype: "mp3",
        fallback_br: Some("128kmp3"),
        fallback_type: Some("mp3"),
    },
    Quality {
        key: "300ogg",
        ts: "MQ普通音质",
        br: "300",
        ctype: "ogg",
        fallback_br: Some("100kogg"),
        fallback_type: Some("ogg"),
    },
    Quality {
        key: "48km4a",
        ts: "LQ标准音质",
        br: "48",
        ctype: "m4a",
        fallback_br: None,
        fallback_type: None,
    },
];

pub async fn get(Query(params): Query<HashMap<String, String>>) -> Response {
    run(params).await
}

pub async fn post(Form(params): Form<HashMap<String, String>>) -> Response {
    run(params).await
}

async fn run(params: HashMap<String, String>) -> Response {
    let empty = json!({"data": [], "gc": "", "pic": "", "size": 0});

    let id_raw = params.get("id").cloned().unwrap_or_default();
    if id_raw.is_empty() {
        return util::json_response(&empty);
    }
    let id = id_raw.replace("HJKW-", "");

    let Some(id) = extract_id(&id) else {
        return util::json_response(&json!({
            "data": [],
            "gc": "无效的歌曲ID或链接",
            "pic": "",
            "size": 0,
        }));
    };

    let info: Value = kuwo::curl(&kuwo::item_info_url(&id))
        .await
        .and_then(|t| serde_json::from_str(&t).ok())
        .unwrap_or_default();

    if util::v_i64(&info["code"]) != 200 {
        return util::json_response(&empty);
    }

    let name = info["data"]["songName"].as_str().unwrap_or("");
    let artist = info["data"]["artistName"].as_str().unwrap_or("");
    let pic = info["data"]["pic"].as_str().unwrap_or("");

    // curl_multi: request every quality level concurrently.
    let results =
        futures::future::join_all(QUALITIES.iter().map(|q| kuwo::fetch_play(q.br, &id))).await;

    // (final_url, ts, type) for qualities that resolved.
    let mut candidates: Vec<(String, &'static str, &'static str)> = Vec::new();

    for (q, result) in QUALITIES.iter().zip(results) {
        let requested_br = extract_br_number(q.br);

        let Some(r) = &result else { continue };
        if r["data"]["url"].is_null() || r["data"]["bitrate"].is_null() {
            continue;
        }

        if util::v_f64(&r["data"]["bitrate"]) == requested_br as f64 {
            if let Some(base) = base_url(r["data"]["url"].as_str().unwrap_or("")) {
                candidates.push((
                    format!("{base}?site=music.hjfggzs.top&qq=2581727235&signer=Sakura."),
                    q.ts,
                    q.ctype,
                ));
            }
        // PHP nests the fallback inside isset(url, bitrate): it only runs
        // when a bitrate came back but didn't match the requested one.
        } else if (q.key == "320kmp3" || q.key == "300ogg") && q.fallback_br.is_some() {
            let fbr = q.fallback_br.unwrap();
            let ftype = q.fallback_type.unwrap_or(q.ctype);
            let fb: Option<Value> = kuwo::curl(&kuwo::play_url(fbr, &id))
                .await
                .and_then(|t| serde_json::from_str(&t).ok());
            if let Some(fb) = fb {
                if !fb["data"]["url"].is_null() && !fb["data"]["bitrate"].is_null() {
                    let fb_requested = extract_br_number(fbr);
                    if util::v_f64(&fb["data"]["bitrate"]) == fb_requested as f64 {
                        if let Some(base) =
                            base_url(fb["data"]["url"].as_str().unwrap_or(""))
                        {
                            candidates.push((
                                format!("{base}?site=music.hjfggzs.top&qq=2581727235&signer=Sakura"),
                                q.ts,
                                ftype,
                            ));
                        }
                    }
                }
            }
        }
    }

    // HEAD requests for file sizes, concurrently (PHP did them serially).
    let sizes = futures::future::join_all(
        candidates.iter().map(|(u, _, _)| kuwo::remote_file_size(u)),
    )
    .await;

    let data: Vec<Value> = candidates
        .iter()
        .zip(sizes)
        .map(|((u, ts, ty), size)| {
            json!({
                "url": u,
                "ts": ts,
                "size": format_size(size),
                "type": ty,
            })
        })
        .collect();

    if !data.is_empty() {
        util::json_response(&json!({
            "data": data,
            "gc": format!("正在播放: {name}\n 歌手: {artist}"),
            "pic": pic,
        }))
    } else {
        util::json_response(&json!({
            "data": [],
            "gc": "获取播放链接失败，请重试或更换歌曲",
            "pic": pic,
            "size": 0,
        }))
    }
}

/// First regex tries `play_detail/(\d+)`, then any `\d+` run.
fn extract_id(input: &str) -> Option<String> {
    if let Some(pos) = input.find("play_detail/") {
        let digits: String = input[pos + "play_detail/".len()..]
            .chars()
            .take_while(|c| c.is_ascii_digit())
            .collect();
        if !digits.is_empty() {
            return Some(digits);
        }
    }
    let start = input.find(|c: char| c.is_ascii_digit())?;
    Some(
        input[start..]
            .chars()
            .take_while(|c| c.is_ascii_digit())
            .collect(),
    )
}

/// extractBrNumber(): leading digit run of the br string.
fn extract_br_number(br: &str) -> i64 {
    br.chars()
        .take_while(|c| c.is_ascii_digit())
        .collect::<String>()
        .parse()
        .unwrap_or(0)
}

/// PHP: $parsed['scheme'] . '://' . $parsed['host'] . $parsed['path']
/// (query/fragment/port are dropped, same as parse_url + manual rebuild).
fn base_url(raw: &str) -> Option<String> {
    let parsed = url::Url::parse(raw).ok()?;
    Some(format!(
        "{}://{}{}",
        parsed.scheme(),
        parsed.host_str()?,
        parsed.path()
    ))
}

fn format_size(bytes: i64) -> String {
    if bytes <= 0 {
        return "0MB".to_string();
    }
    format!("{:.2}MB", bytes as f64 / 1024.0 / 1024.0)
}
