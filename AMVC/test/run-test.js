#!/usr/bin/env node
/**
 * run-test —— 自动化测试:
 *   1. 用 ffmpeg lavfi 生成测试样本（视频 / 纯音频 / 图片）
 *   2. 全部转换为 AMV
 *   3. 用 ffprobe 校验编码参数（amv 视频 320x240@16fps、mp2 22050Hz 单声道）
 *   4. 用 ffmpeg 解码一遍确认文件完整
 */
'use strict';

const { spawnSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..');
const { convertFile, resolveFFmpeg } = require(path.join(ROOT, 'amv-converter.js'));

const MEDIA = path.join(__dirname, 'media');
const OUT = path.join(__dirname, 'out');

let passed = 0, failed = 0;
function check(name, fn) {
  try {
    fn();
    console.log(`  [通过] ${name}`);
    passed++;
  } catch (e) {
    console.log(`  [失败] ${name}\n         ${e.message}`);
    failed++;
  }
}

function resolveFFprobe(ff) {
  const exe = process.platform === 'win32' ? '.exe' : '';
  const cand = path.join(path.dirname(path.resolve(ff)), 'ffprobe' + exe);
  if (fs.existsSync(cand)) return cand;
  const r = spawnSync('ffprobe', ['-version'], { encoding: 'utf8' });
  if (r.status === 0) return 'ffprobe';
  return null;
}

function genSamples(ff) {
  fs.mkdirSync(MEDIA, { recursive: true });
  const gen = (args, file) => {
    if (fs.existsSync(path.join(MEDIA, file))) return;
    console.log(`生成样本: ${file}`);
    const r = spawnSync(ff, ['-y', '-hide_banner', '-loglevel', 'error', ...args], { encoding: 'utf8' });
    if (r.status !== 0) throw new Error(`生成 ${file} 失败: ${r.stderr}`);
  };
  gen(['-f', 'lavfi', '-i', 'testsrc2=duration=6:size=640x360:rate=30',
       '-f', 'lavfi', '-i', 'sine=frequency=440:duration=6',
       '-c:v', 'mpeg4', '-q:v', '5', '-pix_fmt', 'yuv420p',
       '-c:a', 'aac', '-shortest', path.join(MEDIA, 'video.mp4')], 'video.mp4');
  gen(['-f', 'lavfi', '-i', 'sine=frequency=523:duration=8',
       '-c:a', 'pcm_s16le', path.join(MEDIA, 'audio.wav')], 'audio.wav');
  gen(['-f', 'lavfi', '-i', 'testsrc2=duration=1:size=800x600:rate=1',
       '-frames:v', '1', path.join(MEDIA, 'image.jpg')], 'image.jpg');
}

function streamsOf(ffprobe, file) {
  const r = spawnSync(ffprobe, ['-v', 'error', '-print_format', 'json', '-show_streams', '-show_format', file], { encoding: 'utf8' });
  if (r.status !== 0) throw new Error(`ffprobe 失败: ${(r.stderr || '').trim()}`);
  return JSON.parse(r.stdout);
}

function rateToFps(v) {
  if (!v) return 0;
  const [a, b] = String(v).split('/').map(Number);
  return b ? a / b : a;
}

function decodeCheck(ff, file) {
  const r = spawnSync(ff, ['-v', 'error', '-i', file, '-f', 'null', '-'], { encoding: 'utf8', timeout: 120000 });
  if (r.status !== 0) throw new Error(`解码失败(退出码 ${r.status}): ${(r.stderr || '').trim().slice(0, 300)}`);
  if ((r.stderr || '').trim()) throw new Error(`解码有错误输出: ${(r.stderr || '').trim().slice(0, 300)}`);
}

/** AMV 容器头部不写 duration, 用完整解码实测时长最可靠 */
function decodeDuration(ff, file) {
  const r = spawnSync(ff, ['-i', file, '-f', 'null', '-'], { encoding: 'utf8', timeout: 300000 });
  const re = /time=(\d+):(\d+):(\d+(?:\.\d+)?)/g;
  let t = null, m;
  while ((m = re.exec(r.stderr))) t = (+m[1]) * 3600 + (+m[2]) * 60 + (+m[3]);
  return t || 0;
}

async function main() {
  const ff = resolveFFmpeg();
  if (!ff) { console.error('错误: 找不到 ffmpeg'); process.exit(2); }
  console.log(`ffmpeg: ${ff}`);
  const ffprobe = resolveFFprobe(ff);
  if (!ffprobe) console.log('提示: 未找到 ffprobe，将跳过参数校验，仅做解码校验');

  genSamples(ff);
  fs.mkdirSync(OUT, { recursive: true });

  const cases = [
    { name: '视频 → AMV', input: 'video.mp4', expectDuration: 6 },
    { name: '纯音频 → AMV(黑屏)', input: 'audio.wav', expectDuration: 8 },
    { name: '图片 → AMV(循环30秒)', input: 'image.jpg', expectDuration: 30 },
  ];

  for (const c of cases) {
    console.log(`\n== ${c.name} ==============`);
    const input = path.join(MEDIA, c.input);
    const output = path.join(OUT, c.input.replace(/\.[^.]+$/, '') + '.amv');
    const t0 = Date.now();
    await convertFile(input, output, { overwrite: true });
    console.log(`  转换完成: ${(fs.statSync(output).size / 1024).toFixed(1)} KB, ${((Date.now() - t0) / 1000).toFixed(1)}s`);

    if (ffprobe) {
      const data = streamsOf(ffprobe, output);
      const v = data.streams.find((s) => s.codec_type === 'video');
      const a = data.streams.find((s) => s.codec_type === 'audio');
      check('视频流为 amv 编码', () => {
        if (!v) throw new Error('没有视频流');
        if (v.codec_name !== 'amv') throw new Error(`codec=${v.codec_name}`);
      });
      check('分辨率 320x240', () => {
        if (v.width !== 320 || v.height !== 240) throw new Error(`${v.width}x${v.height}`);
      });
      check('帧率 15fps', () => {
        const fps = rateToFps(v.avg_frame_rate) || rateToFps(v.r_frame_rate);
        if (Math.abs(fps - 15) > 0.5) throw new Error(`fps=${v.avg_frame_rate}`);
      });
      check('音频为 adpcm_ima_amv / 22050Hz / 单声道', () => {
        if (!a) throw new Error('没有音频流');
        if (a.codec_name !== 'adpcm_ima_amv') throw new Error(`codec=${a.codec_name}`);
        if (+a.sample_rate !== 22050) throw new Error(`sample_rate=${a.sample_rate}`);
        if (+a.channels !== 1) throw new Error(`channels=${a.channels}`);
      });
      check(`时长约 ${c.expectDuration}s（解码实测）`, () => {
        const d = decodeDuration(ff, output);
        if (d <= 0) throw new Error('未解析到时长');
        if (Math.abs(d - c.expectDuration) > 1.5) throw new Error(`duration=${d}`);
      });
    }
    check('输出文件可完整解码', () => decodeCheck(ff, output));
  }

  // ---- CLI 功能测试（node amv-converter.js）----
  console.log('\n== CLI 功能测试 ==============');
  const CLI = path.join(ROOT, 'amv-converter.js');
  const runCli = (args) => spawnSync(process.execPath, [CLI, ...args], { encoding: 'utf8', cwd: ROOT, timeout: 180000 });

  check('CLI 批量转换目录', () => {
    const r = runCli(['test/media', '-o', 'test/out/node-cli', '--overwrite', '--quiet']);
    if (r.status !== 0) throw new Error(`退出码 ${r.status}: ${(r.stderr || '').slice(0, 200)}`);
    for (const n of ['video.amv', 'audio.amv', 'image.amv']) {
      if (!fs.existsSync(path.join(ROOT, 'test', 'out', 'node-cli', n))) throw new Error('缺少 ' + n);
    }
  });
  check('CLI 并行批量 (-j 2)', () => {
    const r = runCli(['-j', '2', 'test/media', '-o', 'test/out/node-cli2', '--overwrite', '--quiet']);
    if (r.status !== 0) throw new Error(`退出码 ${r.status}: ${(r.stderr || '').slice(0, 200)}`);
    const n = fs.readdirSync(path.join(ROOT, 'test', 'out', 'node-cli2')).filter((f) => f.endsWith('.amv')).length;
    if (n !== 3) throw new Error(`输出数量 ${n}`);
  });
  check('CLI --dry-run 只打印命令不产出文件', () => {
    const target = path.join(ROOT, 'test', 'out', 'dry-run.amv');
    if (fs.existsSync(target)) fs.unlinkSync(target);
    const r = runCli(['test/media/video.mp4', '-o', 'test/out/dry-run.amv', '--dry-run']);
    const text = (r.stdout || '') + (r.stderr || '');   // Node CLI 的输出走 stderr
    if (r.status !== 0) throw new Error(`退出码 ${r.status}`);
    if (!/将执行:.*-f amv/.test(text)) throw new Error('未打印 ffmpeg 命令');
    if (fs.existsSync(target)) throw new Error('dry-run 不应产生输出文件');
  });
  check('CLI --info 显示流信息', () => {
    const r = runCli(['--info', 'test/media/video.mp4']);
    const text = (r.stdout || '') + (r.stderr || '');   // Node CLI 的输出走 stderr
    if (r.status !== 0) throw new Error(`退出码 ${r.status}`);
    if (!/视频\+音频/.test(text)) throw new Error('输出缺少流信息');
  });
  check('CLI --deinterlace 可用', () => {
    const r = runCli(['--deinterlace', 'test/media/video.mp4', '-o', 'test/out/deint.amv', '--overwrite', '--quiet']);
    if (r.status !== 0) throw new Error(`退出码 ${r.status}: ${(r.stderr || '').slice(0, 200)}`);
    decodeCheck(ff, path.join(ROOT, 'test', 'out', 'deint.amv'));
  });

  console.log(`\n测试结果: 通过 ${passed}，失败 ${failed}`);
  process.exitCode = failed ? 1 : 0;
}

main().catch((e) => { console.error('测试执行出错:', e); process.exit(1); });
