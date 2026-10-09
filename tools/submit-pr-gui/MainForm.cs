using System.Net.Http.Json;
using System.Text.Json;
using SubmitPrCs;

namespace SubmitPrGui;

// submit-pr 客户端（配合 submit-pr-server）: 参数区 / 总进度 / 任务列表 / 日志。
// 提交与"更新仓库"都作为作业发给服务端串行执行；客户端按 seq 增量拉取
// 日志+进度事件并渲染。控件只在 UI 线程碰（async/await 回到 UI 上下文）。
public class MainForm : Form
{
    private class Row
    {
        public ListViewItem Item = null!;
        public int StepsTotal, MergedSteps, Retries;
        public string Pr = "";
    }

    private readonly TextBox _serverUrl = new()
    { Text = "http://127.0.0.1:8642", Width = 190 };
    private readonly ComboBox _account = new()
    { DropDownStyle = ComboBoxStyle.DropDownList, Width = 140 };
    private readonly Button _refreshAcc = new() { Text = "刷新账号", Width = 70 };
    private readonly Button _syncBtn = new() { Text = "更新仓库", Width = 90 };
    private readonly CheckBox _nowait = new()
    { Text = "不等待（建 PR 即走）", AutoSize = true, Checked = true };
    private readonly TextBox _repoRoot = new()
    { Text = Environment.CurrentDirectory, Width = 400 };
    private readonly TextBox _dest = new() { Text = ".", Width = 120 };
    private readonly TextBox _sources = new()
    { Multiline = true, ScrollBars = ScrollBars.Vertical, Width = 520, Height = 44 };
    private readonly TextBox _delete = new()
    { Multiline = true, ScrollBars = ScrollBars.Vertical, Width = 240, Height = 44 };
    private readonly TextBox _title = new() { Width = 430 };
    private readonly NumericUpDown _workers = new()
    { Width = 45, Minimum = 0, Maximum = 10 };
    private readonly NumericUpDown _poll = new()
    { Width = 55, Minimum = 30, Maximum = 3600, Value = 300 };
    private readonly CheckBox _strict = new() { Text = "strict", AutoSize = true };
    private readonly CheckBox _dryrun = new() { Text = "dry-run", AutoSize = true };
    private readonly Button _run = new() { Text = "开始提交", Width = 75, Height = 28 };
    private readonly ProgressBar _bar = new() { Dock = DockStyle.Top, Height = 20 };
    private readonly Label _status = new()
    { Dock = DockStyle.Fill, Text = "待命：先启动 submit-pr-server，刷新账号后开始",
      TextAlign = ContentAlignment.MiddleLeft };
    private readonly ListView _units = new()
    { View = View.Details, Dock = DockStyle.Fill, FullRowSelect = true,
      GridLines = true, ShowItemToolTips = true };
    private readonly RichTextBox _log = new()
    { Dock = DockStyle.Bottom, Height = 200, ReadOnly = true,
      BackColor = Color.FromArgb(32, 32, 32), ForeColor = Color.Gainsboro,
      Font = new Font("Consolas", 9F), DetectUrls = true,
      WordWrap = false, ScrollBars = RichTextBoxScrollBars.Both };

    private static readonly HttpClient Http = new();
    private readonly System.Windows.Forms.Timer _timer = new() { Interval = 120 };
    private readonly Dictionary<string, Row> _rows = new();
    private string _jobId = "", _jobKind = "";
    private long _jobSeq;
    private int _tick;
    private bool _running, _polling;
    private int _totalUnits, _finUnits, _mergedPrs, _retries, _stepsTotal;

    public MainForm()
    {
        Text = "submit-pr 客户端 — any-pr 自动提交（进度 / 重试 / 任务状态）";
        Font = new Font("Microsoft YaHei UI", 9F);
        StartPosition = FormStartPosition.CenterScreen;
        Size = new Size(1080, 850);
        MinimumSize = new Size(1080, 660);

        foreach (var (h, w) in new[] { ("任务", 320), ("步骤", 60),
                     ("PR", 80), ("重试", 55), ("状态", 300) })
            _units.Columns.Add(h, w);

        var top = new Panel { Dock = DockStyle.Top, Height = 240 };
        void L(string t, int x, int y) => top.Controls.Add(
            new Label { Text = t, AutoSize = true, Location = new Point(x, y) });
        void C(Control c, int x, int y)
        { c.Location = new Point(x, y); top.Controls.Add(c); }

        L("服务端地址", 10, 12);                C(_serverUrl, 10, 30);
        L("提交账号（accounts.json）", 215, 12); C(_account, 215, 30);
        C(_refreshAcc, 365, 28);               C(_syncBtn, 445, 28);
        C(_nowait, 550, 32);
        L("仓库根目录（fork 检出目录）", 10, 62); C(_repoRoot, 10, 80);
        L("目标目录 dest", 430, 62);           C(_dest, 430, 80);
        L("删除路径（每行一个，非空即删除模式）", 570, 62); C(_delete, 570, 80);
        L("源文件/目录（每行一个）", 10, 128);  C(_sources, 10, 146);
        L("PR 标题（可选，默认自动生成）", 10, 196); C(_title, 10, 214);
        L("并发(默认串行)", 450, 196);         C(_workers, 540, 210);
        L("轮询超时s", 600, 196);              C(_poll, 660, 210);
        C(_strict, 700, 214);                  C(_dryrun, 765, 214);
        C(_run, 975, 208);

        var status = new Panel { Dock = DockStyle.Top, Height = 40 };
        status.Controls.Add(_status);
        status.Controls.Add(_bar);
        Controls.Add(_units);
        Controls.Add(_log);
        Controls.Add(status);
        Controls.Add(top);

        _run.Click += (s, e) => _ = StartSubmitAsync();
        _syncBtn.Click += (s, e) => _ = StartSyncAsync();
        _refreshAcc.Click += (s, e) => _ = RefreshAccountsAsync(true);
        _timer.Tick += (s, e) => _ = PollAsync();
        _timer.Start();
        _ = RefreshAccountsAsync(false);
    }

    // ---------- 与服务端交互 ----------

    private string Base() => _serverUrl.Text.Trim().TrimEnd('/');

    private async Task RefreshAccountsAsync(bool verbose)
    {
        try
        {
            var accs = await Http.GetFromJsonAsync<List<Dictionary<string, JsonElement>>>(
                $"{Base()}/accounts");
            var sel = _account.SelectedItem as string;
            _account.Items.Clear();
            foreach (var a in accs ?? new())
                if (a.TryGetValue("name", out var n))
                    _account.Items.Add(n.GetString() ?? "");
            if (_account.Items.Count > 0)
                _account.SelectedItem = sel ?? _account.Items[0];
            if (verbose) _status.Text = $"已加载 {_account.Items.Count} 个账号。";
        }
        catch (Exception)
        {
            _status.Text = $"连不上服务端 {Base()}——先启动 submit-pr-server。";
        }
    }

    private async Task<string> PostJobAsync(string path, object payload)
    {
        using var resp = await Http.PostAsJsonAsync(Base() + path, payload);
        resp.EnsureSuccessStatusCode();
        var doc = await resp.Content.ReadFromJsonAsync<JsonElement>();
        return doc.GetProperty("jobId").GetString() ?? "";
    }

    private async Task StartSubmitAsync()
    {
        if (_running) return;
        var src = Lines(_sources.Text);
        var del = Lines(_delete.Text);
        var root = _repoRoot.Text.Trim();
        if (src.Count == 0 && del.Count == 0)
        {
            MessageBox.Show("请至少填一个源文件/目录，或删除路径。", "参数");
            return;
        }
        if (!Directory.Exists(root))
        {
            MessageBox.Show($"仓库根目录不存在: {root}", "参数");
            return;
        }
        try
        {
            var jobId = await PostJobAsync("/submit", new Dictionary<string, object?>
            {
                ["account"] = _account.SelectedItem as string ?? "",
                ["repoRoot"] = root,
                ["sources"] = src,
                ["delete"] = del,
                ["dest"] = _dest.Text,
                ["title"] = Blank(_title.Text),
                ["workers"] = (int)_workers.Value,
                ["pollTimeout"] = (int)_poll.Value,
                ["strict"] = _strict.Checked,
                ["dryRun"] = _dryrun.Checked,
                ["noWait"] = _nowait.Checked,
            });
            BeginJob(jobId, "submit", src.Count, del.Count);
        }
        catch (Exception e)
        {
            MessageBox.Show($"提交作业失败: {e.Message}", "服务端");
        }
    }

    private async Task StartSyncAsync()
    {
        if (_running) return;
        var root = _repoRoot.Text.Trim();
        if (!Directory.Exists(root))
        {
            MessageBox.Show($"仓库根目录不存在: {root}", "参数");
            return;
        }
        try
        {
            var jobId = await PostJobAsync("/sync", new Dictionary<string, object?>
            {
                ["account"] = _account.SelectedItem as string ?? "",
                ["repoRoot"] = root,
            });
            BeginJob(jobId, "sync", 0, 0);
        }
        catch (Exception e)
        {
            MessageBox.Show($"更新仓库作业失败: {e.Message}", "服务端");
        }
    }

    private void BeginJob(string jobId, string kind, int srcN, int delN)
    {
        _running = true;
        _jobId = jobId;
        _jobKind = kind;
        _jobSeq = 0;
        _run.Enabled = false;
        _syncBtn.Enabled = false;
        ResetBoard();
        _log.Clear();
        AppendLog(kind == "sync" ? $"作业 #{jobId}: 更新仓库…"
            : $"作业 #{jobId}: {srcN} 个源 → {_dest.Text}" +
              (delN > 0 ? $"（删除 {delN} 个路径）" : "") +
              (_nowait.Checked ? "（不等待模式）" : ""), Color.DodgerBlue);
    }

    private async Task PollAsync()
    {
        if (_jobId.Length == 0 || _polling) return;
        if (++_tick % 3 != 0) return;  // ~360ms 拉一次
        _polling = true;
        try
        {
            using var resp = await Http.GetAsync(
                $"{Base()}/events?job={_jobId}&after={_jobSeq}");
            resp.EnsureSuccessStatusCode();
            var root = JsonDocument.Parse(
                await resp.Content.ReadAsStringAsync()).RootElement;
            foreach (var en in root.GetProperty("entries").EnumerateArray())
            {
                _jobSeq = en.GetProperty("seq").GetInt64();
                if (en.GetProperty("isLog").GetBoolean())
                {
                    AppendLog(en.GetProperty("text").GetString() ?? "",
                        Color.Gainsboro);
                    continue;
                }
                ApplyEv(new Ev
                {
                    Unit = en.GetProperty("unit").GetString() ?? "",
                    Kind = en.GetProperty("kind").GetString() ?? "",
                    Text = en.GetProperty("text").GetString() ?? "",
                    Units = en.TryGetProperty("units", out var u) && u.ValueKind ==
                        JsonValueKind.Number ? u.GetInt32() : 0,
                    Steps = en.TryGetProperty("steps", out var st) && st.ValueKind ==
                        JsonValueKind.Number ? st.GetInt32() : 0,
                });
            }
            if (root.GetProperty("status").GetString() == "done")
                EndJob(root.GetProperty("ok").GetBoolean(),
                    root.GetProperty("error").GetString() ?? "");
        }
        catch (Exception)
        {
            // 瞬时网络抖动: 下一轮重试；连续失败由用户重启服务端
        }
        finally { _polling = false; }
    }

    private void EndJob(bool ok, string error)
    {
        _running = false;
        _jobId = "";
        _run.Enabled = true;
        _syncBtn.Enabled = true;
        if (_jobKind == "sync")
        {
            _bar.Value = _bar.Maximum;
            _status.Text = ok ? "✔ 仓库已更新（本地 main 与 fork 已同步到上游）"
                : $"✘ 更新失败（{error}）";
        }
        else
        {
            _status.Text = (_status.Text.Length > 0 ? _status.Text + "  ·  " : "")
                + (ok ? "✔ 全部完成" : "✘ 未全部合并（见日志）");
        }
        AppendLog(ok ? "== 作业完成 ==" : $"== 作业未完成: {error} ==",
            ok ? Color.LimeGreen : Color.Red);
    }

    // ---------- 看板渲染 ----------

    private static List<string> Lines(string text) => text
        .Split('\n', StringSplitOptions.RemoveEmptyEntries)
        .Select(x => x.Trim()).Where(x => x.Length > 0).ToList();

    private static string? Blank(string s) =>
        string.IsNullOrWhiteSpace(s) ? null : s.Trim();

    private void ResetBoard()
    {
        _rows.Clear();
        _units.Items.Clear();
        _totalUnits = _finUnits = _mergedPrs = _retries = _stepsTotal = 0;
        _bar.Value = 0;
        _bar.Maximum = 1;
        UpdateStatus();
    }

    private void AppendLog(string text, Color c)
    {
        _log.SelectionStart = _log.TextLength;
        _log.SelectionColor = c;
        _log.AppendText($"[{DateTime.Now:HH:mm:ss}] {text}\n");
        _log.SelectionColor = _log.ForeColor;
        _log.ScrollToCaret();
    }

    private void UpdateStatus() => _status.Text =
        $"任务 {_finUnits}/{_totalUnits}  ·  已合并 PR {_mergedPrs}" +
        (_stepsTotal > 0 ? $"/{_stepsTotal}" : "") +
        $"  ·  重试 {_retries}";

    private Row GetRow(string unit)
    {
        if (_rows.TryGetValue(unit, out var r)) return r;
        var it = new ListViewItem(unit) { UseItemStyleForSubItems = false };
        foreach (var _ in new[] { 1, 2, 3, 4 }) it.SubItems.Add("");
        _units.Items.Add(it);
        r = new Row { Item = it };
        _rows[unit] = r;
        return r;
    }

    private void SetState(string unit, string text, Color c)
    {
        var si = GetRow(unit).Item.SubItems[4];
        si.Text = text;
        si.ForeColor = c;
    }

    private static int HeadInt(string s) =>
        int.TryParse(s.Split(' ')[0], out var n) ? n : 0;

    private void LogEv(Ev e, Color c) =>
        AppendLog(e.Unit.Length > 0 ? $"[{e.Unit}] {e.Text}" : e.Text, c);

    private void ApplyEv(Ev e)
    {
        switch (e.Kind)
        {
            case "plan":
                _totalUnits = Math.Max(1, e.Units);
                _stepsTotal = e.Steps;
                _bar.Maximum = Math.Max(1, e.Steps);
                UpdateStatus();
                LogEv(e, Color.DodgerBlue);
                break;
            case "ustart":
                var u = GetRow(e.Unit);
                u.StepsTotal = HeadInt(e.Text);
                u.Item.SubItems[1].Text = $"0/{u.StepsTotal}";
                SetState(e.Unit, "运行中", SystemColors.WindowText);
                LogEv(e, Color.Gray);
                break;
            case "attempt":
                SetState(e.Unit, e.Text, SystemColors.WindowText);
                LogEv(e, Color.Gray);
                break;
            case "wait":  // 高频心跳，只刷状态列不进日志
                SetState(e.Unit, e.Text, SystemColors.WindowText);
                break;
            case "pr":
            case "submitted":
                var p = GetRow(e.Unit);
                if (e.Text.StartsWith('#') && p.Pr.Length == 0)
                { p.Pr = e.Text; p.Item.SubItems[2].Text = e.Text; }
                if (e.Kind == "submitted")
                    SetState(e.Unit, "已提交，待结算", Color.DodgerBlue);
                LogEv(e, e.Kind == "submitted" ? Color.DodgerBlue : Color.Gainsboro);
                break;
            case "pushretry":
            case "retry":
            case "nudge":
                var x = GetRow(e.Unit);
                x.Retries++;
                _retries++;
                x.Item.SubItems[3].Text = x.Retries.ToString();
                SetState(e.Unit, e.Kind == "nudge" ? "停滞强推促发" : "重试中",
                    Color.DarkOrange);
                UpdateStatus();
                LogEv(e, Color.DarkOrange);
                break;
            case "merged":
                var m = GetRow(e.Unit);
                m.MergedSteps++;
                _mergedPrs++;
                if (m.Pr.Length == 0 && e.Text.StartsWith('#'))
                { m.Pr = e.Text; m.Item.SubItems[2].Text = e.Text; }
                m.Item.SubItems[1].Text = $"{m.MergedSteps}/{Math.Max(1, m.StepsTotal)}";
                _bar.Value = Math.Min(_bar.Maximum, _mergedPrs);
                SetState(e.Unit, "已合并", Color.Green);
                UpdateStatus();
                LogEv(e, Color.LimeGreen);
                break;
            case "timeout":
                SetState(e.Unit, "等待合并超时", Color.OrangeRed);
                LogEv(e, Color.OrangeRed);
                break;
            case "udone":
                _finUnits++;
                _bar.Value = Math.Min(_bar.Maximum, _mergedPrs);
                SetState(e.Unit, "完成", Color.Green);
                UpdateStatus();
                break;
            case "fail":
                _finUnits++;
                SetState(e.Unit, "失败（见日志）", Color.Red);
                UpdateStatus();
                LogEv(e, Color.Red);
                break;
            case "verify":
            case "settle":
            case "done":
                LogEv(e, Color.DodgerBlue);
                break;
            default:
                LogEv(e, Color.Gainsboro);
                break;
        }
    }
}
