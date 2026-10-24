#!/usr/bin/env python3
"""gate-sim -- plan a pull request against the Regulations before writing it.

`tools/regulation-lint.py` inspects a git range, so the changes must already
exist before it can say anything. `gate-sim` inverts that order: it takes a
*plan* -- the paths you intend to add, modify, remove, or rename, with their
changed-line counts -- and reports the verdict the Workflow would reach.

Sizing a change before writing it is cheap. Discovering only after the
Workflow has closed the pull request that Article IV's limits were exceeded
is not.

This is a convenience, not an authority (Article VI, Section 4). The Workflow
is the sole enforcement instrument. A plan declares its own facts: `gate-sim`
cannot open blobs, so symlinks, submodules, and binary content are read from
the plan rather than detected in the git objects. See USAGE.md for the plan
schema and worked examples.

    python tools/gate-sim/gate_sim.py --plan plan.json
    python tools/gate-sim/gate_sim.py --add misc/demo.py=40 --modify docs/x.md=12

Exit status: 0 all gates pass, 1 at least one gate fails, 2 usage error.
"""

from __future__ import annotations

import argparse
import json
import re
import sys

MAX_CHANGED_FILES = 20
MAX_CHANGED_LINES = 500
MAX_FILE_LINES = 300

STATUSES = ("added", "modified", "removed", "renamed")

# --- gates mirrored verbatim from .github/workflows/auto-merge.yml ----------

PROTECTED_RE = re.compile(r"^\.github(/|$)", re.IGNORECASE)

# Suffixes excluded from DOCS_RE's tail so `readme-utils.ts` is not mistaken
# for a README, matching the Workflow's EXT_EXCL.
_EXT_EXCL = (
    "c|h|cpp|cc|cxx|hpp|hh|cs|go|rs|rb|erb|py|pyi|java|kt|kts|scala|clj|ex|exs|"
    "erl|hs|ml|fs|vb|swift|php|pl|pm|r|lua|dart|zig|nim|cr|jl|sh|bash|zsh|fish|"
    "ps1|bat|cmd|js|jsx|ts|tsx|mjs|cjs|mts|cts|vue|svelte|astro|elm|css|scss|"
    "sass|less|styl|json|jsonc|ya?ml|toml|xml|ini|cfg|conf|sql|graphql|proto|"
    "tf|nix|ipynb|lock|csv|png|jpe?g|gif|svg|ico|webp|mp[34]|mov|woff2?|ttf|"
    "otf|eot|o|obj|so|dll|exe|wasm|zip|tar|gz|7z|jar|whl"
)

DOCS_RE = re.compile(
    r"(^|/)licen[cs]es?/|(^|/)((un)?licen[cs]e|copying|copyright|readme)s?"
    r"([._-](?!(?:" + _EXT_EXCL + r")$)[a-z0-9]+)*$",
    re.IGNORECASE,
)

PROHIBITED_RE = re.compile(
    r"~$|\.(tmp|temp|bak|sav|old|orig|rej|sw[a-z]|log)$"
    r"|(^|/)(\.DS_Store|Thumbs\.db|desktop\.ini)$"
    r"|(^|/)(__pycache__|\.pytest_cache|\.mypy_cache|\.ruff_cache|\.ipynb_checkpoints"
    r"|\.tox|\.nox|\.cache|\.parcel-cache|\.turbo|\.nyc_output|node_modules|dist"
    r"|build|out|target|coverage|obj|\.next|\.nuxt|\.output|\.gradle|\.vercel)/"
    r"|\.(eslintcache|stylelintcache|py[co]|o|obj|a|lib|so|dylib|dll|exe|com|msi"
    r"|apk|aab|ipa|dmg|deb|rpm|class|jar|war|ear|whl|egg|wasm|pdb|bin|db|sqlite3?"
    r"|zip|tgz|tar|tar\.(gz|bz2|xz|zst)|gz|bz2|xz|7z|rar|zst)$",
    re.IGNORECASE,
)

SECRET_RE = re.compile(
    r"(^|/)\.env([._-][a-z0-9._-]*)?$|(^|/)id_(rsa|dsa|ecdsa|ed25519)$"
    r"|(^|/)credentials(\.json)?$|(^|/)\.(npmrc|netrc|pgpass|htpasswd)$"
    r"|\.(pem|key|p12|pfx|jks|keystore|kdbx|ovpn)$",
    re.IGNORECASE,
)

SECRET_OK_RE = re.compile(r"(^|/)\.env[._-](example|sample|template|dist)$", re.IGNORECASE)

EXCLUDED_RE = re.compile(
    r"(^|/)(package-lock\.json|npm-shrinkwrap\.json|yarn\.lock|pnpm-lock\.yaml|"
    r"bun\.lockb?|composer\.lock|Cargo\.lock|Gemfile\.lock|poetry\.lock|"
    r"Pipfile\.lock|go\.sum)$|(^|/)vendor/|\.(min\.(js|css)|map|snap|lockb?)$",
    re.IGNORECASE,
)


class PlanError(Exception):
    """The plan could not be read at all -- exit 2, not a gate failure."""


def make_entry(path, status="modified", lines=0, previous_path=None, mode="100644", binary=False):
    """One planned file. `lines` is additions + deletions, as GitHub reports it."""
    return {
        "path": path,
        "status": status,
        "lines": int(lines),
        "previous_path": previous_path,
        "mode": str(mode),
        "binary": bool(binary),
    }


def _unique(items):
    return list(dict.fromkeys(items))


def surviving(entries):
    """Entries whose path exists after the merge -- Article III(c) skips removals."""
    return [e for e in entries if e["status"] != "removed"]


def evaluate(entries):
    """Run the nine gates. Returns a list of {name, ok, note} dicts."""
    alive = surviving(entries)

    def touched(predicate):
        # A rename is checked on both sides: into a protected path, or away
        # from a protected document, both count as a modification.
        return [p for e in entries for p in (e["path"], e["previous_path"]) if p and predicate(p)]

    counted = sum(e["lines"] for e in entries if not EXCLUDED_RE.search(e["path"]))
    n_files = len(entries)

    # (gate name, note when passing, offending paths)
    gates = [
        ("protected paths (.github/)", "no path under .github/",
         touched(PROTECTED_RE.search)),
        ("protected documents (license/README)", "no license instrument or README, any depth",
         touched(DOCS_RE.search)),
        ("prohibited filenames", "no temp files, caches, build output, or binaries",
         [e["path"] for e in alive if PROHIBITED_RE.search(e["path"])]),
        ("credential/secret material", "no .env, private keys, or credential stores",
         [e["path"] for e in alive
          if SECRET_RE.search(e["path"]) and not SECRET_OK_RE.search(e["path"])]),
        ("symlinks and submodules", "no symlinks or submodules",
         ["%s (mode %s)" % (e["path"], e["mode"])
          for e in alive if e["mode"] in ("120000", "160000")]),
        ("file count <= %d" % MAX_CHANGED_FILES,
         "%d/%d files" % (n_files, MAX_CHANGED_FILES),
         ["%d files changed" % n_files] if n_files > MAX_CHANGED_FILES else []),
        ("counted lines <= %d" % MAX_CHANGED_LINES,
         "%d/%d counted lines" % (counted, MAX_CHANGED_LINES),
         ["%d counted lines" % counted] if counted > MAX_CHANGED_LINES else []),
        ("per-file lines <= %d" % MAX_FILE_LINES,
         "no file over %d changed lines" % MAX_FILE_LINES,
         ["%s (%d lines)" % (e["path"], e["lines"]) for e in entries
          if not EXCLUDED_RE.search(e["path"]) and e["lines"] > MAX_FILE_LINES]),
        ("binary content (declared)", "no binary blobs",
         [e["path"] for e in alive if e["binary"]]),
    ]

    report = []
    for name, passing_note, offenders in gates:
        bad = _unique(offenders)
        report.append({"name": name, "ok": not bad,
                       "note": ", ".join(bad[:5]) if bad else passing_note})
    return report


def _split_lines(spec):
    path, sep, lines = spec.rpartition("=")
    if not sep or not lines.isdigit():
        raise PlanError("expected PATH=LINES, got %r" % spec)
    return path, int(lines)


def _read_json(raw):
    files = raw.get("files") if isinstance(raw, dict) else raw
    if not isinstance(files, list):
        raise PlanError("plan must be a list, or an object with a 'files' list")
    entries = []
    for item in files:
        if not isinstance(item, dict) or not item.get("path"):
            raise PlanError("every plan entry needs a 'path'")
        status = item.get("status", "modified")
        if status not in STATUSES:
            raise PlanError("unknown status %r (expected one of %s)" % (status, ", ".join(STATUSES)))
        entries.append(make_entry(
            item["path"], status=status, lines=item.get("lines", 0),
            previous_path=item.get("previous_path"), mode=item.get("mode", "100644"),
            binary=item.get("binary", False),
        ))
    return entries


def read_plan(args):
    """Build the entry list from --plan, stdin, and the inline flags."""
    if args.plan:
        try:
            with open(args.plan, "r", encoding="utf-8") as handle:
                raw = json.load(handle)
        except (OSError, ValueError) as exc:
            raise PlanError("cannot read plan %s: %s" % (args.plan, exc))
    else:
        raw = None
        if not sys.stdin.isatty():
            text = sys.stdin.read().strip()
            if text:
                try:
                    raw = json.loads(text)
                except ValueError as exc:
                    raise PlanError("cannot parse plan on stdin: %s" % exc)

    entries = _read_json(raw) if raw is not None else []

    for spec in args.add or []:
        path, lines = _split_lines(spec)
        entries.append(make_entry(path, "added", lines))

    for spec in args.modify or []:
        path, lines = _split_lines(spec)
        entries.append(make_entry(path, "modified", lines))

    for spec in args.remove or []:
        entries.append(make_entry(spec, "removed"))

    for spec in args.rename or []:
        body, sep, lines = spec.rpartition("=")
        if not sep:
            body, lines = spec, "0"
        old, colon, new = body.partition(":")
        if not colon:
            raise PlanError("expected OLD:NEW, got %r" % spec)
        entries.append(make_entry(new, "renamed", lines, previous_path=old))

    return entries


def parse_args(argv):
    parser = argparse.ArgumentParser(
        prog="gate-sim",
        description="Simulate the any-pr auto-merge gates against a planned pull request.",
    )
    parser.add_argument("--plan", metavar="FILE", help="JSON plan file")
    parser.add_argument("--add", metavar="PATH=LINES", action="append", help="file to add")
    parser.add_argument("--modify", metavar="PATH=LINES", action="append", help="file to modify")
    parser.add_argument("--remove", metavar="PATH", action="append", help="file to remove")
    parser.add_argument("--rename", metavar="OLD:NEW[=LINES]", action="append", help="file to rename")
    parser.add_argument("--quiet", action="store_true", help="failures and the verdict only")
    return parser.parse_args(argv)


def main(argv=None):
    args = parse_args(sys.argv[1:] if argv is None else argv)

    try:
        entries = read_plan(args)
    except PlanError as exc:
        print("gate-sim: %s" % exc, file=sys.stderr)
        return 2

    if not entries:
        print("gate-sim: the plan is empty; nothing to check", file=sys.stderr)
        return 2

    report = evaluate(entries)
    counted = sum(e["lines"] for e in entries if not EXCLUDED_RE.search(e["path"]))

    if not args.quiet:
        print("gate-sim -- any-pr Regulations pre-flight planner")
        print("plan: %d file(s), %d counted line(s)" % (len(entries), counted))
        print()

    failures = []
    printed = False
    for index, item in enumerate(report, start=1):
        if args.quiet and item["ok"]:
            continue
        print("[%2d/%d] %-42s %-4s %s"
              % (index, len(report), item["name"], "PASS" if item["ok"] else "FAIL", item["note"]))
        printed = True
        if not item["ok"]:
            failures.append(item)

    if printed:
        print()
    if failures:
        print("VERDICT: %d gate(s) failed -- the Workflow would close this PR:" % len(failures))
        for item in failures:
            print("  - %s: %s" % (item["name"], item["note"]))
        print("\nSplit or amend the plan, then run gate-sim again.")
        return 1

    print("VERDICT: all gates passed -- this plan is eligible for automatic merger,")
    print("subject to merger succeeding against the base branch (Article II, Section 3).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
