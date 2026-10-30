# kessel — a pocket dies of geometry, not of shellfire

Theatre models count casualties per month. Casualties do not take a pocket: as
men are killed the perimeter contracts, so the density of defenders per
kilometre barely moves. A Kessel is not worn down, it is **cut up** — and the
cut creates frontage the same men must now hold on two sides. That is why three
pockets of the same war lasted 105, 72 and 16 days.

```
pocket        days  record   front  airlift
Demyansk       109     105     159      87%
Stalingrad      71      72     113      22%
Korsun          18      16     116      34%
```

## The mechanism

A pocket's life is set by **men per kilometre of front** and by **how many
kilometres the attacker can force it to hold**.

| pocket | men/km | ratio | pressure | lasted |
|---|---|---|---|---|
| Demyansk | 628 | 1.15 | 0.008 | 109 d |
| Stalingrad | 2221 | 1.85 | 0.022 | 71 d |
| Korsun | 482 | 2.10 | 0.054 | 18 d |
Korsun's pressure is seven times Demyansk's from a ratio less than twice as bad:
thin density multiplies it. **Stalingrad had the most men per kilometre of all
three and still fell.** The pressure term puts the ratio over the square root of
density — the only thing here I would defend as a mechanism.

## A cut is a partition, not a process

`INTERIOR_FRACTION = 0.55`, `INTERIOR_DECAY = 0.55`, `MAX_PIECES = 3`.
Stalingrad was cut into two on day 37 against a record of 24, and the northern
factory district was a separate pocket until January. Two pieces, not a
cascade: once divided into fragments, further cutting has less to work with,
and pressure stops creating front and starts killing men.

## Hunger is not a body count

- **the ration roll is administrative**: set by the strength on the day the
  pocket closed. 250,000 men still drew rations for 250,000 when 40,000 were
  dead.
- **strength follows supply with a lag** (`HUNGER_LAG = 12` days). A unit at a
  third of its ration is not a third as strong on day one. That lag is why
  pockets hold out after the food is gone and then fold in a week.

## Airlift is a fraction of a supply line

Demyansk 87%, Stalingrad 22%, Korsun 34%. `SORTIES_PER_DAY = 0.55`, not the 2.0
a serviceable aircraft could manage in summer: of 500 promised at Stalingrad
about a third were flyable, and each flew less than one round trip a day in
winter. **The promise was never the delivery** — serviceability, not orders of
battle.

## What is fitted

`CUT_RATE = 0.025` is set so Demyansk comes out at 105 days: one fitted
parameter against three outcomes. Stalingrad then lands at 71 against 72, Korsun
at 18 against 16. Two predictions within two days is the strongest result here,
and worth being clear how weak that is: two points, one free parameter, three
similar cases. Agreement this good is not hard to get by accident.

What would make it convincing is a fourth pocket held out and never used to set
a constant — Kholm, or a Pacific encirclement. It is not here because I do not
have the perimeter and ration figures for one, and inventing them would destroy
the only thing this file is worth.
13 self-tests, standard library only.
13 self-tests. Standard library only.
