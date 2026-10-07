//! Port of `music-api/kwsc.php`: Kuwo song search.

use std::collections::HashMap;
use std::time::Instant;

use axum::extract::Query;
use axum::http::{header, StatusCode};
use axum::response::{IntoResponse, Response};
use serde_json::{json, Value};

use crate::util;

const RN: i64 = 30;

pub async fn get(Query(params): Query<HashMap<String, String>>) -> Response {
    let key = params
        .get("key")
        .map(|s| s.trim().to_string())
        .unwrap_or_default();
    let mut pn = util::param_i64(&params, "pn", 1);

    if key.is_empty() {
        return (
            StatusCode::BAD_REQUEST,
            [(header::CONTENT_TYPE, "application/json; charset=utf-8")],
            util::pretty_json(&json!({
                "code": 400,
                "message": "缺少必要参数",
                "data": null,
                "tips": "请提供搜索关键词，如：?key=周杰伦&pn=1",
            })),
        )
            .into_response();
    }
    if pn < 1 {
        pn = 1;
    }

    match fetch_search(&key, pn).await {
        Ok((json_body, elapsed_ms)) => {
            let mut search = Vec::new();

            if let Some(abslist) = json_body["abslist"].as_array() {
                for song in abslist {
                    // Album cover preferred, fall back to artist head; the
                    // 120px thumbnail path is rewritten to the 300px one.
                    let album_short = song["web_albumpic_short"].as_str().unwrap_or("");
                    let artist_short = song["web_artistpic_short"].as_str().unwrap_or("");
                    let pic = if !album_short.is_empty() {
                        format!(
                            "https://img4.kuwo.cn/star/albumcover/{}",
                            album_short.replace("120", "300")
                        )
                    } else if !artist_short.is_empty() {
                        format!(
                            "https://img1.kuwo.cn/star/starheads/{}",
                            artist_short.replace("120", "300")
                        )
                    } else {
                        String::new()
                    };

                    search.push(json!({
                        "name": song["SONGNAME"].as_str().unwrap_or(""),
                        "artist": song["ARTIST"].as_str().unwrap_or(""),
                        "rid": util::v_i64(&song["DC_TARGETID"]),
                        "pic": pic,
                        "album": song["ALBUM"].as_str().unwrap_or(""),
                        "duration": util::v_i64(&song["DURATION"]),
                        "pay": util::v_i64(&song["PAY"]),
                    }));
                }
            }

            let total = search.len() as i64;
            let body = json!({
                "code": 0,
                "message": "success",
                "data": {
                    "keyword": key,
                    "page": pn,
                    "pageSize": RN,
                    "total": total,
                    "hasMore": total >= RN,
                    "songs": search,
                },
                "meta": {
                    "timestamp": util::now(),
                    "executionTime": format!("{elapsed_ms:.2}ms"),
                    "source": "酷我音乐",
                    "version": "1.0.0",
                }
            });
            util::json_response(&body)
        }
        Err(e) => (
            StatusCode::INTERNAL_SERVER_ERROR,
            [(header::CONTENT_TYPE, "application/json; charset=utf-8")],
            util::pretty_json(&json!({
                "code": 500,
                "message": "服务器内部错误",
                "data": null,
                "error": {
                    "type": "Exception",
                    "message": e,
                },
                "meta": {
                    "timestamp": util::now(),
                    "tips": "请检查搜索关键词或稍后重试",
                }
            })),
        )
            .into_response(),
    }
}

async fn fetch_search(key: &str, pn: i64) -> Result<(Value, f64), String> {
    let url = format!(
        "http://search.kuwo.cn/r.s?pn={}&rn={RN}&all={}&ft=music&newsearch=1\
         &alflac=1&itemset=web_2013&client=kt&cluster=0&vermerge=1&rformat=json\
         &encoding=utf8&show_copyright_off=1&pcmp4=1&ver=mbox&plat=pc\
         &vipver=MUSIC_9.2.0.0_W6&devid=11404450&newver=1&issubtitle=1&pcjson=1",
        pn - 1,
        util::urlencode(key)
    );

    let start = Instant::now();
    let resp = util::http_client(10, 10)
        .get(&url)
        .header(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 \
             (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36 Edg/115.0.1901.188",
        )
        .header("Accept", "application/json")
        .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        .header("Accept-Encoding", "gzip, deflate")
        .header("Connection", "keep-alive")
        .header("Referer", "https://www.kuwo.cn/")
        .send()
        .await
        .map_err(|e| format!("cURL请求失败: {e}"))?;

    let status = resp.status().as_u16();
    let bytes = resp.bytes().await.map_err(|e| e.to_string())?;
    let elapsed_ms = start.elapsed().as_secs_f64() * 1000.0;

    if status != 200 {
        return Err(format!("酷我API请求失败，HTTP状态码: {status}"));
    }
    if bytes.is_empty() {
        return Err("酷我API返回空响应".to_string());
    }

    // PHP decoded the raw body as UTF-8 JSON, then converted individual
    // fields from GBK when needed. Decoding the whole body as GBK on a
    // UTF-8 parse failure is the equivalent at the source.
    let body = match serde_json::from_slice::<Value>(&bytes) {
        Ok(v) => v,
        Err(_) => {
            let (text, _, _) = encoding_rs::GBK.decode(&bytes);
            serde_json::from_str(&text)
                .map_err(|_| "酷我API返回数据格式错误".to_string())?
        }
    };

    Ok((body, elapsed_ms))
}
