# warmap — the war as something you watch

`misc/theatre-1939-45` prints a table at the end of the war. A table cannot show
the order in which theatres flip, or whether the model runs early against the
record. This renders the same model as a map advancing one month per frame.

```
$ python3 misc/theatre-1939-45/warmap/warmap.py --seed 1939
1942-03  month 31/73

  Britain     France    Poland
  ++++++++++  ########  ##########

  Finland     Balkans   Barbarossa
  ##########  ##....    ++++++++++

  North Afri            Pacific
  ++++++++++            ~~~~~~~~~~

  manpower  █████████         94.8   150.0   ███████████
  morale    ████████████████   1.0     0.9    ██████████
```

## Why an animation

Three things a summary table cannot show, and a frame can:

1. **Order.** The axis sequence 1939-41 is a rush of theatres going dark in
   succession. On a table it is ten rows; on a map it is a wave.
2. **Momentum.** A theatre at contest +0.4 fills four cells of ten; at +0.9 it
   fills all ten. The fill length *is* the data.
3. **Deviation from the record.** The `decided:` line compares the model's
   clock with the historical one, so "Barbarossa 18mo early" is visible the
   moment the model decides it, rather than as a footnote in a report.

## Readable without colour

Shape carries the holder, fill carries momentum:

| glyph | holder |
|---|---|
| `#` | axis |
| `+` | allied |
| `~` | contested |
| `.` | neutral |

Colour repeats the same information for terminals that support it. `--no-color`
is not a degraded mode; it is the same map, because a map that only works in
colour does not work in a pipe or a log file.

## Not a map

The layout is hand-placed on a 48x8 canvas, roughly west-to-east and
north-to-south. It is a diagram whose axes carry meaning, not a projection.
Calling it a map of Europe would be a lie, and the file says so.

## Implementation

The renderer holds no rules. Each frame asks `theatre.simulate` for the war as
of month N by capping `MAX_MONTHS`, so if the model changes the map changes with
it and the two cannot drift. That is O(n²) over 73 months, which costs about a
tenth of a second — cheaper than duplicating the rules and getting them wrong.

Two bugs the animation exposed that the table had hidden:

- Poland stayed drawn through 1945. The visibility test was `months > 0`, but
  a theatre that fought for two months keeps that count forever. It has to be
  the window: `opens <= month <= closes`.
- Fill strings were repeated ten times (`fill * 10`), overflowing the cell and
  overprinting the neighbouring theatre.

Both were invisible in the summary output. That is the argument for the map.

## Usage

```
python3 misc/theatre-1939-45/warmap/warmap.py --seed 1939            animate
python3 misc/theatre-1939-45/warmap/warmap.py --render 30           print frame 30 and exit
python3 misc/theatre-1939-45/warmap/warmap.py --frames 24           first two years
python3 misc/theatre-1939-45/warmap/warmap.py --step                one frame per keypress
python3 misc/theatre-1939-45/warmap/warmap.py --no-color            plain text
python3 misc/theatre-1939-45/warmap/warmap.py --self-test           29 checks
```

Requires `misc/theatre-1939-45` beside it; both ship in this repository.
Standard library only.
