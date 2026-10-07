using System.Runtime.InteropServices;
using System.Windows.Input;
using Microsoft.UI;
using Microsoft.UI.Windowing;
using Microsoft.UI.Xaml;
using ProcessSH.Models;
using ProcessSH.Services;
using ProcessSH.Views;
using WinRT.Interop;
using Windows.Graphics;

namespace ProcessSH;

public sealed partial class MainWindow : Window
{
    private AppWindow _appWindow;
    private bool _hideOnDeactivate;
    private bool _trayMenuOpen;
    private bool _suppressHide;
    private bool _firstActivated;
    private DateTime _lastShownAt = DateTime.MinValue;

    // 子窗口单例
    private SettingsWindow? _settingsWindow;
    private AboutWindow? _aboutWindow;

    private const int WindowWidth = 520;
    private const int BaseHeight = 200;
    private const int MaxHeight = 500;

    [DllImport("user32.dll", SetLastError = true)]
    private static extern bool SetWindowPos(
        IntPtr hWnd,
        IntPtr hWndInsertAfter,
        int X,
        int Y,
        int cx,
        int cy,
        uint uFlags);

    [DllImport("user32.dll")]
    private static extern uint GetDpiForWindow(IntPtr hwnd);

    [DllImport("user32.dll")]
    private static extern bool SetForegroundWindow(IntPtr hWnd);

    [DllImport("user32.dll")]
    private static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);

    private const int SW_SHOW = 5;
    private const uint SWP_NOZORDER = 0x0004;
    private const uint SWP_NOACTIVATE = 0x0010;

    public ICommand ToggleWindowCommand { get; }

    public MainWindow()
    {
        InitializeComponent();

        _appWindow = GetAppWindow();

        ExtendsContentIntoTitleBar = true;
        SetTitleBar(AppTitleBar);

        _appWindow.Resize(new SizeInt32(WindowWidth, BaseHeight));
        PositionWindowBottomLeft();
        _appWindow.IsShownInSwitchers = false;

        TrySetIcon();

        if (_appWindow.Presenter is OverlappedPresenter presenter)
        {
            presenter.IsResizable = false;
            presenter.IsMaximizable = false;
            presenter.IsMinimizable = false;
        }

        RootFrame.Navigate(typeof(MainPage));

        _hideOnDeactivate = AppSettings.Current.HideOnDeactivate;
        Activated += OnWindowActivated;
        Activated += OnFirstActivated;
        Closed += OnWindowClosed;

        ToggleWindowCommand = new RelayCommand(ToggleWindow);

        ApplyLocalization();
        Localization.LanguageChanged += ApplyLocalization;
    }

    private AppWindow GetAppWindow()
    {
        var hwnd = WindowNative.GetWindowHandle(this);
        var windowId = Win32Interop.GetWindowIdFromWindow(hwnd);
        return AppWindow.GetFromWindowId(windowId);
    }

    private void TrySetIcon()
    {
        try
        {
            var icon = Path.Combine(AppContext.BaseDirectory, "Assets", "AppIcon.ico");
            if (File.Exists(icon))
                _appWindow.SetIcon(icon);
        }
        catch { }
    }

   private void ApplyLocalization()
    {
        TrayToggleItem.Text = Localization.Get("tray.toggle");
        TrayClearItem.Text = Localization.Get("tray.clearhistory");
        TraySettingsItem.Text = Localization.Get("tray.settings");
        TrayAboutItem.Text = Localization.Get("tray.about");
        TrayQuitItem.Text = Localization.Get("tray.quit");
    }

    private void PositionWindowBottomLeft()
    {
        var displayArea = DisplayArea.GetFromWindowId(_appWindow.Id, DisplayAreaFallback.Primary);
        if (displayArea == null) return;

        var workArea = displayArea.WorkArea;
        var size = _appWindow.Size;
        int x = workArea.X + 20;
        int y = workArea.Y + workArea.Height - size.Height - 20;

        _appWindow.Move(new PointInt32(x, y));
    }

    /// <summary>
    /// 根据输出行数动态调整窗口高度。
    /// 保持窗口底部不动，只向上扩展（Spotlight 风格）。
    /// </summary>
    public void ResizeForOutput(int lineCount)
    {
        int lineHeight = 20;
        int extra = Math.Max(0, lineCount - 1) * lineHeight;
        int newHeightLogical = Math.Min(BaseHeight + extra, MaxHeight);

        var hwnd = WindowNative.GetWindowHandle(this);
        uint dpi = GetDpiForWindow(hwnd);
        float scalingFactor = dpi / 96f;

        var pos = _appWindow.Position;
        var size = _appWindow.Size;

        int currentBottomPhysical = pos.Y + (int)(size.Height * scalingFactor);
        int newWidthPhysical = (int)(WindowWidth * scalingFactor);
        int newHeightPhysical = (int)(newHeightLogical * scalingFactor);

        int newY = currentBottomPhysical - newHeightPhysical;

        var displayArea = DisplayArea.GetFromWindowId(_appWindow.Id, DisplayAreaFallback.Primary);
        if (displayArea != null)
        {
            var workArea = displayArea.WorkArea;
            if (newY < workArea.Y)
                newY = workArea.Y;
        }

        SetWindowPos(hwnd, IntPtr.Zero,
            pos.X, newY,
            newWidthPhysical,
            newHeightPhysical,
            SWP_NOZORDER | SWP_NOACTIVATE);
    }

    public void SuppressHide(bool suppress)
    {
        _suppressHide = suppress;
    }

    public void RefreshMainPage()
    {
        if (RootFrame.Content is MainPage page)
        {
            page.Refresh();
        }
    }

    private void OnWindowActivated(object sender, WindowActivatedEventArgs args)
    {
        if (!_hideOnDeactivate) return;
        if (_trayMenuOpen) return;
        if (_suppressHide) return;

        if ((DateTime.Now - _lastShownAt).TotalMilliseconds < 500) return;

        if (args.WindowActivationState == WindowActivationState.Deactivated)
        {
            DispatcherQueue.TryEnqueue(async () =>
            {
                await Task.Delay(100);

                if (_trayMenuOpen) return;
                if (_suppressHide) return;
                if ((DateTime.Now - _lastShownAt).TotalMilliseconds < 500) return;

                _appWindow.Hide();
            });
        }
    }

    private void OnFirstActivated(object sender, WindowActivatedEventArgs args)
    {
        if (_firstActivated) return;
        if (args.WindowActivationState == WindowActivationState.Deactivated) return;

        _firstActivated = true;

        DispatcherQueue.TryEnqueue(async () =>
        {
            await Task.Delay(60);
            if (RootFrame.Content is MainPage page)
                page.FocusInput();
        });
    }

    private void TrayMenu_Opening(object sender, object e)
    {
        _trayMenuOpen = true;
    }

    private void TrayMenu_Closed(object sender, object e)
    {
        _trayMenuOpen = false;
    }

    /// <summary>
    /// 关闭主窗口 = 隐藏（不退出）。
    /// 退出只通过托盘菜单“退出”。
    /// </summary>
    private void OnWindowClosed(object sender, WindowEventArgs args)
    {
        args.Handled = true;
        try { _appWindow.Hide(); } catch { }
    }

    /// <summary>
    /// 显示窗口并强制把焦点给输入框。
    /// </summary>
    public void ShowWindow()
    {
        var hwnd = WindowNative.GetWindowHandle(this);

        _lastShownAt = DateTime.Now;

        ShowWindow(hwnd, SW_SHOW);
        SetForegroundWindow(hwnd);
        Activate();

        DispatcherQueue.TryEnqueue(async () =>
        {
            await Task.Delay(60);

            if (RootFrame.Content is MainPage page)
                page.FocusInput();
        });
    }

    public void HideWindow()
    {
        _appWindow.Hide();
    }

    public void ToggleWindow()
    {
        if (_appWindow.IsVisible)
            HideWindow();
        else
            ShowWindow();
    }

    private void TrayToggleWindow_Click(object sender, RoutedEventArgs e)
    {
        ToggleWindow();
    }

    private void TrayClearHistory_Click(object sender, RoutedEventArgs e)
    {
        CommandHistory.Shared.ClearAll();
    }

    private void TraySettings_Click(object sender, RoutedEventArgs e)
    {
        if (_settingsWindow != null)
        {
            _settingsWindow.Activate();
            return;
        }

        _settingsWindow = new SettingsWindow();
        SuppressHide(true);

        _settingsWindow.Closed += (s, args) =>
        {
            _settingsWindow = null;
            SuppressHide(false);
            RefreshMainPage();
        };

        _settingsWindow.Activate();
    }

    private void TrayAbout_Click(object sender, RoutedEventArgs e)
    {
        if (_aboutWindow != null)
        {
            _aboutWindow.Activate();
            return;
        }

        _aboutWindow = new AboutWindow();
        SuppressHide(true);

        _aboutWindow.Closed += (s, args) =>
        {
            _aboutWindow = null;
            SuppressHide(false);
        };

        _aboutWindow.Activate();
    }

    private void TrayQuit_Click(object sender, RoutedEventArgs e)
    {
        Environment.Exit(0);
    }
}

internal sealed class RelayCommand : ICommand
{
    private readonly Action _action;

    public RelayCommand(Action action) => _action = action;

    event EventHandler? ICommand.CanExecuteChanged
    {
        add { }
        remove { }
    }

    public bool CanExecute(object? parameter) => true;

    public void Execute(object? parameter) => _action();
}