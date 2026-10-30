#!/usr/bin/env python3
"""models -- the one-dimensional front and the formations that hold it.

Separated from blitz.py so each file stays readable and under the repository's
300-line limit. See blitz.py for the turn loop and accuracy.py for what this
abstraction cannot represent.
"""

# Front line runs north (Channel coast) to south (Maginot terminus), split into
# five sectors. Positions are sector indices; a side "holds" a sector when its
# position equals or exceeds it. This is a one-dimensional abstraction: real
# fronts are not lines, which is the model's largest known distortion.
SECTORS = [
    "Channel coast (Calais-Boulogne)",
    "Northern (Lille-Arras)",
    "Central (Somme-Aisne)",
    "Ardennes (Sedan-Rethel)",
    "Southern (Maginot terminus)",
]

# Order of magnitude strengths at 10 May 1940. Personnel in thousands,
# armoured vehicles in units. Sources disagree by tens of percent; these are
# midpoints of commonly cited ranges.
ANCHORS = {
    "germany": {"personnel_k": 2700, "armour": 2445, "aircraft": 4000},
    "allied": {"personnel_k": 3300, "armour": 3100, "aircraft": 2900},
}

# Per-turn modifiers. `doctrine` multiplies combat power; `command` scales how
# fast a routed formation recovers; `supply_reach` is how many sectors a depot
# can sustain before supply attenuates.
SIDES = {
    "germany": {"doctrine": 1.35, "command": 1.20, "supply_reach": 3},
    "allied": {"doctrine": 0.85, "command": 0.80, "supply_reach": 2},
}

# Terrain multipliers per sector, applied to the defender.
TERRAIN = [1.05, 1.15, 1.20, 1.35, 1.50]

COHESION_FLOOR = 0.30
MAX_TURNS = 46          # 10 May to 25 June 1940 inclusive
START_DATE = (1940, 5, 10)


class Formation:
    """One corps-sized element. Only four numbers matter."""

    def __init__(self, side: str, name: str, strength: float, armour: float,
                 sector: int):
        self.side = side
        self.name = name
        self.strength = strength      # personnel, thousands
        self.armour = armour          # vehicles
        self.sector = sector
        self.supply = 1.0             # 0..1
        self.cohesion = 1.0           # 0..1; routs below the floor
        self.routed = False

    def power(self, terrain: float) -> float:
        """Combat power: doctrine-weighted mass, degraded by supply."""
        mod = SIDES[self.side]
        mass = self.strength * 0.7 + self.armour * 0.3
        return mass * mod["doctrine"] * self.supply * terrain

    def __repr__(self) -> str:
        return (f"<{self.side} {self.name} s={self.strength:.0f} "
                f"a={self.armour:.0f} x={self.sector} sup={self.supply:.2f} "
                f"coh={self.cohesion:.2f}{' ROUTED' if self.routed else ''}>")


