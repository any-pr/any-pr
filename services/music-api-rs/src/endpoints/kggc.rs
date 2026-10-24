//! Port of `music-api/kggc.php`: Kuwo lyric fetch -> LRC, with a 1h file cache.

use std::collections::HashMap;
use std::time::Duration;

use axum::extract::Query;
use axum::response::Response;
use serde_json::{json, Value};

use crate::util;

const CACHE_DIR: &str = "./cache/";
const CACHE_TTL: Duration = Duration::from_secs(3600);

pub async fn get(Query(params): Query<HashMap<String, String>>) -> Response {
    let hash = params.get("hash").cloned().unwrap_or_default();
    util::json_response_compact(&get_lyric(&hash).await)
}

async fn get_lyric(hash: &str) -> Value {
    let cache_file = format!("{CACHE_DIR}{:x}.json", md5::compute(hash.as_bytes()));

    if let Ok(meta) = tokio::fs::metadata(&cache_file).await {
        let fresh = meta
            .modified()
            .ok()
            .and_then(|m| m.elapsed().ok())
            .map(|age| age < CACHE_TTL)
            .unwrap_or(false);
        if fresh {
            if let Ok(text) = tokio::fs::read_to_string(&cache_file).await {
                if let Ok(v) = serde_json::from_str(&text) {
                    return v;
                }
            }
        }
    }

    let result = fetch_from_kuwo(hash).await;

    if result["msg"] == "成功" {
        let _ = tokio::fs::create_dir_all(CACHE_DIR).await;
        let _ = tokio::fs::write(&cache_file, util::compact_json(&result)).await;
    }

    result
}

async fn fetch_from_kuwo(hash: &str) -> Value {
    if hash.is_empty() {
        return json!({"msg": "失败", "gc": "", "error": "歌曲ID不能为空"});
    }

    let url = format!(
        "https://www.kuwo.cn/openapi/v1/www/lyric/getlyric?musicId={}",
        util::urlencode(hash)
    );

    let resp = util::http_client(10, 10)
        .get(&url)
        .header(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        )
        .header("Referer", "https://www.kuwo.cn/")
        .header("Accept", "application/json")
        .send()
        .await;

    let Ok(resp) = resp else {
        return json!({"msg": "失败", "gc": "", "error": "网络请求失败"});
    };

    let Ok(text) = resp.text().await else {
        return json!({"msg": "失败", "gc": "", "error": "网络请求失败"});
    };
    let Ok(data) = serde_json::from_str::<Value>(&text) else {
        return json!({"msg": "失败", "gc": "", "error": "未找到歌词数据"});
    };

    if util::v_i64(&data["code"]) != 200 {
        return json!({"msg": "失败", "gc": "", "error": "未找到歌词数据"});
    }
    let Some(lrclist) = data["data"]["lrclist"].as_array() else {
        return json!({"msg": "失败", "gc": "", "error": "未找到歌词数据"});
    };
    if lrclist.is_empty() {
        return json!({"msg": "失败", "gc": "", "error": "未找到歌词数据"});
    }

    convert_to_old_format(lrclist)
}

fn convert_to_old_format(lrclist: &[Value]) -> Value {
    let mut lrc = String::from(
        "[ti:]\r\n[ar:]\r\n[al:]\r\n[by:飞鸟·轻音v4.0.0]\r\n[offset:0]\r\n[00:00.000]飞鸟·轻音v4.0.0\r\n",
    );

    for line in lrclist {
        // Kuwo returns `time` as a numeric string; accept numbers too.
        let time = match &line["time"] {
            Value::String(s) => s.trim().parse::<f64>().unwrap_or(0.0),
            other => util::v_f64(other),
        };
        let lyric = line["lineLyric"].as_str().unwrap_or("").trim();

        if !lyric.is_empty() && time >= 0.0 {
            let minutes = (time / 60.0).floor() as i64;
            let seconds = time % 60.0;
            // PHP sprintf("[%02d:%06.3f]", minutes, seconds)
            lrc.push_str(&format!("[{minutes:02}:{seconds:06.3}]{lyric}\r\n"));
        }
    }

    json!({"msg": "成功", "gc": lrc})
}
