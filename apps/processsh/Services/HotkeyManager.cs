using System.Runtime.InteropServices;
using Microsoft.UI.Xaml;
using WinRT.Interop;

namespace ProcessSH.Services;

/// <summary>
/// 全局快捷键管理器。使用 Win32 RegisterHotKey，由系统直接投递 WM_HOTKEY 消息。
/// 不依赖低级键盘钩子，不会因 UI 线程阻塞而被系统移除。
/// </summary>
public sealed class HotkeyManager : IDisposable
{
    private const int WM_HOTKEY = 0x0312;
    private const int HOTKEY_ID = 100;

    [DllImport("user32.dll", SetLastError = true)]
    private static extern bool RegisterHotKey(IntPtr hWnd, int id, uint fsModifiers, uint vk);

    [DllImport("user32.dll", SetLastError = true)]
    private static extern bool UnregisterHotKey(IntPtr hWnd, int id);

    [DllImport("user32.dll")]
    private static extern IntPtr SetWindowLongPtr(IntPtr hWnd, int nIndex, IntPtr dwNewLong);

    [DllImport("user32.dll")]
    private static extern IntPtr CallWindowProc(IntPtr lpPrevWndFunc, IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam);

    private delegate IntPtr WndProcDelegate(IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam);

    private const int GWLP_WNDPROC = -4;

    private readonly IntPtr _hwnd;
    private IntPtr _originalWndProc;
    private readonly WndProcDelegate _wndProcDelegate;
    private Action? _callback;
    private bool _registered;
    private bool _disposed;

    public const uint MOD_ALT = 0x0001;
    public const uint MOD_CONTROL = 0x0002;
    public const uint MOD_SHIFT = 0x0004;
    public const uint MOD_WIN = 0x0008;

    public HotkeyManager(Window window)
    {
        _hwnd = WindowNative.GetWindowHandle(window);
        System.Diagnostics.Debug.WriteLine($"[Hotkey] 构造, HWND = 0x{_hwnd:X}");

        _wndProcDelegate = WndProc;

        if (_hwnd == IntPtr.Zero)
        {
            System.Diagnostics.Debug.WriteLine("[Hotkey] HWND 为 0，子类化跳过");
            return;
        }

        _originalWndProc = SetWindowLongPtr(_hwnd, GWLP_WNDPROC,
            Marshal.GetFunctionPointerForDelegate(_wndProcDelegate));
        System.Diagnostics.Debug.WriteLine($"[Hotkey] 子类化完成, original=0x{_originalWndProc:X}");
    }

    /// <summary>
    /// 注册或重新注册全局快捷键。
    /// 先注销旧键，再用固定 ID 注册新键。
    /// </summary>
    public bool Register(uint modifiers, uint virtualKey, Action callback)
    {
        if (_disposed) return false;

        if (_hwnd == IntPtr.Zero)
        {
            System.Diagnostics.Debug.WriteLine("[Hotkey] HWND 为 0，无法注册");
            return false;
        }

        // 先注销旧键
        if (_registered)
        {
            UnregisterHotKey(_hwnd, HOTKEY_ID);
            _registered = false;
        }

        // 注册新键
        if (!RegisterHotKey(_hwnd, HOTKEY_ID, modifiers, virtualKey))
        {
            var err = Marshal.GetLastWin32Error();
            System.Diagnostics.Debug.WriteLine(
                $"[Hotkey] RegisterHotKey 失败: mods={modifiers}, vk={virtualKey}, err={err}");
            return false;
        }

        _callback = callback;
        _registered = true;

        System.Diagnostics.Debug.WriteLine(
            $"[Hotkey] 注册成功: mods={modifiers}, vk={virtualKey}");
        return true;
    }

    private IntPtr WndProc(IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam)
    {
        if (msg == WM_HOTKEY && wParam.ToInt32() == HOTKEY_ID)
        {
            System.Diagnostics.Debug.WriteLine("[Hotkey] WM_HOTKEY 触发");
            try { _callback?.Invoke(); } catch { }
            return IntPtr.Zero;
        }

        return CallWindowProc(_originalWndProc, hWnd, msg, wParam, lParam);
    }

    /// <summary>把 "Ctrl+Alt+R" 解析成修饰键和虚拟键码</summary>
    public static (uint modifiers, uint vk)? Parse(string hotkey)
    {
        if (string.IsNullOrWhiteSpace(hotkey)) return null;

        uint modifiers = 0;
        uint? vk = null;

        var parts = hotkey.Split('+', StringSplitOptions.RemoveEmptyEntries);
        foreach (var raw in parts)
        {
            var part = raw.Trim();

            if (part.Equals("Ctrl", StringComparison.OrdinalIgnoreCase) ||
                part.Equals("Control", StringComparison.OrdinalIgnoreCase))
                modifiers |= MOD_CONTROL;
            else if (part.Equals("Alt", StringComparison.OrdinalIgnoreCase))
                modifiers |= MOD_ALT;
            else if (part.Equals("Shift", StringComparison.OrdinalIgnoreCase))
                modifiers |= MOD_SHIFT;
            else if (part.Equals("Win", StringComparison.OrdinalIgnoreCase))
                modifiers |= MOD_WIN;
            else
            {
                if (part.Length == 1)
                {
                    var c = char.ToUpperInvariant(part[0]);
                    if (c >= 'A' && c <= 'Z')
                        vk = (uint)c;
                    else if (c >= '0' && c <= '9')
                        vk = (uint)c;
                }
            }
        }

        if (vk == null || modifiers == 0) return null;
        return (modifiers, vk.Value);
    }

    public void Dispose()
    {
        if (_disposed) return;
        _disposed = true;

        try
        {
            if (_registered)
            {
                UnregisterHotKey(_hwnd, HOTKEY_ID);
                _registered = false;
            }

            if (_originalWndProc != IntPtr.Zero && _hwnd != IntPtr.Zero)
            {
                SetWindowLongPtr(_hwnd, GWLP_WNDPROC, _originalWndProc);
                _originalWndProc = IntPtr.Zero;
            }
        }
        catch { }
    }
}