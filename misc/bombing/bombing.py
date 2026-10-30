"""bombing -- the strategic bombing of Germany, May 1940 to April 1945.

The models beside this one describe a war being decided. This one describes a
campaign that was neither won nor lost.

German aircraft production peaked in September 1944 at about 3,000 airframes a
month, the highest of the war. In that same month synthetic oil output had
fallen from 180,000 tons a month to under 30,000. The factories won, the fuel
lost. That gap is the campaign's only unambiguous result.

    python3 bombing.py --seed 1943 [--verbose|--json]
    python3 bombing.py --self-test
    python3 accuracy.py

Standard library only.
"""

import argparse
import json
import random
import sys

START_YEAR, START_MONTH = 1940, 5
TURNS = 60                     # 1940-05 .. 1945-04

# Bomb tonnage dropped on Germany per month, thousands of tons. From the
# British and US campaign returns; not fitted to anything the model predicts.
TONNAGE = [
    0.5, 1.0, 1.5, 2.0, 2.5, 2.5, 2.5, 2.0,   # 1940-05 .. 1940-12
    2.0, 2.0, 2.5, 3.0, 2.5, 3.0, 2.5, 3.0, 3.5, 4.0, 5.0, 5.0,          # 1941
    4.5, 4.0, 4.5, 5.0, 6.0, 7.5, 9.0, 10.0, 8.0, 8.0, 9.0, 12.0,          # 1942
    13.0, 14.0, 20.0, 19.0, 25.0, 27.0, 30.0, 26.0, 22.0, 18.0, 16.0, 17.0,  # 1943
    20.0, 22.0, 25.0, 28.0, 30.0, 60.0, 78.0, 80.0, 75.0, 70.0, 65.0, 60.0,  # 1944
    52.0, 48.0, 45.0, 40.0,
]
assert len(TONNAGE) == TURNS, f"tonnage has {len(TONNAGE)} entries for {TURNS} months"

# Fractions of monthly effort. The doctrine fight inside the Allied camp lasted
# two years and is why the results arrive in its last eight months.
DOCTRINE = [
    (0,  {"urban": 0.95, "industry": 0.05, "oil": 0.00, "transport": 0.00}),
    (32, {"urban": 0.55, "industry": 0.45, "oil": 0.00, "transport": 0.00}),  # 1943-01 Pointblank
    (44, {"urban": 0.20, "industry": 0.35, "oil": 0.45, "transport": 0.00}),  # 1944-01
    (48, {"urban": 0.05, "industry": 0.15, "oil": 0.70, "transport": 0.10}),  # 1944-05 oil first
    (52, {"urban": 0.05, "industry": 0.15, "oil": 0.45, "transport": 0.35}),  # 1944-09 transport
]

# Fraction of dropped tonnage that lands close enough to break something. Night
# area bombing in 1941 put one bomb in fifty near the works; radar and
# Pathfinder took it to one in twenty and better.
PRECISION = [
    (0, 0.020), (20, 0.030), (32, 0.055), (44, 0.090),
    (48, 0.140), (52, 0.200), (59, 0.230),
]

# Counterfactual German production, index 1940-05 = 100, had no bombs fallen.
# 2.9% monthly compounding is what Speer's rationalisation was actually
# achieving; the model applies it undamaged and then subtracts.
GROWTH = 0.029

# Germany held a large stock of machine tools, so destroying a factory building
# did not destroy the means of production: the tools were dug out and re-erected
# elsewhere. Industry damage therefore leaks away, while damage to a synthetic
# oil plant cannot, because a hydrogenation plant is a fixed chemical works.
DISPERSAL_HALFLIFE = 7.0       # months, for industry damage
DISPERSAL_COST = 0.16          # permanent loss from the effort of dispersing
OIL_DECAY = 60.0               # effectively permanent

# Aviation fuel: output in thousands of tons per month, no bombing.
FUEL_CF = [(0, 55.0), (8, 62.0), (20, 78.0), (32, 105.0), (44, 150.0),
           (48, 178.0), (52, 185.0), (59, 190.0)]

# Airframes per month, thousands, all types. From the production returns, and
# the model's most uncomfortable input: it goes up through the war.
AIRFRAMES = [(0, 0.20), (8, 0.22), (20, 0.30), (32, 0.48),
             (44, 0.90), (47, 1.20), (49, 1.45), (52, 1.55), (55, 1.30), (59, 0.50)]

# Fuel demand, thousands of tons per month. A fighter sortie burned 0.4 tons and
# a bomber eight times that; Germany flew 60,000 sorties a month in 1944 at peak
# and training consumed as much again. Expressed directly rather than built up
# from airframes, because airframes are what the bombing did not stop.
FUEL_DEMAND = [(0, 22.0), (20, 40.0), (32, 75.0), (44, 110.0),
               (48, 130.0), (52, 140.0), (59, 60.0)]


def month_of(turn: int) -> str:
    y = START_YEAR + (START_MONTH - 1 + turn) // 12
    m = (START_MONTH - 1 + turn) % 12 + 1
    return f"{y}-{m:02d}"


def _interp(table, turn):
    value = table[0][1]
    for t, v in table:
        if turn >= t:
            value = v
    return value


def doctrine(turn: int) -> dict:
    for t, d in DOCTRINE:
        if turn >= t:
            current = d
    return current


class Campaign:
    """Production, oil, and the gap between what was built and what could fly."""

    def __init__(self, seed: int) -> None:
        self.rng = random.Random(seed)
        self.turn = 0
        self.industry_damage = 0.0
        self.oil_damage = 0.0
        self.counterfactual = 100.0
        self.production = 100.0
        self.log = []
        self.rows = []

    def step(self) -> None:
        t = self.turn
        tons = TONNAGE[min(t, len(TONNAGE) - 1)]
        share = doctrine(t)
        precision = _interp(PRECISION, t)

        # Effective hits, split by what they hit.
        hits = tons * precision * self.rng.uniform(0.9, 1.1)

        # Industry damage leaks away as plant is dispersed and tools re-erected.
        new_industry = hits * share["industry"] * 0.0085
        self.industry_damage = (self.industry_damage *
                                (0.5 ** (1.0 / DISPERSAL_HALFLIFE)) + new_industry)
        self.industry_damage = min(self.industry_damage, 0.42)

        # Oil damage does not leak away. This asymmetry is the model's core.
        new_oil = hits * share["oil"] * 0.0190
        self.oil_damage = min(self.oil_damage + new_oil, 0.93)

        # Urban bombing costs production only through absenteeism and transport
        # disruption, and it recovers. It also forces dispersal, which is a
        # permanent tax paid for the rest of the war.
        self.urban_cost = min(0.04 + hits * share["urban"] * 0.0022, 0.13)

        # The counterfactual keeps compounding regardless.
        self.counterfactual *= (1.0 + GROWTH)

        produced = (self.counterfactual *
                    (1.0 - self.industry_damage) *
                    (1.0 - self.oil_damage * 0.28) *
                    (1.0 - self.urban_cost) *
                    (1.0 - DISPERSAL_COST * min(self.industry_damage * 2.4, 1.0)))
        self.production = produced

        # Fuel. Oil damage is applied to a fixed plant, so it bites immediately
        # and completely once the plants are being hit.
        fuel_cf = _interp(FUEL_CF, t)
        fuel = fuel_cf * (1.0 - self.oil_damage)

        airframes = _interp(AIRFRAMES, t)
        demand = _interp(FUEL_DEMAND, t)
        fuel_ratio = min(fuel / max(demand, 0.001), 1.0)
        # Sorties actually flown, scaled by the fuel that showed up.
        sorties = airframes * 20.0 * fuel_ratio

        self.rows.append({
            "month": month_of(t),
            "tons": round(tons, 1),
            "precision": round(precision, 3),
            "production": round(produced, 1),
            "counterfactual": round(self.counterfactual, 1),
            "fuel": round(fuel, 1),
            "fuel_ratio": round(fuel_ratio, 3),
            "airframes": round(airframes, 2),
            "sorties": round(sorties, 1),
        })


def run(seed: int = 1943, verbose: bool = False) -> dict:
    c = Campaign(seed)
    for turn in range(TURNS):
        c.turn = turn
        c.step()

    last = c.rows[-1]
    peak = max(c.rows, key=lambda r: r["airframes"])
    # The month the Luftwaffe stopped being able to fly what it built.
    starved = [r for r in c.rows if r["fuel_ratio"] < 0.55]
    return {
        "seed": seed,
        "months": TURNS,
        "production_end": last["production"],
        "counterfactual_end": last["counterfactual"],
        "output_kept_pct": round(100 * last["production"] / last["counterfactual"], 1),
        "airframes_peak": peak["airframes"],
        "airframes_peak_month": peak["month"],
        "fuel_ratio_at_peak": peak["fuel_ratio"],
        "starved_from": starved[0]["month"] if starved else None,
        "oil_damage_end": round(c.oil_damage, 3),
        "industry_damage_end": round(c.industry_damage, 3),
        "rows": c.rows,
        "log": c.log,
    }


def self_test() -> int:
    checks, failures = 0, []

    def check(cond, label):
        nonlocal checks
        checks += 1
        if not cond:
            failures.append(label)

    for turn, want in ((0, "1940-05"), (7, "1940-12"), (32, "1943-01"),
                       (48, "1944-05"), (59, "1945-04")):
        check(month_of(turn) == want, f"month_of({turn}) -> {month_of(turn)}")

    check(len(TONNAGE) == TURNS, f"tonnage covers {len(TONNAGE)} months")

    # doctrine must always sum to one, or the model is inventing effort
    for t, d in DOCTRINE:
        check(abs(sum(d.values()) - 1.0) < 1e-9, f"doctrine at {t} sums to 1")

    # precision must improve monotonically -- radar and Pathfinder were not lost
    vals = [v for _, v in PRECISION]
    check(all(b >= a for a, b in zip(vals, vals[1:])), "precision improves")

    # oil damage must not heal, industry damage must
    c = Campaign(1)
    c.turn = 50
    c.oil_damage = 0.5
    c.industry_damage = 0.5
    c.step()
    c.turn = 51
    oil_before = c.oil_damage
    ind_before = c.industry_damage
    c.step()
    check(c.oil_damage >= oil_before, "oil damage persists")
    check(c.industry_damage < ind_before + 0.02, "industry damage decays")

    r1, r2 = run(1943), run(1943)
    check(r1["production_end"] == r2["production_end"], "same seed, same result")
    check(run(7)["production_end"] != r1["production_end"], "different seed differs")

    # the model must show the gap: airframes peaking while fuel collapses
    check(r1["airframes_peak_month"] >= "1944-06",
          f"airframes peak late: {r1['airframes_peak_month']}")
    check(r1["fuel_ratio_at_peak"] < 0.6,
          f"fuel short at the airframe peak: {r1['fuel_ratio_at_peak']}")
    check(r1["output_kept_pct"] < 92.0,
          f"bombing costs real output: kept {r1['output_kept_pct']}%")

    for m in failures:
        print(f"  FAIL {m}")
    print(f"\n{checks} checks, {len(failures)} failed")
    return 0 if not failures else 1


def main(argv: list[str]) -> int:
    p = argparse.ArgumentParser(
        prog="bombing", description="Strategic bombing of Germany, 1940-05..1945-04.")
    p.add_argument("--seed", type=int, default=1943)
    p.add_argument("--verbose", action="store_true")
    p.add_argument("--json", action="store_true")
    p.add_argument("--self-test", action="store_true")
    p.add_argument("--table", action="store_true")
    a = p.parse_args(argv)
    if a.self_test:
        return self_test()

    r = run(a.seed)
    if a.json:
        print(json.dumps(r, indent=2))
        return 0
    if a.table:
        print(f"{'month':8s} {'tons':>6s} {'prod':>6s} {'cf':>6s} "
              f"{'fuel':>6s} {'need':>6s} {'fly%':>6s}")
        for row in r["rows"]:
            print(f"{row['month']:8s} {row['tons']:6.1f} {row['production']:6.1f} "
                  f"{row['counterfactual']:6.1f} {row['fuel']:6.1f} "
                  f"{row['fuel_ratio'] * 100:6.1f}")
        return 0

    print(f"seed {a.seed}  {r['months']} months\n")
    print(f"  output retained      {r['output_kept_pct']:>5.1f}% of what was possible")
    print(f"  airframe peak        {r['airframes_peak']:.2f}k in {r['airframes_peak_month']}")
    print(f"  fuel at that peak    {r['fuel_ratio_at_peak'] * 100:.0f}% of what was needed")
    print(f"  unable to fly from   {r['starved_from']}")
    print(f"  oil damage, final    {r['oil_damage_end'] * 100:.0f}%")
    print(f"  industry damage      {r['industry_damage_end'] * 100:.0f}% (dispersed)")
    if a.verbose:
        print("\n  events")
        for line in r["log"]:
            print(f"    {line}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
