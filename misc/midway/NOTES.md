# midway — 4 June 1942, where minutes decide everything

Two models in this repository already fail at Midway, for opposite reasons:

- `misc/blitz-1940` has a front line. Midway has none.
- `misc/theatre-1939-45` has monthly attrition. Midway lasted a day.

Midway turned on three things neither can express: **who detects whom first**,
**what state a carrier's deck is in**, and **how long a strike package takes to
come round again**.

| file | what it is |
|---|---|
| `midway.py` | the battle, 26 turns of 30 minutes from 04:30 |
| `accuracy.py` | what it gets right, and the flaw it cannot fix |

```
$ python3 midway.py --seed 1942
seed 1942  turns 26

  japan_sunk     Akagi, Kaga, Soryu, Hiryu
  japan_alive    none
  us_sunk        none
  us_alive       Enterprise, Hornet, Yorktown
```

## Deck state is the model

| state | glyph | meaning |
|---|---|---|
| ARMED | `A` | fuelled and armed on deck — a hit here is a disaster |
| LAUNCHING | `^` | aircraft going up |
| AWAY | `>` | strike is out; the deck is bare |
| RECOVERING | `v` | aircraft coming back — the worst place to be caught |
| EMPTY | `-` | nothing on deck |

Hit probability depends on this state, not on aircraft counts:
`RECOVERING 0.065 > ARMED 0.055 > LAUNCHING 0.030 > EMPTY 0.014`. That is the
whole thesis — a carrier is killed by what is on its deck at the moment it is
found, and three of the four Japanese carriers died that way within six minutes
of each other.

## Nagumo's dilemma, made expensive

The model requires Japan to fly the Midway strike and recover it before a ship
strike can launch. That sortie is why the decks were full when the US fleet was
found. It is not a scripted event; it falls out of the deck cycle.

## The flaw

```
$ python3 accuracy.py
     japan sunk  us sunk  count
              4        0    145
              0        3     46
              0        2      3
              4        1      2  <- historical
              ...

    the historical outcome occurs in 2/200 runs
```

**73% of runs end with four Japanese carriers sunk and none American; 23% end
the other way round.** There is almost no middle.

Cause: both fleets detect each other within one turn, both launch on the same
turn, and whoever resolves first sinks enough of the other that it cannot
reply. The battle becomes a coin flip on detection order.

That is not what happened. Japan struck first and still lost, because three US
squadrons arrived at 10:25 while the Japanese decks were full of returning
aircraft. The historical outcome depends on a **coincidence of timing inside a
ten-minute window**, and this model resolves whole strikes on 30-minute
boundaries — so that window cannot exist.

## What would fix it

Five-minute turns, plus cloud cover and combat-air-patrol altitude as state.
The historical attack succeeded partly because the CAP had been drawn down to
low altitude. Six times the turns, and two new variables.

## The honest summary

This model encodes **why carriers die**. It fails at **why this particular
battle happened**. The first is a mechanism; the second is history, and history
is mostly timing.

## Usage

```
python3 midway.py --seed 1942 --verbose     the battle as a log
python3 accuracy.py --seeds 500             the outcome distribution
```

9 self-tests. Standard library only.
