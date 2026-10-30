#!/usr/bin/env python3
"""sensitivity -- does any parameter in the theatre model actually matter?

The theatre model reports 10/10 against the historical record. That number is
suspect because its weights were fitted. A more damning question is whether the
model responds to its inputs at all: change a number, does the war change?

It mostly does not. This tool measures that, one parameter at a time, and prints
how many of the ten theatres move for how large a change. The result is a
one-line indictment of the model, which is more useful than another 10/10.

    python3 sensitivity.py                  full sweep
    python3 sensitivity.py --quick          fewer steps per parameter
    python3 sensitivity.py --self-test

Standard library only. Read-only: every change is reverted before the next.
"""

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import theatre as T  # noqa: E402

# The parameters worth sweeping, and a range that brackets the plausible.
# Each entry: label, getter, setter, values. Written as closures so the sweep
# does not need to know the shape of the data tables.
SWEEPS = [
    ("axis doctrine", 0.60, 2.40),
    ("allied doctrine", 0.50, 2.00),
    ("axis production", 0.50, 2.00),
    ("allied production", 0.60, 2.20),
    ("axis attrition tol", 0.60, 1.40),
    ("allied attrition tol", 0.70, 1.60),
    ("axis industry 1941", 0.40, 3.00),
    ("allied industry 1944", 0.25, 2.00),
    ("axis industry 1944", 0.40, 2.50),
]


def outcome(seed: int = 1939) -> str:
    """Fingerprint the war: one letter per theatre, in declaration order."""
    r = T.simulate(seed)
    return "".join(t["control"][0] for t in r["theatres"])


def apply(name: str, scale: float, original: dict) -> None:
    """Multiply one parameter by `scale`, in place."""
    if name.endswith("doctrine"):
        side = name.split()[0]
        T.SIDES[side]["doctrine"] = original[name] * scale
    elif name.endswith("production"):
        side = name.split()[0]
        T.SIDES[side]["production"] = original[name] * scale
    elif name.endswith("attrition tol"):
        side = name.split()[0]
        T.SIDES[side]["attrition_tolerance"] = original[name] * scale
    elif name.startswith("axis industry"):
        year = name.split()[-1]
        T.INDUSTRY["axis"][year] = original[name] * scale
    elif name.startswith("allied industry"):
        year = name.split()[-1]
        T.INDUSTRY["allied"][year] = original[name] * scale


def snapshot() -> dict:
    """Remember every value the sweeps touch."""
    out = {}
    for side in T.SIDES:
        out[f"{side} doctrine"] = T.SIDES[side]["doctrine"]
        out[f"{side} production"] = T.SIDES[side]["production"]
        out[f"{side} attrition tol"] = T.SIDES[side]["attrition_tolerance"]
    for side in T.INDUSTRY:
        for year in ("1941", "1944"):
            out[f"{side} industry {year}"] = T.INDUSTRY[side][year]
    return out


def restore(snap: dict) -> None:
    for side in T.SIDES:
        T.SIDES[side]["doctrine"] = snap[f"{side} doctrine"]
        T.SIDES[side]["production"] = snap[f"{side} production"]
        T.SIDES[side]["attrition_tolerance"] = snap[f"{side} attrition tol"]
    for side in T.INDUSTRY:
        for year in ("1941", "1944"):
            T.INDUSTRY[side][year] = snap[f"{side} industry {year}"]


def sweep(steps: int) -> list[tuple[str, float, float, int]]:
    """For each parameter, how many theatres move across its range.

    Returns (label, lowest scale, highest scale, distinct outcomes seen).
    """
    snap = snapshot()
    base = outcome()
    rows = []
    try:
        for label, lo, hi in SWEEPS:
            seen = {base}
            for i in range(steps):
                scale = lo + (hi - lo) * i / max(steps - 1, 1)
                restore(snap)
                apply(label, scale, snap)
                seen.add(outcome())
            restore(snap)
            rows.append((label, lo, hi, len(seen)))
    finally:
        restore(snap)
    return rows


def report(steps: int) -> str:
    base = outcome()
    rows = sweep(steps)
    lines = [f"parameter sensitivity, seed 1939", ""]
    lines.append(f"  baseline outcome  {base}   (one letter per theatre)")
    lines.append("")
    lines.append(f"  {'parameter':<22} {'range x':<14} {'outcomes':>8}")
    inert = 0
    for label, lo, hi, n in rows:
        mark = "" if n > 1 else "   <- inert"
        inert += n == 1
        lines.append(f"  {label:<22} {lo:.2f}-{hi:.2f}{'':<4} {n:>8}{mark}")
    lines.append("")
    lines.append(f"  {inert} of {len(rows)} parameters change nothing across the")
    lines.append(f"  tested range. A model whose inputs do not move its output is")
    lines.append(f"  a lookup table with arithmetic around it.")
    lines.append("")
    lines.append("  Why: a theatre's control flips the first time its contest")
    lines.append("  crosses a bar, and never flips back. Once decided, no later")
    lines.append("  ratio can reach it, so only the parameters that act during")
    lines.append("  the theatre's own window can matter -- and for short theatres")
    lines.append("  that window is one month.")
    return "\n".join(lines)


def self_test() -> int:
    checks, failures = 0, []

    def check(cond, label):
        nonlocal checks
        checks += 1
        if not cond:
            failures.append(label)

    snap = snapshot()
    base = outcome()
    check(base == outcome(), "outcome is deterministic")

    # every sweep target exists in the snapshot
    for label, _lo, _hi in SWEEPS:
        check(label in snap, f"{label} present in snapshot")

    # apply/restore is a round trip
    apply("axis doctrine", 2.0, snap)
    check(T.SIDES["axis"]["doctrine"] != snap["axis doctrine"], "apply changes value")
    restore(snap)
    check(T.SIDES["axis"]["doctrine"] == snap["axis doctrine"], "restore reverts")
    check(outcome() == base, "restore reverts the outcome too")

    # a deliberately huge change must move something, or the tool itself is broken
    T.SIDES["axis"]["doctrine"] = snap["axis doctrine"] * 6
    moved = outcome() != base
    restore(snap)
    check(moved, "a 6x doctrine change moves the war")
    check(outcome() == base, "and restoring gets the baseline back")

    # sweep returns one row per parameter
    rows = sweep(5)
    check(len(rows) == len(SWEEPS), "one row per parameter")
    check(all(n >= 1 for *_x, n in rows), "at least the baseline outcome each")

    for m in failures:
        print(f"  FAIL {m}")
    print(f"\n{checks} checks, {len(failures)} failed")
    return 0 if not failures else 1


def main(argv: list[str]) -> int:
    p = argparse.ArgumentParser(prog="sensitivity",
                                description="Measure whether the theatre model "
                                            "responds to its parameters.")
    p.add_argument("--quick", action="store_true", help="fewer steps per sweep")
    p.add_argument("--self-test", action="store_true")
    a = p.parse_args(argv)
    if a.self_test:
        return self_test()
    print(report(5 if a.quick else 13))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
