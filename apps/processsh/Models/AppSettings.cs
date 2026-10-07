using System.Text.Json;
using System.Text.Json.Serialization;

namespace ProcessSH.Models;

public sealed class AppSettings
{
    private static readonly string SettingsDir =
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "ProcessSH");

    private static readonly string SettingsPath =
        Path.Combine(SettingsDir, "settings.json");

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        WriteIndented = true,
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
        Converters = { new JsonStringEnumConverter() },
    };

    private static AppSettings? _instance;
    private static readonly object _lock = new();

    public static AppSettings Current
    {
        get
        {
            lock (_lock)
            {
                if (_instance == null)
                {
                    _instance = Load();
                }
                return _instance;
            }
        }
    }

    public string DefaultWorkingDirectory { get; set; } = string.Empty;
    public bool HideOnDeactivate { get; set; } = true;

    /// <summary>全局快捷键，默认 Ctrl+Alt+R（不含 Win，避免 RegisterHotKey 注册失败）</summary>
    public string ToggleHotkey { get; set; } = "Ctrl+Alt+R";

    public AppLanguage Language { get; set; } = AppLanguage.SimplifiedChinese;

    [JsonIgnore]
    public string ResolvedWorkingDirectory
    {
        get
        {
            if (string.IsNullOrWhiteSpace(DefaultWorkingDirectory))
                return Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);

            try
            {
                var expanded = Environment.ExpandEnvironmentVariables(DefaultWorkingDirectory);
                if (Directory.Exists(expanded))
                    return expanded;
            }
            catch { }

            return Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
        }
    }

    [JsonIgnore]
    public bool IsWorkingDirectoryValid
    {
        get
        {
            if (string.IsNullOrWhiteSpace(DefaultWorkingDirectory))
                return true;
            try
            {
                var expanded = Environment.ExpandEnvironmentVariables(DefaultWorkingDirectory);
                return Directory.Exists(expanded);
            }
            catch { return false; }
        }
    }

    private static AppSettings Load()
    {
        try
        {
            if (File.Exists(SettingsPath))
            {
                var json = File.ReadAllText(SettingsPath);
                var loaded = JsonSerializer.Deserialize<AppSettings>(json, JsonOptions);
                if (loaded != null)
                {
                    System.Diagnostics.Debug.WriteLine(
                        $"[AppSettings] Loaded: Language={loaded.Language}, " +
                        $"Hotkey={loaded.ToggleHotkey}, HideOnDeactivate={loaded.HideOnDeactivate}, " +
                        $"Dir={loaded.DefaultWorkingDirectory}");
                    return loaded;
                }
            }
        }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine($"[AppSettings] Load 失败: {ex.Message}");
        }

        System.Diagnostics.Debug.WriteLine("[AppSettings] 使用默认值");
        return new AppSettings();
    }

    public void Save()
    {
        try
        {
            Directory.CreateDirectory(SettingsDir);
            var json = JsonSerializer.Serialize(this, JsonOptions);
            File.WriteAllText(SettingsPath, json);

            System.Diagnostics.Debug.WriteLine(
                $"[AppSettings] Saved: Language={Language}, Hotkey={ToggleHotkey}");
        }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine($"[AppSettings] Save 失败: {ex.Message}");
        }
    }

    public static void Reload()
    {
        lock (_lock)
        {
            _instance = Load();
        }
    }
}