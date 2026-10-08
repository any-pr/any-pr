using System.Text.RegularExpressions;

namespace SubmitPrCs;

// 核心类型与机器人规则的一比一复刻（对照上游 .github/workflows/auto-merge.yml，勿改）。
public class FileItem
{
    public string Src = "";   // 本地源路径；删除步骤为 null
    public string Rel = "";   // 相对路径（worktree 内定位用，删除时=仓库根相对路径）
    public string Path = "";  // 仓库内目标路径
    public int Adds, Dels;
    public bool Delete;       // true=本步骤删除该文件（git rm）
}

public class Chain
{
    public string Kind = "";                  // create / modify / delete
    public List<FileItem> Steps = new();
}

public class Step
{
    public string Branch = "", Title = "", Body = "";
    public List<FileItem> Batch = new();
}

public class Unit
{
    public string Label = "";
    public List<Step> Steps = new();
    public FileItem Verify = new();           // 最终验证条目（链取末步）
}

public class OpException : Exception
{
    public OpException(string msg) : base(msg) { }
}

public static class Rules
{
    public const int MaxChangedFiles = 20, MaxChangedLines = 500, MaxFileLines = 300;

    private const string ExtExcl =
        "c|h|cpp|cc|cxx|hpp|hh|cs|go|rs|rb|erb|py|pyi|java|kt|kts|scala|clj|ex|exs|erl|" +
        "hs|ml|fs|vb|swift|php|pl|pm|r|lua|dart|zig|nim|cr|jl|sh|bash|zsh|fish|ps1|bat|" +
        "cmd|js|jsx|ts|tsx|mjs|cjs|mts|cts|vue|svelte|astro|elm|css|scss|sass|less|styl|" +
        "json|jsonc|ya?ml|toml|xml|ini|cfg|conf|sql|graphql|proto|tf|nix|ipynb|lock|csv|" +
        "png|jpe?g|gif|svg|ico|webp|mp[34]|mov|woff2?|ttf|otf|eot|o|obj|so|dll|exe|wasm|" +
        "zip|tar|gz|7z|jar|whl";

    private static readonly Regex Protected = new(@"^\.github(/|$)", Ig);
    private static readonly Regex Docs = new(
        @"(^|/)licen[cs]es?/|(^|/)((un)?licen[cs]e|copying|copyright|readme)s?" +
        @"([._-](?!(" + ExtExcl + @")$)[a-z0-9]+)*$", Ig);
    private static readonly Regex Prohibited = new(
        @"~$|\.(tmp|temp|bak|sav|old|orig|rej|sw[a-z]|log)$" +
        @"|(^|/)(\.DS_Store|Thumbs\.db|desktop\.ini)$" +
        @"|(^|/)(__pycache__|\.pytest_cache|\.mypy_cache|\.ruff_cache|\.ipynb_checkpoints" +
        @"|\.tox|\.nox|\.cache|\.parcel-cache|\.turbo|\.nyc_output|node_modules|dist|build" +
        @"|out|target|coverage|obj|\.next|\.nuxt|\.output|\.gradle|\.vercel)/" +
        @"|\.(eslintcache|stylelintcache|py[co]|o|obj|a|lib|so|dylib|dll|exe|com|msi|apk|" +
        @"aab|ipa|dmg|deb|rpm|class|jar|war|ear|whl|egg|wasm|pdb|bin|db|sqlite3?|zip|tgz|" +
        @"tar|tar\.(gz|bz2|xz|zst)|gz|bz2|xz|7z|rar|zst)$", Ig);
    private static readonly Regex Secret = new(
        @"(^|/)\.env([._-][a-z0-9._-]*)?$|(^|/)id_(rsa|dsa|ecdsa|ed25519)$" +
        @"|(^|/)credentials(\.json)?$|(^|/)\.(npmrc|netrc|pgpass|htpasswd)$" +
        @"|\.(pem|key|p12|pfx|jks|keystore|kdbx|ovpn)$", Ig);
    private static readonly Regex SecretOk = new(
        @"(^|/)\.env[._-](example|sample|template|dist)$", Ig);
    private static readonly Regex Excluded = new(
        @"(^|/)(package-lock\.json|npm-shrinkwrap\.json|yarn\.lock|pnpm-lock\.yaml|" +
        @"bun\.lockb?|composer\.lock|Cargo\.lock|Gemfile\.lock|poetry\.lock|Pipfile\.lock|" +
        @"go\.sum)$|(^|/)vendor/|\.(min\.(js|css)|map|snap|lockb?)$", Ig);

    private static RegexOptions Ig => RegexOptions.IgnoreCase | RegexOptions.Compiled;

    public static List<string> GatePathProblems(string p)
    {
        var probs = new List<string>();
        if (Protected.IsMatch(p)) probs.Add("触碰受保护路径 .github/");
        if (Docs.IsMatch(p)) probs.Add("受保护文档 (LICENSE/README 系)");
        if (Prohibited.IsMatch(p)) probs.Add("临时/缓存/构建产物/二进制文件名");
        if (Secret.IsMatch(p) && !SecretOk.IsMatch(p)) probs.Add("疑似密钥/凭据文件");
        return probs;
    }

    public static bool ExcludedPath(string p) => Excluded.IsMatch(p);

    public static bool IsBinary(string path)
    {
        // 与机器人一致: 扫描前 50MB 是否含 NUL 字节。分块读取——逐文件分配
        // 50MB 缓冲会在大项目上造成海量 LOH 分配（实测 4700 文件拖垮整个 probe）。
        const int chunk = 1 << 16;
        using var fs = File.OpenRead(path);
        var buf = new byte[chunk];
        long scanned = 0;
        int n;
        while (scanned < 50L * 1024 * 1024 && (n = fs.Read(buf, 0, chunk)) > 0)
        {
            if (Array.IndexOf(buf, (byte)0, 0, n) >= 0) return true;
            scanned += n;
        }
        return false;
    }

    // 机器人计入 500 行上限的行数（lockfile/vendor/min 等豁免但仍占文件数）
    public static int CountedLines(FileItem it) =>
        Excluded.IsMatch(it.Path) ? 0 : it.Adds + it.Dels;

    // 贪心装箱: 按输入顺序打包，单批 ≤ maxFiles 个文件且 ≤ maxLines 计数行
    public static List<List<FileItem>> MakeBatches(
        List<FileItem> items, int maxLines, int maxFiles)
    {
        var batches = new List<List<FileItem>>();
        var cur = new List<FileItem>();
        int curLines = 0;
        foreach (var it in items)
        {
            int c = CountedLines(it);
            if (cur.Count > 0 && (cur.Count + 1 > maxFiles || curLines + c > maxLines))
            {
                batches.Add(cur);
                cur = new List<FileItem>();
                curLines = 0;
            }
            cur.Add(it);
            curLines += c;
        }
        if (cur.Count > 0) batches.Add(cur);
        return batches;
    }

    // 把 total 行按每块最多 cap 行切块，返回 (start, end)，0 起、不含 end
    public static List<(int Start, int End)> ChunkBounds(int total, int cap)
    {
        var bounds = new List<(int, int)>();
        for (int s = 0; s < total; s += cap) bounds.Add((s, Math.Min(s + cap, total)));
        return bounds;
    }
}
