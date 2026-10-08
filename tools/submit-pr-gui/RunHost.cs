using SubmitPrCs;

namespace SubmitPrGui;

// UI 收集的参数（控件无关，便于无头自测）
public class GuiSettings
{
    public string RepoRoot = "";  // 仓库根目录；RunPlan 在这里解析 fork/target
    public List<string> Sources = new();
    public List<string> Delete = new();
    public string Dest = ".", Title = "", Body = "", Repo = "", Fork = "";
    public int Workers, PollTimeout = 300;
    public bool Strict, DryRun;
}

// UI 无关的运行桥: GuiSettings → Opts → Runner.RunPlan（在后台线程调用）。
// log/ev 回调来自任意线程，UI 侧自行编组；异常在这里兜住不打断后台线程。
public static class RunHost
{
    public static (bool Ok, Exception? Error) Run(GuiSettings g,
        Action<string> log, Action<Ev> ev)
    {
        var old = Environment.CurrentDirectory;
        try
        {
            if (Directory.Exists(g.RepoRoot)) Environment.CurrentDirectory = g.RepoRoot;
            var o = new Opts
            {
                Sources = g.Sources,
                Delete = g.Delete.Count > 0 ? g.Delete : null,
                Dest = string.IsNullOrWhiteSpace(g.Dest) ? "." : g.Dest.Trim(),
                Title = NullIfBlank(g.Title),
                Body = NullIfBlank(g.Body),
                Repo = NullIfBlank(g.Repo),
                Fork = NullIfBlank(g.Fork),
                Workers = g.Workers,
                PollTimeout = g.PollTimeout,
                Strict = g.Strict,
                DryRun = g.DryRun,
                OnEvent = ev,
            };
            return (Runner.RunPlan(o, log), null);
        }
        catch (Exception e)
        {
            return (false, e);
        }
        finally
        {
            Environment.CurrentDirectory = old;
        }
    }

    private static string? NullIfBlank(string s) =>
        string.IsNullOrWhiteSpace(s) ? null : s.Trim();
}
