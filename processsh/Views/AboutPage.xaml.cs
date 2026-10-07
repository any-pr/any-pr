using System.Reflection;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using ProcessSH.Models;

namespace ProcessSH.Views;

public sealed partial class AboutPage : Page
{
    public AboutPage()
    {
        InitializeComponent();
        ApplyLocalization();
        Localization.LanguageChanged += ApplyLocalization;
        Unloaded += OnUnloaded;
    }

    private void OnUnloaded(object sender, RoutedEventArgs e)
    {
        Localization.LanguageChanged -= ApplyLocalization;
    }

    private void ApplyLocalization()
    {
        // 从程序集读版本（对应 csproj 里的 <Version>）
        var version = Assembly.GetExecutingAssembly()
            .GetName().Version?.ToString(3) ?? "1.0.0";

        var versionFormat = Localization.Get("about.version");

        // 如果 JSON 里是 "版本 {0}" 这种带占位符的，格式化；否则直接显示
        VersionText.Text = versionFormat.Contains("{0}")
            ? string.Format(versionFormat, version)
            : versionFormat;

        DescriptionText.Text = Localization.Get("about.description");
        RepoLink.Content = Localization.Get("about.repo");
        CopyrightText.Text = Localization.Get("about.copyright");
    }
}