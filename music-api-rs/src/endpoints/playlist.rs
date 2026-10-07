//! Port of `music-api/playlist.php`: Kuwo playlist detail.

use std::collections::HashMap;

use axum::extract::Query;
use axum::response::Response;
use serde_json::{json, Value};

use crate::{kuwo, util};

pub async fn get(Query(params): Query<HashMap<String, String>>) -> Response {
    let pid = util::param_i64(&params, "pid", 0);
    let pn = util::param_i64(&params, "pn", 1);
    let rn = util::param_i64(&params, "rn", 20);

    if pid <= 0 {
        return util::json_response(&json!({
            "code": 400,
            "msg": "请提供有效的歌单ID (pid)",
            "data": null,
        }));
    }

    let (data, info) = match fetch_playlist_data(pid, pn, rn).await {
        Ok(v) => v,
        Err(e) => {
            return util::json_response(&json!({
                "code": 500,
                "msg": format!("获取歌单数据失败: {e}"),
                "data": null,
            }));
        }
    };

    let mut music_list = Vec::new();
    let current_index = (pn - 1) * rn;

    if let Some(items) = data["musicList"].as_array() {
        for (index, item) in items.iter().enumerate() {
            // PHP str_replace of the literal (and double-backslashed)
            // unicode escape that sometimes shows up in artist names.
            let artist = match util::or(&item["artist"], &json!("未知艺术家")) {
                Value::String(s) => s.clone(),
                other => other.to_string(),
            };
            let artist = artist.replace("\\\\u0026", "&").replace("\\u0026", "&");

            music_list.push(json!({
                "name": util::or(&item["name"], &json!("未知歌曲")),
                "pic": util::or(&item["pic"], util::or(&item["pic120"], &json!(""))),
                "artist": artist,
                "rid": util::or(&item["rid"], &json!(0)),
                "album": util::or(&item["album"], &json!("未知专辑")),
                "index": index + 1,
                "global_index": current_index + index as i64 + 1,
                "source_page": pn,
                "duration": util::or(&item["duration"], &json!(0)),
                "songTimeMinutes": util::or(&item["songTimeMinutes"], &json!("00:00")),
                "hasLossless": util::or(&item["hasLossless"], &json!(false)),
            }));
        }
    }

    let total = &info["total"];
    let total_pages = (util::v_f64(total) / rn.max(1) as f64).ceil();

    let body = json!({
        "code": 200,
        "msg": "获取成功",
        "data": music_list,
        "pagination": {
            "current_page": pn,
            "page_size": rn,
            "current_page_count": data["musicList"].as_array().map_or(0, |a| a.len()),
            "total": total,
            "total_pages": total_pages,
        },
        "playlist_info": {
            "pid": pid,
            "img": info["img"],
            "userName": info["userName"],
            "uPic": info["uPic"],
            "name": info["name"],
            "total_songs": info["total"],
            "listencnt": info["listencnt"],
            "tag": info["tag"],
        },
        "timestamp": chrono::Local::now().format("%Y-%m-%d %H:%M:%S").to_string(),
    });

    util::json_response(&body)
}

/// Returns `(data, playlistInfo)` on success, error message on failure.
async fn fetch_playlist_data(
    pid: i64,
    pn: i64,
    rn: i64,
) -> Result<(Value, Value), String> {
    let query = util::build_query(&[
        ("pid", pid.to_string()),
        ("pn", pn.to_string()),
        ("rn", rn.to_string()),
        ("httpsStatus", "1".into()),
        ("reqId", util::req_id()),
        ("plat", "web_www".into()),
        ("from", String::new()),
    ]);
    let api_url = format!("https://www.kuwo.cn/api/www/playlist/playListInfo?{query}");

    // Fixed kw_token; the csrf header must carry the same value.
    let kw_token = "abcdef1234567890abcdef1234567890";

    let resp = util::http_client(10, 30)
        .get(&api_url)
        .headers(kuwo::www_headers(
            &kuwo::playlist_cookie(kw_token),
            &format!("https://www.kuwo.cn/playlist_detail/{pid}"),
            "1708fbda7f632a61eb5fc5c20dd9c118668785be4d9ed14958b42ad4e3e51dcb0482a225",
            Some(kw_token),
        ))
        .send()
        .await
        .map_err(|e| e.to_string())?;

    let status = resp.status().as_u16();
    let text = resp.text().await.map_err(|e| e.to_string())?;

    if status != 200 {
        return Err(format!("API请求失败，HTTP状态码: {status}"));
    }

    let decoded: Value =
        serde_json::from_str(&text).map_err(|_| "API响应解析失败".to_string())?;

    if decoded["msg"].as_str() != Some("success") {
        return Err(format!(
            "API返回错误: {}",
            decoded["msg"].as_str().unwrap_or("未知错误")
        ));
    }

    if !decoded["data"]["musicList"].is_array() {
        return Err("歌单数据为空".to_string());
    }

    let d = &decoded["data"];
    let info = json!({
        "img": util::or(&d["img700"], &json!("")),
        "uPic": util::or(&d["uPic"], &json!("")),
        "userName": util::or(&d["userName"], &json!("")),
        "name": util::or(&d["name"], &json!("")),
        "total": util::or(&d["total"], &json!(0)),
        "listencnt": util::or(&d["listencnt"], &json!(0)),
        "tag": util::or(&d["tag"], &json!("")),
    });

    Ok((d.clone(), info))
}
