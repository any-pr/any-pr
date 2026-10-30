#!/usr/bin/env python3
"""data -- the tables and classes this model is built from.

Split out so theatre.py stays under the repository's 300-line limit. Read the
comments here before changing a number: the weights are where this model's
honesty problem lives, and accuracy.py explains why.
"""

# Theatres open on these months. Dates are the historical opening of sustained
# fighting, not the first shot, and are deliberately coarse: this model cannot
# resolve weeks.
# name, opens, closes, terrain, holder, axis_weight, allied_weight
#
# The weights are the crux: national industry is not what fights in a theatre.
# In May 1940 all German output pointed at France while the allied total
# carried Britain and empire resources not on that front.
THEATRES = [
    ("Poland", "1939-09", "1939-10", 1.00, "axis", 1.00, 0.45),
    ("Finland", "1939-11", "1940-03", 1.60, "neutral", 0.55, 0.30),
    ("France", "1940-05", "1940-06", 1.10, "allied", 1.00, 0.55),
    ("Britain", "1940-07", "1941-05", 2.20, "allied", 0.45, 1.00),
    ("North Africa", "1940-06", "1943-05", 1.15, "contested", 0.35, 0.30),
    ("Balkans", "1941-04", "1941-05", 1.25, "axis", 0.55, 0.25),
    ("Barbarossa", "1941-06", "1944-12", 1.30, "contested", 0.95, 0.60),
    ("Pacific", "1941-12", "1945-08", 1.40, "contested", 0.55, 0.80),
    ("Italy", "1943-07", "1945-04", 1.45, "contested", 0.40, 0.45),
    ("Normandy", "1944-06", "1944-08", 1.20, "allied", 0.50, 1.00),
]

# doctrine x power; production scales industry; attrition_tolerance on morale.
SIDES = {
    "axis": {"doctrine": 1.30, "production": 1.00, "attrition_tolerance": 0.90},
    "allied": {"doctrine": 0.95, "production": 1.45, "attrition_tolerance": 1.20},
}

# Industrial output, arbitrary units. Only the ratios matter.
YEARS = ["1939", "1940", "1941", "1942", "1943", "1944", "1945"]
INDUSTRY = {
    "axis": dict(zip(YEARS, [60, 72, 80, 92, 105, 112, 70])),
    "allied": dict(zip(YEARS, [48, 62, 74, 120, 168, 200, 185])),
}

# Manpower, in hundreds of thousands, available at the start of each year.
MANPOWER = {
    "axis": dict(zip(YEARS, [45, 60, 85, 95, 90, 80, 45])),
    "allied": dict(zip(YEARS, [60, 80, 110, 150, 175, 190, 170])),
}

MAX_MONTHS = 73      # 1939-09 through 1945-09
START = (1939, 9)


def month_of(index: int) -> str:
    """Campaign calendar: index 0 is 1939-09."""
    y, m = START
    total = y * 12 + (m - 1) + index
    return f"{total // 12:04d}-{total % 12 + 1:02d}"


def _window_months(opens: str, closes: str) -> int:
    """Inclusive month count between two YYYY-MM stamps."""
    oy, om = int(opens[:4]), int(opens[5:7])
    cy, cm = int(closes[:4]), int(closes[5:7])
    return (cy * 12 + cm) - (oy * 12 + om) + 1


class Theatre:
    """One contested area. Positions are abstract: only control matters."""

    def __init__(self, name: str, opens: str, closes: str, terrain: float,
                 holder: str, ax_weight: float = 0.5,
                 al_weight: float = 0.5):
        self.name = name
        self.opens = opens
        self.closes = closes
        self.terrain = terrain
        self.control = holder
        self.ax_weight = ax_weight
        self.al_weight = al_weight
        self.contest = 0.0     # >0 means axis is winning, <0 allied
        self.months = 0
        # Share of industry committed here; a side on three fronts cannot
        # pour everything into one. Without this no theatre is ever decided.
        self.axis_commitment = 0.5
        self.allied_commitment = 0.5

    def active(self, stamp: str) -> bool:
        return self.opens <= stamp <= self.closes


class Side:
    """Aggregate resources for one side across all theatres."""

    def __init__(self, name: str):
        self.name = name
        self.manpower = MANPOWER[name]["1939"]
        self.supply = 1.0
        self.casualties = 0.0
        self.morale = 1.0

    def industry(self, year: str) -> float:
        return INDUSTRY[self.name].get(year, 50.0)

    def reset_year(self, year: str) -> None:
        self.manpower = MANPOWER[self.name].get(year, 30.0)

    def power(self, terrain: float, industry: float) -> float:
        """Front-line power: industry times doctrine, degraded by supply."""
        mod = SIDES[self.name]
        return (industry * mod["production"] * mod["doctrine"]
                * self.supply * self.morale * terrain)


