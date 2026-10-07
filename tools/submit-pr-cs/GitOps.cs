using System.Diagnostics;
using System.Text;

namespace SubmitPrCs;

// git 底层操作: 子进程封装 / 临时 worktree / 精确 numstat / 防御性提交构建。
public static class GitOps
{
    public static string Run(IList<string> cmd, string? cwd = null)
    {
        var psi = new ProcessStartInfo
        {
            FileName = cmd[0],
            WorkingDirectory = cwd ?? "",
            UseShellExecute = false,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            StandardOutputEncoding = Encoding.UTF8,
            StandardErrorEncoding = Encoding.UTF8,
            CreateNoWindow = true,
        };
        foreach (var a in cmd.Skip(1)) psi.ArgumentList.Add(a);
        using var p = Process.Start(psi)!;
        var ot = p.StandardOutput.ReadToEndAsync();
        var et = p.StandardError.ReadToEndAsync();
        p.WaitForExit();
        var o = ot.Result;
        var e = et.Result;
        if (p.ExitCode != 0)
        {
            var detail = (e.Length > 0 ? e : o).Trim();
            throw new OpException($"命令失败 ({p.ExitCode}): {string.Join(' ', cmd)}\n{detail}");
        }
        return o;
    }

    public static byte[] RunBytes(IList<string> cmd, string? cwd = null)
    {
        var psi = new ProcessStartInfo
        {
            FileName = cmd[0],
            WorkingDirectory = cwd ?? "",
            UseShellExecute = false,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            CreateNoWindow = true,
        };
        foreach (var a in cmd.Skip(1)) psi.ArgumentList.Add(a);
        using var p = Process.Start(psi)!;
        using var ms = new MemoryStream();
        p.StandardOutput.BaseStream.CopyTo(ms);
        var err = p.StandardError.ReadToEnd();
        p.WaitForExit();
        if (p.ExitCode != 0)
            throw new OpException($"命令失败 ({p.ExitCode}): {string.Join(' ', cmd)}\n{err.Trim()}");
        return ms.ToArray();
    }

    public static string Gh(params string[] args) => Run(new[] { "gh" }.Concat(args).ToArray()).Trim();

    // fetch: 先常规连接，代理/网络类错误自动降级直连重试一次
    public static void GitFetch(string url, string refspec, string repoRoot)
    {
        string[][] variants = { Array.Empty<string>(), new[] { "-c", "http.proxy=", "-c", "https.proxy=" } };
        Exception? last = null;
        foreach (var cfg in variants)
        {
            try
            {
                Run(new[] { "git" }.Concat(cfg).Concat(new[] { "fetch", url, refspec }).ToArray(), repoRoot);
                return;
            }
            catch (OpException e)
            {
                last = e;
                var m = e.Message.ToLowerInvariant();
                if (!(m.Contains("proxy") || m.Contains("connect") || m.Contains("ssl") ||
                      m.Contains("tls") || m.Contains("handshake") || m.Contains("timeout") ||
                      m.Contains("eof")))
                    throw;
            }
        }
        throw last!;
    }

    public static string LatestMainSha(string target, string repoRoot)
    {
        GitFetch($"https://github.com/{target}.git", "main", repoRoot);
        return Run(new[] { "git", "rev-parse", "FETCH_HEAD" }, repoRoot).Trim();
    }

    public static string MakeWorktree(string repoRoot, string baseSha)
    {
        var wt = Path.Combine(repoRoot, ".git", "submit-pr-worktrees",
            $"w{DateTime.UtcNow.Ticks}");
        Directory.CreateDirectory(Path.GetDirectoryName(wt)!);
        Run(new[] { "git", "worktree", "add", "--detach", wt, baseSha }, repoRoot);
        return wt;
    }

    public static void DropWorktree(string repoRoot, string wt)
    {
        Run(new[] { "git", "worktree", "remove", "--force", wt }, repoRoot);
        Run(new[] { "git", "worktree", "prune" }, repoRoot);
    }

    // 复制批次文件到 worktree（delete 步骤改为移除对应文件，git add 会暂存删除）
    public static void CopyInto(string wt, string dest, List<FileItem> batch)
    {
        foreach (var it in batch)
        {
            var t = dest.Length > 0 ? Path.Combine(wt, dest, it.Rel) : Path.Combine(wt, it.Rel);
            if (it.Delete)
            {
                if (File.Exists(t)) File.Delete(t);
                continue;
            }
            Directory.CreateDirectory(Path.GetDirectoryName(t)!);
            File.Copy(it.Src, t, true);
        }
    }

    // 暂存指定路径（修改与删除都暂存），pathspec 走临时文件规避命令行长度限制。
    // 绝不 "add ."——新建 worktree 存在 racy-git 整树重哈希，并发 worktree 共享
    // 对象库时会在 Windows 上撞对象写锁（failed to insert into database），
    // 且可能把无关文件卷进提交。撞锁类错误小退避重试。
    public static Dictionary<string, (int Adds, int Dels)> StagedNumstat(
        string wt, IEnumerable<string> paths)
    {
        var spec = Path.GetTempFileName();
        try
        {
            File.WriteAllText(spec, string.Join("\0", paths.Distinct()) + "\0");
            for (int attempt = 1; ; attempt++)
            {
                try
                {
                    Run(new[] { "git", "-C", wt, "add", "-A",
                        $"--pathspec-from-file={spec}", "--pathspec-file-nul" });
                    break;
                }
                catch (OpException e) when (attempt < 3 && IsLockNoise(e.Message))
                {
                    Thread.Sleep(2000 * attempt);
                }
            }
            var outp = Run(new[] { "git", "-C", wt, "-c", "core.quotePath=false",
                "diff", "--cached", "--numstat", "-z" });
            var res = new Dictionary<string, (int, int)>();
            foreach (var entry in outp.Split('\0'))
            {
                if (entry.Trim().Length == 0) continue;
                var parts = entry.Split('\t', 3);
                if (parts.Length < 3) continue;
                int a = parts[0] == "-" ? 0 : int.Parse(parts[0]);
                int d = parts[1] == "-" ? 0 : int.Parse(parts[1]);
                res[parts[2]] = (a, d);
            }
            return res;
        }
        finally
        {
            try { File.Delete(spec); } catch { /* 临时文件尽力删 */ }
        }
    }

    private static bool IsLockNoise(string msg)
    {
        var m = msg.ToLowerInvariant();
        return m.Contains("failed to insert into database")
            || m.Contains("permission denied") || m.Contains("unable to write")
            || m.Contains("unable to index") || m.Contains("index.lock");
    }

    // probe: 在临时 worktree 暂存全部候选文件，得到与机器人一致的每个文件变更数
    public static Dictionary<string, (int Adds, int Dels)> ProbeChanges(
        string repoRoot, string baseSha, string dest, List<FileItem> files)
    {
        Run(new[] { "git", "worktree", "prune" }, repoRoot);
        var wt = MakeWorktree(repoRoot, baseSha);
        try
        {
            CopyInto(wt, dest, files);
            return StagedNumstat(wt, files.Select(f => f.Path));
        }
        finally
        {
            DropWorktree(repoRoot, wt);
        }
    }

    // 在临时 worktree 里基于 baseSha 复制文件并提交（提交前按机器人上限兜底复核）
    public static string BuildWorktreeCommit(string repoRoot, string baseSha,
        string dest, List<FileItem> batch, string message)
    {
        var wt = MakeWorktree(repoRoot, baseSha);
        try
        {
            CopyInto(wt, dest, batch);
            var ns = StagedNumstat(wt, batch.Select(x => x.Path));
            // 防污染: 暂存集合必须恰好等于本批文件（多一个都说明 add 语义被破坏）
            var expect = batch.Select(x => x.Path).ToHashSet();
            foreach (var k in ns.Keys)
                if (!expect.Contains(k))
                    throw new OpException($"意外暂存了 {k}（应只有本批文件）");
            int total = ns.Sum(kv => Rules.ExcludedPath(kv.Key)
                ? 0 : kv.Value.Adds + kv.Value.Dels);
            if (ns.Count > Rules.MaxChangedFiles)
                throw new OpException($"文件数 {ns.Count} 超过 {Rules.MaxChangedFiles}");
            if (total > Rules.MaxChangedLines)
                throw new OpException($"计数行数 {total} 超过 {Rules.MaxChangedLines}");
            foreach (var kv in ns)
            {
                if (Rules.ExcludedPath(kv.Key)) continue;
                if (kv.Value.Adds + kv.Value.Dels > Rules.MaxFileLines)
                    throw new OpException(
                        $"{kv.Key} 变更 {kv.Value.Adds + kv.Value.Dels} 行超过 {Rules.MaxFileLines}");
            }
            Run(new[] { "git", "-C", wt, "commit", "-q", "-m", message });
            return wt;
        }
        catch
        {
            DropWorktree(repoRoot, wt);
            throw;
        }
    }
}
