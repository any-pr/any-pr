using System.Collections.ObjectModel;
using ProcessSH.Models;
using ProcessSH.Services;

namespace ProcessSH.ViewModels;

public sealed class CommandViewModel : IDisposable
{
    private readonly CommandHistory _history = CommandHistory.Shared;
    private CancellationTokenSource? _currentCts;

    private int _historyIndex = -1;
    private List<string> _historyCommands = new();
    private string _currentInputBackup = string.Empty;

    private string _inputText = string.Empty;
    public string InputText
    {
        get => _inputText;
        set
        {
            if (_inputText == value) return;
            _inputText = value;
            OnPropertyChanged(nameof(InputText));
            ResetHistoryNavigation();
        }
    }

    private string _outputText = string.Empty;
    public string OutputText
    {
        get => _outputText;
        private set { _outputText = value; OnPropertyChanged(nameof(OutputText)); }
    }

    private bool _isRunning;
    public bool IsRunning
    {
        get => _isRunning;
        private set { _isRunning = value; OnPropertyChanged(nameof(IsRunning)); }
    }

    private bool _canCancel;
    public bool CanCancel
    {
        get => _canCancel;
        private set { _canCancel = value; OnPropertyChanged(nameof(CanCancel)); }
    }

    private bool _useSudo;
    public bool UseSudo
    {
        get => _useSudo;
        set { _useSudo = value; OnPropertyChanged(nameof(UseSudo)); }
    }

    public ObservableCollection<Suggestion> Suggestions { get; } = new();

    private int _selectedIndex;
    public int SelectedIndex
    {
        get => _selectedIndex;
        set { _selectedIndex = value; OnPropertyChanged(nameof(SelectedIndex)); }
    }

    public string CurrentWorkingDirectory { get; private set; }

    public event Action<string>? WindowTitleChanged;
    public event System.ComponentModel.PropertyChangedEventHandler? PropertyChanged;

    private void OnPropertyChanged(string name) =>
        PropertyChanged?.Invoke(this, new System.ComponentModel.PropertyChangedEventArgs(name));

    public CommandViewModel()
    {
        var resolved = AppSettings.Current.ResolvedWorkingDirectory;

        if (string.IsNullOrWhiteSpace(resolved) || !Directory.Exists(resolved))
        {
            resolved = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
        }

        CurrentWorkingDirectory = resolved;
        System.Diagnostics.Debug.WriteLine($"[CommandViewModel] 工作目录: {CurrentWorkingDirectory}");

        UpdateWindowTitle();
    }

    public async Task ExecuteCommandAsync()
    {
        var input = InputText.Trim();
        if (string.IsNullOrEmpty(input)) return;

        var processed = ProcessCommand(input);
        if (processed != input)
        {
            InputText = processed;
        }

        _history.Record(processed);
        ResetHistoryNavigation();

        IsRunning = true;
        CanCancel = true;
        OutputText = string.Empty;

        using var cts = new CancellationTokenSource();
        _currentCts = cts;

        try
        {
            if (UseSudo)
            {
                var elevatedResult = await ElevatedRunner.ExecuteAsync(
                    processed,
                    CurrentWorkingDirectory,
                    10.0);

                OutputText = elevatedResult.IsSuccess
                    ? elevatedResult.Output
                    : $"❌ {elevatedResult.Output}";
                return;
            }

            var result = await CommandExecutor.Shared.ExecuteAsync(
                processed,
                CurrentWorkingDirectory,
                10.0,
                cts.Token);

            if (result.IsTimeout || result.IsCancelled)
            {
                OutputText = result.Output;
            }
            else if (result.IsSuccess)
            {
                OutputText = string.IsNullOrEmpty(result.Output)
                    ? Localization.Get("main.success.nooutput")
                    : result.Output;
            }
            else
            {
                OutputText = string.IsNullOrEmpty(result.Output)
                    ? Localization.Get("main.exitcode", result.ExitCode)
                    : result.Output;
            }
        }
        catch (OperationCanceledException)
        {
            OutputText = Localization.Get("main.cancelled");
        }
        catch (Exception ex)
        {
            OutputText = $"❌ 异常: {ex.Message}";
        }
        finally
        {
            IsRunning = false;
            CanCancel = false;
            _currentCts = null;
        }
    }

    public void CancelExecution()
    {
        if (!IsRunning) return;
        try { _currentCts?.Cancel(); } catch { }
        CommandExecutor.Shared.CancelCurrent();
        IsRunning = false;
        CanCancel = false;
        OutputText = Localization.Get("main.cancelled");
    }

    public void ClearOutput()
    {
        OutputText = string.Empty;
    }

    public void ClearHistory()
    {
        _history.ClearAll();
        ResetHistoryNavigation();
        CloseSuggestions();
    }

    private static string ProcessCommand(string input)
    {
        var trimmed = input.Trim();
        var lower = trimmed.ToLowerInvariant();

        if (lower.StartsWith("start ")) return trimmed;

        if (trimmed.EndsWith(".exe", StringComparison.OrdinalIgnoreCase) ||
            trimmed.EndsWith(".lnk", StringComparison.OrdinalIgnoreCase))
        {
            // start 的第一个引号参数是窗口标题，必须留空
            return trimmed.Contains(' ') ? $"start \"\" \"{trimmed}\"" : $"start {trimmed}";
        }

        return trimmed;
    }

    public void RequestSuggestions()
    {
        if (string.IsNullOrEmpty(InputText))
        {
            CloseSuggestions();
            return;
        }

        var suggester = new CommandSuggester(_history);
        var results = suggester.Suggest(InputText);

        Suggestions.Clear();
        foreach (var s in results)
            Suggestions.Add(s);

        SelectedIndex = 0;
    }

    public void SelectNext()
    {
        if (Suggestions.Count == 0) return;
        SelectedIndex = (SelectedIndex + 1) % Suggestions.Count;
    }

    public void SelectPrevious()
    {
        if (Suggestions.Count == 0) return;
        SelectedIndex = (SelectedIndex - 1 + Suggestions.Count) % Suggestions.Count;
    }

    public void ConfirmSelection()
    {
        if (SelectedIndex < 0 || SelectedIndex >= Suggestions.Count) return;
        ApplySuggestion(Suggestions[SelectedIndex]);
    }

    public void CloseSuggestions()
    {
        Suggestions.Clear();
        SelectedIndex = 0;
    }

    private void ApplySuggestion(Suggestion suggestion)
    {
        var words = InputText.Split(' ', StringSplitOptions.None);
        if (words.Length > 1)
        {
            var prefix = string.Join(' ', words.Take(words.Length - 1));
            InputText = prefix + " " + suggestion.Text;
        }
        else
        {
            InputText = suggestion.Text;
        }

        CloseSuggestions();
        ResetHistoryNavigation();
    }

    public void NavigateHistoryUp()
    {
        if (_historyCommands.Count == 0)
        {
            _historyCommands = _history.GetAll()
                .OrderByDescending(e => e.LastUsed)
                .Select(e => e.Command)
                .ToList();
            _currentInputBackup = InputText;
        }

        if (_historyCommands.Count == 0) return;

        if (_historyIndex == -1)
            _historyIndex = _historyCommands.Count - 1;
        else if (_historyIndex > 0)
            _historyIndex--;

        InputText = _historyCommands[_historyIndex];
    }

    public void NavigateHistoryDown()
    {
        if (_historyCommands.Count == 0) return;

        if (_historyIndex < _historyCommands.Count - 1 && _historyIndex >= 0)
        {
            _historyIndex++;
            InputText = _historyCommands[_historyIndex];
        }
        else if (_historyIndex == _historyCommands.Count - 1)
        {
            _historyIndex = -1;
            InputText = _currentInputBackup;
        }
    }

    private void ResetHistoryNavigation()
    {
        _historyIndex = -1;
        _historyCommands = new List<string>();
        _currentInputBackup = string.Empty;
    }

    private void UpdateWindowTitle()
    {
        WindowTitleChanged?.Invoke("ProcessSH");
    }

    public void Dispose()
    {
        _currentCts?.Dispose();
    }
}