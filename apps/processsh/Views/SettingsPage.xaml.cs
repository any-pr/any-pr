using Microsoft.UI.Input;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using ProcessSH.Models;
using Windows.Storage.Pickers;
using Windows.System;
using Windows.UI.Core;

namespace ProcessSH.Views;

public sealed partial class SettingsPage : Page
{
    private bool _recordingHotkey;
    private bool _loading = true;

    public SettingsPage()
    {
        InitializeComponent();
        ApplyLocalization();
        LoadSettings();
        Localization.LanguageChanged += ApplyLocalization;
        Unloaded += OnUnloaded;
        _loading = false;
    }

    private void OnUnloaded(object sender, RoutedEventArgs e)
    {
        Localization.LanguageChanged -= ApplyLocalization;

        if (_recordingHotkey)
            StopRecording();
    }

    private void ApplyLocalization()
    {
        LanguageHeader.Text = Localization.Get("settings.language");
        WindowHeader.Text = Localization.Get("settings.window");
        HideOnDeactivateToggle.Content = Localization.Get("settings.hideOnDeactivate");
        WorkingDirHeader.Text = Localization.Get("settings.workingdir");
        ChooseDirButton.Content = Localization.Get("settings.workingdir.choose");
        ResetDirButton.Content = Localization.Get("settings.workingdir.reset");
        WorkingDirWarning.Text = Localization.Get("settings.workingdir.invalid");
        HotkeyHeader.Text = Localization.Get("settings.hotkey");
        RecordHotkeyButton.Content = Localization.Get("settings.hotkey.record");
        ResetHotkeyButton.Content = Localization.Get("settings.hotkey.reset");
        HotkeyBox.PlaceholderText = Localization.Get("settings.hotkey.placeholder");
        PowerShellHeader.Text = Localization.Get("settings.powershell");
        PowerShellDesc.Text = Localization.Get("settings.powershell.desc");
        InstallPowerShellButton.Content = Localization.Get("settings.powershell.install");

        if (!_recordingHotkey)
            HotkeyStatus.Text = Localization.Get("settings.hotkey.hint");
    }

    private void LoadSettings()
    {
        var s = AppSettings.Current;

        HideOnDeactivateToggle.IsChecked = s.HideOnDeactivate;
        HotkeyBox.Text = s.ToggleHotkey;

        for (int i = 0; i < LanguageCombo.Items.Count; i++)
        {
            if (LanguageCombo.Items[i] is ComboBoxItem item &&
                item.Tag is string tag &&
                tag == s.Language.ToString())
            {
                LanguageCombo.SelectedIndex = i;
                break;
            }
        }

        WorkingDirBox.Text = string.IsNullOrEmpty(s.DefaultWorkingDirectory)
            ? Environment.GetFolderPath(Environment.SpecialFolder.UserProfile)
            : s.DefaultWorkingDirectory;

        WorkingDirWarning.Visibility = s.IsWorkingDirectoryValid
            ? Visibility.Collapsed
            : Visibility.Visible;
    }

    private void LanguageCombo_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (_loading) return;
        if (LanguageCombo.SelectedItem is not ComboBoxItem item) return;
        if (item.Tag is not string tag) return;

        if (Enum.TryParse<AppLanguage>(tag, out var lang))
        {
            AppSettings.Current.Language = lang;
            AppSettings.Current.Save();
            Localization.Current = lang;
        }
    }

    private void HideOnDeactivateToggle_Click(object sender, RoutedEventArgs e)
    {
        AppSettings.Current.HideOnDeactivate = HideOnDeactivateToggle.IsChecked == true;
        AppSettings.Current.Save();
    }

    private async void ChooseWorkingDir_Click(object sender, RoutedEventArgs e)
    {
        var host = App.MainWindowInstance;
        if (host == null) return;

        var picker = new FolderPicker();
        picker.SuggestedStartLocation = PickerLocationId.ComputerFolder;

        var hwnd = WinRT.Interop.WindowNative.GetWindowHandle(host);
        WinRT.Interop.InitializeWithWindow.Initialize(picker, hwnd);

        var folder = await picker.PickSingleFolderAsync();
        if (folder != null)
        {
            AppSettings.Current.DefaultWorkingDirectory = folder.Path;
            AppSettings.Current.Save();
            LoadSettings();
        }
    }

    private void ResetWorkingDir_Click(object sender, RoutedEventArgs e)
    {
        AppSettings.Current.DefaultWorkingDirectory = string.Empty;
        AppSettings.Current.Save();
        LoadSettings();
    }

    // ─────────────────────────────────────────────
    // 快捷键录制
    // ─────────────────────────────────────────────

    private void RecordHotkey_Click(object sender, RoutedEventArgs e)
    {
        if (_recordingHotkey) return;

        _recordingHotkey = true;

        HotkeyBox.Text = Localization.Get("settings.hotkey.recording");
        HotkeyStatus.Text = Localization.Get("settings.hotkey.recording.hint");

        this.AddHandler(
            UIElement.KeyDownEvent,
            new Microsoft.UI.Xaml.Input.KeyEventHandler(OnHotkeyRecordingKeyDown),
            true);

        this.Focus(FocusState.Programmatic);
    }

    private void OnHotkeyRecordingKeyDown(object sender, Microsoft.UI.Xaml.Input.KeyRoutedEventArgs e)
    {
        if (!_recordingHotkey) return;

        // 修饰键单独按下不处理
        if (e.Key == VirtualKey.Control ||
            e.Key == VirtualKey.Menu ||
            e.Key == VirtualKey.Shift ||
            e.Key == VirtualKey.LeftWindows ||
            e.Key == VirtualKey.RightWindows)
        {
            e.Handled = true;
            return;
        }

        // Esc 取消
        if (e.Key == VirtualKey.Escape)
        {
            e.Handled = true;
            StopRecording();
            HotkeyBox.Text = AppSettings.Current.ToggleHotkey;
            HotkeyStatus.Text = Localization.Get("settings.hotkey.cancelled");
            return;
        }

        e.Handled = true;

        var ctrl = InputKeyboardSource.GetKeyStateForCurrentThread(VirtualKey.Control)
            .HasFlag(CoreVirtualKeyStates.Down);
        var alt = InputKeyboardSource.GetKeyStateForCurrentThread(VirtualKey.Menu)
            .HasFlag(CoreVirtualKeyStates.Down);
        var shift = InputKeyboardSource.GetKeyStateForCurrentThread(VirtualKey.Shift)
            .HasFlag(CoreVirtualKeyStates.Down);
        var win = InputKeyboardSource.GetKeyStateForCurrentThread(VirtualKey.LeftWindows)
            .HasFlag(CoreVirtualKeyStates.Down)
            || InputKeyboardSource.GetKeyStateForCurrentThread(VirtualKey.RightWindows)
            .HasFlag(CoreVirtualKeyStates.Down);

        if (!ctrl && !alt && !shift && !win)
        {
            HotkeyStatus.Text = Localization.Get("settings.hotkey.needModifier");
            return;
        }

        // 禁止 Win 组合，RegisterHotKey 对 Win 支持不可靠
        if (win)
        {
            HotkeyStatus.Text = "不支持 Win 组合键，请使用 Ctrl / Alt / Shift";
            return;
        }

        var keyChar = KeyToChar(e.Key);
        if (keyChar == null)
        {
            HotkeyStatus.Text = Localization.Get("settings.hotkey.needKey");
            return;
        }

        var parts = new List<string>();
        if (ctrl) parts.Add("Ctrl");
        if (alt) parts.Add("Alt");
        if (shift) parts.Add("Shift");
        parts.Add(keyChar);

        var hotkeyString = string.Join("+", parts);

        System.Diagnostics.Debug.WriteLine($"[Settings] 录制: {hotkeyString}");

        var ok = App.ApplyHotkey(hotkeyString);
        System.Diagnostics.Debug.WriteLine($"[Settings] ApplyHotkey 返回: {ok}");

        if (ok)
        {
            AppSettings.Current.ToggleHotkey = hotkeyString;
            AppSettings.Current.Save();
            HotkeyBox.Text = hotkeyString;
            HotkeyStatus.Text = Localization.Get("settings.hotkey.saved");
        }
        else
        {
            HotkeyStatus.Text = Localization.Get("settings.hotkey.conflict");
        }

        StopRecording();
    }

    private void StopRecording()
    {
        if (!_recordingHotkey) return;

        _recordingHotkey = false;
        this.RemoveHandler(
            UIElement.KeyDownEvent,
            new Microsoft.UI.Xaml.Input.KeyEventHandler(OnHotkeyRecordingKeyDown));
    }

    private static string? KeyToChar(VirtualKey key)
    {
        if (key >= VirtualKey.A && key <= VirtualKey.Z)
            return ((char)('A' + (key - VirtualKey.A))).ToString();
        if (key >= VirtualKey.Number0 && key <= VirtualKey.Number9)
            return ((char)('0' + (key - VirtualKey.Number0))).ToString();
        return null;
    }

    private void ResetHotkey_Click(object sender, RoutedEventArgs e)
    {
        const string defaultHotkey = "Ctrl+Alt+R";

        if (App.ApplyHotkey(defaultHotkey))
        {
            AppSettings.Current.ToggleHotkey = defaultHotkey;
            AppSettings.Current.Save();
            HotkeyBox.Text = defaultHotkey;
            HotkeyStatus.Text = Localization.Get("settings.hotkey.resetdone");
        }
        else
        {
            HotkeyStatus.Text = Localization.Get("settings.hotkey.resetfailed");
        }
    }

    private async void InstallPowerShell_Click(object sender, RoutedEventArgs e)
    {
        var storeUri = new Uri("ms-windows-store://pdp/?productid=9MZ1SNWT0N5D");

        if (!await Windows.System.Launcher.LaunchUriAsync(storeUri))
        {
            await Windows.System.Launcher.LaunchUriAsync(
                new Uri("https://apps.microsoft.com/detail/9mz1snwt0n5d"));
        }
    }
}