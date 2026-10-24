# ATP 分层台账

> 主文档：`ATP通道形成数学模型.txt` · 校验：`python3 tools/check_ledger.py`
> id 一经分配即冻结，新行取当前最大值 +1。

## 粒度守则

一行 = 可独立判定来源、可独立引用、可独立修改的最小陈述单元。

## 本脚本保证什么

格式合法、出处存在、定理存在且所在文件零 Axiom/Admitted、txt 锚点被全覆盖。
覆盖锚点 = 行尾 `(N.M)` 公式 / 顶层节 / 缩进子章节 / §7 清单行。

## 本脚本不保证什么

**不保证台账是对的。** 它不校验陈述是否为真、不校验模型是否忠实编码了设定
（定理存在 ≠ 模型对）、不校验结论有无漏掉反例。它只保证台账不会腐化——
即不会指向不存在的东西。

## §0–§1 记号与公理

| id | 陈述 | 位置 | 来源 | 依据 | 机证 | 可观测性 | 能动性 | 依赖 | 备注 |
|---|---|---|---|---|---|---|---|---|---|
| ATP-001 | 全文记号框架：世界状态 W(t) 表述为希尔伯特空间 H 上的态向量，正常演化由算子 U(τ) 给出（W(t+τ)=U(τ)W(t)）；游戏从未给出闭式公式，本文采用"公理 + 可调参数"建模，凡拟合或假定处显式标注〔假定〕 | §0 | 建模约定 | — | 不适用 | 不可观测 | 结构 | — | 不可观测理由：态向量/演化算子与"标注〔假定〕"都是本文的记账框架与写作约定，游戏侧无对应呈现；各符号含义本身可逐条对照 txt §0。 |
| ATP-002 | 循环长度 Δt = 22 min = 1320 s | §0 | 语料 | 01#TT_TimeLoop_Timeline_4 | 已证:delta_t_is_1320 | 语料可查 | 参数 | — | Yarrow：In total, each cycle created by the Ash Twin Project will last precisely 22 minutes。全局时间坐标取某次循环起点为 t=0 属记账约定（不另立行）。 |
| ATP-003 | δ 为负时间窗口：物体进黑洞的登记时刻与其从白洞出现的时刻之差（A1 中 t_out = t_in − δ 的 δ） | §0, §1 | 语料 | 01#WH_TimeDelay_2 | 已证:warp_delta_pos | 语料可查 | 结构 | — | 语料原文：roughly one hundred-thousandth of a second ≈ 10^-5 s（即锚点 (a)）；正性 δ > 0 由 A1 给出。 |
| ATP-004 | N* 为探测成功轮次，N* = 9,318,054 | §0 | 语料 | 04#The 9,318,054th probe located a deep space anomaly matching all known criteria for the Eye of the universe. | 已证:n_star_0_val | 语料可查 | 参数 | — | 精数 9,318,054 只出自 04（社区阿拉伯语翻译 mod 的 key 表，非 datamine 清洗所得）；datamine 系语料同一句作 9,354（dump OrbitalProbeCannon.txt:137 与 03#OrbitalProbeCannon·Probe Tracking Module 均是），且 03 作 thousands of probes、04 作 millions of probes——两源数值与量级皆冲突，未决。§5 全节与 .v 的 n_star 均以此值为准。n_star 在 .v 里是 Definition 常量，n_star_0_val 证其 0 基前驱 = 9,318,053。 |
| ATP-005 | μ(γ) 为世界线 γ 的"副本数"（同一观察者同时存在的份数） | §0 | 语料 | 02#ParadoxSelf | 已证:copies_coexist_consistent | 实测可查 | 结构 | — | canon 中该构型为 μ = 2（跳入 ATP 黑洞者与上一轮的自己对话）；玩家可实测复现（见 A4' 行）。 |
| ATP-006 | A1：跃迁核心能制造"黑洞-白洞"对，落入黑洞的物体从白洞出现，且出现时刻早于落入时刻（t_out = t_in − δ，δ > 0） | §1 | 语料 | 01#WH_TimeDelay_2 | 已证:warp_arrives_before_departure, warp_delta_is_10_m5 | 语料可查 | 结构 | — | Ramie 原话：The interval is incredibly miniscule (roughly one hundred-thousandth of a second)。机器侧把该值编码为 warp_delta = 1/10^5 后，"到达早于出发"是定义性定理（B2，ATP_channel_proof.v §7）。 |
| ATP-007 | A2（定性）：把更多能量注入跃迁核心，δ 变大 | §1 | 语料 | 03#CaveTwin·High Energy Lab | 已证:window_feasible_antitone_w | 语料可查 | 结构 | — | 语料只给定性：The Nomai discovered they could increase the negative time interval between arrival and departure by adding energy to the warp cores。"所需能量随 δ 指数式上升"是本文的拟合写法，见 (3.1)/(3.4) 行。三档锚点数据分见 ATP-008/009/010。 |
| ATP-008 | 锚点 a：常规实验室功率 E_a = 1（相对单位）对应 δ ≈ 1×10^-5 s | §1, §3 | 语料 | 01#WH_TimeDelay_2 | 已证:warp_delta_is_10_m5 | 语料可查 | 参数 | — | 同一锚点在 txt 的 A2 锚点数据与 §3.1 锚点表两处列出；数值原文 one hundred-thousandth ≈ 10^-5 s。 |
| ATP-009 | 锚点 b：背阴城（The Sunless City）全功率 E_b = κ 对应 δ ≈ 0.7 ~ 1 s | §1, §3 | 拟合假定 | — | 不适用 | 实测可查 | 参数 | — | 语料只有定性：Hypothesis confirmed! I saw it!（01#CT_TLE_TimeTravelConfirmed2，原文是让效应 visible to the unaided eyes），无数值；0.7 ~ 1 s 取自社区整理，非语料原文〔假定〕。κ 为"整城全功率 / 实验室功率"之比，游戏未给数值。 |
| ATP-010 | 锚点 c：超新星能量 E_c = E_sn 对应 δ = 1320 s | §1, §3 | 语料 | 03#TowerTwin·Ash Twin Project | 已证:tau2_fits_supernova | 语料可查 | 参数 | — | 语料支持"ATP 用超新星能量把探测数据送回 22 分钟前"；把 E_sn 与 1320 s 直接对应的因果方向（能量所及即窗口上限）是 2026-09-05 裁决，见 §6(2)（Task 6 行）。 |
| ATP-011 | A3：ATP 通道的载荷是编码后的数据 D（开炮指令 + 探测数据 + 记忆），不是物质；每轮探测器都重新发射，从无"探测器从未来返回" | §1 | 语料 | 01#TT_TimeLoop_Timeline_4 | 已证:data_designed, atp_designed_implies_massless | 语料可查 | 结构 | — | Yarrow：the Ash Twin Project will send the order for the Orbital Probe Cannon to fire back in time by 22 minutes / the probe data from this cannon launch back in time。 |
| ATP-012 | A4：ATP 是"信息单行道"——只注入编码数据 D 是工程设计决定，不是"有质量物体必然撕裂时空"的物理必然 | §1 | 裁决 | — | 已证:atp_designed_implies_massless | 不可观测 | 结构 | — | 不可观测理由：该区分不对应任何可观察物——canon 只有"设计回传数据"的正面描述（Yarrow/面具墙）与"运物质 = 撕裂"的缺席，前半为语料、后半为负证据，故整条按裁决记。机器侧 designed 是工程意图谓词（Definition），非 canon 物理定理。 |
| ATP-013 | A4'（实测后果）：把有质量物体（玩家自己）送进 ATP 黑洞，下一轮从 ATP 内部的白洞出来，与上一轮的自己共存并交谈，μ(γ) = 2 的因果相交被宇宙容忍 | §1 | 实测 | 02#ParadoxSelf | 已证:matter_through_atp_nonparadox_consistent, copies_coexist_consistent | 实测可查 | 结构 | — | 对话原文：Well, we're talking to each other and nothing's exploded yet。⇒ 物质能走通 1320 s 通道；文档 4.3 的"物质 = 时空奇点"按设计解读，不按物理定律解读。 |
| ATP-014 | A4''：真正触发时空撕裂结算的是"删除原因"型悖论构型——先让物体完成因果环，再删去其"落入黑洞"这一原因事件，使环失去不动点；共存的前提是原因须逐轮维持 | §1 | 实测 | 03#CaveTwin·High Energy Lab | 已证:hel_paradox_ruptures, rupture_when_stop_jumping | 实测可查 | 结构 | — | 依据栏指向 HEL 实验场景（该构型的实验来源）；结算短语不在 canon 明文文本资产（独立 UI/代码资源，社区已文档化）。两种触发：① HEL 双核 + 侦察兵出白洞后拔核；② ATP 超新星时跳入核心、下一轮不再跳入（删除副本存在的原因）。 |
| ATP-015 | A5：每轮结束后宇宙回到 t=0 重新演化（重置），保留的是经通道注入的 D；记忆只在雕像激活后才进入 D，且只有被雕像配对（S_j 激活）的观察者能读到记忆 | §1 | 语料 | 03#GiantsDeep·Statue Island | 已证:const_reset_constant_starts, others_never_remember | 语料可查 | 结构 | — | 语料：Nomai statues were designed to pair with a single user, record their memories, and send those memories to a storage unit within the Ash Twin Project；The statues were designed to only activate once the Ash Twin Project succeeded, or in the event that it failed。 |

## §2 通道构造

| id | 陈述 | 位置 | 来源 | 依据 | 机证 | 可观测性 | 能动性 | 依赖 | 备注 |
|---|---|---|---|---|---|---|---|---|---|
| ATP-016 | ATP 通道的形成被建模为五个子过程（2.1 能量采集 → 2.2 黑洞生成 → 2.3 白洞定域 → 2.4 数据编码与注入 → 2.5 循环定义），超新星在 t* 爆发 | §2 | 建模约定 | — | 不适用 | 不可观测 | 结构 | — | 不可观测理由：五步分解是本文为组织模型所作的构造；游戏只呈现结果（22 分钟循环与数据回传），不呈现"通道形成"的过程。 |
| ATP-017 | 要形成 1320 s 的负窗口需 E_sn ≥ E_req(1320 s)：采集面吸收超新星辐射得到的可用能量 E_sn 必须够开此窗 | §2.1, (2.1) | 建模约定 | — | 已证:tau2_fits_supernova | 不可观测 | 结构 | — | **重言**：(2.1) 在指数模型下即锚点 (c) 的定义本身（txt §2.1 注与行 367 自述"锚点定义的重言"），不含独立约束，只表达"ATP 以超新星供能是设计前提"，勿当作独立约束。语料支撑"常规能源不够、需超新星量级"（03#CaveTwin·High Energy Lab：it would require new technology to produce the necessary energy）。机器侧 tau2_fits_supernova 即该重言的代数形式；窗口级门槛谓词 window_feasible 是 Definition，非定理。不可观测理由：重言式，不对应任何独立可观察事实。 |
| ATP-018 | 高级跃迁核心在 ATP 核心室中心生成黑洞 BH⁺，事件记为 e⁺ = (t*, x_ATP)，该黑洞的视界即通道入口 | §2.2 | 语料 | 03#TowerTwin·Ash Twin Project | 不适用 | 语料可查 | 结构 | — | 语料：An advanced warp core was successfully forged for installation at the heart of the Ash Twin Project / There is an advanced warp core inside the protective casing at the center of the planet。事件记法 e⁺ = (t*, x_ATP) 与"视界即入口"是本文的建模表述。 |
| ATP-019 | 与 BH⁺ 配对的"过去端白洞"WH⁻ 出现在 e⁻ = (t* − 1320 s, x_ATP)，即 22 分钟前、同一空间位置；WH⁻ 在 t* − Δt 凭空出现并开始接收数据——对过去的世界而言是边界条件注入 | §2.3, (2.2) | 建模约定 | — | 不适用 | 不可观测 | 结构 | — | 不可观测理由："凭空出现 / 边界条件注入"是本文对过去端白洞的建模描述（§6b 方案 A 的语义），游戏只呈现结果，见 A4' 行（ATP-013）与其依据 02#ParadoxSelf。 |
| ATP-020 | 记忆面具 M_i 在 [t*, t*+ε] 把开炮指令 O、探测测量值 X(N*)、雕像记忆 {Mem_j : j 已配对} 编码为数据串 D，经通道 C = (BH⁺ → WH⁻) 传输，到达 t* − Δt 并写回对应面具/雕像 | §2.4, (2.3) | 语料 | 01#TT_TimeLoop_Timeline_4 | 已证:payload_cmd_recover, payload_probe_recover, payload_mem_recover | 语料可查 | 结构 | — | D 的三分量有语料支撑（Yarrow：送回开炮指令与探测数据；面具墙：Received data from previous cycles）；"编码为数据串"的具体协议游戏未给（§6(1) 开放）。三分量装配/恢复已机器化（ATP_channel_proof.v §30 round_payload）。 |
| ATP-021 | 单轮映射 Φ：第 n 轮世界 = 第 n−1 轮世界的重置重演叠加 D_n，即 W_n(t) = U(t)·W_n(0)、W_n(0) = R(W_{n−1}(0)) + D_n，R 为重置算子 | §2.5, (2.4) | 建模约定 | — | 已证:loop_step_def, const_reset_constant_starts, two_track_stability | 不可观测 | 结构 | — | 不可观测理由：Φ 与重置算子 R 是建模构造；R 的物理实现（时间倒带 vs 重建 + 信息注入）是 §6(3) 开放问题。机器注（ATP_channel_proof.v §20）：该式是"世界轨重置 + 登记册轨"两轨合成的记账式写法，勿按字面把注入读作"叠加在轮起点上"。 |
| ATP-022 | 修正（canon 对齐）：D_n 并非空——每轮回传探测遥测与尝试计数（计数 9,318,054 正是靠它累积），且每轮探测弹道随机；无配对者时 D_n 只是不含雕像记忆，故无人记得，但世界并非逐字节严格重复 | §2.5 | 语料 | 01#TT_TimeLoop_MaskActivationStatus | 已证:reg_mono, pair_persist_after | 语料可查 | 结构 | — | 面具墙：MINUTES, SECONDS AGO: Received data from previous cycles；Yarrow：send the probe data … back in time。弹道随机见 01#OPC_Intact_HourglassOrdersReceived（Cannon aligned with randomly selected probe trajectory）。 |

## §3 能量-窗口模型

| id | 陈述 | 位置 | 来源 | 依据 | 机证 | 可观测性 | 能动性 | 依赖 | 备注 |
|---|---|---|---|---|---|---|---|---|---|
| ATP-023 | 模型一（单指数，供参考）：E(δ) = E_a · exp(δ/τ) | §3, (3.1) | 拟合假定 | — | 不适用 | 不可观测 | 结构 | — | 不可观测理由：指数形式是拟合函数形状，游戏无该函数的呈现。txt 自述该模型与三锚点不相容：用锚点 (b)(c) 定参后预测低能端 ≈ E_b ≫ E_a，与锚点 (a) 冲突；旧"把锚点 (a) 回代自身得 δ_a = 0"的循环论证写法已改为预测-冲突形式（txt 行 152）。 |
| ATP-024 | 模型一的定参式：τ = (1320 − 1) / ln(E_c/E_b) | §3, (3.2) | 拟合假定 | — | 已证:tau2_of | 不可观测 | 参数 | — | tau2_of 是 **Definition，非定理**——机器侧只把该式定义为对象，没有"该标定正确"的定理；模型一本身未机器化（与三锚点不相容），同形的代数自洽定理 tau2_fits_supernova 服务的是模型二的 τ₂（见 ATP-027）。不可观测理由：τ 是拟合参数，游戏无此概念；数值由锚点 (b)(c) 给定。 |
| ATP-025 | 模型二（双段，推荐）低能段：δ(E) = τ₁ · ln(1 + E/E₀)（δ ≤ δ_0 ≈ 1 s） | §3, (3.3) | 拟合假定 | — | 已证:low_energy_scaling_invariance, anchors_scale_invariance | 不可观测 | 结构 | — | 〔开放〕低能段参数未标定、为占位示意：由锚点 (a)(b) 得 τ₁ = 10^-5 / ln(1 + 1/E₀) 与超越方程 1 + κ/E₀ = (1 + 1/E₀)^10^5；框架已定（单参数族 E₀(κ)）但 κ 无值 ⇒ 族未定，未标定前 (3.3) 不参与下游（txt §3.3、§7 行 #10、B7 仍开放）。不可观测理由：参数无值，没有任何可核对量。 |
| ATP-026 | 模型二高能段：E(δ) = E_b · exp((δ − 1)/τ₂)（δ > 1 s） | §3, (3.4) | 拟合假定 | — | 已证:energy_doubling_step | 不可观测 | 结构 | — | 不可观测理由：指数形状是拟合形式（游戏未给闭式）。其代数恒等式已机器化——窗口每延长 τ₂ 秒所需能量 ×e，且平移不变性已推广到任意起点。下游结论只依赖高能段。 |
| ATP-027 | τ₂ = 1319 / ln(E_c/E_b)；取 E_c/E_b = 10^15 时机器界 1909/50 < τ₂ < 191/5（即 38.18 < τ₂ < 38.20 s），一位小数 ≈ 38.2 s——窗口每多开约 38 秒，所需能量 ×e | §3, (3.5) | 拟合假定 | — | 已证:tau_lower_bound, tau_upper_bound | 不可观测 | 参数 | — | 比值 10^15 是〔假定〕（超新星 ≈ 10^44 J、全城功率积分 ≈ 10^29 J，均为量级估计）。从 1 s 推到 1320 s 的能量跨度 e^(1319/τ₂) = E_c/E_b = 10^15 是锚点 (c) 的精确重言（tau2_fits_supernova），不是对模型的独立检验。旧写法 τ₂ ≈ 1319/34.5 ≈ 38.2 s 的双重舍入链已删（2026-09-05）。不可观测理由：τ₂ 是拟合参数，非游戏可见量。 |
| ATP-028 | §3.5（叙述层）：塔网跃迁的负时间偏移（δ ≈ 10^-5 s）与 ATP 长窗（δ = 1320 s）是同一 E_req 函数的两个体制，不是两套物理；设定推论是所有跃迁核心共享该函数，等级差异只是可注入能量上限 | §3 | 裁决 | — | 不适用 | 不可观测 | 结构 | — | txt 自标"叙述层"（未形式化）。不可观测理由：这是把两类通道统一到同一函数的设定推论，游戏既不呈现函数也不呈现核心等级差异；可核对的只有两端现象（白洞站 ≈ 10^-5 s、ATP 1320 s）。最近的机器件是 WarpNetwork_model.v 的 win_ok / win_ok_bounds_ok，只管塔网边的窗口数值，不表达本条的"共享同一函数"主张。 |
| ATP-029 | §3.4 推断 (i)：44 min 窗口需再 ×10^15 量级能量（锚点比率 + 指数外推的展开；44 min 处无任何数据点，属启发式预测） | §3 | 拟合假定 | — | 已证:ratio_44min_algebraic, ratio_44_lower, ratio_44_upper, ratio_44_gt_1025_pct_15, ratio_44_lt_103_pct_15 | 不可观测 | 派生 | ATP-027（(3.5) 的 τ₂ 与比值假定） | 代数形式已机器化：E_req(2640) = exp(1320·ln r / 1319) · E_req(1320)；比值有理界 10^15 < ratio < 10^16（B3b）与精细界 1.025×10^15 < ratio < 1.03×10^15（B3c，B3_series_proof.v 尾部）已机器化。不可观测理由：44 min 处无数据点，游戏内不存在该窗口。 |
| ATP-030 | §3.4 推断 (ii)：常规飞船核心的跃迁 δ 约 10^-5 s 量级——空间跃迁几乎不产生时间错位，与白洞站观测一致 | §3 | 语料 | 01#WH_TimeDelay_2 | 已证:warp_delta_is_10_m5 | 语料可查 | 结构 | — | 与锚点 (a) 同源；机器侧只证 1/10^5 这一数值 hook，不证"所有飞船核心同值"——从白洞站观测推广到全部常规核心是本文的推断。 |
| ATP-031 | §3.4 推断 (iii)：太阳站人工引爆失败后，若无法提前获得 E ≥ E_req(1320 s)，通道只能在自然超新星时刻开启——ATP 只能等待自然死亡（游戏主线） | §3 | 语料 | 01#TT_TimeLoop_Timeline_5 | 已证:supernova_energy_blocks_longer_window, supernova_budget_caps_window | 语料可查 | 结构 | — | 语料：The Sun Station did not work（另见 03#TowerTwin·Ash Twin Project：Although the Ash Twin Project was theoretically sound, the Nomai were unable to power it）。(2.1) 的"必须满足"在指数假定下即锚点 (c) 的定义本身，勿当作独立约束。 |

## §4 为什么只能传信息（与物质载荷）

| id | 陈述 | 位置 | 来源 | 依据 | 机证 | 可观测性 | 能动性 | 依赖 | 备注 |
|---|---|---|---|---|---|---|---|---|---|
| ATP-032 | 长度 L 比特的数据串 D 由无质量场（光子）承载，能量 ε ~ L·h·ν；它对宏观时间通道造成的度规扰动标度 h ~ G·ε/(c⁴·r)（无量纲应变），相对通道自身几何的相对扰动 ~ ε/E_sn 可忽略（ε ≪ E_sn），故通道几何不因运载信息而改变 | §4, (4.1) | 建模约定 | — | 已证:carrier_pert_bound, rel_ex_value_lt_inv_60 | 不可观测 | 结构 | — | 不可观测理由：量纲估计式，游戏无度规扰动的任何呈现。注：若写引力加速度应作 Δg ~ Gε/(c²r²)；(4.1) 的 Gε/(c⁴r) 是度规扰动 h 而非 Δg。"ε/E_sn 可忽略"已由 rel_ex_value_lt_inv_60 机器化（< 1/10^60，配套常量 6.626×10^-34 J·s / 1 GHz / 1 Mbit / 10^44 J 全是〔假定〕）。 |
| ATP-033 | 质量字段口径（注 2026-09-05）：模型内 Load.mass = 0 是"静质量为零"的定义性编码，不是被证明的事实；载荷无静质量、带可忽略能量 ε ≪ E_sn；A3 是设计意图而非 canon 物理定理，designed 与通道能力（A4'）刻意分离——通道不排斥质量，只是设计载荷不含 | §4 | 建模约定 | — | 已证:rel_ex_value_lt_inv_60 | 不可观测 | 结构 | — | 不可观测理由：这是模型字段的口径注记（静质量 vs 能量载体），游戏侧没有可观察物。Load.mass 是 Record 字段（定义性编码）；早前"无桥引理不可机器化"的声明不实，已由 ATP_channel_proof.v §18 复核改写。 |
| ATP-034 | 物质载荷的"自洽环"描述框架：对任意可观测世界线 γ，要求其因果历史与未来闭环自洽（存在不动点解）——概念性工具，非可算理论 | §4, (4.2) | 建模约定 | — | 不适用 | 不可观测 | 结构 | — | 不可观测理由：Φ/不动点框架是启发式叙述，Φ 没有可算定义，本文件不宣称它构成形式化理论（txt 自注）。canon 的两个物质案例见 ATP-013（共存）与 ATP-014（删除原因撕裂）。与 2026-09-19 mod 审查同类判据：判据是因果起源是否断裂，非窗口长度。 |
| ATP-035 | "窗口 ≤ 1 s"不是因果安全判据：机器判据只有一条——安全 ⟺ ¬paradox；window ≤ 1 既不充分（短窗 + 悖论删除 ⇒ 撕裂）也不必要（1320 s 无悖论物质载荷不撕裂） | §4 | 建模约定 | — | 已证:hel_paradox_ruptures, nonparadox_consistent, matter_through_atp_nonparadox_consistent | 不可观测 | 结构 | — | 不可观测理由：这是判据口径的防误读注记，不对应游戏内可观察物。designed 里的 ≤1 s 析取支只是锚点 (b) 标定（E_b ↔ 1 s）与 HEL 演示规模的记号，勿读作"短窗 = 因果无干涉"。consistent := ¬paradox 是 Definition（非定理），其性质由上述定理给出。 |
| ATP-036 | 结论（按设计解读，非物理定律）：ATP 通道是挪麦设计的"信息单行道"，设计载荷为编码数据；物质穿越在 canon 中可行（跨轮共存），并不撕裂时空；撕裂结算只出现在"删除原因"式悖论构型 | §4 | 裁决 | — | 已证:nonparadox_consistent, paradox_implies_rupture | 实测可查 | 结构 | — | 本条为文档口径裁定：正文中任何"物质 = 时空奇点"的旧表述均以本节为准。内容为 A3/A4'/A4'' 的重述（ATP-011/013/014），故来源记为裁决；"物质穿越不撕裂"本身玩家可实测。 |

## §5 探测循环的概率与总时长

| id | 陈述 | 位置 | 来源 | 依据 | 机证 | 可观测性 | 能动性 | 依赖 | 备注 |
|---|---|---|---|---|---|---|---|---|---|
| ATP-037 | 探测循环服从几何分布：前 n−1 轮失败、第 n 轮成功的概率 P(N = n) = (1 − p)^(n−1) · p | §5, (5.1) | 建模约定 | — | 已证:geom_sum_closed, geom_sum_div, geom_normalized, first_success_within | 不可观测 | 结构 | — | 不可观测理由：几何分布是建模选择，游戏不给概率模型。其前提"每轮弹道独立随机"有语料支撑（01#OPC_Intact_HourglassOrdersReceived：Cannon aligned with randomly selected probe trajectory；03#OrbitalProbeCannon·Orbital Probe Cannon 同）。均匀弹道 ⇒ 几何的最小型机器化见 ATP_channel_proof.v §8，指数无 off-by-one、与 (5.1) 严格对齐。 |
| ATP-038 | 若 p 恒定，N* = 9,318,054 时的单轮成功概率极大似然估计为 p̂ = 1/N* ≈ 1.07×10^-7（每轮命中概率约一千万分之一） | §5, (5.2) | 建模约定 | — | 已证:mle_unique_at_nstar, phat_band_1e7 | 不可观测 | 派生 | ATP-004（N* = 9,318,054） | 不可观测理由：p̂ 是统计估计量，游戏不给概率值。已机器化：p̂ 在 p ∈ (0,1) 上全局唯一最大（导数符号 + 中值定理）；端点 p = 0、1 与闭区间 [0,1] 版 mle_max / mle_unique_max 已加。 |
| ATP-039 | 循环消耗的总物理时间 T_total = N* · Δt = 9,318,054 × 1320 s = 12,299,831,280 s；年制换算 365.25 天/儒略年 ≈ 389.76 年（剧情取整 ≈ 390），365 天平年 = 390 平年 + 791,280 s（≈ 390.03 > 390） | §5, (5.3) | 建模约定 | — | 已证:t_total_value_z, t_total_remainder, t_total_between_389_and_391_years, t_total_exceeds_390_common_years | 不可观测 | 派生 | ATP-002（Δt）、ATP-004（N*） | 由语料两数做纯算术（两数出处见依赖行），非语料原文。年制口径必须标注：平年 390.03 > 390、儒略年 389.76 < 390，两制结论相反。不可观测理由：没有观察者经历该时间（见 ATP-040），游戏内不可直接观察。 |
| ATP-040 | 这段时间内太阳每 22 分钟死一次、宇宙每 22 分钟重置一次，没有任何观察者保留记忆（雕像未激活），所以对"经历者"而言这段时间不存在——它只存在于探测计数器的累加里 | §5 | 语料 | 03#GiantsDeep·Statue Island | 已证:no_memory_before_success, pre_activation_round_mem_empty | 语料可查 | 结构 | — | 语料：The statues were designed to only activate once the Ash Twin Project succeeded, or in the event that it failed。另见 ATP-022（D_n 非空，但无配对者时无可感知差异）。 |
| ATP-041 | 雕像激活时刻（配对条件）：雕像 S_j 在成功轮 N* 激活，玩家自该轮起拥有跨轮记忆 Mem_player(n+1) ⊇ Mem_player(n) ∪ D_n；此前 9,318,053 轮 D_n 不含记忆 ⇒ 无人记得 | §5, (5.4) | 语料 | 03#GiantsDeep·Statue Island | 已证:recall_monotone, recall_strict_growth, activated_round_mem_unfold | 语料可查 | 结构 | — | 0 基轮号口径：配对轮 = n_star − 1（ATP_channel_proof.v §28 statue_act——Definition，非定理；雕像文件 MemoryStatue_model.v 的 c_a 同口径）；§28 初版曾用 1 基轮号，已按 0 基重写。 |

## 附录：形式化验证状态与建模层约定

| id | 陈述 | 位置 | 来源 | 依据 | 机证 | 可观测性 | 能动性 | 依赖 | 备注 |
|---|---|---|---|---|---|---|---|---|---|
| ATP-042 | 机器保证 §1：Z 整数闭式已由 coqc 验证——Δt = 1320 s；T_total = 12,299,831,280 s = 389 平年 + 32,327,280 s；389 年 < T < 391 年；390 平年 < T 已补 Example 直接判定 | 附录·保证1 | 建模约定 | — | 已证:delta_t_is_1320, t_total_value_z, t_total_remainder, t_total_between_389_and_391_years, t_total_exceeds_390_common_years | 不可观测 | 不适用 | — | 不可观测理由：机器验证状态陈述，非游戏或语料内容；被验证数值的出处见 ATP-002/ATP-039。平年制 T ≈ 390.03 > 390（直接判定），儒略年制 ≈ 389.76 < 390。 |
| ATP-043 | 机器保证 §2：几何分布闭式（乘法主定理 + 除法推论）、归一化、首成功 CDF 已机器化，指数无 off-by-one，与式 (5.1) 严格对齐 | 附录·保证2 | 建模约定 | — | 已证:geom_sum_closed, geom_sum_div, geom_normalized, first_success_within | 不可观测 | 不适用 | — | 不可观测理由：机器验证状态陈述；模型侧出处见 ATP-037（(5.1)）与 ATP_channel_proof.v §8 的均匀弹道 ⇒ 几何最小机器化。 |
| ATP-044 | 机器保证 §3：E_req(Δt) = E_b·exp((Δt − 1)/τ₂) 在 1320 s 处精确返回 E_sn，平移不变性（每延长 τ₂ 秒 ×e）已推广到任意起点；数值链 τ₂ 界、44 min 外推代数形式、ratio 粗界与精细界均已机器化 | 附录·保证3 | 建模约定 | — | 已证:tau2_fits_supernova, energy_doubling_step, tau_upper_bound, tau_lower_bound, ratio_44min_algebraic, ratio_44_lower, ratio_44_upper | 不可观测 | 不适用 | — | 不可观测理由：机器验证状态陈述；模型侧出处见 ATP-026/027/029。精细界:1.025×10^15 < ratio < 1.03×10^15（B3c，B3_series_proof.v 尾部，2026-09-05 追加）。 |
| ATP-045 | 机器保证 §4：通道语义模型 v2（撕裂 = 悖论事件属性，与 canon 对齐）已完全形式化——设计谓词 designed 与撕裂判定 consistent := ¬paradox 分离，共存与悖论撕裂均成定理 | 附录·保证4 | 建模约定 | — | 已证:data_designed, matter_through_atp_nonparadox_consistent, hel_paradox_ruptures | 不可观测 | 不适用 | — | 不可观测理由：机器验证状态陈述；语义的 canon 依据见 ATP-013/014。"窗口与质量不进入撕裂判定"在模型内是定理，不是假定。 |
| ATP-046 | 机器保证 §6（事件因果层）：悖论"删除原因 → 无一致赋值"的最小机制模型已成定理，把 §4 的谓词级结论推进到因果事件图 | 附录·保证6 | 建模约定 | — | 已证:hel_consistent_with_cause, hel_inconsistent_after_removal | 不可观测 | 不适用 | — | 不可观测理由：机器验证状态陈述；叙事侧出处见 ATP-014。行序沿用 txt 附录原序（§6 列于 §5 之前）。 |
| ATP-047 | 机器保证 §5：p̂ = 1/(INR k + 1) 在 p ∈ (0,1) 上全局唯一最大（导数符号 + MVT_cor2 + ln_lt_inv）；端点 p = 0、1 已补引理，闭区间 [0,1] 版 mle_max / mle_unique_max 已加 | 附录·保证5 | 建模约定 | — | 已证:mle_optimal, mle_unique_max, mle_max_closed, mle_unique_max_closed | 不可观测 | 不适用 | — | 不可观测理由：机器验证状态陈述；模型侧出处见 ATP-038（(5.2)）。 |
| ATP-048 | 验证脚本与兼容性：docs/ATP_channel_proof.v（txt 记 2283 行，§1–§30 + 备注，144 条 Qed 全收尾、零 Axiom；§9–§30 为后续清单扩展）与 companion 数值界 docs/B3_series_proof.v（758 行）；兼容 Rocq ≥ 9.0（用 Stdlib 前缀，9.1.1 验证，8.20 不兼容） | 附录·验证脚本 | 建模约定 | — | 不适用 | 不可观测 | 不适用 | — | 不可观测理由：仓库与工具链事实，非游戏或语料内容。复核（2026-09-19）：ATP_channel_proof.v 现 2284 行、Qed 恰 144 条、无 Axiom/Admitted，txt 所记 2283 行为旧值；B3_series_proof.v 758 行相符。 |
| ATP-049 | 建模层约定 1：§4/§6 的定理是"语义模型与机制模型内部自洽"的机器证明，不是从 A1–A5 的公理化推导；"删除原因 → 撕裂"已由 §6 事件因果层封闭，但 A1/A2/A5 机制层与 Φ 框架仍未形式化 | 附录·约定1 | 建模约定 | — | 不适用 | 不可观测 | 不适用 | — | 不可观测理由：模型的边界声明，不描述任何可观察物。B 系列状态（2026-09-05 更新）：B2 计时事实已封闭（ATP_channel_proof.v §7）；B3 τ 数值界已完成（B3_series_proof.v）；B4 缩放欠定已形式化（§7）；B6 均匀弹道 → 几何已封闭（§8）；B7 数据欠定仍开放（κ 等无数值）。原引用的 ATP_模型完整性分析.md 未入库，判定内联于此。 |
| ATP-050 | canon 修正（2026-09-04 两轮审查确认）：① 跳入 ATP 黑洞的实测 = 跨轮自我共存（ParadoxSelf），非撕裂；② 时空撕裂结算只关联"删除原因"式悖论（该结算文本不在 canon 语料内，依游戏实测/社区整理）；③ 白洞站负时间 = one hundred-thousandth ≈ 10^-5 s（锚点 (a) 已改）；④ 高能实验室全城能量实验无数值（锚点 (b) 已标〔假定〕） | 附录·约定2 | 语料 | 02#ParadoxSelf | 不适用 | 语料可查 | 结构 | — | 依据栏只列主要场景；四条各自出处为 02#ParadoxSelf（①）、游戏实测/社区（②，canon 无明文）、03#WhiteHole·White Hole Station 与 01#WH_TimeDelay_2（③）、01#CT_TLE_TimeTravelConfirmed2（④，只有定性无数字）。 |
| ATP-051 | 模型窗口为 nat 秒，真实 δ < 1 s 塌缩到 {0,1}；designed 的 ≤1 支与一切窗口判定均按此离散化口径读，勿在连续时间上读 | 附录·约定3 | 建模约定 | — | 不适用 | 不可观测 | 参数 | — | 不可观测理由：实现层编码口径，行为等价即可，游戏侧无可观察物（载入口径见 Load.window_s 字段）。2026-09-05 修订：旧"窗口 ∈ [2,1319] ∪ [1321,∞) 的物质载荷被判不一致系编码选择"是 v1 残留句、已删——v2 的 consistent := ¬paradox 与窗长/质量无关（nonparadox_consistent）。 |
| ATP-052 | 低能段 (3.3) 参数未标定，为占位示意；(2.1) 是锚点定义的重言 | 附录·约定4 | 开放 | — | 不适用 | 不可观测 | 不适用 | — | 不可观测理由：两条都是模型内部状态（参数无值 / 定义式重言），没有可观察物。详见 ATP-017（(2.1)）与 ATP-025（(3.3)）。 |
| ATP-053 | 年制口径：平年 390.03（> 390）/ 儒略年 389.76（< 390），全文统一标注，勿混用 | 附录·约定5 | 建模约定 | — | 已证:t_total_between_389_and_391_years, t_total_exceeds_390_common_years | 不可观测 | 不适用 | — | 不可观测理由：换算口径（记账约定），非游戏内容；数值出处见 ATP-039。 |
