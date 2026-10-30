#!/usr/bin/env python3
"""blitz -- deterministic turn-based simulation of the 1940 Western campaign.

A list of dated events is a list, not a model. A model has state, rules, and a
resolution step, and it can disagree with the record. This one does; see
accuracy.py.

Per turn: resupply attenuates with distance from a depot, each formation fights
one opponent it faces, losses scale with force ratio and terrain, formations
below a cohesion floor rout, the front moves toward whoever holds superiority.
Each defender is engaged at most once per turn -- without that cap every
formation in a sector piles onto one opponent and the ratio reaches 11:1.

Integer arithmetic and a seeded PRNG, so a seed always replays identically.

    python3 blitz.py --seed 1940 [--verbose|--json]
    python3 blitz.py --self-test
    python3 accuracy.py

Standard library only. Not a game: there are no decisions to make.
"""

import argparse
import json
import random
import sys

from models import (ANCHORS, COHESION_FLOOR, MAX_TURNS, SECTORS, SIDES,
                    START_DATE, TERRAIN, Formation)

def date_of(turn: int) -> str:
    """Campaign calendar: turn 0 is 10 May 1940."""
    y, m, d = START_DATE
    day = d + turn
    # May has 31 days; the campaign runs into June.
    while day > 31:
        day -= 31
        m += 1
        if m > 12:
            m = 1
            y += 1
    return f"{y:04d}-{m:02d}-{day:02d}"


def build(seed: int) -> dict[str, list[Formation]]:
    """Initial order of battle, seeded so strength jitter is reproducible."""
    rng, g, a = random.Random(seed), ANCHORS["germany"], ANCHORS["allied"]
    jitter = lambda base, spread=0.06: base * (1 + rng.uniform(-spread, spread))

    # Germany concentrated in the centre (Ardennes), weak on the flanks --
    # the historical Schwerpunkt, and the reason the model can reproduce a
    # breakthrough at all.
    germany = [
        Formation("germany", "Panzergruppe Kleist", jitter(g["personnel_k"] * 0.16),
                  jitter(g["armour"] * 0.42), 3),
        Formation("germany", "Sixth Army", jitter(g["personnel_k"] * 0.20),
                  jitter(g["armour"] * 0.10), 2),
        Formation("germany", "Eighteenth Army", jitter(g["personnel_k"] * 0.14),
                  jitter(g["armour"] * 0.08), 1),
        Formation("germany", "Sixteenth Army", jitter(g["personnel_k"] * 0.12),
                  jitter(g["armour"] * 0.06), 4),
        Formation("germany", "Reserve", jitter(g["personnel_k"] * 0.10), 0.0, 2),
    ]
    # Allies distributed evenly along the line, armour parcelled out in
    # infantry-support packages rather than concentrated.
    allied = [
        Formation("allied", "French First Army", jitter(a["personnel_k"] * 0.18),
                  jitter(a["armour"] * 0.22), 1),
        Formation("allied", "French Ninth Army", jitter(a["personnel_k"] * 0.14),
                  jitter(a["armour"] * 0.14), 3),
        Formation("allied", "French Second Army", jitter(a["personnel_k"] * 0.14),
                  jitter(a["armour"] * 0.16), 4),
        Formation("allied", "BEF", jitter(a["personnel_k"] * 0.12),
                  jitter(a["armour"] * 0.10), 1),
        Formation("allied", "French Seventh Army", jitter(a["personnel_k"] * 0.10),
                  jitter(a["armour"] * 0.08), 0),
    ]
    return {"germany": germany, "allied": allied}


def resupply(side: str, forms: list[Formation], depot_sector: int,
             rng: random.Random | None = None) -> None:
    """Supply attenuates with distance from the depot, and routed units rally."""
    reach = SIDES[side]["supply_reach"]
    command = SIDES[side]["command"]
    for f in forms:
        if f.routed:
            f.supply = min(1.0, f.supply + 0.10)
            # A routed formation can be reconstituted if it still has men.
            if rng is not None and f.strength > 25:
                if rng.random() < 0.45 * command:
                    f.routed = False
                    f.cohesion = max(f.cohesion, COHESION_FLOOR + 0.10)
            continue
        distance = abs(f.sector - depot_sector)
        f.cohesion = min(1.0, f.cohesion + 0.05)
        if distance <= reach:
            f.supply = min(1.0, f.supply + 0.16)
        else:
            f.supply = max(0.15, f.supply - 0.10 * (distance - reach))


def resolve(germany: list[Formation], allied: list[Formation],
            rng: random.Random, date: str) -> list[str]:
    """One combat turn. Returns human-readable events."""
    events = []

    # Each defender is engaged at most once per turn. Without this, every
    # formation in a sector piles onto the same opponent and the force ratio
    # reaches 11:1, which no line survives. One engagement per defender per
    # turn is also the closer model: a corps has one front.
    engaged: set[str] = set()

    for attacker_side, attackers, defenders in (
        ("germany", germany, allied),
        ("allied", allied, germany),
    ):
        for atk in attackers:
            if atk.routed or atk.strength <= 1:
                continue
            targets = [d for d in defenders
                       if not d.routed and d.name not in engaged
                       and abs(d.sector - atk.sector) <= 1]
            if not targets:
                continue
            dfn = max(targets, key=lambda d: d.strength)
            engaged.add(dfn.name)
            terrain = TERRAIN[dfn.sector]
            ap = atk.power(1.0)
            dp = dfn.power(terrain)
            ratio = ap / max(dp, 1.0)

            # Losses: the weaker side loses more, non-linearly.
            base = 0.35 * (atk.strength + dfn.strength) / 100 * rng.uniform(0.92, 1.08)
            if ratio >= 1.0:
                atk_loss = base / ratio
                dfn_loss = base * min(ratio, 3.0)
            else:
                atk_loss = base * min(1 / ratio, 3.0)
                dfn_loss = base / (1 / ratio)

            atk.strength = max(0.0, atk.strength - atk_loss)
            dfn.strength = max(0.0, dfn.strength - dfn_loss)
            atk.armour = max(0.0, atk.armour - atk_loss * 0.10)
            dfn.armour = max(0.0, dfn.armour - dfn_loss * 0.12)
            atk.supply = max(0.15, atk.supply - 0.04)
            dfn.supply = max(0.15, dfn.supply - 0.055)

            dfn.cohesion = max(0.0, dfn.cohesion - (ratio - 1.0) * 0.009
                               if ratio > 1.0 else dfn.cohesion)
            atk.cohesion = max(0.0, atk.cohesion - (1.0 / ratio - 1.0) * 0.008
                               if ratio < 1.0 else atk.cohesion)

            if ratio >= 1.4:
                events.append(f"{date} {atk.side} {atk.name} breaks "
                              f"{dfn.name} at {SECTORS[dfn.sector]} "
                              f"(ratio {ratio:.2f})")
            if dfn.cohesion < COHESION_FLOOR and not dfn.routed:
                dfn.routed, dfn.sector = True, max(0, dfn.sector - 1)
                events.append(f"{date} {dfn.side} {dfn.name} routs")

            if atk.side == "germany" and ratio >= 1.2 and not dfn.routed:
                atk.sector = min(4, atk.sector)

    return events


def simulate(seed: int, verbose: bool = False) -> dict:
    """Run the whole campaign. Returns a result record."""
    rng = random.Random(seed)
    orders = build(seed)
    germany, allied = orders["germany"], orders["allied"]

    log: list[str] = []
    turns = 0
    for turn in range(MAX_TURNS):
        date = date_of(turn)
        resupply("germany", germany, depot_sector=2, rng=rng)
        resupply("allied", allied, depot_sector=4, rng=rng)
        events = resolve(germany, allied, rng, date)
        turns += 1
        if verbose:
            log.extend(events)

        # Campaign ends when the allied line has no cohesive formation north
        # of the Maginot terminus, or when both sides are exhausted.
        # End needs both a broken allied line and spent German armour: a
        # single collapse is a rout, not a capitulation.
        allied_north = [f for f in allied if not f.routed and f.sector <= 2]
        german_armour = sum(f.armour for f in germany if not f.routed)
        allied_men = sum(f.strength for f in allied if not f.routed)
        if not allied_north and allied_men < 900:
            log.append(f"{date} allied line north of {SECTORS[4]} collapses")
            break
        if german_armour < 200 and not any(
                f.sector >= 3 and not f.routed for f in germany):
            log.append(f"{date} german offensive culminates")
            break

    return {
        "seed": seed,
        "turns": turns,
        "end_date": date_of(max(turns - 1, 0)),
        "germany": [_snap(f) for f in germany],
        "allied": [_snap(f) for f in allied],
        "log": log,
    }


def _snap(f: Formation) -> dict:
    return {"name": f.name, "side": f.side, "strength": round(f.strength, 1),
            "armour": round(f.armour), "sector": f.sector,
            "supply": round(f.supply, 2), "cohesion": round(f.cohesion, 2),
            "routed": f.routed}


def self_test() -> int:
    """Pure-function checks; no campaign is run."""
    checks = 0
    failures = []

    def check(cond, label):
        nonlocal checks
        checks += 1
        if not cond:
            failures.append(label)

    check(date_of(0) == "1940-05-10", f"date_of(0) -> {date_of(0)}")
    check(date_of(21) == "1940-05-31", f"date_of(21) -> {date_of(21)}")
    check(date_of(22) == "1940-06-01", f"date_of(22) -> {date_of(22)}")
    check(date_of(45) == "1940-06-24", f"date_of(45) -> {date_of(45)}")

    f = Formation("germany", "T", 100.0, 100.0, 3)
    f.supply = 1.0
    check(f.power(1.0) == (100 * 0.7 + 100 * 0.3) * 1.35,
          f"power at full supply -> {f.power(1.0)}")
    f.supply = 0.5
    check(f.power(1.0) == (100 * 0.7 + 100 * 0.3) * 1.35 * 0.5,
          "power halves with supply")

    # Terrain favours the defender
    check(TERRAIN[4] > TERRAIN[0], "southern sector is the most defensible")

    # Resupply attenuates past supply_reach
    near = Formation("germany", "near", 100, 0, 2)
    far = Formation("germany", "far", 100, 0, 4)
    near.supply = far.supply = 0.3
    resupply("germany", [near], depot_sector=2)
    resupply("germany", [far], depot_sector=0)
    check(near.supply > far.supply, "near depot resupplies faster")

    a, b, c = simulate(1940), simulate(1940), simulate(7)
    check(a["turns"] == b["turns"], "same seed, same turn count")
    check(a["allied"] == b["allied"], "same seed, same allied state")
    check(c["germany"] != a["germany"], "different seed changes the order of battle")

    for msg in failures:
        print(f"  FAIL {msg}")
    print(f"\n{checks} checks, {len(failures)} failed")
    return 0 if not failures else 1


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(
        prog="blitz",
        description="Deterministic turn-based simulation of the 1940 Western campaign.",
    )
    parser.add_argument("--seed", type=int, default=1940)
    parser.add_argument("--verbose", action="store_true")
    parser.add_argument("--json", action="store_true")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args(argv)

    if args.self_test:
        return self_test()

    result = simulate(args.seed, verbose=args.verbose)
    if args.json:
        print(json.dumps(result, indent=2))
        return 0

    print(f"seed {result['seed']}  turns {result['turns']}  "
          f"ended {result['end_date']}\n")
    for side in ("germany", "allied"):
        print(f"  {side}")
        for f in result[side]:
            flag = " ROUTED" if f["routed"] else ""
            print(f"    {f['name']:<24} str {f['strength']:>7.1f}  "
                  f"arm {f['armour']:>5.0f}  sector {f['sector']}  "
                  f"sup {f['supply']:.2f}  coh {f['cohesion']:.2f}{flag}")
        print()
    if args.verbose:
        print("  events", *(f"    {ln}" for ln in result["log"]), sep="\n")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
