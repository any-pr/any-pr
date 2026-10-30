# bombing — a campaign that won and lost at the same time

Every model here so far describes a war being decided. This one describes a
campaign that was neither won nor lost, and the reason is one number.

**Aircraft production peaked in September 1944 at about 3,000 airframes. In
that same month synthetic oil output had fallen from 180,000 tons a month to
under 30,000.** The factories won. The fuel lost.

```
$ python3 bombing.py
seed 1943  60 months

  output retained      60.1% of what was possible
  airframe peak        1.55k in 1944-09
  fuel at that peak    47% of what was needed
  unable to fly from   1944-09
  oil damage, final    93%
  industry damage      11% (dispersed)
```

## The asymmetry

This is the model's whole content, and it is not doctrinal — it is what the
returns show.

| target | what a hit does | why |
|---|---|---|
| **oil** | permanent | a hydrogenation plant is a chemical works: hit it and the hydrogen, catalyst and pressure vessels are gone |
| **industry** | recovers | machine tools were dug out of the rubble and re-erected elsewhere. Dispersal cost output, but preserved capacity |

`DISPERSAL_HALFLIFE = 7.0` months; `OIL_DECAY` is effectively permanent. That
one asymmetry is why the campaign looks ineffective for four years and
decisive in the fifth:

```
1944-06  tons  60.0  fuel 136.3  fly 100%   airframes 1.30k
1944-08  tons  80.0  fuel  87.7  fly  67%   airframes 1.55k
1944-10  tons  70.0  fuel  44.9  fly  32%   airframes 1.50k
1944-12  tons  60.0  fuel  12.9  fly   9%   airframes 1.50k
```

**Airframe output never falls. It rises in the same months the fuel
disappears.** That is the campaign.

## Doctrine is the other variable

`DOCTRINE` is the two-year argument inside the Allied camp. Until 1943 the RAF
bombed cities at night because that was all it could hit. The model charges for
it: urban bombing costs production only through absenteeism and transport, and
it recovers.

| month | urban | industry | oil | transport |
|---|---|---|---|---|
| 1940-05 | 0.95 | 0.05 | — | — |
| 1943-01 | 0.55 | 0.45 | — | — |
| 1944-01 | 0.20 | 0.35 | **0.45** | — |
| 1944-05 | 0.05 | 0.15 | **0.70** | 0.10 |
Precision is why the doctrine changed, and it is not a separate story:
`PRECISION` runs from 0.020 in 1940 to 0.230 in 1945. Area bombing was not a
moral failure a better doctrine fixed; it was what a 1940 bombsight could do.

## The counterfactual, and why it is the weak part

The model computes what Speer's rationalisation would have delivered with no
bombs — 2.9% monthly compounding — and reports the gap: **40% prevented**.

The honest range in the economic histories is 10–30%, mostly in the last eight
months. The model says 40% and is wrong, because **it assumes the bombed economy
was already performing at the rate it would have reached unbombed.** That is
circular, and the counterfactual has no ceiling: it compounds for five years as
though labour and materials were unlimited.

`accuracy.py` says so in its output. A number that looks like a result and is
actually an assumption is the thing most likely to be quoted.

## What is actually established

The oil result. It needs no counterfactual: damage to a chemical works is
damage to a chemical works, and the fuel curve is measured.

## Usage

    python3 bombing.py --table       month by month
    python3 bombing.py --seed 1943 -v
    python3 accuracy.py --seeds 20
19 self-tests. Standard library only.
