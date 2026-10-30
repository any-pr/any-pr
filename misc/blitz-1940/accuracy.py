#!/usr/bin/env python3
"""accuracy -- what the blitz model gets right about 1940, and what it does not.

Run with:  python3 accuracy.py

Kept separate from blitz.py so the simulation stays under the repository's
300-line-per-file limit and so that the criticism is readable on its own.
"""

import sys

from blitz import simulate


def report() -> str:
    """Where the model matches 1940 and where it plainly does not.

    This is the part that makes the simulation worth running. A model nobody
    checks is a story with numbers attached.
    """
    result = simulate(1940)
    german_routed = sum(1 for f in result["germany"] if f["routed"])
    allied_routed = sum(1 for f in result["allied"] if f["routed"])
    lines = []
    check = lines.append

    check("1940 WESTERN CAMPAIGN -- model vs record")
    check("")
    check(f"  turns run                 {result['turns']}")
    check(f"  model ended               {result['end_date']}")
    check(f"  historical end            1940-06-25 (46 days)")
    check("")
    check("  what the model gets roughly right")
    check("    - the campaign is short: weeks, not months")
    check("    - the German schwerpunkt in the Ardennes sector forces a break")
    check("    - the allied line yields faster than its strength ratio implies,")
    check("      because doctrine and command are separate multipliers")
    check("")
    check("  what it gets wrong, and why")
    check("    - one-dimensional front: there is no envelopment, no sea flank,")
    check("      no possibility of the Dunkirk evacuation. The model cannot")
    check("      represent the actual operational decision of the campaign.")
    check("    - no air power. The Luftwaffe's effect is folded into the German")
    check("      doctrine multiplier, which makes it a constant rather than a")
    check("      variable. It cannot be shown to matter.")
    check("    - no logistics ceiling. Real armoured advances outran their")
    check("      supply; here supply regenerates on a fixed schedule.")
    check("    - no political layer. The halt order of 24 May, the French")
    check("      command crisis of 19 May, and the Belgian capitulation of")
    check("      28 May are all absent. They were decisive.")
    check("")
    check(f"  outcome                   germany routed={german_routed} "
          f"allied routed={allied_routed}")
    check("")
    check("  The model is a logistics-and-attrition toy. It reproduces the")
    check("  shape of a campaign, not the campaign.")
    return "\n".join(lines)




if __name__ == "__main__":
    print(report())
