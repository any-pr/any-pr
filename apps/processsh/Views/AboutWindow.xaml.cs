using Microsoft.UI;
using Microsoft.UI.Windowing;
using Microsoft.UI.Xaml;
using ProcessSH.Models;
using WinRT.Interop;
using Windows.Graphics;

namespace ProcessSH.Views;

public sealed partial class AboutWindow : Window
{
    public AboutWindow()
    {
        InitializeComponent();

        var hwnd = WindowNative.GetWindowHandle(this);
        var windowId = Win32Interop.GetWindowIdFromWindow(hwnd);
        var appWindow = AppWindow.GetFromWindowId(windowId);

        appWindow.Resize(new SizeInt32(360, 220));
        TrySetIcon(appWindow);

        if (appWindow.Presenter is OverlappedPresenter presenter)
        {
            presenter.IsMaximizable = false;
            presenter.IsMinimizable = false;
            presenter.IsResizable = false;
        }

        // 应用本地化标题
        ApplyLocalization();
        Localization.LanguageChanged += ApplyLocalization;

        Closed += (s, e) =>
        {
            Localization.LanguageChanged -= ApplyLocalization;
        };

        RootFrame.Navigate(typeof(AboutPage));
    }

    private void ApplyLocalization()
    {
        Title = Localization.Get("about.title");
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