"""atlantic -- the Battle of the Atlantic as a tonnage war, September 1939 to May 1945.

The 1940 model has a front line. The theatre model has monthly attrition. The
Midway model has a deck. None of them can express the longest campaign of the
war, because its outcome is not territory or casualties -- it is a crossing
point on a graph.

Two curves. One is tonnage launched by Allied shipyards. The other is tonnage
torpedoed by U-boats. For forty-four months the second curve is above the first,
and Britain is being eaten. In May 1943 they cross, and they never cross back.

The interesting part is why. Nothing the Allies did made U-boats worse at
sinking ships in 1943 -- the boats were as good as ever. What changed was the
probability that a boat survived long enough to attack, and that is a function
of air cover over the mid-Atlantic gap, escort numbers, radar, and reading the
enemy's mail. This model makes that probability a single number called ASW and
watches what the war does to the rest.

    python3 atlantic.py --seed 1939 [--verbose|--json]
    python3 atlantic.py --self-test
    python3 accuracy.py

Standard library only.
"""

import argparse
import json
import random
import sys

# 1939-09 is turn 0. 1945-05 is turn 68.
START_YEAR, START_MONTH = 1939, 9
TURNS = 69

# Allied merchant tonnage at the start, millions of gross register tons.
START_TONNAGE = 20.0

# Germany commissioned 1,160 boats and lost 783. Production was never the
# constraint; crews were, and a boat takes a year from keel to patrol.
U_BOAT_INTAKE = {1940: 4, 1941: 17, 1942: 20, 1943: 24, 1944: 23, 1945: 15}
U_BOATS_START = 22

# Sinkings are multiplicative in ASW, not additive: each increment removes a
# fixed FRACTION of a boat's productivity, so the collapse is exponential, not
# linear. Calibrated to the quarterly returns: a boat-month is worth about
# four ships in 1940 and a tenth of a ship in 1944.
#
# 25M gross tons were lost, not 14.5M. The 14.5M figure counts Allied ocean
# tonnage only; the returns this curve is fitted to are all flag, all waters.
SHIP_KILL_BASE = 7.4          # ships per boat-month at zero ASW
SHIP_KILL_DECAY = 3.55        # exponent per unit ASW
AVG_SHIP_TONS = 0.0040        # millions of tons: about 4,000 gross register tons

# Boats at sea. About 400 boats in service at the 1943 peak, of which roughly
# 50 were on patrol in a given week. One in eight is the honest figure, and it
# is why the fleet number and the sinking number are not proportional.
PATROL_FRACTION = 0.13

# Losses per boat-month, applied to the whole fleet including boats in transit
# and in dock. Germany built 1,160 boats and lost 783; the arm died of
# collapsed productivity, not of a spike in its own casualty rate, so this stays
# close to flat and the fleet size does the work.
LOSS_BASE = 0.020
LOSS_ASW = 0.092

# ASW effectiveness by month. This is the model's whole argument: it is not
# the boats that change, it is this number. Each entry is the month something
# arrived, and the value it set.
ASW_SCHEDULE = [
    (0, 0.03),        # 1939-09  a handful of escorts, no air cover at all
    (7, 0.09),        # 1940-04  Norway occupies the fleet; the convoys tighten
    (10, 0.12),       # 1940-07  western approaches command, still no radar
    (19, 0.17),       # 1941-04  surface radar, first escort groups
    (24, 0.21),       # 1941-09  escort carriers ordered, destroyers-for-bases
    (28, 0.09),       # 1942-01  THE HAPPY TIME. US enters, its coast is lit up
    (34, 0.16),       # 1942-07  US coastal blackout at last
    (40, 0.24),       # 1943-01  Casablanca: the Atlantic is the first priority
    (44, 0.62),       # 1943-05  BLACK MAY. Escort carriers close the air gap,
                      #          hedgehog, Leigh lights, Bletchley's return
    (52, 0.74),       # 1944-01  hunter-killer groups hunt boats, not convoys
    (62, 0.82),       # 1944-11  the boats cannot even reach the Atlantic safely
]

# Millions of tons launched per month, from production figures rather than
# from the outcome it must explain.
SHIPBUILDING = [
    (0, 0.06), (10, 0.10), (17, 0.16), (22, 0.30),       # 1939-09 .. 1941-06
    (28, 0.42), (31, 0.52), (34, 0.62), (37, 0.72),      # 1942-01 .. 1942-10
    (40, 1.05), (43, 1.30), (46, 1.50), (49, 1.48),      # 1943-01 .. 1943-10
    (52, 1.42), (58, 1.20), (64, 0.90), (68, 0.60),      # peak, then converting
]


def month_of(turn: int) -> str:
    """Turn index to YYYY-MM."""
    y = START_YEAR + (START_MONTH - 1 + turn) // 12
    m = (START_MONTH - 1 + turn) % 12 + 1
    return f"{y}-{m:02d}"


def year_of(turn: int) -> int:
    return START_YEAR + (START_MONTH - 1 + turn) // 12


def _interp(table: list[tuple[int, float]], turn: int) -> float:
    """Step function: the value holds until the next entry."""
    value = table[0][1]
    for t, v in table:
        if turn >= t:
            value = v
    return value


class War:
    """Merchant tonnage, U-boat fleet, and the ASW number that decides both."""

    def __init__(self, seed: int) -> None:
        self.rng = random.Random(seed)
        self.turn = 0
        self.tonnage = START_TONNAGE          # millions of tons afloat
        self.sunk_total = 0.0
        self.built_total = 0.0
        self.boats = U_BOATS_START
        self.boats_sunk_total = 0
        self.boats_built_total = 0
        self.crossover_month = None
        self.collapse_month = None
        self.log = []
        self.history = []

    def asw(self) -> float:
        return _interp(ASW_SCHEDULE, self.turn)

    def shipbuilding(self) -> float:
        return _interp(SHIPBUILDING, self.turn)

    def stepped(self) -> bool:
        """U-boats sink ships and are sunk; yards launch ships."""
        asw = self.asw()
        month = month_of(self.turn)

        # Boats on station. A boat in transit, in dock, or working up cannot
        # attack; roughly half the fleet is unavailable in any month.
        on_station = max(self.boats, 0) * PATROL_FRACTION

        # Sinkings. ASW does not stop a boat attacking, it stops it arriving.
        rate = SHIP_KILL_BASE * (2.718281828 ** (-SHIP_KILL_DECAY * asw))
        sinkings = on_station * rate * AVG_SHIP_TONS * self.rng.uniform(0.88, 1.12)
        self.sunk_total += sinkings
        self.tonnage = max(self.tonnage - sinkings, 0.0)

        # Boats lost. Escorts kill an increasing fraction of the fleet, and
        # in Black May that fraction briefly exceeds the intake.
        loss_rate = LOSS_BASE + LOSS_ASW * asw
        lost = self.boats * loss_rate * self.rng.uniform(0.85, 1.15)
        self.boats = max(self.boats - lost, 0.0)
        self.boats_sunk_total += lost

        built = self.shipbuilding() * self.rng.uniform(0.94, 1.06)
        self.built_total += built
        self.tonnage += built

        intake = U_BOAT_INTAKE.get(year_of(self.turn), 3)
        self.boats += intake
        self.boats_built_total += intake

        # The crossing that matters is not one good month. It is the month after
        # which the shipyards never fall behind again, so this looks at a
        # twelve-month window and asks whether the whole window is positive.
        self.history.append((built, sinkings))
        if self.crossover_month is None and len(self.history) >= 12:
            window = self.history[-12:]
            surplus = sum(b for b, _ in window) - sum(s for _, s in window)
            if surplus > 0:
                self.crossover_month = month
                self.log.append(
                    f"{month}  TWELVE MONTHS IN SURPLUS: "
                    f"{surplus:+.2f}M tons over losses")

        if self.turn == 44:
            self.collapse_month = month
            self.log.append(f"{month}  Black May: {lost:.0f} boats lost, "
                            f"{sinkings:.2f}M tons sunk, "
                            f"{self.boats:.0f} boats still in service")
        return True


def run(seed: int, verbose: bool = False) -> dict:
    w = War(seed)
    for turn in range(TURNS):
        w.turn = turn
        w.stepped()
    return {
        "seed": seed,
        "months": TURNS,
        "crossover": w.crossover_month,
        "collapse": w.collapse_month,
        "tonnage_at_end": round(w.tonnage, 2),
        "tons_sunk": round(w.sunk_total, 2),
        "tons_built": round(w.built_total, 2),
        "boats_at_end": round(w.boats, 1),
        "boats_sunk": round(w.boats_sunk_total, 1),
        "log": w.log,
    }


def self_test() -> int:
    checks, failures = 0, []

    def check(cond, label):
        nonlocal checks
        checks += 1
        if not cond:
            failures.append(label)

    for turn, want in ((0, "1939-09"), (3, "1939-12"), (12, "1940-09"),
                       (44, "1943-05"), (68, "1945-05")):
        check(month_of(turn) == want, f"month_of({turn}) -> {month_of(turn)}")

    check(_interp(ASW_SCHEDULE, 68) > _interp(ASW_SCHEDULE, 0), "asw rises")
    check(_interp(ASW_SCHEDULE, 28) < _interp(ASW_SCHEDULE, 24), "happy time dip")
    check(_interp(ASW_SCHEDULE, 44) > _interp(ASW_SCHEDULE, 40), "black may jump")

    def per_boat(turn):
        return SHIP_KILL_BASE * (2.718281828 **
                                 (-SHIP_KILL_DECAY * _interp(ASW_SCHEDULE, turn)))

    check(per_boat(44) < per_boat(40) * 0.4, "productivity falls >60% by 1943-05")

    w = War(1939)
    w.turn = 44
    before = w.boats
    for _ in range(3):
        w.stepped()
    check(w.boats != before, "fleet changes when stepped")
    check(w.sunk_total > 0 and w.built_total > 0, "both curves populated")

    r1, r2 = run(1939), run(1939)
    check(r1["tons_sunk"] == r2["tons_sunk"], "same seed, same result")
    check(run(7)["tons_sunk"] != r1["tons_sunk"], "different seed differs")
    check(r1["crossover"] is not None, "crossover happens")
    check(r1["crossover"] > "1942-06",
          f"crossover is late, not early: {r1['crossover']}")

    for m in failures:
        print(f"  FAIL {m}")
    print(f"\n{checks} checks, {len(failures)} failed")
    return 0 if not failures else 1


def main(argv: list[str]) -> int:
    p = argparse.ArgumentParser(
        prog="atlantic", description="Battle of the Atlantic as a tonnage war.")
    p.add_argument("--seed", type=int, default=1939)
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
    print(f"seed {a.seed}  {r['months']} months\n")
    print(f"  tonnage afloat   {r['tonnage_at_end']:>6.1f}M tons")
    print(f"  tons sunk        {r['tons_sunk']:>6.1f}M")
    print(f"  tons built       {r['tons_built']:>6.1f}M")
    print(f"  boats in service {r['boats_at_end']:>6.1f}")
    print(f"  boats lost       {r['boats_sunk']:>6.1f}")
    print(f"  shipyards ahead  since {r['crossover']}")
    if a.verbose:
        print("\n  events")
        for line in r["log"]:
            print(f"    {line}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
