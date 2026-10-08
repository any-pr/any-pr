namespace SubmitPrCs;

// 结构化进度事件: 控制台模式完全不用（OnEvent 为 null），
// GUI 消费它做任务列表/进度条/重试计数。Unit 为空表示全局事件。
public class Ev
{
    public DateTime Ts = DateTime.Now;
    public string Unit = "";  // 任务标签（Execute 的 Label，或分支名）
    public string Kind = "";  // ustart/pr/wait/attempt/retry/nudge/pushretry/
                              // merged/timeout/udone/fail/verify/done/skip
    public string Text = "";
    public int Units, Steps;  // 仅 plan 事件: 任务总数 / PR 总数（进度条分母）

    public static void Emit(Opts o, string unit, string kind, string text) =>
        o.OnEvent?.Invoke(new Ev { Unit = unit, Kind = kind, Text = text });

    public static void EmitPlan(Opts o, int units, int steps, string text) =>
        o.OnEvent?.Invoke(new Ev { Unit = "", Kind = "plan", Text = text,
            Units = units, Steps = steps });
}
