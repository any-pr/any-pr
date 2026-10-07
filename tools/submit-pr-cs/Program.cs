namespace SubmitPrCs;

// CLI 入口: 手工解析参数（零依赖）→ RunPlan → 退出码。
public class Opts
{
    public List<string> Sources = new();
    public List<string>? Delete;
    public string Dest = ".";
    public string? Repo, Fork, Title, Body;
    public string BranchPrefix = "auto-pr";
    public int? MaxLines, MaxFiles, MaxFileLines;
    public int PollTimeout = 300, PollInterval = 15, MaxRetries = 3, Workers = 0;
    public bool Strict, DryRun;
}

public static class Program
{
    public static int Main(string[] args)
    {
        try { Console.OutputEncoding = System.Text.Encoding.UTF8; } catch { /* 忽略 */ }
        Opts o;
        try
        {
            o = Parse(args);
        }
        catch (OpException e)
        {
            Console.Error.WriteLine(e.Message);
            return 2;
        }
        try
        {
            var ok = Runner.RunPlan(o, s => Console.WriteLine(s));
            return ok ? 0 : 1;
        }
        catch (OpException e)
        {
            Console.Error.WriteLine($"错误: {e.Message}");
            return 1;
        }
        catch (Exception e)
        {
            Console.Error.WriteLine($"错误: {e}");
            return 1;
        }
    }

    private static Opts Parse(string[] args)
    {
        var o = new Opts();
        for (int i = 0; i < args.Length; i++)
        {
            var a = args[i];
            switch (a)
            {
                case "--delete":
                    o.Delete = new List<string>();
                    while (i + 1 < args.Length && !args[i + 1].StartsWith("--"))
                        o.Delete.Add(args[++i]);
                    if (o.Delete.Count == 0) throw new OpException("--delete 需要至少一个路径");
                    break;
                case "--dest": o.Dest = Next(args, ref i, a); break;
                case "--repo": o.Repo = Next(args, ref i, a); break;
                case "--fork": o.Fork = Next(args, ref i, a); break;
                case "--title": o.Title = Next(args, ref i, a); break;
                case "--body": o.Body = Next(args, ref i, a); break;
                case "--branch-prefix": o.BranchPrefix = Next(args, ref i, a); break;
                case "--max-lines": o.MaxLines = int.Parse(Next(args, ref i, a)); break;
                case "--max-files": o.MaxFiles = int.Parse(Next(args, ref i, a)); break;
                case "--max-file-lines": o.MaxFileLines = int.Parse(Next(args, ref i, a)); break;
                case "--poll-timeout": o.PollTimeout = int.Parse(Next(args, ref i, a)); break;
                case "--poll-interval": o.PollInterval = int.Parse(Next(args, ref i, a)); break;
                case "--max-retries": o.MaxRetries = int.Parse(Next(args, ref i, a)); break;
                case "--workers": o.Workers = int.Parse(Next(args, ref i, a)); break;
                case "--strict": o.Strict = true; break;
                case "--dry-run": o.DryRun = true; break;
                case "-h" or "--help": throw new OpException(Help);
                default:
                    if (a.StartsWith("--"))
                        throw new OpException($"未知参数: {a}\n{Help}");
                    o.Sources.Add(a);
                    break;
            }
        }
        return o;
    }

    private static string Next(string[] args, ref int i, string flag)
    {
        if (i + 1 >= args.Length) throw new OpException($"{flag} 缺少参数值");
        return args[++i];
    }

    private const string Help =
        "用法: submit-pr-cs <源文件或目录...> --dest <仓库内目录> [选项]\n" +
        "      submit-pr-cs --delete <仓库内路径...> [选项]\n" +
        "  --title/--body           PR 标题与描述（默认自动生成）\n" +
        "  --max-lines/--max-files/--max-file-lines  收紧上限（默认=机器人上限）\n" +
        "  --workers N              并发数（默认全部并发, 上限 10）\n" +
        "  --poll-timeout/--poll-interval/--max-retries  轮询与重试参数\n" +
        "  --strict                 有跳过文件时中止\n" +
        "  --dry-run                只打印计划不提交";
}
