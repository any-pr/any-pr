namespace SubmitPrCs;

// 仓库更新: fetch 上游 main → 快进本地 main → 推给 fork。
// 别人也在持续 PR，提交前/间隙保持本地与 fork 跟上上游。
public static class Sync
{
    public static bool SyncRepo(string target, string fork, string repoRoot,
        Action<string> log, string? token = null)
    {
        GitOps.GitFetch($"https://github.com/{target}.git", "main", repoRoot);
        var newSha = GitOps.Run(new[] { "git", "rev-parse", "FETCH_HEAD" }, repoRoot).Trim();
        var curSha = GitOps.Run(new[] { "git", "rev-parse", "HEAD" }, repoRoot).Trim();
        if (newSha == curSha)
        {
            log($"本地已是上游最新: {curSha[..10]}");
        }
        else
        {
            var branch = GitOps.Run(new[] { "git", "rev-parse", "--abbrev-ref", "HEAD" },
                repoRoot).Trim();
            if (branch != "main")
                throw new OpException($"当前在 {branch} 分支——请先切回 main 再更新。");
            var n = GitOps.Run(new[] { "git", "rev-list", "--count", $"HEAD..{newSha}" },
                repoRoot).Trim();
            log($"落后 {n} 个提交，快进 {curSha[..10]} → {newSha[..10]}…");
            FastForward(repoRoot, newSha, log);
            log($"已快进 {n} 个提交。");
        }
        var url = token is null ? $"https://github.com/{fork}.git"
            : $"https://x-access-token:{token}@github.com/{fork}.git";
        GitOps.Run(new[] { "git", "-C", repoRoot, "push", url, "main:refs/heads/main" },
            repoRoot);
        log("fork main 已同步。");
        return true;
    }

    // 快进本地 main。工作树的"未提交改动"常是已通过 PR 合并上去的内容:
    // 被挡时逐文件比对 blob——与上游一致的直接收下（内容已在上游，丢弃无损），
    // 不一致的保留原样并报错，由用户决定怎么处理。
    private static void FastForward(string repoRoot, string newSha, Action<string> log)
    {
        for (int i = 1; ; i++)
        {
            try
            {
                GitOps.Run(new[] { "git", "merge", "--ff-only", newSha }, repoRoot);
                return;
            }
            catch (OpException e) when (i <= 2 &&
                 e.Message.Contains("would be overwritten"))
            {
                var diff = GitOps.Run(new[] { "git", "diff", "--name-only",
                    $"HEAD..{newSha}" }, repoRoot);
                var blocked = new List<string>();
                foreach (var p in diff.Split('\n'))
                {
                    var path = p.TrimEnd('\r');
                    if (path.Length == 0) continue;
                    var wtBlob = File.Exists(Path.Combine(repoRoot, path))
                        ? GitOps.Run(new[] { "git", "hash-object", "--", path }, repoRoot).Trim()
                        : "";
                    var upBlob = GitOps.Run(new[] { "git", "rev-parse", $"{newSha}:{path}" },
                        repoRoot).Trim();
                    if (wtBlob.Length > 0 && wtBlob == upBlob)
                    {
                        GitOps.Run(new[] { "git", "checkout", newSha, "--", path }, repoRoot);
                        log($"  已收下与上游一致的本地改动: {path}");
                    }
                    else blocked.Add(path);
                }
                if (blocked.Count > 0)
                    throw new OpException("以下本地改动与上游不同，为避免丢失已中止更新，请先处理:\n  " +
                        string.Join("\n  ", blocked));
            }
        }
    }
}
