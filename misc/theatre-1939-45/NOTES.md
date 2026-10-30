# theatre — the Second World War as a strategic model

| file | what it is |
|---|---|
| `data.py` | theatres, industry curves, classes |
| `theatre.py` | the month loop |
| `accuracy.py` | outcome vs record; `--holdout` |

```
$ python3 theatre.py --seed 1939   →  73 months, ended 1945-09
$ python3 accuracy.py              →  10/10 theatres match the record
```

## Why not extend the 1940 model

`misc/blitz-1940` works at corps level on a one-dimensional front, which cannot
express a city battle, a landing, or a carrier engagement. One level up instead:
the unit is a **theatre**, the step a **month**, the state **resources** — the
only abstraction covering 73 months without lying about any of them.

## The rule

Per theatre per month: industry divided among the fronts that side is fighting;
doctrine and terrain multiply it; the ratio sets casualties, morale drift, and
control. A theatre closing within a quarter has a lower bar.

Front-count division is load-bearing: a side on three fronts cannot put its
whole output into one, and without it no theatre is ever decided.

## The honesty problem

`10/10` is not a result — the weights were tuned while looking at those
outcomes. Across 30 seeds: 10/10 for 17, 9/10 for 13, nothing below 9. That is
what a fitted model looks like.

`accuracy.py --holdout` sets a theatre's weights to neutral 0.5/0.5 and asks
what the model predicts anyway. North Africa, Pacific, Italy, Barbarossa and
Normandy all still come out allied, so the late war is driven by the industry
curves — which came from production figures, not outcomes. That defence does
not extend to 1939-41, where the weights do the work.

## What it cannot do

No counter-offensive mechanic; casualties in arbitrary units, so only the ratio
is meaningful; no bombing, convoys or lend-lease, so the allied advantage is a
table lookup; no politics, so opening dates are inputs and the war's shape is
assumed. **Can** answer: is an axis victory in 1941-42 plausible at these
ratios? **Cannot**: why Barbarossa failed.
