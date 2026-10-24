﻿using System.Collections.Generic;
using Microsoft.UI.Xaml;
using ProcessSH.Models;
using ProcessSH.Services;

namespace ProcessSH;

public partial class App : Application
{
    private HotkeyManager? _hotkey;

    public static MainWindow? MainWindowInstance { get; private set; }

    /// <summary>所有打开的主窗口（单窗口模式下最多 1 个）</summary>
    public static List<MainWindow> AllWindows { get; } = new();

    /// <summary>当前全局快捷键管理器实例</summary>
    public static HotkeyManager? HotkeyInstance { get; private set; }

    public App()
    {
        InitializeComponent();
    }

    protected override void OnLaunched(LaunchActivatedEventArgs args)
    {
        // 1. 加载设置
        var settings = AppSettings.Current;

        // 2. 初始化本地化
        Localization.Current = settings.Language;

        // 3. 创建（或复用）唯一主窗口
        var window = GetOrCreateMainWindow();
        window.Activate();

        // 4. 延迟创建 HotkeyManager，确保窗口 HWND 已就绪
        window.DispatcherQueue.TryEnqueue(() =>
        {
            try
            {
                _hotkey = new HotkeyManager(window);
                HotkeyInstance = _hotkey;

                ApplyHotkey(settings.ToggleHotkey);
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"[App] Hotkey 初始化异常: {ex}");
            }
        });

        AppDomain.CurrentDomain.ProcessExit += (s, e) =>
        {
            _hotkey?.Dispose();
        };
    }

    /// <summary>
    /// 获取或创建唯一主窗口。
    /// 已存在则直接返回；否则新建并登记。
    /// 注意：不订阅 Closed，避免窗口隐藏时把 MainWindowInstance 置空。
    /// </summary>
    public static MainWindow GetOrCreateMainWindow()
    {
        if (MainWindowInstance != null)
            return MainWindowInstance;

        var w = new MainWindow();
        MainWindowInstance = w;
        AllWindows.Add(w);
        return w;
    }

    /// <summary>
    /// 应用新的全局快捷键。成功返回 true。
    /// 注册失败时回滚到旧的快捷键（如果旧快捷键仍有效）。
    /// </summary>
    public static bool ApplyHotkey(string hotkeyString)
    {
        if (HotkeyInstance == null || MainWindowInstance == null)
        {
            System.Diagnostics.Debug.WriteLine(
                $"[App] ApplyHotkey 跳过: HotkeyInstance={HotkeyInstance != null}, MainWindowInstance={MainWindowInstance != null}");
            return false;
        }

        var parsed = HotkeyManager.Parse(hotkeyString);
        if (parsed == null)
        {
            System.Diagnostics.Debug.WriteLine($"[App] ApplyHotkey 解析失败: {hotkeyString}");
            return false;
        }

        var previous = AppSettings.Current.ToggleHotkey;

        var ok = HotkeyInstance.Register(
            parsed.Value.modifiers,
            parsed.Value.vk,
            () => MainWindowInstance?.ToggleWindow());

        System.Diagnostics.Debug.WriteLine($"[App] ApplyHotkey: {hotkeyString} => {ok}");

        if (!ok && !string.Equals(previous, hotkeyString, StringComparison.Ordinal))
        {
            var oldParsed = HotkeyManager.Parse(previous);
            if (oldParsed != null)
            {
                HotkeyInstance.Register(
                    oldParsed.Value.modifiers,
                    oldParsed.Value.vk,
                    () => MainWindowInstance?.ToggleWindow());
            }
        }

        return ok;
    }
}