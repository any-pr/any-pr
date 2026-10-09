using System.Collections.Concurrent;
using SubmitPrCs;

namespace SubmitPrServer;

// 作业: 服务端串行执行的最小单位（提交 / 更新仓库都算作业）。
// 日志行与进度事件统一编成带序号的条目，客户端按 after=seq 增量拉取。
public class JobEntry
{
    public long Seq { get; set; }
    public string Unit { get; set; } = "";
    public string Kind { get; set; } = "";
    public string Text { get; set; } = "";
    public int Units { get; set; }
    public int Steps { get; set; }
    public bool IsLog { get; set; }
}

public class Job
{
    public string Id { get; } = Guid.NewGuid().ToString("N")[..8];
    public string Kind { get; init; } = "";  // submit / sync
    public string Label { get; init; } = "";
    public string Status { get; private set; } = "queued";  // queued/running/done
    public bool Ok { get; private set; }
    public string Error { get; private set; } = "";
    public List<JobEntry> Entries { get; } = new();
    internal Action<Job>? Run { get; init; }
    private readonly object _gate = new();
    private long _seq;

    public JobEntry Add(string unit, string kind, string text, bool isLog,
        int units = 0, int steps = 0)
    {
        lock (_gate)
        {
            var e = new JobEntry { Seq = ++_seq, Unit = unit, Kind = kind,
                Text = text, Units = units, Steps = steps, IsLog = isLog };
            Entries.Add(e);
            return e;
        }
    }

    public List<JobEntry> After(long seq)
    {
        lock (_gate) return Entries.Where(e => e.Seq > seq).ToList();
    }

    internal void Begin() { Status = "running"; }
    internal void Finish(bool ok, string error)
    {
        Ok = ok;
        Error = error ?? "";
        Status = "done";
    }
}

// 单工作线程串行出队: 天然满足"串行提交"，多客户端同时入队也只是排队。
public static class Jobs
{
    private static readonly ConcurrentQueue<Job> Queue = new();
    private static readonly List<Job> All = new();
    private static readonly object AllGate = new();

    public static Job Enqueue(string kind, string label, Action<Job> run)
    {
        var j = new Job { Kind = kind, Label = label, Run = run };
        lock (AllGate)
        {
            All.Add(j);
            if (All.Count > 100) All.RemoveRange(0, All.Count - 100);
        }
        Queue.Enqueue(j);
        Console.WriteLine($"[jobs] 入队 {kind} #{j.Id} {label}");
        return j;
    }

    public static Job? Find(string id)
    {
        lock (AllGate) return All.FirstOrDefault(j => j.Id == id);
    }

    public static List<Job> AllList()
    {
        lock (AllGate) return All.ToList();
    }

    public static void StartWorker()
    {
        new Thread(Loop) { IsBackground = true, Name = "job-worker" }.Start();
    }

    private static void Loop()
    {
        while (true)
        {
            if (!Queue.TryDequeue(out var j)) { Thread.Sleep(200); continue; }
            Console.WriteLine($"[jobs] 开始 {j.Kind} #{j.Id} {j.Label}");
            j.Begin();
            var ok = false;
            string err = "";
            try
            {
                if (j.Run != null) { j.Run(j); ok = true; }
                else ok = false;
            }
            catch (Exception e)
            {
                err = e.Message;
                j.Add("", "fail", e.Message, false);
                Console.WriteLine($"[jobs] 失败 #{j.Id}: {e.Message}");
            }
            finally
            {
                // 还原账号环境，避免影响后续使用 gh 登录态的作业
                GitOps.GhEnv = null;
            }
            j.Finish(ok, err);
            Console.WriteLine($"[jobs] 完成 #{j.Id} ok={ok}");
        }
    }
}
