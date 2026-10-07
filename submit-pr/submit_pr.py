#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
submit_pr.py — 自动向 any-pr 上游仓库提交 PR（保证每个 PR 都能被 auto-merge 机器人顺利合并）

用法（在本仓库克隆内运行）:
    python submit-pr/submit_pr.py <源文件或目录...> --dest <仓库内目标目录> [选项]

示例:
    # 把某个文件夹里的所有文件提交到仓库的 music-api/ 目录
    python submit-pr/submit_pr.py "Z:\\playlist.php等5项文件" --dest music-api

    # 只预演，不真正提交
    python submit-pr/submit_pr.py some_dir --dest music-api --dry-run

    # 删除仓库内文件（超大文本自动渐进截断后删除）
    python submit-pr/submit_pr.py --delete some/path.txt

工作原理（详见 rules.py / gitops.py）:
    上游 any-pr/any-pr 的 .github/workflows/auto-merge.yml 会在 PR 打开时自动
    检查并合并满足规则的 PR。本脚本在本地完整复刻其全部规则（rules.py），把
    超限的提交自动拆分成多个 PR，因此提交出的每个 PR 必定能通过机器人检查。

    行数统计口径与 GitHub 机器人完全一致: 在临时 worktree 中把文件暂存后用
    `git diff --cached --numstat` 得到每个文件的真实 additions+deletions。

    流程: fetch 上游 main 最新 commit → 在临时 worktree 里基于它建分支并提交
    → push 到自己 fork → 所有批次并发向上游开 PR（默认全部同时，--workers 可限）
    → 轮询等待合并 → 若因冲突/竞态停滞，自动换新基底重建分支强推重试。各批文件
    路径不相交，因此无论机器人以何种顺序合并都不会冲突。新建超大文件（超过单
    文件行数上限）自动拆成"渐进前缀"分块链串行合并，链间仍并发。全程不直接
    push 任何 main 分支。
"""

from __future__ import annotations

import argparse
import json
import shutil
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from files import (base_content, build_delete_chain, build_step_text,
                   collect_sources, plan_file_ops)
from gitops import (gh, latest_main_sha, run as git_run, run_retry,
                    submit_batch)
from rules import MAX_CHANGED_FILES, MAX_CHANGED_LINES, MAX_FILE_LINES
from rules import counted_lines, gate_path_problems, is_binary, make_batches


def main() -> None:
    ap = argparse.ArgumentParser(
        description="自动向 any-pr 上游提交能被 auto-merge 机器人顺利合并的 PR",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    ap.add_argument("sources", nargs="*", help="要提交的源文件或目录（可多个）")
    ap.add_argument("--delete", nargs="+", metavar="REPO_PATH", default=None,
                    help="删除仓库内文件（相对仓库根的路径，可多个；"
                         "超大文本自动渐进截断后删除，与提交模式二选一）")
    ap.add_argument("--dest", default=".", help="仓库内目标目录（默认仓库根）")
    ap.add_argument("--repo", default=None, help="上游仓库（默认自动探测 fork 的父仓库）")
    ap.add_argument("--fork", default=None, help="分支所在的 fork（默认当前仓库）")
    ap.add_argument("--title", default=None, help="PR 标题（默认自动生成）")
    ap.add_argument("--body", default=None, help="PR 描述（默认自动生成）")
    ap.add_argument("--branch-prefix", default="auto-pr", help="分支名前缀")
    ap.add_argument("--max-lines", type=int, default=MAX_CHANGED_LINES,
                    help="单 PR 行数上限（只会收紧到机器人上限）")
    ap.add_argument("--max-files", type=int, default=MAX_CHANGED_FILES,
                    help="单 PR 文件数上限（只会收紧到机器人上限）")
    ap.add_argument("--max-file-lines", type=int, default=MAX_FILE_LINES,
                    help="单文件行数上限（只会收紧到机器人上限）")
    ap.add_argument("--poll-timeout", type=int, default=300, help="等待合并的超时秒数")
    ap.add_argument("--poll-interval", type=int, default=15, help="轮询间隔秒数")
    ap.add_argument("--max-retries", type=int, default=3, help="冲突重试次数")
    ap.add_argument("--workers", type=int, default=0,
                    help="并发提交的 PR 数上限（默认 0 = 所有批次同时提交，"
                         "路径不相交所以合并顺序无关；--workers 1 即串行）")
    ap.add_argument("--strict", action="store_true",
                    help="有文件因规则被跳过时直接中止，而不是跳过继续")
    ap.add_argument("--dry-run", action="store_true", help="只打印计划，不真正提交")
    args = ap.parse_args()

    # 上限只会比机器人更严，不会更松
    max_lines = min(args.max_lines, MAX_CHANGED_LINES)
    max_files = min(args.max_files, MAX_CHANGED_FILES)
    max_file_lines = min(args.max_file_lines, MAX_FILE_LINES)

    dest = args.dest.strip("/") if args.dest.strip("/") not in ("", ".") else ""
    if dest and gate_path_problems(dest + "/x"):
        sys.exit(f"错误: 目标目录 {dest} 命中受保护规则。")

    repo_root = git_run(["git", "rev-parse", "--show-toplevel"]).strip()
    info = json.loads(gh("repo", "view", "--json", "nameWithOwner,parent"))
    fork = args.fork or info["nameWithOwner"]
    parent_obj = info.get("parent")
    parent = (f"{parent_obj['owner']['login']}/{parent_obj['name']}"
              if parent_obj else None)
    target = args.repo or parent or fork
    print(f"上游仓库: {target}   分支推送目标: {fork}")

    if args.delete and args.sources:
        sys.exit("错误: --delete 模式下不要同时传源文件/目录。")
    files = collect_sources(args.sources or [])
    if not files and not args.delete:
        sys.exit("错误: 请提供要提交的源文件/目录，或用 --delete 指定要删除的仓库内文件。")

    base = latest_main_sha(target, repo_root)
    print(f"基于上游 main: {base[:10]}")

    # 第一步: 路径/内容规则预检（机器人所有基于文件名与内容的规则）
    accepted: list[dict] = []
    skipped: list[tuple[str, str]] = []
    for f in files:
        f["path"] = f"{dest}/{f['rel']}" if dest else f["rel"]
        probs = gate_path_problems(f["path"])
        if probs:
            skipped.append((f["path"], "；".join(probs)))
        elif is_binary(Path(f["src"])):
            skipped.append((f["path"], "二进制内容（含 NUL 字节）"))
        else:
            accepted.append(f)

    # 第二步: 分类与链规划（普通文件留 accepted，超限文件进 chains）
    ts = time.strftime("%Y%m%d-%H%M%S")
    tmp_dir = Path(repo_root) / ".git" / "submit-pr-tmp" / ts
    chains: dict[str, dict] = {}
    cap = min(max_file_lines, max_lines)
    if args.delete:
        for p in args.delete:
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
                {"src": None, "rel": p, "path": p},  # rel 必须是仓库根相对路径
                old, cap, str(tmp_dir))}
    else:
        accepted, sk, chains = plan_file_ops(
            accepted, base, dest, repo_root, cap, max_file_lines, str(tmp_dir))
        skipped += sk

    for path, why in skipped:
        print(f"  [跳过] {path} — {why}")
    if skipped and args.strict:
        sys.exit("错误: --strict 模式下存在被跳过的文件，已中止。")
    if not accepted and not chains:
        print("没有需要提交的内容（可能全部与上游一致）。")
        return

    batches = make_batches(accepted, max_lines, max_files)
    print(f"\n计划: {len(accepted)} 个普通文件 → {len(batches)} 个 PR，"
          f"{len(chains)} 个大文件分块链（单 PR ≤ {max_lines} 行 / {max_files} 文件，"
          f"分块 ≤ {cap} 行）")
    for i, b in enumerate(batches, 1):
        lines = sum(counted_lines(it) for it in b)
        names = ", ".join(it["path"] for it in b)
        print(f"  PR {i}/{len(batches)}: {len(b)} 个文件, {lines} 行 — {names}")
    kind_name = {"create": "分块链", "modify": "改写链", "delete": "删除链"}
    for path, ch in chains.items():
        total = sum(s["adds"] + s["dels"] for s in ch["steps"])
        print(f"  {kind_name[ch['kind']]} {path}: 共 {total} 行变更 → "
              f"{len(ch['steps'])} 个渐进 PR（同链串行合并，链间并发）")
    if args.dry_run:
        print("\n[dry-run] 未真正提交。")
        return

    # 第三步: 并发提交。任务单元 = 普通批（单步）或分块链（多步、串行换新基底），
    # 各单元文件路径不相交，单元之间并发互不干扰。
    dest_label = dest or "root"
    units: list[tuple[str, list, list[dict]]] = []  # (名称, 步骤, 涉及文件)

    def pr_text(batch: list[dict], i: int, n: int) -> tuple[str, str]:
        title = args.title or f"{dest_label}: add {len(batch)} file(s)"
        if n > 1:
            title = f"{title} (part {i}/{n})"
        rows = "\n".join(
            f"| `{it['path']}` | {it['adds'] + it['dels']} |" for it in batch)
        total = sum(counted_lines(it) for it in batch)
        body = args.body or (
            f"## Summary\n\nAdds {len(batch)} file(s) into `{dest_label}/`"
            + (f" — batch {i}/{n}, auto-split to respect the "
               f"{MAX_CHANGED_LINES}-line / {MAX_CHANGED_FILES}-file limits of the "
               f"auto-merge regulations." if n > 1 else ".")
            + f"\n\n| file | changed lines |\n|---|---|\n{rows}\n\n"
            f"**{total} counted lines** total. "
            "Pre-validated locally by `submit-pr/submit_pr.py` against every "
            "gate rule.\n")
        return title, body

    for i, b in enumerate(batches, 1):
        title, body = pr_text(b, i, len(batches))
        units.append((f"PR {i}/{len(batches)}",
                      [(f"{args.branch_prefix}-{ts}-{i}", title, body, b)], b))
    for path, ch in chains.items():
        j = len(units) + 1  # 链内步骤的分支名带单元序号，保证全局唯一
        n = len(ch["steps"])
        steps, done = [], 0
        for k, c in enumerate(ch["steps"], 1):
            title, body = build_step_text(ch["kind"], c, k, n, cap,
                                          dest_label, args.title, args.body, done)
            done += c["adds"]
            steps.append((f"{args.branch_prefix}-{ts}-{j}x{k}", title, body, [c]))
        units.append((f"{kind_name[ch['kind']]} {path}（{n} 块）", steps,
                      [ch["steps"][-1]]))

    workers = max(1, min(args.workers or len(units), len(units), 10))
    print(f"\n并发提交 {len(units)} 个任务（{workers} 路并行）…")
    results: dict[int, list] = {}
    errors: dict[int, str] = {}

    def run_unit(steps: list) -> list:
        out = []
        for branch, title, body, batch in steps:
            # 分块链的每一步都必须基于包含前一块的最新 main
            b2 = latest_main_sha(target, repo_root) if len(steps) > 1 else base
            out.append(submit_batch(repo_root, target, fork, b2, branch, batch,
                                    dest, title, body, args.poll_timeout,
                                    args.poll_interval, args.max_retries))
        return out

    try:
        with ThreadPoolExecutor(max_workers=workers) as ex:
            futs = [ex.submit(run_unit, steps) for _, steps, _ in units]
            for j, fut in enumerate(futs, 1):
                try:
                    results[j] = fut.result()
                except Exception as e:  # 单个任务失败不影响其他任务，最后统一报告
                    errors[j] = str(e)
    except KeyboardInterrupt:
        print("\n已中断。")
        sys.exit(130)
    finally:
        shutil.rmtree(tmp_dir, ignore_errors=True)

    # 第四步: 汇总并验证上游 main 上的最终内容
    print("\n== 结果")
    ok = True
    for j, (label, _, items) in enumerate(units, 1):
        if j in errors:
            ok = False
            print(f"  {label}: 失败 — {errors[j]}")
            continue
        for r in results[j]:
            print(f"  PR #{r['pr']}: {r['result']}  {r['url']}")
            if r["result"] != "merged":
                ok = False

    run_retry(["git", "fetch", f"https://github.com/{target}.git", "main"],
              cwd=repo_root)
    tree = git_run(["git", "ls-tree", "-r", "FETCH_HEAD"], cwd=repo_root)
    sha_by_path = {}
    for line in tree.splitlines():
        meta, path = line.split("\t", 1)
        sha_by_path[path] = meta.split()[2]
    # 验证最终状态: 普通批验证全部条目，分块链只看末块（src=原文件）
    submitted = [it for j, (_, _, items) in enumerate(units, 1)
                 if j not in errors for it in items]
    missing = [it["path"] for it in submitted
               if not it.get("delete") and it["path"] not in sha_by_path]
    changed = []
    for it in submitted:
        if it.get("delete"):  # 删除链: 验证文件确实不在 main 上
            if it["path"] in sha_by_path:
                changed.append(f"{it['path']}（应已删除却仍存在）")
            continue
        if it["path"] in sha_by_path:  # 比对内容而非仅路径——修改型提交必须查
            local = git_run(["git", "hash-object", it["src"]],
                            cwd=repo_root).strip()
            if local != sha_by_path[it["path"]]:
                changed.append(it["path"])
    if missing:
        ok = False
        print(f"  [!] 以下文件未出现在上游 main 上: {', '.join(missing)}")
    if changed:
        ok = False
        print(f"  [!] 以下文件在上游 main 上的内容与提交内容不一致: {', '.join(changed)}")
    if not missing and not changed:
        print(f"  已验证: 已提交的 {len(submitted)} 个文件的内容都与上游 main 一致。")

    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    try:
        main()
    except RuntimeError as e:
        print(f"错误: {e}", file=sys.stderr)
        sys.exit(1)
