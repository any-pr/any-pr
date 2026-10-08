namespace SubmitPrCs;

// 并发执行: 任务单元（普通批 / 渐进链 / 批量删除）并行跑，最终验证上游内容。
public static class Execute
{
    private class UnitState
    {
        public string Label = "";
        public List<Step> Steps = new();
        public List<FileItem> Verify = new();  // 最终验证条目（普通批=全部文件）
        public List<Runner.StepResult> Results = new();
        public string? Error;
    }

    public static bool ExecutePlan(Opts o, Action<string> log, string target,
        string fork, string baseSha, string dest, string destLabel,
        List<List<FileItem>> batches, Dictionary<string, Chain> chains,
        List<List<FileItem>> deleteBatches, int cap, string repoRoot,
        string tmpDir, int maxLines, int maxFiles,
        Dictionary<string, string> kindName)
    {
        var ts = DateTime.UtcNow.ToString("yyyyMMdd-HHmmss");
        var prefix = o.BranchPrefix;
        var units = new List<UnitState>();

        for (int i = 0; i < batches.Count; i++)
        {
            var b = batches[i];
            var (title, body) = Plan.BuildBatchText(b, i + 1, batches.Count,
                destLabel, o.Title, o.Body, maxLines, maxFiles);
            units.Add(new UnitState
            {
                Label = $"PR {i + 1}/{batches.Count}",
                Steps = new List<Step> { new()
                {
                    Branch = $"{prefix}-{ts}-{i + 1}", Title = title, Body = body,
                    Batch = b
                } },
                Verify = b.ToList(),
            });
        }
        foreach (var (path, ch) in chains)
        {
            var j = units.Count + 1;
            var steps = new List<Step>();
            int done = 0;
            for (int k = 0; k < ch.Steps.Count; k++)
            {
                var c = ch.Steps[k];
                var (title, body) = Plan.BuildStepText(ch, c, k + 1,
                    ch.Steps.Count, cap, destLabel, o.Title, o.Body, done);
                done += c.Adds;
                steps.Add(new Step
                {
                    Branch = $"{prefix}-{ts}-{j}x{k + 1}", Title = title,
                    Body = body, Batch = new List<FileItem> { c }
                });
            }
            units.Add(new UnitState
            {
                Label = $"{kindName[ch.Kind]} {path}（{ch.Steps.Count} 块）",
                Steps = steps, Verify = new List<FileItem> { ch.Steps[^1] },
            });
        }
        for (int i = 0; i < deleteBatches.Count; i++)
        {
            var b = deleteBatches[i];
            var title = o.Title ?? $"remove {b.Count} file(s)";
            if (deleteBatches.Count > 1 && o.Title != null)
                title = $"{o.Title} (part {i + 1}/{deleteBatches.Count})";
            units.Add(new UnitState
            {
                Label = $"批量删除 {i + 1}/{deleteBatches.Count}",
                Steps = new List<Step> { new()
                {
                    Branch = $"{prefix}-{ts}-d{i + 1}", Title = title,
                    Body = o.Body ??
                        $"Removes {b.Count} file(s): {string.Join(", ",
                            b.Select(x => "`" + x.Path + "`"))}.\n\n" +
                        "Pre-validated locally by `submit-pr-cs`.\n",
                    Batch = b,
                } },
                Verify = b.ToList(),
            });
        }

        var workers = Math.Max(1, Math.Min(Math.Min(
            o.Workers > 0 ? o.Workers : units.Count, units.Count), 10));
        var totalSteps = units.Sum(u => u.Steps.Count);
        log($"并发提交 {units.Count} 个任务（{workers} 路并行，共 {totalSteps} 个 PR）…");
        Ev.EmitPlan(o, units.Count, totalSteps,
            $"{units.Count} 个任务 / {totalSteps} 个 PR / {workers} 路并行");
        foreach (var u in units)
            Ev.Emit(o, u.Label, "ustart", $"{u.Steps.Count} 步");
        foreach (var (u, j) in units.Select((u, j) => (u, j + 1)))
            log($"[{u.Label}] 开始（{u.Steps.Count} 步）");

        var sw = System.Diagnostics.Stopwatch.StartNew();
        var gate = new object();  // 单线程上下日志（Console 并发写交错难读）
        void LogThreadSafe(string s) { lock (gate) log(s); }
        Parallel.ForEach(units.Select((u, j) => (u, j + 1)),
            new ParallelOptions { MaxDegreeOfParallelism = workers },
            t =>
            {
                var (u, j) = t;
                try
                {
                    var res = new List<Runner.StepResult>();
                    for (int k = 0; k < u.Steps.Count; k++)
                    {
                        var s = u.Steps[k];
                        // 链的每一步都必须基于包含前一步的最新 main
                        var b2 = u.Steps.Count > 1
                            ? GitOps.LatestMainSha(target, repoRoot) : baseSha;
                        var r = Runner.SubmitBatch(o, u.Label, repoRoot, target,
                            fork, b2, s.Branch, s.Batch, dest, s.Title, s.Body,
                            o.PollTimeout, o.PollInterval, o.MaxRetries,
                            LogThreadSafe);
                        res.Add(r);
                        LogThreadSafe($"[{u.Label}] 第 {k + 1}/{u.Steps.Count} 步 → " +
                            $"PR #{r.Pr}: {r.Url}");
                        if (u.Steps.Count > 1 && r.Result != "merged")
                            throw new OpException(
                                $"链第 {k + 1}/{u.Steps.Count} 步未合并" +
                                $"（{r.Result}），中止后续步骤");
                    }
                    u.Results = res;
                    LogThreadSafe($"[{u.Label}] {u.Results[^1].Result}");
                    Ev.Emit(o, u.Label, "udone", u.Results[^1].Result);
                }
                catch (Exception e)
                {
                    u.Error = e.Message;
                    LogThreadSafe($"[{u.Label}] 失败 — {e.Message}");
                    Ev.Emit(o, u.Label, "fail", e.Message);
                }
            });
        sw.Stop();

        var ok = units.All(u => u.Error == null && u.Results.All(r => r.Result == "merged"));
        // 最终验证: 普通批验全部条目，链只看末步（创建/改写=原文件，删除=应不存在）
        var submitted = units.Where(u => u.Error == null)
            .SelectMany(u => u.Verify).ToList();
        var problems = Plan.VerifyOnMain(target, repoRoot, submitted, log);
        Ev.Emit(o, "", "verify", problems.Count == 0
            ? $"已验证: {submitted.Count} 个文件内容均与上游 main 一致"
            : string.Join("；", problems));
        if (problems.Count > 0) ok = false;
        foreach (var p in problems) log($"  [!] {p}");
        log($"  耗时 {sw.Elapsed.TotalMinutes:F1} 分钟，" +
            $"{units.Count} 个任务 / {totalSteps} 个 PR。");
        return ok;
    }
}
