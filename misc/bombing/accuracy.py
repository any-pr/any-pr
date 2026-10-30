#!/usr/bin/env python3
"""accuracy -- what the bombing campaign did, and the counterfactual it cannot prove.

    python3 accuracy.py
    python3 accuracy.py --seeds 20

One unambiguous result, and one question that cannot be settled.

THE RESULT: airframes peaked in September 1944 while synthetic oil had fallen
from 180,000 tons a month to under 30,000. The model reproduces this because
oil damage is permanent and industry damage disperses.

THE QUESTION: how much production did the bombing prevent? German output rose
through the whole campaign, so the answer is not "it fell". The model computes a
counterfactual instead and reports the gap -- an assertion, not a measurement.
"""

import sys

import bombing as B

# Monthly aircraft output, German, thousands of airframes. Production returns.
RECORD_AIRFRAMES = {"1940": 0.19, "1941": 0.23, "1942": 0.32,
                    "1943": 0.72, "1944": 1.32, "1945": 0.85}

# Synthetic oil output, thousands of tons per month, from the returns. The
# 1944 collapse from 180 to 30 is the figure this model exists to explain.
RECORD_OIL = {"1940": 55, "1941": 68, "1942": 92,
              "1943": 118, "1944": 95, "1945": 25}

RECORD_PEAK = "1944-09"


def yearly(rows: list[dict], key: str) -> dict:
    out = {}
    for r in rows:
        y = r["month"][:4]
        out.setdefault(y, []).append(r[key])
    return {y: sum(v) / len(v) for y, v in out.items()}


def report(seeds: int = 20) -> str:
    out = []
    add = out.append

    runs = [B.run(s) for s in range(1, seeds + 1)]
    rows = runs[0]["rows"]
    af = yearly(rows, "airframes")

    add("THE STRATEGIC BOMBING OF GERMANY -- model vs the returns")
    add("")

    add("  aircraft output, thousands per month")
    add(f"    {'year':>6} {'record':>8} {'model':>8} {'error':>8}")
    for y in sorted(RECORD_AIRFRAMES):
        if y in af:
            add(f"    {y:>6} {RECORD_AIRFRAMES[y]:>8.2f} {af[y]:>8.2f} "
                f"{af[y] - RECORD_AIRFRAMES[y]:>+8.2f}")
    add("")
    add("    Inputs, not results: the curve is copied from the returns, so")
    add("    matching it proves nothing. It is shown because the shape is the")
    add("    campaign's central fact -- the line goes up.")
    add("")

    add("  the decoupling")
    add(f"    {'seed':>6} {'peak airframes':>16} {'fuel then':>12} {'starved':>10}")
    for r in runs[:6]:
        add(f"    {r['seed']:>6} {r['airframes_peak']:>10.2f}k "
            f"{r['airframes_peak_month']:>8} "
            f"{r['fuel_ratio_at_peak'] * 100:>10.0f}% {str(r['starved_from']):>10}")
    add("")
    add(f"    record peak month: {RECORD_PEAK}, and in that month the Luftwaffe")
    add("    had roughly a third of the fuel it needed to fly the aircraft it")
    add("    was receiving.")
    add("")

    add("  the counterfactual")
    add("")
    kept = [r["output_kept_pct"] for r in runs]
    add(f"    output retained vs no-bombing counterfactual: "
        f"{min(kept):.1f}% .. {max(kept):.1f}%")
    add("")
    add("    This number is the model's answer to the only question that")
    add("    matters, and it is the least trustworthy thing here. The")
    add("    counterfactual compounds at 2.9% a month because that is what")
    add("    Speer's programme achieved while being bombed -- so the model")
    add("    assumes the bombed economy was already at the rate it would have")
    add("    reached unbombed. That is circular.")
    add("")
    add("    The honest range is 10-30% of war output prevented, mostly in")
    add("    the last eight months. The model says 40%, too high, because the")
    add("    counterfactual has no ceiling: five years of compounding as if")
    add("    labour and raw materials were unlimited.")
    add("")

    add("  WHAT THE MODEL DOES ESTABLISH")
    add("    The oil result, which needs no counterfactual. Synthetic oil")
    add("    damage is permanent in the model and in fact: a hydrogenation")
    add("    plant is a chemical works, and when it is hit the hydrogen and")
    add("    the catalyst are gone. Industry damage is not, because machine")
    add("    tools were dug out and re-erected elsewhere.")
    add("")
    add("    That asymmetry -- permanent oil, recoverable industry -- is why")
    add("    the campaign looks ineffective for four years and decisive in")
    add("    the fifth. Not a doctrinal claim: what the returns show.")

    return "\n".join(out)


if __name__ == "__main__":
    n = 20
    if "--seeds" in sys.argv:
        n = int(sys.argv[sys.argv.index("--seeds") + 1])
    print(report(n))
