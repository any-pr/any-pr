using System.Text.Json;

namespace SubmitPrCs;

// GitHub 平台操作: 分支推送（常规→直连→常规 降级）/ PR 创建（读副本延迟容错）/ 轮询。
public static class GitHubOps
{
    // gh 的跨仓库 --head 格式是 owner:branch，不是 owner/repo:branch
    public static string HeadSpec(string target, string fork, string branch) =>
        fork == target ? branch : $"{fork.Split('/')[0]}:{branch}";

    // push 幂等（同分支同内容），按"常规→直连→常规"退避重试兜底代理抖动
    public static void PushBranch(string fork, string branch, string wt, bool force)
    {
        string[][] variants = { Array.Empty<string>(),
            new[] { "-c", "http.proxy=", "-c", "https.proxy=" }, Array.Empty<string>() };
        var url = $"https://github.com/{fork}.git";
        for (int i = 0; i < variants.Length; i++)
        {
            var cmd = new List<string> { "git", "-C", wt };
            cmd.AddRange(variants[i]);
            cmd.AddRange(new[] { "push", url, $"HEAD:refs/heads/{branch}" });
            if (force) cmd.Add("--force");
            try
            {
                GitOps.Run(cmd);
                return;
            }
            catch (OpException e)
            {
                if (i == variants.Length - 1) throw;
                Console.WriteLine($"[{branch}] push 失败，{5 * (i + 1)}s 后重试 " +
                                  $"({i + 1}/{variants.Length})…: {LastLine(e.Message)}");
                Thread.Sleep(5000 * (i + 1));
            }
        }
    }

    private static string LastLine(string s)
    {
        var lines = s.Split('\n');
        return lines[^1].Trim();
    }

    // push 后 GraphQL 读副本可能短暂看不到新分支，等 REST API 可见再开 PR
    public static void WaitForBranch(string fork, string branch, int timeoutSec = 90)
    {
        var deadline = DateTime.UtcNow.AddSeconds(timeoutSec);
        while (DateTime.UtcNow < deadline)
        {
            try
            {
                GitOps.Gh("api", $"repos/{fork}/branches/{branch}", "--jq", ".name");
                return;
            }
            catch (OpException)
            {
                Thread.Sleep(3000);
            }
        }
        throw new OpException($"分支 {branch} 推送后 {timeoutSec}s 内未在 {fork} 上可见");
    }

    public static int? GetOpenPr(string target, string fork, string branch)
    {
        var outp = GitOps.Gh("pr", "list", "--repo", target, "--head",
            HeadSpec(target, fork, branch), "--state", "open", "--json", "number",
            "--jq", ".[0].number // empty");
        return outp.Length > 0 ? int.Parse(outp) : null;
    }

    // 创建 PR；对读副本延迟导致的瞬时报错重试
    public static string CreatePr(string target, string fork, string branch,
        string title, string body, int attempts = 3)
    {
        var head = HeadSpec(target, fork, branch);
        OpException? last = null;
        for (int i = 1; i <= attempts; i++)
        {
            try
            {
                return GitOps.Gh("pr", "create", "--repo", target, "--base", "main",
                    "--head", head, "--title", title, "--body", body);
            }
            catch (OpException e)
            {
                last = e;
                var m = e.Message;
                if ((m.Contains("Head sha") || m.Contains("not all refs are readable")
                     || m.Contains("No commits between")) && i < attempts)
                {
                    Thread.Sleep(10000);
                    continue;
                }
                throw;
            }
        }
        throw last!;
    }

    public static string PrState(string target, int pr) =>
        JsonDocument.Parse(GitOps.Gh("pr", "view", pr.ToString(), "--repo", target,
            "--json", "state")).RootElement.GetProperty("state").GetString()!;

    public static string LastComment(string target, int pr)
    {
        try
        {
            using var doc = JsonDocument.Parse(GitOps.Gh("pr", "view",
                pr.ToString(), "--repo", target, "--json", "comments"));
            var arr = doc.RootElement.GetProperty("comments");
            if (arr.GetArrayLength() == 0) return "";
            return arr[arr.GetArrayLength() - 1].GetProperty("body").GetString() ?? "";
        }
        catch (OpException)
        {
            return "";
        }
    }
}
