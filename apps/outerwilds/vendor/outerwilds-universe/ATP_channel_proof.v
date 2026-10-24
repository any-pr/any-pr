(* ==========================================================================
   《星际拓荒》ATP 时间通道 —— 形式化验证脚本 (Rocq / Coq ≥ 9.0，9.1.1 验证)
   对应文档：docs/ATP通道形成数学模型.txt

   内容：
     §1 常数与总时长（Z 上的闭式计算，vm_compute 验证）
     §2 探测几何分布（实数，归纳证明闭式）
     §3 能量-窗口闭式公式的代数自洽（数值拟合不在机器内）
     §4 通道语义模型 v2（撕裂 = 悖论事件属性，与 canon 对齐）
     §5 极大似然 p̂=1/N 的最优性（MVT 实分析，闭区间版 + 十进制带 +
         观测 N* 实例化）
     §6 事件因果层（悖论删除 → 无一致赋值，最小机制模型）
     §7 剩余边界：B2 计时事实（含 1/10^5 hook）、B4 缩放不变性
     §8 B6：有限均匀弹道模型 ⇒ 几何分布
     §9–§29 为 2026-09-05 后续清单与机器化扩展（清单行号见 txt §7）：
     §9 信息载荷相对扰动界（符号阈值）       §10 间隙动作可达性
     §11 能量环平衡（ring_balance）          §12 环一致（ring_consistent）
     §13 44 分钟外推的代数形式               §14 间隙可达方向+缺因泛化
     §15 低能段锚点族缩放不变性             §16/§19 载荷门控最小件+三分量脊
     §17 注入-衰减时序                       §18 ε 常量数值化（含 1/10^60 严格不等式）
     §20 重置代数（世界轨；算子序注记）      §21 22 分钟信息传播锥（秒制步长；可达=距离球）
     §22 双副本共存（μ=2）                  §23 循环总能量预算
     §24 双轨：世界重置 + 登记册单调         §25 事件层↔Load 语义桥
     §26 语义闭包 I（bits 无关/designed 拆分）
     §27 语义闭包 II（悖论双向/删除链/三层一致）
     §28 任意阈值界 + 触发链（0 基轮对齐修复）
     §29 window_feasible + 能量预算上限（δ_max 裁决机器化）
     §30 载荷三分量合并（(2.3) D 的装配/恢复/记忆门控缝合 statue_act）

   重要说明（2026-09-04 审查后修订 v2）：
   §4 是"通道语义模型"的完全形式化：撕裂被建模为悖论删除事件的
   属性（consistent := ¬paradox），与 canon 一致——物质走 ATP 通道
   是跨轮自我共存（matter_through_atp_nonparadox_consistent），
   悖论删除构型才触发撕裂（paradox_implies_rupture）；"按设计只送
   数据"独立为 designed 谓词（质量=0 ∨ 窗口≤1），不参与撕裂判定。
   A1/A2/A5 机制层与能量-窗口数值拟合不在模型内（txt 附录"仍开放"
   清单）。零 Axiom / Admitted。
   ========================================================================== *)
From Stdlib Require Import Reals.
From Stdlib Require Import Psatz.
From Stdlib Require Import ZArith.
From Stdlib Require Import List.
Open Scope R_scope.

(* ======================================================================
   §1 常数与总时长
   ====================================================================== *)

Definition loop_min : nat := 22.
Definition min_s    : nat := 60.
Definition delta_t  : nat := loop_min * min_s.     (* 循环窗口 1320 s *)
Definition n_star   : nat := 9318054.              (* 探测成功轮次 *)

(* Z 版本常量：闭式计算快，供机器验证 *)
Definition t_total_z : Z := (9318054 * 1320)%Z.
Definition year_s_z  : Z := (365 * 24 * 60 * 60)%Z.

Example delta_t_is_1320 : delta_t = 1320%nat.
Proof. reflexivity. Qed.

Example t_total_value_z : t_total_z = 12299831280%Z.
Proof. reflexivity. Qed.

(* T_total = N*·Δt = 12,299,831,280 s = 389 平年 + 32,327,280 s
   （≈ 390.03 平年；按 365.25 天/儒略年 ≈ 389.76 年） *)
Example t_total_remainder : t_total_z = (389 * year_s_z + 32327280)%Z.
Proof. reflexivity. Qed.

(* 平年（365 天）口径判定：T > 390 平年（T = 390.03 平年，多 791,280 s）；
   儒略年（365.25 天）口径下 T ≈ 389.76 < 390 —— 年制不同结论相反，
   凡说"是否小于 390 年"必须标注年制。 *)
Example t_total_exceeds_390_common_years :
  (390 * year_s_z < t_total_z)%Z.
Proof. vm_compute. reflexivity. Qed.

Theorem t_total_between_389_and_391_years :
  (389 * year_s_z < t_total_z /\ t_total_z < 391 * year_s_z)%Z.
Proof.
  vm_compute. split; reflexivity.
Qed.

(* ======================================================================
   §2 探测循环的几何分布
   ====================================================================== *)

(* 迭代幂（避免依赖标准库 pow 的展开细节） *)
Fixpoint rp (q : R) (n : nat) : R :=
  match n with
  | O   => 1
  | S m => q * rp q m
  end.

(* 部分和 Σ_{k=0..n} q^k *)
Fixpoint gsum (q : R) (n : nat) : R :=
  match n with
  | O   => 1
  | S m => gsum q m + rp q (S m)
  end.

(* 主定理（乘法形式）：
   (1 − q) · Σ_{k=0..n} q^k = 1 − q^(n+1)                        *)
Theorem geom_sum_closed : forall (q : R) (n : nat),
  (1 - q) * gsum q n = 1 - rp q (S n).
Proof.
  intros q n.
  induction n as [| n IH].
  - simpl. ring.
  - simpl. rewrite Rmult_plus_distr_l. rewrite IH. simpl. ring.
Qed.

(* 推论（除法形式，完整形式化）：q ≠ 1 时
   Σ_{k=0..n} q^k = (1 − q^(n+1)) / (1 − q)                      *)
Theorem geom_sum_div : forall (q : R) (n : nat),
  q <> 1 -> gsum q n = (1 - rp q (S n)) / (1 - q).
Proof.
  intros q n Hq.
  assert (Hz : 1 - q <> 0) by lra.
  apply (Rmult_eq_reg_l (1 - q) (gsum q n) ((1 - rp q (S n)) / (1 - q))).
  - rewrite geom_sum_closed.
    rewrite (Rmult_comm (1 - q)).
    unfold Rdiv.
    rewrite Rmult_assoc.
    rewrite (Rinv_l (1 - q) Hz).
    ring.
  - exact Hz.
Qed.

(* 前 trials 轮全失败的概率 = (1−p)^trials *)
Definition fail_prob (p : R) (trials : nat) : R :=
  rp (1 - p) trials.

(* 单轮成功概率 p 时，在 (n+1) 轮内至少成功一次的概率：
   p · Σ_{k=0..n} (1−p)^k = 1 − (1−p)^(n+1)                     *)
Theorem first_success_within : forall (p : R) (n : nat),
  p * gsum (1 - p) n = 1 - fail_prob p (S n).
Proof.
  intros p n.
  unfold fail_prob.
  pose proof (geom_sum_closed (1 - p) n) as H.
  assert (Hp : (1 - (1 - p)) = p) by lra.
  rewrite Hp in H.
  exact H.
Qed.

(* 归一化：成功概率 + 全失败概率 = 1 *)
Corollary geom_normalized : forall (p : R) (n : nat),
  p * gsum (1 - p) n + fail_prob p (S n) = 1.
Proof.
  intros. rewrite first_success_within. lra.
Qed.

(* 成功晚于第 N* 轮（前 N* 轮全失败）的概率；符号结果，未被定理引用 *)
Definition prob_all_fail_before_success (p : R) : R :=
  fail_prob p n_star.

(* 极大似然估计 p̂ = 1/N*：以 k = N* − 1、p̂ = 1/(INR k + 1) 的形式
   在 §5 中完整证明（见 mle_optimal / mle_unique_max）。 *)

(* ======================================================================
   §3 能量-窗口闭式公式（高能段）
   ====================================================================== *)

(* E_req(Δt) = E_b · exp((Δt − 1) / τ₂)，Δt 以秒为单位 *)
Definition E_req (E_b tau2 : R) (dt_s : R) : R :=
  E_b * exp ((dt_s - 1) / tau2).

(* τ₂ = 1319 / ln(E_sn / E_b) —— 由锚点 (1 s, E_b)、(1320 s, E_sn) 拟合 *)
Definition tau2_of (E_sn E_b : R) : R :=
  1319 / ln (E_sn / E_b).

(* 闭式在 Δt = 1320 s 处精确返回 E_sn *)
Theorem tau2_fits_supernova :
  forall (E_b E_sn : R),
    0 < E_b -> E_b < E_sn ->
    0 < ln (E_sn / E_b) ->
    E_req E_b (tau2_of E_sn E_b) 1320 = E_sn.
Proof.
  intros E_b E_sn H0 Hlt Hln.
  unfold E_req, tau2_of.
  assert (H1319 : 1320 - 1 = 1319) by lra.
  rewrite H1319.
  assert (Hpos : 0 < E_sn / E_b).
  { unfold Rdiv. apply Rmult_lt_0_compat.
    - lra.
    - apply Rinv_0_lt_compat. lra. }
  assert (Hdiv : 1319 / (1319 / ln (E_sn / E_b)) = ln (E_sn / E_b)).
  { field.
    all: first [ lra | apply Rlt_not_eq; exact Hln ]. }
  rewrite Hdiv.
  rewrite (exp_ln (E_sn / E_b) Hpos).
  field. lra.
Qed.

(* 窗口每延长 τ₂ 秒，所需能量 ×e（任意起点 t 的平移不变性） *)
Theorem energy_doubling_step : forall (E_b tau2 t : R),
  tau2 <> 0 ->
  E_req E_b tau2 (t + tau2) = exp 1 * E_req E_b tau2 t.
Proof.
  intros E_b tau2 t Ht.
  unfold E_req.
  assert (Hsplit : (t + tau2 - 1) / tau2 = (t - 1) / tau2 + 1).
  { field. exact Ht. }
  rewrite Hsplit. rewrite exp_plus. ring.
Qed.

(* ======================================================================
   §4 通道语义模型 v2（完全形式化；2026-09-04 与 canon A4'/A4'' 对齐）
   ======================================================================
   模型字段：
     bits     : 数据串（载荷为数据时的内容）
     mass     : 质量(kg)
     window_s : 窗口（nat 秒）
     paradox  : 是否处于"删除原因"式悖论构型
   语义：
     designed   l := mass=0 ∨ window≤1    挪麦"按设计会送入通道"的载荷
     consistent l := ¬paradox l           不会触发撕裂结算
   canon 依据：
     · 跳入 ATP 黑洞 → 跨轮自我共存（ParadoxSelf），非撕裂；
     · 撕裂结算只关联"删除原因"式悖论（如高能实验室实验）；
     · 窗口与质量不进入撕裂判定（本模型内为定理，非假定）。
   A1/A2/A5 机制层、能量-窗口数值不在本模型内（见 txt 附录）。    *)

Record Load : Set := Load_mk {
  bits     : list bool;
  mass     : nat;      (* 静质量（kg）——〔注 2026-09-05：质量字段口径〕mass=0
                          是"静质量为零"的定义性编码，不是被证明的事实
                          （txt §4.1 注 2026-09-05；designed 是工程意图非
                          canon 物理定理） *)
  window_s : nat;      (* 窗口长（nat 秒；真实 δ<1 s 塌缩到 {0,1}，离散化口径） *)
  paradox  : bool
}.

(* 注：本 Record 的窗长与质量字段都不进入撕裂判定（consistent）；designed
   里的 ≤1 s 析取支是锚点标定与 HEL 演示规模的记号，不是因果判据
   （txt "≤1 s 不是因果安全判据"注 2026-09-05）。 *)

Definition Data : Load := Load_mk nil 0%nat 0%nat false.

Definition Matter (l : Load) : Prop := (0 < mass l)%nat.
Definition time_channel (l : Load) : Prop := (window_s l = 1320)%nat.
(* designed：挪麦"按设计会送入通道"的载荷（工程许可集合，非 A3 演绎）。
   〔双义点名 2026-09-05：w ≤ 1 支同时承担两义——锚点 (b) 的 ≈1 s 标定
   与 HEL 高能实验室的 10⁻⁵ s 演示规模；是记号不是因果判据，勿读作
   "短窗 = 因果无干涉"（见 Record 下注与 txt §4.1 注）。〕 *)
Definition designed (l : Load) : Prop :=
  (mass l = 0)%nat \/ (window_s l <= 1)%nat.
Definition consistent (l : Load) : Prop := ~ is_true (paradox l).

(* T1 数据载荷符合设计（质量 0） *)
Theorem data_designed : designed Data.
Proof.
  unfold designed, Data, mass. simpl. left. reflexivity.
Qed.

(* T2 设计推论：走 ATP（1320 s）的载荷若符合设计，必为质量 0 *)
Theorem atp_designed_implies_massless :
  forall l, time_channel l -> designed l -> (mass l = 0)%nat.
Proof.
  intros l Ht Hd.
  unfold time_channel, designed in *.
  destruct Hd as [Hz | Hw].
  - exact Hz.
  - lia.
Qed.

(* T3 canon A4'：有质量物体可走通 1320 s 通道且不撕裂（跨轮自我共存） *)
Definition Player (m : nat) : Load := Load_mk nil m 1320%nat false.

Theorem matter_through_atp_nonparadox_consistent :
  forall m, (0 < m)%nat ->
    Matter (Player m) /\ time_channel (Player m) /\ consistent (Player m).
Proof.
  intros m Hm.
  unfold Matter, time_channel, consistent, Player.
  simpl.
  split; [ exact Hm | split; [ reflexivity | intro H; discriminate H ] ].
Qed.

(* T4 canon A4''：悖论删除构型 ⇒ 撕裂（不一致）——与窗口/质量无关 *)
Theorem paradox_implies_rupture :
  forall l, is_true (paradox l) -> ~ consistent l.
Proof.
  intros l Hp Hc. unfold consistent in Hc. exact (Hc Hp).
Qed.

(* T5 非悖论载荷恒不撕裂（撕裂判定与窗口、质量无关——canon 语义） *)
Theorem nonparadox_consistent :
  forall (b : list bool) (m w : nat),
    consistent (Load_mk b m w false).
Proof.
  intros. unfold consistent. simpl. intro H. discriminate H.
Qed.

(* T6 高能实验室"删除原因"实例：短窗 + 悖论 ⇒ 撕裂 *)
Theorem hel_paradox_ruptures :
  exists l, (window_s l <= 1)%nat /\ is_true (paradox l) /\ ~ consistent l.
Proof.
  exists (Load_mk nil 1%nat 1%nat true).
  unfold consistent. simpl.
  split; [ lia | split; [ reflexivity | intro H; apply H; reflexivity ] ].
Qed.

(* ======================================================================
   §5 极大似然估计 p̂ = 1/N 的最优性（完全形式化：求导 + MVT）
   ======================================================================
   设定：N = k + 1 ≥ 2 次尝试，观测到"第 N 轮首次成功"。
   似然 L_k(p) = p·(1−p)^k；以下证明：
     ∀p∈(0,1), p ≠ p̂ ⟹ L_k(p) < L_k(p̂)，其中 p̂ = 1/(INR k + 1)。
   方法：h = ln∘L 在 (0, p̂) 严格增、在 (p̂, 1) 严格减，
   由 MVT_cor2（中值定理）＋ h' 的符号得到；再用 ln_lt_inv 还原。*)

Definition Llik (k : nat) (p : R) : R := p * rp (1 - p) k.
Definition hlog_g (k : nat) (x : R) : R := INR k * ln (1 - x).
Definition hlog (k : nat) : R -> R := (ln + hlog_g k)%F.
Definition hder (k : nat) (x : R) : R := 1 / x - INR k * (1 / (1 - x)).
Definition phat (k : nat) : R := 1 / (INR k + 1).

(* ---- 基础辅助 ---- *)

Lemma rp_pos : forall q k, 0 < q -> 0 < rp q k.
Proof.
  intros q k Hq. induction k as [| k IH]; simpl.
  - lra.
  - apply Rmult_lt_0_compat; assumption.
Qed.

Lemma ln_rp : forall b k, 0 < b -> ln (rp b k) = INR k * ln b.
Proof.
  intros b k Hb. induction k as [| k IH].
  - simpl. rewrite ln_1. ring.
  - change (ln (b * rp b k) = INR (S k) * ln b).
    rewrite (ln_mult b (rp b k) Hb (rp_pos b k Hb)).
    rewrite IH.
    rewrite S_INR.
    ring.
Qed.

Lemma INR_k_pos : forall k, (1 <= k)%nat -> 0 < INR k.
Proof.
  intros k Hk. apply lt_0_INR. lia.
Qed.

Lemma phat_pos : forall k, (1 <= k)%nat -> 0 < phat k.
Proof.
  intros k Hk.
  assert (Hk0 : 0 < INR k) by (apply INR_k_pos; exact Hk).
  unfold phat, Rdiv.
  apply Rmult_lt_0_compat; [ lra | apply Rinv_0_lt_compat; lra ].
Qed.

Lemma phat_lt_1 : forall k, (1 <= k)%nat -> phat k < 1.
Proof.
  intros k Hk. unfold phat.
  assert (Hk0 : 0 < INR k) by (apply INR_k_pos; exact Hk).
  assert (Hr : 1 < INR k + 1) by lra.
  assert (Hprod : 0 < 1 * (INR k + 1)) by lra.
  assert (Hd : 1 / (INR k + 1) = / (INR k + 1)) by (field; try lra).
  rewrite Hd.
  assert (Hc := Rinv_lt_contravar 1 (INR k + 1) Hprod Hr).
  assert (Hinv1 : / 1 = 1) by (field; try lra).
  rewrite Hinv1 in Hc. exact Hc.
Qed.

Lemma Llik_pos : forall k p, 0 < p -> p < 1 -> 0 < Llik k p.
Proof.
  intros k p H0 H1. unfold Llik.
  apply Rmult_lt_0_compat; [ exact H0 | apply rp_pos; lra ].
Qed.

(* ---- 导数计算 ---- *)

Lemma deriv_lin_minus_id : forall x,
  derivable_pt_lim (fun t : R => 1 - t) x (0 - 1).
Proof.
  intros x.
  apply (derivable_pt_lim_minus (fct_cte 1) id x 0 1
           (derivable_pt_lim_const 1 x) (derivable_pt_lim_id x)).
Qed.

Lemma deriv_ln_minus_id : forall x, 0 < x -> x < 1 ->
  derivable_pt_lim (fun t : R => ln (1 - t)) x
    ((1 / (1 - x)) * (0 - 1)).
Proof.
  intros x Hx0 Hx1.
  assert (H1m : 0 < 1 - x) by lra.
  assert (Hdl : derivable_pt_lim ln (1 - x) (1 / (1 - x))).
  { assert (Heq : 1 / (1 - x) = / (1 - x)) by (field; try lra).
    rewrite Heq.
    apply derivable_pt_lim_ln. exact H1m. }
  (* comp：ln ∘ (1 − t) *)
  assert (Hcomp := derivable_pt_lim_comp (fun t : R => 1 - t) ln x
                     (0 - 1) (1 / (1 - x))
                     (deriv_lin_minus_id x) Hdl).
  change (comp ln (fun t : R => 1 - t)) with (fun t : R => ln (1 - t)) in Hcomp.
  exact Hcomp.
Qed.

Lemma deriv_h_lim : forall k x, 0 < x -> x < 1 ->
  derivable_pt_lim (hlog k) x (hder k x).
Proof.
  intros k x Hx0 Hx1.
  assert (Hdln : derivable_pt_lim ln x (1 / x)).
  { assert (Heq : 1 / x = / x) by (field; try lra).
    rewrite Heq.
    apply derivable_pt_lim_ln. exact Hx0. }
  assert (Hdlog : derivable_pt_lim (fun t : R => ln (1 - t)) x
                    ((1 / (1 - x)) * (0 - 1)))
    by (apply deriv_ln_minus_id; assumption).
  assert (Hdscale : derivable_pt_lim
             (fun t : R => INR k * ln (1 - t)) x
             (INR k * ((1 / (1 - x)) * (0 - 1)))).
  { apply (derivable_pt_lim_scal (fun t : R => ln (1 - t))
             (INR k) x ((1 / (1 - x)) * (0 - 1)) Hdlog). }
  assert (Hplus := derivable_pt_lim_plus ln
             (fun t : R => INR k * ln (1 - t)) x
             (1 / x) (INR k * ((1 / (1 - x)) * (0 - 1)))
             Hdln Hdscale).
  assert (Hv : 1 / x + INR k * ((1 / (1 - x)) * (0 - 1)) = hder k x).
  { unfold hder. field. lra. }
  rewrite Hv in Hplus. exact Hplus.
Qed.

(* ---- hder 的两种符号 ---- *)

Lemma hder_form : forall k x, x <> 0 -> x <> 1 ->
  hder k x = (1 - (INR k + 1) * x) * ((1 / x) * (1 / (1 - x))).
Proof.
  intros k x Hx0 Hx1.
  assert (Hn1 : 1 - x <> 0).
  { intros H. apply Hx1. lra. }
  unfold hder.
  field.
  split; [ exact Hn1 | exact Hx0 ].
Qed.

Lemma neg_mul_pos_neg : forall a b, a < 0 -> 0 < b -> a * b < 0.
Proof.
  intros a b Ha Hb.
  pose proof (Rmult_lt_compat_r b a 0 Hb Ha) as Ht.
  rewrite Rmult_0_l in Ht. exact Ht.
Qed.

Lemma hder_pos_lt_phat : forall k c, (1 <= k)%nat ->
  0 < c -> c < phat k -> 0 < hder k c.
Proof.
  intros k c Hk Hc0 Hcph.
  assert (Hc1 : c < 1).
  { apply Rlt_trans with (phat k); [ exact Hcph | apply phat_lt_1; exact Hk ]. }
  rewrite hder_form; [ | lra | lra ].
  assert (Hk0 : 0 < INR k) by (apply INR_k_pos; exact Hk).
  assert (Hk1 : 0 < INR k + 1) by lra.
  assert (Ht := Rmult_lt_compat_l (INR k + 1) c (phat k) Hk1 Hcph).
  assert (Hone : (INR k + 1) * phat k = 1).
  { unfold phat. field. lra. }
  rewrite Hone in Ht.
  assert (HA : 0 < 1 - (INR k + 1) * c) by lra.
  assert (HB : 0 < 1 / c)
    by (unfold Rdiv; apply Rmult_lt_0_compat; [ lra | apply Rinv_0_lt_compat; exact Hc0 ]).
  assert (HC : 0 < 1 / (1 - c))
    by (unfold Rdiv; apply Rmult_lt_0_compat; [ lra | apply Rinv_0_lt_compat; lra ]).
  assert (HBC : 0 < (1 / c) * (1 / (1 - c)))
    by (apply Rmult_lt_0_compat; assumption).
  apply Rmult_lt_0_compat; assumption.
Qed.

Lemma hder_neg_above_phat : forall k c, (1 <= k)%nat ->
  phat k < c -> c < 1 -> hder k c < 0.
Proof.
  intros k c Hk Hcph Hc1.
  assert (Hp0 : 0 < phat k) by (apply phat_pos; exact Hk).
  rewrite hder_form; [ | lra | lra ].
  assert (Hk0 : 0 < INR k) by (apply INR_k_pos; exact Hk).
  assert (Hk1 : 0 < INR k + 1) by lra.
  assert (Ht := Rmult_lt_compat_l (INR k + 1) (phat k) c Hk1 Hcph).
  assert (Hone : (INR k + 1) * phat k = 1).
  { unfold phat. field. lra. }
  rewrite Hone in Ht.
  assert (HA : 1 - (INR k + 1) * c < 0) by lra.
  assert (HB : 0 < 1 / c)
    by (unfold Rdiv; apply Rmult_lt_0_compat; [ lra | apply Rinv_0_lt_compat; lra ]).
  assert (HC : 0 < 1 / (1 - c))
    by (unfold Rdiv; apply Rmult_lt_0_compat; [ lra | apply Rinv_0_lt_compat; lra ]).
  assert (HBC : 0 < (1 / c) * (1 / (1 - c)))
    by (apply Rmult_lt_0_compat; assumption).
  exact (neg_mul_pos_neg (1 - (INR k + 1) * c)
           ((1 / c) * (1 / (1 - c))) HA HBC).
Qed.

(* ---- 用 MVT 证明单调性 ---- *)

Lemma h_inc_below : forall k a b, (1 <= k)%nat ->
  0 < a -> a < b -> b <= phat k -> hlog k a < hlog k b.
Proof.
  intros k a b Hk Ha0 Hab Hbph.
  pose proof (MVT_cor2 (hlog k) (hder k) a b Hab) as Hm.
  assert (Hall : forall c, a <= c <= b ->
                  derivable_pt_lim (hlog k) c (hder k c)).
  { intros c [Hac Hcb]. apply deriv_h_lim.
    - lra.
    - assert (Hp1 : phat k < 1) by (apply phat_lt_1; exact Hk).
      lra. }
  destruct (Hm Hall) as [c [Heq [Hac Hcb]]].
  assert (Hc0 : 0 < c) by lra.
  assert (Hcph : c < phat k) by lra.
  assert (Hdpos : 0 < hder k c)
    by (apply hder_pos_lt_phat; [ exact Hk | exact Hc0 | exact Hcph ]).
  assert (Hbma : 0 < b - a) by lra.
  assert (Hprod : 0 < hder k c * (b - a))
    by (apply Rmult_lt_0_compat; assumption).
  rewrite <- Heq in Hprod. lra.
Qed.

Lemma h_dec_above : forall k a b, (1 <= k)%nat ->
  phat k <= a -> a < b -> b < 1 -> hlog k b < hlog k a.
Proof.
  intros k a b Hk Haph Hab Hb1.
  pose proof (MVT_cor2 (hlog k) (hder k) a b Hab) as Hm.
  assert (Hall : forall c, a <= c <= b ->
                  derivable_pt_lim (hlog k) c (hder k c)).
  { intros c [Hac Hcb]. apply deriv_h_lim.
    - assert (Hp0 : 0 < phat k) by (apply phat_pos; exact Hk).
      lra.
    - lra. }
  destruct (Hm Hall) as [c [Heq [Hac Hcb]]].
  assert (Hcph : phat k < c) by lra.
  assert (Hc1 : c < 1) by lra.
  assert (Hdneg : hder k c < 0)
    by (apply hder_neg_above_phat; [ exact Hk | exact Hcph | exact Hc1 ]).
  assert (Hbma : 0 < b - a) by lra.
  assert (Hprod : hder k c * (b - a) < 0)
    by (apply neg_mul_pos_neg; assumption).
  rewrite <- Heq in Hprod. lra.
Qed.

(* ---- 全局最大 ---- *)

Lemma h_global_max : forall k p, (1 <= k)%nat ->
  0 < p -> p < 1 -> p <> phat k -> hlog k p < hlog k (phat k).
Proof.
  intros k p Hk Hp0 Hp1 Hne.
  destruct (Req_dec p (phat k)) as [Heq | Hd].
  - exfalso. exact (Hne Heq).
  - destruct (Rtotal_order p (phat k)) as [Hlt | [Heq | Hgt]].
    + apply h_inc_below with (a := p) (b := phat k);
        try exact Hk; try exact Hp0.
      * exact Hlt.
      * apply Rle_refl.
    + exfalso. exact (Hne Heq).
    + apply h_dec_above with (a := phat k) (b := p);
        try exact Hk.
      * apply Rle_refl.
      * exact Hgt.
      * exact Hp1.
Qed.

(* ---- 还原到似然 ---- *)

Lemma loglik_log : forall k p, 0 < p -> p < 1 ->
  ln (Llik k p) = hlog k p.
Proof.
  intros k p Hp0 Hp1. unfold Llik, hlog.
  assert (H1m : 0 < 1 - p) by lra.
  rewrite (ln_mult p (rp (1 - p) k) Hp0 (rp_pos (1 - p) k H1m)).
  rewrite (ln_rp (1 - p) k H1m).
  reflexivity.
Qed.

Theorem mle_optimal : forall (k : nat) (p : R),
  (1 <= k)%nat -> 0 < p -> p < 1 -> p <> phat k ->
  Llik k p < Llik k (phat k).
Proof.
  intros k p Hk Hp0 Hp1 Hne.
  apply ln_lt_inv.
  - apply Llik_pos; assumption.
  - apply Llik_pos; [ apply phat_pos; exact Hk | apply phat_lt_1; exact Hk ].
  - rewrite (loglik_log k p Hp0 Hp1).
    rewrite (loglik_log k (phat k) (phat_pos k Hk) (phat_lt_1 k Hk)).
    apply h_global_max; assumption.
Qed.

(* 推论：p̂ 是（唯一）最大值点 *)
Theorem mle_max : forall (k : nat) (p : R),
  (1 <= k)%nat -> 0 < p -> p < 1 ->
  Llik k p <= Llik k (phat k).
Proof.
  intros k p Hk Hp0 Hp1.
  destruct (Req_dec p (phat k)) as [Heq | Hne].
  - rewrite Heq. apply Rle_refl.
  - apply Rlt_le. apply mle_optimal; assumption.
Qed.

Theorem mle_unique_max : forall (k : nat) (p : R),
  (1 <= k)%nat -> 0 < p -> p < 1 ->
  Llik k p = Llik k (phat k) -> p = phat k.
Proof.
  intros k p Hk Hp0 Hp1 Heq.
  destruct (Req_dec p (phat k)) as [Hpeq | Hpne].
  - exact Hpeq.
  - exfalso.
    pose proof (mle_optimal k p Hk Hp0 Hp1 Hpne) as Hs.
    rewrite Heq in Hs.
    apply (Rlt_irrefl (Llik k (phat k))). exact Hs.
Qed.

(* ---- 端点行为与闭区间 [0,1] 版本 ---- *)

Lemma rp_zero_base : forall k, (1 <= k)%nat -> rp 0 k = 0.
Proof.
  intros k Hk. destruct k as [| k'].
  - lia.
  - simpl. ring.
Qed.

Lemma Llik_at_zero : forall k, Llik k 0 = 0.
Proof. intros k. unfold Llik. ring. Qed.

Lemma Llik_at_one : forall k, (1 <= k)%nat -> Llik k 1 = 0.
Proof.
  intros k Hk. unfold Llik.
  assert (Hz : 1 - 1 = 0) by lra. rewrite Hz.
  rewrite (rp_zero_base k Hk). ring.
Qed.

Lemma Llik_phat_pos : forall k, (1 <= k)%nat -> 0 < Llik k (phat k).
Proof.
  intros k Hk.
  apply Llik_pos; [ apply phat_pos; exact Hk | apply phat_lt_1; exact Hk ].
Qed.

(* 闭区间版：p̂ 在 [0,1] 上仍是最大值点 *)
Theorem mle_max_closed : forall (k : nat) (p : R),
  (1 <= k)%nat -> 0 <= p -> p <= 1 ->
  Llik k p <= Llik k (phat k).
Proof.
  intros k p Hk Hp0 Hp1.
  destruct (Req_dec p 0) as [Hpz | Hpnz].
  - rewrite Hpz, Llik_at_zero.
    apply Rlt_le. apply Llik_phat_pos. exact Hk.
  - destruct (Req_dec p 1) as [Hpo | Hpno].
    + rewrite Hpo, (Llik_at_one k Hk).
      apply Rlt_le. apply Llik_phat_pos. exact Hk.
    + destruct (Rle_lt_or_eq_dec 0 p Hp0) as [Hp0lt | Hp0eq].
      * destruct (Rle_lt_or_eq_dec p 1 Hp1) as [Hp1lt | Hp1eq].
        -- apply mle_max; assumption.
        -- exfalso. exact (Hpno Hp1eq).
      * exfalso. exact (Hpnz (eq_sym Hp0eq)).
Qed.

(* 闭区间版：唯一性 *)
Theorem mle_unique_max_closed : forall (k : nat) (p : R),
  (1 <= k)%nat -> 0 <= p -> p <= 1 ->
  Llik k p = Llik k (phat k) -> p = phat k.
Proof.
  intros k p Hk Hp0 Hp1 Heq.
  destruct (Req_dec p 0) as [Hpz | Hpnz].
  - exfalso.
    rewrite Hpz, Llik_at_zero in Heq.
    pose proof (Llik_phat_pos k Hk) as Hpos.
    rewrite <- Heq in Hpos.
    apply (Rlt_irrefl 0). exact Hpos.
  - destruct (Req_dec p 1) as [Hpo | Hpno].
    + exfalso.
      rewrite Hpo, (Llik_at_one k Hk) in Heq.
      pose proof (Llik_phat_pos k Hk) as Hpos.
      rewrite <- Heq in Hpos.
      apply (Rlt_irrefl 0). exact Hpos.
    + destruct (Rle_lt_or_eq_dec 0 p Hp0) as [Hp0lt | Hp0eq].
      * destruct (Rle_lt_or_eq_dec p 1 Hp1) as [Hp1lt | Hp1eq].
        -- apply mle_unique_max; assumption.
        -- exfalso. exact (Hpno Hp1eq).
      * exfalso. exact (Hpnz (eq_sym Hp0eq)).
Qed.

(* 应用到 N* = 9,318,054：取 k = n_star − 1（即 N = k + 1），
   最优估计 p̂ = 1/(INR (n_star−1) + 1) = 1/INR n_star ≈ 1.07×10^-7，
   与文档 §5.1 的 p̂ = 1/N* 一致（S_INR：INR n_star = INR (n_star−1) + 1）。
   〔注 2026-09-05：十进制带以 R 字面量 9318054 陈述（与 INR n_star 同值，
   INR 归约层等价，未单列桥引理）；勿用 INR 大 nat 进 nra——unary 表示
   会炸，实测失败。〕 *)

(* 十进制带：1.07×10^-7 < p̂ < 1.08×10^-7（nra 直闭，2026-09-05） *)
Theorem phat_band_1e7 :
  107 / 10 ^ 9 < 1 / 9318054 /\ 1 / 9318054 < 108 / 10 ^ 9.
Proof. nra. Qed.

(* ---- 观测常数实例化（2026-09-05 追加）：把泛化最优性钉到 N* ---- *)
(* p̂ 在观测轮的值：phat (n_star−1) = 1/INR n_star（n_star = S (n_star−1)
   定义性成立，S_INR 桥接分母） *)
Lemma phat_at_nstar : phat (n_star - 1) = 1 / INR n_star.
Proof.
  assert (Hsucc : n_star = S (n_star - 1)) by (symmetry; reflexivity).
  unfold phat. rewrite Hsucc. rewrite (S_INR (n_star - 1)). reflexivity.
Qed.

(* MLE 唯一性在观测数据上的实例：观测到 N* = 9,318,054 次才首成功 ⇒
   p̂ = 1/INR n_star 是闭区间 [0,1] 上唯一的极大似然估计
   （mle_unique_max_closed 实例化；1 ≤ n_star−1 由数值判定） *)
Theorem mle_unique_at_nstar :
  forall p : R,
    0 <= p -> p <= 1 ->
    Llik (n_star - 1) p = Llik (n_star - 1) (1 / INR n_star) ->
    p = 1 / INR n_star.
Proof.
  intros p Hp0 Hp1 Heq.
  rewrite <- (phat_at_nstar) in Heq.
  apply (mle_unique_max_closed (n_star - 1) p).
  - assert (Hleb : Nat.leb 1 (n_star - 1) = true) by reflexivity.
    apply (proj1 (Nat.leb_le 1 (n_star - 1))). exact Hleb.
  - exact Hp0.
  - exact Hp1.
  - exact Heq.
Qed.

(* ======================================================================
   §6 事件因果层：悖论"删除原因 → 无一致赋值"的最小机制模型
   ======================================================================
   机制（把 §4 的谓词级"悖论 ⇒ 撕裂"推进到因果事件图）：
     · 每个效应事件 e：要么是根（cause e = None），要么要求某原因事件
       在场（cause e = Some c 且 present c = true）。
     · consistent_events := 事件集 dom 内每个在场事件都满足该要求。
   高能实验室场景：0 = 原因（把石块送入黑洞），1 = 效应（石块从白洞
   出来）。删除原因而保留效应 ⇒ 不存在一致赋值（定理 2）。
   只依赖 nat/bool/list，零 Axiom。                                  *)

Definition cause_ok
  (cause : nat -> option nat) (present : nat -> bool) (e : nat) : Prop :=
  match cause e with
  | None   => True
  | Some c => is_true (present c)
  end.

Definition consistent_events
  (cause : nat -> option nat) (present : nat -> bool) (dom : list nat) : Prop :=
  forall e, In e dom -> is_true (present e) -> cause_ok cause present e.

(* 场景：0=原因（送入黑洞），1=效应（从白洞出来） *)
Definition hel_cause (e : nat) : option nat :=
  match e with
  | 1 => Some 0%nat
  | _ => None
  end.

Definition hel_present (e : nat) : bool :=
  match e with 0 => true | 1 => true | _ => false end.

Definition hel_present_no_cause (e : nat) : bool :=
  match e with 1 => true | _ => false end.

(* 定理 1：原因在场时，HEL 事件图一致（短窗副本被容忍的机制表述） *)
Theorem hel_consistent_with_cause :
  consistent_events hel_cause hel_present (0%nat :: 1%nat :: nil).
Proof.
  intros e He Hp.
  unfold cause_ok.
  destruct (hel_cause e) as [c |] eqn:Hc.
  - destruct He as [He0 | [He1 | []]]; subst.
    + simpl in Hc. discriminate.
    + simpl in Hc. injection Hc. intros Hc0. subst.
      unfold is_true. simpl. reflexivity.
  - exact I.
Qed.

(* 定理 2：删除原因（石块不再进入黑洞）而保留效应 ⇒ 无一致赋值 *)
Theorem hel_inconsistent_after_removal :
  ~ consistent_events hel_cause hel_present_no_cause (0%nat :: 1%nat :: nil).
Proof.
  intros H.
  assert (Hbad : cause_ok hel_cause hel_present_no_cause 1%nat).
  { apply (H 1%nat).
    - right. left. reflexivity.
    - unfold is_true. simpl. reflexivity. }
  unfold cause_ok in Hbad.
  simpl in Hbad. unfold is_true in Hbad. discriminate Hbad.
Qed.

(* 定理 3：机制层结论与 §4 语义层一致（该构型在语义层必判撕裂） *)
Theorem causal_gap_semantics :
  ~ consistent (Load_mk nil 1%nat 1%nat true).
Proof.
  intros Hc. unfold consistent in Hc. exact (Hc (eq_refl true)).
Qed.

(* ---- §6b 自举与逐轮维持（社区规则：不再跳入 = 删除原因） ----
   规则（wiki Endings，2026-09-04 社区确认）：
     · 第 r 轮开局的副本存在（copy r）⟺ 第 r−1 轮有人跳入（j r−1）；
     · 副本存在期间，其因果环要求本轮终点仍有人跳入（逐轮维持）。
       某轮"有副本却无人跳入" ⇒ 无一致解（撕裂条件）。
   定理 4：持续跳入 ⇒ 逐轮一致（共存被容忍）。
   定理 5：从某轮起停止跳入 ⇒ 不一致（删除原因）。
   定理 6：副本存在性 = 上一轮跳入（定义性）。                     *)

Definition jump_forever (r : nat) : bool := true.

Definition jump_stop_at_2 (r : nat) : bool :=
  match r with
  | 0 => true | 1 => true | 2 => true | _ => false
  end.

(* copy r := 上一轮（r−1）是否跳入；第 0 轮无副本 *)
Definition copy_of (j : nat -> bool) (r : nat) : bool :=
  match r with
  | 0    => false
  | S m  => j m
  end.

(* 逐轮一致：轮 r 有副本 ⇒ 本轮终点有人跳入（维持原因） *)
Definition bootstrap_consistent (j : nat -> bool) (N : nat) : Prop :=
  forall r, (r <= N)%nat -> is_true (copy_of j r) -> is_true (j r).

(* 定理 4：持续跳入 ⇒ 共存一致 *)
Theorem coexistence_stable :
  bootstrap_consistent jump_forever 3.
Proof.
  intros r Hr Hc.
  unfold jump_forever, is_true. reflexivity.
Qed.

(* 定理 5：第 2 轮后停止跳入 ⇒ 不一致（社区"不再跳入=撕裂"） *)
Theorem rupture_when_stop_jumping :
  ~ bootstrap_consistent jump_stop_at_2 3.
Proof.
  intros H.
  assert (Hbad : is_true (jump_stop_at_2 3)).
  { apply (H 3%nat).
    - lia.
    - unfold copy_of, jump_stop_at_2. simpl. reflexivity. }
  unfold jump_stop_at_2 in Hbad. simpl in Hbad.
  unfold is_true in Hbad.
  discriminate.
Qed.

(* 定理 6：副本存在性 = 上一轮跳入（定义性事实） *)
Theorem copy_is_last_jump :
  forall (j : nat -> bool) (m : nat), copy_of j (S m) = j m.
Proof.
  intros. unfold copy_of. reflexivity.
Qed.

(* ======================================================================
   §7 剩余边界的封闭：B2（计时事实）与 B4（缩放不变性）
   ======================================================================
   B2：把"白洞站测量 δ≈10^-5 s（WHS_X3，one hundred-thousandth）"编码为
   计时定义后，'到达早于出发'是定义性定理（A1 的内容即其自身，
   无新增公理）。
   B4：低能段模型只依赖比值 E/E₀ ⇒ 绝对能量标度 λ 任意缩放都不改变
   预测 ⇒ 在 κ（绝对标度）缺失时参数族不可能唯一（欠定的事实性
   形式化）。                                                *)

(* ---- B2：计时事实 ---- *)

(* 白洞站实测负时间：one hundred-thousandth of a second（WHS_X3） *)
Definition warp_delta : R := / 100000.

(* 与 txt 锚点 (a) 的 1×10^-5 s 写法同值（hook 引理，2026-09-05） *)
Lemma warp_delta_is_10_m5 : warp_delta = 1 / 10 ^ 5.
Proof. unfold warp_delta. nra. Qed.

Theorem warp_delta_pos : 0 < warp_delta.
Proof.
  unfold warp_delta. apply Rinv_0_lt_compat. lra.
Qed.

(* 到达早于出发：t_out = t_in − δ < t_in *)
Theorem warp_arrives_before_departure :
  forall t, t - warp_delta < t.
Proof.
  intros t. unfold warp_delta.
  pose proof warp_delta_pos as Hd.
  lra.
Qed.

(* ---- B4：缩放不变性（绝对标度不可辨识 ⇒ 参数族欠定） ---- *)

(* 低能段形状：δ(E) = τ₁ · ln(1 + E/E₀)（文档式 (3.3)） *)
Definition delta_low (tau1 E0 : R) (E : R) : R :=
  tau1 * ln (1 + E / E0).

(* 等价变形：δ 只依赖比值 E/E₀。联合缩放 (E, E₀) ↦ (λE, λE₀) 不变 *)
Lemma low_energy_scaling_invariance :
  forall (tau1 E0 E lam : R),
    lam <> 0 -> E0 <> 0 ->
    delta_low tau1 (lam * E0) (lam * E) = delta_low tau1 E0 E.
Proof.
  intros tau1 E0 E lam Hlam HE0.
  unfold delta_low.
  assert (Hq : (lam * E) / (lam * E0) = E / E0).
  { assert (Hc : lam * E0 <> 0).
    { intros Hz.
      destruct (Rmult_integral lam E0 Hz) as [Hl | He].
      - apply Hlam. exact Hl.
      - apply HE0. exact He. }
    field. split; assumption. }
  rewrite Hq. reflexivity.
Qed.

(* 推论：存在无穷多互异 (E₀ 标度) 给出同一预测形状——文本说明见
   docs/ATP_模型完整性分析.md B4（κ 缺失 ⇒ 无唯一模型） *)
Theorem low_energy_scale_family :
  forall (tau1 E0 : R), E0 <> 0 ->
  forall lam, lam <> 0 ->
    delta_low tau1 (lam * E0) (lam * 1) = delta_low tau1 E0 1.
Proof.
  intros. apply low_energy_scaling_invariance; assumption.
Qed.

(* ======================================================================
   §8 B6：有限均匀弹道模型 ⇒ 几何分布（随机性假设的最小显式化）
   ======================================================================
   处理：把 §5 的"i.i.d. 几何"还原为 canon 可陈述的最小随机原语——
   每轮探测器从 D 条弹道中均匀选择一条，其中 k 条会命中（0<k<D；
   canon："每轮弹道随机"，01:1466/03:244 语境）。在该模型下：
     P(前 m 轮全避) = ((D−k)/D)^m；P(第 m+1 轮首次命中) = q^m·p，
     p := k/D。这些恒等式为定理；唯一保留的统计原语是"均匀选择"
     （无测度论库时不可再推导，已在注释中显式化）。              *)

Lemma INR_add_local : forall n m : nat, INR (n + m) = INR n + INR m.
Proof.
  intros n m. induction m as [| m IH].
  - rewrite Nat.add_0_r. simpl. lra.
  - rewrite Nat.add_succ_r. rewrite S_INR. rewrite IH. rewrite S_INR. lra.
Qed.

(* 均匀模型中的"全避"因子：1 − k/D = (D−k)/D *)
Lemma uniform_ratio_identity :
  forall D k : nat, (k <= D)%nat -> (INR D <> 0) ->
    INR (D - k) / INR D = 1 - INR k / INR D.
Proof.
  intros D k Hle HD0.
  assert (HD : Nat.add (Nat.sub D k) k = D) by lia.
  assert (Hin : INR (D - k) + INR k = INR D).
  { rewrite <- (INR_add_local (D - k) k). rewrite HD. reflexivity. }
  assert (HA : INR (D - k) = INR D - INR k) by lra.
  rewrite HA.
  field. exact HD0.
Qed.

(* 定理：均匀模型下"前 m 轮全避"的概率因子 = (1 − k/D)^m *)
Theorem uniform_avoid_factor_geom :
  forall (D k m : nat), (k <= D)%nat -> (INR D <> 0) ->
    rp (INR (D - k) / INR D) m = rp (1 - INR k / INR D) m.
Proof.
  intros D k m Hle HD0.
  induction m as [| m IH].
  - simpl. reflexivity.
  - simpl. rewrite IH.
    rewrite (uniform_ratio_identity D k Hle HD0).
    reflexivity.
Qed.

(* 定理：均匀模型下"第 m+1 轮首次命中"= q^m · p（几何分布 pmf） *)
Theorem uniform_first_hit_geom :
  forall (D k m : nat), (k <= D)%nat -> (INR D <> 0) ->
    rp (1 - INR k / INR D) m * (INR k / INR D)
    = rp (INR (D - k) / INR D) m * (INR k / INR D).
Proof.
  intros D k m Hle HD0.
  rewrite (uniform_avoid_factor_geom D k m Hle HD0).
  reflexivity.
Qed.

(* 注：当 0<k<D 时 p := k/D ∈ (0,1)，q := 1−p ∈ (0,1)，正是 §5 几何
   模型要求的开区间条件；§5 的 mle_optimal 直接适用。 *)

(* ======================================================================
   §9 信息载荷的相对扰动界（后续清单 #1；2026-09-05）
   ======================================================================
   设定（txt §4.1 口径）：数据由无质量场承载，能量 ε = h·L·ν（符号化，
  量纲/常量取值为〔假定〕）；相对扰动 = ε/E_sn。本节机器化"ε 足够小时
   相对扰动有界"的代数部分（阈值数值仍为参数）。                          *)

Definition carrier_energy (h L nu : R) : R := h * (L * nu).
Definition rel_pert (eps E_sn : R) : R := eps / E_sn.

Lemma carrier_nonneg : forall h L nu, 0 <= h -> 0 <= L -> 0 <= nu ->
  0 <= carrier_energy h L nu.
Proof.
  intros h L nu Hh HL Hnu. unfold carrier_energy.
  apply Rmult_le_pos; [ exact Hh | apply Rmult_le_pos; assumption ].
Qed.

Lemma rel_pert_bound : forall eps E_sn,
  0 <= eps -> 0 < E_sn -> eps <= E_sn / 1000 ->
  rel_pert eps E_sn <= 1 / 1000.
Proof.
  intros eps E_sn Heps HE Hle. unfold rel_pert.
  assert (Hz : E_sn <> 0) by lra.
  assert (Heq : (E_sn / 1000) * / E_sn = 1 / 1000).
  { unfold Rdiv. field. exact Hz. }
  assert (Hm : eps * / E_sn <= (E_sn / 1000) * / E_sn).
  { apply Rmult_le_compat_r.
    - apply Rlt_le. apply Rinv_0_lt_compat. exact HE.
    - exact Hle. }
  rewrite Heq in Hm. exact Hm.
Qed.

(* 载体形式：h·L·ν ≤ E_sn/1000 ⇒ 相对扰动 ≤ 1/1000 *)
Lemma carrier_pert_bound : forall h L nu E_sn,
  0 <= h -> 0 <= L -> 0 <= nu -> 0 < E_sn ->
  carrier_energy h L nu <= E_sn / 1000 ->
  rel_pert (carrier_energy h L nu) E_sn <= 1 / 1000.
Proof.
  intros h L nu E_sn Hh HL Hnu HE Hle.
  apply (rel_pert_bound (carrier_energy h L nu) E_sn).
  - apply carrier_nonneg; assumption.
  - exact HE.
  - exact Hle.
Qed.

(* ======================================================================
   §10 间隙动作可达性（后续清单 #2/#3；2026-09-05）
   ======================================================================
   机制（正文 §6 因果层的扩展口）：撕裂 = 删除原因构型；删除需要"在
   间隙 [t_out, t_in] 内存在一个占用 ≥ minact 时长的动作"。δ < minact
   ⇒ 任何删除动作都塞不进间隙（gap_too_small_blocks）⇒ 原因自动在场
   ⇒ 短窗构型一致性自动成立（10⁻⁵ s 窗口"像没产生因果"的机器表述）。
   信息（#3）不改变几何：知识再多，间隙不够时删除动作仍不可达。
   〔机器化范围注 2026-09-05：本节机器覆盖止于第一段"删除动作不可达"
   （纯 List/nat/Lia）；"原因自动在场 ⇒ 一致性成立"需把间隙/动作层
   （nat 时间）与 §6 的 present 标志层接起来——桥定理不存在（txt §7
   行 #2/#3 保持候选），本节不宣称后两段已机器化。〕                    *)

Definition del_span_ok (out inn minact a b : nat) : Prop :=
  (out <= a)%nat /\ (b <= inn)%nat /\ (minact <= b - a)%nat.

(* 定理：间隙长度小于最小动作跨度 ⇒ 间隙内不存在删除动作 *)
Lemma gap_too_small_blocks :
  forall out inn minact, (inn - out < minact)%nat ->
  forall a b, (out <= a)%nat -> (b <= inn)%nat ->
  ~ del_span_ok out inn minact a b.
Proof.
  intros out inn minact Hgap a b Ha Hb Hdel.
  unfold del_span_ok in Hdel.
  destruct Hdel as [Ha' [Hb' Hlen]].
  assert (H1 : (b - a <= b - out)%nat).
  { apply (Nat.sub_le_mono_l out a b). lia. }
  assert (H2 : (b - out <= inn - out)%nat).
  { apply (Nat.sub_le_mono_r b inn out). lia. }
  assert (Htot : (b - a <= inn - out)%nat) by lia.
  lia.
Qed.

(* 推论：#3 信息层表述——知识不能单独制造删除（几何不变） *)
Lemma knowledge_alone_cannot_delete :
  forall (knows_delete : bool) out inn minact,
    (inn - out < minact)%nat ->
    forall a b, (out <= a)%nat -> (b <= inn)%nat ->
    ~ del_span_ok out inn minact a b.
Proof.
  intros knows_delete out inn minact Hgap a b Ha Hb.
  apply (gap_too_small_blocks out inn minact Hgap a b Ha Hb).
Qed.

(* ======================================================================
   §11 能量闭合条件最小骨架（后续清单 #5；2026-09-05）
   ======================================================================
   §6b 方案 B：能量账目只在完整自洽环上结算，不存在单轮收支要求。
   定义环平衡 = Σ(采集 − 成本)；两条定理：① 单轮可不平衡而整环闭合
   （实例）；② 每轮都平衡 ⇒ 环平衡为零（平凡方向）。                       *)

Definition ring_balance (ecoll ecost : nat -> R) (N : nat) : R :=
  sum_f_R0 (fun i : nat => ecoll i - ecost i) N.

(* 实例：第 0 轮多采 5、第 1 轮多耗 5——单轮都不平衡，两轮环闭合 *)
Definition demo_ecoll (n : nat) : R :=
  match n with 0 => 5 | S _ => 0 end.
Definition demo_ecost (n : nat) : R :=
  match n with 0 => 0 | 1 => 5 | S (S _) => 0 end.

Theorem local_imbalance_ring_closed :
  ring_balance demo_ecoll demo_ecost 1 = 0.
Proof.
  unfold ring_balance. simpl. ring.
Qed.

(* 平凡方向：每轮收支相等 ⇒ 环平衡为零（对任意轮数 N；先证 sum 版） *)
Lemma sum_balanced_zero :
  forall (ecoll ecost : nat -> R) (N : nat),
    (forall i, (i <= N)%nat -> ecoll i = ecost i) ->
    sum_f_R0 (fun i : nat => ecoll i - ecost i) N = 0.
Proof.
  intros ecoll ecost. induction N as [| N IH]; intros Hall.
  - simpl. rewrite (Hall 0%nat (le_n 0)). ring.
  - simpl.
    assert (Hsum : sum_f_R0 (fun i : nat => ecoll i - ecost i) N = 0).
    { apply (IH (fun i Hi => Hall i (le_S _ _ Hi))). }
    rewrite Hsum.
    rewrite (Hall (S N) (le_n (S N))). ring.
Qed.

Theorem all_balanced_zero :
  forall (ecoll ecost : nat -> R) (N : nat),
    (forall i, (i <= N)%nat -> ecoll i = ecost i) ->
    ring_balance ecoll ecost N = 0.
Proof.
  intros ecoll ecost N Hall. unfold ring_balance.
  apply (sum_balanced_zero ecoll ecost N Hall).
Qed.

(* ======================================================================
   §12 能量闭合条件接入因果一致环（候选 #6；2026-09-05）
   ======================================================================
   §6b 方案 B 的完整形态：一致环 = 事件因果一致（§6 consistent_events）
   ∧ 环能量闭合（§11 ring_balance = 0）。                              *)

Definition ring_consistent
  (cause : nat -> option nat) (present : nat -> bool) (dom : list nat)
  (ecoll ecost : nat -> R) (N : nat) : Prop :=
  consistent_events cause present dom /\ ring_balance ecoll ecost N = 0.

(* 定义直读：环一致 = 因果一致 ∧ 能量闭合 *)
Theorem ring_consistent_iff_split :
  forall cause present dom ecoll ecost N,
    ring_consistent cause present dom ecoll ecost N <->
    consistent_events cause present dom /\ ring_balance ecoll ecost N = 0.
Proof. intros. reflexivity. Qed.

(* 实例：HEL 因果图（原因在场）配"第 0 轮多采 5、第 1 轮多耗 5"——
   单轮都不平衡，整环闭合，因果一致 ⇒ 环一致 *)
Theorem hel_ring_consistent_example :
  ring_consistent hel_cause hel_present (0%nat :: 1%nat :: nil)
    demo_ecoll demo_ecost 1.
Proof.
  unfold ring_consistent. split.
  - exact hel_consistent_with_cause.
  - exact local_imbalance_ring_closed.
Qed.

(* 对应 HEL 悖论版：删除原因 ⇒ 因果层已不一致（能量闭合无法救回） *)
Theorem hel_ring_after_removal_inconsistent :
  ~ ring_consistent hel_cause hel_present_no_cause (0%nat :: 1%nat :: nil)
      demo_ecoll demo_ecost 1.
Proof.
  unfold ring_consistent. intros H.
  destruct H as [Hc _].
  exact (hel_inconsistent_after_removal Hc).
Qed.

(* ======================================================================
   §13 44 分钟外推的代数形式（候选 #7；2026-09-05）
   ======================================================================
   E_req(2640) = exp(1320·ln r / 1319) · E_req(1320)，r = E_sn/E_b。
   数值面：r = 10^15 时 exp(1320·ln(10^15)/1319) ≈ 1.03×10^15（人工估算；
   有理界需 B3 类 exp 基础设施，另立）。                                    *)

Theorem ratio_44min_algebraic :
  forall (E_b E_sn : R),
    0 < E_b -> E_b < E_sn -> 0 < ln (E_sn / E_b) ->
    E_req E_b (tau2_of E_sn E_b) 2640
    = exp (1320 * ln (E_sn / E_b) / 1319)
      * E_req E_b (tau2_of E_sn E_b) 1320.
Proof.
  intros E_b E_sn H0 Hlt Hln.
  assert (Ha :
    (2640 - 1) / (1319 / ln (E_sn / E_b))
    = 1320 * ln (E_sn / E_b) / 1319 + ln (E_sn / E_b)).
  { field. all: first [ lra | apply Rlt_not_eq; exact Hln ]. }
  assert (Hb :
    (1320 - 1) / (1319 / ln (E_sn / E_b)) = ln (E_sn / E_b)).
  { field. all: first [ lra | apply Rlt_not_eq; exact Hln ]. }
  unfold E_req, tau2_of.
  rewrite Ha. rewrite Hb.
  rewrite (exp_plus (1320 * ln (E_sn / E_b) / 1319) (ln (E_sn / E_b))).
  ring.
Qed.

(* ======================================================================
   §14 因果删除：可达方向 + 缺因不一致（候选 a；2026-09-05）
   ======================================================================
   补 §10 的反方向：① 间隙足够 ⇒ 删除动作的"时间段"存在（可达性）；
   ② 任何"效应在场而原因缺席"的构型都不一致（§6 特例的泛化）。       *)

(* 泛化：效应在场、其直接原因缺席 ⇒ 事件层不一致 *)
Lemma inconsistent_effect_without_cause :
  forall (cause : nat -> option nat) (present : nat -> bool)
         (dom : list nat) e c,
    cause e = Some c -> In e dom -> is_true (present e) ->
    ~ is_true (present c) -> ~ consistent_events cause present dom.
Proof.
  intros cause present dom e c Hc Hin Hpe Hpc Hcons.
  pose proof (Hcons e Hin Hpe) as Hr.
  unfold cause_ok in Hr. rewrite Hc in Hr.
  exact (Hpc Hr).
Qed.

(* 可达性：间隙 ≥ minact 且区间非退化 ⇒ 存在可容纳删除动作的时间段 *)
Lemma gap_sufficient_interval :
  forall out inn minact, (out <= inn)%nat -> (minact <= inn - out)%nat ->
  exists a b, (out <= a)%nat /\ (b <= inn)%nat /\ (minact <= b - a)%nat.
Proof.
  intros out inn minact Hoi Hgap.
  exists out, (out + minact)%nat.
  split; [ lia | split; [ | ] ].
  - (* out + minact <= inn：由 minact <= inn - out 与 out <= inn *)
    assert (Hadd : (out + minact <= out + (inn - out))%nat).
    { apply Nat.add_le_mono_l. exact Hgap. }
    assert (Heq : (out + (inn - out) = inn)%nat).
    { rewrite Nat.add_comm. apply Nat.sub_add. exact Hoi. }
    lia.
  - (* minact <= (out + minact) - out *)
    rewrite Nat.add_comm. rewrite (Nat.add_sub minact out). lia.
Qed.

(* ======================================================================
   §15 低能段锚点族的缩放欠定（候选 b；2026-09-05）
   ======================================================================
   锚点方程（txt §3.3 标定框架）：ln(1+κ/E₀) = 10^5·ln(1+E_a/E₀)。
   联合缩放 (κ, E₀, E_a) ↦ λ·(·) 不变 ⇒ 绝对能量单位不可辨识 ⇒ κ 无值时
   参数族欠定（B4 风格的 κ 版）。                                     *)

Definition anchors_ok (kappa E0 Ea : R) : Prop :=
  ln (1 + kappa / E0) = 100000 * ln (1 + Ea / E0).

Theorem anchors_scale_invariance :
  forall kappa E0 Ea lam : R,
    lam <> 0 -> E0 <> 0 ->
    anchors_ok (lam * kappa) (lam * E0) (lam * Ea) <-> anchors_ok kappa E0 Ea.
Proof.
  intros kappa E0 Ea lam Hlam HE0.
  assert (Hz : lam * E0 <> 0).
  { intros Hprod. destruct (Rmult_integral lam E0 Hprod) as [Hl | He].
    - exact (Hlam Hl).
    - exact (HE0 He). }
  assert (Hf1 : (lam * kappa) / (lam * E0) = kappa / E0).
  { field. exact (conj HE0 Hlam). }
  assert (Hf2 : (lam * Ea) / (lam * E0) = Ea / E0).
  { field. exact (conj HE0 Hlam). }
  unfold anchors_ok.
  rewrite Hf1. rewrite Hf2. tauto.
Qed.

(* ======================================================================
   §16 载荷三分量的最小机器件（候选 c；2026-09-05）
   ======================================================================
   分量接口：指令（cmd_sent）、探测计数（probe_id，§2 计数侧）、
   雕像记忆（mem_bits——canonical 语义在 MemoryStatue_model.v，自包含
   惯例下此处仅重复最小门控）。指令环：探测发射 ⇔ 指令在场（回传语义）。 *)

Record Payload : Set := Payload_mk {
  cmd_sent : bool;
  probe_id : nat;
  mem_bits : list bool
}.

(* 〔注 2026-09-05 修订：Payload 曾为孤立定义；三分量合并机器化已落地为
   §30 round_payload（装配/恢复/门控缝合 statue_act），txt §7 行 #7 关闭。
   门控脊定理（fires/mem_payload 系）与 §30 的桥见 fires_iff_cmd_bit。〕 *)

Definition fires (delivered energy : nat -> bool) (n : nat) : bool :=
  andb (delivered n) (energy n).

(* 指令环：只有指令在场（且能量在位）才发射探测 *)
Lemma fires_requires_delivered :
  forall (delivered energy : nat -> bool) n,
    is_true (fires delivered energy n) -> is_true (delivered n).
Proof.
  intros delivered energy n H.
  unfold fires, is_true in *.
  exact (proj1 (proj1 (Bool.andb_true_iff (delivered n) (energy n)) H)).
Qed.

(* 记忆分量门控（最小重复；完整语义见 MemoryStatue_model.v M1） *)
Definition mem_payload (paired : bool) (m : list bool) : list bool :=
  if paired then m else nil.

Lemma mem_empty_when_unpaired : forall paired m,
  paired = false -> mem_payload paired m = nil.
Proof.
  intros paired m Hp. unfold mem_payload. rewrite Hp. reflexivity.
Qed.

(* ======================================================================
   §17 维持期：注入完成 vs 结构衰减（候选 e；2026-09-05）
   ======================================================================
   6b 待补的机器骨架：注入须在窗口结构衰减前完成；衰减在先 ⇒ 注入
   未完成。                                                             *)

Definition inject_completed (decay inject : nat) : Prop :=
  (inject < decay)%nat.

Theorem decay_first_blocks_injection :
  forall decay inject, (decay <= inject)%nat -> ~ inject_completed decay inject.
Proof.
  intros decay inject Hd Hc. unfold inject_completed in Hc. lia.
Qed.

(* ======================================================================
   §18 ε 载荷的常量数值化试算（2026-09-05）
   ======================================================================
   把 §9 的符号 ε = h·ν·L 落到一组〔假定〕常量上（h ≈ 6.626e-34 J·s，
   载波 1 GHz，载荷 1 Mbit，E_sn 能标 10^44 J），机器结算 ε/E_sn 的量级：
   ≈ 6.6×10^-63（远小于 1/10^60）。常量全为假定；数值为演示口径。
   边界声明（2026-09-05 复核修订）："ε/E_sn < 1/10^60"的严格不等式是
   机器定理 rel_ex_value_lt_inv_60（nra 直闭）。早前的"未机器化——无标准
   库桥引理"声明不实：pow_IZR 等桥在 Stdlib.Reals.RIneq 自 Coq 8.7 起存在，
   nra 直接处理 R 字面量幂（8.20/9.2 双验，假命题被拒非真空）；精确等式
   陈述为 rel_ex_value。                                                    *)

Definition h_planck : R := 6626 / 10 ^ 37.    (* ≈ 6.626e-34 J·s 〔假定〕 *)
Definition nu_chan : R := 10 ^ 9.             (* 载波 1 GHz 〔假定〕 *)
Definition bits_len : R := 10 ^ 6.            (* 载荷 1 Mbit 〔假定〕 *)
Definition E_sn_ref : R := 10 ^ 44.           (* 超新星能标 10^44 J 〔假定〕 *)
Definition eps_carrier_ex : R := h_planck * (nu_chan * bits_len).

Lemma pow10_37_split : 10 ^ 37 = 10 ^ 22 * 10 ^ 15.
Proof. rewrite <- (pow_add 10 22 15). reflexivity. Qed.

Lemma pow10_66_split : 10 ^ 66 = 10 ^ 6 * 10 ^ 60.
Proof. rewrite <- (pow_add 10 6 60). reflexivity. Qed.

Lemma eps_ex_value : eps_carrier_ex = 6626 / 10 ^ 22.
Proof.
  unfold eps_carrier_ex, h_planck, nu_chan, bits_len.
  rewrite <- (pow_add 10 9 6).
  change (10 ^ (9 + 6)) with (10 ^ 15).
  rewrite (pow10_37_split).
  field. all: apply pow_nonzero; nra.
Qed.

Lemma rel_ex_value : rel_pert eps_carrier_ex E_sn_ref = 6626 / 10 ^ 66.
Proof.
  unfold rel_pert, E_sn_ref.
  rewrite eps_ex_value.
  assert (Hmul : (6626 / 10 ^ 22) / 10 ^ 44 = 6626 / (10 ^ 22 * 10 ^ 44)).
  { field. all: apply pow_nonzero; nra. }
  rewrite Hmul.
  rewrite <- (pow_add 10 22 44).
  change (10 ^ (22 + 44)) with (10 ^ 66).
  reflexivity.
Qed.

(* 严格不等式（2026-09-05 复核补证）：ε/E_sn 远小于 1/10^60 —— nra 直闭，
   对应 §18 节首注释的边界声明与 txt §4.1 "可忽略（ε << E_sn）"的机器句 *)
Theorem rel_ex_value_lt_inv_60 : rel_pert eps_carrier_ex E_sn_ref < 1 / 10 ^ 60.
Proof.
  unfold rel_pert, E_sn_ref. rewrite eps_ex_value. nra.
Qed.

(* 数值口径：ε/E_sn = 6626/10^66 ≈ 6.6×10^-63（小数化人工估算，见上例等式） *)

(* ======================================================================
   §19 载荷三分量·完整脊（候选 c 完整版；2026-09-05）
   ======================================================================
   在 §16 最小件上补"脊"定理：指令-发射-存储-记忆门控的逐轮链。
   语义注解：探测数据分量 X_N 的统计侧在 §2/§5；雕像记忆的 canonical
   语义在 MemoryStatue_model.v（§5a C1/C2：配对轮起记忆非空）——自包含
   惯例下本文件只复现门控脊，不跨文件 import。                            *)

(* 能量在场时：指令在场 ⇔ 发射（fires 的完整方向） *)
Lemma fires_when_delivered_energy :
  forall (delivered energy : nat -> bool) n,
    is_true (delivered n) -> is_true (energy n) ->
    is_true (fires delivered energy n).
Proof.
  intros delivered energy n Hd He.
  unfold fires, is_true in *.
  apply (proj2 (Bool.andb_true_iff (delivered n) (energy n))).
  split; assumption.
Qed.

(* 存储：成功轮即存储轮（定义性：store 由 success 给出） *)
Definition stored (success : nat -> bool) (n : nat) : bool := success n.

Lemma stored_iff_success :
  forall (success : nat -> bool) n,
    is_true (stored success n) <-> is_true (success n).
Proof. intros. unfold stored, is_true. tauto. Qed.

(* 记忆门控持久性：配对一旦成立，之后各轮记忆分量均在场 *)
Lemma pair_persist_after :
  forall (paired : nat -> bool) a n,
    (forall k, is_true (paired k) -> is_true (paired (S k))) ->
    is_true (paired a) -> (a <= n)%nat -> is_true (paired n).
Proof.
  intros paired a n Hmono Hpa Han.
  induction n as [| n IH].
  - assert (Ha0 : a = 0%nat) by lia. subst a. exact Hpa.
  - destruct (Nat.eq_dec a (S n)).
    + rewrite <- e. exact Hpa.
    + assert (HaS : (a <= n)%nat) by lia.
      apply Hmono. exact (IH HaS).
Qed.

(* 脊注解：指令逐轮在场 ⇒ 发射逐轮发生（能量在场时）——
   对应正文"开炮指令经通道每轮注入"的逐轮维持（§6b 自举环的载荷侧） *)
Lemma ring_launch_chain :
  forall (delivered energy : nat -> bool),
    (forall n, is_true (delivered n)) ->
    (forall n, is_true (energy n)) ->
    forall n, is_true (fires delivered energy n).
Proof.
  intros delivered energy Hd He n.
  apply fires_when_delivered_energy; auto.
Qed.

(* ======================================================================
   §20 重置代数最小机器化（方向 A；2026-09-05）
   ======================================================================
   txt §2.5 的 Φ 框架最小件：重置算子 rop 幂等 ⇒ 循环起点状态已落在
   重置像上（"每轮从重置后的世界开始"的机器表述）。世界状态抽象为 R。
   〔算子序注记 2026-09-05：步算子取 rop∘iop（先注入、后重置）——本件
   只编码"世界轨"的重置代数：轮起点 = rop 像（const 重置 ⇒ 每轮同构，
   const_reset_constant_starts），注入被重置吸收。txt (2.4) 的 "+D_n"
   跨轮存活不在本轨——数据/计数走登记册轨（§24 reg_mono 单调累积），
   (2.4) 是两轨合成的记账式写法；勿按 (2.4) 字面把 iop 读作"叠加在轮
   起点上"。〕                                                *)

Fixpoint nfold {A : Type} (f : A -> A) (n : nat) (a : A) : A :=
  match n with O => a | S m => f (nfold f m a) end.

Definition loop_state (rop iop : R -> R) (s0 : R) (n : nat) : R :=
  nfold (fun s => rop (iop s)) n s0.

Theorem loop_step_def :
  forall rop iop s0 n,
    loop_state rop iop s0 (S n) = rop (iop (loop_state rop iop s0 n)).
Proof. intros. reflexivity. Qed.

(* 幂等重置 ⇒ 轮起点状态固定于重置像（n ≥ 1） *)
Theorem start_states_reset_fixed :
  forall (rop iop : R -> R) s0,
    (forall s, rop (rop s) = rop s) ->
    forall n, rop (loop_state rop iop s0 (S n)) = loop_state rop iop s0 (S n).
Proof.
  intros rop iop s0 H_idem n.
  induction n as [| n IH].
  - rewrite !loop_step_def. exact (H_idem (iop s0)).
  - rewrite loop_step_def.
    rewrite (H_idem (iop (loop_state rop iop s0 (S n)))).
    rewrite <- loop_step_def. reflexivity.
Qed.

(* 全重置特例：重置为常量 ⇒ 每个轮起点同构（"世界照常被重置"的最小形式） *)
Theorem const_reset_constant_starts :
  forall (rop iop : R -> R) s0 r0,
    (forall s, rop s = r0) ->
    forall n, loop_state rop iop s0 (S n) = r0.
Proof.
  intros rop iop s0 r0 Hconst n.
  induction n as [| n IH].
  - rewrite loop_step_def. exact (Hconst (iop s0)).
  - rewrite loop_step_def. exact (Hconst (iop (loop_state rop iop s0 (S n)))).
Qed.

(* ======================================================================
   §21 22 分钟的信息传播锥（方向 B；2026-09-05）
   ======================================================================
   知识沿"接触图"在轮内扩散：一步 = 与已知者相邻即获得信息。语义注：
   步长单位 = 轮内时间离散，取 1 步 = 1 s（与全局窗口秒制对齐——
   reach_within_loop 的 1320 步 = 22 分钟轮长）。
   〔修订 2026-09-05：旧注"1 步/分钟级、22 步 = 一轮上限"与机器件的
   k+1320 口径互斥（1 步/分钟 × 1320 步 = 22 小时 ≠ 22 分钟），已删。
   步长粒度本身是〔假定〕，两读数都兼容单调性定理；取秒制以对齐
   Load.window_s 与 δ_max 口径。〕与 Q7 传染场（QuantumRules_model.v）
   同语法：一个是区域量子化、一个是信息可达。                              *)

Definition reach_step (adj : nat -> list nat) (S : list nat) : list nat :=
  S ++ concat (map adj S).

Definition reach (adj : nat -> list nat) (src k : nat) : list nat :=
  nfold (reach_step adj) k (src :: nil).

Lemma reach_mem_next :
  forall (adj : nat -> list nat) S x y,
    In x S -> In y (adj x) -> In y (reach_step adj S).
Proof.
  intros adj S x y Hx Hy. unfold reach_step.
  apply in_or_app; right.
  apply in_concat.
  exists (adj x). split.
  - apply in_map_iff. exists x. split; [ reflexivity | exact Hx ].
  - exact Hy.
Qed.

Lemma reach_mono :
  forall (adj : nat -> list nat) S x,
    In x S -> In x (reach_step adj S).
Proof.
  intros adj S x Hx. unfold reach_step. apply in_or_app; left. exact Hx.
Qed.

Lemma reach_grows :
  forall (adj : nat -> list nat) src x k,
    In x (reach adj src k) -> In x (reach adj src (S k)).
Proof.
  intros adj src x k Hx. unfold reach.
  apply reach_mono. exact Hx.
Qed.

Lemma reach_mono_k :
  forall (adj : nat -> list nat) src x k d,
    In x (reach adj src k) -> In x (reach adj src (k + d)).
Proof.
  intros adj src x k d. revert k.
  induction d as [| d IH]; intros k Hx.
  - rewrite Nat.add_0_r. exact Hx.
  - rewrite Nat.add_succ_r.
    apply reach_grows. apply IH. exact Hx.
Qed.

(* 一轮内的传播上界：1320 秒轮长内至多累积 1320 步可达集
   （步长单位 ≤ 1 s 的离散口径；语义见节首注） *)
Corollary reach_within_loop :
  forall (adj : nat -> list nat) src x k,
    In x (reach adj src k) -> In x (reach adj src (k + 1320)).
Proof.
  intros. apply (reach_mono_k adj src x k 1320). exact H.
Qed.

(* ---- 锥的精确化（2026-09-05 追加）：可达集 = 距离球 ---- *)
(* 上一组引理只证"步数不减单调"，不含锥的边界语义（步数不够传不到）。
   这里引入路径概念把锥坐实：walk adj src x n = 自 src 沿接触图恰走
   n 步到 x（0 步含自身）。可达 ⇔ 存在 ≤k 步路径（reach_iff_walk），
   故 k 步可达集恰是距离 ≤ k 的节点集；1320 步 = 22 分钟轮长
   （1 s/步口径）即锥半径上界。                                    *)

Inductive walk (adj : nat -> list nat) : nat -> nat -> nat -> Prop :=
| walk_zero : forall v, walk adj v v 0
| walk_step : forall u v w n,
    walk adj u v n -> In w (adj v) -> walk adj u w (S n).

Lemma reach_contains_src :
  forall (adj : nat -> list nat) src k, In src (reach adj src k).
Proof.
  intros adj src k. induction k as [| k IH].
  - unfold reach. simpl. left. reflexivity.
  - apply reach_grows. exact IH.
Qed.

(* 路径 ⇒ 可达：恰 n 步的路径在任意步界 k ≥ n 的可达集内 *)
Lemma walk_implies_reach :
  forall (adj : nat -> list nat) src x n,
    walk adj src x n -> forall k, (n <= k)%nat -> In x (reach adj src k).
Proof.
  intros adj src x n Hw.
  induction Hw as [v | u v w n Hw IH Hadj]; intros k Hk.
  - apply reach_contains_src.
  - destruct k as [| k'].
    + lia.
    + unfold reach.
      apply (reach_mem_next adj (reach adj u k') v w).
      * assert (Hn : (n <= k')%nat) by lia. exact (IH k' Hn).
      * exact Hadj.
Qed.

(* 可达 ⇒ 存在 ≤ k 步路径 *)
Lemma reach_implies_walk_le :
  forall (adj : nat -> list nat) src x k,
    In x (reach adj src k) ->
    exists n, (n <= k)%nat /\ walk adj src x n.
Proof.
  intros adj src x k. revert x.
  induction k as [| k IH]; intros x Hx.
  - unfold reach in Hx. simpl in Hx.
    destruct Hx as [Hx | Hrest].
    + subst x. exists 0%nat. split; [ lia | apply walk_zero ].
    + inversion Hrest.
  - assert (Hstep : In x (reach_step adj (reach adj src k))).
    { unfold reach in Hx. simpl in Hx. exact Hx. }
    unfold reach_step in Hstep.
    destruct (in_app_or (reach adj src k)
                        (concat (map adj (reach adj src k))) x Hstep) as [Hl | Hr].
    + destruct (IH x Hl) as [n [Hn Hw]].
      exists n. split; [ lia | exact Hw ].
    + destruct (proj1 (in_concat (map adj (reach adj src k)) x) Hr)
        as [v [Hvm Hxv]].
      destruct (proj1 (in_map_iff adj (reach adj src k) v) Hvm)
        as [y [Hy Hvy]].
      rewrite <- Hy in Hxv.
      destruct (IH y Hvy) as [n [Hn Hw]].
      exists (S n). split; [ lia | ].
      apply (walk_step adj src y x n Hw Hxv).
Qed.

(* 等价：k 步可达集 = 距离 ≤ k 的节点集（锥的半径上界语义） *)
Theorem reach_iff_walk :
  forall (adj : nat -> list nat) src x k,
    In x (reach adj src k) <->
    exists n, (n <= k)%nat /\ walk adj src x n.
Proof.
  intros adj src x k. split.
  - apply reach_implies_walk_le.
  - intros [n [Hn Hw]]. apply (walk_implies_reach adj src x n Hw k Hn).
Qed.

(* 负侧（锥的不可达声明）：k 步内无路径 ⇒ 不在 k 步可达集——
   "步数不够就传不到"：距源需 > k 步的节点在 k 步传播锥外 *)
Corollary no_walk_no_reach :
  forall (adj : nat -> list nat) src x k,
    (forall n, (n <= k)%nat -> ~ walk adj src x n) ->
    ~ In x (reach adj src k).
Proof.
  intros adj src x k Hall Hx.
  destruct (reach_implies_walk_le adj src x k Hx) as [n [Hn Hw]].
  exact (Hall n Hn Hw).
Qed.

(* ======================================================================
   §22 双副本共存的显式化（方向 C；2026-09-05）
   ======================================================================
   A4'（ParadoxSelf，μ=2）从注释升级为定理链：两个副本事件共享同一
   原因（跳入）且原因在场 ⇒ 一致；删除原因 ⇒ 副本构型不一致。          *)

Definition copies_cause (e : nat) : option nat :=
  match e with
  | 0 => Some 2%nat
  | 1 => Some 2%nat
  | _ => None
  end.

Definition copies_all_present (e : nat) : bool := true.

Theorem copies_coexist_consistent :
  consistent_events copies_cause copies_all_present (0%nat :: 1%nat :: 2%nat :: nil).
Proof.
  intros e He Hp.
  unfold cause_ok.
  destruct (copies_cause e) as [c |] eqn:Hc.
  - unfold is_true. reflexivity.
  - exact I.
Qed.

Definition copies_no_cause (e : nat) : bool :=
  match e with 2 => false | _ => true end.

Theorem copies_removal_inconsistent :
  ~ consistent_events copies_cause copies_no_cause (0%nat :: 1%nat :: 2%nat :: nil).
Proof.
  intros H.
  assert (Hbad : cause_ok copies_cause copies_no_cause 0%nat).
  { apply (H 0%nat).
    - left. reflexivity.
    - unfold is_true. reflexivity. }
  unfold cause_ok in Hbad. simpl in Hbad.
  unfold is_true in Hbad. discriminate.
Qed.

(* ======================================================================
   §23 循环总能量预算的示例口径（方向 D；2026-09-05）
   ======================================================================
   每轮开窗吃一整颗超新星能标 E_sn_ref = 10^44 J（锚点 c 口径）；N 轮
   总预算 = N · E_sn_ref。小 N 机器示例；9.3M 轮的 J 口径量级带已机器化
   （budget_nstar_J_band，2026-09-05 追加）。                              *)

Definition total_budget (N : nat) : R := INR N * E_sn_ref.

Example budget_two_loops : total_budget 2 = 2 * 10 ^ 44.
Proof.
  unfold total_budget, E_sn_ref. simpl. ring.
Qed.

(* 预算单调：轮数越多总预算不降（E_sn_ref > 0） *)
Theorem budget_monotone :
  forall N, total_budget N <= total_budget (S N).
Proof.
  intros N. unfold total_budget.
  rewrite S_INR.
  assert (HE : 0 <= E_sn_ref).
  { unfold E_sn_ref. apply Rlt_le. apply pow_lt. nra. }
  rewrite (Rmult_comm (INR N + 1) E_sn_ref).
  rewrite (Rmult_comm (INR N) E_sn_ref).
  apply Rmult_le_compat_l.
  - exact HE.
  - nra.
Qed.

(* J 口径量级带（2026-09-05）：n_star 轮总预算 = 9,318,054 × 10^44 J。
   〔口径注：与 total_budget n_star = INR n_star · E_sn_ref 同值——R 层以
   字面量 9318054 陈述，INR 大 nat 无 nra 桥（实测陷阱，见 §18/§5 注），
   跨层等式未单列；n_star = 9318054 见 §1。〕量级：介于 10^50 与 10^51 J，
   即"约 9.3×10^50 J"的机器句。 *)
Theorem budget_nstar_J_band : 9 * 10 ^ 50 < 9318054 * 10 ^ 44 < 10 ^ 51.
Proof. nra. Qed.

(* ======================================================================
   §24 语义桥：停机、登记册与两轨合并（2026-09-05）
   ======================================================================
   把叙述层三句关键语义补成机器件：
   ① 停机（拔核/无指令）⇒ 不再发射（fires 的逆方向）；
   ② 探测计数以"登记册"形式跨轮累积（reg_n 单调）——txt §2.5/5.2 的
      "计数器照常累积"；
   ③ 两轨合并：世界侧全重置（§20 const_reset_constant_starts）与登记册
      侧单调（②）并存 ⇒ "世界每轮相同、计数照常涨"的最小机器表述。     *)

(* ① 停机语义：指令缺席 ⇒ 发射不可能 *)
Lemma fires_blocked_without_command :
  forall (delivered energy : nat -> bool) n,
    ~ is_true (delivered n) -> ~ is_true (fires delivered energy n).
Proof.
  intros delivered energy n Hd Hf.
  apply Hd. exact (fires_requires_delivered delivered energy n Hf).
Qed.

(* ② 登记册：探测/数据记录跨轮单调累积（不受世界重置影响） *)
Fixpoint reg_n (d : nat -> list nat) (n : nat) : list nat :=
  match n with
  | O   => d 0%nat
  | S m => reg_n d m ++ d (S m)
  end.

Lemma reg_mono :
  forall (d : nat -> list nat) n x,
    In x (reg_n d n) -> In x (reg_n d (S n)).
Proof.
  intros d n x Hx. simpl. apply in_or_app; left. exact Hx.
Qed.

Lemma reg_mono_k :
  forall (d : nat -> list nat) n k x,
    In x (reg_n d n) -> In x (reg_n d (n + k)).
Proof.
  intros d n k. revert n.
  induction k as [| k IH]; intros n x Hx.
  - rewrite Nat.add_0_r. exact Hx.
  - rewrite Nat.add_succ_r.
    apply reg_mono. exact (IH n x Hx).
Qed.

(* ③ 两轨合并（机器表述）：世界起点每轮同构（全重置）∧ 登记册单调
   ——"世界重演、数据照旧"的载体分离语义（叙述锚：探测计数器 9,318,054
   在无记忆时代照常累积；记忆侧 canonical 在 MemoryStatue_model.v） *)
Theorem two_track_stability :
  forall (rop iop : R -> R) s0 r0 (d : nat -> list nat),
    (forall s, rop s = r0) ->
    (forall n, loop_state rop iop s0 (S n) = r0) /\
    (forall n x, In x (reg_n d n) -> In x (reg_n d (S n))).
Proof.
  intros rop iop s0 r0 d Hconst.
  split.
  - intros n. apply (const_reset_constant_starts rop iop s0 r0 Hconst n).
  - intros n x Hx. apply reg_mono. exact Hx.
Qed.

(* ======================================================================
   §25 语义闭包：事件层 ↔ Load 层的通用桥 + designed 语义拆类
   （2026-09-05，语义定义面加固）
   ======================================================================
   ① 通用桥：paradox_of = 事件构型中"在场效应缺在场原因"的可判定存在；
      事件层不一致 ⇒ paradox_of 真（generic，取代 HEL 手工单点）；
      一致 ⇒ paradox_of 假（构造性方向）。
   ② designed 拆类：data_only（mass=0）才是 A3"只送信息"的编码；
      designed = data_only ∨ 短窗物质（工程许可集合），二者显式分离。     *)

(* ① 悖论标志的构型定义（可判定：existsb） *)
Definition paradox_of (cause : nat -> option nat)
  (present : nat -> bool) (dom : list nat) : bool :=
  existsb (fun e =>
    match cause e with
    | Some c => (present e && negb (present c))%bool
    | None   => false
    end) dom.

(* 事件层 → 语义层：paradox_of 真 ⇒ 事件层不一致（generic 桥） *)
Theorem event_paradox_implies_inconsistent :
  forall (cause : nat -> option nat) (present : nat -> bool) dom,
    is_true (paradox_of cause present dom) ->
    ~ consistent_events cause present dom.
Proof.
  intros cause present dom Hp.
  unfold paradox_of, is_true in Hp.
  apply (proj1 (existsb_exists
    (fun e => match cause e with
              | Some c => (present e && negb (present c))%bool
              | None => false end) dom)) in Hp.
  destruct Hp as [e [He Hf]].
  destruct (cause e) as [c |] eqn:Hc.
  - apply (Bool.andb_true_iff (present e) (negb (present c))) in Hf.
    destruct Hf as [Hpe Hnpc].
    apply (proj1 (Bool.negb_true_iff (present c))) in Hnpc.
    assert (Hpc : ~ is_true (present c)).
    { intros Ht. unfold is_true in Ht. rewrite Hnpc in Ht. discriminate. }
    apply (inconsistent_effect_without_cause cause present dom e c Hc He Hpe Hpc).
  - discriminate.
Qed.

(* 语义层 → 事件层（构造性方向）：一致 ⇒ paradox_of 假 *)
Theorem event_consistent_no_paradox :
  forall (cause : nat -> option nat) (present : nat -> bool) dom,
    consistent_events cause present dom ->
    ~ is_true (paradox_of cause present dom).
Proof.
  intros cause present dom Hc Hp.
  exact (event_paradox_implies_inconsistent cause present dom Hp Hc).
Qed.

(* 由事件构型生成的 Load（paradox 位即 paradox_of）。
   〔注 2026-09-05：mass/window 为硬编码占位（0 / 1320），只传 paradox 位
   过桥——勿用 event_load 推 designed 或质量语义；事件层无质量概念。〕 *)
Definition event_load (cause : nat -> option nat)
  (present : nat -> bool) (dom : list nat) : Load :=
  Load_mk nil 0%nat 1320%nat (paradox_of cause present dom).

(* 桥（构造性方向）：事件层一致 ⇒ 对应 Load 判"不撕裂" *)
Theorem semantics_bridge_consistent :
  forall (cause : nat -> option nat) (present : nat -> bool) dom,
    consistent_events cause present dom ->
    consistent (event_load cause present dom).
Proof.
  intros cause present dom Hc.
  unfold consistent, event_load.
  exact (event_consistent_no_paradox cause present dom Hc).
Qed.

(* 反向（¬paradox_of ⇒ 事件层一致）是构造性可证的：consistent_events 的
   检查就是逐事件 cause_ok，有限域上不存在性可判定——等价定理见 §27。 *)

(* ② designed 语义拆类：data_only = A3"只送信息"的编码 *)
Definition data_only (l : Load) : Prop := mass l = 0%nat.

Theorem data_only_designed : forall l, data_only l -> designed l.
Proof. intros l Hd. unfold designed. left. exact Hd. Qed.

(* designed 是"工程许可集合"：短窗物质也在内（≠ A3） *)
Example short_window_matter_designed :
  exists l, designed l /\ ~ data_only l.
Proof.
  exists (Load_mk nil 1%nat 1%nat false).
  split.
  - unfold designed. right. reflexivity.
  - intros Hd. unfold data_only in Hd. discriminate.
Qed.

Theorem designed_split :
  forall l, designed l <->
    data_only l \/ (0 < mass l)%nat /\ (window_s l <= 1)%nat.
Proof.
  intros l. unfold designed, data_only.
  split.
  - intros [Hm | Hw].
    + left. exact Hm.
    + destruct (Nat.eq_dec (mass l) 0) as [Hz | Hnz].
      * left. exact Hz.
      * right. split; [ lia | exact Hw ].
  - intros [Hm | [Hpos Hw]].
    + left. exact Hm.
    + right. exact Hw.
Qed.

(* ======================================================================
   §26 语义加固补充（2026-09-05）
   ======================================================================
   ① bits 无关性：designed/consistent 从不读载荷内容——"通道判定与
      数据内容无关"（A3 侧）立成定理；
   ② 两层共存绑定：Load 层共存容忍（A4'）与事件层副本共存（§22）
      绑成一条引用链；
   ③ 停机→记忆侧停增的语义注（canonical 在 MemoryStatue_model.v）。
   ========================================================================== *)

(* ① 判定与内容无关：换 bits 不改变 designed/consistent *)
Theorem designed_bits_irrelevant :
  forall b b' m w p,
    designed (Load_mk b m w p) <-> designed (Load_mk b' m w p).
Proof. intros. unfold designed. tauto. Qed.

Theorem consistent_bits_irrelevant :
  forall b b' m w p,
    consistent (Load_mk b m w p) <-> consistent (Load_mk b' m w p).
Proof. intros. unfold consistent, is_true. tauto. Qed.

(* ② 两层共存绑定：玩家走通 1320 s（Load 层一致）∧ 副本共享跳入原因
    （事件层一致）——"物质共存被容忍"在两层同一条引用 *)
Theorem player_coexist_two_layer :
  forall m, (0 < m)%nat ->
    consistent (Player m) /\
    consistent_events copies_cause copies_all_present (0%nat :: 1%nat :: 2%nat :: nil).
Proof.
  intros m Hm. split.
  - exact (proj2 (proj2 (matter_through_atp_nonparadox_consistent m Hm))).
  - exact copies_coexist_consistent.
Qed.

(* ③ 语义注：停机（拔核/无指令）后不再有新的雕像记忆注入——
   注入源在雕像文件侧（MemoryStatue_model.v：K 只在 cut 结算时增长），
   本文件侧对应 fires_blocked_without_command（§24①）。跨文件不 import，
   联动由本注与雕像文件头互指维持。 *)

(* ======================================================================
   §27 语义闭包 II：等价桥、删除链与三层一致（2026-09-05）
   ======================================================================
   全部基于既有定义与已锚事实，零新裁决：
   ① consistent_events ↔ ¬paradox_of（构造性双向；修正 §25 的旧注释）；
   ② 删除链：把"删除原因"实现为布尔翻转（after_delete），效果先在场、
      原因后缺席 ⇒ 不一致（HEL 叙事 = 游戏实测，非新裁决）；
   ③ 三层一致：Load 共存容忍（A4'）∧ 事件层副本共存 ∧ 逐轮维持
      （jump_forever 泛化）绑成单链。                                     *)

(* ① 等价桥（构造性双向） *)
Theorem paradox_of_iff_not_consistent :
  forall (cause : nat -> option nat) (present : nat -> bool) dom,
    consistent_events cause present dom <->
    ~ is_true (paradox_of cause present dom).
Proof.
  intros cause present dom.
  split.
  - apply event_consistent_no_paradox.
  - intros Hnp.
    unfold consistent_events.
    intros e He Hpe.
    unfold cause_ok.
    destruct (cause e) as [c |] eqn:Hc.
    + destruct (present c) eqn:Hcpc.
      * reflexivity.
      * exfalso. apply Hnp.
        unfold paradox_of, is_true.
        apply (proj2 (existsb_exists
          (fun x => match cause x with
                    | Some d => (present x && negb (present d))%bool
                    | None => false end) dom)).
        exists e. split; [ exact He | ].
        rewrite Hc.
        rewrite Hpe. rewrite Hcpc.
        simpl. reflexivity.
    + exact I.
Qed.

(* ② 删除实现为布尔翻转：原因缺席、效应仍在场 ⇒ 不一致 *)
Definition delete_cause (present : nat -> bool) (c : nat) (x : nat) : bool :=
  if Nat.eqb x c then false else present x.

Theorem delete_cause_inconsistent :
  forall (cause : nat -> option nat) (present : nat -> bool)
         (dom : list nat) e c,
    cause e = Some c -> In e dom -> is_true (present e) -> (e <> c)%nat ->
    ~ consistent_events cause (delete_cause present c) dom.
Proof.
  intros cause present dom e c Hc Hin Hpe Hne Hcons.
  assert (Hpe' : is_true (delete_cause present c e)).
  { unfold delete_cause, is_true.
    destruct (Nat.eqb e c) eqn:Heq.
    - apply Nat.eqb_eq in Heq. exfalso. exact (Hne Heq).
    - exact Hpe. }
  assert (Hpc' : ~ is_true (delete_cause present c c)).
  { unfold delete_cause, is_true.
    rewrite (proj2 (Nat.eqb_eq c c) eq_refl). intro H. discriminate. }
  apply (inconsistent_effect_without_cause cause (delete_cause present c)
           dom e c Hc Hin Hpe' Hpc').
  exact Hcons.
Qed.

(* 链注：§14 的 gap_sufficient_interval 给出删除动作可插入的时间段；
   本定理给出该动作一旦执行（翻转 present）即不一致——两段合起来即
   "间隙可达 ⇒ 可构造删除 ⇒ 撕裂"（HEL 实测叙事的机器链）。 *)

(* ③ 三层一致单链（jump_forever 泛化到任意轮数） *)
Theorem jump_forever_consistent :
  forall N, bootstrap_consistent jump_forever N.
Proof.
  intros N r Hr Hc. unfold jump_forever, is_true. reflexivity.
Qed.

Theorem three_layer_coexistence :
  forall m N, (0 < m)%nat ->
    consistent (Player m) /\
    consistent_events copies_cause copies_all_present (0%nat :: 1%nat :: 2%nat :: nil) /\
    bootstrap_consistent jump_forever N.
Proof.
  intros m N Hm.
  split.
  - exact (proj2 (proj2 (matter_through_atp_nonparadox_consistent m Hm))).
  - split.
    + exact copies_coexist_consistent.
    + exact (jump_forever_consistent N).
Qed.

(* ======================================================================
   §28 加固续：任意阈值界 + 触发链最小件（2026-09-05）
   ======================================================================
   ① rel_pert_bound 从 1/1000 特例泛化到任意正阈值 X（§9 变引理）；
   ② 触发链：成功只发生在 n_star（既有常数）⇒ n_star 之前无激活——
      "前 9,318,053 轮雕像休眠"的机器前缀（记忆侧 canonical 在雕像文件）。*)

(* ① 任意阈值版相对扰动界 *)
Theorem rel_pert_bound_gen :
  forall eps E_sn X : R,
    0 <= eps -> 0 < E_sn -> 0 < X ->
    eps <= E_sn / X ->
    rel_pert eps E_sn <= 1 / X.
Proof.
  intros eps E_sn X Heps HE HX Hle. unfold rel_pert.
  assert (Hz : E_sn <> 0) by lra.
  assert (Heq : (E_sn / X) * / E_sn = 1 / X).
  { unfold Rdiv. field. split; [ lra | exact Hz ]. }
  assert (Hm : eps * / E_sn <= (E_sn / X) * / E_sn).
  { unfold Rdiv. apply Rmult_le_compat_r.
    - apply Rlt_le. apply Rinv_0_lt_compat. exact HE.
    - exact Hle. }
  rewrite Heq in Hm. exact Hm.
Qed.

(* ② 触发链最小件：雕像激活自成功轮的 0 基轮起（口径对齐雕像文件：
   MemoryStatue_model.v 的配对轮 c_a = n_star − 1；切勿再用 1 基轮号
   当 0 基轮用——§28 初版曾在此 off-by-one） *)
Definition n_star_0 : nat := n_star - 1.

Example n_star_0_val : n_star_0 = 9318053%nat.
Proof. unfold n_star_0. reflexivity. Qed.

Definition statue_act (n : nat) : bool := Nat.leb n_star_0 n.

Lemma no_activation_before_star :
  forall n, (n < n_star_0)%nat -> statue_act n = false.
Proof.
  intros n Hlt. unfold statue_act.
  destruct (Nat.leb n_star_0 n) eqn:Hleb.
  - apply (proj1 (Nat.leb_le n_star_0 n)) in Hleb. lia.
  - reflexivity.
Qed.

(* 激活首轮 = n_star_0（0 基）；雕像侧 C1：首次记忆在 S c_a 轮起点 *)
Lemma activation_starts_at_star0 : statue_act n_star_0 = true.
Proof.
  unfold statue_act. rewrite (proj2 (Nat.leb_le n_star_0 n_star_0) (le_n _)).
  reflexivity.
Qed.

(* ======================================================================
   §29 能量门槛入 Load 语义 + 口径修复记录（2026-09-05）
   ======================================================================
   ① window_feasible：把 §3 的 E_req 门槛与 §4 Load 的窗口同框——
      "注入能量足够开此窗"成为 Load 上的谓词；单调性两定理。
   ② 口径修复：§28 初版 statue_act 用 1 基轮号（eqb n n_star），与雕像
      文件 0 基 c_a = n_star − 1 差一轮——已按 0 基重写并留 Example。     *)

(* ① 开窗可行：注入能量 ≥ E_req(窗长) *)
Definition window_feasible (E E_b tau2 : R) (l : Load) : Prop :=
  E_req E_b tau2 (INR (window_s l)) <= E.

(* 能量单调：注入越多越可行（窗不变） *)
Theorem window_feasible_mono_E :
  forall E1 E2 E_b tau2 l,
    E1 <= E2 ->
    window_feasible E1 E_b tau2 l ->
    window_feasible E2 E_b tau2 l.
Proof.
  intros E1 E2 E_b tau2 l Hle Hf.
  unfold window_feasible in *. lra.
Qed.

(* 窗长单调（高能段指数律，τ₂ > 0）：窗越长所需能量越多 *)
Theorem window_feasible_antitone_w :
  forall E E_b tau2 l1 l2,
    0 <= E_b ->
    0 < tau2 ->
    (window_s l1 <= window_s l2)%nat ->
    window_feasible E E_b tau2 l2 ->
    window_feasible E E_b tau2 l1.
Proof.
  intros E E_b tau2 l1 l2 Hb Ht Hw Hf.
  unfold window_feasible in *.
  destruct (Nat.eq_dec (window_s l1) (window_s l2)) as [Heq | Hneq].
  - rewrite <- Heq in Hf. exact Hf.
  - assert (Hltn : (window_s l1 < window_s l2)%nat) by lia.
    assert (Hmono :
      E_req E_b tau2 (INR (window_s l1)) <= E_req E_b tau2 (INR (window_s l2))).
    { unfold E_req. apply Rmult_le_compat_l.
      - exact Hb.
      - apply Rlt_le. apply exp_increasing.
        assert (Hin : INR (window_s l1) < INR (window_s l2))
          by (apply lt_INR; exact Hltn).
        assert (Hd :
          (INR (window_s l1) - 1) / tau2 < (INR (window_s l2) - 1) / tau2).
        { unfold Rdiv. apply Rmult_lt_compat_r.
          - apply Rinv_0_lt_compat. exact Ht.
          - lra. }
        exact Hd. }
    exact (Rle_trans (E_req E_b tau2 (INR (window_s l1)))
                     (E_req E_b tau2 (INR (window_s l2))) E Hmono Hf).
Qed.

(* 锚点重言注：E = E_sn 时 1320 s 窗可行（E_req(1320) = E_sn，
   tau2_fits_supernova）；窗口级可行性与撕裂判定（consistent）互不依赖，
   同 designed 的"工程面/判定面"分离。裁决"δ_max = 1320 由恒星能量预算
   决定"（txt §6(2)）的机器推论即 supernova_energy_blocks_longer_window：
   能量所及即窗口上限——超过 1320 s 的窗，E_sn 严格不足。模型不设
   1319/1320/1321 的端点特殊性：窗口端点不进入 consistent/designed。 *)

(* ② 能量预算上限（2026-09-05，裁决机器化）：窗长超过锚点 c 的 1320 s 时
   E_sn 严格不足。E_req 在 τ₂ > 0 下随窗长严格增（exp_increasing），
   由锚点等式 E_req(1320) = E_sn 直接得出——"都计算好了"的定理形态。 *)
Theorem supernova_energy_blocks_longer_window :
  forall (E_b E_sn tau2 w : R),
    0 < E_b -> 0 < tau2 ->
    E_req E_b tau2 1320 = E_sn ->
    1320 < w ->
    E_sn < E_req E_b tau2 w.
Proof.
  intros E_b E_sn tau2 w Hb Ht Hfit Hw.
  rewrite <- Hfit.
  unfold E_req.
  apply Rmult_lt_compat_l; [ exact Hb | ].
  apply exp_increasing.
  unfold Rdiv. apply Rmult_lt_compat_r.
  - apply Rinv_0_lt_compat. exact Ht.
  - lra.
Qed.

(* 1320 的 INR/R 字面量同值桥（窗口秒嵌入用；R 字面量经 IZR 记号） *)
Example INR_1320_R : INR 1320 = 1320.
Proof. apply (INR_IZR_INZ 1320). Qed.

(* Load 侧：nat 秒窗长 > 1320 的载荷，E_sn 不足 ⇒ 开窗不可行
   （window_feasible = false 侧的模型后果；撕裂判定不联动，见节首注） *)
Corollary supernova_budget_caps_window :
  forall (l : Load) (E_b E_sn tau2 : R),
    0 < E_b -> 0 < tau2 ->
    E_req E_b tau2 1320 = E_sn ->
    (1320 < window_s l)%nat ->
    ~ window_feasible E_sn E_b tau2 l.
Proof.
  intros l E_b E_sn tau2 Hb Ht Hfit Hw Hf.
  unfold window_feasible in Hf.
  assert (Hw' : 1320 < INR (window_s l)).
  { assert (Hin : INR 1320 < INR (window_s l)) by (apply lt_INR; exact Hw).
    rewrite <- INR_1320_R. exact Hin. }  (* 桥：R 字面量 1320 ↔ INR 1320（非转换等价，须 rewrite） *)
  assert (Hlt : E_sn < E_req E_b tau2 (INR (window_s l))).
  { apply (supernova_energy_blocks_longer_window E_b E_sn tau2 (INR (window_s l))).
    - exact Hb.
    - exact Ht.
    - exact Hfit.
    - exact Hw'. }
  lra.
Qed.

(* ======================================================================
   §30 载荷三分量合并（txt §7 行 #7 关闭；2026-09-05）
   ======================================================================
   (2.3) 的 D = { 开炮指令 O、探测测量值 X_N、雕像记忆 Mem_j } 的
   机器形态（注释内避词法记 X_N，即 txt (2.3) 的探测测量分量）：每轮载荷 =
   Payload 元组（三分量经轮函数装配）。canon 未给编码协议（txt §6(1)
   开放），故不发明位串编码——"合并完整" = 载荷即元组且三分量可恢复
   （字段投影定理），记忆分量受配对门控并缝合 §28 的 statue_act（配对
   轮 = 成功轮 = n_star_0，0 基）。
   语义锚点：
     · 指令分量 cmd_sent：fires 的指令侧（与 §19 脊互指；载荷级桥见
       fires_iff_cmd_bit）；
     · 探测分量 probe_id：每轮一发探测（§2/§5 统计侧）；成功轮 n_star_0
       的载荷计数 = n_star（success_round_attempt_no）；
     · 记忆分量 mem_bits：未配对 ⇒ nil（pre_activation_round_mem_empty =
       "前 9,318,053 轮 D 不含雕像记忆"的载荷句）；配对后 ⇒ 当轮录制
       （activated_round_mem_unfold；内容层跨轮累积 canonical 语义在
       MemoryStatue_model.v §4 mn_recall/M1，本文件不 import）。       *)

(* 轮载荷装配：三分量 → (2.3) 的单一载荷 D（记忆分量先经配对门控） *)
Definition round_payload
  (cmd   : nat -> bool)      (* 指令分量：本轮 O 是否在场 *)
  (probe : nat -> nat)       (* 探测分量：本轮计数/测量标识 *)
  (memo  : nat -> list bool) (* 雕像记忆原始内容（配对轮起由雕像侧录制） *)
  (paired : nat -> bool)     (* 配对门控（ATP 侧实例 = statue_act） *)
  (n : nat) : Payload :=
  Payload_mk (cmd n) (probe n) (mem_payload (paired n) (memo n)).

(* 恢复：装配的逆（三分量各字段 = 输入轮函数值；"无信息丢失"的分解侧） *)
Lemma payload_cmd_recover :
  forall (cmd : nat -> bool) probe memo paired n,
    cmd_sent (round_payload cmd probe memo paired n) = cmd n.
Proof. intros. reflexivity. Qed.

Lemma payload_probe_recover :
  forall (cmd : nat -> bool) probe memo paired n,
    probe_id (round_payload cmd probe memo paired n) = probe n.
Proof. intros. reflexivity. Qed.

Lemma payload_mem_recover :
  forall (cmd : nat -> bool) probe memo paired n,
    mem_bits (round_payload cmd probe memo paired n) =
    mem_payload (paired n) (memo n).
Proof. intros. reflexivity. Qed.

(* 载荷级桥：发射 ⟺ 载荷的指令分量在场 ∧ 能量在场（连通 §19 脊与 §30 装配） *)
Lemma fires_iff_cmd_bit :
  forall (cmd : nat -> bool) (energy : nat -> bool)
         (probe : nat -> nat) (memo : nat -> list bool) (paired : nat -> bool) n,
    is_true (fires cmd energy n) <->
    is_true (cmd_sent (round_payload cmd probe memo paired n)) /\ is_true (energy n).
Proof.
  intros cmd energy probe memo paired n.
  unfold fires, is_true.
  rewrite (payload_cmd_recover cmd probe memo paired n).
  exact (Bool.andb_true_iff (cmd n) (energy n)).
Qed.

(* §28 补全：statue_act 激活后持续（"配对一旦成立逐轮保持"，雕像文件
   pair_persist 规则的最小实例；§28 只证了首轮与之前） *)
Lemma statue_act_persists :
  forall n, (n_star_0 <= n)%nat -> statue_act n = true.
Proof.
  intros n Hle. unfold statue_act.
  apply (proj2 (Nat.leb_le n_star_0 n)). exact Hle.
Qed.

(* 记忆门控缝合 statue_act：激活轮之前 D 的记忆分量恒为 nil——
   "前 9,318,053 轮 D 不含雕像记忆、无人记得"（txt §2.5 修正段）的载荷句 *)
Theorem pre_activation_round_mem_empty :
  forall (cmd : nat -> bool) (probe : nat -> nat) (memo : nat -> list bool) n,
    (n < n_star_0)%nat ->
    mem_bits (round_payload cmd probe memo statue_act n) = nil.
Proof.
  intros cmd probe memo n Hn.
  unfold round_payload.
  rewrite (no_activation_before_star n Hn).
  reflexivity.
Qed.

(* 激活轮起：D 的记忆分量 = 当轮录制内容（门控开；内容层在雕像文件） *)
Theorem activated_round_mem_unfold :
  forall (cmd : nat -> bool) (probe : nat -> nat) (memo : nat -> list bool) n,
    (n_star_0 <= n)%nat ->
    mem_bits (round_payload cmd probe memo statue_act n) = memo n.
Proof.
  intros cmd probe memo n Hn.
  unfold round_payload.
  rewrite (statue_act_persists n Hn).
  reflexivity.
Qed.

(* 探测分量与成功轮对齐：每轮一发探测，0 基轮 n 是第 S n 次尝试；
   成功轮（雕像激活轮）n_star_0 上计数 = n_star = 9,318,054（§2/§5 口径） *)
Definition attempt_no (n : nat) : nat := S n.

Example success_round_attempt_no : attempt_no n_star_0 = n_star.
Proof. unfold attempt_no, n_star_0. reflexivity. Qed.

(* ---- 探测停止实例（2026-09-05 追加）---- *)
(* 指令分量截止于成功轮（0 基 n_star_0），与 statue_act（激活自 n_star_0
   起）对称：同一轮既是雕像激活轮又是最后一次探测轮——成功探测 → 停发、
   去眼（canon：探测序列止于 N*）。 *)
Definition cmd_until_success (n : nat) : bool := Nat.leb n n_star_0.

Lemma cmd_stops_after_success :
  forall n, (n_star_0 < n)%nat -> cmd_until_success n = false.
Proof.
  intros n Hgt. unfold cmd_until_success.
  destruct (Nat.leb n n_star_0) eqn:Hleb.
  - apply (proj1 (Nat.leb_le n n_star_0)) in Hleb. lia.
  - reflexivity.
Qed.

Lemma cmd_holds_through_success :
  forall n, (n <= n_star_0)%nat -> cmd_until_success n = true.
Proof.
  intros n Hle. unfold cmd_until_success.
  apply (proj2 (Nat.leb_le n n_star_0)). exact Hle.
Qed.

(* 成功轮之后无发射：指令分量缺席 ⇒ fires 停（fires_requires_delivered
   的截止实例；§24① 的停机通用版在此落数值） *)
Theorem no_probe_after_success :
  forall (energy : nat -> bool) n,
    (n_star_0 < n)%nat ->
    ~ is_true (fires cmd_until_success energy n).
Proof.
  intros energy n Hgt Hf.
  apply (fires_requires_delivered cmd_until_success energy n) in Hf.
  unfold is_true in Hf.
  rewrite (cmd_stops_after_success n Hgt) in Hf.
  discriminate.
Qed.

(* 载荷侧版本：截止后 round_payload 的指令位 = false（经恢复引理） *)
Lemma payload_cmd_off_after_success :
  forall (probe : nat -> nat) (memo : nat -> list bool)
         (paired : nat -> bool) n,
    (n_star_0 < n)%nat ->
    cmd_sent (round_payload cmd_until_success probe memo paired n) = false.
Proof.
  intros probe memo paired n Hgt.
  rewrite (payload_cmd_recover cmd_until_success probe memo paired n).
  exact (cmd_stops_after_success n Hgt).
Qed.

(* ======================================================================
   备注
   ======================================================================
   1. §1–§30 全部为可执行验证的定理（§9–§30 为 2026-09-05 后续清单
      边界扩展；§28/§29 含 0 基轮对齐修复与 δ_max 能量预算上限推论；
      §30 收尾 txt §7 行 #7 载荷三分量合并），文件中没有任何 Axiom /
      Admitted。
   2. 实数与 ln/exp 来自标准库，其构造本身带 Dedekind 完备性/经典逻辑
      公理，属库的固有前提，非本文档新增。
   3. 编译：coqc ATP_channel_proof.v（Rocq/Coq ≥ 9.0——文件使用 Stdlib 前缀，
      8.20 不兼容；9.1.1 验证通过）。
   4. §4 是编码规则的自洽性检查而非 A1–A5 的公理化推导（见文件头说明与
      txt 附录的"建模层约定与已知缺口"）；端点 p=0/1 已覆盖，闭区间
      唯一最大见 mle_max_closed / mle_unique_max_closed。
   ====================================================================== *)
