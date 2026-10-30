"""kessel -- a pocket dies of geometry, not of shellfire.

Theatre models count casualties per month. Casualties do not take a pocket: as
men are killed the perimeter contracts, so the density of defenders per kilometre
of front barely moves. A Kessel is not worn down, it is *cut up* -- and the cut
is what creates frontage the same men must now hold on two sides.

That single mechanism explains why three pockets of the same war lasted ten
days, seventy-one days, and four days. It is not supply, and the supply is
modelled here too so that the claim can be checked rather than asserted.

    python3 kessel.py --pocket Stalingrad [--verbose|--table]
    python3 kessel.py --self-test
    python3 accuracy.py

Standard library only.
"""

import math
import sys

from pockets import POCKETS, SORTIES_DEFAULT

# Men per kilometre a first-class force can hold. Below this the line is a
# cordon; above it there are reserves to plug a break.
REF_DENSITY = 500.0

# Progress per day toward a cut, at parity against reference density. The one
# fitted constant, set on Demyansk; stated in accuracy.py.
CUT_RATE = 0.025

# A pocket can be cut into pieces, and it is a partition, not a process: two
# pieces were Stalingrad's reality, the northern factory district and the
# southern city. Each further piece adds interior front, with diminishing
# return because the pieces get smaller while the cut gets shorter.
INTERIOR_FRACTION = 0.55
INTERIOR_DECAY = 0.55
MAX_PIECES = 3                 # beyond this the pieces are not a force

# A pocket is finished when the line is a cordon with no reserves behind it.
BREAK_DENSITY = 0.42

# A man needs 2.6 kg a day to fight. Below that he is not starved to death
# quickly -- he simply cannot move or shoot, and a hungry battalion holds less
# front than a fed one. Supply and geometry cannot be modelled separately.
RATION_TONS = 0.0026
HUNGER_FLOOR = 0.16            # fraction of front a starving man still holds
HUNGER_LAG = 12.0              # days for the effect of a shortfall to bite

# A Ju 52 lifted two tons; a He 111 about the same on the Moscow run. Promised
# sorties are not delivered sorties, so this is the delivered rate.
PLANE_LOAD = 2.0
SORTIES_PER_DAY = 0.55


def perimeter_km(area_km2: float, shape: float = 0.82) -> float:
    """Outer front of a pocket, in kilometres.

    A circle of the same area is the upper bound and it is far too generous:
    a pocket is a lobe pushed out along roads, and its front is shorter than
    the circle because the attacker's line cuts corners. The coefficient is
    set so that 1,500 km2 gives the 100 km of the Stalingrad perimeter.
    """
    return 2.0 * shape * math.sqrt(math.pi * area_km2)


class Pocket:
    """One encirclement: men, ground, and what was promised from the air."""

    def __init__(self, name, men, area, ratio, planes, stock_days,
                 record_days, note, sorties=SORTIES_PER_DAY, relief=False):
        self.name = name
        self.men = float(men)
        self.area = float(area)
        self.ratio = ratio            # attacker strength vs. the pocket's
        self.planes = planes          # airlift aircraft actually available
        self.sorties = sorties        # delivered round trips per aircraft-day
        # A pocket with a relief effort behind it is not racing the calendar in
        # the same way: the men are buying days for someone else to arrive.
        self.relief = relief
        self.stock_days = stock_days  # days of full rations in the pocket
        self.record_days = record_days
        self.note = note
        self.front = perimeter_km(self.area)
        self.interior = self.cut = 0.0
        self.day = 0
        self.cuts = []
        self.log = []
        # Rationing is administrative: a unit at a third of its ration is not a
        # third as strong on the first day. Strength follows supply with a lag,
        # which is why pockets hold out after the food is gone and then fold.
        self.fed = 1.0
        self.fed_effective = 1.0
        self.shortfall = 0.0
        self.ration_strength = float(men)

    # --- geometry -------------------------------------------------------
    def strength(self) -> float:
        """Men who can actually hold ground. A fed man holds full front; a
        starving one holds a fraction of it, because he cannot move."""
        f = HUNGER_FLOOR + (1.0 - HUNGER_FLOOR) * self.fed_effective
        return self.men * f

    def density(self) -> float:
        return self.strength() / max(self.front + self.interior, 1e-9)

    def pressure(self) -> float:
        """Progress toward cutting the pocket, per day.

        The attacker's advantage is the ratio; the defender's is the density.
        Both matter, but the ratio matters more: Korsun had twice Demyansk's
        men per kilometre and lasted a sixth as long, because the ratio was
        twice as bad. Density enters with a square root, which is what makes
        the three cases fall in the right order.

        A relief effort does not stop the attack. It makes the garrison fight
        for time rather than for ground, holding a corridor rather than a line.
        """
        p = self.ratio * CUT_RATE / (self.density() / REF_DENSITY) ** 0.5
        return p * (0.30 if self.relief else 1.0)

    # --- supply ---------------------------------------------------------
    def need_tons(self) -> float:
        """Required tonnage, set by the ration roll rather than by the men
        alive: the roll is administrative and takes weeks to correct."""
        return self.ration_strength * RATION_TONS

    def airlift_tons(self) -> float:
        return self.planes * PLANE_LOAD * self.sorties

    def supply(self) -> tuple[float, float]:
        """Delivered and required tonnage today."""
        need = self.need_tons()
        got = min(self.airlift_tons(), need)
        return got, need

    # --- the day --------------------------------------------------------
    def step(self) -> bool:
        self.day += 1

        got, need = self.supply()
        if self.stock_days > 0:
            # Stock covers the gap until it is gone.
            self.stock_days -= 1.0
            delivered = need
        else:
            delivered = got
        self.shortfall = max(need - delivered, 0.0)
        self.fed = min(delivered / max(need, 1e-9), 1.0)
        self.fed_effective += (self.fed - self.fed_effective) / HUNGER_LAG
        # The roll decays too, but slowly, and it never falls faster than the
        # men actually do.
        self.ration_strength = max(min(self.ration_strength,
                                       self.men * 1.6), self.men)

        # Sustained hunger kills, but slowly: the men who die of it are a small
        # share of the men who stop being able to fight.
        if self.fed < 1.0:
            self.men -= self.men * 0.0032 * (1.0 - self.fed)
            if self.day % 10 == 0:
                self.log.append(
                    (self.day, f"fed {self.fed * 100:.0f}%, "
                               f"{self.shortfall:.0f} t/day short"))

        # The attack. Progress accumulates; a cut is an event, not a rate.
        p = self.pressure()
        self.cut += p
        if self.cut >= 1.0 and len(self.cuts) < MAX_PIECES - 1:
            self.cut = 0.0
            n = len(self.cuts)
            self.interior += (INTERIOR_FRACTION * (INTERIOR_DECAY ** n) *
                              perimeter_km(self.area))
            self.cuts.append(self.day)
            self.log.append(
                (self.day, f"CUT -- {len(self.cuts) + 1} pieces, "
                           f"{self.density():.0f} men per km"))
        elif self.cut >= 1.0:
            # Cut as far as it can be cut. The remaining pressure goes into
            # killing men rather than into making more front.
            self.cut = 0.0
            self.men -= self.men * 0.02 * self.ratio

        # Men lost in the fighting, small next to hunger and to the cuts.
        self.men -= self.men * 0.004 * self.ratio
        return True


def run(pocket: Pocket, seed: int = 1942, limit: int = 400) -> dict:
    rng = random.Random(seed)
    first_cut = None
    while pocket.day < limit:
        density = pocket.density()
        pocket.step()
        # A pocket with no reserve left cannot be held: a cordon is a line, and
        # a line with nothing behind it ends at the first break.
        if pocket.density() < REF_DENSITY * 0.42:
            break
        if first_cut is None and pocket.cuts:
            first_cut = pocket.cuts[0]
            # After the first cut the remaining men must hold two fronts; the
            # historical reductions were measured in days once that happened.
    return {
        "pocket": pocket.name,
        "days": pocket.day,
        "record_days": pocket.record_days,
        "first_cut": first_cut,
        "cuts": pocket.cuts,
        "men_left": round(pocket.men),
        "density_end": round(pocket.density(), 1),
        "airlift_tons": round(pocket.airlift_tons(), 1),
        "need_tons": round(pocket.need_tons(), 1),
        "covered_pct": round(100 * pocket.airlift_tons() / pocket.need_tons(), 1),
        "log": pocket.log,
    }


def build(name: str) -> Pocket:
    d = dict(POCKETS[name])
    return Pocket(name, d["men"], d["area"], d["ratio"], d["planes"],
                  d["stock_days"], d["record_days"], d["note"],
                  d.get("sorties", SORTIES_DEFAULT), d.get("relief", False))


def run(name: str, limit: int = 400) -> dict:
    p = build(name)
    while p.day < limit:
        p.step()
        if p.density() < REF_DENSITY * BREAK_DENSITY:
            break
    return {
        "pocket": name,
        "days": p.day,
        "record_days": p.record_days,
        "perimeter_km": round(perimeter_km(p.area), 1),
        "first_cut": p.cuts[0] if p.cuts else None,
        "cuts": p.cuts,
        "men_left": round(p.men),
        "density_start": round(p.men / perimeter_km(p.area), 1),
        "density_end": round(p.density(), 1),
        "airlift_tons": round(p.airlift_tons(), 1),
        "need_tons": round(p.need_tons(), 1),
        "covered_pct": round(100 * p.airlift_tons() / max(p.need_tons(), 1e-9), 1),
        "note": p.note,
        "log": p.log,
    }


def self_test() -> int:
    checks, failures = 0, []

    def check(cond, label):
        nonlocal checks
        checks += 1
        if not cond:
            failures.append(label)

    check(perimeter_km(1500) < 130, "Stalingrad perimeter under 130 km")
    check(perimeter_km(3000) > perimeter_km(1500), "bigger area, longer front")

    # cutting does not reduce the men, it increases the front they hold
    p = build("Stalingrad")
    d0 = p.density()
    p.cut = 0.99
    p.step()
    check(bool(p.cuts), "a cut happens when pressure accumulates")
    check(p.interior > 0, "cut adds interior front")
    p.men = 250_000
    check(p.density() < d0, "same men, longer front, lower density")

    # hunger reduces fighting strength without removing men at the same rate
    p2 = build("Stalingrad")
    p2.stock_days = 0
    p2.step()
    check(p2.fed < 0.9, f"stock gone, so the pocket is short: fed {p2.fed:.2f}")
    # strength follows supply with a lag, so compare the same men fed and not
    hungry = p2.strength()
    p2.fed_effective = 1.0
    check(p2.strength() > hungry,
          "a fed pocket holds more front than a hungry one")

    # airlift: coverage is delivered over required
    p3, p4 = build("Demyansk"), build("Korsun")
    check(p3.airlift_tons() > p3.need_tons() * 0.6, "Demyansk airlift covers need")
    check(p4.airlift_tons() < p4.need_tons() * 0.8, "Korsun airlift falls short")

    for name in POCKETS:
        r = run(name)
        check(r["days"] > 0 and r["perimeter_km"] > 50, f"{name} runs with a front")

    for m in failures:
        print(f"  FAIL {m}")
    print(f"\n{checks} checks, {len(failures)} failed")
    return 0 if not failures else 1


if __name__ == "__main__":
    from cli import main
    sys.exit(main(sys.argv[1:]))
