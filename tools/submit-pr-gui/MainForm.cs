using System.Collections.Concurrent;
using SubmitPrCs;

namespace SubmitPrGui;

// 主窗体: 参数区(上) / 总进度条+状态行 / 任务列表(填充) / 日志(下)。
// 后台线程只往并发队列塞日志与事件，UI 定时器统一取走——控件永远只在 UI 线程碰。
// 事件看板逻辑也在本文件（UnitBoard 曾独立成文件，后按结构而非上传限制合并）。
public class MainForm : Form
{
    private class Row
    {
        public ListViewItem Item = null!;
        public int StepsTotal, MergedSteps, Retries;
        public string Pr = "";
    }

    private readonly TextBox _repoRoot = new()
    { Text = Environment.CurrentDirectory, Width = 400 };
    private readonly TextBox _dest = new() { Text = ".", Width = 120 };
    private readonly TextBox _sources = new()
    { Multiline = true, ScrollBars = ScrollBars.Vertical, Width = 520, Height = 44 };
    private readonly TextBox _delete = new()
    { Multiline = true, ScrollBars = ScrollBars.Vertical, Width = 240, Height = 44 };
    private readonly TextBox _title = new() { Width = 430 };
    private readonly TextBox _repo = new() { Width = 130 };
    private readonly TextBox _fork = new() { Width = 130 };
    private readonly NumericUpDown _workers = new()
    { Width = 45, Minimum = 0, Maximum = 10 };
    private readonly NumericUpDown _poll = new()
    { Width = 55, Minimum = 30, Maximum = 3600, Value = 300 };
    private readonly CheckBox _strict = new() { Text = "strict", AutoSize = true };
    private readonly CheckBox _dryrun = new() { Text = "dry-run", AutoSize = true };
    private readonly Button _run = new() { Text = "开始提交", Width = 75, Height = 28 };
    private readonly ProgressBar _bar = new() { Dock = DockStyle.Top, Height = 20 };
    private readonly Label _status = new()
    { Dock = DockStyle.Fill, Text = "待命：填好源与目标目录后点「开始提交」",
      TextAlign = ContentAlignment.MiddleLeft };
    private readonly ListView _units = new()
    { View = View.Details, Dock = DockStyle.Fill, FullRowSelect = true,
      GridLines = true, ShowItemToolTips = true };
    private readonly RichTextBox _log = new()
    { Dock = DockStyle.Bottom, Height = 200, ReadOnly = true,
      BackColor = Color.FromArgb(32, 32, 32), ForeColor = Color.Gainsboro,
      Font = new Font("Consolas", 9F), DetectUrls = true,
      WordWrap = false, ScrollBars = RichTextBoxScrollBars.Both };

    private readonly ConcurrentQueue<string> _logQ = new();
    private readonly ConcurrentQueue<Ev> _evQ = new();
    private readonly ConcurrentQueue<bool> _finQ = new();
    private readonly System.Windows.Forms.Timer _timer = new() { Interval = 120 };
    private readonly Dictionary<string, Row> _rows = new();
    private int _totalUnits, _finUnits, _mergedPrs, _retries, _stepsTotal;
    private bool _running;

    public MainForm()
    {
        Text = "submit-pr GUI — any-pr 自动提交（进度 / 重试 / 任务状态）";
        Font = new Font("Microsoft YaHei UI", 9F);
        StartPosition = FormStartPosition.CenterScreen;
        Size = new Size(1060, 780);
        MinimumSize = new Size(1060, 600);

        foreach (var (h, w) in new[] { ("任务", 320), ("步骤", 60),
                     ("PR", 80), ("重试", 55), ("状态", 300) })
            _units.Columns.Add(h, w);

        var top = new Panel { Dock = DockStyle.Top, Height = 185 };
        void L(string t, int x, int y) => top.Controls.Add(
            new Label { Text = t, AutoSize = true, Location = new Point(x, y) });
        void C(Control c, int x, int y)
        { c.Location = new Point(x, y); top.Controls.Add(c); }

        L("仓库根目录（fork 检出目录）", 10, 12); C(_repoRoot, 10, 30);
        L("目标目录 dest", 430, 12);              C(_dest, 430, 30);
        L("删除路径（每行一个，非空即删除模式）", 570, 12); C(_delete, 570, 30);
        L("源文件/目录（每行一个）", 10, 66);     C(_sources, 10, 84);
        L("PR 标题（可选，默认自动生成）", 10, 136); C(_title, 10, 154);
        L("repo（空=自动）", 450, 136);           C(_repo, 450, 154);
        L("fork（空=自动）", 590, 136);           C(_fork, 590, 154);
        L("并发(0=全开)", 730, 136);              C(_workers, 730, 154);
        L("轮询超时s", 785, 136);                 C(_poll, 785, 154);
        C(_strict, 850, 157);                     C(_dryrun, 915, 157);
        C(_run, 975, 152);

        var status = new Panel { Dock = DockStyle.Top, Height = 40 };
        status.Controls.Add(_status);   // Fill
        status.Controls.Add(_bar);      // Top
        Controls.Add(_units);           // Fill 先加
        Controls.Add(_log);             // Bottom
        Controls.Add(status);           // Top（先加的 Top 靠下）
        Controls.Add(top);

        _run.Click += (s, e) => Start();
        _timer.Tick += (s, e) => Drain();
        _timer.Start();
    }

    private static List<string> Lines(string text) => text
        .Split('\n', StringSplitOptions.RemoveEmptyEntries)
        .Select(x => x.Trim()).Where(x => x.Length > 0).ToList();

    private void Start()
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
        var g = new GuiSettings
        {
            RepoRoot = root, Sources = src, Delete = del,
            Dest = _dest.Text, Title = _title.Text,
            Repo = _repo.Text, Fork = _fork.Text,
            Workers = (int)_workers.Value, PollTimeout = (int)_poll.Value,
            Strict = _strict.Checked, DryRun = _dryrun.Checked,
        };
        _running = true;
        _run.Enabled = false;
        ResetBoard();
        _log.Clear();
        AppendLog((g.DryRun ? "[dry-run] " : "") +
            $"开始: {src.Count} 个源 → {g.Dest}" +
            (del.Count > 0 ? $"（删除 {del.Count} 个路径）" : ""), Color.DodgerBlue);
        _ = Task.Run(() =>
        {
            var (ok, err) = RunHost.Run(g,
                m => _logQ.Enqueue(m), ev => _evQ.Enqueue(ev));
            _logQ.Enqueue(ok ? "== 结束: 全部合并且内容验证通过 =="
                : $"== 结束: 未全部合并{(err != null ? $" — {err.Message}" : "")} ==");
            _finQ.Enqueue(ok);
        });
    }

    private void Drain()
    {
        while (_logQ.TryDequeue(out var ln)) AppendLog(ln, Color.Gainsboro);
        while (_evQ.TryDequeue(out var ev)) ApplyEv(ev);
        while (_finQ.TryDequeue(out var ok))
        {
            _running = false;
            _run.Enabled = true;
            _status.Text = (_status.Text.Length > 0 ? _status.Text + "  ·  " : "")
                + (ok ? "✔ 全部完成" : "✘ 未全部合并（见日志）");
        }
    }

    // ---------- 事件看板: 消费 Ev 流维护任务行 / 进度条 / 状态行 ----------

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
                var p = GetRow(e.Unit);
                p.Pr = e.Text;
                p.Item.SubItems[2].Text = e.Text;
                LogEv(e, Color.Gainsboro);
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
                if (m.Pr.Length == 0) { m.Pr = e.Text; m.Item.SubItems[2].Text = e.Text; }
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
            case "done":
                LogEv(e, Color.DodgerBlue);
                break;
            default:
                LogEv(e, Color.Gainsboro);
                break;
        }
    }
}
