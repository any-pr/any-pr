namespace SubmitPrGui;

internal static class Program
{
    [STAThread]
    private static int Main(string[] args)
    {
        if (args.Length > 0 && args[0] == "--selftest") return SelfTest();
        ApplicationConfiguration.Initialize();
        Application.Run(new MainForm());
        return 0;
    }

    // 无头自测: 临时夹具走一遍 dry-run（含一个必被门禁跳过的 .log），
    // 校验 GUI→核心桥接与事件流是否畅通，打印全部日志/事件后以退出码报告。
    private static int SelfTest()
    {
        var dir = Path.Combine(Path.GetTempPath(), "sp-gui-selftest");
        Directory.CreateDirectory(dir);
        File.WriteAllText(Path.Combine(dir, "a.txt"), "selftest A\n");
        File.WriteAllText(Path.Combine(dir, "b.txt"), "selftest B\nline2\n");
        File.WriteAllText(Path.Combine(dir, "junk.log"), "should be skipped\n");
        var g = new GuiSettings
        {
            RepoRoot = Environment.CurrentDirectory,
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
