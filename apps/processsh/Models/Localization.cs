using System.Text.Json;

namespace ProcessSH.Models;

public enum AppLanguage
{
    SimplifiedChinese,
    TraditionalChinese,
    English,
}

/// <summary>
/// 运行时本地化。从 Assets/Localization/{lang}.json 读取键值。
/// </summary>
public static class Localization
{
    private static readonly object _lock = new();
    private static Dictionary<string, string> _strings = new(StringComparer.Ordinal);

    private static AppLanguage _current = AppLanguage.SimplifiedChinese;
    private static bool _initialized;

    public static event Action? LanguageChanged;

    public static AppLanguage Current
    {
        get => _current;
        set
        {
            // 第一次必须初始化；之后只有值真的变了才重新加载
            if (_initialized && _current == value) return;

            _current = value;
            _initialized = true;
            Initialize();
            LanguageChanged?.Invoke();
        }
    }

    public static void Initialize()
    {
        var langFile = _current switch
        {
            AppLanguage.English => "en.json",
            AppLanguage.TraditionalChinese => "zh-Hant.json",
            AppLanguage.SimplifiedChinese => "zh-Hans.json",
            _ => "zh-Hans.json",
        };

        var path = Path.Combine(AppContext.BaseDirectory, "Assets", "Localization", langFile);

        var loaded = new Dictionary<string, string>(StringComparer.Ordinal);
        try
        {
            if (File.Exists(path))
            {
                var json = File.ReadAllText(path);
                var dict = JsonSerializer.Deserialize<Dictionary<string, string>>(json);
                if (dict != null)
                {
                    foreach (var kv in dict)
                        loaded[kv.Key] = kv.Value;
                }
            }
            else
            {
                System.Diagnostics.Debug.WriteLine($"[Localization] 文件不存在: {path}");
            }
        }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine($"[Localization] 加载失败: {ex.Message}");
        }

        lock (_lock)
        {
            _strings = loaded;
        }

        System.Diagnostics.Debug.WriteLine(
            $"[Localization] Initialize: _current={_current}, file={langFile}, count={loaded.Count}");
    }

    public static string Get(string key)
    {
        lock (_lock)
        {
            if (_strings.TryGetValue(key, out var value))
                return value;
        }

        System.Diagnostics.Debug.WriteLine(
            $"[Localization] 缺失 key: {key}, _current={_current}, 词条数={_strings.Count}");
        return key;
    }

    public static string Get(string key, params object?[] args)
    {
        var format = Get(key);
        try
        {
            return string.Format(format, args);
        }
        catch
        {
            return format;
        }
    }
}