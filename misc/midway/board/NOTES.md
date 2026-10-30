# board — the battle as deck states, not numbers

`misc/midway` models 4 June 1942 as 26 turns of thirty minutes. Its log line
`Akagi recovers its strike` is accurate and forgettable. This is the same turn
rendered so the deck cycle is visible, because the deck cycle *is* the model.

```
$ python3 board.py --frame 8

08:30  turn  9/26

  JAPAN
    Akagi      X  sunk       Kaga       v  1/6 #.....
    Soryu      v  6/6 ######  Hiryu      v  6/6 ######
  US
    Enterprise v  6/6 ######  Hornet     v  6/6 ######
    Yorktown   v  6/6 ######

  A armed  ^ launching  > away  v recovering  - empty  X sunk
```

## Glyphs

| state | glyph | meaning |
|---|---|---|
| ARMED | `A` | fuelled and armed on deck — a hit here is a disaster |
| LAUNCHING | `^` | aircraft going up |
| AWAY | `>` | strike is out; the deck is bare |
| RECOVERING | `v` | aircraft coming back — the worst place to be caught |
| EMPTY | `-` | nothing on deck |
| sunk | `X` | gone |

The hull bar (`######` to `......`) is `hp/6`, so damage is a length rather than
a number. Colour repeats the same distinction; `--no-color` gives the identical
board, because a state display that only works in colour does not work in a log.

## Watching the cycle

```
05:00  turn  1   Akagi ^   launching
05:30  turn  2   Akagi >   away
06:00  turn  3   Akagi >   away
06:30  turn  4   Akagi v   recovering
```

Four frames from `^` to `v` is the round trip. The historical disaster is what
happens when the fourth frame coincides with an enemy strike arriving — which is
why `RECOVERING` has the highest hit probability in the model.

## What it showed me

The animation made one thing obvious that the log hid: **all seven carriers
cycle in lockstep**. They launch on the same turn, are away on the same turn,
and recover on the same turn, because nothing in the model staggers them.
Real carriers were staggered by fuel state, deck damage, and the fact that they
were launched at different times of the morning.

That is a model defect this display exposed, and it is why `accuracy.py` in
`misc/midway` reports a near-binary outcome distribution: lockstep decks mean
the first side to resolve wipes out the other.

## Usage

```
python3 board.py --seed 1942            animate
python3 board.py --frame 8              print one frame
python3 board.py --no-color             plain text
python3 board.py --self-test            16 checks
```

Requires `misc/midway` on the path; both ship here. Standard library only.
