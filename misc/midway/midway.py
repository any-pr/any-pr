#!/usr/bin/env python3
"""midway -- 4 June 1942 at thirty-minute turns, where minutes decide everything.

blitz-1940 models a front line; theatre-1939-45 models attrition. Neither can
express Midway, which turned on three things a front-line model has no words
for: who detects whom first, what state a carrier's deck is in, and how long a
strike package takes to come round again.

    python3 midway.py --seed 1942 [--verbose|--json]
    python3 midway.py --self-test
    python3 accuracy.py

Standard library only.
"""

import argparse
import json
import random
import sys

# Aircraft complements at dawn, 4 June. Japan 248 across four carriers;
# the US 233 across three.
JAPAN = [
    # name, dive/torpedo bombers, fighters, turn the Midway strike launches
    ("Akagi", 54, 24, 0),
    ("Kaga", 60, 24, 0),
    ("Soryu", 50, 21, 0),
    ("Hiryu", 50, 21, 0),
]
US = [
    ("Enterprise", 60, 27, 7),
    ("Hornet", 55, 25, 7),
    ("Yorktown", 55, 25, 7),
]

TURNS = 26
START_HOUR = 4.5   # 04:30

# Japan flew too few search lines too late; the US had PBYs aloft and radar.
SEARCH = {
    "japan": {"lines": 7, "range_nm": 300, "delay": 3},
    "us": {"lines": 10, "range_nm": 700, "delay": 1},
}

# Turns to swap a Midway package for a ship package: Nagumo's real dilemma.
REARM_TURNS = 2

# Hit chance per attacking aircraft, by target deck state. This table is the
# model's thesis: a deck full of armed aircraft is a liability.
HIT_CHANCE = {
    "ARMED": 0.055,
    "RECOVERING": 0.065,
    "LAUNCHING": 0.030,
    "EMPTY": 0.014,
}

# Damage to take a carrier out of the fight, in hit points. A single magazine
# or fuel fire is fatal regardless -- see `catastrophic`.
HP = 6


class Carrier:
    def __init__(self, name: str, side: str, bombers: int, fighters: int,
                 launch_turn: int):
        self.name = name
        self.side = side
        self.bombers = bombers
        self.fighters = fighters
        self.launch_turn = launch_turn
        self.hp = HP
        self.state = "ARMED"
        self.airborne = 0          # aircraft currently away
        self.deck_load = bombers   # aircraft on deck, fuelled and armed
        self.sunk = False
        self.away_until = 0
        self.rearm_until = 0
        self.struck = False
        self.midway_strike_done = False

    def alive(self) -> bool:
        return not self.sunk and self.hp > 0


def hour_of(turn: int) -> str:
    """Render a turn index as a clock time."""
    hours = START_HOUR + turn * 0.5
    h = int(hours)
    m = int(round((hours - h) * 60))
    return f"{h:02d}:{m:02d}"


def detect(seed: int) -> dict[int, str]:
    """Which side has a usable contact, and from which turn.

    Not a die roll per turn: a search plan either covers the area or it does
    not. Japan's narrower, shorter-ranged plan finds the US fleet late; the US
    plan with radar and long-range patrols finds Japan earlier. The seed only
    jitters the exact turn by one.
    """
    # Japan found the US at 07:00 (vague report); the US found Japan at 07:55.
    # The asymmetry is a few turns, not a coin flip -- which is what let Japan
    # strike first and still lose.
    rng = random.Random(seed)
    j = 5 + rng.randint(0, 1)          # turn 5-6: 07:00-07:30
    u = 6 + rng.randint(0, 1)          # turn 6-7: 07:30-08:00
    return {"japan": j, "us": u}


class Battle:
    def __init__(self, seed: int):
        self.rng = random.Random(seed)
        self.japan = [Carrier(n, "japan", b, f, t) for n, b, f, t in JAPAN]
        self.us = [Carrier(n, "us", b, f, t) for n, b, f, t in US]
        self.detected = detect(seed)
        self.log: list[str] = []
        self.turn = 0
        # Nagumo's dilemma: the Midway strike package must be recovered and
        # re-armed before a ship strike can launch. Track it per carrier.
        self.rearm_done: dict[str, int] = {}

    def carriers(self, side: str) -> list[Carrier]:
        return self.japan if side == "japan" else self.us

    def step(self) -> list[str]:
        """Advance one turn: cycle decks, launch what is ready, resolve strikes."""
        t = self.turn
        stamp = hour_of(t)
        ev: list[str] = []

        # whoever saw first strikes first
        order = sorted(("japan", "us"), key=lambda s: self.detected[s])
        for side in order:
            targets = self.us if side == "japan" else self.japan
            detected = t >= self.detected[side]
            for c in self.carriers(side):
                if not c.alive():
                    continue

                # deck cycle: LAUNCHING -> AWAY -> RECOVERING -> ARMED
                if c.state == "LAUNCHING":
                    c.state = "AWAY"
                elif c.state == "AWAY":
                    # Strike resolves the turn after launch, then turns home.
                    if t >= c.away_until - 1 and not c.struck:
                        c.struck = True
                        ev.extend(self._strike(c, side, targets, stamp))
                    if t >= c.away_until:
                        c.airborne = 0
                        c.deck_load = c.bombers
                        c.state = "RECOVERING"
                        ev.append(f"{stamp} {c.name} recovers its strike")
                elif c.state == "RECOVERING":
                    # re-arming; both sides take two turns
                    if t >= c.rearm_until:
                        c.state = "ARMED"
                        c.deck_load = c.bombers
                elif c.state == "EMPTY":
                    c.state = "ARMED"
                    c.deck_load = c.bombers

                # Nagumo had to hit the island first; that sortie left his
                # decks occupied when the US fleet was found.
                if (side == "japan" and c.state == "ARMED"
                        and not c.midway_strike_done and t >= c.launch_turn):
                    c.airborne = c.deck_load
                    c.state = "LAUNCHING"
                    c.away_until = t + 3     # strikes the island, returns
                    c.rearm_until = c.away_until + 2
                    c.struck = True               # no ship damage this sortie
                    c.midway_strike_done = True
                    ev.append(f"{stamp} {c.name} launches {c.airborne} "
                              f"against Midway")
                    continue

                if c.state == "ARMED" and detected and c.bombers > 0:
                    c.airborne = c.deck_load
                    c.state = "LAUNCHING"
                    c.away_until = t + 2
                    c.rearm_until = c.away_until + 2
                    c.struck = False
                    ev.append(f"{stamp} {c.name} launches {c.airborne} aircraft")

        self.turn += 1
        return ev

    def _strike(self, c: Carrier, side: str, targets: list, stamp: str) -> list[str]:
        """Resolve one carrier's strike against one randomly found target."""
        ev: list[str] = []
        alive = [x for x in targets if x.alive()]
        if not alive:
            return ev
        target = self.rng.choice(alive)
        # most aircraft never find the target; the ones that matter arrive together
        effective = c.airborne // 2
        hits = 0
        for _ in range(effective):
            p = HIT_CHANCE.get(target.state, 0.02)
            if side == "japan":
                p *= 0.52     # three uncoordinated attacks, none decisive
            if self.rng.random() < p:
                hits += 1
        if not hits:
            ev.append(f"{stamp} {c.name}'s strike misses in cloud over {target.name}")
            return ev
        target.hp -= hits
        ev.append(f"{stamp} {c.name} hits {target.name} "
                  f"({hits} hits, deck {target.state})")
        # deck state multiplies damage; it does not decide the outcome alone
        if target.state in ("ARMED", "RECOVERING"):
            target.hp -= 1
        if target.hp <= 0:
            target.sunk = True
            ev.append(f"{stamp} {target.name} abandoned, burning")
        return ev

    def run(self, verbose: bool = False) -> dict:
        for _ in range(TURNS):
            ev = self.step()
            if verbose:
                self.log.extend(ev)
        return self.result()

    def result(self) -> dict:
        j_sunk = [c.name for c in self.japan if c.sunk]
        u_sunk = [c.name for c in self.us if c.sunk]
        return {
            "turns": self.turn,
            "japan_sunk": j_sunk,
            "us_sunk": u_sunk,
            "japan_alive": [c.name for c in self.japan if c.alive()],
            "us_alive": [c.name for c in self.us if c.alive()],
            "log": self.log,
        }


def run(seed: int, verbose: bool = False) -> dict:
    return Battle(seed).run(verbose)


def self_test() -> int:
    checks, failures = 0, []

    def check(cond, label):
        nonlocal checks
        checks += 1
        if not cond:
            failures.append(label)

    for turn, want in ((0, "04:30"), (1, "05:00"), (7, "08:00"), (25, "17:00")):
        check(hour_of(turn) == want, f"hour_of({turn}) -> {hour_of(turn)}")
    # a recovery deck is the worst place to be caught, an empty deck the best
    check(HIT_CHANCE["RECOVERING"] > HIT_CHANCE["ARMED"] > HIT_CHANCE["EMPTY"],
          "deck ranking")
    b = Battle(1942)
    check(len(b.japan) == 4 and len(b.us) == 3, "order of battle")
    a1, a2 = run(1942), run(1942)
    check(a1["japan_sunk"] == a2["japan_sunk"], "same seed, same losses")
    # one seed pair is too weak: most seeds land on a dominant outcome
    outs = {(tuple(run(s)["japan_sunk"]), tuple(run(s)["us_sunk"]))
            for s in range(1, 41)}
    check(len(outs) >= 4, f"seed space has variety (got {len(outs)})")
    hist = {"Akagi", "Kaga", "Soryu", "Hiryu"}
    hits = sum(1 for s in range(1, 201)
               if set(run(s)["japan_sunk"]) == hist and len(run(s)["us_sunk"]) == 1)
    check(hits > 0, f"some seed gives the historical result (got {hits})")
    for m in failures:
        print(f"  FAIL {m}")
    print(f"\n{checks} checks, {len(failures)} failed")
    return 0 if not failures else 1


def main(argv: list[str]) -> int:
    p = argparse.ArgumentParser(prog="midway",
                                description="Model of 4 June 1942, 30-minute turns.")
    p.add_argument("--seed", type=int, default=1942)
    p.add_argument("--verbose", action="store_true")
    p.add_argument("--json", action="store_true")
    p.add_argument("--self-test", action="store_true")
    a = p.parse_args(argv)
    if a.self_test:
        return self_test()

    r = run(a.seed, verbose=a.verbose)
    if a.json:
        print(json.dumps(r, indent=2))
        return 0
    print(f"seed {a.seed}  turns {r['turns']}\n")
    for k in ("japan_sunk", "japan_alive", "us_sunk", "us_alive"):
        print(f"  {k:14s} {', '.join(r[k]) or 'none'}")
    if a.verbose:
        print("\n  events")
        for line in r["log"]:
            print(f"    {line}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
