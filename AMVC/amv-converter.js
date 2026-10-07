#!/usr/bin/env node
/**
 * amv-converter —— 任意格式 → AMV 转换器（MP3/MP4 随身播放器用的 AMV 视频格式）
 *
 * 命令行用法:
 *   node amv-converter.js [选项] <输入文件或目录...>
 *   AMVConverter.exe [选项] <输入文件或目录...>
 *
 * 示例:
 *   node amv-converter.js movie.mp4                  # 输出 movie.amv (默认 320x240@16fps)
 *   node amv-converter.js -s 160x120 clip.mkv        # 指定分辨率(自动取 16 的倍数)
 *   node amv-converter.js -o out.amv input.avi       # 指定输出文件
 *   node amv-converter.js -o ./amv目录 ./视频目录     # 批量转换整个目录
 *   node amv-converter.js song.mp3                   # 纯音频 → 自动配黑屏视频
 *
 * 也可作为模块使用:
 *   const { convertFile } = require('./amv-converter');
 *   await convertFile('input.mp4', 'output.amv', { width: 320, height: 240 });
 *
 * 依赖: 仅需要 ffmpeg 可执行文件（系统 PATH、本目录、或 SEA 打包内嵌均可）。
 */
'use strict';

const { spawn, spawnSync } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');

const VERSION = '1.1.0';

const DEFAULTS = Object.freeze({
  width: 320,      // 分辨率宽（AMV 常见 320x240 / 160x120 / 128x128，会自动取 16 的倍数）
  height: 240,     // 分辨率高
  fps: 15,         // 帧率（AMV: 音频固定 22050Hz，帧率必须能整除它，合法值 10/14/15）
  quality: 9,      // 视频质量 2~31，数字越小越清晰、文件越大
  stretch: false,  // false=等比缩放+黑边补齐；true=拉伸铺满
  deinterlace: false, // 隔行扫描源（DV/老摄像机）先做 yadif 去隔行
  duration: 30,    // 静态图片输入时的输出时长(秒)
  jobs: 1,         // 批量转换并行数
  overwrite: false,
  keep: false,     // 失败时保留残留的输出文件
  quiet: false,
  dryRun: false,   // 只构建命令不执行
  ffmpeg: null,    // 手动指定 ffmpeg 路径
});

const URL_RE = /^[a-z][a-z0-9+.-]*:\/\//i;
const IMAGE_EXT = new Set(['.jpg', '.jpeg', '.png', '.bmp', '.gif', '.webp', '.tif', '.tiff', '.avif']);
const MEDIA_EXT = new Set([
  ...IMAGE_EXT,
  '.mp4', '.avi', '.mkv', '.mov', '.wmv', '.flv', '.webm', '.mpg', '.mpeg', '.mpe',
  '.ts', '.m2ts', '.mts', '.m4v', '.3gp', '.3g2', '.vob', '.ogv', '.rm', '.rmvb', '.asf',
  '.mp3', '.wav', '.flac', '.m4a', '.aac', '.ogg', '.wma', '.ape', '.opus', '.amr', '.mid',
  '.amv',
]);

const isWin = process.platform === 'win32';

// ---------------------------------------------------------------- 小工具

function out(msg) { try { fs.writeSync(2, String(msg) + '\n'); } catch (_) {} }
function die(code, msg) { out(msg); process.exit(code); }

function fmtSec(s) {
  s = Math.max(0, Math.round(s));
  const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), sec = s % 60;
  const mm = String(m).padStart(2, '0'), ss = String(sec).padStart(2, '0');
  return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`;
}

/** AMV 编码器要求宽高为 16 的倍数 */
function round16(n) { return Math.max(16, Math.floor(n / 16) * 16); }

function parseSize(str) {
  const s = String(str || '').trim().toLowerCase().replace(/[x*×]/g, 'x');
  if (!/^\d{2,5}x\d{2,5}$/.test(s)) {
    throw new Error(`分辨率格式应为 宽x高，例如 320x240（收到: "${str}"）`);
  }
  const [w, h] = s.split('x').map(Number);
  if (w < 16 || h < 16) throw new Error('分辨率太小（至少 16x16）');
  const rw = round16(w), rh = round16(h);
  if (rw !== w || rh !== h) {
    out(`提示: 分辨率 ${w}x${h} 不是 16 的倍数，已调整为 ${rw}x${rh}（AMV 编码器要求）`);
  }
  return { w: rw, h: rh };
}

function isURL(s) { return URL_RE.test(s); }

function ensureAmvExt(p) {
  const ext = path.extname(p).toLowerCase();
  if (ext === '.amv') return p;
  return p.replace(/\.[^.\\/:*?"<>|]*$/, '') + '.amv';
}

// ---------------------------------------------------------------- 查找 ffmpeg

/** 读取 SEA（单文件 exe）内嵌资源 */
function seaAsset(name) {
  try {
    const sea = require('node:sea');
    if (typeof sea.isSea === 'function' && sea.isSea()) {
      const ab = sea.getAsset(name);
      return Buffer.from(ab, 0, ab.byteLength);
    }
  } catch (_) { /* 非 SEA 环境 */ }
  return null;
}

function canExecute(cmd) {
  try {
    const r = spawnSync(cmd, ['-version'], { encoding: 'utf8', windowsHide: true, timeout: 15000 });
    return r.status === 0;
  } catch (_) { return false; }
}

/** 从内嵌资源把 ffmpeg.exe 释放到磁盘，返回其路径（优先放 exe 同目录） */
function extractEmbeddedFFmpeg() {
  const buf = seaAsset('ffmpeg.exe');
  if (!buf) return null;
  const targets = [
    path.join(path.dirname(process.execPath), 'ffmpeg.exe'),
    path.join(os.homedir(), '.amv-converter', 'ffmpeg.exe'),
  ];
  for (const dst of targets) {
    try {
      fs.mkdirSync(path.dirname(dst), { recursive: true });
      fs.writeFileSync(dst, buf);
      return dst;
    } catch (_) { /* 目录只读等，换下一个位置 */ }
  }
  return null;
}

/** 按优先级定位可用的 ffmpeg，返回绝对路径或 null */
function resolveFFmpeg(explicit) {
  const candidates = [];
  if (explicit) candidates.push(explicit);
  if (process.env.AMV_FFMPEG) candidates.push(process.env.AMV_FFMPEG);
  // exe 同目录（打包后）或脚本所在目录（开发时）
  candidates.push(path.join(path.dirname(process.execPath), 'ffmpeg.exe'));
  candidates.push(path.join(__dirname, 'ffmpeg.exe'));
  // ffmpeg-static npm 包（如果恰好安装了）
  try {
    const p = require('ffmpeg-static');
    if (p) candidates.push(p);
  } catch (_) { /* 未安装，忽略 */ }

  for (const c of candidates) {
    if (c && fs.existsSync(c) && canExecute(c)) return c;
  }
  // SEA 打包：从 exe 内部释放
  if (seaAsset('ffmpeg.exe')) {
    const extracted = extractEmbeddedFFmpeg();
    if (extracted) return extracted;
  }
  // 系统 PATH
  if (canExecute(isWin ? 'ffmpeg.exe' : 'ffmpeg')) return 'ffmpeg';
  return null;
}

// ---------------------------------------------------------------- 探测输入

/**
 * 用 `ffmpeg -i <file>`（不转码）解析输入的流信息，避免依赖 ffprobe。
 * 返回 { hasVideo, hasAudio, isImage, duration }
 */
function probeMedia(ffmpegPath, file) {
  const isImage = !isURL(file) && IMAGE_EXT.has(path.extname(file).toLowerCase());
  let info = '';
  try {
    // 不指定输出时 ffmpeg 会以退出码 1 结束，但流信息都打印在 stderr 上
    const r = spawnSync(ffmpegPath, ['-hide_banner', '-i', file], {
      encoding: 'utf8', windowsHide: true, timeout: 60000,
    });
    info = (r.stderr || '') + '\n' + (r.stdout || '');
  } catch (_) { /* 下面按无流处理 */ }
  const hasVideo = /Stream #\d+:\d+.*?: Video: /.test(info);
  const hasAudio = /Stream #\d+:\d+.*?: Audio: /.test(info);
  let duration = 0;
  const d = /Duration:\s*(\d+):(\d+):(\d+(?:\.\d+)?)/.exec(info);
  if (d) duration = (+d[1]) * 3600 + (+d[2]) * 60 + (+d[3]);
  return { hasVideo, hasAudio, isImage, duration };
}

// ---------------------------------------------------------------- 组装参数

function buildFFmpegArgs(input, output, o, info) {
  const vf = (o.deinterlace ? 'yadif,' : '') +
    (o.stretch
      ? `scale=${o.width}:${o.height}`
      : `scale=${o.width}:${o.height}:force_original_aspect_ratio=decrease,` +
        `pad=${o.width}:${o.height}:(ow-iw)/2:(oh-ih)/2:color=black`) + `,fps=${o.fps}`;

  const args = ['-hide_banner', '-loglevel', 'error', '-nostdin', '-progress', 'pipe:1'];
  // 缩放算法: lanczos + 精确舍入, 缩小分辨率时比默认 bilinear 更清晰
  args.push('-sws_flags', 'lanczos+accurate_rnd+full_chroma_int');
  args.push(o.overwrite ? '-y' : '-n');
  const maps = [];

  if (info.isImage) {
    // 静态图片循环成视频
    args.push('-loop', '1', '-framerate', String(o.fps), '-i', input);
    maps.push('0:v');
    if (info.hasAudio) {
      maps.push('0:a');
    } else {
      args.push('-f', 'lavfi', '-i', 'anullsrc=channel_layout=mono:sample_rate=22050');
      maps.push('1:a');
    }
    args.push('-t', String(o.duration));
  } else if (info.hasVideo) {
    args.push('-i', input);
    maps.push('0:v');
    if (info.hasAudio) {
      maps.push('0:a');
    } else {
      // 视频没音轨 → 补静音
      args.push('-f', 'lavfi', '-i', 'anullsrc=channel_layout=mono:sample_rate=22050');
      maps.push('1:a');
      args.push('-shortest');
    }
  } else {
    // 纯音频 → 配一段黑屏视频（AMV 播放器显示画面）
    args.push('-f', 'lavfi', '-i', `color=c=black:s=${o.width}x${o.height}:r=${o.fps}`);
    args.push('-i', input);
    maps.push('0:v', '1:a');
    args.push('-shortest');
  }

  for (const m of maps) args.push('-map', m);
  args.push('-map_metadata', '-1');           // 丢弃元数据，老播放器更稳
  args.push('-vf', vf);
  args.push('-c:v', 'amv', '-q:v', String(o.quality), '-pix_fmt', 'yuvj420p');
  // AMV 音频规格: adpcm_ima_amv, 22050Hz 单声道, 块大小 = 采样率/帧率（amv muxer 强制要求）
  args.push('-c:a', 'adpcm_ima_amv', '-ar', '22050', '-ac', '1',
            '-block_size', String(Math.round(22050 / o.fps)));
  args.push('-f', 'amv', output);
  return args;
}

// ---------------------------------------------------------------- 执行转换

function runFFmpeg(ffmpegPath, args, meta) {
  return new Promise((resolve, reject) => {
    const existedBefore = fs.existsSync(meta.output);
    let killed = false;
    const child = spawn(ffmpegPath, args, { stdio: ['ignore', 'pipe', 'pipe'], windowsHide: true });

    let errTail = '';
    let outBuf = '';
    let lastLine = '';

    const clearProgress = () => {
      if (lastLine) { try { fs.writeSync(2, '\r' + ' '.repeat(lastLine.length + 2) + '\r'); } catch (_) {} lastLine = ''; }
    };
    const draw = (line) => {
      if (line === lastLine) return;
      try { fs.writeSync(2, line); } catch (_) {}
      lastLine = line;
    };

    child.stdout.setEncoding('utf8');
    child.stdout.on('data', (s) => {
      // -progress pipe:1 输出 key=value 行，out_time_ms 实际是微秒
      outBuf += s;
      if (outBuf.length > 8192) outBuf = outBuf.slice(-4096);
      if (meta.quiet) return;
      let t = null, m;
      const re = /out_time_ms=(\d+)/g;
      while ((m = re.exec(outBuf))) t = +m[1];
      if (t == null) return;
      const sec = t / 1e6;
      const total = meta.duration || 0;
      if (total > 0) {
        const pct = Math.min(99, Math.floor((sec / total) * 100));
        const w = 24;
        const fill = Math.round((w * pct) / 100);
        draw(`\r转换中 [${'#'.repeat(fill)}${'-'.repeat(w - fill)}] ${String(pct).padStart(3)}%  ${fmtSec(sec)}/${fmtSec(total)}`);
      } else {
        draw(`\r转换中 已处理 ${fmtSec(sec)}`);
      }
    });

    child.stderr.setEncoding('utf8');
    child.stderr.on('data', (s) => { errTail = (errTail + s).slice(-4000); });

    child.on('error', (err) => {
      reject(new Error(`无法启动 ffmpeg (${ffmpegPath}): ${err.message}`));
    });

    const onInt = () => { killed = true; try { child.kill('SIGKILL'); } catch (_) {} };
    process.on('SIGINT', onInt);

    child.on('close', (code) => {
      process.removeListener('SIGINT', onInt);
      clearProgress();
      if (code === 0 && !killed) return resolve();
      // 失败时清理残留的输出文件（之前就存在的不动）
      if (!existedBefore && !meta.keep) {
