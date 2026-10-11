(* ==========================================================================
   《星际拓荒》记忆雕像信息链路 —— 形式化模型 (Rocq / Coq >= 9.0)
   —— 规格：docs/superpowers/specs/2026-09-04-memory-statue-design.md
   内容：休眠 / 激活配对 / 录制（事件日志+轮终点截断）/ 面具回传 /
   跨轮召回累积 (ATP 文档式 (5.4))。canon 依据：语料
   GD_StatueIsland_FirstMemoryStatueTest / Shared_GD_StatueIsland_TT_TimeLoopInterior /
   TT_TimeLoop_MaskActivationStatus / Gabbro 对话（02、04）。
   零公理 / 零未证项 / 零参数化常量。纯 List/nat/bool。
   编译：coqc docs/MemoryStatue_model.v
   ========================================================================== *)

From Stdlib Require Import List.
From Stdlib Require Import PeanoNat.
From Stdlib Require Import Lia.

(* --------------------------------------------------------------------------
   时间与轮次常数（口径见规格 §4：0 基轮号）
   -------------------------------------------------------------------------- *)
Definition c_T : nat := 1320.            (* 超新星终点（秒，ATP 口径 Δt） *)
Definition c_nstar : nat := 9318054.     (* 探测成功 = 第 9,318,054 次尝试 *)
Definition c_a : nat := c_nstar - 1.     (* 配对轮（0 基：第 9,318,054 次尝试轮） *)

Example c_T_val : c_T = 1320%nat.        Proof. reflexivity. Qed.
Example c_nstar_val : c_nstar = 9318054%nat. Proof. reflexivity. Qed.
Example c_a_val : c_a = 9318053%nat.     Proof. reflexivity. Qed.

(* ==========================================================================
   §1 记忆原子、网络记录与日志
   ========================================================================== *)

Local Open Scope bool_scope.  (* && / || 记法（mn_log 过滤器用到） *)

(* 记忆原子：内容(payload)抽象——只保留轮号/时刻/主体。
   跨轮互异由 ev_loop 保证（events_tagged 规则）。 *)
Record MEvent : Set := MEvent_mk {
  ev_loop  : nat;
  ev_time  : nat;
  ev_owner : nat
}.

Record MemoryNetwork : Set := MN_mk {
  events     : nat -> list MEvent;       (* 每轮发生的全部经历事件 *)
  pair_t     : nat -> nat -> bool;       (* (主体 s, 轮 n) 配对成立 *)
  death_inst : nat -> nat -> option nat; (* (主体 s, 轮 n) 死亡时刻；None=活到终点 *)
  cut        : nat -> nat;               (* 轮 n 的终点（≤ c_T） *)
  unit_of    : nat -> nat;               (* 主体专属存储单元 = 面具编号 *)
  (* ---- 规则字段（canon 依据见规格 §6 表） ---- *)
  pair_persist   : forall s n, is_true (pair_t s n) ->
                   is_true (pair_t s (S n));
  cut_le_T       : forall n, cut n <= c_T;
  cut_not_beyond_death :
    forall s n t, is_true (pair_t s n) -> death_inst s n = Some t ->
      cut n <= t;
  cut_requires_paired_death :
    forall n, cut n < c_T ->
      exists s, is_true (pair_t s n) /\ death_inst s n = Some (cut n);
  unit_injective : forall s1 s2, unit_of s1 = unit_of s2 -> s1 = s2;
  events_tagged  : forall n e, In e (events n) -> ev_loop e = n
}.

(* 雕像日志：已配对才录制；只录本人事件；只录到轮终点 cut n。
   配对轮"快照回读"= 不限制起始时刻（canon：加布罗"replayed everything
   I've just done"）。 *)
Definition mn_log (m : MemoryNetwork) (s n : nat) : list MEvent :=
  if pair_t m s n
  then filter (fun e => Nat.eqb (ev_owner e) s && Nat.leb (ev_time e) (cut m n))
              (events m n)
  else nil.

(* filter 成员判定：In x (filter f l) <-> In x l /\ f x = true *)
Lemma in_filter_iff : forall (A : Type) (f : A -> bool) (x : A) (l : list A),
  In x (filter f l) <-> In x l /\ f x = true.
Proof.
  intros A f x l. induction l as [| a l IH]; simpl.
  - split; [ intros H; destruct H | intros [H _]; exact H ].
  - destruct (f a) eqn:Hf; simpl; split.
    + (* 左向分支一：f a = true；In x (a :: filter f l) 分两支 *)
      intros H. destruct H as [Hxa | Hrest].
      * (* a = x：代入后用 Hf 证 f x = true *)
        rewrite Hxa in Hf. rewrite Hxa.
        split; [ left; reflexivity | exact Hf ].
      * (* 第二支 In x (filter f l)：用 IH 左向展开 *)
        destruct (proj1 IH Hrest) as [Hxl Hfx].
        split; [ right; exact Hxl | exact Hfx ].
    + (* 右向分支一：f a = true；In x (a :: l) 分两支 *)
      intros [Hxl Hfx]. destruct Hxl as [Hxa | Hrest].
      * (* a = x：f a = true 已由分支给出，直接入头 *)
        rewrite Hxa. left. reflexivity.
      * (* 第二支 In x l：IH 右向 *)
        right. exact (proj2 IH (conj Hrest Hfx)).
    + (* 左向分支二：f a = false；filter 只余 (filter f l) *)
      intros H. destruct (proj1 IH H) as [Hxl Hfx].
      split; [ right; exact Hxl | exact Hfx ].
    + (* 右向分支二：f a = false；a = x 时 f x = true 与分支矛盾 *)
      intros [Hxl Hfx]. destruct Hxl as [Hxa | Hrest].
      * rewrite Hxa in Hf. rewrite Hfx in Hf. discriminate Hf.
      * exact (proj2 IH (conj Hrest Hfx)).
Qed.

(* 日志成员判定（核心引理，M6/M7/M9/C 系列都从它读出） *)
Lemma mn_log_in_iff :
  forall (m : MemoryNetwork) s n e,
    In e (mn_log m s n) <->
      is_true (pair_t m s n) /\
      In e (events m n) /\ ev_owner e = s /\ ev_time e <= cut m n.
Proof.
  intros m s n e.
  unfold mn_log.
  destruct (pair_t m s n) eqn:Hp; simpl.
  - (* 配对分支：pair_t m s n = true；in_filter_iff 与 andb/eqb/leb 拆解 *)
    change (In e (filter (fun e0 => Nat.eqb (ev_owner e0) s &&
                                   Nat.leb (ev_time e0) (cut m n)) (events m n))
           <-> true = true /\ In e (events m n) /\ ev_owner e = s /\
                 ev_time e <= cut m n).
    split.
    + intros HIn.
      apply (proj1 (in_filter_iff MEvent
                     (fun e0 => Nat.eqb (ev_owner e0) s &&
                                Nat.leb (ev_time e0) (cut m n))
                     e (events m n))) in HIn.
      destruct HIn as [Hine Hcond].
      change (Nat.eqb (ev_owner e) s && Nat.leb (ev_time e) (cut m n) = true) in Hcond.
      destruct (proj1 (Bool.andb_true_iff (Nat.eqb (ev_owner e) s)
                                           (Nat.leb (ev_time e) (cut m n))) Hcond)
        as [Ho Ht].
      pose proof (proj1 (Nat.eqb_eq (ev_owner e) s) Ho) as Hown.
      pose proof (proj1 (Nat.leb_le (ev_time e) (cut m n)) Ht) as Htime.
      split; [ reflexivity | ].
      split; [ exact Hine | ].
      split; [ exact Hown | exact Htime ].
    + intros [Htt [Hine [Hown Htime]]].
      apply (proj2 (in_filter_iff MEvent
                     (fun e0 => Nat.eqb (ev_owner e0) s &&
                                Nat.leb (ev_time e0) (cut m n))
                     e (events m n))).
      split; [ exact Hine | ].
      change (Nat.eqb (ev_owner e) s && Nat.leb (ev_time e) (cut m n) = true).
      apply (proj2 (Bool.andb_true_iff (Nat.eqb (ev_owner e) s)
                                       (Nat.leb (ev_time e) (cut m n)))).
      split.
      * exact (proj2 (Nat.eqb_eq (ev_owner e) s) Hown).
      * exact (proj2 (Nat.leb_le (ev_time e) (cut m n)) Htime).
  - (* 未配对分支：pair_t m s n = false；mn_log = nil，双向都 trivial *)
    split.
    + intros Hnil. destruct Hnil.
    + intros [Hpf _]. cbv in Hpf. discriminate Hpf.
Qed.

Lemma log_dormant_nil :   (* = M1 *)
  forall (m : MemoryNetwork) s n,
    pair_t m s n = false -> mn_log m s n = nil.
Proof. intros m s n Hp. unfold mn_log. rewrite Hp. reflexivity. Qed.

Lemma log_bounded_by_cut :  (* = M6 *)
  forall (m : MemoryNetwork) s n e,
    In e (mn_log m s n) -> ev_time e <= cut m n.
Proof.
  (* 策略：mn_log_in_iff 左向（proj1）得
     (pair ∧ (In ∧ (owner=s ∧ time ≤ cut)))，逐层取出 time ≤ cut。 *)
  intros m s n e HIn.
  destruct (proj1 (mn_log_in_iff m s n e) HIn) as [Hp [Hine [Hown Htime]]].
  exact Htime.
Qed.

Lemma log_tagged_loop :  (* = M9 *)
  forall (m : MemoryNetwork) s n e,
    In e (mn_log m s n) -> ev_loop e = n.
Proof.
  (* 策略：mn_log_in_iff 左向取出 In e (events m n)，再用规则字段
     events_tagged。 *)
  intros m s n e HIn.
  destruct (proj1 (mn_log_in_iff m s n e) HIn) as [Hp [Hine [Hown Htime]]].
  exact (events_tagged m n e Hine).
Qed.

(* ==========================================================================
   §2 召回集（跨轮累积，(5.4) 逐字定义）
   ========================================================================== *)

Fixpoint mn_recall (m : MemoryNetwork) (s : nat) (n : nat) : list MEvent :=
  match n with
  | O   => nil
  | S k => mn_recall m s k ++ mn_log m s k
  end.

Theorem recall_step :
  forall (m : MemoryNetwork) s n,
    mn_recall m s (S n) = mn_recall m s n ++ mn_log m s n.
Proof. intros. reflexivity. Qed.

(* 列表代数：l ++ m = l -> m = nil（M8 用） *)
Lemma app_l_nil_eq : forall (A : Type) (l m : list A),
  l ++ m = l -> m = nil.
Proof.
  intros A l. induction l as [| a l IH]; intros m H.
  - exact H.
  - simpl in H. injection H as Htl. exact (IH m Htl).
Qed.

Theorem recall_flat_unpaired :   (* = M2 *)
  forall (m : MemoryNetwork) s n,
    pair_t m s n = false -> mn_recall m s (S n) = mn_recall m s n.
Proof.
  intros m s n Hp. rewrite recall_step.
  rewrite (log_dormant_nil m s n Hp). rewrite app_nil_r. reflexivity.
Qed.

(* 辅助：从起点开始从未配对的轮，召回恒空（∀ k < n 版，供实例化） *)
Lemma recall_before_pair :
  forall (m : MemoryNetwork) s n,
    (forall k, k < n -> pair_t m s k = false) -> mn_recall m s n = nil.
Proof.
  (* 策略：对 n 归纳。
     - O：mn_recall 定义 = nil，reflexivity。
     - S n：rewrite recall_step；IH 前提 (∀k<n) 由 (∀k<S n) 得；
       log 为 nil：log_dormant_nil（取 k=n，n<S n，前提给 pair=false）；
       nil++nil=nil 化简。 *)
  intros m s n. induction n as [| n IH]; intros Hall.
  - reflexivity.
  - rewrite recall_step.
    rewrite (log_dormant_nil m s n (Hall n (Nat.lt_succ_diag_r n))).
    rewrite (IH (fun k Hk => Hall k (Nat.lt_lt_succ_r k n Hk))).
    reflexivity.
Qed.

Theorem recall_empty_before_pair :   (* = M3（规格形态：∀ k ≤ n） *)
  forall (m : MemoryNetwork) s n,
    (forall k, k <= n -> pair_t m s k = false) -> mn_recall m s (S n) = nil.
Proof.
  (* 策略：apply recall_before_pair（n 换成 S n 处用其 k<S n 前提推出
     k≤n？注意 recall_before_pair 结论 mn_recall (S n) = nil 需前提 ∀k < S n，
     即 k ≤ n —— 与给定前提一致。直接 apply recall_before_pair；intros k Hk；
     apply H；lia。 *)
  intros m s n Hall.
  apply (recall_before_pair m s (S n)).
  intros k Hk. apply Hall. lia.
Qed.

Theorem recall_monotone :   (* = M4 *)
  forall (m : MemoryNetwork) s n x,
    In x (mn_recall m s n) -> In x (mn_recall m s (S n)).
Proof.
  intros m s n x Hx. rewrite recall_step.
  apply in_or_app. left. exact Hx.
Qed.

Theorem first_recall_is_pair_log :   (* = M5 *)
  forall (m : MemoryNetwork) s n,
    (forall k, k < n -> pair_t m s k = false) ->
    is_true (pair_t m s n) ->
    mn_recall m s (S n) = mn_log m s n.
Proof.
  (* 策略：rewrite recall_step；rewrite (recall_before_pair m s n)；
     前提从 ∀k<n 直接给出；simpl nil++l = l。 *)
  intros m s n Hall Hp. rewrite recall_step.
  rewrite (recall_before_pair m s n Hall).
  reflexivity.
Qed.

Theorem recall_owner_pure :   (* = M7（一雕像一人 → 无串扰） *)
  forall (m : MemoryNetwork) s n e,
    In e (mn_recall m s n) -> ev_owner e = s.
Proof.
  (* 策略：对 n 归纳。
     - O：In nil 矛盾。
     - S n：rewrite recall_step in H；in_app_or 分两支：
       * 本轮回传：mn_log_in_iff 取 ev_owner = s；
       * 旧召回：IH。 *)
  intros m s n. induction n as [| n IH]; intros e H.
  - simpl in H. destruct H.
  - rewrite recall_step in H.
    apply in_app_or in H.
    destruct H as [Hold | Hcur].
    + exact (IH e Hold).
    + destruct (proj1 (mn_log_in_iff m s n e) Hcur) as [Hp [Hine [Hown Htime]]].
      exact Hown.
Qed.

Theorem recall_strict_growth :   (* = M8 *)
  forall (m : MemoryNetwork) s n e,
    is_true (pair_t m s n) ->
    In e (events m n) -> ev_owner e = s -> ev_time e <= cut m n ->
    mn_recall m s (S n) <> mn_recall m s n.
Proof.
  (* 策略：
     1) 前提四件套 -> In e (mn_log m s n)（mn_log_in_iff 右向）。
     2) 故 mn_log m s n <> nil（In e nil 矛盾）。
     3) rewrite recall_step；intro Heq；
        apply (app_l_nil_eq (mn_recall m s n) (mn_log m s n) Heq)；矛盾。 *)
  intros m s n e Hp Hine Hown Htime.
  pose proof (proj2 (mn_log_in_iff m s n e)
                    (conj Hp (conj Hine (conj Hown Htime)))) as Hlog.
  intro Heq. rewrite recall_step in Heq.
  pose proof (app_l_nil_eq MEvent (mn_recall m s n) (mn_log m s n) Heq) as Hnil.
  rewrite Hnil in Hlog. simpl in Hlog. destruct Hlog.
Qed.

(* 支撑：召回集条目的轮号必早于当前轮（C4 用） *)
Lemma recall_tag_lt :
  forall (m : MemoryNetwork) s n e,
    In e (mn_recall m s n) -> ev_loop e < n.
Proof.
  (* 策略：对 n 归纳；S n 分支 in_app_or：
     - 本轮 log：log_tagged_loop -> ev_loop = n < S n（lia）；
     - 旧召回：IH 给出 ev_loop < n < S n（lia 收尾）。 *)
  intros m s n. induction n as [| n IH]; intros e H.
  - simpl in H. destruct H.
  - rewrite recall_step in H.
    apply in_app_or in H.
    destruct H as [Hold | Hcur].
    + pose proof (IH e Hold) as Hlt. lia.
    + rewrite (log_tagged_loop m s n e Hcur). lia.
Qed.

(* ==========================================================================
   §3 轮终点语义与存储单元
   ========================================================================== *)

Theorem cut_min_of_paired_deaths :   (* = M10（字段直读） *)
  forall (m : MemoryNetwork) n,
    cut m n < c_T ->
    exists s, is_true (pair_t m s n) /\ death_inst m s n = Some (cut m n).
Proof. intros m n H. exact (cut_requires_paired_death m n H). Qed.

Theorem full_loop_if_no_paired_death :   (* = M11 *)
  forall (m : MemoryNetwork) n,
    (forall s, is_true (pair_t m s n) -> death_inst m s n = None) ->
    cut m n = c_T.
Proof.
  (* 策略：assert (Hlt : ~ cut m n < c_T)。
        intro Hc；cut_min_of_paired_deaths 给 s；前提实例化 s 得 death = None，
        与 Some (cut m n) 矛盾（rewrite/discriminate）。
     然后 Nat.le_antisymm：[cut_le_T m n] 与 [Nat.nlt_ge 的右向：~x<y -> y<=x]。
     注：Rocq 9 stdlib 已将两引理更名（原 le_antisym / Nat.not_lt），
     语句不变：Nat.nlt_ge : ~ n < m <-> m <= n。 *)
  intros m n Hall.
  assert (Hlt : ~ cut m n < c_T).
  { intro Hc.
    destruct (cut_min_of_paired_deaths m n Hc) as [s [Hps Hd]].
    pose proof (Hall s Hps) as Hnone.
    rewrite Hd in Hnone. discriminate Hnone. }
  apply Nat.le_antisymm.
  - exact (cut_le_T m n).
  - apply (proj1 (Nat.nlt_ge (cut m n) c_T) Hlt).
Qed.

Theorem unpaired_death_keeps_loop :   (* = M12（M11 推论；规格定案形态） *)
  forall (m : MemoryNetwork) s n,
    pair_t m s n = false ->
    (forall s', is_true (pair_t m s' n) -> death_inst m s' n = None) ->
    cut m n = c_T.
Proof.
  intros m s n Hp Hall. apply (full_loop_if_no_paired_death m n Hall).
Qed.

Theorem no_cross_unit_delivery :   (* = M13（M7 推论：面罩不回他人记忆） *)
  forall (m : MemoryNetwork) s1 s2 n e,
    s1 <> s2 -> In e (mn_recall m s1 n) -> ev_owner e = s2 -> False.
Proof.
  (* 策略：recall_owner_pure（M7）给 ev_owner e = s1；代入 Hown 得 s1 = s2，
     与 s1 <> s2 矛盾。 *)
  intros m s1 s2 n e Hne Hin Hown.
  pose proof (recall_owner_pure m s1 n e Hin) as H1.
  rewrite H1 in Hown.
  exact (Hne Hown).
Qed.

Theorem storage_identity_injective :   (* = M14（字段直读） *)
  forall (m : MemoryNetwork) s1 s2,
    unit_of m s1 = unit_of m s2 -> s1 = s2.
Proof. intros m s1 s2 H. exact (unit_injective m s1 s2 H). Qed.

(* ==========================================================================
   §4 canon 实例 canon_net（规格 §7 数据表）
   ========================================================================== *)

(* 配对：0=你(TH 博物馆)、1=加布罗(GD 塑像岛) 自轮 c_a 起；其他主体永不配对 *)
Definition cn_pair (s n : nat) : bool :=
  if Nat.ltb n c_a then false
  else match s with O | S O => true | S (S _) => false end.

(* 死亡：仅演示轮 c_a+2 的你（死于 t=300）；其余 None *)
Definition cn_death (s n : nat) : option nat :=
  if (Nat.eqb s 0 && Nat.eqb n (c_a + 2))%bool then Some 300 else None.

(* 轮终点：c_a+2 轮早终 300（你死亡触发），其余满轮 1320 *)
Definition cn_cut (n : nat) : nat :=
  if Nat.eqb n (c_a + 2) then 300 else c_T.

(* 存储单元=面具号：你->1(TH)、加布罗->2(GD)、s>=2 -> s+3（避开 0=PTM/1/2；
   保持全射域上单射） *)
Definition cn_unit (s : nat) : nat :=
  match s with
  | O     => 1
  | S O   => 2
  | S (S k) => S (S (S (S (S k))))
  end.

(* 经历事件：轮 n >= c_a 起，你每轮 t=60、加布罗每轮 t=120；
   c_a+2 轮另加你 t=900 的事件（演示死亡截断排除它）；其余轮空 *)
Definition cn_events (n : nat) : list MEvent :=
  if Nat.ltb n c_a then nil
  else
    let e0 := MEvent_mk n 60 0 in
    let e1 := MEvent_mk n 120 1 in
    if Nat.eqb n (c_a + 2)
    then e0 :: MEvent_mk n 900 0 :: e1 :: nil
    else e0 :: e1 :: nil.

Lemma cn_pair_persist : forall s n,
  is_true (cn_pair s n) -> is_true (cn_pair s (S n)).
Proof.
  (* 策略：unfold is_true；destruct s（O/S O/S(S _) 三支）后把前提留在目标内
     做 ltb 拆分（destruct 只作用于目标内出现项）。
     - s = O、S O：配对真 ⇒ ltb n c_a = false ⇒（ltb_ge）c_a ≤ n ⇒
       （ltb_ge）ltb (S n) c_a = false，结论 if 分支 reflexivity；
       若 ltb (S n) c_a 为真则 n、S n 夹逼 c_a 矛盾（lia）。
     - s ≥ 2：cn_pair 恒 false，与前提矛盾（discriminate）。 *)
  unfold is_true. intros s n.
  destruct s as [| [| s'] ].
  - (* s = 0：你自 c_a 轮起持续配对 *)
    unfold cn_pair.
    destruct (Nat.ltb n c_a) eqn:Hl.
    + intro Hc. simpl in Hc. discriminate Hc.
    + intro Hc. simpl in Hc. clear Hc.
      destruct (Nat.ltb (S n) c_a) eqn:H2.
      * apply (proj1 (Nat.ltb_lt (S n) c_a)) in H2.
        apply (proj1 (Nat.ltb_ge n c_a)) in Hl.
        exfalso. lia.
      * reflexivity.
  - (* s = 1：加布罗同你一样自 c_a 轮起持续配对 *)
    unfold cn_pair.
    destruct (Nat.ltb n c_a) eqn:Hl.
    + intro Hc. simpl in Hc. discriminate Hc.
    + intro Hc. simpl in Hc. clear Hc.
      destruct (Nat.ltb (S n) c_a) eqn:H2.
      * apply (proj1 (Nat.ltb_lt (S n) c_a)) in H2.
        apply (proj1 (Nat.ltb_ge n c_a)) in Hl.
        exfalso. lia.
      * reflexivity.
  - (* s ≥ 2：spec 永不配对，前提与 cn_pair = false 矛盾 *)
    unfold cn_pair.
    destruct (Nat.ltb n c_a) eqn:Hl; intro Hc; simpl in Hc; discriminate Hc.
Qed.

Lemma cn_cut_le_T : forall n, cn_cut n <= c_T.
Proof.
  (* 策略：unfold cn_cut；destruct eqb 分轮：c_a+2 轮 300 ≤ c_T，其余轮 c_T ≤ c_T。 *)
  intros n. unfold cn_cut.
  destruct (Nat.eqb n (c_a + 2)) eqn:Hn.
  - unfold c_T. lia.
  - apply le_n.
Qed.

Lemma cn_cut_not_beyond_death : forall s n t,
  is_true (cn_pair s n) -> cn_death s n = Some t -> cn_cut n <= t.
Proof.
  (* 策略：unfold is_true；destruct s 三支。
     - s = 0：death 的 && 里 eqb n (c_a+2) 拆两路（目标内 unfold cn_cut 后拆分）：
       true 支 death = Some 300 ⇒ t = 300 且 cut = 300（injection + lia）；
       false 支 death = None 与 Some 矛盾（discriminate）。
     - s = 1：eqb 1 0 = false ⇒ death 恒 None → discriminate。
     - s ≥ 2：cn_pair 恒 false → 前提矛盾。 *)
  unfold is_true. intros s n t H Hd.
  destruct s as [| [| s'] ].
  - (* s = 0：死亡只发生在演示轮 c_a+2 的 t=300，恰为该轮 cut *)
    unfold cn_cut.
    destruct (Nat.eqb n (c_a + 2)) eqn:Hn.
    + unfold cn_death in Hd. rewrite Hn in Hd. simpl in Hd.
      injection Hd as Ht. lia.
    + unfold cn_death in Hd. rewrite Hn in Hd. simpl in Hd.
      discriminate Hd.
  - (* s = 1：加布罗永不死亡（死亡时刻恒 None） *)
    unfold cn_death in Hd. simpl in Hd. discriminate Hd.
  - (* s ≥ 2：永不配对，前提矛盾 *)
    assert (Hf : cn_pair (S (S s')) n = false).
    { unfold cn_pair. destruct (Nat.ltb n c_a) eqn:Hl; reflexivity. }
    rewrite Hf in H. discriminate H.
Qed.

Lemma cn_cut_requires_paired_death : forall n,
  cn_cut n < c_T ->
  exists s, is_true (cn_pair s n) /\ cn_death s n = Some (cn_cut n).
Proof.
  (* 策略：unfold is_true/cn_cut 后对目标内 eqb n (c_a+2) 拆分。
     - true 支（cut=300）：取 s=0；配对：ltb 真则 n < c_a 与 n = c_a+2（eqb_eq）
       矛盾（lia）；death：unfold 后 rewrite eqb 方程再 simpl/reflexivity。
     - false 支（cut=c_T）：前提 c_T < c_T 矛盾（lia）。 *)
  unfold is_true. intros n.
  unfold cn_cut.
  destruct (Nat.eqb n (c_a + 2)) eqn:Hn.
  - (* n = c_a + 2：cut = 300 < 1320，见证 s = 0（你，TH 博物馆） *)
    intro Hlt. exists 0. split.
    + (* cn_pair 0 n = true：n = c_a+2 ≥ c_a *)
      unfold cn_pair. simpl.
      destruct (Nat.ltb n c_a) eqn:Hl.
      * apply (proj1 (Nat.ltb_lt n c_a)) in Hl.
        exfalso.
        assert (n = c_a + 2) by (apply (proj1 (Nat.eqb_eq n (c_a + 2))); exact Hn).
        lia.
      * reflexivity.
    + (* cn_death 0 n = Some 300 = Some (cn_cut n) *)
      unfold cn_death. rewrite Hn. simpl. reflexivity.
  - (* n ≠ c_a + 2：cut = c_T，前提 c_T < c_T 矛盾 *)
    intro Hlt. exfalso. simpl in Hlt. unfold c_T in Hlt. lia.
Qed.

Lemma cn_unit_injective : forall s1 s2,
  cn_unit s1 = cn_unit s2 -> s1 = s2.
Proof.
  (* 策略：destruct s1、s2（O/S O/S(S k) 九格）；simpl in H 后 congruence
     （构造子互异 discriminate、S 链注入、末格 5+k1 = 5+k2 → k1=k2）。 *)
  intros s1 s2 H.
  destruct s1 as [| [| k1]]; destruct s2 as [| [| k2]]; simpl in H; congruence.
Qed.

Lemma cn_events_tagged : forall n e, In e (cn_events n) -> ev_loop e = n.
Proof.
  (* 策略：unfold cn_events in H；两个布尔测试（ltb/eqb）只出现在假设里，
     destruct 需变量：pose 出测试变量 b/c 再改写进假设后 destruct。
     - ltb true 支：nil，In 矛盾。
     - ltb false 支拆 eqb：两支行（c_a+2 轮三事件 / 其余两事件）
       In 逐层 left/right，每个 e 即 MEvent_mk n _ _，代入后 reflexivity。 *)
  intros n e HIn.
  unfold cn_events in HIn.
  pose (b := Nat.ltb n c_a).
  assert (Hlb : b = Nat.ltb n c_a) by reflexivity.
  rewrite <- Hlb in HIn.
  destruct b.
  - simpl in HIn. destruct HIn.
  - pose (c := Nat.eqb n (c_a + 2)).
    assert (Hc : c = Nat.eqb n (c_a + 2)) by reflexivity.
    rewrite <- Hc in HIn.
    destruct c.
    + simpl in HIn.
      destruct HIn as [He0 | [He9 | [He1 | Hnil]]].
      * subst e. reflexivity.
      * subst e. reflexivity.
      * subst e. reflexivity.
      * destruct Hnil.
    + simpl in HIn.
      destruct HIn as [He0 | [He1 | Hnil]].
      * subst e. reflexivity.
      * subst e. reflexivity.
      * destruct Hnil.
Qed.

Definition canon_net : MemoryNetwork :=
  MN_mk cn_events cn_pair cn_death cn_cut cn_unit
    cn_pair_persist cn_cut_le_T cn_cut_not_beyond_death
    cn_cut_requires_paired_death cn_unit_injective cn_events_tagged.

(* ==========================================================================
   §5 canon 实例定理
   ========================================================================== *)

(* 配对边界事实：成功轮（c_a 轮）前无人配对：k < c_a ⇒ cn_pair s k = false *)
Lemma cn_pair_false_before_a : forall s k, k < c_a -> cn_pair s k = false.
Proof.
  (* 策略：unfold cn_pair 后 if 条件为 ltb k c_a；由 k < c_a 用
     Nat.ltb_lt 右向得 ltb = true（rewrite 判定方程，先于任何化简），
     if 走 false 支后 reflexivity。 *)
  intros s k Hk. unfold cn_pair.
  rewrite (proj2 (Nat.ltb_lt k c_a) Hk). reflexivity.
Qed.

(* 配对边界事实：c_a 轮（0 基成功轮起点）你(0)/加布罗(1)开始配对 *)
Lemma cn_pair_true_at_a : forall s, s = 0 \/ s = 1 ->
  is_true (cn_pair s c_a).
Proof.
  (* 策略：unfold is_true, cn_pair；if 条件 ltb c_a c_a 恒 false：
     destruct eqn 拆两支（不化简 c_a），true 支由 Nat.ltb_lt 得
     c_a < c_a（lia 矛盾）；false 支代入 s=0 / s=1 后 match 归约出 true。 *)
  unfold is_true. intros s Hs. unfold cn_pair.
  destruct (Nat.ltb c_a c_a) eqn:Hl.
  - exfalso. apply (proj1 (Nat.ltb_lt c_a c_a)) in Hl. lia.
  - destruct Hs as [Hs0 | Hs1]; subst s; reflexivity.
Qed.

(* C3：其他人（s ≥ 2）任何轮召回恒空 *)
Theorem others_never_remember :
  forall s n, 2 <= s -> mn_recall canon_net s n = nil.
Proof.
  (* 策略：recall_before_pair：只需 ∀k<n，pair_t canon_net s k = false
     （change 把投影归约到 cn_pair；unfold cn_pair 后按 s≥2 分解 s：
     O / S O 与 2<=s 矛盾 lia，S(S _) 支 match 恒走 false 支）。 *)
  intros s n Hs2.
  apply (recall_before_pair canon_net s n).
  intros k Hk.
  change (cn_pair s k = false).
  unfold cn_pair.
  destruct s as [| [| s'] ].
  - exfalso. lia.
  - exfalso. lia.
  - destruct (Nat.ltb k c_a); reflexivity.
Qed.

(* C2：你(0)/加布罗(1)在成功轮前（含配对轮起点）召回恒空 *)
Theorem no_memory_before_success :
  forall n, n <= c_a -> mn_recall canon_net 0 n = nil /\
                         mn_recall canon_net 1 n = nil.
Proof.
  (* 策略：split 后各 apply recall_before_pair（结论 mn_recall ... n = nil，
     n 即当前 n）；前提 ∀k<n：k < n <= c_a ⇒ k < c_a（lia），
     用 cn_pair_false_before_a。 *)
  intros n Hn. split.
  - apply (recall_before_pair canon_net 0 n).
    intros k Hk.
    change (cn_pair 0 k = false).
    apply (cn_pair_false_before_a 0 k). lia.
  - apply (recall_before_pair canon_net 1 n).
    intros k Hk.
    change (cn_pair 1 k = false).
    apply (cn_pair_false_before_a 1 k). lia.
Qed.

(* C1：首次拥有记忆 = 0 基轮 c_a+1 起点（你与加布罗同轮），且日志非空 *)
Theorem first_memory_after_success :
  mn_recall canon_net 0 (S c_a) <> nil /\
  mn_recall canon_net 1 (S c_a) <> nil.
Proof.
  (* 策略（先证三条共享事实，避免重复）：
     - ltb c_a c_a = false、eqb c_a (c_a+2) = false（destruct eqn，
       true 支用 Nat.ltb_lt / Nat.eqb_eq + lia 导出矛盾）；
     - 故 cn_cut c_a = c_T（rewrite He_a 后 reflexivity）；
     - 两主体的配对轮事件 MEvent_mk c_a 60 0 / MEvent_mk c_a 120 1
       均在 cn_events c_a 列内（rewrite 两判定后 simpl，left/right）。
     然后 split；主体 0 / 1 同构：first_recall_is_pair_log 把
     mn_recall (S c_a) 化为配对轮日志 mn_log c_a；mn_log_in_iff 右向
     给出成员（配对 true + 事件在列 + owner 匹配 + 60/120 <= cut=c_T），
     成员 ⇒ 非空（rewrite 空日志到成员得 In e nil 矛盾）。 *)
  assert (Hl_a : Nat.ltb c_a c_a = false).
  { destruct (Nat.ltb c_a c_a) eqn:Hl.
    - exfalso. apply (proj1 (Nat.ltb_lt c_a c_a)) in Hl. lia.
    - reflexivity. }
  assert (He_a : Nat.eqb c_a (c_a + 2) = false).
  { destruct (Nat.eqb c_a (c_a + 2)) eqn:He.
    - exfalso. apply (proj1 (Nat.eqb_eq c_a (c_a + 2))) in He. lia.
    - reflexivity. }
  assert (Hcut_a : cn_cut c_a = c_T).
  { unfold cn_cut. rewrite He_a. reflexivity. }
  assert (Hm0e : In (MEvent_mk c_a 60 0) (cn_events c_a)).
  { unfold cn_events. rewrite Hl_a, He_a. simpl. left. reflexivity. }
  assert (Hm1e : In (MEvent_mk c_a 120 1) (cn_events c_a)).
  { unfold cn_events. rewrite Hl_a, He_a. simpl. right. left. reflexivity. }
  split.
  - (* 主体 0（你）：首回忆 = 配对轮日志，含 MEvent_mk c_a 60 0 ⇒ 非空 *)
    assert (Hlog0 : mn_recall canon_net 0 (S c_a) = mn_log canon_net 0 c_a).
    { apply (first_recall_is_pair_log canon_net 0 c_a).
      - intros k Hk.
        change (cn_pair 0 k = false).
        exact (cn_pair_false_before_a 0 k Hk).
      - change (is_true (cn_pair 0 c_a)).
        exact (cn_pair_true_at_a 0 (or_introl eq_refl)). }
    rewrite Hlog0.
    assert (Hm0 : In (MEvent_mk c_a 60 0) (mn_log canon_net 0 c_a)).
    { apply (proj2 (mn_log_in_iff canon_net 0 c_a (MEvent_mk c_a 60 0))).
      split.
      - change (is_true (cn_pair 0 c_a)).
        exact (cn_pair_true_at_a 0 (or_introl eq_refl)).
      - split.
        + change (In (MEvent_mk c_a 60 0) (cn_events c_a)).
          exact Hm0e.
        + split.
          * reflexivity.
          * change (60 <= cn_cut c_a).
            rewrite Hcut_a. unfold c_T. lia. }
    intro Hn0. rewrite Hn0 in Hm0. simpl in Hm0. destruct Hm0.
  - (* 主体 1（加布罗）：同构，见证 MEvent_mk c_a 120 1，120 <= c_T *)
    assert (Hlog1 : mn_recall canon_net 1 (S c_a) = mn_log canon_net 1 c_a).
    { apply (first_recall_is_pair_log canon_net 1 c_a).
      - intros k Hk.
        change (cn_pair 1 k = false).
        exact (cn_pair_false_before_a 1 k Hk).
      - change (is_true (cn_pair 1 c_a)).
        exact (cn_pair_true_at_a 1 (or_intror eq_refl)). }
    rewrite Hlog1.
    assert (Hm1 : In (MEvent_mk c_a 120 1) (mn_log canon_net 1 c_a)).
    { apply (proj2 (mn_log_in_iff canon_net 1 c_a (MEvent_mk c_a 120 1))).
      split.
      - change (is_true (cn_pair 1 c_a)).
        exact (cn_pair_true_at_a 1 (or_intror eq_refl)).
      - split.
        + change (In (MEvent_mk c_a 120 1) (cn_events c_a)).
          exact Hm1e.
        + split.
          * reflexivity.
          * change (120 <= cn_cut c_a).
            rewrite Hcut_a. unfold c_T. lia. }
    intro Hn1. rewrite Hn1 in Hm1. simpl in Hm1. destruct Hm1.
Qed.
(* C4a：演示轮 c_a+2 的你死于 300 -> 轮终点 = 300（cut 函数直接给出） *)
Theorem death_cut_demo_cut :
  cn_cut (c_a + 2) = 300 /\ cn_death 0 (c_a + 2) = Some 300.
Proof.
  (* 策略：判定方程先行（§4/§5a 惯例，勿让 simpl/vm_compute 展开 c_a）。
     - cut 支：unfold cn_cut 后 rewrite eqb_eq 的右向（proj2 + eq_refl）
       把 eqb (c_a+2) (c_a+2) 判定改写成 true，if 走 then 支，reflexivity；
     - death 支：unfold cn_death 后同法改写两处判定
       （eqb 0 0 = true 与 eqb (c_a+2) (c_a+2) = true），
       andb true true 归约后 reflexivity。 *)
  split.
  - unfold cn_cut.
    rewrite (proj2 (Nat.eqb_eq (c_a + 2) (c_a + 2)) eq_refl).
    reflexivity.
  - unfold cn_death.
    rewrite (proj2 (Nat.eqb_eq 0 0) eq_refl).
    rewrite (proj2 (Nat.eqb_eq (c_a + 2) (c_a + 2)) eq_refl).
    reflexivity.
Qed.

(* C4b：t=900 的经历事件（晚于死亡 300）绝不在次轮召回中 *)
Theorem death_cut_demo_excluded :
  ~ In (MEvent_mk (c_a + 2) 900 0) (mn_recall canon_net 0 (S (c_a + 2))).
Proof.
  (* 策略：intro H；rewrite recall_step in H 拆出旧召回 ++ 本轮 log；
     in_app_or 分两支，均归谬：
     - 旧召回：recall_tag_lt 给 ev_loop e < c_a+2，而 e 的 ev_loop 恰为
       c_a+2（change 只归约 ev_loop 投影、不动 c_a），lia 矛盾；
     - 本轮 log：log_bounded_by_cut 给 ev_time e <= cut canon_net (c_a+2)。
       注意：不能让 kernel 转换去比较 cut canon_net (c_a+2) 与 cn_cut
       (c_a+2)（会求值 c_a 的 9.3M 位链，极慢）；改用 §4 惯例在 tactic
       层先证 cut 投影方程：cbv 只 delta[iota] 指定定义、随后 rewrite
       eqb 判定方程，取 cut 值 = 300；rewrite 进 Ht 后 change 归约
       ev_time 投影得 900 <= 300，lia 判假。 *)
  intro H.
  rewrite recall_step in H.
  apply in_app_or in H.
  destruct H as [Hold | Hcur].
  - (* 旧召回：轮号标签必须早于 c_a+2，但该事件标签恰为 c_a+2 *)
    pose proof (recall_tag_lt canon_net 0 (c_a + 2)
                             (MEvent_mk (c_a + 2) 900 0) Hold) as Hlt.
    change (c_a + 2 < c_a + 2) in Hlt.
    lia.
  - (* 本轮 log：事件时刻 900 已越过轮终点 cut = 300 *)
    pose proof (log_bounded_by_cut canon_net 0 (c_a + 2)
                                   (MEvent_mk (c_a + 2) 900 0) Hcur) as Ht.
    assert (Hc : cut canon_net (c_a + 2) = 300).
    { cbv beta iota delta [canon_net cut cn_cut].
      rewrite (proj2 (Nat.eqb_eq (c_a + 2) (c_a + 2)) eq_refl).
      reflexivity. }
    rewrite Hc in Ht.
    change (900 <= 300) in Ht.
    lia.
Qed.

(* 面具活跃表：0=PTM、1=TH、2=GD 活跃；3..7 恒不活跃（8 座面具墙） *)
Definition cn_mask_active (idm : nat) : bool := Nat.ltb idm 3.

(* C5：活跃 ⇔ 编号 ∈ {0,1,2}（规格 §7 表；PTM 数据内容不建模，见边界 §10.4） *)
Theorem three_masks_active :
  forall idm, is_true (cn_mask_active idm) <->
              idm = 0 \/ idm = 1 \/ idm = 2.
Proof.
  (* 策略：unfold is_true, cn_mask_active；rewrite Nat.ltb_lt 把判定式化为
     算术比较 idm < 3；destruct idm 四支（O / S O / S(S O) / S(S(S m))）：
     - O/S O/S(S O) 支：左向由析取项直接 right/left + reflexivity；
       右向 destruct 拆三支析取后每支 lia 闭合（闭算术事实/矛盾等式）；
     - S(S(S m)) 支：左向前提 S(S(S m)) < 3 与 lia 矛盾；
       右向三支等式（S(S(S m)) 等于 0/1/2）皆 lia 判不可达。
     注：数字都很小（仅 3/0/1/2），lia 展开的只是小 unary，与 c_a 无关。 *)
  unfold is_true, cn_mask_active. intro idm. rewrite Nat.ltb_lt.
  destruct idm as [| [| [| m'] ] ].
  - (* idm = 0 *)
    split; intros H.
    + left. reflexivity.
    + destruct H as [Ha | [Hb | Hc]]; lia.
  - (* idm = 1 *)
    split; intros H.
    + right. left. reflexivity.
    + destruct H as [Ha | [Hb | Hc]]; lia.
  - (* idm = 2 *)
    split; intros H.
    + right. right. reflexivity.
    + destruct H as [Ha | [Hb | Hc]]; lia.
  - (* idm >= 3 *)
    split; intros H.
    + exfalso. lia.
    + destruct H as [Ha | [Hb | Hc]]; lia.
Qed.

(* 三座活跃 + 其余不活跃（数值判定） *)
Example mask_active_examples :
  is_true (cn_mask_active 0) /\ is_true (cn_mask_active 1) /\
  is_true (cn_mask_active 2) /\ ~ is_true (cn_mask_active 3) /\
  ~ is_true (cn_mask_active 7).
Proof.
  (* 参与计算的全是小常数 0/1/2/3/7（ltb 判定），vm_compute 安全
     （绝不触 c_a/c_nstar 的 9.3M 位链）。算出三件 true=true 与两件
     (false=true)->False；split 后等式 reflexivity、蕴含 discriminate。 *)
  vm_compute.
  repeat split.
  all: try reflexivity.
  all: intros Hc; discriminate Hc.
Qed.
(* ==========================================================================
   §6 数值示例与边界声明
   ========================================================================== *)

(* C6：口径数值判定（规格 §4） *)
Example c_a_plus_one : c_a + 1 = 9318054%nat.
Proof.
  (* 整体对 9.3M 位 c_nstar 做加法归约不可行（reflexivity 极慢、
     vm_compute 直接栈溢出）。先用 Nat.add_1_r 把 c_a+1 折成 S c_a ——
     与 §0 c_a_val 同款的“sub 一步”快形状 —— 再 reflexivity。 *)
  rewrite (Nat.add_1_r c_a). reflexivity.
Qed.
Example c_T_is_loop_len : c_T = 22 * 60. Proof. reflexivity. Qed.

(* 边界声明（不建模，注释备案；对应规格 §10）：
   1. 记忆内容语义（payload 抽象为原子编号）；
   2. 配对选择机制（"最近者"是 canon 唯一规则，凝视/意识理论属人物观点）；
   3. ConsciousnessDebate：通道运载物 = 记忆数据，主体肉身不随通道迁移
      （与 ATP A3"信息单行道"一致；哲学层不入机器）；
   4. PTM 探测数据内容不建模（仅面罩编号 0 占位，A3 其余分量属 ATP 层）；
   5. 死亡时刻 = c_T 与 None 轮终点等价（实例只用 t < c_T 的早死）；
   6. 睡眠/坐火等游戏性结算不入模型；
   7. 快照语义边界：配对授予当日早前经历访问权，不授予此前各轮
      （M3/recall_before_pair 保证激活前召回恒空）。
   ========================================================================== *)

(* 终审：coqc 通过；零公理 / 零未证项 / 零参数化常量（含 §5b/§6 全部证明 Qed）。 *)
