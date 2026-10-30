#!/usr/bin/env python3
"""board -- watch the Midway model turn by turn, where deck state is the picture.

At Midway the decisive information is what each carrier's deck is doing at a
given minute. A text log says "Akagi recovers its strike"; a board says the
same thing with a symbol that changes, and the pattern is what you remember.

    python3 board.py --seed 1942            animate
    python3 board.py --seed 1942 --frame 8  print one frame
    python3 board.py --self-test

Deck state glyphs, chosen so the state is readable without colour:

    A  armed      fuelled and armed on deck. A hit here is a disaster.
    ^  launching  aircraft going up.
    >  away       the strike is out; the deck is bare.
    v  recovering aircraft coming back. The worst place to be caught.
    -  empty      nothing on deck.
    X  sunk

Standard library only.
"""

import argparse
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import midway as M  # noqa: E402

GLYPH = {"ARMED": "A", "LAUNCHING": "^", "AWAY": ">",
          "RECOVERING": "v", "EMPTY": "-"}
COLOR = {"ARMED": "\033[38;5;203m", "LAUNCHING": "\033[38;5;214m",
          "AWAY": "\033[38;5;244m", "RECOVERING": "\033[38;5;221m",
          "EMPTY": "\033[38;5;240m"}
RESET, DIM, BOLD = "\033[0m", "\033[2m", "\033[1m"
WIDTH = 62


def cell(c: M.Carrier, colour: bool) -> str:
    """One carrier as a fixed-width cell: name, state glyph, hull bar."""
    if c.sunk:
        body = "X  sunk     "
    else:
        bar = "#" * max(c.hp, 0) + "." * max(M.HP - max(c.hp, 0), 0)
        body = f"{GLYPH.get(c.state, '?')}  {c.hp}/{M.HP} {bar:<6}"
    name = c.name[:10]
    if colour and not c.sunk:
        return f"{name:<11}{COLOR.get(c.state, '')}{body}{RESET}"
    return f"{name:<11}{body}"


def frame(b: M.Battle, colour: bool) -> str:
    stamp = M.hour_of(min(b.turn, M.TURNS - 1))
    head = f"{stamp}  turn {b.turn:>2}/{M.TURNS}"
    if colour:
        head = BOLD + head + RESET
    lines = [head, ""]
    lines.append("  JAPAN")
    for i in range(0, len(b.japan), 2):
        lines.append("    " + "  ".join(cell(c, colour) for c in b.japan[i:i + 2]))
    lines.append("  US")
    for i in range(0, len(b.us), 2):
        lines.append("    " + "  ".join(cell(c, colour) for c in b.us[i:i + 2]))
    lines.append("")
    legend = "  A armed  ^ launching  > away  v recovering  - empty  X sunk"
    lines.append(DIM + legend + RESET if colour else legend)
    return "\n".join(lines)


def frames(seed: int, upto: int | None = None) -> list[M.Battle]:
    """One board per turn, each showing the war as of that turn."""
    total = upto or M.TURNS
    original, out = M.TURNS, []
    try:
        for n in range(1, total + 1):
            M.TURNS = n
            b = M.Battle(seed)
            b.run()
            out.append(b)
    finally:
        M.TURNS = original
    return out


def play(seq: list[M.Battle], colour: bool, delay: float) -> None:
    if colour:
        sys.stdout.write("\033[?25l\033[2J")
    try:
        for b in seq:
            sys.stdout.write(("\033[H" if colour else "") + frame(b, colour) + "\n")
            sys.stdout.flush()
            time.sleep(delay)
    finally:
        if colour:
            sys.stdout.write("\033[?25h\033[0m")


def self_test() -> int:
    checks, failures = 0, []

    def check(cond, label):
        nonlocal checks
        checks += 1
        if not cond:
            failures.append(label)

    # every state the model can emit has a glyph and a colour
    for st in ("ARMED", "LAUNCHING", "AWAY", "RECOVERING", "EMPTY"):
        check(st in GLYPH, f"glyph for {st}")
        check(st in COLOR, f"colour for {st}")

    b = M.Battle(1942)
    plain = frame(b, False)
    check("\033[" not in plain, "no ANSI without colour")
    check("JAPAN" in plain and "US" in plain, "both fleets shown")
    check("Akagi" in plain, "carrier names shown")
    check("\033[" in frame(b, True), "ANSI with colour")

    # a sunk carrier renders as sunk, not as a hull bar
    b.japan[0].sunk = True
    check("sunk" in frame(b, False), "sunk carrier marked")

    check(len(frames(seed=1942, upto=10)) == 10, "one board per turn")

    for m in failures:
        print(f"  FAIL {m}")
    print(f"\n{checks} checks, {len(failures)} failed")
    return 0 if not failures else 1


def main(argv: list[str]) -> int:
    p = argparse.ArgumentParser(prog="board")
    p.add_argument("--seed", type=int, default=1942)
    p.add_argument("--frame", type=int, default=None)
    p.add_argument("--delay", type=float, default=0.25)
    p.add_argument("--no-color", action="store_true")
    p.add_argument("--self-test", action="store_true")
    a = p.parse_args(argv)

    if a.self_test:
        return self_test()

    colour = not a.no_color and sys.stdout.isatty()
    if a.frame is not None:
        M.TURNS = a.frame + 1
        b = M.Battle(a.seed)
        b.run()
        print(frame(b, False))
        return 0

    play(frames(a.seed), colour, a.delay)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
