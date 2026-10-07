// AmvConverterLib.cs —— AMV 转换核心库（.NET Framework 4.0+，Windows 自带 csc 可编译，无需安装任何东西）
//
// 编译见同目录 build-cs.bat。作为 DLL 被其他 .NET 程序调用:
//   int rc = AmvTools.AmvConverter.Convert(@"C:\in.mp4", @"C:\out.amv");
//   if (rc != 0) Console.WriteLine(AmvTools.AmvConverter.LastError);
//
// 需要进度回调 / 并行批量 / 获取完整错误与命令行时, 使用 Run():
//   AmvTools.AmvResult r = AmvTools.AmvConverter.Run(input, output, options, (sec, total) => { ... });
//   Run() 线程安全, 可以多个线程同时调用做并行批量转换。
//
// 返回码: 0=成功; 10=参数错误; 11=输入不存在; 12=输出已存在; 1=未找到/无法启动 ffmpeg; 其他=ffmpeg 退出码。
//
// AMV 规范要点（ffmpeg 原生 amv muxer 强制要求）:
//   视频 amv(mjpeg) yuvj420p; 音频 adpcm_ima_amv 22050Hz 单声道, block_size=22050/帧率;
//   帧率仅支持 10/14/15（22050 必须能被帧率整除）; 宽高自动取 16 的倍数。
using System;
using System.Collections.Concurrent;
using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.Text;
using System.Text.RegularExpressions;

namespace AmvTools
{
    /// <summary>转换选项（与命令行参数一一对应）</summary>
    public sealed class AmvOptions
    {
        public int Width = 320;
        public int Height = 240;
        public int Fps = 15;
        public int Quality = 9;      // 2~31, 越小越清晰
        public bool Stretch;         // true=拉伸铺满; false=等比缩放+黑边
        public bool Deinterlace;     // 隔行扫描源（DV/老摄像机）先做 yadif 去隔行
        public int Duration = 30;    // 静态图片输出时长(秒)
        public bool Overwrite;
        public bool Keep;            // 失败时保留残留输出
        public bool Quiet;
        public bool DryRun;          // 只构建命令不执行（用于 --dry-run）
        public string FFmpegPath;    // 显式指定 ffmpeg 路径
    }

    /// <summary>输入媒体探测结果</summary>
    public sealed class MediaInfo
    {
        public bool HasVideo;
        public bool HasAudio;
        public bool IsImage;
        public double Duration;
    }

    /// <summary>一次转换的结果</summary>
    public sealed class AmvResult
    {
        public int ExitCode;
        public string OutputPath;
        public string Error;         // ExitCode != 0 时的失败原因
        public string CommandLine;   // 实际执行的 ffmpeg 命令（DryRun 也返回）
    }

    public static class AmvConverter
    {
        public const string Version = "1.1.0";

        /// <summary>合法帧率: AMV 音频固定 22050Hz, 必须能被帧率整除</summary>
        public static readonly int[] SupportedFps = new int[] { 10, 14, 15 };

        public static readonly string[] ImageExtensions = new string[] {
            ".jpg", ".jpeg", ".png", ".bmp", ".gif", ".webp", ".tif", ".tiff", ".avif" };

        public static readonly string[] MediaExtensions = new string[] {
            ".mp4", ".avi", ".mkv", ".mov", ".wmv", ".flv", ".webm", ".mpg", ".mpeg", ".mpe",
            ".ts", ".m2ts", ".mts", ".m4v", ".3gp", ".3g2", ".vob", ".ogv", ".rm", ".rmvb", ".asf",
            ".mp3", ".wav", ".flac", ".m4a", ".aac", ".ogg", ".wma", ".ape", ".opus", ".amr", ".mid",
            ".amv",
            ".jpg", ".jpeg", ".png", ".bmp", ".gif", ".webp", ".tif", ".tiff", ".avif" };

        /// <summary>最近一次 Convert/ConvertEx/ConvertFull 失败的原因（便捷入口用; 并行请改用 Run().Error）</summary>
        public static string LastError { get; private set; }

        private static string _resolvedFFmpeg;
        private static readonly ConcurrentDictionary<long, Process> _running =
            new ConcurrentDictionary<long, Process>();

        // ------------------------------------------------------------ 便捷入口

        /// <summary>默认参数转换（320x240 @ 15fps, 质量 9）。返回 0 表示成功。</summary>
        public static int Convert(string input, string output)
        {
            return ConvertFull(input, output, new AmvOptions(), null);
        }

        /// <summary>完整参数转换（保留的兼容入口）。宽高自动取 16 的倍数, fps 非法时按 15 处理。</summary>
        public static int ConvertEx(string input, string output, string ffmpegPath,
                                    int width, int height, int fps, int quality)
        {
            AmvOptions o = new AmvOptions();
            o.FFmpegPath = ffmpegPath;
            o.Width = width;
            o.Height = height;
            o.Fps = fps;
            o.Quality = quality;
            o.Overwrite = true;
            o.Quiet = true;
            return ConvertFull(input, output, o, null);
        }

        /// <summary>
        /// 核心转换（便捷封装, 结果通过 LastError 获取）。并行/需要完整结果请改用 Run()。
        /// 视频转 AMV；纯音频自动配黑屏画面；静态图片循环 Duration 秒；无音轨自动补静音。
        /// </summary>
        public static int ConvertFull(string input, string output, AmvOptions o, Action<double, double> onProgress)
        {
            AmvResult r = Run(input, output, o, onProgress);
            if (r.ExitCode != 0) LastError = r.Error;
            return r.ExitCode;
        }

        /// <summary>
        /// 核心入口（线程安全, 可多线程并发调用）。
        /// onProgress(已处理秒数, 总秒数) 可为 null; o.DryRun=true 时只构建命令不执行。
        /// </summary>
        public static AmvResult Run(string input, string output, AmvOptions o, Action<double, double> onProgress)
        {
            AmvResult r = new AmvResult();
            r.OutputPath = output;
            if (o == null) o = new AmvOptions();

            if (string.IsNullOrEmpty(input) || string.IsNullOrEmpty(output))
            { r.ExitCode = 10; r.Error = "输入/输出路径不能为空"; return r; }

            o.Width = Round16(o.Width);
            o.Height = Round16(o.Height);
            if (o.Width < 16 || o.Height < 16) { r.ExitCode = 10; r.Error = "分辨率过小（至少 16x16）"; return r; }
            if (Array.IndexOf(SupportedFps, o.Fps) < 0)
            { r.ExitCode = 10; r.Error = "帧率仅支持 10/14/15（AMV 音频固定 22050Hz, 采样率必须能被帧率整除）"; return r; }
            if (o.Quality < 2 || o.Quality > 31) { r.ExitCode = 10; r.Error = "quality 取值范围 2~31"; return r; }
            if (o.Duration <= 0) o.Duration = 30;

            if (!IsUrl(input) && !File.Exists(input))
            { r.ExitCode = 11; r.Error = "输入文件不存在: " + input; return r; }

            string outFull = Path.GetFullPath(output);
            r.OutputPath = outFull;
            if (!Path.GetExtension(outFull).Equals(".amv", StringComparison.OrdinalIgnoreCase))
                outFull = Path.ChangeExtension(outFull, ".amv");

            bool existedBefore = File.Exists(outFull);
            if (existedBefore && !o.Overwrite && !o.DryRun)
            { r.ExitCode = 12; r.Error = "输出文件已存在: " + outFull + "（指定 Overwrite/--overwrite 覆盖）"; return r; }

            if (!o.DryRun)
            {
                try
                {
                    string dir = Path.GetDirectoryName(outFull);
                    if (!string.IsNullOrEmpty(dir)) Directory.CreateDirectory(dir);
                }
                catch (Exception ex) { r.ExitCode = 10; r.Error = "无法创建输出目录: " + ex.Message; return r; }
            }

            string ff = ResolveFFmpeg(o.FFmpegPath);
            if (ff == null)
            {
                r.ExitCode = 1;
                r.Error = "未找到可用的 ffmpeg。请: 1) 把 ffmpeg.exe 放在本程序同目录; " +
                          "2) 或加入 PATH; 3) 或用 --ffmpeg 指定路径";
                return r;
            }

            MediaInfo info = Probe(ff, input);
            if (!info.HasVideo && !info.HasAudio && !info.IsImage)
            { r.ExitCode = 10; r.Error = "输入文件中没有可用的音视频流: " + input; return r; }

            string args = BuildArguments(input, outFull, o, info);
            r.CommandLine = "\"" + ff + "\" " + args;
            if (o.DryRun) { r.ExitCode = 0; return r; }

            int rc = RunFFmpeg(ff, args, info.IsImage ? (double)o.Duration : info.Duration, o.Quiet ? null : onProgress);
            r.ExitCode = rc;
            if (rc != 0)
            {
                r.Error = LastError;
                if (!existedBefore && !o.Keep)
                { try { File.Delete(outFull); } catch { } }
            }
            return r;
        }

        /// <summary>终止所有正在进行的 ffmpeg 转换（Ctrl+C 等场景; 线程安全）</summary>
        public static void CancelCurrent()
        {
            foreach (System.Collections.Generic.KeyValuePair<long, Process> kv in _running)
            {
                try { kv.Value.Kill(); } catch { }
            }
        }

        // ------------------------------------------------------------ ffmpeg 定位

        /// <summary>定位 ffmpeg: 参数 → 环境变量 AMV_FFMPEG → 本程序集目录 → 宿主 exe 目录 → PATH</summary>
        public static string ResolveFFmpeg(string explicitPath)
        {
            if (!string.IsNullOrEmpty(explicitPath))
            {
                if (TryFFmpeg(explicitPath)) return explicitPath;
                return null;
            }
            if (_resolvedFFmpeg != null) return _resolvedFFmpeg;

            string env = Environment.GetEnvironmentVariable("AMV_FFMPEG");
            if (!string.IsNullOrEmpty(env) && TryFFmpeg(env)) return _resolvedFFmpeg = env;

            try
            {
                string dir = Path.GetDirectoryName(typeof(AmvConverter).Assembly.Location);
                if (!string.IsNullOrEmpty(dir))
                {
                    string cand = Path.Combine(dir, "ffmpeg.exe");
                    if (TryFFmpeg(cand)) return _resolvedFFmpeg = cand;
                }
            }
            catch { }

            try
            {
                ProcessModule main = Process.GetCurrentProcess().MainModule;
                if (main != null && !string.IsNullOrEmpty(main.FileName))
                {
                    string cand = Path.Combine(Path.GetDirectoryName(main.FileName), "ffmpeg.exe");
                    if (TryFFmpeg(cand)) return _resolvedFFmpeg = cand;
                }
            }
            catch { }

            string pathEnv = Environment.GetEnvironmentVariable("PATH");
            if (!string.IsNullOrEmpty(pathEnv))
            {
                foreach (string raw in pathEnv.Split(';'))
                {
                    string d = raw.Trim();
                    if (d.Length == 0) continue;
                    try
                    {
                        string cand = Path.Combine(d, "ffmpeg.exe");
                        if (File.Exists(cand) && TryFFmpeg(cand)) return _resolvedFFmpeg = cand;
                    }
                    catch { }
                }
            }
            // 最后交给 PATH 直接解析
            if (TryFFmpeg("ffmpeg.exe")) return _resolvedFFmpeg = "ffmpeg.exe";
            return null;
        }

        private static bool TryFFmpeg(string path)
        {
            try
            {
                ProcessStartInfo psi = new ProcessStartInfo();
                psi.FileName = path;
                psi.Arguments = "-version";
                psi.UseShellExecute = false;
                psi.CreateNoWindow = true;
                psi.RedirectStandardOutput = true;
                psi.RedirectStandardError = true;
                using (Process p = Process.Start(psi))
                {
                    p.StandardOutput.ReadToEnd();
                    p.StandardError.ReadToEnd();
                    p.WaitForExit(15000);
                    return p.ExitCode == 0;
                }
            }
            catch { return false; }
        }

        // ------------------------------------------------------------ 探测与参数

        /// <summary>用 `ffmpeg -i`（不转码）探测输入的流信息，不依赖 ffprobe</summary>
        public static MediaInfo Probe(string ffmpegPath, string input)
        {
            MediaInfo mi = new MediaInfo();
            mi.IsImage = !IsUrl(input) && IsImageExt(input);
            try
            {
                ProcessStartInfo psi = new ProcessStartInfo();
                psi.FileName = ffmpegPath;
                psi.Arguments = "-hide_banner -i \"" + input + "\"";
                psi.UseShellExecute = false;
                psi.CreateNoWindow = true;
                psi.RedirectStandardError = true;
                psi.RedirectStandardOutput = true;
                using (Process p = Process.Start(psi))
                {
                    // 不指定输出时 ffmpeg 以退出码 1 结束, 流信息都在 stderr
                    string err = p.StandardError.ReadToEnd();
                    p.StandardOutput.ReadToEnd();
                    p.WaitForExit();
                    mi.HasVideo = Regex.IsMatch(err, @"Stream #\d+:\d+.*?: Video: ");
