using System.Diagnostics;
using System.Text.Json;

internal static class Program
{
    private static readonly IReadOnlyDictionary<string, string> LanguageByExtension =
        new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase)
        {
            [".c"] = "C",
            [".h"] = "C",
            [".cc"] = "C++",
            [".cpp"] = "C++",
            [".cxx"] = "C++",
            [".hh"] = "C++",
            [".hpp"] = "C++",
            [".cs"] = "C#",
            [".css"] = "CSS",
            [".go"] = "Go",
            [".html"] = "HTML",
            [".java"] = "Java",
            [".js"] = "JavaScript",
            [".jsx"] = "JavaScript",
            [".kt"] = "Kotlin",
            [".kts"] = "Kotlin",
            [".php"] = "PHP",
            [".py"] = "Python",
            [".pyi"] = "Python",
            [".rb"] = "Ruby",
            [".rs"] = "Rust",
            [".scss"] = "CSS",
            [".sh"] = "Shell",
            [".swift"] = "Swift",
            [".ts"] = "TypeScript",
            [".tsx"] = "TypeScript",
            [".vb"] = "Visual Basic",
            [".xaml"] = "XML",
            [".xml"] = "XML"
        };

    private static int Main(string[] args)
    {
        try
        {
            return Run(args);
        }
        catch (Exception exception)
        {
            Console.Error.WriteLine($"repo-language-radar: {exception.Message}");
            return 1;
        }
    }

    private static int Run(string[] args)
    {
        string? requestedPath = null;
        var json = false;

        for (var index = 0; index < args.Length; index++)
        {
            switch (args[index])
            {
                case "--help":
                case "-h":
                    PrintHelp();
                    return 0;
                case "--json":
                    json = true;
                    break;
                case "--path":
                    if (++index >= args.Length || args[index].StartsWith("--", StringComparison.Ordinal))
                    {
                        Console.Error.WriteLine("--path requires a directory.");
                        return 2;
                    }

                    requestedPath = args[index];
                    break;
                default:
                    Console.Error.WriteLine($"Unknown argument: {args[index]}");
                    PrintHelp();
                    return 2;
            }
        }

        var startDirectory = Path.GetFullPath(requestedPath ?? Environment.CurrentDirectory);
        var root = GetRepositoryRoot(startDirectory);
        var totals = new Dictionary<string, long>(StringComparer.Ordinal);
        var byteTotals = new Dictionary<string, long>(StringComparer.Ordinal);
        var skippedBinaryFiles = 0;
        var paths = RunGit(root, "ls-files", "-z").Split('\0', StringSplitOptions.RemoveEmptyEntries);

        foreach (var relativePath in paths)
        {
            if (!LanguageByExtension.TryGetValue(Path.GetExtension(relativePath), out var language))
            {
                continue;
            }

            var fullPath = Path.Combine(root, relativePath.Replace('/', Path.DirectorySeparatorChar));
            var metrics = CountSourceFile(fullPath);
            if (metrics is null)
            {
                skippedBinaryFiles++;
                continue;
            }

            totals[language] = totals.GetValueOrDefault(language) + metrics.Value.Lines;
            byteTotals[language] = byteTotals.GetValueOrDefault(language) + metrics.Value.Bytes;
        }

        var totalBytes = byteTotals.Values.Sum();
        var ranking = totals
            .OrderByDescending(entry => entry.Value)
            .ThenBy(entry => entry.Key, StringComparer.Ordinal)
            .Select(entry => new LanguageTotal(
                entry.Key,
                entry.Value,
                byteTotals.GetValueOrDefault(entry.Key),
                totalBytes == 0 ? 0 : byteTotals.GetValueOrDefault(entry.Key) * 100d / totalBytes))
            .ToArray();
        var csharpLines = totals.GetValueOrDefault("C#");
        var leadingNonCsharpLines = totals
            .Where(entry => entry.Key != "C#")
            .Select(entry => entry.Value)
            .DefaultIfEmpty(0)
            .Max();
        var linesToPassFirstPlace = Math.Max(0, leadingNonCsharpLines - csharpLines + 1);

        if (json)
        {
            Console.WriteLine(JsonSerializer.Serialize(new
            {
                repository = root,
                trackedSourceLines = ranking,
                totalSourceBytes = totalBytes,
                csharpLines,
                linesToPassFirstPlace,
                skippedBinaryFiles
            }));
            return 0;
        }

        Console.WriteLine($"Repository: {root}");
        Console.WriteLine("Tracked source lines (current working tree):");
        foreach (var entry in ranking)
        {
            Console.WriteLine(
                $"  {entry.Language,-14} {entry.Lines,12:N0} lines  {entry.Bytes,14:N0} bytes  {entry.ByteShare,6:F2}%");
        }

        Console.WriteLine("Byte shares use recognized tracked source files; they are not GitHub Linguist results.");
        Console.WriteLine(linesToPassFirstPlace == 0
            ? "C# is already strictly ahead of every other counted language."
            : $"C# needs {linesToPassFirstPlace:N0} more lines to rank first.");
        if (skippedBinaryFiles > 0)
        {
            Console.WriteLine($"Skipped binary-looking source files: {skippedBinaryFiles:N0}");
        }

        return 0;
    }

    private static string GetRepositoryRoot(string directory)
    {
        if (!Directory.Exists(directory))
        {
            throw new DirectoryNotFoundException($"Directory not found: {directory}");
        }

        return RunGit(directory, "rev-parse", "--show-toplevel").Trim();
    }

    private static string RunGit(string directory, params string[] arguments)
    {
        var startInfo = new ProcessStartInfo("git")
        {
            WorkingDirectory = directory,
            RedirectStandardOutput = true,
            UseShellExecute = false,
            StandardOutputEncoding = System.Text.Encoding.UTF8
        };
        foreach (var argument in arguments)
        {
            startInfo.ArgumentList.Add(argument);
        }

        using var process = Process.Start(startInfo)
            ?? throw new InvalidOperationException("Could not start git.");
        var output = process.StandardOutput.ReadToEnd();
        process.WaitForExit();
        if (process.ExitCode != 0)
        {
            throw new InvalidOperationException("git command failed.");
        }

        return output;
    }

    private static SourceMetrics? CountSourceFile(string path)
    {
        using var stream = File.OpenRead(path);
        var buffer = new byte[64 * 1024];
        long lines = 0;
        var hasContent = false;
        var previousWasCarriageReturn = false;
        byte lastByte = 0;
        int bytesRead;

        while ((bytesRead = stream.Read(buffer, 0, buffer.Length)) > 0)
        {
            for (var index = 0; index < bytesRead; index++)
            {
                var current = buffer[index];
                if (current == 0)
                {
                    return null;
                }

                if (current == '\r')
                {
                    lines++;
                    previousWasCarriageReturn = true;
                }
                else if (current == '\n')
                {
                    if (!previousWasCarriageReturn)
                    {
                        lines++;
                    }

                    previousWasCarriageReturn = false;
                }
                else
                {
                    previousWasCarriageReturn = false;
                }

                hasContent = true;
                lastByte = current;
            }
        }

        return new SourceMetrics(
            hasContent && lastByte != (byte)'\r' && lastByte != (byte)'\n' ? lines + 1 : lines,
            stream.Length);
    }

    private static void PrintHelp()
    {
        Console.WriteLine(
            "Usage: repo-language-radar [--path <directory>] [--json]\n" +
            "Counts tracked source lines in the current Git working tree.\n" +
            "  --path <directory>  Start in this directory (defaults to current directory)\n" +
            "  --json              Print machine-readable results\n" +
            "  -h, --help          Show this help");
    }

    private readonly record struct SourceMetrics(long Lines, long Bytes);

    private sealed record LanguageTotal(string Language, long Lines, long Bytes, double ByteShare);
}
