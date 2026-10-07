# -*- coding: utf-8 -*-
"""
芒果TV取流接口(免Cookie)
========================

零第三方依赖,纯 Python 标准库实现。启动:

    py mgtv_api.py            # 默认监听 0.0.0.0:8899
    py mgtv_api.py --port 9000

接口:
    GET /parse?url=<链接或id>[&def=1-4][&backend=local|telecom][&hdcn=凭据]
                                                          解析视频,返回各清晰度 m3u8
    GET /play?url=<...>[&def=1-4][&backend=...][&hdcn=...] 302 跳转 m3u8
    GET /proxy/seg.m3u8?u=<urlencode的m3u8地址>           流量代理(自动带UA/Referer,分片重写)
    GET /episodes?cid=<collection_id>[&page=1&size=50]    剧集列表
    GET /                                                  使用说明

backend 说明:
    local(默认)  本机直连芒果官方接口(免Cookie,游客权限流)
    telecom      走第三方公开接口 api.telecom.ac.cn/mango(免key,画质含义不同;
                 其蓝光1080P与VIP内容需提供 hdcn 凭据 —— 芒果TV会员Web端凭据,
                 可用参数传入或设环境变量 MGTV_HDCN)。上游稳定性无保证。

url 参数支持:
    https://www.mgtv.com/b/{cid}/{vid}.html   PC播放页
    https://m.mgtv.com/b/{cid}/{vid}.html     H5播放页
    纯数字                                      video_id
    纯数字带前缀 vid= / cid=                    指定 video_id / collection_id

取流链路(全部免Cookie):
    1. pcweb.api.mgtv.com/video/streamList   (随机 did/suuid)
    2. web-disp.titan.mgtv.com/atcl          (追加 did/suuid,返回JSON含m3u8直链)
    3. m3u8 / ts 走 titan CDN,或经本服务 /proxy 代理播放

说明:
    - 免登录返回"游客权限"流:免费内容各清晰度全片,VIP内容为试看流(约5分钟)。
    - 仅限学习研究,请勿商用;版权内容请在芒果TV官方渠道观看。
"""

import argparse
import json
import os
import re
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

UA = ('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 '
      '(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36')
BASE_HEADERS = {
    'User-Agent': UA,
    'Referer': 'https://www.mgtv.com/',
    'Accept': '*/*',
    'Accept-Language': 'zh-CN,zh;q=0.9',
}

STREAMLIST_URL = 'https://pcweb.api.mgtv.com/video/streamList'
EPISODE_URL = 'https://pcweb.api.mgtv.com/episode/list'
VINFO_URL = 'https://pcweb.api.mgtv.com/player/vinfo'
ATCL_HOST = 'https://web-disp.titan.mgtv.com'
TELECOM_API = 'https://api.telecom.ac.cn/mango'

DEF_PRIORITY = [3, 2, 4, 1]  # 默认选清晰度的优先级:超清>高清>蓝光>标清

# 每个进程复用同一组设备标识;失效时通过 refresh_session() 重置
_session_lock = threading.Lock()
_session = {'did': str(uuid.uuid4()), 'suuid': str(uuid.uuid4())}


def refresh_session():
    with _session_lock:
        _session['did'] = str(uuid.uuid4())
        _session['suuid'] = str(uuid.uuid4())


def http_get(url, headers=None, timeout=15):
    h = dict(BASE_HEADERS)
    if headers:
        h.update(headers)
    req = urllib.request.Request(url, headers=h)
    return urllib.request.urlopen(req, timeout=timeout)


def http_get_json(url, timeout=15):
    resp = http_get(url, timeout=timeout)
    return json.loads(resp.read().decode('utf-8', 'replace'))


class ApiError(Exception):
    def __init__(self, message, status=400):
        super().__init__(message)
        self.status = status


# ---------------------------------------------------------------- 取流核心

def parse_target(target):
    """从各种输入形态解析出 (video_id, collection_id)。"""
    target = (target or '').strip()
    if not target:
        raise ApiError('缺少 url 参数')
    m = re.search(r'mgtv\.com/b/(\d+)/(\d+)\.html', target)
    if m:
        return m.group(2), m.group(1)
    m = re.fullmatch(r'vid=(\d+)', target)
    if m:
        return m.group(1), None
    m = re.fullmatch(r'cid=(\d+)', target)
    if m:
        return None, m.group(1)
    if target.isdigit():
        # 10位以上一般是video_id,较短的视为collection_id
        return (target, None) if len(target) >= 8 else (None, target)
    raise ApiError('无法识别的链接,期望形如 https://www.mgtv.com/b/{cid}/{vid}.html 或 video_id')


def first_episode_of(collection_id):
    data = http_get_json(f'{EPISODE_URL}?collection_id={collection_id}&page=1&size=1')['data']
    items = data.get('list') or []
    if not items:
        raise ApiError(f'collection {collection_id} 下没有剧集', 404)
    return items[0]['video_id']


def fetch_vinfo(video_id, collection_id):
    """player/vinfo:补齐标题等元信息(不依赖它也能取流)。"""
    try:
        r = http_get_json(f'{VINFO_URL}?video_id={video_id}&cid={collection_id or ""}'
                          '&pid=&cxid=&entranceType=0&_support=10000000&allowedRC=1')
        return (r.get('data') or {}).get('info') or {}
    except Exception:
        return {}


def fetch_streams(video_id, collection_id=None):
    """调 streamList + atcl,返回 (info, [stream,...])。stream 每项含可直接播放的 m3u8。"""
    with _session_lock:
        did, suuid = _session['did'], _session['suuid']

    q = urllib.parse.urlencode(dict(
        playType=1, auth_mode=1, definitionType=2, definition=2, fileSourceType=1,
        video_id=video_id, did=did, suuid=suuid, vf='h264', cxid='', entranceType=0,
        type='pch5', _support=10000000, src='mgtv', abroad=0, appVersion='9.0.8',
        allowedRC=1, sdk_version=154, cputy='Chrome', av1cap=1,
        cid=collection_id or '',
    ))
    r = http_get_json(f'{STREAMLIST_URL}?{q}')
    if r.get('code') != 200:
        raise ApiError(f'streamList 返回 {r.get("code")}: {r.get("msg")}', 502)
    data = r.get('data') or {}
    info = data.get('info') or {}
    if not info:
        raise ApiError('未取到视频信息(视频可能已下架或仅App端可见)', 404)

    streams = []
    for s in data.get('stream') or []:
        item = {
            'def': s.get('definition'),
            'name': s.get('name'),
            'standardName': s.get('standardName'),
            'resolution': f'{s.get("videoWidth")}x{s.get("videoHeight")}' if s.get('videoWidth') else '',
            'needPay': bool(s.get('needPay')),
            'filebitrate': s.get('filebitrate'),
            'videoFormat': s.get('videoFormat'),
            'm3u8': '',
        }
        if s.get('url'):
            try:
                atcl = f'{ATCL_HOST}{s["url"]}&did={did}&suuid={suuid}'
                j = http_get_json(atcl)
                item['m3u8'] = j.get('info') or ''
            except Exception as e:
                item['error'] = f'atcl 失败: {e}'
        streams.append(item)
    return info, streams


def resolve_streams(target, def_=None):
    """完整解析。返回 dict。"""
    video_id, cid = parse_target(target)
    if not video_id:
        video_id = first_episode_of(cid)
    if not cid:
        cid = None

    info, streams = fetch_streams(video_id, cid)
    meta = fetch_vinfo(video_id, cid)

    playable = [s for s in streams if s['m3u8']]
    chosen = None
    if def_:
        chosen = next((s for s in playable if int(s['def']) == int(def_)), None)
        if not chosen:
            raise ApiError(f'清晰度 def={def_} 不可用(可能为VIP画质,未登录拿不到)')
    else:
        for d in DEF_PRIORITY:
            chosen = next((s for s in playable if int(s['def']) == d), None)
            if chosen:
                break

    trialtime = info.get('trialtime') or 0
    duration = info.get('duration') or 0
    base = {'video_id': video_id, 'collection_id': info.get('collection_id') or cid,
            'title': meta.get('title') or info.get('title') or '',
            'part': info.get('series') or info.get('partName') or '',
            'desc': meta.get('desc') or info.get('desc') or '',
            'duration': info.get('duration'),
            'trialtime': info.get('trialtime'),
            # 试看时长小于总时长 => 未登录只能拿到试看流
            'is_preview_only': bool(trialtime and duration and trialtime < duration),
            'qualities': streams,
            'selected': None}
    if chosen:
        chosen = dict(chosen)
        chosen['proxy'] = ('/proxy/seg.m3u8?u='
                           + urllib.parse.quote(chosen['m3u8'], safe=''))
        base['selected'] = chosen
    return base


# --------------------------------------- 外部后端:api.telecom.ac.cn/mango
# 第三方公开接口(免key)。不带 hdcn 凭据时与我方本地链路权限一致(免费画质/试看);
# 带 hdcn(芒果TV会员在 Web 端的凭据)可解锁 VIP 内容与 1080P。
# 上游随时可能失效,故仅作 fallback/可选,默认走本地链路。

def resolve_streams_telecom(video_id, collection_id, hdcn=None, qua=None):
    params = {'id': video_id, 'title': collection_id or ''}
    if qua:
        params['qua'] = qua
    if hdcn:
        params['hdcn'] = hdcn
    r = http_get_json(TELECOM_API + '?' + urllib.parse.urlencode(params))
    if r.get('Status') == 'False':
        raise ApiError(f'telecom 后端失败: {r.get("Message", "")} {r.get("Info", "")}', 502)

    name2def = {'标清': 1, '高清': 2, '超清': 3, '蓝光': 4}
    streams = []
    for name, d in name2def.items():
        u = r.get(name)
        if u and u.startswith('http'):
            streams.append({'def': d, 'name': name, 'standardName': '',
                            'resolution': '', 'needPay': False,
                            'filebitrate': None, 'videoFormat': 'H264', 'm3u8': u})
    if not streams:
        raise ApiError('telecom 后端未返回任何可用流'
                       '(1080P/蓝光与VIP内容需 hdcn 凭据;上游也可能已失效)', 502)

    chosen = None
    if qua is not None:
        try:
            want = int(qua)
            chosen = next((s for s in streams if s['def'] == want), None)
        except (TypeError, ValueError):
            pass
    if not chosen:
        for d in DEF_PRIORITY:
            chosen = next((s for s in streams if s['def'] == d), None)
            if chosen:
                break

    chosen = dict(chosen)
    chosen['proxy'] = '/proxy/seg.m3u8?u=' + urllib.parse.quote(chosen['m3u8'], safe='')
    return {
        'backend': 'telecom',
        'video_id': video_id, 'collection_id': collection_id,
        'title': r.get('VideoName', ''), 'part': r.get('VideoTitle', ''),
        'desc': '', 'duration': None, 'trialtime': None, 'is_preview_only': None,
        'qualities': streams, 'selected': chosen,
    }


# ---------------------------------------------------------------- m3u8 代理

def proxy_m3u8(url):
    """拉取 m3u8 并把分片地址重写为本服务代理地址。

    分片用 /proxy/<name>.ts?u=... 的路径形式,让 ffmpeg/hls.js 等按扩展名识别。
    """
    resp = http_get(url)
    body = resp.read().decode('utf-8', 'replace')
    out = []
    for line in body.splitlines():
        stripped = line.strip()
        if stripped and not stripped.startswith('#'):
            absu = urllib.parse.urljoin(url, stripped)
            quoted = urllib.parse.quote(absu, safe='')
            tail = '.m3u8' if '.m3u8' in absu.split('?')[0] else '.ts'
            out.append(f'/proxy/seg{tail}?u={quoted}')
        else:
            out.append(line)
    return ('\n'.join(out) + '\n').encode('utf-8')


# ---------------------------------------------------------------- HTTP 服务
