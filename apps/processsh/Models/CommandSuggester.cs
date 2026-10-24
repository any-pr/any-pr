namespace ProcessSH.Models;

public sealed class CommandSuggester
{
    private readonly CommandHistory _history;
    private List<string> _cachedCommands = new();
    private DateTime _lastCacheUpdate = DateTime.MinValue;
    private static readonly TimeSpan CacheTtl = TimeSpan.FromSeconds(60);
    private static readonly object _cacheLock = new();

    /// <summary>参数一定是路径的命令</summary>
    private static readonly HashSet<string> PathOnlyCommands = new(StringComparer.OrdinalIgnoreCase)
    {
        "cd", "chdir", "dir", "ls", "type", "cat", "copy", "move", "del", "rm",
        "mkdir", "md", "rmdir", "rd", "start", "open", "explorer",
    };

    public CommandSuggester(CommandHistory history)
    {
        _history = history;
    }

    public List<Suggestion> Suggest(string input)
    {
        var lastWord = GetLastWord(input);

        if (string.IsNullOrEmpty(lastWord)) return new List<Suggestion>();

        if (IsPathLike(input, lastWord))
            return SuggestPaths(lastWord);

        return SuggestCommandsAndHistory(lastWord);
    }

    /// <summary>
    /// 取输入中光标所在的"词"。按空格切分，但忽略引号内的空格。
    /// </summary>
    private static string GetLastWord(string input)
    {
        if (string.IsNullOrEmpty(input)) return string.Empty;

        bool inQuote = false;
        int lastSpace = -1;
        for (int i = 0; i < input.Length; i++)
        {
            var c = input[i];
            if (c == '"') inQuote = !inQuote;
            else if (c == ' ' && !inQuote) lastSpace = i;
        }

        var word = lastSpace >= 0 ? input.Substring(lastSpace + 1) : input;
        return word.Trim('"');
    }

    /// <summary>判断当前输入的最后一个词是否应该按路径补全</summary>
    private static bool IsPathLike(string input, string lastWord)
    {
        if (lastWord.StartsWith("/") || lastWord.StartsWith("~") || lastWord.StartsWith("\\"))
            return true;

        if (lastWord.Length >= 2 &&
            char.IsLetter(lastWord[0]) &&
            lastWord[1] == ':')
            return true;

        if (lastWord == "." || lastWord == ".." ||
            lastWord.StartsWith(".\\") || lastWord.StartsWith("..\\") ||
            lastWord.StartsWith("./") || lastWord.StartsWith("../"))
            return true;

        if (lastWord.StartsWith("%") || lastWord.StartsWith("$env:"))
            return true;

        var firstWord = GetFirstWord(input);
        if (!string.IsNullOrEmpty(firstWord) && PathOnlyCommands.Contains(firstWord))
            return true;

        if (lastWord.Contains("\\") || lastWord.Contains("/"))
            return true;

        return false;
    }

    private static string GetFirstWord(string input)
    {
        var idx = input.IndexOf(' ');
        return idx < 0 ? input : input.Substring(0, idx);
    }

    private List<Suggestion> SuggestPaths(string input)
    {
        try
        {
            var expanded = Environment.ExpandEnvironmentVariables(input);

            if (expanded.StartsWith("$env:"))
                return new List<Suggestion>();

            var dir = Path.GetDirectoryName(expanded);
            var partial = Path.GetFileName(expanded);

            if (string.IsNullOrEmpty(dir) && expanded.Length >= 2 && expanded[1] == ':')
            {
                dir = expanded.Substring(0, 2) + "\\";
                partial = string.Empty;
            }

            if (string.IsNullOrEmpty(dir)) dir = ".";

            if (dir == ".")
                dir = Directory.GetCurrentDirectory();
            else if (dir == "..")
                dir = Path.GetDirectoryName(Directory.GetCurrentDirectory()) ?? ".";

            if (!Directory.Exists(dir)) return new List<Suggestion>();

            return Directory.GetFileSystemEntries(dir, partial + "*")
                .Take(20)
                .Select(path =>
                {
                    var display = path;
                    if (Directory.Exists(path))
                        display += "\\";
                    // 含空格时加引号，避免执行时被拆成多个参数
                    if (display.Contains(' '))
                        display = "\"" + display + "\"";
                    return new Suggestion(display, SuggestionType.Path);
                })
                .ToList();
        }
        catch
        {
            return new List<Suggestion>();
        }
    }

    private List<Suggestion> SuggestCommandsAndHistory(string prefix)
    {
        var suggestions = new List<Suggestion>();
        var seen = new HashSet<string>(StringComparer.OrdinalIgnoreCase);

        foreach (var entry in _history.Query(prefix))
        {
            if (seen.Add(entry.Command))
                suggestions.Add(new Suggestion(entry.Command, SuggestionType.History, entry.Count));
        }

        foreach (var cmd in FindSystemCommands(prefix))
        {
            if (seen.Add(cmd))
                suggestions.Add(new Suggestion(cmd, SuggestionType.Command));
        }

        return suggestions.OrderByDescending(s => s.Priority).ToList();
    }

    private List<string> FindSystemCommands(string prefix)
    {
        lock (_cacheLock)
        {
            if (DateTime.UtcNow - _lastCacheUpdate < CacheTtl && _cachedCommands.Count > 0)
            {
                return _cachedCommands
                    .Where(c => c.StartsWith(prefix, StringComparison.OrdinalIgnoreCase))
                    .Take(20)
                    .ToList();
            }

            var pathString = Environment.GetEnvironmentVariable("PATH") ?? string.Empty;
            var paths = pathString.Split(';', StringSplitOptions.RemoveEmptyEntries);

            var all = new List<string>();
            var seen = new HashSet<string>(StringComparer.OrdinalIgnoreCase);

            foreach (var dir in paths)
            {
                try
                {
                    foreach (var file in Directory.GetFiles(dir))
                    {
                        var name = Path.GetFileNameWithoutExtension(file);
                        if (string.IsNullOrEmpty(name)) continue;
                        if (name.StartsWith(".")) continue;
                        if (!seen.Add(name)) continue;

                        var ext = Path.GetExtension(file);
                        if (ext.Equals(".exe", StringComparison.OrdinalIgnoreCase) ||
                            ext.Equals(".cmd", StringComparison.OrdinalIgnoreCase) ||
                            ext.Equals(".bat", StringComparison.OrdinalIgnoreCase) ||
                            ext.Equals(".ps1", StringComparison.OrdinalIgnoreCase))
                        {
                            all.Add(name);
                        }
                    }
                }
                catch { }
            }

            _cachedCommands = all.OrderBy(c => c).ToList();
            _lastCacheUpdate = DateTime.UtcNow;

            return _cachedCommands
                .Where(c => c.StartsWith(prefix, StringComparison.OrdinalIgnoreCase))
                .Take(20)
                .ToList();
        }
    }
}