/**
 * test-cs —— C# 版全流程测试（跑 C# exe 与 DLL，不测 Node 版）:
 *   1. 编译 cs/（调用 build-cs.bat）
 *   2. exe: -V / --info / --dry-run / 三种输入转换 + ffprobe 校验 / 并行批量 / 覆盖保护 / 非法参数 / 解码校验
 *   3. DLL: PowerShell Add-Type 调用 AmvConverter.dll
 */
'use strict';

const { spawnSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..');
const CS = path.join(ROOT, 'cs');
const EXE = path.join(CS, 'AMVConverter.exe');
const MEDIA = path.join(ROOT, 'test', 'media');
const OUT = path.join(ROOT, 'test', 'out-cs');

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
function assert(cond, msg) { if (!cond) throw new Error(msg); }

function runExe(args, timeout = 180000) {
  return spawnSync(EXE, args, { encoding: 'utf8', cwd: ROOT, timeout });
}

function findTool(name) {
  const r = spawnSync(process.platform === 'win32' ? 'where' : 'which', [name], { encoding: 'utf8' });
  if (r.status === 0) {
    for (const line of r.stdout.split(/\r?\n/)) {
      const p = line.trim();
      if (p && fs.existsSync(p)) return p;
    }
  }
  return name;
}

function genSamples() {
  fs.mkdirSync(MEDIA, { recursive: true });
  const ff = findTool('ffmpeg');
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

function ffprobeJson(file) {
  const r = spawnSync(findTool('ffprobe'),
    ['-v', 'error', '-print_format', 'json', '-show_streams', '-show_format', file],
    { encoding: 'utf8' });
  assert(r.status === 0, `ffprobe 失败: ${(r.stderr || '').trim()}`);
  return JSON.parse(r.stdout);
}
function rateToFps(v) {
  if (!v) return 0;
  const [a, b] = String(v).split('/').map(Number);
  return b ? a / b : a;
}
function decodeDuration(file) {
  const r = spawnSync(findTool('ffmpeg'), ['-i', file, '-f', 'null', '-'], { encoding: 'utf8', timeout: 300000 });
  const re = /time=(\d+):(\d+):(\d+(?:\.\d+)?)/g;
  let t = null, m;
  while ((m = re.exec(r.stderr))) t = (+m[1]) * 3600 + (+m[2]) * 60 + (+m[3]);
  return t || 0;
}
function decodeCheck(file) {
  const r = spawnSync(findTool('ffmpeg'), ['-v', 'error', '-i', file, '-f', 'null', '-'], { encoding: 'utf8', timeout: 300000 });
  assert(r.status === 0 && !(r.stderr || '').trim(), `解码失败: ${(r.stderr || '').trim().slice(0, 200)}`);
}

function main() {
  // 0. 编译
  console.log('== 编译 ==============');
  const build = spawnSync('cmd', ['/c', path.join(CS, 'build-cs.bat')], { encoding: 'utf8', cwd: ROOT, timeout: 120000 });
  assert(build.status === 0 && fs.existsSync(EXE), `编译失败:\n${build.stdout}\n${build.stderr}`);
  console.log('  编译完成\n');

  genSamples();
  fs.mkdirSync(OUT, { recursive: true });

  console.log('== C# exe ==============');
  const v = runExe(['--version']);
  check('exe --version', () => {
    assert(v.status === 0 && /v1\.1\.0/.test(v.stdout), `输出: ${v.stdout}${v.stderr}`);
  });
  check('exe --info 显示流信息', () => {
    const r = runExe(['--info', path.join(MEDIA, 'video.mp4'), path.join(MEDIA, 'audio.wav'), path.join(MEDIA, 'image.jpg')]);
    assert(r.status === 0, `退出码 ${r.status}`);
    assert(/视频\+音频/.test(r.stdout) && /纯音频/.test(r.stdout) && /图片/.test(r.stdout),
      `输出:\n${r.stdout}`);
  });
  check('exe --dry-run 只打印命令不产出文件', () => {
    const target = path.join(OUT, 'dry.amv');
    if (fs.existsSync(target)) fs.unlinkSync(target);
    const r = runExe([path.join(MEDIA, 'video.mp4'), '-o', target, '--dry-run']);
    assert(r.status === 0, `退出码 ${r.status}: ${r.stderr}`);
    assert(/-f amv/.test(r.stdout), '未打印 ffmpeg 命令');
    assert(!fs.existsSync(target), 'dry-run 不应产生输出文件');
  });

  const videoAmv = path.join(OUT, 'video.amv');
  check('exe 视频转换 + ffprobe 参数校验', () => {
    const r = runExe([path.join(MEDIA, 'video.mp4'), '-o', videoAmv, '--overwrite', '--quiet']);
    assert(r.status === 0, `退出码 ${r.status}: ${r.stderr}`);
    const data = ffprobeJson(videoAmv);
    const vst = data.streams.find((s) => s.codec_type === 'video');
    const ast = data.streams.find((s) => s.codec_type === 'audio');
    assert(vst && vst.codec_name === 'amv', `video codec=${vst && vst.codec_name}`);
    assert(vst.width === 320 && vst.height === 240, `${vst.width}x${vst.height}`);
    assert(Math.abs((rateToFps(vst.avg_frame_rate) || rateToFps(vst.r_frame_rate)) - 15) < 0.5, `fps=${vst.avg_frame_rate}`);
    assert(ast && ast.codec_name === 'adpcm_ima_amv', `audio codec=${ast && ast.codec_name}`);
    assert(+ast.sample_rate === 22050 && +ast.channels === 1, `${ast.sample_rate}Hz ${ast.channels}ch`);
  });
  check('视频 AMV 解码实测时长 ≈6s', () => {
    const d = decodeDuration(videoAmv);
    assert(Math.abs(d - 6) <= 1.5, `duration=${d}`);
  });
  check('exe 纯音频 → 黑屏 AMV (≈8s)', () => {
    const out = path.join(OUT, 'audio.amv');
    const r = runExe([path.join(MEDIA, 'audio.wav'), '-o', out, '--overwrite', '--quiet']);
    assert(r.status === 0, `退出码 ${r.status}: ${r.stderr}`);
    const d = decodeDuration(out);
    assert(Math.abs(d - 8) <= 1.5, `duration=${d}`);
    decodeCheck(out);
  });
  check('exe 图片 → 循环 AMV (≈30s)', () => {
    const out = path.join(OUT, 'image.amv');
    const r = runExe([path.join(MEDIA, 'image.jpg'), '-o', out, '--overwrite', '--quiet']);
    assert(r.status === 0, `退出码 ${r.status}: ${r.stderr}`);
    const d = decodeDuration(out);
    assert(Math.abs(d - 30) <= 1.5, `duration=${d}`);
    decodeCheck(out);
  });
  check('exe 并行批量 (-j 3)', () => {
    const dir = path.join(OUT, 'batch');
    const r = runExe(['-j', '3', MEDIA, '-o', dir, '--overwrite', '--quiet']);
    assert(r.status === 0, `退出码 ${r.status}: ${r.stderr}`);
    const n = fs.readdirSync(dir).filter((f) => f.endsWith('.amv')).length;
    assert(n === 3, `输出数量 ${n}`);
    for (const f of fs.readdirSync(dir)) decodeCheck(path.join(dir, f));
  });
  check('exe 分辨率自动取 16 倍数 (160x120 → 160x112)', () => {
    const out = path.join(OUT, 'small.amv');
    const r = runExe(['-s', '160x120', path.join(MEDIA, 'video.mp4'), '-o', out, '--overwrite', '--quiet']);
    assert(r.status === 0, `退出码 ${r.status}: ${r.stderr}`);
    const vst = ffprobeJson(out).streams.find((s) => s.codec_type === 'video');
    assert(vst.width === 160 && vst.height === 112, `${vst.width}x${vst.height}`);
  });
  check('exe 覆盖保护 (不加 --overwrite 应失败, 退出码 3)', () => {
    const r = runExe([path.join(MEDIA, 'video.mp4'), '-o', videoAmv, '--quiet']);
    assert(r.status === 3, `退出码 ${r.status}`);
  });
  check('exe 非法帧率 (退出码 1)', () => {
    const r = runExe(['-r', '16', path.join(MEDIA, 'video.mp4'), '-o', path.join(OUT, 'x.amv'), '--overwrite']);
    assert(r.status === 1, `退出码 ${r.status}`);
  });

  console.log('\n== C# DLL ==============');
  check('PowerShell 调用 AmvConverter.dll', () => {
    const r = spawnSync('powershell', ['-NoProfile', '-ExecutionPolicy', 'Bypass',
      '-File', path.join(CS, 'test-dll.ps1')], { encoding: 'utf8', timeout: 180000 });
    assert(r.status === 0, `退出码 ${r.status}: ${(r.stdout || '')}${(r.stderr || '')}`.slice(0, 300));
    assert(/return code: 0/.test(r.stdout || ''), `输出: ${r.stdout}`);
  });

  console.log(`\n测试结果: 通过 ${passed}，失败 ${failed}`);
  process.exitCode = failed ? 1 : 0;
}

main();
