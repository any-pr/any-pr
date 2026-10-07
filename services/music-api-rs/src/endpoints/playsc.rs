//! Port of `music-api/playsc.php`: search Kuwo playlists by keyword,
//! returning the upstream API response verbatim.

use std::collections::HashMap;

use axum::extract::Query;
use axum::response::Response;
use serde_json::{json, Value};

use crate::{kuwo, util};

struct FetchError {
    message: String,
    request_url: Option<String>,
}

impl FetchError {
    fn msg(message: impl Into<String>) -> Self {
        Self {
            message: message.into(),
            request_url: None,
        }
    }
}

pub async fn get(Query(params): Query<HashMap<String, String>>) -> Response {
    let key = params
        .get("key")
        .map(|s| s.trim().to_string())
        .unwrap_or_default();
    let pn = util::param_i64(&params, "pn", 1).max(1);
    let rn = util::param_i64(&params, "rn", 30).max(1);

    if key.is_empty() {
        return util::json_response(&json!({
            "code": 400,
            "msg": "搜索关键词不能为空",
            "data": null,
        }));
    }

    match search_playlist_by_key(&key, pn, rn).await {
        Ok(data) => util::json_response(&data),
        Err(e) => util::json_response(&json!({
            "code": 500,
            "msg": format!("获取搜索列表失败: {}", e.message),
            "data": null,
            "debug": e.request_url,
        })),
    }
}

async fn search_playlist_by_key(key: &str, pn: i64, rn: i64) -> Result<Value, FetchError> {
    let query = util::build_query(&[
        ("key", key.to_string()),
        ("pn", pn.to_string()),
        ("rn", rn.to_string()),
        ("httpsStatus", "1".into()),
        ("reqId", util::req_id()),
        ("plat", "web_www".into()),
        ("from", String::new()),
    ]);
    let full_url =
        format!("https://www.kuwo.cn/api/www/search/searchPlayListBykeyWord?{query}");

    let resp = util::http_client(10, 30)
        .get(&full_url)
        .headers(kuwo::www_headers(
            &kuwo::playsc_cookie(),
            // PHP sent the raw (unencoded) keyword in Referer.
            &format!("https://www.kuwo.cn/search/playlist?key={key}"),
            "460adaf85c643b5b8456f0f03bd0e07a409f8983058eae5f59e14ed8d6df1dc9034a2a36",
            None,
        ))
        .send()
        .await
        .map_err(|e| FetchError::msg(e.to_string()))?;

    let status = resp.status().as_u16();
    let text = resp
        .text()
        .await
        .map_err(|e| FetchError::msg(e.to_string()))?;

    if status != 200 {
        return Err(FetchError {
            message: format!("搜索请求失败，HTTP状态码: {status}"),
            request_url: Some(full_url),
        });
    }

    let decoded: Value = serde_json::from_str(&text)
        .map_err(|_| FetchError::msg("搜索数据解析失败"))?;

    // PHP: isset($r['success']) && $r['success'] === false
    if decoded["success"].as_bool() == Some(false) {
        return Err(FetchError::msg(format!(
            "API返回错误: {}",
            decoded["message"].as_str().unwrap_or("未知错误")
        )));
    }

    if decoded["msg"].as_str() != Some("success") {
        return Err(FetchError::msg(format!(
            "API返回错误: {}",
            decoded["msg"].as_str().unwrap_or("未知错误")
        )));
    }

    Ok(decoded)
}
