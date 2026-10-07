# -*- coding: utf-8 -*-
"""
files.py — 源文件收集与"新建超大文件"的渐进分块。

超大新建文件无法单个 PR 提交（机器人单文件 ≤300 行），解决方案是把文件按行
拆成渐进前缀: 第 1 个 PR 创建前 cap 行，第 2 个 PR 追加到前 2×cap 行……每块
diff 都是 ≤cap 行的纯追加，末块直接用原文件字节保证最终内容与源完全一致。
同一文件的各块必须按序合并（块 k+1 的 diff 基于"main 已有块 k"），不同文件
之间互不影响，可与其他 PR 并发。
"""

from __future__ import annotations

import sys
from pathlib import Path

from gitops import run
from rules import chunk_bounds


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


def file_exists_on_base(base_sha: str, path: str, repo_root: str) -> bool:
    """判断 path 在基线 commit 上是否已存在（区分新建与修改）。"""
    try:
        run(["git", "cat-file", "-e", f"{base_sha}:{path}"], cwd=repo_root)
        return True
    except RuntimeError:
        return False


def build_chunk_chain(item: dict, cap: int, tmp_dir: str) -> list[dict]:
    """把新建超大文件拆成按序提交的渐进前缀块（每块 ≤cap 行）。

    返回按序排列的块条目: 第 k 块的 src 指向前 k×cap 行的前缀临时文件，
    末块的 src 直接用原文件（保证最终内容与源逐字节一致）。
    """
    data = Path(item["src"]).read_bytes()
    lines = data.splitlines(keepends=True)  # 保留 \r\n 与末行无换行等细节
    bounds = chunk_bounds(len(lines), cap)
    Path(tmp_dir).mkdir(parents=True, exist_ok=True)
    chain = []
    for k, (a, b) in enumerate(bounds, 1):
        if k == len(bounds):
            src = item["src"]
        else:
            # 临时文件名带源文件名——多个链共享 tmp_dir，不能只用块序号
            src = str(Path(tmp_dir) / f"{k}.{Path(item['rel']).name}.part")
            Path(src).write_bytes(b"".join(lines[:b]))
        chain.append({"src": src, "rel": item["rel"], "path": item["path"],
                      "adds": b - a, "dels": 0})
    return chain
