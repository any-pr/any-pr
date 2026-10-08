namespace SubmitPrCs;

// 源文件收集与"超大文件"的渐进分块链（创建/修改/删除），每步 ≤cap 行。
public static class Chains
{
    public static List<FileItem> CollectSources(List<string> sources)
    {
        var files = new List<FileItem>();
        var seen = new Dictionary<string, string>();
        foreach (var s in sources)
        {
            if (!Directory.Exists(s) && !File.Exists(s))
                throw new OpException($"源不存在: {s}");
            var entries = new List<(string Src, string Rel)>();
            if (File.Exists(s) && !Directory.Exists(s))
            {
                entries.Add((Path.GetFullPath(s), Path.GetFileName(s)!));
            }
            else
            {
                // 跳过嵌套版本库等 VCS 目录——绝不能把 .git 内部文件当作源提交
                var excludedDirs = new HashSet<string> { ".git", ".svn", ".hg" };
                foreach (var f in Directory.EnumerateFiles(s, "*",
                    SearchOption.AllDirectories).OrderBy(x => x))
                {
                    var relDir = Path.GetRelativePath(s, f);
                    var segs = relDir.Split(Path.DirectorySeparatorChar,
                        Path.AltDirectorySeparatorChar);
                    if (segs.Take(segs.Length - 1).Any(excludedDirs.Contains))
                        continue;
                    var attr = File.GetAttributes(f);
                    if ((attr & FileAttributes.ReparsePoint) != 0)
                    {
                        Console.WriteLine($"  [跳过] {f} 是符号链接，仓库不允许（已跳过）");
                        continue;
                    }
                    entries.Add((f, relDir.Replace('\\', '/')));
                }
            }
            foreach (var (src, rel) in entries)
            {
                if (seen.ContainsKey(rel))
                    throw new OpException($"目标相对路径冲突: {rel}（{src} 与 {seen[rel]}）");
                seen[rel] = src;
                files.Add(new FileItem { Src = src, Rel = rel });
            }
        }
        return files;
    }

    // 读取基线 commit 上某文件的内容；不存在返回 null（空文件返回空数组）
    public static byte[]? BaseContent(string baseSha, string path, string repoRoot)
    {
        try
        {
            return GitOps.RunBytes(new[] { "git", "show", $"{baseSha}:{path}" }, repoRoot);
        }
        catch (OpException)
        {
            return null;
        }
    }

    private static string Tag(FileItem it) => it.Rel.Replace('/', '_');

    private static string PrefixFile(byte[][] lines, int end, string tmpDir, string tag)
    {
        var dir = Directory.CreateDirectory(tmpDir);
        var p = Path.Combine(dir.FullName, $"{tag}.{end}.part");
        using var fs = new FileStream(p, FileMode.Create);
        for (int i = 0; i < end; i++) fs.Write(lines[i]);
        return p;
    }

    private static byte[][] SplitKeepEnds(byte[] data)
    {
        var lines = new List<byte[]>();
        int start = 0;
        for (int i = 0; i < data.Length; i++)
        {
            if (data[i] == (byte)'\n')
            {
                lines.Add(data[start..(i + 1)]);
                start = i + 1;
            }
        }
        if (start < data.Length) lines.Add(data[start..]);
        return lines.ToArray();
    }

    private static byte[] Join(byte[][] lines, int end)
    {
        using var ms = new MemoryStream();
        for (int i = 0; i < end; i++) ms.Write(lines[i]);
        return ms.ToArray();
    }

    // 新建超大文件 → 渐进创建链（每块 ≤cap 行，末块=原文件字节）
    public static List<FileItem> BuildChunkChain(FileItem item, int cap, string tmpDir)
    {
        var lines = SplitKeepEnds(File.ReadAllBytes(item.Src));
        var tag = Tag(item);
        var chain = new List<FileItem>();
        int k = 0;
        foreach (var (a, b) in Rules.ChunkBounds(lines.Length, cap))
        {
            k++;
            var src = b == lines.Length ? item.Src : PrefixFile(lines, b, tmpDir, $"{tag}.c{k}");
            chain.Add(new FileItem { Src = src, Rel = item.Rel, Path = item.Path, Adds = b - a });
        }
        return chain;
    }

    // 超大 diff 修改 → 截断到公共前缀 + 逐步追加（每步 ≤cap 行）。
    // 公共前缀比较忽略行尾风格（blob 可能被 autocrlf 归一化成 LF 而本地是 CRLF）；
    // 若旧内容是 CRLF，追加段前缀也转成 CRLF，保证每步都是纯追加。
    public static List<FileItem> BuildModifyChain(FileItem item, byte[] oldBytes,
        int cap, string tmpDir)
    {
        var old = SplitKeepEnds(oldBytes);
        var newLines = SplitKeepEnds(File.ReadAllBytes(item.Src));
        int common = 0;
        while (common < old.Length && common < newLines.Length &&
               TrimEol(old[common]).AsSpan().SequenceEqual(TrimEol(newLines[common]).AsSpan()))
            common++;
        if (old.Any(l => l.EndsWith((byte)'\r')))
        {
            newLines = newLines
                .Select(l => l.Length >= 2 && l[^2] == (byte)'\r' ? l
                    : l[^1] == (byte)'\n' ? l[..^1].Concat(new byte[] { (byte)'\r', (byte)'\n' }).ToArray()
                    : l)
                .ToArray();
        }
        var tag = Tag(item);
        var steps = new List<FileItem>();
        int k = old.Length;
        while (k > common)
        {
            int k2 = Math.Max(common, k - cap);
            steps.Add(new FileItem { Src = PrefixFile(old, k2, tmpDir, $"{tag}.t{k2}"),
                Rel = item.Rel, Path = item.Path, Dels = k - k2 });
            k = k2;
        }
        int m = common;
        while (m < newLines.Length)
        {
            int m2 = Math.Min(newLines.Length, m + cap);
            var src = m2 == newLines.Length ? item.Src : PrefixFile(newLines, m2, tmpDir, $"{tag}.g{m2}");
            steps.Add(new FileItem { Src = src, Rel = item.Rel, Path = item.Path, Adds = m2 - m });
            m = m2;
        }
        return steps;
    }

    private static byte[] TrimEol(byte[] line) =>
        line.Length > 0 && line[^1] == (byte)'\n'
            ? (line.Length > 1 && line[^2] == (byte)'\r' ? line[..^2] : line[..^1])
            : line;

    // 删除已存在文件: 大文本先按 cap 行逐步截断，末步真正删除。
    // 豁免文件（lockfile/vendor 等）与二进制按 0 计数行，一步删除。
    public static List<FileItem> BuildDeleteChain(FileItem item, byte[] oldBytes,
        int cap, string tmpDir)
    {
        if (Rules.ExcludedPath(item.Path) || Array.IndexOf(oldBytes, (byte)0) >= 0)
            return new List<FileItem> { new() { Rel = item.Rel, Path = item.Path,
                Delete = true } };
        var lines = SplitKeepEnds(oldBytes);
        var tag = Tag(item);
        var steps = new List<FileItem>();
        int k = lines.Length;
        while (k > cap)
        {
            int k2 = k - cap;
            steps.Add(new FileItem { Src = PrefixFile(lines, k2, tmpDir, $"{tag}.d{k2}"),
                Rel = item.Rel, Path = item.Path, Dels = k - k2 });
            k = k2;
        }
        steps.Add(new FileItem { Rel = item.Rel, Path = item.Path, Delete = true, Dels = k });
        return steps;
    }
}
