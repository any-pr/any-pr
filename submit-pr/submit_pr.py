#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
submit_pr.py — 自动向 any-pr 上游仓库提交 PR（命令行入口，保证每个 PR 都能被
auto-merge 机器人顺利合并）。

用法（在本仓库克隆内运行）:
    python submit-pr/submit_pr.py <源文件或目录...> --dest <仓库内目标目录> [选项]
    python submit-pr/submit_pr.py --delete <仓库内路径...> [选项]
    python submit-pr/submit_pr.py --gui        # 图形界面

示例:
    python submit-pr/submit_pr.py "Z:\\某文件夹" --dest music-api
    python submit-pr/submit_pr.py some_dir --dest music-api --dry-run
    python submit-pr/submit_pr.py --delete some/path.txt

工作原理与全部规则复刻见 rules.py / files.py / gitops.py / runner.py 的模块
文档。要点: 本地完整复刻上游 auto-merge 机器人规则；超限自动拆分成多个 PR
（新建/修改/删除都有对应的渐进分块链）；所有批次默认并发提交；每步 PR 创建
后轮询等待合并，冲突/竞态停滞自动换新基底强推重试；结束后按 blob SHA 逐文件
验证上游 main 上的最终内容。全程不直接 push 任何 main 分支。
"""

from __future__ import annotations

import argparse
import sys

from runner import run_plan


class ConsoleReporter:
    """把 run_plan 的进度事件打印到控制台。"""

    def __call__(self, ev: str, **kw) -> None:
        if ev == "log":
            print(kw["text"])
        elif ev == "plan":
            print(f"\n并发提交 {kw['total']} 个任务（共 {kw['steps']} 个 PR）…")
        elif ev == "unit":
            print(f"[{kw['label']}] 开始（{kw['steps']} 步）")
        elif ev == "step":
            print(f"[任务 {kw['idx']}] 第 {kw['k']} 步 → PR #{kw['pr']}: {kw['url']}")
        elif ev == "unit_done":
            print(f"[{kw['label']}] {kw['status']}")


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
    ap.add_argument("--max-lines", type=int, default=None,
                    help="单 PR 行数上限（只会收紧到机器人上限）")
    ap.add_argument("--max-files", type=int, default=None,
                    help="单 PR 文件数上限（只会收紧到机器人上限）")
    ap.add_argument("--max-file-lines", type=int, default=None,
                    help="单文件行数上限（只会收紧到机器人上限）")
    ap.add_argument("--poll-timeout", type=int, default=300, help="等待合并的超时秒数")
    ap.add_argument("--poll-interval", type=int, default=15, help="轮询间隔秒数")
    ap.add_argument("--max-retries", type=int, default=3, help="冲突/停滞重试次数")
    ap.add_argument("--workers", type=int, default=0,
                    help="并发提交的 PR 数上限（默认 0 = 所有任务同时提交）")
    ap.add_argument("--strict", action="store_true",
                    help="有文件因规则被跳过时直接中止，而不是跳过继续")
    ap.add_argument("--dry-run", action="store_true", help="只打印计划，不真正提交")
    ap.add_argument("--gui", action="store_true", help="启动图形界面")
    args = ap.parse_args()

    if args.gui:
        import gui
        gui.main()
        return

    opts = {k: v for k, v in vars(args).items() if v is not None and v != []}
    opts.pop("gui", None)
    try:
        res = run_plan(opts, ConsoleReporter())
        sys.exit(0 if res["ok"] else 1)
    except KeyboardInterrupt:
        print("\n已中断。")
        sys.exit(130)
    except (RuntimeError, ValueError) as e:
        print(f"错误: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
