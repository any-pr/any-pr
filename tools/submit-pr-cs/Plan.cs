using System.Text.Json;

namespace SubmitPrCs;

// 分类规划（probe 出与机器人一致的行数并规划链）、PR 文案、最终内容验证。
public static class Plan
{
    // 第二步: probe 出每个文件的真实 additions+deletions，超限文件规划链。
    // 返回 (留在普通批的文件, 跳过清单, chains)。豁免文件不计行数。
    public static (List<FileItem>, List<(string, string)>, Dictionary<string, Chain>)
        PlanFileOps(List<FileItem> accepted, string baseSha, string dest,
        string repoRoot, int cap, int maxFileLines, string tmpDir)
    {
        var skipped = new List<(string, string)>();
        var chains = new Dictionary<string, Chain>();
        var (ns, ignored) = GitOps.ProbeChanges(repoRoot, baseSha, dest, accepted);
        foreach (var f in accepted)
        {
            if (ignored.Contains(f.Path))
            {
                skipped.Add((f.Path, "被 .gitignore 忽略（any-pr 或项目自身的排除规则）"));
                continue;
            }
            (f.Adds, f.Dels) = ns.TryGetValue(f.Path, out var v) ? v : (0, 0);
            int diff = f.Adds + f.Dels;
            if (Rules.ExcludedPath(f.Path)) continue;
            if (diff <= maxFileLines)
            {
                if (diff == 0)
                    skipped.Add((f.Path, "与上游 main 内容完全相同，无需提交"));
                continue;
            }
            var old = Chains.BaseContent(baseSha, f.Path, repoRoot);
            if (old == null)  // 新建 → 渐进创建链
                chains[f.Path] = new Chain { Kind = "create",
                    Steps = Chains.BuildChunkChain(f, cap, tmpDir) };
            else if (Array.IndexOf(old, (byte)0) >= 0)  // 旧内容为二进制
                skipped.Add((f.Path, "修改前内容为二进制，暂不支持拆分修改，请手动处理"));
            else  // 修改 → 截断+追加链
                chains[f.Path] = new Chain { Kind = "modify",
                    Steps = Chains.BuildModifyChain(f, old, cap, tmpDir) };
        }
        var skipSet = new HashSet<string>(skipped.Select(s => s.Item1));
        var left = accepted.Where(f => !chains.ContainsKey(f.Path)
            && !skipSet.Contains(f.Path)).ToList();
        return (left, skipped, chains);
    }

    public static (string, string) BuildBatchText(List<FileItem> batch, int i, int n,
        string destLabel, string? userTitle, string? userBody, int maxLines,
        int maxFiles)
    {
        var title = userTitle ?? $"{destLabel}: add {batch.Count} file(s)";
        if (n > 1) title = $"{title} (part {i}/{n})";
        var rows = string.Join("\n", batch.Select(it =>
            $"| `{it.Path}` | {it.Adds + it.Dels} |"));
        var total = batch.Sum(Rules.CountedLines);
        var body = userBody ?? (
            $"## Summary\n\nAdds {batch.Count} file(s) into `{destLabel}/`" +
            (n > 1 ? $" — batch {i}/{n}, auto-split to respect the {maxLines}-line" +
            $" / {maxFiles}-file limits of the auto-merge regulations." : ".") +
            $"\n\n| file | changed lines |\n|---|---|\n{rows}\n\n" +
            $"**{total} counted lines** total. Pre-validated locally by " +
            $"`submit-pr-cs` against every gate rule.\n");
        return (title, body);
    }

    public static (string, string) BuildStepText(Chain ch, FileItem c, int k, int n,
        int cap, string destLabel, string? userTitle, string? userBody, int done)
    {
        var suffix = n > 1 ? $" (chunk {k}/{n})" : "";
        string title;
        string body;
        if (ch.Kind == "create")
        {
            title = userTitle != null ? userTitle + suffix
                : $"{destLabel}: add {c.Rel}{suffix}";
            body = userBody ?? (
                $"Progressively creates `{c.Path}` in {n} chunks (≤{cap} lines each) " +
                $"to satisfy the per-file limit of the auto-merge regulations. " +
                $"Chunk {k}/{n}: the file now holds its first {done} lines.\n\n" +
                "Pre-validated locally by `submit-pr-cs`.\n");
        }
        else if (ch.Kind == "modify")
        {
            title = userTitle != null ? userTitle + suffix
                : $"{destLabel}: update {c.Rel}{suffix}";
            body = userBody ?? (
                $"Progressively rewrites `{c.Path}` in {n} steps (≤{cap} lines each) " +
                $"to satisfy the per-file limit of the auto-merge regulations. " +
                $"Step {k}/{n}.\n\nPre-validated locally by `submit-pr-cs`.\n");
        }
        else
        {
            title = userTitle != null ? userTitle + suffix
                : $"remove {c.Path}{suffix}";
            body = userBody ?? (
                $"Progressively removes `{c.Path}` in {n} steps (≤{cap} lines each) " +
                $"to satisfy the per-file limit of the auto-merge regulations. " +
                $"Step {k}/{n}: {(k < n ? "file truncated" : "file removed")}.\n\n" +
                "Pre-validated locally by `submit-pr-cs`.\n");
        }
        return (title, body);
    }

    // 最终验证: 按 blob SHA 逐文件比对上游 main 上的最终状态。
    // 验证条目: 普通批全部文件；链只看末步（创建/改写=原文件）；删除=应不存在。
    public static List<string> VerifyOnMain(string target, string repoRoot,
        List<FileItem> submitted, Action<string> log)
    {
        GitOps.GitFetch($"https://github.com/{target}.git", "main", repoRoot);
        var tree = GitOps.Run(new[] { "git", "ls-tree", "-r", "FETCH_HEAD" }, repoRoot);
        var shaByPath = new Dictionary<string, string>();
        foreach (var ln in tree.Split('\n'))
        {
            var ix = ln.IndexOf('\t');
            if (ix > 0) shaByPath[ln[(ix + 1)..].TrimEnd('\r')] = ln[..ix].Split()[2];
        }
        var problems = new List<string>();
        foreach (var it in submitted)
        {
            if (it.Delete)
            {
                if (shaByPath.ContainsKey(it.Path))
                    problems.Add($"{it.Path}（应已删除却仍存在）");
                continue;
            }
            if (!shaByPath.ContainsKey(it.Path))
            {
                problems.Add($"{it.Path}（未出现在上游 main 上）");
                continue;
            }
            var local = GitOps.Run(new[] { "git", "hash-object", it.Src },
                repoRoot).Trim();
            if (local != shaByPath[it.Path])
                problems.Add($"{it.Path}（内容与提交内容不一致）");
        }
        if (problems.Count == 0)
            log($"  已验证: 已提交的 {submitted.Count} 个文件的内容都与上游 main 一致。");
        return problems;
    }
}
