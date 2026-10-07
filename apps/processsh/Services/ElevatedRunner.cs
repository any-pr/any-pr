using System.Diagnostics;
using ProcessSH.Models;

namespace ProcessSH.Services;

/// <summary>
/// 按需提权执行器。通过 runas 动词启动独立的提权进程执行单条命令。
/// </summary>
public static class ElevatedRunner
{
    public static async Task<CommandResult> ExecuteAsync(
        string command,
        string workingDirectory,
        double timeoutSeconds = 10.0)
    {
        if (string.IsNullOrWhiteSpace(workingDirectory) || !Directory.Exists(workingDirectory))
        {
            workingDirectory = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
        }

        var psi = new ProcessStartInfo
        {
            FileName = "pwsh.exe",
            Arguments = BuildArguments(command),
            WorkingDirectory = workingDirectory,
            Verb = "runas",
            UseShellExecute = true,
            // UseShellExecute=true 时 CreateNoWindow 无效，这里保留也不影响
        };

        try
        {
            using var process = Process.Start(psi);
            if (process == null)
                return CommandResult.Failure("提权进程启动失败");

            using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(timeoutSeconds));
            try
            {
                await process.WaitForExitAsync(cts.Token).ConfigureAwait(false);
            }
            catch (OperationCanceledException)
            {
                try { process.Kill(entireProcessTree: true); } catch { }
                return CommandResult.Failure(
                    Localization.Get("main.timeout", timeoutSeconds),
                    isTimeout: true);
            }

            return process.ExitCode == 0
                ? CommandResult.Success("命令执行成功（提权模式无输出回传）")
                : CommandResult.Failure(Localization.Get("main.exitcode", process.ExitCode), process.ExitCode);
        }
        catch (System.ComponentModel.Win32Exception)
        {
            return CommandResult.Failure("用户取消了提权请求");
        }
        catch (Exception ex)
        {
            return CommandResult.Failure(ex.Message);
        }
    }

    private static string BuildArguments(string command)
    {
        var script = "[Console]::OutputEncoding=[System.Text.Encoding]::UTF8; " + command;
        var bytes = System.Text.Encoding.Unicode.GetBytes(script);
        var encoded = Convert.ToBase64String(bytes);
        return $"-NoProfile -NonInteractive -EncodedCommand {encoded}";
    }
}