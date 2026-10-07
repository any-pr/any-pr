# -*- coding: utf-8 -*-
"""
gitops.py — git / gh / GitHub API 的所有底层操作。

包括: 子进程封装、临时 worktree（精确对齐 GitHub 的变更行数统计）、
分支推送、PR 创建（含跨仓库 --head 格式与 GraphQL 读副本延迟的容错）、
PR 状态轮询。
"""

from __future__ import annotations

import json
import shutil
import subprocess
import threading
import time
from pathlib import Path

from rules import (EXCLUDED_RE, MAX_CHANGED_FILES, MAX_CHANGED_LINES,
                   MAX_FILE_LINES, counted_lines)

_print_lock = threading.Lock()


def log(label: str, msg: str) -> None:
    """线程安全的带前缀输出（并发提交时多个批次同时打印）。"""
    with _print_lock:
        print(f"[{label}] {msg}")


def run(cmd: list[str], cwd: str | None = None) -> str:
    p = subprocess.run(
        cmd, cwd=cwd, capture_output=True, text=True, encoding="utf-8", errors="replace"
    )
    if p.returncode != 0:
        raise RuntimeError(
            f"命令失败 ({p.returncode}): {' '.join(cmd)}\n{(p.stderr or p.stdout).strip()}"
        )
    return p.stdout


def gh(*args: str) -> str:
    return run(["gh", *args]).strip()


def git_fetch(url: str, ref: str, repo_root: str) -> None:
    """fetch: 先常规连接，代理/网络类错误自动降级直连重试一次。"""
    variants = ([], ["-c", "http.proxy=", "-c", "https.proxy="])
    last: Exception | None = None
    for cfg in variants:
        try:
            run(["git", *cfg, "fetch", url, ref], cwd=repo_root)
            return
        except RuntimeError as e:
            last = e
            if not any(k in str(e).lower() for k in
                       ("proxy", "connect", "ssl", "tls", "handshake",
                        "timeout", "eof")):
                raise
    raise last  # pragma: no cover


def latest_main_sha(target: str, repo_root: str) -> str:
    git_fetch(f"https://github.com/{target}.git", "main", repo_root)
    return run(["git", "rev-parse", "FETCH_HEAD"], cwd=repo_root).strip()


# ---------------------------------------------------------------------------
# 临时 worktree：精确拿到与 GitHub 机器人一致的变更行数
# ---------------------------------------------------------------------------
def make_worktree(repo_root: str, base_sha: str) -> str:
    wt = Path(repo_root) / ".git" / "submit-pr-worktrees" / f"w{time.time_ns()}"
    wt.parent.mkdir(parents=True, exist_ok=True)
    run(["git", "worktree", "add", "--detach", str(wt), base_sha], cwd=repo_root)
    return str(wt)


def drop_worktree(repo_root: str, wt: str) -> None:
    run(["git", "worktree", "remove", "--force", str(wt)], cwd=repo_root)
    run(["git", "worktree", "prune"], cwd=repo_root)


def copy_into(wt: str, dest: str, batch: list[dict]) -> None:
    for item in batch:
        if item.get("delete"):  # 删除步骤: 移除工作树文件,后续 git add 会暂存删除
            p = Path(wt) / dest / item["rel"] if dest else Path(wt) / item["rel"]
            p.unlink(missing_ok=True)
            continue
        t = Path(wt) / dest / item["rel"] if dest else Path(wt) / item["rel"]
        t.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(item["src"], t)


def staged_numstat(wt: str, dest: str) -> dict[str, tuple[int, int]]:
    run(["git", "-C", wt, "add", "--", dest or "."])
    out = run(
        ["git", "-C", wt, "-c", "core.quotePath=false",
         "diff", "--cached", "--numstat", "-z"]
    )
    res: dict[str, tuple[int, int]] = {}
    for entry in out.split("\0"):
        if not entry.strip():
            continue
        adds, dels, path = entry.split("\t", 2)
        res[path] = (
            0 if adds == "-" else int(adds),
            0 if dels == "-" else int(dels),
        )
    return res


def probe_changes(repo_root: str, base_sha: str, dest: str,
                  files: list[dict]) -> dict[str, tuple[int, int]]:
    """返回 {rel: (adds, dels)} —— 与机器人看到的 .changes 完全一致。"""
    run(["git", "worktree", "prune"], cwd=repo_root)
    wt = make_worktree(repo_root, base_sha)
    try:
        copy_into(wt, dest, files)
        return staged_numstat(wt, dest)
    finally:
        drop_worktree(repo_root, wt)


def build_worktree_commit(repo_root: str, base_sha: str, dest: str,
                          batch: list[dict], message: str) -> str:
    """在临时 worktree 里基于 base_sha 复制文件并提交，返回 worktree 路径。

    提交前用真实 numstat 对照机器人上限（MAX_*，而非用户收紧值——单文件批
    可能超出用户收紧值但绝不会超出机器人上限）再校验一次，防御性兜底。
    """
    wt = make_worktree(repo_root, base_sha)
    try:
        copy_into(wt, dest, batch)
        ns = staged_numstat(wt, dest)
        total = sum(counted_lines({"path": p, "adds": a, "dels": d})
                    for p, (a, d) in ns.items())
        assert len(ns) <= MAX_CHANGED_FILES, f"文件数 {len(ns)} 超过 {MAX_CHANGED_FILES}"
        assert total <= MAX_CHANGED_LINES, f"计数行数 {total} 超过 {MAX_CHANGED_LINES}"
        for p, (a, d) in ns.items():
            if not EXCLUDED_RE.search(p):
                assert a + d <= MAX_FILE_LINES, f"{p} 变更 {a + d} 行超过 {MAX_FILE_LINES}"
        run(["git", "-C", wt, "commit", "-q", "-m", message])
        return wt
    except Exception:
        drop_worktree(repo_root, wt)
        raise


# ---------------------------------------------------------------------------
# 分支推送与 PR 创建 / 轮询
# ---------------------------------------------------------------------------
def head_spec(target: str, fork: str, branch: str) -> str:
    """gh 的跨仓库 --head 格式是 owner:branch，不是 owner/repo:branch。"""
    return branch if fork == target else f"{fork.split('/')[0]}:{branch}"


def push_branch(fork: str, branch: str, wt: str, force: bool) -> None:
    # push 幂等（同分支同内容），按"常规→直连→常规"退避重试兜底代理抖动
    variants = ([], ["-c", "http.proxy=", "-c", "https.proxy="], [])
    ref = f"HEAD:refs/heads/{branch}"
    for i, cfg in enumerate(variants, 1):
        cmd = (["git", "-C", wt, *cfg, "push", f"https://github.com/{fork}.git",
                ref] + (["--force"] if force else []))
        try:
            run(cmd)
            return
        except RuntimeError as e:
            if i == len(variants):
                raise
            log(branch, f"push 失败，{5 * i}s 后重试 ({i}/{len(variants)})…")
            time.sleep(5 * i)


def wait_for_branch(fork: str, branch: str, timeout: int = 90) -> None:
    """push 后 GraphQL 读副本可能短暂看不到新分支，等 REST API 可见再开 PR。"""
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            gh("api", f"repos/{fork}/branches/{branch}", "--jq", ".name")
            return
        except RuntimeError:
            time.sleep(3)
    raise RuntimeError(f"分支 {branch} 推送后 {timeout}s 内未在 {fork} 上可见")


def get_open_pr(target: str, fork: str, branch: str) -> int | None:
    out = gh("pr", "list", "--repo", target, "--head", head_spec(target, fork, branch),
             "--state", "open", "--json", "number", "--jq", ".[0].number // empty")
    return int(out) if out else None


def create_pr(target: str, fork: str, branch: str, title: str, body: str,
              attempts: int = 3) -> str:
    """创建 PR；对读副本延迟导致的瞬时报错重试。"""
    head = head_spec(target, fork, branch)
    last_err: Exception | None = None
    for i in range(1, attempts + 1):
        try:
            return gh("pr", "create", "--repo", target, "--base", "main",
                      "--head", head, "--title", title, "--body", body)
        except RuntimeError as e:
            last_err = e
            msg = str(e)
            if any(k in msg for k in ("Head sha", "not all refs are readable",
                                      "No commits between")) and i < attempts:
                time.sleep(10)
                continue
            raise
    raise last_err  # pragma: no cover


def pr_state(target: str, pr: int) -> str:
    return json.loads(gh("pr", "view", str(pr), "--repo", target, "--json", "state"))["state"]


def last_comment(target: str, pr: int) -> str:
    try:
        out = json.loads(
            gh("pr", "view", str(pr), "--repo", target, "--json", "comments")
        )
        cs = out.get("comments") or []
        return cs[-1].get("body", "") if cs else ""
    except RuntimeError:
        return ""
