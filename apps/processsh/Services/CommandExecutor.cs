using System.Diagnostics;
using System.Text;
using ProcessSH.Models;

namespace ProcessSH.Services;

public sealed class CommandExecutor
{
    public static CommandExecutor Shared { get; } = new();

    private const int MaxOutputSize = 10 * 1024 * 1024;
    private readonly object _stateLock = new();
    private Process? _currentProcess;
    private CancellationTokenSource? _currentCts;

    private CommandExecutor() { }

    public async Task<CommandResult> ExecuteAsync(
        string command,
        string workingDirectory,
        double timeoutSeconds = 10.0,
        CancellationToken cancellationToken = default)
    {
        if (string.IsNullOrWhiteSpace(workingDirectory) || !Directory.Exists(workingDirectory))
        {
            workingDirectory = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
        }

        CancelCurrent();

        using var cts = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        cts.CancelAfter(TimeSpan.FromSeconds(timeoutSeconds));

        lock (_stateLock)
        {
            _currentCts = cts;
        }

        var startInfo = new ProcessStartInfo
        {
            FileName = ResolvePwshPath(),
            Arguments = BuildArguments(command),
            WorkingDirectory = workingDirectory,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            UseShellExecute = false,
            CreateNoWindow = true,
            StandardOutputEncoding = Encoding.UTF8,
            StandardErrorEncoding = Encoding.UTF8,
        };

        var process = new Process { StartInfo = startInfo };
        var output = new StringBuilder();
        var error = new StringBuilder();
        var outputLock = new object();

        process.OutputDataReceived += (_, e) =>
        {
            if (e.Data == null) return;
            lock (outputLock)
            {
                if (output.Length + e.Data.Length <= MaxOutputSize)
                    output.AppendLine(e.Data);
            }
        };

        process.ErrorDataReceived += (_, e) =>
        {
            if (e.Data == null) return;
            lock (outputLock)
            {
                if (error.Length + e.Data.Length <= MaxOutputSize)
                    error.AppendLine(e.Data);
            }
        };

        try
        {
            process.Start();
            process.BeginOutputReadLine();
            process.BeginErrorReadLine();

            lock (_stateLock)
            {
                _currentProcess = process;
            }

            await process.WaitForExitAsync(cts.Token).ConfigureAwait(false);
            process.WaitForExit();

            var outputText = output.ToString().TrimEnd();
            var errorText = error.ToString().TrimEnd();
            var combined = CombineOutput(outputText, errorText);

            if (cancellationToken.IsCancellationRequested)
            {
                return CommandResult.Failure(Localization.Get("main.cancelled"), exitCode: -1, isCancelled: true);
            }

            if (process.ExitCode == 0)
            {
                return CommandResult.Success(combined);
            }

            return CommandResult.Failure(
                combined.Length > 0 ? combined : Localization.Get("main.exitcode", process.ExitCode),
                exitCode: process.ExitCode);
        }
        catch (OperationCanceledException)
        {
            TryKill(process);

            if (cancellationToken.IsCancellationRequested)
            {
                return CommandResult.Failure(Localization.Get("main.cancelled"), exitCode: -1, isCancelled: true);
            }

            return CommandResult.Failure(
                Localization.Get("main.timeout", timeoutSeconds),
                exitCode: -1,
                isTimeout: true);
        }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine($"[CommandExecutor] 异常: {ex.GetType().Name}: {ex.Message}");
            return CommandResult.Failure(ex.Message, exitCode: -1);
        }
        finally
        {
            lock (_stateLock)
            {
                if (_currentProcess == process)
                {
                    _currentProcess = null;
                    _currentCts = null;
                }
            }
            process.Dispose();
        }
    }

    public void CancelCurrent()
    {
        Process? process;
        CancellationTokenSource? cts;

        lock (_stateLock)
        {
            process = _currentProcess;
            cts = _currentCts;
            _currentProcess = null;
            _currentCts = null;
        }

        try { cts?.Cancel(); } catch { }
        if (process != null) TryKill(process);
    }

    private static void TryKill(Process process)
    {
        try
        {
            if (!process.HasExited)
                process.Kill(entireProcessTree: true);
        }
        catch { }
    }

    private static string CombineOutput(string output, string error)
    {
        if (string.IsNullOrEmpty(error)) return output;
        if (string.IsNullOrEmpty(output)) return error;
        return output + Environment.NewLine + error;
    }

    private static string ResolvePwshPath()
    {
        var localPwsh = Path.Combine(AppContext.BaseDirectory, "pwsh", "pwsh.exe");
        if (File.Exists(localPwsh)) return localPwsh;
        return "pwsh.exe";
    }

    /// <summary>
    /// 用 -EncodedCommand 传命令，避免引号/特殊字符转义问题。
    /// PowerShell 要求 UTF-16LE 的 Base64。
    /// </summary>
    private static string BuildArguments(string command)
    {
        var script = "[Console]::OutputEncoding=[System.Text.Encoding]::UTF8; " + command;
        var bytes = Encoding.Unicode.GetBytes(script);
        var encoded = Convert.ToBase64String(bytes);
        return $"-NoProfile -NonInteractive -EncodedCommand {encoded}";
    }
}

public sealed class CommandResult
{
    public bool IsSuccess { get; init; }
    public string Output { get; init; } = string.Empty;
    public int ExitCode { get; init; }
    public bool IsTimeout { get; init; }
    public bool IsCancelled { get; init; }
    public string? ErrorMessage { get; init; }

    public static CommandResult Success(string output) => new()
    {
        IsSuccess = true,
        Output = output,
        ExitCode = 0,
    };

    public static CommandResult Failure(
        string errorMessage,
        int exitCode = -1,
        bool isTimeout = false,
        bool isCancelled = false) => new()
    {
        IsSuccess = false,
        Output = errorMessage,
        ErrorMessage = errorMessage,
        ExitCode = exitCode,
        IsTimeout = isTimeout,
        IsCancelled = isCancelled,
    };
}