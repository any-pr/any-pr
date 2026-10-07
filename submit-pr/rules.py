# -*- coding: utf-8 -*-
"""
rules.py — 上游 any-pr/any-pr auto-merge 机器人规则在本地的一比一复刻。

常量与正则逐条对照上游 .github/workflows/auto-merge.yml（勿随意改动），
保证"本地校验通过 = 机器人必定放行"。
"""

from __future__ import annotations

import re
from pathlib import Path

MAX_CHANGED_FILES = 20
MAX_CHANGED_LINES = 500
MAX_FILE_LINES = 300

PROTECTED_RE = re.compile(r"^\.github(/|$)", re.I)

EXT_EXCL = (
    "c|h|cpp|cc|cxx|hpp|hh|cs|go|rs|rb|erb|py|pyi|java|kt|kts|scala|clj|ex|exs|erl|"
    "hs|ml|fs|vb|swift|php|pl|pm|r|lua|dart|zig|nim|cr|jl|sh|bash|zsh|fish|ps1|bat|"
    "cmd|js|jsx|ts|tsx|mjs|cjs|mts|cts|vue|svelte|astro|elm|css|scss|sass|less|styl|"
    "json|jsonc|ya?ml|toml|xml|ini|cfg|conf|sql|graphql|proto|tf|nix|ipynb|lock|csv|"
    "png|jpe?g|gif|svg|ico|webp|mp[34]|mov|woff2?|ttf|otf|eot|o|obj|so|dll|exe|wasm|"
    "zip|tar|gz|7z|jar|whl"
)
DOCS_RE = re.compile(
    r"(^|/)licen[cs]es?/|(^|/)((un)?licen[cs]e|copying|copyright|readme)s?"
    rf"([._-](?!({EXT_EXCL})$)[a-z0-9]+)*$",
    re.I,
)
PROHIBITED_RE = re.compile(
    r"~$|\.(tmp|temp|bak|sav|old|orig|rej|sw[a-z]|log)$"
    r"|(^|/)(\.DS_Store|Thumbs\.db|desktop\.ini)$"
    r"|(^|/)(__pycache__|\.pytest_cache|\.mypy_cache|\.ruff_cache|\.ipynb_checkpoints"
    r"|\.tox|\.nox|\.cache|\.parcel-cache|\.turbo|\.nyc_output|node_modules|dist|build"
    r"|out|target|coverage|obj|\.next|\.nuxt|\.output|\.gradle|\.vercel)/"
    r"|\.(eslintcache|stylelintcache|py[co]|o|obj|a|lib|so|dylib|dll|exe|com|msi|apk|"
    r"aab|ipa|dmg|deb|rpm|class|jar|war|ear|whl|egg|wasm|pdb|bin|db|sqlite3?|zip|tgz|"
    r"tar|tar\.(gz|bz2|xz|zst)|gz|bz2|xz|7z|rar|zst)$",
    re.I,
)
SECRET_RE = re.compile(
    r"(^|/)\.env([._-][a-z0-9._-]*)?$|(^|/)id_(rsa|dsa|ecdsa|ed25519)$"
    r"|(^|/)credentials(\.json)?$|(^|/)\.(npmrc|netrc|pgpass|htpasswd)$"
    r"|\.(pem|key|p12|pfx|jks|keystore|kdbx|ovpn)$",
    re.I,
)
SECRET_OK_RE = re.compile(r"(^|/)\.env[._-](example|sample|template|dist)$", re.I)
EXCLUDED_RE = re.compile(
    r"(^|/)(package-lock\.json|npm-shrinkwrap\.json|yarn\.lock|pnpm-lock\.yaml|"
    r"bun\.lockb?|composer\.lock|Cargo\.lock|Gemfile\.lock|poetry\.lock|Pipfile\.lock|"
    r"go\.sum)$|(^|/)vendor/|\.(min\.(js|css)|map|snap|lockb?)$",
    re.I,
)


def is_binary(path: Path) -> bool:
    """与机器人一致: 扫描前 50MB 是否含 NUL 字节。"""
    with open(path, "rb") as f:
        return b"\x00" in f.read(50 * 1024 * 1024)


def gate_path_problems(dest_path: str) -> list[str]:
    """按目标路径（仓库内路径）检查机器人所有基于文件名的规则。"""
    probs = []
    if PROTECTED_RE.search(dest_path):
        probs.append("触碰受保护路径 .github/")
    if DOCS_RE.search(dest_path):
        probs.append("受保护文档 (LICENSE/README 系)")
    if PROHIBITED_RE.search(dest_path):
        probs.append("临时/缓存/构建产物/二进制文件名")
    if SECRET_RE.search(dest_path) and not SECRET_OK_RE.search(dest_path):
        probs.append("疑似密钥/凭据文件")
    return probs


def counted_lines(item: dict) -> int:
    """机器人计入 500 行上限的行数（lockfile/vendor/min 等豁免但仍占文件数）。"""
    return 0 if EXCLUDED_RE.search(item["path"]) else item["adds"] + item["dels"]


def make_batches(items: list[dict], max_lines: int,
                 max_files: int) -> list[list[dict]]:
    """贪心装箱: 按输入顺序打包，单批 ≤ max_files 个文件且 ≤ max_lines 计数行。"""
    batches, cur, cur_lines = [], [], 0
    for it in items:
        c = counted_lines(it)
        if cur and (len(cur) + 1 > max_files or cur_lines + c > max_lines):
            batches.append(cur)
            cur, cur_lines = [], 0
        cur.append(it)
        cur_lines += c
    if cur:
        batches.append(cur)
    return batches


def chunk_bounds(total: int, cap: int) -> list[tuple[int, int]]:
    """把 total 行按每块最多 cap 行切块，返回 [(start, end) …]（0 起，不含 end）。"""
    return [(s, min(s + cap, total)) for s in range(0, total, cap)] if total else []
