#!/usr/bin/env python3
"""theatre -- a strategic-level model of the Second World War, 1939-45.

misc/blitz-1940 models the 1940 campaign at corps level on a one-dimensional
front. That abstraction cannot express a city battle, a landing, or a carrier
engagement, so it cannot be extended to the whole war. This is one level up:
the unit is a theatre, the step is a month, the state is resources.

Per theatre per month: industry becomes combat power, divided among the fronts
a side is actually fighting; doctrine and terrain multiply it; the ratio sets
losses, casualty morale drift, and whether control changes.

    python3 theatre.py --seed 1939 [--verbose|--json]
    python3 theatre.py --self-test
    python3 accuracy.py            # outcome vs record, and what is fitted
    python3 accuracy.py --holdout   # neutral-weight test

Standard library only. Not a game: there are no decisions to make.
"""

import argparse
import json
import random
import sys

from data import (INDUSTRY, MANPOWER, MAX_MONTHS, SIDES, START, THEATRES,
                  Side, Theatre, _window_months, month_of)

def resolve_month(ax: Side, al: Side, th: Theatre, stamp: str,
                  rng: random.Random, ax_fronts: int = 1,
                  al_fronts: int = 1) -> list[str]:
    """One month in one theatre. Returns events.

    `ax_fronts` / `al_fronts` are how many theatres that side is fighting in
    this month; industry is divided evenly among them.
    """
    year = stamp[:4]
    events: list[str] = []
    ax_share = 1.0 / max(ax_fronts, 1)
    al_share = 1.0 / max(al_fronts, 1)

    # Terrain favours whoever holds the ground.
    ax_mult = 1.0 if th.control == "axis" else th.terrain
    al_mult = 1.0 if th.control == "allied" else th.terrain

    ap = (ax.power(ax_mult, ax.industry(year) * ax_share * th.ax_weight)
          * rng.uniform(0.90, 1.10))
    lp = (al.power(al_mult, al.industry(year) * al_share * th.al_weight)
          * rng.uniform(0.90, 1.10))
    ratio = ap / max(lp, 0.001)

    # Casualties are proportional to the opposing effort, and the loser of the
    # month loses more. Supply is consumed by offensive action either way.
    base = 0.010 * (ap + lp) / 100
    if ratio >= 1.0:
        ax_loss, al_loss = base / ratio, base * min(ratio, 4.0)
    else:
        ax_loss, al_loss = base * min(1 / ratio, 4.0), base / (1 / ratio)

    tol_ax = SIDES["axis"]["attrition_tolerance"]
    tol_al = SIDES["allied"]["attrition_tolerance"]
    ax.casualties += ax_loss
    al.casualties += al_loss
    ax.manpower = max(0.0, ax.manpower - ax_loss * tol_ax)
    al.manpower = max(0.0, al.manpower - al_loss * tol_al)
    ax.supply = max(0.20, ax.supply - 0.05)
    al.supply = max(0.20, al.supply - 0.05)

    # Morale drifts with the casualty exchange, slowly.
    ax.morale = max(0.35, min(1.15, ax.morale - (ratio - 1) * 0.006 * tol_ax))
    al.morale = max(0.35, min(1.15, al.morale + (ratio - 1) * 0.006 * tol_al))

    # Control shifts on decisive months. A short theatre (Poland, France,
    # Normandy) is decided by one or two months of fighting, so the threshold
    # has to be reachable in that time -- accumulation alone cannot do it.
    if ratio >= 1.35 or ratio <= 0.74:
        gain = min(0.60, (ratio - 1.0) * 0.55 if ratio > 1 else
                   (1.0 / ratio - 1.0) * 0.55)
        who = "axis" if ratio > 1 else "allied"
        th.contest = (min(1.0, th.contest + gain) if who == "axis"
                      else max(-1.0, th.contest - gain))
        events.append(f"{stamp} {th.name}: {who} advances ({ratio:.2f})")

    # A theatre that closes within a quarter has only a few months to be
    # decided, so it needs a lower bar than a four-year front. Deriving the bar
    # from the window length keeps one rule instead of a special case per war.
    window = _window_months(th.opens, th.closes)
    bar = 0.55 if window <= 3 else 0.80

    if th.contest >= bar and th.control != "axis":
        th.control = "axis"
        events.append(f"{stamp} {th.name}: AXIS TAKES THE THEATRE")
    elif th.contest <= -bar and th.control != "allied":
        th.control = "allied"
        events.append(f"{stamp} {th.name}: ALLIED TAKES THE THEATRE")

    th.months += 1
    return events


def simulate(seed: int, verbose: bool = False) -> dict:
    """Run the whole war month by month, 1939-09 to 1945-09."""
    rng = random.Random(seed)
    ax, al = Side("axis"), Side("allied")
    theatres = [Theatre(*t) for t in THEATRES]

    log: list[str] = []
    current_year = ""
    timeline: list[dict] = []

    for month in range(MAX_MONTHS):
        stamp = month_of(month)
        year = stamp[:4]
        if year != current_year:
            ax.reset_year(year)
            al.reset_year(year)
            current_year = year

        # Supply recovers between months: this is a monthly, not weekly, model.
        ax.supply = min(1.0, ax.supply + 0.35)
        al.supply = min(1.0, al.supply + 0.35)

        active = [t for t in theatres if t.active(stamp)]
        # A side fights only the theatres it is actually engaged in; a theatre
        # it already holds uncontested costs no front-line effort.
        ax_fronts = sum(1 for t in active if t.control != "axis")
        al_fronts = sum(1 for t in active if t.control != "allied")
        for th in active:
            events = resolve_month(ax, al, th, stamp, rng,
                                   max(ax_fronts, 1), max(al_fronts, 1))
            if verbose:
                log.extend(events)

        timeline.append({
            "month": stamp, "active": len(active),
            "axis_manpower": round(ax.manpower, 1),
            "allied_manpower": round(al.manpower, 1),
            "axis_morale": round(ax.morale, 3),
            "allied_morale": round(al.morale, 3),
            "axis_held": sum(1 for t in theatres if t.control == "axis"),
            "allied_held": sum(1 for t in theatres if t.control == "allied")})

        # The war ends when one side's manpower or morale is spent.
        if ax.manpower <= 1 or ax.morale <= 0.40:
            log.append(f"{stamp} axis collapses")
            break
        if al.manpower <= 1 or al.morale <= 0.40:
            log.append(f"{stamp} allied collapse")
            break

    return {
        "seed": seed,
        "months": len(timeline),
        "end": timeline[-1]["month"] if timeline else month_of(0),
        "theatres": [{"name": t.name, "control": t.control,
                      "contest": round(t.contest, 2), "months": t.months}
                     for t in theatres],
        "axis": {"casualties": round(ax.casualties, 1),
                 "manpower": round(ax.manpower, 1),
                 "morale": round(ax.morale, 3)},
        "allied": {"casualties": round(al.casualties, 1),
                   "manpower": round(al.manpower, 1),
                   "morale": round(al.morale, 3)},
        "timeline": timeline,
        "log": log,
    }


def self_test() -> int:
    checks = 0
    failures = []

    def check(cond, label):
        nonlocal checks
        checks += 1
        if not cond:
            failures.append(label)

    for idx, want in ((0, "1939-09"), (3, "1939-12"), (4, "1940-01"),
                      (72, "1945-09")):
        check(month_of(idx) == want, f"month_of({idx}) -> {month_of(idx)}")

    t = Theatre("T", "1940-01", "1945-01", 1.2, "contested")
    for stamp, want in (("1940-01", True), ("1945-01", True),
                        ("1939-12", False), ("1945-02", False)):
        check(t.active(stamp) == want, f"active({stamp}) -> {t.active(stamp)}")

    s = Side("axis")
    s.supply = 1.0
    s.morale = 1.0
    base = s.power(1.0, 100.0)
    s.supply = 0.5
    check(abs(s.power(1.0, 100.0) - base / 2) < 1e-9, "supply halves power")

    check(INDUSTRY["allied"]["1944"] > INDUSTRY["axis"]["1944"],
          "allied industry exceeds axis by 1944")
    check(INDUSTRY["axis"]["1941"] > INDUSTRY["allied"]["1941"],
          "axis industry exceeds allied in 1941")

    a, b, c = simulate(1939), simulate(1939), simulate(7)
    check(a["months"] == b["months"], "same seed, same duration")
    check(a["theatres"] == b["theatres"], "same seed, same theatres")
    check(a["theatres"] != c["theatres"], "different seed changes the war")

    for msg in failures:
        print(f"  FAIL {msg}")
    print(f"\n{checks} checks, {len(failures)} failed")
    return 0 if not failures else 1


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(
        prog="theatre",
        description="Strategic-level month-by-month model of the Second World War.",
    )
    parser.add_argument("--seed", type=int, default=1939)
    parser.add_argument("--verbose", action="store_true")
    parser.add_argument("--json", action="store_true")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args(argv)

    if args.self_test:
        return self_test()

    r = simulate(args.seed, verbose=args.verbose)
    if args.json:
        print(json.dumps(r, indent=2))
        return 0

    print(f"seed {r['seed']}  months {r['months']}  ended {r['end']}\n")
    for side in ("axis", "allied"):
        s = r[side]
        print(f"  {side:<7} casualties {s['casualties']:>7.1f}  "
              f"manpower {s['manpower']:>6.1f}  morale {s['morale']:.3f}")
    print("\n  theatres")
    for t in r["theatres"]:
        print(f"    {t['name']:<14} {t['control']:<9} "
              f"contest {t['contest']:+.2f}  {t['months']:>2} months")
    if args.verbose:
        print("\n  events", *(f"    {x}" for x in r["log"]), sep="\n")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
