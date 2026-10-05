#!/usr/bin/env python3
"""Pre-flight check for the any-pr Regulations.

Mirrors the gates enforced by `.github/workflows/auto-merge.yml` so a
contributor can learn whether a pull request will be merged or closed
*before* opening it. A local convenience only: it confers no authority,
is not an access control, and the Workflow remains the sole enforcement
instrument (Article VI, Section 4). Standard library only.

    python tools/regulation-lint.py                 # origin/main...HEAD
    python tools/regulation-lint.py --staged        # what git commit takes
    python tools/regulation-lint.py --base main~5   # any rev range

Exit status: 0 all gates pass, 1 a gate fails, 2 usage error.
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys

# Article IV - Size Limitations. Mirrors the workflow's env block.
MAX_CHANGED_FILES = 20
MAX_CHANGED_LINES = 500
MAX_FILE_LINES = 300

# Article III(e) - the workflow scans the first 50 MB of each blob for NUL.
BINARY_SCAN_BYTES = 50 * 1024 * 1024

# Code/asset extensions excluded from the governance-document suffix match,
# so that e.g. license-check.ts is not treated as a protected document.
EXT_EXCL = (
    r"c|h|cpp|cc|cxx|hpp|hh|cs|go|rs|rb|erb|py|pyi|java|kt|kts|scala|clj|ex|exs|erl|hs|ml"
    r"|fs|vb|swift|php|pl|pm|r|lua|dart|zig|nim|cr|jl|sh|bash|zsh|fish|ps1|bat|cmd|js|jsx"
    r"|ts|tsx|mjs|cjs|mts|cts|vue|svelte|astro|elm|css|scss|sass|less|styl|json|jsonc|ya?ml"
    r"|toml|xml|ini|cfg|conf|sql|graphql|proto|tf|nix|ipynb|lock|csv|png|jpe?g|gif|svg|ico"
    r"|webp|mp[34]|mov|woff2?|ttf|otf|eot|o|obj|so|dll|exe|wasm|zip|tar|gz|7z|jar|whl"
)

# Article III(a) - everything under .github/ is off-limits.
PROTECTED_RE = re.compile(r"^\.github(/|$)", re.IGNORECASE)

# Article III(b) - license instruments and the README, at any depth.
DOCS_RE = re.compile(
    r"(^|/)licen[cs]es?/|(^|/)((un)?licen[cs]e|copying|copyright|readme)s?"
    r"([._-](?!(" + EXT_EXCL + r")$)[a-z0-9]+)*$",
    re.IGNORECASE,
)

# Article III(c) - temporary files, caches, build output, binaries.
PROHIBITED_RE = re.compile(
    r"~$|\.(tmp|temp|bak|sav|old|orig|rej|sw[a-z]|log)$"
    r"|(^|/)(\.DS_Store|Thumbs\.db|desktop\.ini)$"
    r"|(^|/)(__pycache__|\.pytest_cache|\.mypy_cache|\.ruff_cache"
    r"|\.ipynb_checkpoints|\.tox|\.nox|\.cache|\.parcel-cache|\.turbo"
    r"|\.nyc_output|node_modules|dist|build|out|target|coverage|obj|\.next"
    r"|\.nuxt|\.output|\.gradle|\.vercel)/"
    r"|\.(eslintcache|stylelintcache|py[co]|o|obj|a|lib|so|dylib|dll|exe|com"
    r"|msi|apk|aab|ipa|dmg|deb|rpm|class|jar|war|ear|whl|egg|wasm|pdb|bin|db"
    r"|sqlite3?|zip|tgz|tar|tar\.(gz|bz2|xz|zst)|gz|bz2|xz|7z|rar|zst)$",
    re.IGNORECASE,
)

# Article III(c) - credential and secret material.
SECRET_RE = re.compile(
    r"(^|/)\.env([._-][a-z0-9._-]*)?$|(^|/)id_(rsa|dsa|ecdsa|ed25519)$"
    r"|(^|/)credentials(\.json)?$|(^|/)\.(npmrc|netrc|pgpass|htpasswd)$"
    r"|\.(pem|key|p12|pfx|jks|keystore|kdbx|ovpn)$",
    re.IGNORECASE,
)
SECRET_OK_RE = re.compile(r"(^|/)\.env[._-](example|sample|template|dist)$", re.IGNORECASE)

# Article IV, Section 4 - generated/vendored content is exempt from the line
# counts but still counts toward the file limit.
EXCLUDED_RE = re.compile(
    r"(^|/)(package-lock\.json|npm-shrinkwrap\.json|yarn\.lock|pnpm-lock\.yaml"
    r"|bun\.lockb?|composer\.lock|Cargo\.lock|Gemfile\.lock|poetry\.lock"
    r"|Pipfile\.lock|go\.sum)$|(^|/)vendor/|\.(min\.(js|css)|map|snap|lockb?)$",
    re.IGNORECASE,
)

GIT = ["git", "-c", "core.quotepath=false"]


def git(*args: str) -> str:
    """Run git and return stdout; raise on failure."""
    proc = subprocess.run(GIT + list(args), capture_output=True, text=True,
                          encoding="utf-8", errors="replace")
    if proc.returncode != 0:
        raise RuntimeError("git {} failed: {}".format(" ".join(args), proc.stderr.strip()))
    return proc.stdout


def git_ok(*args: str) -> bool:
    return subprocess.run(GIT + list(args), capture_output=True).returncode == 0


def split_rename(raw: str) -> tuple[str, str | None]:
    """Split a numstat path into (new_path, previous_path)."""
    if " => " not in raw:
        return raw, None
    if "{" in raw and "}" in raw:
        prefix, _, rest = raw.partition("{")
        body, _, suffix = rest.partition("}")
        old_seg, _, new_seg = body.partition(" => ")
        return prefix + new_seg + suffix, prefix + old_seg + suffix
    old, _, new = raw.partition(" => ")
    return new, old


def collect(head: str, base: str | None, staged: bool) -> list[dict]:
    """Collect changed files with their line counts and status."""
    if staged:
        numstat_args = ["diff", "--cached", "--numstat", "--find-renames"]
        status_args = ["diff", "--cached", "--name-status", "--find-renames"]
    else:
        rng = "{}...{}".format(base, head)
        numstat_args = ["diff", "--numstat", "--find-renames", rng]
        status_args = ["diff", "--name-status", "--find-renames", rng]

    status = {}
    for line in git(*status_args).splitlines():
        parts = line.split("\t")
        if len(parts) < 2:
            continue
        status[parts[2] if len(parts) >= 3 else parts[1]] = parts[0]

    files = []
    for line in git(*numstat_args).splitlines():
        if not line.strip():
            continue
        added, deleted, raw = line.split("\t", 2)
        path, previous = split_rename(raw)
        changes = (int(added) if added.isdigit() else 0) \
            + (int(deleted) if deleted.isdigit() else 0)
        files.append({"path": path, "previous": previous,
                      "status": status.get(path, "M"), "changes": changes})
    return files


def blob_bytes(rev: str) -> bytes:
    """Read a blob from the object store, truncated to the scan window."""
    proc = subprocess.Popen(GIT + ["cat-file", "blob", rev], stdout=subprocess.PIPE)
    try:
        data = proc.stdout.read(BINARY_SCAN_BYTES + 1)
    finally:
        if proc.stdout:
            proc.stdout.close()
        proc.terminate()
    return data


def is_binary(rev: str) -> bool:
    try:
        return b"\x00" in blob_bytes(rev)
    except (OSError, ValueError):
        return False


def modes(head: str, staged: bool) -> dict[str, str]:
    """Map path -> git file mode (120000 symlink, 160000 submodule)."""
    out = {}
    if staged:
        for line in git("ls-files", "-s").splitlines():
            meta, _, path = line.partition("\t")
            out[path] = meta.split(" ", 1)[0]
    else:
        for line in git("ls-tree", "-r", head).splitlines():
            meta, _, path = line.partition("\t")
            out[path] = meta.split(" ", 2)[0]
    return out


def check(files: list[dict], head: str, staged: bool, mode_map: dict[str, str]) -> list[dict]:
    """Return gate results: {name, ok, detail}."""
    results = []

    def add(name: str, ok: bool, detail: str = "") -> None:
        results.append({"name": name, "ok": ok, "detail": detail})

    touched = []
    for f in files:
        touched.append(f["path"])
        if f["previous"]:
            touched.append(f["previous"])
    touched = sorted(set(touched))
    surviving = [f["path"] for f in files if f["status"] != "removed"]

    bad = [p for p in touched if PROTECTED_RE.search(p)]
    add("protected paths (.github/)", not bad, ", ".join(bad[:5]))

    bad = [p for p in touched if DOCS_RE.search(p)]
    add("protected documents (license/README)", not bad, ", ".join(bad[:5]))

    bad = [p for p in surviving if PROHIBITED_RE.search(p)]
    add("prohibited filenames", not bad, ", ".join(bad[:5]))

    bad = [p for p in surviving
           if SECRET_RE.search(p) and not SECRET_OK_RE.search(p)]
    add("credential/secret material", not bad, ", ".join(bad[:5]))

    links = []
    for path in surviving:
        mode = mode_map.get(path, "")
        if mode == "120000":
            links.append("symlink: " + path)
        elif mode == "160000":
            links.append("submodule: " + path)
    add("symlinks and submodules", not links, ", ".join(links[:5]))

    n_files = len(files)
    add("file count <= {}".format(MAX_CHANGED_FILES), n_files <= MAX_CHANGED_FILES,
        "{} files changed".format(n_files))

    counted = sum(f["changes"] for f in files if not EXCLUDED_RE.search(f["path"]))
    total = sum(f["changes"] for f in files)
    add("counted lines <= {}".format(MAX_CHANGED_LINES), counted <= MAX_CHANGED_LINES,
        "{} counted / {} total".format(counted, total))

    big = ["{} ({} lines)".format(f["path"], f["changes"]) for f in files
           if not EXCLUDED_RE.search(f["path"]) and f["changes"] > MAX_FILE_LINES]
    add("per-file lines <= {}".format(MAX_FILE_LINES), not big, ", ".join(big[:3]))

    binaries = []
    for path in surviving:
        rev = ":" + path if staged else "{}:{}".format(head, path)
        if git_ok("cat-file", "-e", rev) and is_binary(rev):
            binaries.append(path)
    add("binary content", not binaries, ", ".join(binaries[:5]))

    return results


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(
        description="Pre-flight check for the any-pr Regulations.")
    parser.add_argument("--base", default="origin/main",
                        help="base ref to compare against (default: origin/main)")
    parser.add_argument("--head", default="HEAD", help="head ref (default: HEAD)")
    parser.add_argument("--staged", action="store_true",
                        help="inspect staged changes instead of a ref range")
    parser.add_argument("--quiet", action="store_true",
                        help="print only failures and the summary")
    args = parser.parse_args(argv)

    if not git_ok("rev-parse", "--git-dir"):
        print("error: not inside a git repository", file=sys.stderr)
        return 2

    if not args.staged and not git_ok("rev-parse", "--verify", args.base + "^{commit}"):
        print("error: base ref '{}' not found; pass --base or use --staged"
              .format(args.base), file=sys.stderr)
        return 2

    try:
        files = collect(args.head, None if args.staged else args.base, args.staged)
        mode_map = modes(args.head, args.staged)
        results = check(files, args.head, args.staged, mode_map)
    except RuntimeError as exc:
        print("error: {}".format(exc), file=sys.stderr)
        return 2

    scope = "staged changes" if args.staged else "{}...{}".format(args.base, args.head)
    print("regulation-lint -- any-pr Regulations pre-flight check")
    print("scope: {}".format(scope))
    print("files changed: {}".format(len(files)))
    print()

    failed = 0
    for i, r in enumerate(results, 1):
        if not r["ok"]:
            failed += 1
        if r["ok"] and args.quiet:
            continue
        line = "[{:>2}/{}] {:<38} {}".format(i, len(results), r["name"],
                                             "PASS" if r["ok"] else "FAIL")
        if not r["ok"] and r["detail"]:
            line += "  <- " + r["detail"]
        print(line)

    print()
    if failed:
        print("VERDICT: {} gate(s) failed -- this PR would be CLOSED without "
              "merger.".format(failed))
        return 1
    print("VERDICT: all gates passed -- this PR is eligible for automatic merger,")
    print("subject to merger succeeding against the base branch (Article II, "
          "Section 3).")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
