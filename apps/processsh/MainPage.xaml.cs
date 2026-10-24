using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Microsoft.UI.Xaml.Media;
using ProcessSH.Models;
using ProcessSH.ViewModels;
using Windows.System;

namespace ProcessSH;

public sealed partial class MainPage : Page
{
    private CommandViewModel? _viewModel;
    private MainWindow? _hostWindow;

    public MainPage()
    {
        InitializeComponent();
        Loaded += OnLoaded;
        Unloaded += OnUnloaded;
    }

    private void OnLoaded(object sender, RoutedEventArgs e)
    {
        _hostWindow = FindHostWindow();

        _viewModel = new CommandViewModel();
        _viewModel.PropertyChanged += OnViewModelPropertyChanged;
        _viewModel.WindowTitleChanged += OnWindowTitleChanged;

        SudoToggle.IsChecked = _viewModel.UseSudo;

        ApplyLocalization();
        Localization.LanguageChanged += ApplyLocalization;

        // 页面加载完成后主动聚焦一次
        _ = FocusInputAsync();
    }

    private void OnUnloaded(object sender, RoutedEventArgs e)
    {
        Localization.LanguageChanged -= ApplyLocalization;

        if (_viewModel != null)
        {
            _viewModel.PropertyChanged -= OnViewModelPropertyChanged;
            _viewModel.WindowTitleChanged -= OnWindowTitleChanged;
            _viewModel.Dispose();
            _viewModel = null;
        }
    }

    private MainWindow? FindHostWindow()
    {
        foreach (var w in App.AllWindows)
        {
            if (w.Content is FrameworkElement root && root.XamlRoot == this.XamlRoot)
                return w;
        }
        return App.MainWindowInstance;
    }

    private void ApplyLocalization()
    {
        DispatcherQueue.TryEnqueue(() =>
        {
            InputBox.PlaceholderText = Localization.Get("main.placeholder");
            SudoToggle.Content = Localization.Get("main.sudo");
            HintBlock.Text = Localization.Get("main.hint");
            ToolTipService.SetToolTip(CancelButton, Localization.Get("main.cancel.tooltip"));
            ToolTipService.SetToolTip(ClearButton, Localization.Get("main.clear.tooltip"));
        });
    }

    public void Refresh()
    {
        if (_viewModel != null)
        {
            _viewModel.PropertyChanged -= OnViewModelPropertyChanged;
            _viewModel.WindowTitleChanged -= OnWindowTitleChanged;
            _viewModel.Dispose();
        }

        _viewModel = new CommandViewModel();
        _viewModel.PropertyChanged += OnViewModelPropertyChanged;
        _viewModel.WindowTitleChanged += OnWindowTitleChanged;

        OutputBlock.Text = string.Empty;
        OutputScroll.Visibility = Visibility.Collapsed;
        HintBlock.Visibility = Visibility.Visible;
        ClearButton.Visibility = Visibility.Collapsed;

        SudoToggle.IsChecked = _viewModel.UseSudo;

        _hostWindow?.ResizeForOutput(0);

        _ = FocusInputAsync();
    }

    private void OnViewModelPropertyChanged(object? sender, System.ComponentModel.PropertyChangedEventArgs e)
    {
        if (_viewModel == null) return;

        DispatcherQueue.TryEnqueue(() =>
        {
            switch (e.PropertyName)
            {
                case nameof(CommandViewModel.OutputText):
                    OutputBlock.Text = _viewModel.OutputText;
                    var hasOutput = !string.IsNullOrEmpty(_viewModel.OutputText);
                    OutputScroll.Visibility = hasOutput ? Visibility.Visible : Visibility.Collapsed;
                    HintBlock.Visibility = hasOutput ? Visibility.Collapsed : Visibility.Visible;
                    ClearButton.Visibility = hasOutput ? Visibility.Visible : Visibility.Collapsed;

                    if (hasOutput)
                    {
                        var lineCount = _viewModel.OutputText.Split('\n').Length;
                        _hostWindow?.ResizeForOutput(lineCount);
                    }
                    else
                    {
                        _hostWindow?.ResizeForOutput(0);
                    }
                    break;

                case nameof(CommandViewModel.IsRunning):
                    BusyRing.IsActive = _viewModel.IsRunning;
                    CancelButton.Visibility = _viewModel.IsRunning ? Visibility.Visible : Visibility.Collapsed;
                    InputBox.IsEnabled = !_viewModel.IsRunning;
                    break;

                case nameof(CommandViewModel.InputText):
                    if (InputBox.Text != _viewModel.InputText)
                        InputBox.Text = _viewModel.InputText;
                    break;

                case nameof(CommandViewModel.UseSudo):
                    if (SudoToggle.IsChecked != _viewModel.UseSudo)
                        SudoToggle.IsChecked = _viewModel.UseSudo;
                    break;
            }
        });
    }

    private void OnWindowTitleChanged(string title)
    {
    }

    // ─────────────────────────────────────────────
    // 焦点
    // ─────────────────────────────────────────────

    /// <summary>
    /// 把焦点交给输入框。多次重试 + 找内部 TextBox，
    /// 解决 Hide/Show 后焦点不落到 AutoSuggestBox 的问题。
    /// </summary>
    public async Task FocusInputAsync()
    {
        // 确保输入框可用
        if (!InputBox.IsEnabled)
            InputBox.IsEnabled = true;

        for (int i = 0; i < 8; i++)
        {
            try
            {
                InputBox.Focus(FocusState.Programmatic);

                // AutoSuggestBox 内部有一个 TextBox，确保它也拿到焦点
                var inner = FindDescendant<TextBox>(InputBox);
                inner?.Focus(FocusState.Programmatic);

                if (InputBox.FocusState != FocusState.Unfocused ||
                    (inner != null && inner.FocusState != FocusState.Unfocused))
                {
                    return;
                }
            }
            catch { }

            await Task.Delay(40);
        }
    }

    /// <summary>兼容旧调用（同步版）</summary>
    public void FocusInput()
    {
        _ = FocusInputAsync();
    }

    private static T? FindDescendant<T>(DependencyObject root) where T : DependencyObject
    {
        var count = VisualTreeHelper.GetChildrenCount(root);
        for (int i = 0; i < count; i++)
        {
            var child = VisualTreeHelper.GetChild(root, i);
            if (child is T t) return t;

            var found = FindDescendant<T>(child);
            if (found != null) return found;
        }
        return null;
    }

    // ─────────────────────────────────────────────
    // AutoSuggestBox 事件
    // ─────────────────────────────────────────────

    private void InputBox_TextChanged(AutoSuggestBox sender, AutoSuggestBoxTextChangedEventArgs args)
    {
        if (args.Reason != AutoSuggestionBoxTextChangeReason.UserInput) return;
        if (_viewModel == null) return;

        _viewModel.InputText = sender.Text;
        _viewModel.RequestSuggestions();

        sender.ItemsSource = _viewModel.Suggestions.ToList();
    }

    private async void InputBox_QuerySubmitted(AutoSuggestBox sender, AutoSuggestBoxQuerySubmittedEventArgs args)
    {
        if (_viewModel == null) return;

        if (args.ChosenSuggestion is Suggestion chosen)
        {
            _viewModel.InputText = chosen.Text;
            sender.Text = chosen.Text;
            return;
        }

        _viewModel.InputText = sender.Text;
        await _viewModel.ExecuteCommandAsync();
    }

    private void InputBox_SuggestionChosen(AutoSuggestBox sender, AutoSuggestBoxSuggestionChosenEventArgs args)
    {
        if (args.SelectedItem is Suggestion s)
        {
            sender.Text = s.Text;
        }
    }

    private void InputBox_KeyDown(object sender, KeyRoutedEventArgs e)
    {
        if (_viewModel == null) return;

        var hasSuggestions = _viewModel.Suggestions.Count > 0;

        switch (e.Key)
        {
            case VirtualKey.Tab:
                e.Handled = true;
                _viewModel.InputText = InputBox.Text;
                _viewModel.RequestSuggestions();
                InputBox.ItemsSource = _viewModel.Suggestions.ToList();
                break;

            case VirtualKey.Up:
                if (hasSuggestions)
                {
                    e.Handled = true;
                    _viewModel.SelectPrevious();
                    InputBox.ItemsSource = _viewModel.Suggestions.ToList();
                }
                else
                {
                    e.Handled = true;
                    _viewModel.NavigateHistoryUp();
                    InputBox.Text = _viewModel.InputText;
                }
                break;

            case VirtualKey.Down:
                if (hasSuggestions)
                {
                    e.Handled = true;
                    _viewModel.SelectNext();
                    InputBox.ItemsSource = _viewModel.Suggestions.ToList();
                }
                else
                {
                    e.Handled = true;
                    _viewModel.NavigateHistoryDown();
                    InputBox.Text = _viewModel.InputText;
                }
                break;

            case VirtualKey.Escape:
                e.Handled = true;
                _viewModel.CloseSuggestions();
                InputBox.ItemsSource = null;
                break;
        }
    }

    private void CancelButton_Click(object sender, RoutedEventArgs e)
    {
        _viewModel?.CancelExecution();
    }

    private void ClearButton_Click(object sender, RoutedEventArgs e)
    {
        _viewModel?.ClearOutput();
    }

    private void SudoToggle_Changed(object sender, RoutedEventArgs e)
    {
        if (_viewModel != null)
            _viewModel.UseSudo = SudoToggle.IsChecked == true;
    }
}