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

工作原理（详见 rules.py / gitops.py）:
    上游 any-pr/any-pr 的 .github/workflows/auto-merge.yml 会在 PR 打开时自动
    检查并合并满足规则的 PR。本脚本在本地完整复刻其全部规则（rules.py），把
    超限的提交自动拆分成多个 PR，因此提交出的每个 PR 必定能通过机器人检查。

    行数统计口径与 GitHub 机器人完全一致: 在临时 worktree 中把文件暂存后用
    `git diff --cached --numstat` 得到每个文件的真实 additions+deletions。

    流程: fetch 上游 main 最新 commit → 在临时 worktree 里基于它建分支并提交
    → push 到自己 fork → 所有批次并发向上游开 PR（默认全部同时，--workers 可限）
    → 轮询等待合并 → 若因冲突被关，自动换新基底重建分支强推重试。各批文件路径
    不相交，因此无论机器人以何种顺序合并都不会冲突。全程不直接 push 任何 main 分支。
"""

from __future__ import annotations

import argparse
import json
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from gitops import (gh, latest_main_sha, probe_changes, run as git_run,
                    submit_batch)
from rules import MAX_CHANGED_FILES, MAX_CHANGED_LINES, MAX_FILE_LINES
from rules import EXCLUDED_RE, counted_lines, gate_path_problems, is_binary
from rules import make_batches


def collect_sources(sources: list[str]) -> list[dict]:
    """收集要提交的文件 → [{src, rel}]，rel 为相对各自源根目录的目标相对路径。"""
    files: list[dict] = []
    seen: dict[str, str] = {}
    for s in sources:
        sp = Path(s)
        if not sp.exists():
            sys.exit(f"错误: 源不存在: {s}")
        if sp.is_file():
            entries = [(sp, sp.name)]
        else:
            entries = []
            for f in sorted(sp.rglob("*")):
                if f.is_symlink():
                    print(f"  [跳过] {f} 是符号链接，仓库不允许（已跳过）")
                elif f.is_file():
                    entries.append((f, f.relative_to(sp).as_posix()))
        for src, rel in entries:
            if rel in seen:
                sys.exit(f"错误: 目标相对路径冲突: {rel}（{src} 与 {seen[rel]}）")
            seen[rel] = str(src)
            files.append({"src": str(src), "rel": rel})
    return files


def main() -> None:
    ap = argparse.ArgumentParser(
        description="自动向 any-pr 上游提交能被 auto-merge 机器人顺利合并的 PR",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    ap.add_argument("sources", nargs="+", help="要提交的源文件或目录（可多个）")
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

    files = collect_sources(args.sources)
    if not files:
        sys.exit("错误: 没有找到任何要提交的文件。")

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

    # 第二步: 用临时 worktree 拿到与机器人一致的每个文件变更行数
    if accepted:
        ns = probe_changes(repo_root, base, dest, accepted)
        for f in accepted:
            f["adds"], f["dels"] = ns.get(f["path"], (0, 0))
            if not EXCLUDED_RE.search(f["path"]) \
                    and f["adds"] + f["dels"] > max_file_lines:
                skipped.append(
                    (f["path"],
                     f"单文件变更 {f['adds'] + f['dels']} 行 > 上限 {max_file_lines}，"
                     f"无法通过拆分解决")
                )
            elif f["adds"] + f["dels"] == 0:
                skipped.append((f["path"], "与上游 main 内容完全相同，无需提交"))
        accepted = [f for f in accepted
                    if not any(p == f["path"] for p, _ in skipped)]

    for path, why in skipped:
        print(f"  [跳过] {path} — {why}")
    if skipped and args.strict:
        sys.exit("错误: --strict 模式下存在被跳过的文件，已中止。")
    if not accepted:
        print("没有需要提交的内容（可能全部与上游一致）。")
        return

    batches = make_batches(accepted, max_lines, max_files)
    print(f"\n计划: {len(accepted)} 个文件 → {len(batches)} 个 PR"
          f"（单 PR ≤ {max_lines} 行 / {max_files} 文件）")
    for i, b in enumerate(batches, 1):
        lines = sum(counted_lines(it) for it in b)
        names = ", ".join(it["path"] for it in b)
        print(f"  PR {i}/{len(batches)}: {len(b)} 个文件, {lines} 行 — {names}")
    if args.dry_run:
        print("\n[dry-run] 未真正提交。")
        return

    # 第三步: 并发提交所有批次并等待合并（各批文件路径不相交，互不干扰）
    ts = time.strftime("%Y%m%d-%H%M%S")
    dest_label = dest or "root"
    workers = max(1, min(args.workers or len(batches), len(batches), 10))
    print(f"\n并发提交 {len(batches)} 个 PR（{workers} 路并行）…")

    results: dict[int, dict] = {}
    errors: dict[int, str] = {}

    def job(i: int, batch: list[dict]) -> None:
        branch = f"{args.branch_prefix}-{ts}-{i}"
        title = args.title or f"{dest_label}: add {len(batch)} file(s)"
        if len(batches) > 1:
            title = f"{title} (part {i}/{len(batches)})"
        rows = "\n".join(
            f"| `{it['path']}` | {it['adds'] + it['dels']} |" for it in batch)
        total = sum(counted_lines(it) for it in batch)
        body = args.body or (
            f"## Summary\n\nAdds {len(batch)} file(s) into `{dest_label}/`"
            + (f" — batch {i}/{len(batches)}, auto-split to respect the "
               f"{MAX_CHANGED_LINES}-line / {MAX_CHANGED_FILES}-file limits of the "
               f"auto-merge regulations." if len(batches) > 1 else ".")
            + f"\n\n| file | changed lines |\n|---|---|\n{rows}\n\n"
            f"**{total} counted lines** total. "
            "Pre-validated locally by `submit-pr/submit_pr.py` against every "
            "gate rule.\n"
        )
        results[i] = submit_batch(
            repo_root, target, fork, base, branch, batch, dest, title, body,
            args.poll_timeout, args.poll_interval, args.max_retries,
        )

    try:
        with ThreadPoolExecutor(max_workers=workers) as ex:
            futs = [ex.submit(job, i, b) for i, b in enumerate(batches, 1)]
            for fut in futs:
                try:
                    fut.result()
                except Exception as e:  # 单批失败不影响其他批，最后统一报告
                    errors[futs.index(fut) + 1] = str(e)
    except KeyboardInterrupt:
        print("\n已中断。")
        sys.exit(130)

    # 第四步: 汇总并验证上游 main 上的最终内容
    print("\n== 结果")
    ok = True
    for i in range(1, len(batches) + 1):
        if i in errors:
            ok = False
            print(f"  PR {i}/{len(batches)}: 失败 — {errors[i]}")
        else:
            r = results[i]
            print(f"  PR #{r['pr']}: {r['result']}  {r['url']}")
            if r["result"] != "merged":
                ok = False

    git_run(["git", "fetch", f"https://github.com/{target}.git", "main"], cwd=repo_root)
    tree = git_run(["git", "ls-tree", "-r", "--name-only", "FETCH_HEAD"], cwd=repo_root)
    on_main = set(tree.splitlines())
    merged_paths = [it["path"] for i, b in enumerate(batches, 1)
                    if i not in errors for it in b]
    missing = [p for p in merged_paths if p not in on_main]
    if missing:
        ok = False
        print(f"  [!] 以下文件未出现在上游 main 上: {', '.join(missing)}")
    else:
        print(f"  已验证: 已合并批次的 {len(merged_paths)} 个文件都在上游 main 上。")

    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    try:
        main()
    except RuntimeError as e:
        print(f"错误: {e}", file=sys.stderr)
        sys.exit(1)
