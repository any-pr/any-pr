using System.Diagnostics;
using System.Text;

namespace SubmitPrCs;

// git 底层操作: 子进程封装 / 临时 worktree / 精确 numstat / 防御性提交构建。
public static class GitOps
{
    // 多账号: 服务端在作业开始时设置（如 GH_TOKEN=<该账号 token>），作业串行执行无竞态；
    // 控制台直接运行时保持 null，用 gh 的默认登录态。
    public static Dictionary<string, string>? GhEnv;

    public static string Run(IList<string> cmd, string? cwd = null,
        Dictionary<string, string>? env = null)
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
        if (env != null)
            foreach (var kv in env) psi.Environment[kv.Key] = kv.Value;
        using var p = Process.Start(psi)!;
        var ot = p.StandardOutput.ReadToEndAsync();
        var et = p.StandardError.ReadToEndAsync();
        p.WaitForExit();
        var o = ot.Result;
        var e = et.Result;
        if (p.ExitCode != 0)
        {
            var detail = (e.Length > 0 ? e : o).Trim();
            throw new OpException(Redact(
                $"命令失败 ({p.ExitCode}): {string.Join(' ', cmd)}\n{detail}"));
        }
        return o;
    }

    // 错误信息里可能出现 https://token@github.com/... 形式的地址，上抛前打码
    private static string Redact(string s) =>
        System.Text.RegularExpressions.Regex.Replace(s, @"(?<=://)[^/@\s]+@", "***@");

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

    public static string Gh(params string[] args) =>
        Run(new[] { "gh" }.Concat(args).ToArray(), null, GhEnv).Trim();

    // 在 worktree 上下文里判定哪些路径被 .gitignore（含子目录 .gitignore）忽略。
    // 被忽略的路径 git add 会拒绝；项目作者用 .gitignore 表达"不 vendor"的意图，
    // 上传时也应跳过它们。返回被忽略的路径集合。
    public static HashSet<string> CheckIgnored(string wt, IEnumerable<string> paths)
    {
        var ignored = new HashSet<string>();
        var psi = new ProcessStartInfo
        {
            FileName = "git",
            WorkingDirectory = wt,
            UseShellExecute = false,
            RedirectStandardInput = true,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            CreateNoWindow = true,
        };
        foreach (var a in new[] { "-C", wt, "check-ignore", "-z", "--stdin" })
            psi.ArgumentList.Add(a);
        using var p = Process.Start(psi)!;
        // 先挂起 stdout 异步读取再写 stdin——命中路径多时 stdout 会先写满
        // 管道缓冲（~4KB），若同步写 stdin 会互相阻塞造成死锁
        var outTask = p.StandardOutput.ReadToEndAsync();
        var errTask = p.StandardError.ReadToEndAsync();
        var stdin = string.Join("\0", paths.Distinct()) + "\0";
        p.StandardInput.Write(stdin);
        p.StandardInput.Close();
        p.WaitForExit();
        var stdout = outTask.Result;
        if (p.ExitCode == 0)  // 0=有被忽略的路径, 1=都没有
            foreach (var s in stdout.Split('\0', StringSplitOptions.RemoveEmptyEntries))
                ignored.Add(s.TrimEnd('\r'));
        else if (p.ExitCode != 1)  // 其他退出码是真错误，吞掉会让忽略路径漏进提交
            throw new OpException(
                $"git check-ignore 失败 ({p.ExitCode}): {errTask.Result.Trim()}");
        return ignored;
    }

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

    // 串行化对同一仓库的 fetch+rev-parse：并发线程同时 fetch 会竞写 FETCH_HEAD，
    // 轻则 rev-parse 读到坏引用，重则拿到过期 main SHA（链步 diff 因此失配被断言拦截）
    private static readonly SemaphoreSlim FetchLock = new(1, 1);

    public static string LatestMainSha(string target, string repoRoot)
    {
        FetchLock.Wait();
        try
        {
            GitFetch($"https://github.com/{target}.git", "main", repoRoot);
            return Run(new[] { "git", "rev-parse", "FETCH_HEAD" }, repoRoot).Trim();
        }
        finally
        {
            FetchLock.Release();
        }
    }

    public static string MakeWorktree(string repoRoot, string baseSha)
    {
        var wt = Path.Combine(repoRoot, ".git", "submit-pr-worktrees",
            $"w{DateTime.UtcNow.Ticks}");
        Directory.CreateDirectory(Path.GetDirectoryName(wt)!);
        // 并发 worktree add 偶发 .git/worktrees 写冲突 → 退避重试
        for (int i = 1; ; i++)
        {
            try
            {
                Run(new[] { "git", "worktree", "add", "--detach", wt, baseSha }, repoRoot);
                return wt;
            }
            catch (OpException) when (i < 3)
            {
                Thread.Sleep(2000 * i);
            }
        }
    }

    public static void DropWorktree(string repoRoot, string wt)
    {
        for (int i = 1; ; i++)
        {
            try
            {
                Run(new[] { "git", "worktree", "remove", "--force", wt }, repoRoot);
                break;
            }
            catch (OpException) when (i < 3)
            {
                Thread.Sleep(2000 * i);
            }
        }
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

    // probe: 在临时 worktree 暂存全部候选文件，得到与机器人一致的每个文件变更数。
    // 被 .gitignore 忽略的路径不参与 add，单独返回给调用方做跳过报告。
    public static (Dictionary<string, (int Adds, int Dels)> Ns, List<string> Ignored)
        ProbeChanges(string repoRoot, string baseSha, string dest, List<FileItem> files)
    {
        Run(new[] { "git", "worktree", "prune" }, repoRoot);
        var wt = MakeWorktree(repoRoot, baseSha);
        try
        {
            CopyInto(wt, dest, files);
            var ignored = CheckIgnored(wt, files.Select(f => f.Path));
            var addable = files.Where(f => !ignored.Contains(f.Path)).ToList();
            var ns = addable.Count > 0
                ? StagedNumstat(wt, addable.Select(f => f.Path))
                : new Dictionary<string, (int, int)>();
            return (ns, ignored.OrderBy(x => x).ToList());
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
