# atlantic — the Battle of the Atlantic as a tonnage war

Three models here already fail at the Atlantic: `blitz-1940` has a front line
and the Atlantic has none; `theatre-1939-45` has ten theatres and the Atlantic
is not one of them; `midway` resolves a battle in a day and this one ran for 68
months.

The Atlantic is the longest campaign of the war and its outcome is not
territory. It is a **crossing point on a graph**: the month after which Allied
shipyards out-built the torpedo and never fell behind again.

| file | what it is |
|---|---|
| `atlantic.py` | the tonnage war, 69 months from 1939-09 |
| `accuracy.py` | yearly sinkings and the crossing against the returns |

```
$ python3 atlantic.py --verbose
seed 1939  69 months

  tonnage afloat     22.3M tons
  tons sunk          18.2M
  tons built         46.9M
  boats in service  242.7
  boats lost        922.3
  shipyards ahead  since 1943-03

  events
    1943-05  Black May: 33 boats lost, 0.21M tons sunk, 371 boats still in service
```

## The two curves

Shipbuilding comes from production figures. Sinkings are the fleet multiplied
by a per-boat productivity that depends on one number, ASW.

```
month    ships sunk/month   boats on patrol
1942-05       182.1             33.9
1943-01       139.7             44.3
1943-05        40.5             49.5      <- the collapse
1944-01        24.3             45.4
```

**Sinkings fell 71% while the number of boats on patrol went up.** That is the
whole argument. German boats did not get worse at sinking ships in 1943 — there
were more of them than ever. They stopped arriving.

## ASW is the model

`ASW_SCHEDULE` is where every judgement in this model lives. Each entry is a
month and the value it set:

| month | ASW | what arrived |
|---|---|---|
| 1939-09 | 0.03 | a handful of escorts, no air cover at all |
| 1941-04 | 0.17 | surface radar, first escort groups |
| **1942-01** | **0.06** | **the Happy Time — US enters, its coast is lit up** |
| 1942-07 | 0.16 | coastal blackout at last |
| **1943-05** | **0.62** | **Black May — escort carriers close the air gap** |
| 1944-01 | 0.74 | hunter-killer groups hunt boats, not convoys |

Productivity is exponential in ASW. A linear model makes the war a grind; the
real war was a cliff, which is why May 1943 is remembered and April is not.

## Accuracy

```
$ python3 accuracy.py
      year   record    model    error
      1939     0.75     0.36    -0.39
      1940     3.27     1.57    -1.70
      1941     4.32     3.32    -1.00
      1942     8.25     8.13    -0.12
      1943     3.20     3.58    +0.38
      1944     1.05     1.01    -0.04
      1945     0.45     0.27    -0.18
    mean absolute error: 0.54M tons
```

The crossing comes out at **1943-02..03** against a record of **1943-05**. Four
months early, and the cause is known: the shipbuilding curve is too steep for
early 1943 because the model cannot divert a quarter of US output to the
Pacific. A diversion constant would fix the date — and would be fitted to the
outcome it predicts, which is the exact failure that makes
`misc/theatre-1939-45` score 10/10. It is not in the model.

1940 is the worst year (−1.70M). The gap is real: night surface attacks and
wolf-pack tactics changed what a boat could do in the autumn of 1940, and this
model has one flat productivity curve for the whole first half of the war.

## Why not fit it

The temptation is to raise 1940 output until the error disappears. That makes
the table look better and the model mean less: the error is a statement about
what a single ASW number cannot capture, and it is left visible.

## Usage

    python3 atlantic.py --seed 1939 --verbose   the war as a ledger
    python3 accuracy.py --seeds 20              yearly errors, crossing spread

15 self-tests. Standard library only.
