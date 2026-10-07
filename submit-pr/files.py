# -*- coding: utf-8 -*-
"""
files.py — 源文件收集、变更分类与"超大文件"的渐进分块链。

机器人单文件上限对新建/修改/删除一视同仁（diff 的 additions+deletions）。
超出时把操作拆成渐进前缀链，每步 ≤cap 行:
    创建  第 1 块创建前 cap 行，后续每块追加 cap 行，末块用原文件字节；
    修改  先把旧内容截断到新旧公共前缀，再按 cap 行逐步换成新内容；
    删除  大文本先按 cap 行逐步截断，末步真正删除文件。
同一文件的各块必须按序合并（块 k+1 的 diff 基于"main 已有块 k"），不同文件
之间互不影响，可与其他 PR 并发。
"""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path

from gitops import probe_changes
from rules import EXCLUDED_RE, chunk_bounds


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


def base_content(base_sha: str, path: str, repo_root: str) -> bytes | None:
    """读取基线 commit 上某文件的内容；不存在返回 None。"""
    p = subprocess.run(["git", "show", f"{base_sha}:{path}"], cwd=repo_root,
                       capture_output=True)
    return p.stdout if p.returncode == 0 else None


def _prefix_file(lines: list[bytes], end: int, tmp_dir: str, tag: str) -> str:
    """把 lines 的前 end 行写入临时前缀文件（tag 需含源文件名以防碰撞）。"""
    p = Path(tmp_dir) / f"{tag}.{end}.part"
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_bytes(b"".join(lines[:end]))
    return str(p)


def _tag(item: dict) -> str:
    """临时文件的命名空间: 完整 rel 路径替换分隔符，避免不同文件的同序号块碰撞。"""
    return item["rel"].replace("/", "_")


def build_chunk_chain(item: dict, cap: int, tmp_dir: str) -> list[dict]:
    """新建超大文件 → 渐进创建链（每块 ≤cap 行，末块=原文件字节）。"""
    lines = Path(item["src"]).read_bytes().splitlines(keepends=True)
    tag = _tag(item)
    chain = []
    for k, (a, b) in enumerate(chunk_bounds(len(lines), cap), 1):
        src = (item["src"] if b == len(lines)
               else _prefix_file(lines, b, tmp_dir, f"{tag}.c{k}"))
        chain.append({"src": src, "rel": item["rel"], "path": item["path"],
                      "adds": b - a, "dels": 0})
    return chain


def build_modify_chain(item: dict, old_bytes: bytes, cap: int,
                       tmp_dir: str) -> list[dict]:
    """超大 diff 修改 → 截断到公共前缀 + 逐步追加（每步 ≤cap 行）。

    纯追加（旧内容是新内容前缀）只走追加段；纯截断反之。
    公共前缀比较忽略行尾风格（blob 可能被 autocrlf 归一化成 LF 而本地是
    CRLF）；若旧内容是 CRLF，追加段前缀也转成 CRLF，保证每步都是纯追加。
    """
    old = old_bytes.splitlines(keepends=True)
    new = Path(item["src"]).read_bytes().splitlines(keepends=True)
    common = 0
    for a, b in zip(old, new):
        if a.rstrip(b"\r\n") != b.rstrip(b"\r\n"):
            break
        common += 1
    if any(l.endswith(b"\r\n") for l in old):  # 追加段匹配旧的行尾风格
        new = [l[:-1] + b"\r\n" if l.endswith(b"\n") and not l.endswith(b"\r\n")
               else l for l in new]
    tag = _tag(item)
    steps, k = [], len(old)
    while k > common:  # 截断段: 每步最多删 cap 行
        k2 = max(common, k - cap)
        steps.append({**item, "src": _prefix_file(old, k2, tmp_dir, f"{tag}.t{k2}"),
                      "adds": 0, "dels": k - k2})
        k = k2
    m = common
    while m < len(new):  # 追加段: 每步最多加 cap 行
        m2 = min(len(new), m + cap)
        src = (item["src"] if m2 == len(new)
               else _prefix_file(new, m2, tmp_dir, f"{tag}.g{m2}"))
        steps.append({**item, "src": src, "adds": m2 - m, "dels": 0})
        m = m2
    return steps


def build_delete_chain(item: dict, old_bytes: bytes, cap: int,
                       tmp_dir: str) -> list[dict]:
    """删除已存在文件: 大文本先按 cap 行逐步截断，末步真正删除。

    豁免文件（lockfile/vendor 等）与二进制按 0 计数行，一步删除。
    """
    if EXCLUDED_RE.search(item["path"]) or b"\x00" in old_bytes:
        return [{**item, "delete": True, "adds": 0, "dels": 0}]
    lines = old_bytes.splitlines(keepends=True)
    tag = _tag(item)
    steps, k = [], len(lines)
    while k > cap:
        k2 = k - cap
        steps.append({**item, "src": _prefix_file(lines, k2, tmp_dir, f"{tag}.d{k2}"),
                      "adds": 0, "dels": k - k2})
        k = k2
    steps.append({**item, "delete": True, "adds": 0, "dels": k})
    return steps


def plan_file_ops(accepted: list[dict], base: str, dest: str, repo_root: str,
                  cap: int, max_file_lines: int,
                  tmp_dir: str) -> tuple[list[dict], list, dict]:
    """第二步: probe 出与机器人一致的变更行数，超限文件规划链。

    返回 (accepted, skipped, chains)；chains[path] = {"kind": "create"|"modify",
    "steps": […]"}。豁免文件不计行数，不做上限检查。
    """
    skipped, chains = [], {}
    ns = probe_changes(repo_root, base, dest, accepted)
    for f in accepted:
        f["adds"], f["dels"] = ns.get(f["path"], (0, 0))
        diff = f["adds"] + f["dels"]
        if EXCLUDED_RE.search(f["path"]):
            continue
        if diff <= max_file_lines:
            if diff == 0:
                skipped.append((f["path"], "与上游 main 内容完全相同，无需提交"))
            continue
        old = base_content(base, f["path"], repo_root)
        if old is None:  # 新建 → 渐进创建链
            chains[f["path"]] = {"kind": "create",
                                 "steps": build_chunk_chain(f, cap, tmp_dir)}
        elif b"\x00" in old:  # 旧内容是二进制，无法按行拆分
            skipped.append((f["path"],
                            "修改前内容为二进制，暂不支持拆分修改，请手动处理"))
        else:  # 修改 → 截断+追加链
            chains[f["path"]] = {"kind": "modify",
                                 "steps": build_modify_chain(f, old, cap, tmp_dir)}
    left = [f for f in accepted if f["path"] not in chains
            and not any(p == f["path"] for p, _ in skipped)]
    return left, skipped, chains


def build_step_text(kind: str, c: dict, k: int, n: int, cap: int,
                    dest_label: str, user_title: str | None,
                    user_body: str | None, done: int = 0) -> tuple[str, str]:
    """为链中第 k/n 步生成 PR 标题与描述（done = 创建链截至本块的累计行数）。"""
    suffix = f" (chunk {k}/{n})" if n > 1 else ""
    if kind == "create":
        title = (user_title + suffix if user_title
                 else f"{dest_label}: add {c['rel']}{suffix}")
        body = user_body or (
            f"Progressively creates `{c['path']}` in {n} chunks (≤{cap} lines "
            f"each) to satisfy the per-file limit of the auto-merge regulations. "
            f"Chunk {k}/{n}: the file now holds its first {done} lines.\n\n"
            "Pre-validated locally by `submit-pr/submit_pr.py`.\n")
    elif kind == "modify":
        title = (user_title + suffix if user_title
                 else f"{dest_label}: update {c['rel']}{suffix}")
        body = user_body or (
            f"Progressively rewrites `{c['path']}` in {n} steps (≤{cap} lines "
            f"each) to satisfy the per-file limit of the auto-merge regulations. "
            f"Step {k}/{n}.\n\n"
            "Pre-validated locally by `submit-pr/submit_pr.py`.\n")
    else:
        title = (user_title + suffix if user_title
                 else f"remove {c['path']}{suffix}")
        body = user_body or (
            f"Progressively removes `{c['path']}` in {n} steps (≤{cap} lines "
            f"each) to satisfy the per-file limit of the auto-merge regulations. "
            f"Step {k}/{n}: {'file truncated' if k < n else 'file removed'}.\n\n"
            "Pre-validated locally by `submit-pr/submit_pr.py`.\n")
    return title, body
