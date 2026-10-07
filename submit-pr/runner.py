# -*- coding: utf-8 -*-
"""
runner.py — 提交编排: 单批提交循环 + 总流程 run_plan + 进度事件。

run_plan(opts, report, should_stop) 是 CLI 与 GUI 共用的入口。
    opts 关键字: sources / delete / dest / title / body / branch_prefix /
                 max_lines / max_files / max_file_lines / poll_timeout /
                 poll_interval / max_retries / workers / dry_run / strict
    report(ev, **kw) 进度事件:
        log       text                 自由文本（计划/跳过/验证等）
        plan      total steps          任务单元总数与 PR 总步数确定
        unit      idx label steps      任务单元登记
        step      idx k pr url         单元内某一步的 PR 已创建
        unit_done idx label status     merged / timeout / failed
        done      ok                   全部结束
    should_stop() 返回 True 时不再开始剩余任务单元。
"""

from __future__ import annotations

import json
import shutil
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from files import (base_content, build_delete_chain, build_step_text,
                   collect_sources, plan_file_ops, verify_on_main)
from gitops import (build_worktree_commit, create_pr, drop_worktree, gh,
                    get_open_pr, last_comment, latest_main_sha, pr_state,
                    push_branch, run as git_run, wait_for_branch)
from rules import MAX_CHANGED_FILES, MAX_CHANGED_LINES, MAX_FILE_LINES
from rules import counted_lines, gate_path_problems, is_binary, make_batches

NUDGE_AFTER = 120  # PR 打开后超过这么多秒仍无进展，就强推促发重新检查


def submit_batch(repo_root: str, target: str, fork: str, base_sha: str,
                 branch: str, batch: list[dict], dest: str, title: str,
                 body: str, poll_timeout: int, poll_interval: int,
                 max_retries: int, report=lambda ev, **kw: None) -> dict:
    """提交一批文件并等待合并。各批文件路径不相交，可并发提交/合并。

    机器人有两种情况需要重新触发检查（重试上限内各换一次新基底强推，
    synchronize 事件会让它重新跑检查后合并）:
      - PR 因 base 变动合并冲突被关 / 被评论要求 rebase；
      - 合并请求与别的 PR 的合并发生竞态时，机器人会静默退出且不再重试，
        PR 就永远停在 OPEN —— 只能靠强推促发。
    """
    pr: int | None = None
    base = base_sha
    attempt = 0
    while attempt < max_retries:
        attempt += 1
        wt = build_worktree_commit(repo_root, base, dest, batch, title)
        try:
            push_branch(fork, branch, wt, force=pr is not None)
        finally:
            drop_worktree(repo_root, wt)
        last_push = time.time()

        if pr is None:
            pr = get_open_pr(target, fork, branch)
            if pr is None:
                wait_for_branch(fork, branch)
                url = create_pr(target, fork, branch, title, body)
                pr = int(url.rstrip("/").split("/")[-1])
                report("log", text=f"[{branch}] PR #{pr}: {url}")

        deadline = time.time() + poll_timeout
        while True:
            st = pr_state(target, pr)
            if st == "MERGED":
                return {"pr": pr, "result": "merged",
                        "url": f"https://github.com/{target}/pull/{pr}"}
            if st == "CLOSED":
                comment = last_comment(target, pr)
                if "conflict" in comment.lower() and attempt < max_retries:
                    report("log", text=f"[{branch}] 与 main 冲突，换新基底重试 "
                                       f"({attempt}/{max_retries})…")
                    base = latest_main_sha(target, repo_root)
                    break
                raise RuntimeError(f"PR #{pr} 被机器人关闭:\n{comment or '(无评论)'}")
            if attempt < max_retries and time.time() - last_push > NUDGE_AFTER:
                report("log", text=f"[{branch}] {NUDGE_AFTER}s 仍无进展"
                                   f"（可能与其他 PR 的合并竞态），换新基底强推"
                                   f" ({attempt}/{max_retries})…")
                base = latest_main_sha(target, repo_root)
                break
            if time.time() >= deadline:
                return {"pr": pr, "result": "timeout",
                        "url": f"https://github.com/{target}/pull/{pr}"}
            time.sleep(poll_interval)
    raise RuntimeError(f"PR #{pr} 重试 {max_retries} 次仍未合并")


def _tmp_dir(repo_root: str) -> Path:
    return (Path(repo_root) / ".git" / "submit-pr-tmp"
            / time.strftime("%Y%m%d-%H%M%S"))


def run_plan(opts: dict, report=lambda ev, **kw: None,
             should_stop=lambda: False) -> dict:
    """完整提交流程。返回 {"ok": bool}。"""
    max_lines = min(int(opts.get("max_lines", MAX_CHANGED_LINES)), MAX_CHANGED_LINES)
    max_files = min(int(opts.get("max_files", MAX_CHANGED_FILES)), MAX_CHANGED_FILES)
    max_file_lines = min(int(opts.get("max_file_lines", MAX_FILE_LINES)),
                         MAX_FILE_LINES)
    cap = min(max_file_lines, max_lines)
    dest = (opts.get("dest") or "").strip("/")
    dest = dest if dest not in ("", ".") else ""
    dest_label = dest or "root"
    if dest and gate_path_problems(dest + "/x"):
        raise ValueError(f"目标目录 {dest} 命中受保护规则。")

    repo_root = git_run(["git", "rev-parse", "--show-toplevel"]).strip()
    info = json.loads(gh("repo", "view", "--json", "nameWithOwner,parent"))
    fork = opts.get("fork") or info["nameWithOwner"]
    parent_obj = info.get("parent")
    parent = (f"{parent_obj['owner']['login']}/{parent_obj['name']}"
              if parent_obj else None)
    target = opts.get("repo") or parent or fork
    report("log", text=f"上游仓库: {target}   分支推送目标: {fork}")

    delete = opts.get("delete")
    sources = collect_sources(opts.get("sources") or [])
    if delete and sources:
        raise ValueError("--delete 模式下不要同时传源文件/目录。")
    if not sources and not delete:
        raise ValueError("没有要提交的源文件/目录，也没有 --delete 目标。")

    base = latest_main_sha(target, repo_root)
    report("log", text=f"基于上游 main: {base[:10]}")

    tmp_dir = _tmp_dir(repo_root)
    accepted, skipped, chains = [], [], {}
    if delete:
        for p in delete:
            p = p.replace("\\", "/").strip("/")
            probs = gate_path_problems(p)
            if probs:
                skipped.append((p, "；".join(probs)))
                continue
            old = base_content(base, p, repo_root)
            if old is None:
                skipped.append((p, "上游 main 上不存在，无需删除"))
                continue
            chains[p] = {"kind": "delete", "steps": build_delete_chain(
                {"src": None, "rel": p, "path": p}, old, cap, str(tmp_dir))}
    else:
        for f in sources:
            f["path"] = f"{dest}/{f['rel']}" if dest else f["rel"]
            probs = gate_path_problems(f["path"])
            if probs:
                skipped.append((f["path"], "；".join(probs)))
            elif is_binary(Path(f["src"])):
                skipped.append((f["path"], "二进制内容（含 NUL 字节）"))
            else:
                accepted.append(f)
        accepted, sk, chains = plan_file_ops(
            accepted, base, dest, repo_root, cap, max_file_lines, str(tmp_dir))
        skipped += sk

    for path, why in skipped:
        report("log", text=f"  [跳过] {path} — {why}")
    if skipped and opts.get("strict"):
        raise ValueError("strict 模式下存在被跳过的文件，已中止。")
    if not accepted and not chains:
        report("log", text="没有需要提交的内容（可能全部与上游一致）。")
        return {"ok": True, "nothing": True}

    batches = make_batches(accepted, max_lines, max_files)
    kind_name = {"create": "分块链", "modify": "改写链", "delete": "删除链"}
    for i, b in enumerate(batches, 1):
        lines = sum(counted_lines(it) for it in b)
        report("log", text=f"  PR {i}/{len(batches)}: {len(b)} 个文件, {lines} 行"
                           f" — {', '.join(it['path'] for it in b)}")
    for path, ch in chains.items():
        total = sum(s["adds"] + s["dels"] for s in ch["steps"])
        report("log", text=f"  {kind_name[ch['kind']]} {path}: 共 {total} 行变更"
                           f" → {len(ch['steps'])} 个渐进 PR（同链串行，链间并发）")
    if opts.get("dry_run"):
        report("log", text="[dry-run] 未真正提交。")
        shutil.rmtree(tmp_dir, ignore_errors=True)
        return {"ok": True, "nothing": True}

    try:
        ok = _execute(opts, report, should_stop, target, fork, base, dest,
                      dest_label, batches, chains, cap, repo_root, tmp_dir,
                      max_lines, max_files, kind_name)
    finally:
        shutil.rmtree(tmp_dir, ignore_errors=True)
    return {"ok": ok}


def _execute(opts, report, should_stop, target, fork, base, dest, dest_label,
             batches, chains, cap, repo_root, tmp_dir, max_lines, max_files,
             kind_name) -> bool:
    ts = time.strftime("%Y%m%d-%H%M%S")
    prefix = opts.get("branch_prefix", "auto-pr")
    units: list[tuple[str, list, list[dict]]] = []  # (名称, 步骤, 验证条目)

    def pr_text(batch, i, n):
        title = opts.get("title") or f"{dest_label}: add {len(batch)} file(s)"
        if n > 1:
            title = f"{title} (part {i}/{n})"
        rows = "\n".join(f"| `{it['path']}` | {it['adds'] + it['dels']} |"
                         for it in batch)
        total = sum(counted_lines(it) for it in batch)
        body = opts.get("body") or (
            f"## Summary\n\nAdds {len(batch)} file(s) into `{dest_label}/`"
            + (f" — batch {i}/{n}, auto-split to respect the {max_lines}-line /"
               f" {max_files}-file limits of the auto-merge regulations."
               if n > 1 else ".")
            + f"\n\n| file | changed lines |\n|---|---|\n{rows}\n\n"
              f"**{total} counted lines** total. Pre-validated locally by "
              f"`submit-pr/submit_pr.py` against every gate rule.\n")
        return title, body

    for i, b in enumerate(batches, 1):
        title, body = pr_text(b, i, len(batches))
        units.append((f"PR {i}/{len(batches)}", [(f"{prefix}-{ts}-{i}",
                                                  title, body, b)], b))
    for path, ch in chains.items():
        j = len(units) + 1
        n = len(ch["steps"])
        steps, done = [], 0
        for k, c in enumerate(ch["steps"], 1):
            title, body = build_step_text(ch["kind"], c, k, n, cap, dest_label,
                                          opts.get("title"), opts.get("body"),
                                          done)
            done += c["adds"]
            steps.append((f"{prefix}-{ts}-{j}x{k}", title, body, [c]))
        units.append((f"{kind_name[ch['kind']]} {path}（{n} 块）", steps,
                      [ch["steps"][-1]]))

    workers = max(1, min(int(opts.get("workers") or len(units)), len(units), 10))
    report("plan", total=len(units), steps=sum(len(u[1]) for u in units))
    for j, (label, steps, _) in enumerate(units, 1):
        report("unit", idx=j, label=label, steps=len(steps))

    results: dict[int, list] = {}
    errors: dict[int, str] = {}

    def run_unit(j, steps):
        out = []
        for k, (branch, title, body, batch) in enumerate(steps, 1):
            # 链的每一步都必须基于包含前一步的最新 main；单步单元用初始 base
            b2 = latest_main_sha(target, repo_root) if len(steps) > 1 else base
            r = submit_batch(repo_root, target, fork, b2, branch, batch, dest,
                             title, body, int(opts.get("poll_timeout", 300)),
                             int(opts.get("poll_interval", 15)),
                             int(opts.get("max_retries", 3)), report)
            out.append(r)
            report("step", idx=j, k=k, pr=r["pr"], url=r["url"])
            if len(steps) > 1 and r["result"] != "merged":
                raise RuntimeError(f"链第 {k}/{len(steps)} 步未合并"
                                   f"（{r['result']}），中止后续步骤")
        return out

    def job(j, steps):
        if should_stop():
            errors[j] = "已取消"
            report("unit_done", idx=j, label=units[j - 1][0], status="cancelled")
            return
        try:
            res = run_unit(j, steps)
            results[j] = res
            report("unit_done", idx=j, label=units[j - 1][0],
                   status=res[-1]["result"])
        except Exception as e:
            errors[j] = str(e)
            report("unit_done", idx=j, label=units[j - 1][0], status="failed")

    try:
        with ThreadPoolExecutor(max_workers=workers) as ex:
            futs = [ex.submit(job, j, u[1]) for j, u in enumerate(units, 1)]
            for f in futs:
                f.result()
    except KeyboardInterrupt:
        raise
    finally:
        shutil.rmtree(tmp_dir, ignore_errors=True)

    ok = not errors and all(r["result"] == "merged"
                            for rs in results.values() for r in rs)
    # 最终验证: 普通批验全部条目，链只看末步（创建/改写=原文件，删除=应不存在）
    submitted = [it for j, (_, _, items) in enumerate(units, 1)
                 if j not in errors for it in items]
    problems = verify_on_main(target, repo_root, submitted, report)
    if problems:
        ok = False
        for p in problems:
            report("log", text=f"  [!] {p}")
    report("done", ok=ok)
    return ok
