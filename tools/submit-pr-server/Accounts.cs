using System.Text.Json;

namespace SubmitPrServer;

// 多 GitHub 账号: 每个账号 = fork + 可选 token（空 = 用 gh 当前登录态）。
// 配置放在服务端工作目录的 accounts.json，token 只留在本机。
public class Account
{
    public string Name { get; set; } = "";
    public string Fork { get; set; } = "snowy-yang/any-pr";
    public string? Target { get; set; } = "any-pr/any-pr";
    public string? Token { get; set; }
}

public class AccountsFile
{
    public List<Account> Accounts { get; set; } = new();
}

public static class Accounts
{
    // 放用户目录而不是仓库内: 既不污染工作区，也绝无被当作"源文件"提交泄漏 token 的可能
    public static readonly string Path = System.IO.Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.UserProfile),
        ".submit-pr", "accounts.json");

    public static List<Account> Load()
    {
        Directory.CreateDirectory(System.IO.Path.GetDirectoryName(Path)!);
        if (!File.Exists(Path))
        {
            var tpl = new AccountsFile
            {
                Accounts = { new Account { Name = "默认账号",
                    Fork = "snowy-yang/any-pr", Target = "any-pr/any-pr",
                    Token = "" } },
            };
            File.WriteAllText(Path, JsonSerializer.Serialize(tpl,
                new JsonSerializerOptions { WriteIndented = true }));
            Console.WriteLine($"已生成配置模板 {Path}——按需增改账号（Token 留空则用 gh 登录态）。");
        }
        var file = JsonSerializer.Deserialize<AccountsFile>(File.ReadAllText(Path),
            new JsonSerializerOptions { PropertyNameCaseInsensitive = true })
            ?? new AccountsFile();
        if (file.Accounts.Count == 0)
            throw new InvalidOperationException($"{Path} 里至少需要一个账号条目。");
        foreach (var a in file.Accounts)
            Console.WriteLine($"账号: {a.Name} → fork {a.Fork}, target {a.Target}, " +
                $"token {(string.IsNullOrEmpty(a.Token) ? "(gh 登录态)" : "已配置")}");
        return file.Accounts;
    }
}
