(* ==========================================================================
   B3 自建级数证明：38.18 < 1319 / ln(10^15) < 38.20
   ==========================================================================
   目标归约（已核）：
     τ := 1319 / ln(10^15)；38.18 = 1909/50，38.20 = 191/5。
     τ < 191/5  ⟺  ln(10^15) > 6595/191
     τ > 1909/50 ⟺  ln(10^15) < 65950/1909
   路线：
     1) 用 exp_in（幂级数和）与 exp_lt_inv 把 ln 界化为 exp 界：
        再经 ln(10^15) = 15·ln 10 落到两个小有理点的 exp 界：
        e^{1151/500} < 10 < e^{2303/1000}（1151/500 = 2.302 < ln 10 < 2.303）
     2) 对有理点 x 的 exp 上下界用部分和 + 余项：
        下界：exp x ≥ S_N(x)（正项级数，部分和单调趋于极限）
        上界：exp x ≤ S_N(x) + x^{N+1}/(N+1)! · 1/(1 − x/(N+2))
               （余项几何控制，x < N+2；N = 11）
     3) 闭式有理比较用 nra 结算：阶乘分母先经二进制 Z 阶乘 factZ
        写成 IZR 字面量（规避一元 nat 阶乘展开），不做 vm_compute。
   本文件已完整：exp 级数表示、部分和/余项上界（epsilon 证明）、
   数值结算与两个主定理（38.18 < 1319/ln(10^15) < 38.20），零 Axiom。
   ========================================================================== *)

From Stdlib Require Import Reals.
From Stdlib Require Import Psatz.
From Stdlib Require Import Arith.
From Stdlib Require Import Lia.
Open Scope R_scope.

(* exp 的幂级数和表示：exp_in x (exp x) —— 由 exist_exp 的性质直接得到 *)
Lemma exp_is_sum : forall x, exp_in x (exp x).
Proof.
  intros x.
  unfold exp.
  exact (proj2_sig (exist_exp x)).
Qed.

(* 部分和：S_N(x) = Σ_{k=0..N} x^k / k! *)
Fixpoint fact (n : nat) : nat :=
  match n with
  | O => 1
  | S m => S m * fact m
  end.

Fixpoint partial (x : R) (N : nat) : R :=
  match N with
  | O => 1
  | S m => partial x m + x ^ (S m) / INR (fact (S m))
  end.

Lemma fact_gt0 : forall n, 0 < INR (fact n).
Proof.
  intros n. apply lt_0_INR. induction n; simpl; lia.
Qed.

Lemma fact_neq0 : forall n, INR (fact n) <> 0.
Proof. intros n. apply Rgt_not_eq. apply fact_gt0. Qed.

(* 余项控制常数：R = x^{N+1}/(N+1)! * 1/(1 - x/(N+2)) *)
Definition tail_cap (x : R) (N : nat) : R :=
  x ^ (N + 1) / INR (fact (N + 1)) * / (1 - x / INR (N + 2)).

Lemma pow_nonneg : forall x n, 0 <= x -> 0 <= x ^ n.
Proof.
  intros x n Hx. induction n as [| n IH]; simpl.
  - lra.
  - apply Rmult_le_pos; assumption.
Qed.

Lemma term_nonneg : forall x k, 0 <= x -> 0 <= / INR (fact k) * x ^ k.
Proof.
  intros x k Hx.
  apply Rmult_le_pos.
  - apply Rlt_le. apply Rinv_0_lt_compat. apply fact_gt0.
  - apply pow_nonneg; exact Hx.
Qed.

Lemma partial_le_next : forall x n, 0 <= x -> partial x n <= partial x (S n).
Proof.
  intros x n Hx.
  assert (Ht : 0 <= x ^ (S n) / INR (fact (S n))) by
    (unfold Rdiv; apply Rmult_le_pos;
     [ apply pow_nonneg; exact Hx | apply Rlt_le; apply Rinv_0_lt_compat; apply fact_gt0 ]).
  change (partial x n <= partial x n + x ^ (S n) / INR (fact (S n))).
  lra.
Qed.

Lemma mono_k : forall x a k, 0 <= x -> partial x a <= partial x (a + k).
Proof.
  intros x a k Hx. induction k as [| k IHk]; simpl.
  - rewrite Nat.add_0_r. apply Rle_refl.
  - rewrite (Nat.add_succ_r a k).
    apply Rle_trans with (partial x (a + k)).
    + exact IHk.
    + apply partial_le_next. exact Hx.
Qed.

Lemma partial_mono_steps : forall x a b, 0 <= x -> (a <= b)%nat ->
  partial x a <= partial x b.
Proof.
  intros x a b Hx Hab.
  assert (Hadd : b = Nat.add a (Nat.sub b a)) by lia.
  assert (Hmon : partial x a <= partial x (Nat.add a (Nat.sub b a)))
    by (apply mono_k; exact Hx).
  rewrite <- Hadd in Hmon. exact Hmon.
Qed.

Lemma partial_eq_sum : forall x N, partial x N =
  sum_f_R0 (fun k : nat => / INR (fact k) * x ^ k) N.
Proof.
  intros x N. induction N as [| N IH]; simpl.
  - assert (Hi : / 1 = 1) by field. rewrite Hi. ring.
  - rewrite IH. unfold Rdiv. ring.
Qed.

Lemma le_eps_char : forall a b,
  (forall e : R, 0 < e -> a < b + e) -> a <= b.
Proof.
  intros a b H.
  apply Rnot_gt_le. intros Habs.
  assert (He : 0 < (a - b) / 2) by lra.
  specialize (H ((a - b) / 2) He).
  lra.
Qed.

(* exp 下界：正项级数 ⇒ 部分和 ≤ exp *)
Lemma exp_ge_partial : forall x N, 0 <= x -> partial x N <= exp x.
Proof.
  intros x N Hx.
  apply le_eps_char. intros eps Hep.
  assert (He2 : 0 < eps / 2) by lra.
  assert (Hinf : infinite_sum (fun k : nat => / INR (fact k) * x ^ k) (exp x)).
  { exact (exp_is_sum x). }
  destruct (Hinf (eps / 2) He2) as [M HM].
  set (n := Nat.max N M).
  assert (HnN : (N <= n)%nat) by (unfold n; lia).
  assert (HnM : (M <= n)%nat) by (unfold n; lia).
  assert (Hmon : partial x N <= partial x n) by (apply partial_mono_steps; assumption).
  assert (Hsn : partial x n <= exp x + eps / 2).
  { apply Rle_trans with (Rabs (partial x n - exp x) + exp x).
    - assert (Hle : partial x n - exp x <= Rabs (partial x n - exp x))
        by apply Rle_abs.
      lra.
    - unfold R_dist in HM.
      assert (Hsum_eq : sum_f_R0
                (fun k : nat => / INR (fact k) * x ^ k) n = partial x n).
      { symmetry. apply partial_eq_sum. }
      specialize (HM n HnM).
      rewrite Hsum_eq in HM.
      replace (exp x + eps / 2) with (eps / 2 + exp x) by ring.
      apply Rplus_le_compat_r.
      apply Rlt_le. exact HM. }
  apply Rle_lt_trans with (exp x + eps / 2).
  - apply Rle_trans with (partial x n); [ exact Hmon | exact Hsn ].
  - lra.
Qed.

(* ======================================================================
   §B  exp 上界：部分和 + 余项控制（epsilon 证明，本文件核心）
   记 t_k := x^k/k!，r := x/(N+2)。0 ≤ x < N+2 时 r ∈ [0,1)，且对
   k ≥ N+1 有 t_{k+1} = t_k·x/(k+1) ≤ t_k·r（逐项几何衰减），故
     Σ_{k≥N+1} t_k ≤ t_{N+1}·Σ_{j≥0} r^j = t_{N+1}/(1−r) = tail_cap。
   ====================================================================== *)

(* 单项非负（除法书写形式） *)
Lemma term_div_nonneg : forall x k, 0 <= x -> 0 <= x ^ k / INR (fact k).
Proof.
  intros x k Hx.
  unfold Rdiv. rewrite Rmult_comm. apply term_nonneg. exact Hx.
Qed.

(* t_{k+1} = t_k · x/(k+1)：级数项的单步递推 *)
Lemma term_succ : forall x k,
  x ^ (S k) / INR (fact (S k)) =
  (x ^ k / INR (fact k)) * (x / INR (S k)).
Proof.
  intros x k.
  change (x ^ (S k)) with (x * x ^ k).
  change (INR (fact (S k))) with (INR ((S k) * fact k)).
  rewrite mult_INR.
  field; repeat split;
    try apply fact_neq0;
    try (apply Rgt_not_eq; apply lt_0_INR; lia).
Qed.

(* 公比 r = x/(N+2) 非负 *)
Lemma r_nonneg : forall x N, 0 <= x -> 0 <= x / INR (N + 2).
Proof.
  intros x N Hx.
  unfold Rdiv.
  apply Rmult_le_pos; [ exact Hx | ].
  apply Rlt_le. apply Rinv_0_lt_compat. apply lt_0_INR. lia.
Qed.

(* 比值逐项缩小：k ≥ N+2 时 x/k ≤ x/(N+2)（x ≥ 0） *)
Lemma ratio_decay : forall x N k, 0 <= x -> (N + 2 <= k)%nat ->
  x / INR k <= x / INR (N + 2).
Proof.
  intros x N k Hx Hk.
  unfold Rdiv.
  apply Rmult_le_compat_l; [ exact Hx | ].
  apply Rinv_le_contravar.
  - apply lt_0_INR. lia.
  - apply le_INR. exact Hk.
Qed.

(* 分母 1 − x/(N+2) 为正 *)
Lemma geom_den_pos : forall x N, 0 <= x -> x < INR (N + 2) ->
  0 < 1 - x / INR (N + 2).
Proof.
  intros x N Hx Hxl.
  assert (Hd : 0 < INR (N + 2)) by (apply lt_0_INR; lia).
  assert (Hn : INR (N + 2) <> 0) by (apply Rgt_not_eq; exact Hd).
  assert (Heq : 1 - x / INR (N + 2) =
                (INR (N + 2) - x) / INR (N + 2)).
  { unfold Rdiv. field. exact Hn. }
  rewrite Heq.
  apply Rdiv_lt_0_compat.
  - lra.
  - exact Hd.
Qed.

(* 公比 < 1：x < N+2 时 x/(N+2) < 1 *)
Lemma r_lt_1 : forall x N, x < INR (N + 2) -> x / INR (N + 2) < 1.
Proof.
  intros x N Hxl.
  assert (Hd : 0 < INR (N + 2)) by (apply lt_0_INR; lia).
  assert (Hn : INR (N + 2) <> 0) by (apply Rgt_not_eq; exact Hd).
  apply (Rmult_lt_reg_l (INR (N + 2)) (x / INR (N + 2)) 1).
  - exact Hd.
  - unfold Rdiv.
    rewrite <- Rmult_assoc.
    rewrite (Rmult_comm (INR (N + 2)) x).
    rewrite Rmult_assoc.
    rewrite (Rinv_r (INR (N + 2)) Hn).
    rewrite Rmult_1_r.
    rewrite Rmult_1_r.
    exact Hxl.
Qed.

(* 尾项几何控制：t_{N+1+j} ≤ t_{N+1} · r^j（j 归纳） *)
Lemma tail_term_le : forall x N j, 0 <= x -> x < INR (N + 2) ->
  x ^ (N + 1 + j) / INR (fact (N + 1 + j))
  <= (x ^ (N + 1) / INR (fact (N + 1))) * (x / INR (N + 2)) ^ j.
Proof.
  intros x N j Hx Hxl.
  induction j as [| j IH].
  - rewrite Nat.add_0_r. rewrite pow_O. rewrite Rmult_1_r.
    apply Rle_refl.
  - (* 项递推 + 比值缩小 + 归纳假设 *)
    assert (He : (N + 1 + S j)%nat = S (N + 1 + j)) by lia.
    rewrite He.
    rewrite (term_succ x (N + 1 + j)).
    apply Rle_trans with
      ((x ^ (N + 1 + j) / INR (fact (N + 1 + j))) * (x / INR (N + 2))).
    + apply Rmult_le_compat_l.
      * apply term_div_nonneg. exact Hx.
      * apply ratio_decay; [ exact Hx | ].
        lia.  (* N + 2 ≤ S (N+1+j) *)
    + apply Rle_trans with
        ((x ^ (N + 1) / INR (fact (N + 1))) * (x / INR (N + 2)) ^ j
         * (x / INR (N + 2))).
      * apply Rmult_le_compat_r.
        -- apply r_nonneg. exact Hx.
        -- exact IH.
      * change ((x / INR (N + 2)) ^ S j)
          with ((x / INR (N + 2)) * (x / INR (N + 2)) ^ j).
        ring_simplify. apply Rle_refl.
Qed.

(* 有限等比和闭式：(1−r^{t+1})/(1−r) *)
Lemma geom_sum_closed : forall (r : R) (t : nat), r <> 1 ->
  sum_f_R0 (fun j : nat => r ^ j) t = (1 - r ^ (S t)) / (1 - r).
Proof.
  intros r t Hr.
  assert (Hz : 1 - r <> 0) by lra.
  induction t as [| t IH].
  - simpl. field. exact Hz.
  - change (sum_f_R0 (fun j : nat => r ^ j) (S t))
      with (sum_f_R0 (fun j : nat => r ^ j) t + r ^ (S t)).
    rewrite IH.
    change (r ^ (S (S t))) with (r * r ^ (S t)).
    field. exact Hz.
Qed.

(* 有限等比和 ≤ 1/(1−r)（0 ≤ r < 1） *)
Lemma geom_sum_bound : forall (r : R) (t : nat), 0 <= r -> r < 1 ->
  sum_f_R0 (fun j : nat => r ^ j) t <= / (1 - r).
Proof.
  intros r t Hr0 Hr1.
  assert (Hz : r <> 1) by (apply Rlt_not_eq; exact Hr1).
  assert (Hd : 0 < 1 - r) by lra.
  rewrite (geom_sum_closed r t Hz).
  assert (Hpow : 0 <= r ^ (S t)) by (apply pow_nonneg; exact Hr0).
  rewrite <- (Rmult_1_l (/ (1 - r))).
  apply Rmult_le_compat_r.
  - apply Rlt_le. apply Rinv_0_lt_compat. exact Hd.
  - lra.
Qed.

(* 有限尾和 ≤ t_{N+1}·(等比部分和) *)
Lemma tail_sum_geom : forall x N t, 0 <= x -> x < INR (N + 2) ->
  sum_f_R0 (fun j : nat => x ^ (N + 1 + j) / INR (fact (N + 1 + j))) t
  <= (x ^ (N + 1) / INR (fact (N + 1))) *
     sum_f_R0 (fun j : nat => (x / INR (N + 2)) ^ j) t.
Proof.
  intros x N t Hx Hxl.
  induction t as [| t IH].
  - simpl. rewrite Nat.add_0_r. rewrite Rmult_1_r.
    apply Rle_refl.
  - change (sum_f_R0 (fun j : nat => x ^ (N + 1 + j) / INR (fact (N + 1 + j)))
             (S t))
      with (sum_f_R0 (fun j : nat => x ^ (N + 1 + j) / INR (fact (N + 1 + j))) t
            + x ^ (N + 1 + S t) / INR (fact (N + 1 + S t))).
    change (sum_f_R0 (fun j : nat => (x / INR (N + 2)) ^ j) (S t))
      with (sum_f_R0 (fun j : nat => (x / INR (N + 2)) ^ j) t
            + (x / INR (N + 2)) ^ S t).
    rewrite Rmult_plus_distr_l.
    apply Rplus_le_compat.
    + exact IH.
    + apply tail_term_le; assumption.
Qed.

(* 有限尾和 ≤ tail_cap（几何封顶） *)
Lemma tail_le_cap : forall x N t, 0 <= x -> x < INR (N + 2) ->
  sum_f_R0 (fun j : nat => x ^ (N + 1 + j) / INR (fact (N + 1 + j))) t
  <= tail_cap x N.
Proof.
  intros x N t Hx Hxl.
  apply Rle_trans with
    ((x ^ (N + 1) / INR (fact (N + 1))) * / (1 - x / INR (N + 2))).
  - apply Rle_trans with
      ((x ^ (N + 1) / INR (fact (N + 1))) *
       sum_f_R0 (fun j : nat => (x / INR (N + 2)) ^ j) t).
    + apply tail_sum_geom; assumption.
    + apply Rmult_le_compat_l.
      * apply term_div_nonneg. exact Hx.
      * apply geom_sum_bound; [ apply r_nonneg; exact Hx | ].
        apply r_lt_1. exact Hxl.
  - unfold tail_cap. reflexivity.
Qed.

(* 部分和从 N+1 起的分解：partial x (N+1+t) = partial x N + 有限尾和 *)
Lemma partial_plus : forall x N t,
  partial x (N + 1 + t) =
  partial x N + sum_f_R0 (fun j : nat => x ^ (N + 1 + j) / INR (fact (N + 1 + j))) t.
Proof.
  intros x N t.
  induction t as [| t IH].
  - assert (He0 : (N + 1 + 0)%nat = (N + 1)%nat) by lia. rewrite He0.
    assert (He1 : (N + 1)%nat = S N) by lia. rewrite He1.
    change (sum_f_R0 (fun j : nat => x ^ (S N + j) / INR (fact (S N + j))) 0)
      with (x ^ (S N + 0) / INR (fact (S N + 0))).
    rewrite Nat.add_0_r.
    reflexivity.
  - assert (He : (N + 1 + S t)%nat = S (N + 1 + t)) by lia.
    rewrite He.
    change (partial x (S (N + 1 + t)))
      with (partial x (N + 1 + t)
            + x ^ (S (N + 1 + t)) / INR (fact (S (N + 1 + t)))).
    rewrite IH.
    rewrite <- He.
    change (sum_f_R0 (fun j : nat => x ^ (N + 1 + j) / INR (fact (N + 1 + j))) (S t))
      with (sum_f_R0 (fun j : nat => x ^ (N + 1 + j) / INR (fact (N + 1 + j))) t
            + x ^ (N + 1 + S t) / INR (fact (N + 1 + S t))).
    ring.
Qed.

(* exp 上界主引理：epsilon 证明（部分和收敛到 exp + 尾项几何封顶） *)
Lemma exp_le_partial_tail : forall x N,
  0 <= x -> x < INR (N + 2) ->
  exp x <= partial x N + tail_cap x N.
Proof.
  intros x N Hx Hxl.
  apply le_eps_char. intros eps Hep.
  assert (He2 : 0 < eps / 2) by lra.
  assert (Hinf : infinite_sum (fun k : nat => / INR (fact k) * x ^ k) (exp x)).
  { exact (exp_is_sum x). }
  destruct (Hinf (eps / 2) He2) as [M HM].
  set (n := Nat.max M (N + 1)).
  assert (HnM : (M <= n)%nat) by (unfold n; lia).
  assert (HnN : (N + 1 <= n)%nat) by (unfold n; lia).
  (* 部分和逼近 exp：exp < partial x n + eps/2 *)
  assert (Hsn : exp x < partial x n + eps / 2).
  { assert (Hsum_eq : sum_f_R0
              (fun k : nat => / INR (fact k) * x ^ k) n = partial x n).
    { symmetry. apply partial_eq_sum. }
    assert (Habs : Rabs (partial x n - exp x) < eps / 2).
    { unfold R_dist in HM.
      specialize (HM n HnM).
      rewrite Hsum_eq in HM.
      exact HM. }
    assert (Hle : exp x - partial x n <= Rabs (partial x n - exp x)).
    { apply Rle_trans with (Rabs (exp x - partial x n)).
      - apply Rle_abs.
      - rewrite (Rabs_minus_sym (exp x) (partial x n)). apply Rle_refl. }
    lra.
  }
  (* 部分和 ≤ partial N + tail_cap（用 n = N+1+t 分解） *)
  assert (Hcap : partial x n <= partial x N + tail_cap x N).
  { assert (Ht : n = (N + 1 + (n - (N + 1)))%nat) by lia.
    rewrite Ht.
    rewrite (partial_plus x N (n - (N + 1))).
    apply Rplus_le_compat_l.
    apply tail_le_cap; assumption.
  }
  apply Rlt_le_trans with (partial x N + tail_cap x N + eps / 2).
  - apply Rlt_le_trans with (partial x n + eps / 2).
    + exact Hsn.
    + apply Rplus_le_compat_r. exact Hcap.
  - lra.
Qed.

(* ======================================================================
   §C  数值结算（不用一元 nat 直接展开阶乘）
   目标归约：ln(10^15) = 15·ln 10，故只需
     e^{1151/500} < 10 < e^{2303/1000}      （1151/500 = 2.302，2303/1000 = 2.303）
   因为 6595/191 < 15·(1151/500) 且 15·(2303/1000) < 65950/1909（有理比较）。
   N = 11 时部分和/余项分母出现 INR (fact 1..12)：这些用二进制 Z 阶乘
   factZ 求值（rewrite 成 IZR 字面量），最终闭式比较由 nra 结算。
   ====================================================================== *)

(* 二进制 Z 阶乘：只在 Z 上计算，规避一元 nat 爆炸 *)
Fixpoint factZ (n : nat) : Z :=
  match n with
  | O => 1%Z
  | S m => (Z.of_nat (S m)) * factZ m
  end.

(* INR(fact n) = IZR(factZ n)：符号归纳，不求值 *)
Lemma INR_fact_factZ : forall n, INR (fact n) = IZR (factZ n).
Proof.
  induction n as [| n IH]; simpl.
  - reflexivity.
  - change (INR ((S n) * fact n) = IZR (Z.of_nat (S n) * factZ n)).
    rewrite mult_INR, IH, INR_IZR_INZ.
    rewrite <- mult_IZR.
    reflexivity.
Qed.

Lemma factZ_1 : factZ 1 = 1%Z. Proof. reflexivity. Qed.
Lemma factZ_2 : factZ 2 = 2%Z. Proof. reflexivity. Qed.
Lemma factZ_3 : factZ 3 = 6%Z. Proof. reflexivity. Qed.
Lemma factZ_4 : factZ 4 = 24%Z. Proof. reflexivity. Qed.
Lemma factZ_5 : factZ 5 = 120%Z. Proof. reflexivity. Qed.
Lemma factZ_6 : factZ 6 = 720%Z. Proof. reflexivity. Qed.
Lemma factZ_7 : factZ 7 = 5040%Z. Proof. reflexivity. Qed.
Lemma factZ_8 : factZ 8 = 40320%Z. Proof. reflexivity. Qed.
Lemma factZ_9 : factZ 9 = 362880%Z. Proof. reflexivity. Qed.
Lemma factZ_10 : factZ 10 = 3628800%Z. Proof. reflexivity. Qed.
Lemma factZ_11 : factZ 11 = 39916800%Z. Proof. reflexivity. Qed.
Lemma factZ_12 : factZ 12 = 479001600%Z. Proof. reflexivity. Qed.

Lemma Zofnat_13 : Z.of_nat 13 = 13%Z. Proof. reflexivity. Qed.

(* 把 INR(fact k) 换成 IZR 数字字面量（其余 INR 走 INR_IZR_INZ） *)
Ltac strip_facts :=
  cbv [partial];
  unfold tail_cap;
  cbv [Nat.add];
  repeat rewrite INR_fact_factZ;
  repeat (first [ rewrite factZ_12 | rewrite factZ_11 | rewrite factZ_10
                | rewrite factZ_9 | rewrite factZ_8 | rewrite factZ_7
                | rewrite factZ_6 | rewrite factZ_5 | rewrite factZ_4
                | rewrite factZ_3 | rewrite factZ_2 | rewrite factZ_1 ]);
  rewrite ?INR_IZR_INZ;
  try replace (Z.of_nat 13) with (13%Z) by reflexivity.

(* 数值上界：S_11(2.302) + 余项 < 10 *)
Lemma upper_partial_cap_lt_10 :
  partial (1151 / 500) 11 + tail_cap (1151 / 500) 11 < 10.
Proof.
  strip_facts.
  nra.
Qed.

(* 数值下界：10 < S_11(2.303) *)
Lemma lower_partial_gt_10 : 10 < partial (2303 / 1000) 11.
Proof.
  strip_facts.
  nra.
Qed.

(* ======================================================================
   §D  exp / ln 应用与两个主定理
   ====================================================================== *)

(* e^{1151/500} < 10（经 §B 的上界引理 + §C 数值结算） *)
Lemma exp_lt_10_at_2302 : exp (1151 / 500) < 10.
Proof.
  apply Rle_lt_trans with (partial (1151 / 500) 11 + tail_cap (1151 / 500) 11).
  - apply exp_le_partial_tail.
    + nra.
    + change (1151 / 500 < INR 13).
      rewrite INR_IZR_INZ. change (Z.of_nat 13) with (13%Z). nra.
  - exact upper_partial_cap_lt_10.
Qed.

(* 10 < e^{2303/1000}（§A 的下界引理 + §C 数值结算） *)
Lemma exp_10_lt_at_2303 : 10 < exp (2303 / 1000).
Proof.
  apply Rlt_le_trans with (partial (2303 / 1000) 11).
  - exact lower_partial_gt_10.
  - apply exp_ge_partial. nra.
Qed.

(* 两个有理点的正性 *)
Lemma lt_0_10 : 0 < 10. Proof. nra. Qed.

(* ln 10 > 2.302（= 1151/500） *)
Lemma ln_10_gt_2302 : 1151 / 500 < ln 10.
Proof.
  apply exp_lt_inv.
  rewrite (exp_ln 10 lt_0_10).
  exact exp_lt_10_at_2302.
Qed.

(* ln 10 < 2.303（= 2303/1000） *)
Lemma ln_10_lt_2303 : ln 10 < 2303 / 1000.
Proof.
  apply exp_lt_inv.
  rewrite (exp_ln 10 lt_0_10).
  exact exp_10_lt_at_2303.
Qed.

(* ln 10 > 0 *)
Lemma ln_10_pos : 0 < ln 10.
Proof.
  assert (H : ln 1 < ln 10) by (apply ln_increasing; nra).
  rewrite ln_1 in H. exact H.
Qed.

(* ln(10^15) = 15·ln 10 *)
Lemma ln_10_pow15 : ln (10 ^ 15) = 15 * ln 10.
Proof.
  rewrite (ln_pow 10 lt_0_10 15).
  rewrite INR_IZR_INZ.
  change (Z.of_nat 15) with (15%Z).
  reflexivity.
Qed.

(* ln(10^15) > 6595/191 *)
Lemma ln_15_gt_6595_191 : 6595 / 191 < ln (10 ^ 15).
Proof.
  rewrite ln_10_pow15.
  assert (H1 : 6595 / 191 < 15 * (1151 / 500)) by nra.
  assert (H2 : 15 * (1151 / 500) < 15 * ln 10)
    by (apply Rmult_lt_compat_l; [ nra | exact ln_10_gt_2302 ]).
  nra.
Qed.

(* ln(10^15) < 65950/1909 *)
Lemma ln_15_lt_65950_1909 : ln (10 ^ 15) < 65950 / 1909.
Proof.
  rewrite ln_10_pow15.
  assert (H1 : 15 * (2303 / 1000) < 65950 / 1909) by nra.
  assert (H2 : 15 * ln 10 < 15 * (2303 / 1000))
    by (apply Rmult_lt_compat_l; [ nra | exact ln_10_lt_2303 ]).
  nra.
Qed.

(* 除法形式辅助：a/b < c/d ⟸ a·d < c·b（b, d > 0） *)
Lemma lt_frac_r : forall a b c d : R, 0 < b -> 0 < d ->
  a * d < c * b -> a / b < c / d.
Proof.
  intros a b c d Hb Hd Hm.
  apply Rmult_lt_reg_l with (r := b * d).
  - apply Rmult_lt_0_compat; assumption.
  - field_simplify; try lra; try (apply Rgt_not_eq; lra).
Qed.

(* 主定理 1：τ < 38.20（1319/ln(10^15) < 191/5） *)
Theorem tau_upper_bound : 1319 / ln (10 ^ 15) < 191 / 5.
Proof.
  assert (HL : 0 < ln (10 ^ 15)).
  { assert (Hp10 : 0 < ln 10) by exact ln_10_pos.
    rewrite ln_10_pow15. nra. }
  apply (lt_frac_r 1319 (ln (10 ^ 15)) 191 5).
  - exact HL.
  - nra.
  - pose proof ln_15_gt_6595_191. nra.
Qed.

(* 主定理 2：38.18 < τ（1909/50 < 1319/ln(10^15)） *)
Theorem tau_lower_bound : 1909 / 50 < 1319 / ln (10 ^ 15).
Proof.
  assert (HL : 0 < ln (10 ^ 15)).
  { assert (Hp10 : 0 < ln 10) by exact ln_10_pos.
    rewrite ln_10_pow15. nra. }
  apply (lt_frac_r 1909 50 1319 (ln (10 ^ 15))).
  - nra.
  - exact HL.
  - pose proof ln_15_lt_65950_1909. nra.
Qed.

(* ======================================================================
   B3b：44 分钟外推比值的上下界（候选 d；2026-09-05）
   ======================================================================
   ratio44 := exp(1320·ln(10^15)/1319)，即 E_req(2640)/E_req(1320)。
   机器结论：10^15 < ratio44 < 10^16（粗界；精细十进制界
   1.025×10^15 < ratio44 < 1.03×10^15 见 B3c，2026-09-05 追加——
   "≈1.03"从注释升级为机器句）。                                          *)

Lemma pow15_pos : 0 < 10 ^ 15.
Proof. apply pow_lt. nra. Qed.

Lemma pow16_pos : 0 < 10 ^ 16.
Proof. apply pow_lt. nra. Qed.

Lemma ln_10_pow16 : ln (10 ^ 16) = 16 * ln 10.
Proof.
  rewrite (ln_pow 10 lt_0_10 16).
  rewrite INR_IZR_INZ.
  change (Z.of_nat 16) with (16%Z).
  reflexivity.
Qed.

Theorem ratio_44_lower : 10 ^ 15 < exp (1320 * ln (10 ^ 15) / 1319).
Proof.
  assert (Hv : 0 < 10 ^ 15) by (apply pow_lt; nra).
  set (v := 10 ^ 15) at 1.
  rewrite <- (exp_ln v Hv).
  apply exp_increasing.
  change (ln v < 1320 * ln v / 1319).
  assert (HLv : 0 < ln v).
  { unfold v. rewrite ln_10_pow15. pose proof ln_10_pos. nra. }
  apply (Rmult_lt_reg_l 1319 (ln v) (1320 * ln v / 1319)); [ nra | ].
  assert (Hmul : 1319 * (1320 * ln v / 1319) = 1320 * ln v).
  { field. }
  rewrite Hmul.
  rewrite (Rmult_comm 1319 (ln v)).
  rewrite (Rmult_comm 1320 (ln v)).
  assert (Hlt : 1319 < 1320) by nra.
  exact (Rmult_lt_compat_l (ln v) 1319 1320 HLv Hlt).
Qed.

Theorem ratio_44_upper : exp (1320 * ln (10 ^ 15) / 1319) < 10 ^ 16.
Proof.
  assert (Hv16 : 0 < 10 ^ 16) by (apply pow_lt; nra).
  rewrite <- (exp_ln (10 ^ 16) Hv16).
  apply exp_increasing.
  pose proof ln_10_pos as Hp10.
  rewrite ln_10_pow16. rewrite ln_10_pow15.
  assert (Hnum : 1320 * 15 < 1319 * 16) by nra.
  apply (Rmult_lt_reg_l 1319 (1320 * (15 * ln 10) / 1319) (16 * ln 10)); [ nra | ].
  assert (Hmulu : 1319 * (1320 * (15 * ln 10) / 1319) = 1320 * (15 * ln 10)).
  { field. }
  rewrite Hmulu.
  rewrite <- (Rmult_assoc 1320 15 (ln 10)).
  rewrite <- (Rmult_assoc 1319 16 (ln 10)).
  exact (Rmult_lt_compat_r (ln 10) (1320 * 15) (1319 * 16) Hp10 Hnum).
Qed.

(* ======================================================================
   B3c：ratio44 的精细十进制界（2026-09-05 追加）
   ======================================================================
   把 B3b 注释里的"数值 ≈1.03×10^15（人工估算）"升级为机器句：
      1.025×10^15 < ratio44 < 1.03×10^15
   路线（复用 B3 级数基础设施）：
     上界：ratio44 < 1.03×10^15 ⟸ exp(1320·ln r/1319) < exp(ln(103/100)
           + 15·ln 10) ⟸ 15·ln 10 < 1319·ln(103/100)，其中
           ln(103/100) > 131/5000 来自 exp_lt_inv + exp 级数上界
           exp(131/5000) < 103/100（N=2 + 余项几何封顶）；
     下界：ratio44 > 1.025×10^15 ⟸ ln(1025/1000) < 1/40 < 15·ln 10/1319，
           其中 ln(1025/1000) < 1/40 来自 1025/1000 < exp(1/40)
           （部分和下界，N=2）。
   坑记录：strip_facts 后 tail_cap 分母出现 Z.of_nat (2+2)，须先
   replace (Z.of_nat 4) with (4%Z) 再 nra（strip_facts 原只处理 13）。*)

(* e^{131/5000} < 103/100：exp 级数 N=2 部分和 + 余项几何封顶 *)
Lemma exp_131_5000_lt_103_100 : exp (131 / 5000) < 103 / 100.
Proof.
  apply Rle_lt_trans with (partial (131 / 5000) 2 + tail_cap (131 / 5000) 2).
  - apply exp_le_partial_tail.
    + nra.
    + change (131 / 5000 < INR (2 + 2)).
      rewrite INR_IZR_INZ. simpl. nra.
  - strip_facts.
    replace (Z.of_nat 4) with (4%Z) by reflexivity.
    nra.
Qed.

(* 上界：ratio44 < 1.03×10^15 *)
Theorem ratio_44_lt_103_pct_15 :
  exp (1320 * ln (10 ^ 15) / 1319) < 103 / 100 * (10 ^ 15).
Proof.
  assert (Hp : 0 < 103 / 100 * 10 ^ 15).
  { apply Rmult_lt_0_compat; [ nra | exact pow15_pos ]. }
  rewrite <- (exp_ln (103 / 100 * 10 ^ 15) Hp).
  apply exp_increasing.
  assert (Hml : ln (103 / 100 * 10 ^ 15) = ln (103 / 100) + ln (10 ^ 15)).
  { apply ln_mult; [ nra | exact pow15_pos ]. }
  rewrite Hml. rewrite ln_10_pow15.
  pose proof ln_10_pos as HL.
  assert (Hbig : 15 * ln 10 < 1319 * ln (103 / 100)).
  { (* ln(103/100) > 131/5000：exp_lt_inv 反转 + e^{131/5000} 上界 *)
    assert (Hln : 131 / 5000 < ln (103 / 100)).
    { apply exp_lt_inv.
      rewrite (exp_ln (103 / 100)).
      - exact exp_131_5000_lt_103_100.
      - nra. }
    (* 15·ln 10 < 15·2.303 < 1319·(131/5000) *)
    assert (H1 : 15 * ln 10 < 34545 / 1000).
    { assert (Ht : 15 * ln 10 < 15 * (2303 / 1000))
        by (apply Rmult_lt_compat_l; [ nra | exact ln_10_lt_2303 ]).
      nra. }
    assert (H2 : 34545 / 1000 < 1319 * (131 / 5000)) by nra.
    apply Rlt_trans with (1319 * (131 / 5000)).
    - apply Rlt_trans with (34545 / 1000).
      + exact H1.
      + exact H2.
    - apply Rmult_lt_compat_l; [ nra | exact Hln ].
  }
  (* 目标：1320·(15·ln 10)/1319 < ln(103/100) + 15·ln 10 *)
  apply (Rmult_lt_reg_l 1319 (1320 * (15 * ln 10) / 1319)
                         (ln (103 / 100) + 15 * ln 10)); [ nra | ].
  field_simplify.
  nra.
Qed.

(* 下界：e^{1/40} > 1.025（部分和 N=2 下界） *)
Lemma exp_1_40_gt_1025_1000 : 1025 / 1000 < exp (1 / 40).
Proof.
  apply Rlt_le_trans with (partial (1 / 40) 2).
  - strip_facts.
    replace (Z.of_nat 4) with (4%Z) by reflexivity.  (* partial 2 处无尾项 *)
    nra.
  - apply exp_ge_partial. nra.
Qed.

Lemma ln_1025_1000_lt_1_40 : ln (1025 / 1000) < 1 / 40.
Proof.
  apply exp_lt_inv.
  rewrite (exp_ln (1025 / 1000)).
  - exact exp_1_40_gt_1025_1000.
  - nra.
Qed.

(* 下界：ratio44 > 1.025×10^15 *)
Theorem ratio_44_gt_1025_pct_15 :
  1025 / 1000 * (10 ^ 15) < exp (1320 * ln (10 ^ 15) / 1319).
Proof.
  assert (Hp : 0 < 1025 / 1000 * 10 ^ 15).
  { apply Rmult_lt_0_compat; [ nra | exact pow15_pos ]. }
  rewrite <- (exp_ln (1025 / 1000 * 10 ^ 15) Hp).
  apply exp_increasing.
  assert (Hml : ln (1025 / 1000 * 10 ^ 15) = ln (1025 / 1000) + ln (10 ^ 15)).
  { apply ln_mult; [ nra | exact pow15_pos ]. }
  rewrite Hml. rewrite ln_10_pow15.
  assert (HL : 0 < ln 10) by exact ln_10_pos.
  (* ln(1.025) < 1/40 < (15·ln 10)/1319 *)
  assert (Hdiv : 1 / 40 < 15 * ln 10 / 1319).
  { apply Rmult_lt_reg_l with 1319; [ nra | ].
    field_simplify.
    pose proof ln_10_gt_2302 as Hg.
    nra. }
  apply Rlt_trans with (1 / 40 + 15 * ln 10).
  - apply Rplus_lt_compat_r. exact ln_1025_1000_lt_1_40.
  - nra.
Qed.
