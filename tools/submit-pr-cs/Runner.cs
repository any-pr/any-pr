namespace SubmitPrCs;

// 提交编排: 单批提交循环（停滞自动强推促发）+ 总流程 RunPlan。
public static class Runner
{
    private const int NudgeAfter = 120;  // PR 无进展多少秒后强推促发重新检查

    public class StepResult { public int Pr; public string Result = "", Url = ""; }

    // 提交一批文件并等待合并。各批文件路径不相交，可并发提交/合并。
    // 机器人有两种情况需要重新触发检查（重试上限内各换一次新基底强推）:
    //   - PR 因 base 变动合并冲突被关 / 被评论要求 rebase；
    //   - 与其他 PR 的合并竞态导致其静默退出，PR 永远停在 OPEN。
    public static StepResult SubmitBatch(string repoRoot, string target,
        string fork, string baseSha, string branch, List<FileItem> batch,
        string dest, string title, string body, int pollTimeout,
        int pollInterval, int maxRetries, Action<string> log)
    {
        int? pr = null;
        var baseNow = baseSha;
        for (int attempt = 1; attempt <= maxRetries; attempt++)
        {
            var wt = GitOps.BuildWorktreeCommit(repoRoot, baseNow, dest, batch, title);
            try
            {
                GitHubOps.PushBranch(fork, branch, wt, force: pr != null);
            }
            finally
            {
                GitOps.DropWorktree(repoRoot, wt);
            }
            var lastPush = DateTime.UtcNow;

            pr ??= GitHubOps.GetOpenPr(target, fork, branch);
            if (pr == null)
            {
                GitHubOps.WaitForBranch(fork, branch);
                var url = GitHubOps.CreatePr(target, fork, branch, title, body);
                pr = int.Parse(url.TrimEnd('/').Split('/')[^1]);
                log($"[{branch}] PR #{pr}: {url}");
            }

            var deadline = DateTime.UtcNow.AddSeconds(pollTimeout);
            while (true)
            {
                var st = GitHubOps.PrStateTolerant(target, pr.Value);
                if (st == "MERGED")
                    return new StepResult { Pr = pr.Value, Result = "merged",
                        Url = $"https://github.com/{target}/pull/{pr}" };
                if (st == "CLOSED")
                {
                    var comment = GitHubOps.LastComment(target, pr.Value);
                    if (comment.ToLower().Contains("conflict") && attempt < maxRetries)
                    {
                        log($"[{branch}] 与 main 冲突，换新基底重试 " +
                            $"({attempt}/{maxRetries})…");
                        baseNow = GitOps.LatestMainSha(target, repoRoot);
                        break;
                    }
                    throw new OpException(
                        $"PR #{pr} 被机器人关闭:\n{(comment.Length > 0 ? comment : "(无评论)")}");
                }
                if (attempt < maxRetries &&
                    (DateTime.UtcNow - lastPush).TotalSeconds > NudgeAfter)
                {
                    log($"[{branch}] {NudgeAfter}s 仍无进展（可能与其他 PR 的合并" +
                        $"竞态），换新基底强推 ({attempt}/{maxRetries})…");
                    baseNow = GitOps.LatestMainSha(target, repoRoot);
                    break;
                }
                if (DateTime.UtcNow >= deadline)
                    return new StepResult { Pr = pr.Value, Result = "timeout",
                        Url = $"https://github.com/{target}/pull/{pr}" };
                Thread.Sleep(pollInterval * 1000);
            }
        }
        throw new OpException($"PR #{pr} 重试 {maxRetries} 次仍未合并");
    }

    // 完整提交流程。返回 true=全部合并且最终内容验证通过。
    public static bool RunPlan(Opts o, Action<string> log)
    {
        int maxLines = Math.Min(o.MaxLines ?? Rules.MaxChangedLines, Rules.MaxChangedLines);
        int maxFiles = Math.Min(o.MaxFiles ?? Rules.MaxChangedFiles, Rules.MaxChangedFiles);
        int maxFileLines = Math.Min(o.MaxFileLines ?? Rules.MaxFileLines, Rules.MaxFileLines);
        int cap = Math.Min(maxFileLines, maxLines);
        var dest = (o.Dest ?? "").Trim('/');
        dest = dest is "" or "." ? "" : dest;
        var destLabel = dest.Length > 0 ? dest : "root";
        if (dest.Length > 0 && Rules.GatePathProblems(dest + "/x").Count > 0)
            throw new OpException($"目标目录 {dest} 命中受保护规则。");

        // 预检: git/gh 可用性提前到做任何规划之前，失败给出明确指引
        Preflight();
        var repoRoot = GitOps.Run(new[] { "git", "rev-parse", "--show-toplevel" }).Trim();
        var (fork, target) = ResolveForkTarget(o);
        log($"上游仓库: {target}   分支推送目标: {fork}");

        var delete = o.Delete;
        var sources = delete == null ? Chains.CollectSources(o.Sources) : new List<FileItem>();
        if (delete != null && sources.Count > 0)
            throw new OpException("--delete 模式下不要同时传源文件/目录。");
        if (sources.Count == 0 && (delete == null || delete.Count == 0))
            throw new OpException("没有要提交的源文件/目录，也没有 --delete 目标。");

        var baseSha = GitOps.LatestMainSha(target, repoRoot);
        log($"基于上游 main: {baseSha[..10]}");

        var tmpDir = Path.Combine(repoRoot, ".git", "submit-pr-tmp",
            DateTime.UtcNow.ToString("yyyyMMdd-HHmmss"));
        try
        {
            var accepted = new List<FileItem>();
            var skipped = new List<(string Path, string Why)>();
            var chains = new Dictionary<string, Chain>();
            var deleteBatches = new List<List<FileItem>>();
            if (delete != null)
            {
                // 删除模式: 单步可删的文件打平成批量删除批，需要渐进截断的走链
                var simpleDeletes = new List<FileItem>();
                foreach (var raw in delete)
                {
                    var p = raw.Replace('\\', '/').Trim('/');
                    var probs = Rules.GatePathProblems(p);
                    if (probs.Count > 0) { skipped.Add((p, string.Join("；", probs))); continue; }
                    var old = Chains.BaseContent(baseSha, p, repoRoot);
                    if (old == null) { skipped.Add((p, "上游 main 上不存在，无需删除")); continue; }
                    var chain = Chains.BuildDeleteChain(new FileItem { Rel = p, Path = p },
                        old, cap, tmpDir);
                    if (chain.Count == 1) simpleDeletes.Add(chain[0]);
                    else chains[p] = new Chain { Kind = "delete", Steps = chain };
                }
                deleteBatches = Rules.MakeBatches(simpleDeletes, maxLines, maxFiles);
            }
            else
            {
                foreach (var f in sources)
                {
                    f.Path = dest.Length > 0 ? $"{dest}/{f.Rel}" : f.Rel;
                    var probs = Rules.GatePathProblems(f.Path);
                    if (probs.Count > 0) skipped.Add((f.Path, string.Join("；", probs)));
                    else if (Rules.IsBinary(f.Src))
                        skipped.Add((f.Path, "二进制内容（含 NUL 字节）"));
                    else accepted.Add(f);
                }
                var (left, sk, ch) = Plan.PlanFileOps(accepted, baseSha, dest, repoRoot,
                    cap, maxFileLines, tmpDir);
                accepted = left;
                skipped.AddRange(sk);
                chains = ch;
            }

            foreach (var (path, why) in skipped) log($"  [跳过] {path} — {why}");
            if (skipped.Count > 0 && o.Strict)
                throw new OpException("strict 模式下存在被跳过的文件，已中止。");
            if (accepted.Count == 0 && chains.Count == 0 && deleteBatches.Count == 0)
            {
                log("没有需要提交的内容（可能全部与上游一致）。");
                return true;
            }

            var kindName = new Dictionary<string, string>
            {
                ["create"] = "分块链", ["modify"] = "改写链", ["delete"] = "删除链"
            };
            var batches = Rules.MakeBatches(accepted, maxLines, maxFiles);
            for (int i = 0; i < batches.Count; i++)
            {
                var b = batches[i];
                log($"  PR {i + 1}/{batches.Count}: {b.Count} 个文件, " +
                    $"{b.Sum(Rules.CountedLines)} 行 — {string.Join(", ", b.Select(x => x.Path))}");
            }
            foreach (var (path, ch) in chains)
            {
                var total = ch.Steps.Sum(s => s.Adds + s.Dels);
                log($"  {kindName[ch.Kind]} {path}: 共 {total} 行变更 → " +
                    $"{ch.Steps.Count} 个渐进 PR（同链串行，链间并发）");
            }
            if (deleteBatches.Count > 0)
                log($"  批量删除: {deleteBatches.Sum(b => b.Count)} 个文件 → " +
                    $"{deleteBatches.Count} 个 PR");
            if (o.DryRun)
            {
                log("[dry-run] 未真正提交。");
                return true;
            }

            return Execute.ExecutePlan(o, log, target, fork, baseSha, dest,
                destLabel, batches, chains, deleteBatches, cap, repoRoot, tmpDir,
                maxLines, maxFiles, kindName);
        }
        finally
        {
            Cleanup(tmpDir);
        }
    }

    // 预检依赖与凭据: 没有它们时第一步就会以晦涩的进程错误失败，
    // 提前在这里换成可操作的提示（装 git / gh auth login）。
    private static void Preflight()
    {
        try { GitOps.Run(new[] { "git", "--version" }); }
        catch (Exception)
        {
            throw new OpException("找不到可用的 git，请先安装 git 并加入 PATH。");
        }
        try { GitOps.Gh("auth", "status"); }
        catch (OpException e)
        {
            throw new OpException($"gh 未认证或不可用，请先执行 gh auth login:\n{e.Message}");
        }
    }

    private static (string Fork, string Target) ResolveForkTarget(Opts o)
    {
        if (o.Fork != null) return (o.Fork, o.Repo ?? o.Fork);
        using var doc = System.Text.Json.JsonDocument.Parse(GitOps.Gh("repo",
            "view", "--json", "nameWithOwner,parent"));
        var root = doc.RootElement;
        var fork = root.GetProperty("nameWithOwner").GetString()!;
        var parent = root.GetProperty("parent");
        var target = parent.ValueKind == System.Text.Json.JsonValueKind.Undefined
            ? fork
            : $"{parent.GetProperty("owner").GetProperty("login").GetString()}" +
              $"/{parent.GetProperty("name").GetString()}";
        return (fork, target);
    }

    private static void Cleanup(string tmpDir)
    {
        try { Directory.Delete(tmpDir, true); } catch { /* 尽力清理 */ }
    }
}
