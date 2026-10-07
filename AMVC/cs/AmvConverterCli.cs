// AmvConverterCli.cs —— AMV 转换命令行（与 AmvConverterLib.cs 一起编译为 AMVConverter.exe）
//
// 用法: AMVConverter.exe [选项] <输入文件或目录...>
//   AMVConverter.exe movie.mp4                        → movie.amv (320x240 @ 15fps)
//   AMVConverter.exe -s 160x120 -o ./amv ./视频目录     → 批量转换
//   AMVConverter.exe -j 4 -o ./amv ./视频目录          → 4 路并行批量
//   AMVConverter.exe --info movie.mp4                 → 只查看输入信息
//   AMVConverter.exe --dry-run movie.mp4              → 只打印 ffmpeg 命令不执行
//   AMVConverter.exe song.mp3                         → 纯音频配黑屏画面
//
// 从资源管理器双击/拖拽运行结束时自动暂停（按 Enter 退出），方便看到结果。
using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using AmvTools;

static class Program
{
    static volatile int s_interrupted;
    static readonly object PrintLock = new object();

    static int Main(string[] argv)
    {
        try { Console.OutputEncoding = Encoding.UTF8; } catch { }

        int rc = RealMain(argv);

        // 从资源管理器启动（双击/拖拽）时暂停, 避免窗口一闪而过; 输出被重定向(脚本/管道)时不暂停
        if (!Console.IsOutputRedirected && LaunchedFromExplorer())
        {
            Console.Write("\n按 Enter 键退出...");
            try { Console.ReadLine(); } catch { }
        }
        return rc;
    }

    /// <summary>判断父进程是否为 explorer（双击 exe / 拖拽文件的场景）</summary>
    static bool LaunchedFromExplorer()
    {
        try
        {
            int pid = Process.GetCurrentProcess().Id;
            using (System.Management.ManagementObjectSearcher searcher = new System.Management.ManagementObjectSearcher(
                "SELECT ParentProcessId FROM Win32_Process WHERE ProcessId = " + pid))
            {
                foreach (System.Management.ManagementObject mo in searcher.Get())
                {
                    int parent;
                    if (!int.TryParse(mo["ParentProcessId"].ToString(), out parent)) return false;
                    using (Process pp = Process.GetProcessById(parent))
                        return pp.ProcessName.Equals("explorer", StringComparison.OrdinalIgnoreCase);
                }
            }
        }
        catch { }
        return false;
    }

    static int RealMain(string[] argv)
    {
        AmvOptions opts = new AmvOptions();
        string outPath = null;
        string sizeArg = null;
        int jobs = 1;
        bool infoMode = false, dryRun = false;
        List<string> rawInputs = new List<string>();

        for (int i = 0; i < argv.Length; i++)
        {
            string a = argv[i];
            string inline = null;
            if (a.StartsWith("--") && a.IndexOf('=') > 0)
            {
                int eq = a.IndexOf('=');
                inline = a.Substring(eq + 1);
                a = a.Substring(0, eq);
            }
            bool need = (a == "-o" || a == "--out" || a == "-s" || a == "--size" || a == "-r" || a == "--fps"
                      || a == "-q" || a == "--quality" || a == "--duration" || a == "--ffmpeg"
                      || a == "-j" || a == "--jobs");
            if (need && inline == null)
            {
                if (i + 1 >= argv.Length) return Fail("错误: 参数 " + a + " 缺少值（见 --help）");
                inline = argv[++i];
            }
            switch (a)
            {
                case "-h": case "--help": PrintHelp(); return 0;
                case "-V": case "--version": Console.WriteLine("AMVConverter (C#) v" + AmvConverter.Version); return 0;
                case "-o": case "--out": outPath = inline; break;
                case "-s": case "--size": sizeArg = inline; break;
                case "-r": case "--fps":
                    { int v; if (!int.TryParse(inline, out v)) return Fail("错误: --fps 不是数字: " + inline); opts.Fps = v; break; }
                case "-q": case "--quality":
                    { int v; if (!int.TryParse(inline, out v)) return Fail("错误: --quality 不是数字: " + inline); opts.Quality = v; break; }
                case "--duration":
                    { int v; if (!int.TryParse(inline, out v)) return Fail("错误: --duration 不是数字: " + inline); opts.Duration = v; break; }
                case "-j": case "--jobs":
                    { int v; if (!int.TryParse(inline, out v) || v < 1 || v > 16) return Fail("错误: --jobs 取值 1~16"); jobs = v; break; }
                case "--ffmpeg": opts.FFmpegPath = inline; break;
                case "--stretch": opts.Stretch = true; break;
                case "--deinterlace": opts.Deinterlace = true; break;
                case "--overwrite": opts.Overwrite = true; break;
                case "--keep": opts.Keep = true; break;
                case "--quiet": opts.Quiet = true; break;
                case "--dry-run": dryRun = true; break;
                case "--info": infoMode = true; break;
                default:
                    if (a.StartsWith("-") && a != "-") return Fail("错误: 未知选项 " + a + "（见 --help）");
                    rawInputs.Add(inline != null ? inline : a);
                    break;
            }
        }

        if (rawInputs.Count == 0) { PrintHelp(); return 1; }

        if (sizeArg != null)
        {
            Match m = Regex.Match(sizeArg.Trim(), @"^(\d{2,5})[xX*](\d{2,5})$");
            if (!m.Success) return Fail("错误: 分辨率格式应为 宽x高，例如 320x240（收到: \"" + sizeArg + "\"）");
            int w = int.Parse(m.Groups[1].Value), h = int.Parse(m.Groups[2].Value);
            int rw = AmvConverter.Round16(w), rh = AmvConverter.Round16(h);
            if (rw != w || rh != h)
                Console.WriteLine("提示: 分辨率 " + w + "x" + h + " 不是 16 的倍数，已调整为 " + rw + "x" + rh + "（AMV 编码器要求）");
            opts.Width = rw; opts.Height = rh;
        }
        if (Array.IndexOf(AmvConverter.SupportedFps, opts.Fps) < 0)
            return Fail("错误: --fps 仅支持 10/14/15（AMV 音频固定 22050Hz，采样率必须能被帧率整除）");

        // 收集输入（目录 → 展开为媒体文件列表）
        List<string> files = new List<string>();
        try
        {
            foreach (string t in rawInputs)
            {
                if (AmvConverter.IsUrl(t)) { files.Add(t); continue; }
                if (Directory.Exists(t))
                {
                    List<string> found = new List<string>();
                    foreach (string f in Directory.GetFiles(t))
                        if (AmvConverter.IsMediaExt(f)) found.Add(f);
                    found.Sort(StringComparer.OrdinalIgnoreCase);
                    if (found.Count == 0)
                        Console.WriteLine("提示: 目录中没有可识别的媒体文件，已跳过: " + t);
                    files.AddRange(found);
                }
                else if (File.Exists(t)) files.Add(t);
                else return Fail("错误: 无法访问: " + t);
            }
        }
        catch (Exception ex) { return Fail("错误: " + ex.Message); }
        if (files.Count == 0) return Fail("错误: 没有可转换的输入");

        string ff = AmvConverter.ResolveFFmpeg(opts.FFmpegPath);
        if (ff == null)
        {
            Console.Error.WriteLine("错误: 未找到可用的 ffmpeg。请任选其一:");
            Console.Error.WriteLine("  1) 把 ffmpeg.exe 放在本程序同目录");
            Console.Error.WriteLine("  2) 安装 ffmpeg 并加入 PATH");
            Console.Error.WriteLine("  3) 用 --ffmpeg 参数指定 ffmpeg 路径");
            return 2;
        }

        // ---- --info 模式: 只查看输入信息 ----
        if (infoMode)
        {
            foreach (string f in files)
            {
                MediaInfo mi = AmvConverter.Probe(ff, f);
                string kind = mi.IsImage ? "图片"
                    : (mi.HasVideo ? (mi.HasAudio ? "视频+音频" : "视频(无音轨)")
                                   : (mi.HasAudio ? "纯音频" : "未知"));
                Console.WriteLine(string.Format("{0}  [{1}, {2}]", f, kind, FmtSec(mi.Duration)));
            }
            return 0;
        }

        if (!opts.Quiet)
        {
            Console.WriteLine("ffmpeg: " + ff);
            Console.WriteLine("参数: " + opts.Width + "x" + opts.Height + "@" + opts.Fps + "fps q=" + opts.Quality +
                              " 音频 adpcm_ima_amv 22050Hz 单声道" +
                              (opts.Deinterlace ? " +去隔行" : "") +
                              (jobs > 1 ? " 并行x" + jobs : ""));
            Console.WriteLine("共 " + files.Count + " 个文件，开始转换...");
        }

        Console.CancelKeyPress += delegate(object s, ConsoleCancelEventArgs e)
        {
            e.Cancel = true;
            s_interrupted = 130;
            AmvConverter.CancelCurrent();
        };

        // 计算输出路径（批量与并行共用）
        bool batch = files.Count > 1;
        string outDir = null, outFile = null;
        if (outPath != null)
        {
            bool looksLikeFile = Regex.IsMatch(outPath, @"\.[a-z0-9]{1,5}$", RegexOptions.IgnoreCase) && !Directory.Exists(outPath);
            if (batch)
            {
                if (looksLikeFile) return Fail("错误: 多个输入时 --out 必须是目录");
                outDir = outPath;
                try { Directory.CreateDirectory(outDir); } catch (Exception ex) { return Fail("错误: " + ex.Message); }
            }
            else if (looksLikeFile) outFile = outPath;
            else { outDir = outPath; Directory.CreateDirectory(outDir); }
        }
        string[] dests = new string[files.Count];
        for (int i = 0; i < files.Count; i++)
        {
            dests[i] = outFile != null && files.Count == 1
                ? outFile
                : Path.Combine(outDir != null ? outDir : Path.GetDirectoryName(Path.GetFullPath(files[i])),
                               Path.GetFileNameWithoutExtension(files[i]) + ".amv");
        }

        // ---- --dry-run 模式: 只打印命令不执行 ----
        if (dryRun)
        {
            opts.DryRun = true;
            int fail = 0;
            foreach (string f in files)
            {
                int idx = files.IndexOf(f);
                AmvResult r = AmvConverter.Run(f, dests[idx], opts, null);
                if (r.ExitCode == 0) Console.WriteLine("将执行: " + r.CommandLine);
                else { Console.WriteLine("[失败] " + f + ": " + r.Error); fail++; }
            }
            return fail > 0 ? 3 : 0;
        }

        // ---- 并行批量 ----
        if (jobs > 1 && files.Count > 1)
        {
            ConcurrentQueue<int> queue = new ConcurrentQueue<int>();
            for (int i = 0; i < files.Count; i++) queue.Enqueue(i);
            int done = 0, fail = 0;
            List<Thread> threads = new List<Thread>();
            for (int t = 0; t < jobs; t++)
            {
                Thread th = new Thread(delegate()
                {
                    while (s_interrupted == 0)
                    {
                        int i;
                        if (!queue.TryDequeue(out i)) break;
                        string f = files[i];
                        try
                        {
                            DateTime t1 = DateTime.Now;
                            AmvResult r = AmvConverter.Run(f, dests[i], opts, null);
                            lock (PrintLock)
                            {
                                if (r.ExitCode == 0)
                                {
                                    done++;
                                    Console.WriteLine("[完成 " + (done + fail) + "/" + files.Count + "] " + f + " → " +
                                        Path.GetFullPath(dests[i]) + "（" + (DateTime.Now - t1).TotalSeconds.ToString("F1") + "s）");
                                }
                                else
                                {
                                    fail++;
                                    Console.WriteLine("[失败 " + (done + fail) + "/" + files.Count + "] " + f + ": " +
                                        (r.Error ?? ("返回码 " + r.ExitCode)));
                                }
                            }
                        }
                        catch (Exception ex)
                        {
                            lock (PrintLock) { fail++; Console.WriteLine("[失败] " + f + ": " + ex.Message); }
                        }
                    }
                });
                th.IsBackground = true;
                threads.Add(th);
                th.Start();
            }
            foreach (Thread th in threads) th.Join();
            if (s_interrupted != 0) { Console.WriteLine("已中断"); return 130; }
            Console.WriteLine("转换完成: 成功 " + done + "，失败 " + fail);
            return fail > 0 ? 3 : 0;
        }

        // ---- 串行（默认, 带进度条）----
        int ok = 0, failCount = 0;
        DateTime t0 = DateTime.Now;
        for (int i = 0; i < files.Count; i++)
        {
            if (s_interrupted != 0) break;
            string f = files[i];
            try
            {
                DateTime t1 = DateTime.Now;
