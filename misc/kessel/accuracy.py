#!/usr/bin/env python3
"""accuracy -- three pockets against the calendar, and why they differ.

    python3 accuracy.py

A pocket's life is set by men per kilometre of front, and by how many kilometres
the attacker can force it to hold. CUT_RATE is the one free parameter, set to
reproduce Demyansk; the other two are predictions. Stalingrad lands within a day
and Korsun within two -- evidence or luck, and with two points it cannot be
settled.
"""

import kessel as K


def report() -> str:
    out = []
    add = out.append

    add("THREE POCKETS -- the model against the record")
    add("")
    add(f"  {'pocket':12s} {'model':>6s} {'record':>7s} {'error':>7s} "
        f"{'men/km':>7s} {'ratio':>6s} {'airlift':>8s}")
    days = {}
    for name in ("Demyansk", "Stalingrad", "Korsun"):
        p = K.build(name)
        start = p.men / p.front
        r = K.run(name)
        days[name] = r["days"]
        add(f"  {name:12s} {r['days']:>6d} {r['record_days']:>7d} "
            f"{r['days'] - r['record_days']:>+7d} {start:>7.0f} "
            f"{p.ratio:>6.2f} {r['covered_pct']:>7.0f}%")
    add("")

    add("  THE MECHANISM")
    add("    Casualties do not take a pocket: a shrunken perimeter raises the")
    add("    density of the survivors. A cut does, because the attacker")
    add("    creates an interior front the same men must also hold.")
    add("    Demyansk: 628 men/km at ratio 1.15, pressure 0.008. Korsun: 482")
    add("    at 2.10, pressure 0.054 -- seven times, from a ratio less than")
    add("    twice as bad, because thin density multiplies it. The ordering")
    add("    is the ratio's: Stalingrad had the most men per kilometre of all")
    add("    three and still fell.")
    add("")

    add("  AIRLIFT")
    add("    Stalingrad: 500 promised, 22% coverage. Demyansk: 87%. Airlift is")
    add("    not a supply line but a fraction of one, set by serviceability.")
    add("")

    add("  WHAT IS FITTED, AND WHAT IS A PREDICTION")
    add("    CUT_RATE is set so Demyansk comes out at 105 days: one fitted")
    add("    parameter against three outcomes. Stalingrad then lands at 71")
    add("    against 72, Korsun at 18 against 16. Two predictions within two")
    add("    days is the strongest result here, and worth being clear how")
    add("    weak that is: two points, one parameter, three similar cases.")
    add("")
    add("    What would make it convincing is a fourth pocket with a")
    add("    different shape -- Kholm or a Pacific encirclement -- held out")
    add("    and never used to set a constant. It is not here because I do")
    add("    not have its perimeter and ration figures, and inventing them")
    add("    would destroy the only thing this file is worth.")

    return "\n".join(out)


if __name__ == "__main__":
    print(report())
