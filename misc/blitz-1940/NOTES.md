# blitz — the 1940 Western campaign as a runnable model

Three files, standard library only, no dependencies:

| file | what it is |
|---|---|
| `models.py` | the front, the formations, the constants |
| `blitz.py` | the turn loop: resupply, combat, rout, advance |
| `accuracy.py` | what it gets right about 1940, and what it does not |

```
$ python3 blitz.py --seed 1940
seed 1940  turns 46  ended 1940-06-24

$ python3 accuracy.py
1940 WESTERN CAMPAIGN -- model vs record
  model ended               1940-06-24
  historical end            1940-06-25 (46 days)
```

## The model

Five sectors from the Channel to the Maginot terminus. Each side has five
formations with personnel, armour, supply, and cohesion. Each turn:

1. supply regenerates from a rear depot, attenuating with distance
2. each formation attacks one opponent it faces
3. losses scale with force ratio and terrain
4. formations below a cohesion floor rout, retreating one sector
5. routed formations can rally if they still have men

**Each defender is engaged at most once per turn.** Without this cap every
German formation in a sector piles onto the same French one and the ratio
reaches 11:1, which no line survives.

## Calibration

Parameters were tuned against the historical duration, not fitted to it. Over
twenty seeds:

| seeds reaching 46 days | median | shortest |
|---|---|---|---|
| 15 of 20 | 46 | 15 |

The five that end early collapse between days 15 and 19 — a rout the allied
side cannot rally from. The historical outcome was not the only one these
rules admit.

## What it cannot represent

`accuracy.py` prints this, and it is the reason the file exists:

- **One-dimensional front.** No envelopment, no sea flank, no Dunkirk. The
  model cannot represent the actual operational decision of the campaign.
- **No air power.** The Luftwaffe is folded into the German doctrine
  multiplier, making it a constant. It cannot be shown to matter.
- **No logistics ceiling.** Real armoured advances outran supply; here supply
  regenerates on a schedule.
- **No political layer.** The 24 May halt order, the French command crisis of
  19 May, the Belgian capitulation of 28 May — all absent, all decisive.

The model is a logistics-and-attrition toy. It reproduces the shape of a
campaign, not the campaign. `accuracy.py` is the part that makes it worth
running: a model nobody checks is a story with numbers attached.

## Determinism

Seed in, identical campaign out. `--self-test` runs 11 checks, including
"same seed, same result", so the replay claim is tested rather than asserted.
