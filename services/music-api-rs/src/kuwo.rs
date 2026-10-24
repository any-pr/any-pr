//! Upstream Kuwo API client: URLs, user agents, header/cookie blocks and
//! the small GET/HEAD helpers the endpoint handlers share.

use reqwest::header::{HeaderMap, HeaderValue};
use serde_json::Value;

use crate::util;

pub const IPHONE_UA: &str = "Mozilla/5.0 (iPhone; CPU iPhone OS 14_0 like Mac OS X) \
    AppleWebKit/537.36 (KHTML, like Gecko) Version/14.0 Mobile/15E148 \
    Safari/537.36";

pub const DESKTOP_UA: &str = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) \
    AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36";

// ---------------------------------------------------------------------------
// kw.php upstreams

/// `wapi.kuwo.cn` song info used by kw.php.
pub fn item_info_url(id: &str) -> String {
    format!("https://wapi.kuwo.cn/api/www/share/itemInfo?source=15&sourceId={id}")
}

/// `mobi.kuwo.cn` convert_url_with_sign play link for a bitrate + rid.
pub fn play_url(br: &str, rid: &str) -> String {
    format!(
        "http://mobi.kuwo.cn/mobi.s?f=web&user=0\
         &source=kwplayer_ar_8.5.5.0_apk_keluze.apk\
         &type=convert_url_with_sign&br={br}&rid={rid}"
    )
}

/// Generic GET matching the PHP `curl()` helper's header set.
pub async fn curl(url: &str) -> Option<String> {
    util::http_client(10, 15)
        .get(url)
        .header("Connection", "keep-alive")
        .header("Cache-Control", "max-age=0")
        .header("Upgrade-Insecure-Requests", "1")
        .header("User-Agent", IPHONE_UA)
        .header("Sec-Fetch-Dest", "document")
        .header("Accept-Language", "zh-CN,zh;q=0.9")
        .send()
        .await
        .ok()?
        .text()
        .await
        .ok()
}

/// Per-quality play request used in the concurrent loop (slimmer headers).
pub async fn fetch_play(br: &str, rid: &str) -> Option<Value> {
    let text = util::http_client(10, 15)
        .get(play_url(br, rid))
        .header("Connection", "keep-alive")
        .header("Cache-Control", "max-age=0")
        .header("User-Agent", IPHONE_UA)
        .header("Accept-Language", "zh-CN,zh;q=0.9")
        .send()
        .await
        .ok()?
        .text()
        .await
        .ok()?;
    serde_json::from_str(&text).ok()
}

/// HEAD size, mirroring CURLINFO_CONTENT_LENGTH_DOWNLOAD (-1 = unknown).
pub async fn remote_file_size(url: &str) -> i64 {
    match util::http_client(5, 10).head(url).send().await {
        Ok(resp) => resp
            .headers()
            .get(reqwest::header::CONTENT_LENGTH)
            .and_then(|v| v.to_str().ok())
            .and_then(|s| s.parse::<i64>().ok())
            .unwrap_or(-1),
        Err(_) => -1,
    }
}

// ---------------------------------------------------------------------------
// www.kuwo.cn api/www endpoints (playlist.php / playsc.php)

/// Header block the `api/www/*` endpoints expect. `csrf` is sent as the
/// `csrf` header when present (playlist.php sets it, playsc.php doesn't).
pub fn www_headers(cookie: &str, referer: &str, secret: &str, csrf: Option<&str>) -> HeaderMap {
    let mut m = HeaderMap::new();
    let mut ins = |k: &'static str, v: String| {
        // Referer legitimately carries raw UTF-8 (unencoded keyword).
        m.insert(k, HeaderValue::from_maybe_shared(v).expect("invalid header value"));
    };
    ins("Accept", "application/json, text/plain, */*".into());
    ins("Accept-Encoding", "gzip, deflate, br, zstd".into());
    ins("Accept-Language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7".into());
    ins("Connection", "keep-alive".into());
    ins("Cookie", cookie.into());
    ins("Referer", referer.into());
    ins("Secret", secret.into());
    ins("Sec-Fetch-Dest", "empty".into());
    ins("Sec-Fetch-Mode", "cors".into());
    ins("Sec-Fetch-Site", "same-origin".into());
    ins("User-Agent", DESKTOP_UA.into());
    ins("X-Requested-With", "mark.via".into());
    ins("sec-ch-ua", "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\"".into());
    ins("sec-ch-ua-mobile", "?0".into());
    ins("sec-ch-ua-platform", "\"Windows\"".into());
    if let Some(c) = csrf {
        ins("csrf", c.to_string());
    }
    m
}

/// playlist.php's hardcoded cookie jar; `Hm_lpvt` is time(), `kw_token`
/// matches the `csrf` header value.
pub fn playlist_cookie(kw_token: &str) -> String {
    format!(
        "Hm_lvt_cdb524f42f0ce19b169a8071123a4797=1770031436; \
         HMACCOUNT=57D1CB274A615438; _ga=GA1.2.529121512.1770271383; \
         gid=ae5aa769-e158-453e-aabf-a057e918c74b; \
         JSESSIONID=1vtnlso07iiy41r8va3cb6ymrb; \
         _gid=GA1.2.1530369396.1771752811; \
         Hm_lpvt_cdb524f42f0ce19b169a8071123a4797={}; \
         _gat=1; \
         Hm_Iuvt_cdb524f42f23cer9b268564v7y735ewrq2324=4XpxnDBJ5zeSFcp5nHtizdMNQ4QMecxG; \
         kw_token={kw_token}",
        util::now()
    )
}

/// playsc.php's hardcoded cookie jar; `Hm_lpvt` is time().
pub fn playsc_cookie() -> String {
    format!(
        "HMACCOUNT=BD7A725883CF7DF6; _ga=GA1.2.798647205.1771926917; \
         h5Uuid=bfd735973d6b4e7084591657e3421a-5a; \
         Hm_lvt_cdb524f42f0ce19b169a8071123a4797=1771926914,1774026747; \
         _gid=GA1.2.195742011.1774026748; \
         Hm_lpvt_cdb524f42f0ce19b169a8071123a4797={}; \
         _gat=1; \
         Hm_Iuvt_cdb524f42f23cer9b268564v7y735ewrq2324=eZQZMCSpZsPapjQWHPxT2t2XPa5APYxE",
        util::now()
    )
}
