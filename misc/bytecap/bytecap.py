#!/usr/bin/env python3
"""bytecap — measure a change by bytes, not by line counts.

Article IV caps *counted lines*: 20 files, 500 lines total, 300 lines per
file. Section 4 then exempts generated and vendored content from the line
counts, on the sound grounds that a lockfile's line count says nothing about
review effort.

The exemption removes the only ceiling on the one thing it does not measure:
bytes. A plan of nineteen ordinary files plus a single generated file sails
through every gate regardless of how large that file is.

    $ python3 gate_sim.py --add Cargo.lock=5000000
    VERDICT: all gates passed

`bytecap` reports the number the Regulations currently do not consult. It
takes the same plan shape `gate-sim` accepts, resolves each path against a
git tree when one is available, and prints per-file and total byte counts
alongside a baseline ceiling. It changes no verdict: the Workflow is the sole
enforcement instrument (Article VI, Section 4), and nothing here is a gate.

    python3 bytecap.py --staged
    python3 bytecap.py --base main~5
    python3 bytecap.py --plan plan.json --max-file 1048576 --max-total 5242880

Exit status: 0 under every ceiling, 1 at least one over, 2 usage error.
Standard library only.
"""

import argparse
import json
import subprocess
import sys

# Baseline ceilings. Chosen to sit far above real source files (the largest
# hand-written file in this repository is well under 300 KB) and far below a
# runaway generated artifact. These are advisory: no gate reads them.
DEFAULT_MAX_FILE = 1024 * 1024        # 1 MiB
DEFAULT_MAX_TOTAL = 5 * 1024 * 1024   # 5 MiB

EXCLUDED_RE_HINT = (
    "generated/vendored names, which Article IV Section 4 exempts from the "
    "line counts but which are still measured here"
)


def human(n: int) -> str:
    """Render a byte count compactly."""
    for unit in ("B", "KiB", "MiB", "GiB"):
        if abs(n) < 1024 or unit == "GiB":
            return f"{n:.0f} {unit}" if unit == "B" else f"{n:.1f} {unit}"
        n /= 1024
    return f"{n} B"


def git(*args: str) -> str:
    """Run git and return stdout, raising on failure."""
    out = subprocess.run(
        ("git",) + args,
        capture_output=True,
        text=True,
        check=False,
    )
    if out.returncode != 0:
        raise RuntimeError(out.stderr.strip() or f"git {' '.join(args)} failed")
    return out.stdout


def blobs_from_range(base: str) -> list[tuple[str, int]]:
    """Paths changed in base..HEAD with the size of their post-image blob.

    A path removed by the range has no post-image; it is reported at 0 bytes
    because a merge that deletes a file does not grow the tree.
    """
    names = [p for p in git("diff", "--name-only", f"{base}..HEAD").splitlines() if p]
    return [(p, blob_size("HEAD", p)) for p in names]


def blobs_from_staged() -> list[tuple[str, int]]:
    """Paths staged for the next commit, measured at their staged blob."""
    names = [p for p in git("diff", "--cached", "--name-only").splitlines() if p]
    return [(p, blob_size(":0", p)) for p in names]


def blob_size(rev: str, path: str) -> int:
    """Size in bytes of path's blob at rev, or 0 when the path is absent."""
    try:
        return int(git("cat-file", "-s", f"{rev}:{path}").strip())
    except RuntimeError:
        return 0


def blobs_from_plan(plan_path: str) -> list[tuple[str, int]]:
    """Read a gate-sim style plan and measure each declared path.

    gate-sim plans carry changed-line counts, not bytes. When a path exists
    in the working tree its size is read from disk; otherwise the plan's
    optional `bytes` field is used, and a plan that supplies neither is
    reported at 0 rather than guessed at.
    """
    with open(plan_path, encoding="utf-8") as fh:
        raw = json.load(fh)
    files = raw.get("files", raw) if isinstance(raw, dict) else raw
    out = []
    for entry in files:
        if isinstance(entry, str):
            entry = {"path": entry}
        path = entry.get("path")
        if not path:
            raise ValueError("plan entry without a path")
        size = None
        try:
            with open(path, "rb") as fh:
                size = len(fh.read())
        except OSError:
            size = entry.get("bytes")
        out.append((path, int(size or 0)))
    return out


def report(rows: list[tuple[str, int]], max_file: int, max_total: int) -> int:
    """Print the table and return an exit status."""
    if not rows:
        print("no files to measure")
        return 0

    rows = sorted(rows, key=lambda r: -r[1])
    width = max(len(p) for p, _ in rows)
    total = sum(s for _, s in rows)

    over_file = [p for p, s in rows if s > max_file]
    for path, size in rows:
        mark = "OVER" if size > max_file else "    "
        print(f"  {mark}  {path:<{width}}  {human(size):>10}")

    print(f"\n  files       {len(rows)}")
    print(f"  total       {human(total)}")
    print(f"  per-file    ceiling {human(max_file)}"
          f"{'  -- ' + str(len(over_file)) + ' over' if over_file else '  -- ok'}")
    print(f"  per-change  ceiling {human(max_total)}"
          f"{'  -- over' if total > max_total else '  -- ok'}")
    print(f"\n  note: {EXCLUDED_RE_HINT}")

    return 1 if (over_file or total > max_total) else 0


def self_test() -> int:
    """Exercise the formatter and the threshold logic; no network, no git."""
    checks = 0
    failures = []

    def check(cond, label):
        nonlocal checks
        checks += 1
        if not cond:
            failures.append(label)

    check(human(0) == "0 B", "human(0)")
    check(human(1) == "1 B", "human(1)")
    check(human(1024) == "1.0 KiB", "human(1024)")
    check(human(1024 * 1024) == "1.0 MiB", "human(1 MiB)")
    check(human(5 * 1024 * 1024 * 2) == "10.0 MiB", "human(10 MiB)")

    # A small plan passes; one oversize file fails; a large total fails.
    import io
    import contextlib

    def quiet(*a, **k):
        buf = io.StringIO()
        with contextlib.redirect_stdout(buf):
            rc = report(*a, **k)
        return rc, buf.getvalue()

    rc, _ = quiet([("a.py", 100), ("b.py", 200)], 1024, 10_000)
    check(rc == 0, "small plan passes")
    rc, _ = quiet([("big.lock", 2048)], 1024, 10_000)
    check(rc == 1, "oversize file fails")
    rc, _ = quiet([("x", 600), ("y", 600)], 1024, 1000)
    check(rc == 1, "oversize total fails")
    rc, text = quiet([], 1024, 10_000)
    check(rc == 0 and "no files" in text, "empty plan is a no-op")

    for f in failures:
        print(f"  FAIL {f}")
    print(f"\n{checks} checks, {len(failures)} failed")
    return 0 if not failures else 1


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(
        prog="bytecap",
        description="Measure a change by bytes, not by counted lines.",
    )
    src = parser.add_mutually_exclusive_group()
    src.add_argument("--staged", action="store_true",
                     help="measure what git commit would take")
    src.add_argument("--base", metavar="REV",
                     help="measure REV..HEAD, e.g. origin/main")
    src.add_argument("--plan", metavar="FILE",
                     help="read a gate-sim style plan and measure its paths")
    parser.add_argument("--max-file", type=int, default=DEFAULT_MAX_FILE,
                        help=f"per-file ceiling in bytes (default {DEFAULT_MAX_FILE})")
    parser.add_argument("--max-total", type=int, default=DEFAULT_MAX_TOTAL,
                        help=f"per-change ceiling in bytes (default {DEFAULT_MAX_TOTAL})")
    parser.add_argument("--self-test", action="store_true",
                        help="run the built-in checks and exit")
    args = parser.parse_args(argv)

    if args.self_test:
        return self_test()

    try:
        if args.plan:
            rows = blobs_from_plan(args.plan)
        elif args.staged:
            rows = blobs_from_staged()
        elif args.base:
            rows = blobs_from_range(args.base)
        else:
            parser.error("give --staged, --base REV, or --plan FILE")
            return 2
    except (OSError, ValueError, RuntimeError) as exc:
        print(f"bytecap: {exc}", file=sys.stderr)
        return 2

    return report(rows, args.max_file, args.max_total)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
