namespace SubmitPrGui;

internal static class Program
{
    [STAThread]
    private static int Main(string[] args)
    {
        if (args.Length > 0 && args[0] == "--selftest") return SelfTest(args);
        // 可选位置参数: 预填仓库根目录（启动脚本传参用）
        var root = args.Length > 0 && args[0] != "--selftest"
            && Directory.Exists(args[0]) ? args[0] : null;
        ApplicationConfiguration.Initialize();
        Application.Run(new MainForm(root));
        return 0;
    }

    // 无头自测: 临时夹具走一遍 dry-run（含一个必被门禁跳过的 .log），
    // 校验 GUI→核心桥接与事件流是否畅通，打印全部日志/事件后以退出码报告。
    // 可选第二个参数指定仓库根目录（独立于仓库存放时必须传，默认取当前目录）。
    private static int SelfTest(string[] args)
    {
        var root = args.Length > 1 && Directory.Exists(args[1])
            ? args[1] : Environment.CurrentDirectory;
        var dir = Path.Combine(Path.GetTempPath(), "sp-gui-selftest");
        Directory.CreateDirectory(dir);
        File.WriteAllText(Path.Combine(dir, "a.txt"), "selftest A\n");
        File.WriteAllText(Path.Combine(dir, "b.txt"), "selftest B\nline2\n");
        File.WriteAllText(Path.Combine(dir, "junk.log"), "should be skipped\n");
        var g = new GuiSettings
        {
            RepoRoot = root,
            Sources = new List<string> { dir },
            Dest = "misc/sp-gui-selftest",
            DryRun = true,
            Repo = "any-pr/any-pr",
            Fork = "snowy-yang/any-pr",
        };
        int evCount = 0;
        var (ok, err) = RunHost.Run(g,
            s => Console.WriteLine("LOG " + s),
            e => { evCount++; Console.WriteLine($"EV  [{e.Unit}] {e.Kind}: {e.Text}"); });
        Console.WriteLine($"selftest: ok={ok} events={evCount} err={err?.Message}");
        try { Directory.Delete(dir, true); } catch { /* 尽力清理 */ }
        return ok && err == null && evCount > 0 ? 0 : 1;
    }
}
