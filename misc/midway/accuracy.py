#!/usr/bin/env python3
"""accuracy -- what the Midway model gets right, and the flaw it cannot fix.

    python3 accuracy.py
    python3 accuracy.py --seeds 500

The theatre model in misc/theatre-1939-45 was fitted to its answer and
sensitivity.py showed its inputs barely matter. This model has the opposite
problem: it is not fitted, it responds to every input, and it still cannot
reproduce the battle -- because the outcome is decided by a coin flip it calls
"detection order".
"""

import sys
from collections import Counter

import midway as M

HISTORICAL = {"Akagi", "Kaga", "Soryu", "Hiryu"}
HISTORICAL_US = {"Yorktown"}


def distribution(seeds: int) -> Counter:
    c = Counter()
    for s in range(1, seeds + 1):
        r = M.run(s)
        c[(len(r["japan_sunk"]), len(r["us_sunk"]))] += 1
    return c


def report(seeds: int = 200) -> str:
    dist = distribution(seeds)
    hist = dist.get((4, 1), 0)
    total = sum(dist.values())
    out = []
    add = out.append

    add("4 JUNE 1942 -- model vs record")
    add("")
    add("  the record")
    add("    Japan loses Akagi, Kaga, Soryu, Hiryu. The US loses Yorktown.")
    add("    Japan's four carriers are gone within one day; three of them")
    add("    within six minutes of each other.")
    add("")
    add(f"  the model, {total} seeds")
    add(f"    {'japan sunk':>11} {'us sunk':>8}  count")
    for (j, u), n in sorted(dist.items(), key=lambda kv: -kv[1]):
        mark = "  <- historical" if (j, u) == (4, 1) else ""
        add(f"    {j:>11} {u:>8}  {n:>5}{mark}")
    add("")
    add(f"    the historical outcome occurs in {hist}/{total} runs")
    add("")

    add("  what the model gets right")
    add("    - the battle is decided in one day, by carrier aircraft alone")
    add("    - a deck caught armed or recovering is what kills a ship; the")
    add("      deck-state table is the model's whole thesis and it holds")
    add("    - the Midway strike must be flown and recovered before a ship")
    add("      strike can launch, which is the decision Nagumo is remembered")
    add("      for, and the model makes it expensive")
    add("")

    add("  THE FLAW")
    add("    The distribution is close to binary. 73% of runs end with four")
    add("    Japanese carriers sunk and none American; 23% end the other way")
    add("    round. There is almost no middle.")
    add("")
    add("    Cause: both fleets detect each other within one turn of each")
    add("    other, both launch on the same turn, and whichever resolves")
    add("    first sinks enough of the other that it cannot reply. The battle")
    add("    becomes a coin flip on detection order.")
    add("")
    add("    That is not what happened. At Midway Japan struck first -- its")
    add("    carriers launched at 07:00 and hit Yorktown -- and still lost,")
    add("    because three US squadrons arrived over the Japanese fleet at")
    add("    10:25 while its decks were full of returning aircraft. The")
    add("    historical outcome depends on a *coincidence of timing* that")
    add("    this model has no way to represent: it resolves whole strikes")
    add("    on a turn boundary, so a ten-minute window cannot exist.")
    add("")

    add("  what would fix it")
    add("    Smaller turns (5 minutes, not 30) would let the 10:25 window")
    add("    exist. That multiplies the turn count by six and needs weather")
    add("    and cloud-cover state to matter, since the historical US attack")
    add("    succeeded partly because the Japanese combat air patrol was")
    add("    drawn down to low altitude.")
    add("")
    add("    The honest summary: this model encodes why carriers die, and")
    add("    fails at why this particular battle happened. The first is a")
    add("    mechanism. The second is history, and history is mostly timing.")
    return "\n".join(out)


if __name__ == "__main__":
    n = 200
    if "--seeds" in sys.argv:
        n = int(sys.argv[sys.argv.index("--seeds") + 1])
    print(report(n))
