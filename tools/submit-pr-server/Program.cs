using System.Net;
using System.Text;
using System.Text.Json;
using SubmitPrCs;

namespace SubmitPrServer;

// 本地 HTTP 服务（只绑 127.0.0.1）:
//   GET  /accounts          账号列表（绝不下发 token）
//   GET  /jobs              最近作业列表
//   GET  /events?job=&after=  作业条目增量拉取（日志+进度事件）
//   POST /submit            入队提交作业（body 见 SubmitReq）
//   POST /sync              入队"更新仓库"作业（fetch 上游→ff main→推 fork）
public class SubmitReq
{
    public string? Account { get; set; }
    public string RepoRoot { get; set; } = ".";
    public List<string> Sources { get; set; } = new();
    public List<string>? Delete { get; set; }
    public string Dest { get; set; } = ".";
    public string? Title { get; set; }
    public string? Body { get; set; }
    public string? Repo { get; set; }
    public string? Fork { get; set; }
    public int Workers { get; set; }
    public int PollTimeout { get; set; } = 300;
    public bool Strict { get; set; }
    public bool DryRun { get; set; }
    public bool NoWait { get; set; } = true;
}

public class SyncReq
{
    public string? Account { get; set; }
    public string RepoRoot { get; set; } = ".";
}

internal static class Program
{
    private static readonly JsonSerializerOptions J = new(JsonSerializerDefaults.Web);
    private static List<Account> _accounts = new();

    private static int Main(string[] args)
    {
        try { Console.OutputEncoding = Encoding.UTF8; } catch { /* 忽略 */ }
        var port = args.Length > 1 && args[0] == "--port" && int.TryParse(args[1], out var p)
            ? p : 8642;
        _accounts = Accounts.Load();
        Jobs.StartWorker();
        var listener = new HttpListener();
        listener.Prefixes.Add($"http://127.0.0.1:{port}/");
        listener.Start();
        Console.WriteLine($"submit-pr-server 就绪: http://127.0.0.1:{port}/ " +
            $"（账号 {string.Join(", ", _accounts.Select(a => a.Name))}）");
        while (true)
        {
            var ctx = listener.GetContext();
            try
            {
                Handle(ctx);
            }
            catch (Exception e)
            {
                Write(ctx, 500, new { error = e.Message });
            }
        }
    }

    private static void Handle(HttpListenerContext ctx)
    {
        var req = ctx.Request;
        var path = req.Url!.AbsolutePath.TrimEnd('/');
        switch (path, req.HttpMethod)
        {
            case ("/accounts", "GET"):
                Write(ctx, 200, new
                {
                    accounts = _accounts.Select(a => new
                    { name = a.Name, fork = a.Fork, target = a.Target })
                });
                break;
            case ("/jobs", "GET"):
                Write(ctx, 200, new
                {
                    jobs = Jobs.AllList().Select(j => new
                    { id = j.Id, kind = j.Kind, label = j.Label, status = j.Status,
                      ok = j.Ok, error = j.Error })
                });
                break;
            case ("/events", "GET"):
                var id = Q(req, "job");
                var job = Jobs.Find(id) ?? throw new InvalidOperationException(
                    $"作业不存在: {id}");
                long after = long.TryParse(Q(req, "after"), out var a) ? a : 0;
                Write(ctx, 200, new
                {
                    id = job.Id, status = job.Status, ok = job.Ok, error = job.Error,
                    entries = job.After(after),
                });
                break;
            case ("/submit", "POST"):
                var sr = Read<SubmitReq>(ctx) ?? throw new InvalidOperationException(
                    "请求体不是合法的提交参数。");
                var acc = Account(sr.Account);
                Jobs.Enqueue("submit",
                    $"{acc?.Name ?? "gh 登录态"} → {sr.Dest}" +
                    (sr.DryRun ? "（dry-run）" : ""),
                    j => RunSubmit(j, sr, acc));
                Write(ctx, 200, new { jobId = LastId() });
                break;
            case ("/sync", "POST"):
                var y = Read<SyncReq>(ctx) ?? new SyncReq();
                var acc2 = Account(y.Account);
                Jobs.Enqueue("sync", $"更新仓库 {y.RepoRoot}",
                    j => RunSync(j, y, acc2));
                Write(ctx, 200, new { jobId = LastId() });
                break;
            default:
                Write(ctx, 404, new { error = $"未知路径: {req.HttpMethod} {path}" });
                break;
        }
    }

    private static string? LastId() => Jobs.AllList().LastOrDefault()?.Id;

    private static Account? Account(string? name)
    {
        if (string.IsNullOrEmpty(name)) return null;  // gh 登录态
        return _accounts.FirstOrDefault(a => a.Name == name)
            ?? throw new InvalidOperationException($"accounts.json 里没有账号: {name}");
    }

    private static void RunSubmit(Job j, SubmitReq r, Account? acc)
    {
        ApplyAccount(acc);
        var o = new Opts
        {
            Sources = r.Sources,
            Delete = r.Delete is { Count: > 0 } ? r.Delete : null,
            Dest = string.IsNullOrWhiteSpace(r.Dest) ? "." : r.Dest.Trim(),
            Title = BlankToNull(r.Title),
            Body = BlankToNull(r.Body),
            Repo = acc?.Target ?? BlankToNull(r.Repo),
            Fork = acc?.Fork ?? BlankToNull(r.Fork),
            Workers = r.Workers,
            PollTimeout = r.PollTimeout,
            Strict = r.Strict,
            DryRun = r.DryRun,
            NoWait = r.NoWait,
            PushToken = NonEmpty(acc?.Token),
            OnEvent = ev => j.Add(ev.Unit, ev.Kind, ev.Text, false, ev.Units, ev.Steps),
        };
        WithCwd(r.RepoRoot, () => Runner.RunPlan(o, s => j.Add("", "", s, true)));
    }

    private static void RunSync(Job j, SyncReq r, Account? acc)
    {
        ApplyAccount(acc);
        var repoRoot = GitOps.Run(new[] { "git", "-C", r.RepoRoot,
            "rev-parse", "--show-toplevel" }).Trim();
        var fork = acc?.Fork ?? "snowy-yang/any-pr";
        var target = acc?.Target ?? "any-pr/any-pr";
        Sync.SyncRepo(target!, fork, repoRoot, s => j.Add("", "", s, true),
            NonEmpty(acc?.Token));
    }

    // 多账号注入: gh 走 GH_TOKEN；push 用 Opts.PushToken（RunSync 直接传参）
    private static void ApplyAccount(Account? acc)
    {
        GitOps.GhEnv = NonEmpty(acc?.Token) is { } t
            ? new Dictionary<string, string> { ["GH_TOKEN"] = t } : null;
    }

    private static void WithCwd(string dir, Func<bool> f)
    {
        var old = Environment.CurrentDirectory;
        try
        {
            if (Directory.Exists(dir)) Environment.CurrentDirectory = dir;
            if (!f()) throw new InvalidOperationException("作业未全部合并（详见事件流）");
        }
        finally { Environment.CurrentDirectory = old; }
    }

    private static string? NonEmpty(string? s) =>
        string.IsNullOrWhiteSpace(s) ? null : s.Trim();

    private static string? BlankToNull(string? s) =>
        string.IsNullOrWhiteSpace(s) ? null : s.Trim();

    private static string Q(HttpListenerRequest r, string key)
        => r.QueryString[key] ?? "";

    private static T? Read<T>(HttpListenerContext ctx) where T : class
    {
        using var rd = new StreamReader(ctx.Request.InputStream, Encoding.UTF8);
        var body = rd.ReadToEnd();
        return body.Length == 0 ? null
            : JsonSerializer.Deserialize<T>(body, J);
    }

    private static void Write(HttpListenerContext ctx, int code, object payload)
    {
        var bytes = JsonSerializer.SerializeToUtf8Bytes(payload, J);
        ctx.Response.StatusCode = code;
        ctx.Response.ContentType = "application/json; charset=utf-8";
        ctx.Response.ContentLength64 = bytes.Length;
        ctx.Response.OutputStream.Write(bytes);
        ctx.Response.OutputStream.Close();
    }
}
