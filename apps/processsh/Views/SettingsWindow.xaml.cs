using Microsoft.UI;
using Microsoft.UI.Windowing;
using Microsoft.UI.Xaml;
using ProcessSH.Models;
using WinRT.Interop;
using Windows.Graphics;

namespace ProcessSH.Views;

public sealed partial class SettingsWindow : Window
{
    public SettingsWindow()
    {
        InitializeComponent();

        var hwnd = WindowNative.GetWindowHandle(this);
        var windowId = Win32Interop.GetWindowIdFromWindow(hwnd);
        var appWindow = AppWindow.GetFromWindowId(windowId);

        appWindow.Resize(new SizeInt32(480, 640));
        TrySetIcon(appWindow);

        if (appWindow.Presenter is OverlappedPresenter presenter)
        {
            presenter.IsMaximizable = false;
            presenter.IsMinimizable = false;
        }

        // 应用本地化标题
        ApplyLocalization();
        Localization.LanguageChanged += ApplyLocalization;

        Closed += (s, e) =>
        {
            Localization.LanguageChanged -= ApplyLocalization;
        };

        RootFrame.Navigate(typeof(SettingsPage));
    }

    private void ApplyLocalization()
    {
        Title = Localization.Get("settings.title");
    }

    private static void TrySetIcon(AppWindow appWindow)
    {
        try
        {
            var icon = Path.Combine(AppContext.BaseDirectory, "Assets", "AppIcon.ico");
            if (File.Exists(icon))
                appWindow.SetIcon(icon);
        }
        catch { }
    }
}