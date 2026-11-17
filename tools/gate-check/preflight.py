#!/usr/bin/env python3
"""preflight.py -- reproduce this repository's auto-merge gate locally.

The gate lives in .github/workflows/auto-merge.yml and is intentionally strict:
a pull request that fails any check is closed, not asked to rebase. This script
runs the same checks against your working tree before you open one, so you find
out in a second instead of after Auto-merge PRs has closed the PR.

    python tools/gate-check/preflight.py                # against origin/main
    python tools/gate-check/preflight.py upstream/main  # against another ref
    python tools/gate-check/preflight.py --files a.py   # only these paths

Exit code 0 means the gate should merge the PR; 1 means it would be closed.

Approximations, stated plainly:

  * Line counts come from `git diff --numstat`, which is what GitHub reports as
    `.changes` (additions + deletions). Rename detection differs at the margins.
  * The binary test scans the first 50 MB for a NUL byte, exactly as the
    workflow does. UTF-16 text trips it; store text as UTF-8.
  * "Head moved during inspection" and merge conflicts cannot be predicted from
    a local tree; the conflict check here is best effort via `git merge-tree`.
"""

import json
import os
import re
import subprocess
import sys

MAX_FILES, MAX_LINES, MAX_FILE_LINES = 20, 500, 300

EXT_EXCL = ("c|h|cpp|cc|cxx|hpp|hh|cs|go|rs|rb|erb|py|pyi|java|kt|kts|scala|clj|ex|exs|"
            "erl|hs|ml|fs|vb|swift|php|pl|pm|r|lua|dart|zig|nim|cr|jl|sh|bash|zsh|fish|"
            "ps1|bat|cmd|js|jsx|ts|tsx|mjs|cjs|mts|cts|vue|svelte|astro|elm|css|scss|"
            "sass|less|styl|json|jsonc|ya?ml|toml|xml|ini|cfg|conf|sql|graphql|proto|tf|"
            "nix|ipynb|lock|csv|png|jpe?g|gif|svg|ico|webp|mp[34]|mov|woff2?|ttf|otf|eot|"
            "o|obj|so|dll|exe|wasm|zip|tar|gz|7z|jar|whl")

PROTECTED = r"^\.github(/|$)"
DOCS = (r"(^|/)licen[cs]es?/|(^|/)((un)?licen[cs]e|copying|copyright|readme)s?"
        r"([._-](?!(" + EXT_EXCL + r")$)[a-z0-9]+)*$")
PROHIBITED = (
    r"~$|\.(tmp|temp|bak|sav|old|orig|rej|sw[a-z]|log)$"
    r"|(^|/)(\.DS_Store|Thumbs\.db|desktop\.ini)$"
    r"|(^|/)(__pycache__|\.pytest_cache|\.mypy_cache|\.ruff_cache|\.ipynb_checkpoints|\.tox|"
    r"\.nox|\.cache|\.parcel-cache|\.turbo|\.nyc_output|node_modules|dist|build|out|target|"
    r"coverage|obj|\.next|\.nuxt|\.output|\.gradle|\.vercel)/"
    r"|\.(eslintcache|stylelintcache|py[co]|o|obj|a|lib|so|dylib|dll|exe|com|msi|apk|aab|"
    r"ipa|dmg|deb|rpm|class|jar|war|ear|whl|egg|wasm|pdb|bin|db|sqlite3?|zip|tgz|tar|"
    r"tar\.(gz|bz2|xz|zst)|gz|bz2|xz|7z|rar|zst)$")
SECRET = (r"(^|/)\.env([._-][a-z0-9._-]*)?$|(^|/)id_(rsa|dsa|ecdsa|ed25519)$"
          r"|(^|/)credentials(\.json)?$|(^|/)\.(npmrc|netrc|pgpass|htpasswd)$"
          r"|\.(pem|key|p12|pfx|jks|keystore|kdbx|ovpn)$")
SECRET_OK = r"(^|/)\.env[._-](example|sample|template|dist)$"
EXCLUDED = (r"(^|/)(package-lock\.json|npm-shrinkwrap\.json|yarn\.lock|pnpm-lock\.yaml|"
            r"bun\.lockb?|composer\.lock|Cargo\.lock|Gemfile\.lock|poetry\.lock|"
            r"Pipfile\.lock|go\.sum)$|(^|/)vendor/|\.(min\.(js|css)|map|snap|lockb?)$")
# The rules the gate applies, in the order it applies them. `--list-rules`
# prints this table so it can be read against the workflow without opening it.
RULES = [
    ("protected", ".github/ at any depth, including renames into or out of it", PROTECTED),
    ("documents", "licence instruments and README variants, at any depth", DOCS),
    ("prohibited", "temporary, cached, build-output and binary paths", PROHIBITED),
    ("secret", "credential and key filenames", SECRET),
    ("excluded", "generated or vendored files left out of the line count", EXCLUDED),
]

# Not part of the workflow: catching a key pasted into a file whose name is fine.
SECRET_LITERAL = re.compile(
    r"ghp_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|AKIA[0-9A-Z]{16}"
    r"|-----BEGIN [A-Z ]*PRIVATE KEY-----|sk-[A-Za-z0-9]{32,}")

# A file may opt out of the literal scan by containing this marker, which is
# what a fixture needs: a test for "reject key-shaped text" has to contain
# key-shaped text. The marker is deliberate and greppable, so a real secret
# cannot slip through by accident.
FIXTURE_MARKER = "gate-check: allow-secret-fixtures"

BOM = bytes([0xEF, 0xBB, 0xBF])
CRLF = bytes([13, 10])
LF = bytes([10])

RED, GRN, YEL, DIM, RST = "\033[31m", "\033[32m", "\033[33m", "\033[2m", "\033[0m"
FAILS, WARNS = [], []


EXPLAIN = False
STAGED = False


def fail(msg, rule=None):
    """Record a failure, naming the rule that matched when --explain is on."""
    FAILS.append(f"[{rule}] {msg}" if EXPLAIN and rule else msg)


def warn(msg):
    WARNS.append(msg)


def git(*args):
    """Run git; return stdout bytes, or None if the command failed."""
    p = subprocess.run(["git", *args], capture_output=True)
    return p.stdout if p.returncode == 0 else None


class Change:
    """One entry of the diff: post-merge path, previous path, status, line deltas."""

    def __init__(self, path, prev="", status="M", adds=0, dels=0, mode="100644"):
        self.path, self.prev, self.status = path, prev, status
        self.adds, self.dels, self.mode = adds, dels, mode


def text(b):
    return b.decode("utf-8", "surrogateescape")


def diffargs(base):
    """Compare against the index instead of a ref when --staged is set."""
    return ["--cached"] if STAGED else [base]


def numstat(base):
    """git diff --numstat -M -z, including the two-path form used by renames."""
    raw = git("diff", "--numstat", "-M", "-z", *diffargs(base))
    if raw is None:
        return []
    toks, out, i = raw.split(b"\0"), [], 0
    while i < len(toks):
        parts = toks[i].split(b"\t")
        if len(parts) >= 2 and (parts[0].isdigit() or parts[0] == b"-"):
            adds = 0 if parts[0] == b"-" else int(parts[0])
            dels = 0 if parts[1] == b"-" else int(parts[1])
            if len(parts) >= 3 and parts[2]:
                path, prev = text(parts[2]), ""
            else:  # rename: old and new arrive as separate NUL tokens
                path, prev = text(toks[i + 2]), text(toks[i + 1])
                i += 2
            out.append(Change(path, prev, "M", adds, dels))
        i += 1
    return out


def modes(base):
    """git diff --raw -M -z -> {path: new mode}, for symlink/submodule detection."""
    raw = git("diff", "--raw", "-M", "-z", *diffargs(base))
    if raw is None:
        return {}
    toks, out, i = raw.split(b"\0"), {}, 0
    while i < len(toks):
        if toks[i].startswith(b":"):
            meta = toks[i][1:].split(b" ")
            if meta and meta[-1][:1] in (b"R", b"C"):
                out[text(toks[i + 2])] = text(meta[1])
                i += 2
            else:
                out[text(toks[i + 1])] = text(meta[1])
                i += 1
        i += 1
    return out


def untracked():
    raw = git("ls-files", "--others", "--exclude-standard", "-z") or b""
    out = []
    for tok in raw.split(b"\0"):
        if not tok:
            continue
        path = text(tok)
        adds = 0
        if os.path.isfile(path) and not os.path.islink(path):
            with open(path, "rb") as f:
                adds = f.read().count(b"\n")
        mode = "120000" if os.path.islink(path) else "100644"
        out.append(Change(path, "", "A", adds, 0, mode))
    return out


def check(changes, baseline_known):
    for c in changes:
        # Status D means the path is gone after merge; the workflow skips those.
        c.status = "D" if not os.path.exists(c.path) else ("M" if baseline_known else "A")

    if len(changes) > MAX_FILES:
        fail(f"{len(changes)} files changed, limit is {MAX_FILES} "
             f"(lockfiles count as files; there is no exemption)", rule="max-files")

    for c in changes:
        for cand in {c.path, c.prev} - {""}:
            if re.search(PROTECTED, cand, re.I):
                fail(f"{cand}: under .github/ (renames into or out of it count)",
                     rule="protected")
            if re.search(DOCS, cand, re.I):
                fail(f"{cand}: licence or README file — never accepted via PR",
                     rule="documents")
        if c.status != "D":
            if re.search(PROHIBITED, c.path, re.I):
                fail(f"{c.path}: temp/cache/build-artifact/binary path", rule="prohibited")
            if re.search(SECRET, c.path, re.I) and not re.search(SECRET_OK, c.path, re.I):
                fail(f"{c.path}: looks like a credential or key file", rule="secret")
        if c.status != "D" and c.mode == "120000":
            fail(f"{c.path}: symlink (git mode 120000)", rule="symlink")
        if c.status != "D" and c.mode == "160000":
            fail(f"{c.path}: submodule (git mode 160000)", rule="submodule")

    counted = 0
    for c in changes:
        if re.search(EXCLUDED, c.path, re.I):
            continue
        changed = c.adds + c.dels
        counted += changed
        if changed > MAX_FILE_LINES:
            fail(f"{c.path}: {changed} changed lines, per-file limit is {MAX_FILE_LINES}",
                 rule="max-file-lines")
    if counted > MAX_LINES:
        fail(f"{counted} counted lines, limit is {MAX_LINES} "
             f"(lockfiles, vendor/, *.min.*, *.map and *.snap are excluded)",
             rule="max-lines")

    for c in changes:
        if c.status == "D" or not os.path.isfile(c.path) or os.path.islink(c.path):
            continue
        with open(c.path, "rb") as f:
            blob = f.read(50 * 1024 * 1024)
        if b"\0" in blob:
            fail(f"{c.path}: contains a NUL byte, so the gate treats it as binary "
                 f"(UTF-16 text trips this too; store text as UTF-8)", rule="binary")
            continue
        head = text(blob[:2_000_000])
        if blob.startswith(BOM):
            warn(f"{c.path}: starts with a UTF-8 BOM")
        crlf = blob.count(CRLF)
        if crlf and blob.count(LF) != crlf:
            warn(f"{c.path}: mixes CRLF and LF line endings")
        if SECRET_LITERAL.search(head) and FIXTURE_MARKER not in head:
            fail(f"{c.path}: contains what looks like a live credential literal "
                 f"(add '{FIXTURE_MARKER}' if it is a fixture)", rule="secret-literal")

    return counted


def main(argv):
    if git("rev-parse", "--git-dir") is None:
        print(f"{RED}not a git repository{RST}")
        return 1
    os.chdir(text(git("rev-parse", "--show-toplevel")).strip())

    if argv[:1] == ["--list-rules"]:
        for name, purpose, pattern in RULES:
            print(name)
            print(f"  {purpose}")
            print(f"  {pattern}")
            print()
        return 0

    global EXPLAIN, STAGED, MAX_FILES, MAX_LINES, MAX_FILE_LINES
    EXPLAIN = "--explain" in argv
    STAGED = "--staged" in argv
    as_json = "--json" in argv

    # The gate's numbers are also the useful numbers for a stricter local
    # habit, so they can be overridden without editing the copy.
    limits = {"--max-files": MAX_FILES, "--max-lines": MAX_LINES,
              "--max-file-lines": MAX_FILE_LINES}
    rest, i = [], 0
    while i < len(argv):
        arg = argv[i]
        if arg in limits and i + 1 < len(argv):
            limits[arg] = int(argv[i + 1])
            i += 2
            continue
        if arg not in ("--json", "--explain", "--staged"):
            rest.append(arg)
        i += 1
    MAX_FILES, MAX_LINES, MAX_FILE_LINES = (
        limits["--max-files"], limits["--max-lines"], limits["--max-file-lines"])
    argv = rest

    only = None
    base = "origin/main"
    if argv[:1] == ["--files"]:
        only = argv[1:]
    elif argv:
        base = argv[0]

    if only is not None:
        changes = []
        for path in only:
            if not os.path.exists(path):
                fail(f"{path}: does not exist")
                continue
            with open(path, "rb") as f:
                adds = f.read().count(b"\n") if os.path.isfile(path) else 0
            changes.append(Change(path, "", "A", adds, 0,
                                  "120000" if os.path.islink(path) else "100644"))
        print(f"checking {len(changes)} named path(s)")
    else:
        if git("rev-parse", "--verify", "-q", base) is None:
            print(f"{RED}{base} does not exist — git fetch first{RST}")
            return 1
        merge_base = text(git("merge-base", base, "HEAD") or b"").strip()
        changes = numstat(merge_base or base)
        known = modes(merge_base or base)
        for c in changes:
            c.mode = known.get(c.path, c.mode)
        if not STAGED:
            changes += untracked()
        shallow = text(git("rev-parse", "--is-shallow-repository") or b"true").strip() == "true"
        if STAGED:
            print("checking the index (--staged)")
        else:
            print(f"base: {base}  (merge-base {merge_base[:9] or 'unavailable, shallow clone'})")
        if not merge_base or shallow:
            warn("shallow clone: history is truncated, so the conflict probe is skipped "
                 "(git fetch --unshallow to enable it)")
        elif not git("status", "--porcelain") and git("merge-tree", "--write-tree", base) is None:
            warn("git merge-tree reports a conflict with the base branch; rebase first")

    counted = check(changes, baseline_known=only is None)

    if as_json:
        print(json.dumps({
            "base": None if only is not None else base,
            "files": len(changes),
            "counted_lines": counted,
            "failures": FAILS,
            "warnings": WARNS,
            "ok": not FAILS,
        }, indent=2))
        return 1 if FAILS else 0

    print(f"files={len(changes)}  counted_lines={counted}")

    for w in WARNS:
        print(f"{YEL}WARN{RST} {w}")
    for f in FAILS:
        print(f"{RED}FAIL{RST} {f}")
    if FAILS:
        print(f"\n{RED}The gate would close this PR. Fix the items above first.{RST}")
        return 1
    print(f"\n{GRN}PASS{RST} within {MAX_FILES} files / {MAX_LINES} lines / "
          f"{MAX_FILE_LINES} per file, no protected path, no binary/link/secret.")
    print(f"{DIM}Still on you: keep the PR out of draft, and do not push while the "
          f"gate is running.{RST}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
