# sensitivity — does any parameter in the model actually matter?

The theatre model reports 10/10 against the historical record, and
`accuracy.py` admits that number is fitted. A more damning question: does the
model respond to its inputs at all?

```
$ python3 sensitivity.py

  parameter              range x        outcomes
  axis doctrine          0.60-2.40            3
  allied doctrine        0.50-2.00            2
  axis production        0.50-2.00            2
  allied production      0.60-2.20            2
  axis attrition tol     0.60-1.40            1   <- inert
  allied attrition tol   0.70-1.60            1   <- inert
  axis industry 1941     0.40-3.00            1   <- inert
  allied industry 1944   0.25-2.00            1   <- inert
  axis industry 1944     0.40-2.50            1   <- inert

  5 of 9 parameters change nothing.
```

## What this means

**Axis industry in 1941 can be multiplied by three, or cut to 0.4, and
Barbarossa's outcome does not move.** The German war economy is, in this model,
decoration. That is a stronger statement than "the weights were fitted": fitted
weights at least respond to something.

The four parameters that do move the outcome are all doctrine or production
multipliers — flat numbers applied to every theatre at once. So the model reacts
to a global dial and ignores the data tables.

## Why

Control flips the first time a theatre's contest crosses a bar, and never flips
back. Once a theatre is decided, no later ratio can reach it. Only parameters
that act *during that theatre's own window* can change anything, and for the
short theatres — Poland, France, Normandy, Balkans — that window is one or two
months.

Barbarossa runs 43 months, so in principle it could move. It does not, because
its force ratio starts at 1.62 and falls below 1.0 by 1943; the contest
accumulates during 1941-42 but the gain per month is capped, and the accumulated
value never crosses the bar before the ratio turns against the axis.

## The fix that was tried, and what it showed

Making control reversible — recomputing it each month from contest instead of
latching it — raises the count of responsive parameters but does not fix the
structure:

| parameter | inert (latched) | inert (reversible) |
|---|---|---|
| axis doctrine | no | no |
| allied industry 1944 | yes | no |
| axis industry 1941 | yes | yes |

Industry still barely matters. The cause is upstream: a theatre is a single
scalar, so an offensive either crosses the bar in its opening month or it never
does. Real campaigns change hands because a *corps* is committed or withheld, and
this model has no corps.

The corps-level model in `misc/blitz-1940` does have them, which is why its
parameters matter more — and why it cannot cover the whole war.

## Why ship this

Because the alternative is another 10/10 in a README. A model that cannot be
moved by its own inputs should say so, in a form a reader can run and re-derive.

`sensitivity.py --quick` takes five steps per parameter; the default takes
thirteen. Every change is reverted before the next sweep, and `--self-test`
checks that the round trip is clean and that a 6x doctrine change does move the
war — so the tool itself is not silently broken.
