#!/usr/bin/env python3
"""warmap -- the 1939-45 war as something you watch, not something you read.

The theatre model prints a table at the end. A table cannot show the order in
which theatres flip, or how early the model runs against the record. This
renders the same model as a map advancing one month per frame.

    python3 warmap.py --seed 1939          animate
    python3 warmap.py --render 30         print frame 30 and exit
    python3 warmap.py --no-color          plain text, for pipes
    python3 warmap.py --self-test

Standard library only: ANSI escapes and a shared model. Runs over ssh.
"""

import argparse
import os
import shutil
import sys
import time

# Reuse the model rather than copying it. The repository's own module is the
# source of truth; if the simulation changes, the map changes with it.
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import theatre as T  # noqa: E402

# Rough west-to-east, north-to-south layout on a 48x8 canvas. Not a map of
# Europe: a diagram whose axes carry meaning. The layout is hand-placed.
PLACES = {
    "Britain":      ( 1,  0),
    "Normandy":     (13,  0),
    "France":       (25,  0),
    "Poland":       (37,  0),
    "Finland":      ( 1,  3),
    "Italy":        (13,  3),
    "Balkans":      (25,  3),
    "Barbarossa":   (37,  3),
    "North Africa": ( 1,  6),
    "Pacific":      (37,  6),
}

WIDTH, HEIGHT = 48, 8

# glyph and colour per holder, in one table so they cannot drift apart
STYLE = {
    "axis":      ("#", "\033[38;5;203m"),   # red
    "allied":    ("+", "\033[38;5;75m"),    # blue
    "neutral":   (".", "\033[38;5;244m"),   # grey
    "contested": ("~", "\033[38;5;221m"),   # amber
}
RESET = "\033[0m"
DIM = "\033[2m"
BOLD = "\033[1m"


def density(contest: float, control: str) -> str:
    """Fill string for a theatre row.

    Shape carries the holder, density carries momentum. That matters because
    colour is unavailable in a pipe, a log file, or a monochrome terminal, and
    a map that only works in colour is a map that does not work.
    """
    glyph = STYLE.get(control, ("?", ""))[0]
    strength = abs(contest)
    return (glyph * 10 if strength >= 0.75 else
            glyph * 6 + "." * 4 if strength >= 0.35 else glyph * 2 + "." * 8)


def canvas() -> list[list[str]]:
    return [[" "] * WIDTH for _ in range(HEIGHT)]


def stamp(c: list[list[str]], x: int, y: int, text: str) -> None:
    """Write text at (x, y), clipping to the canvas."""
    if not (0 <= y < HEIGHT):
        return
    for i, ch in enumerate(text):
        if 0 <= x + i < WIDTH:
            c[y][x + i] = ch


def draw(frame: dict, use_color: bool) -> str:
    """Render one month. Returns the frame as a string, cursor home included."""
    c = canvas()
    states = frame["states"]

    # Theatre boxes: a three-row cell whose fill shows who holds the ground.
    # The fill is the interesting part -- a sector at contest +0.4 is half
    # axis, and that gradient is what a single glyph cannot show.
    for name, (x, y) in PLACES.items():
        st = states.get(name)
        if st is None:
            continue
        control, contest = st
        tint = control
        if abs(contest) > 0.35:
            tint = "axis" if contest > 0 else "allied"
        fill = density(contest, control)
        label = name[:10]
        if use_color:
            top = f"{STYLE.get(tint, ('', ''))[1]}{fill:<10}{RESET}"
        else:
            top = f"{fill:<10}"
        stamp(c, x, y, f"{label:<10}")
        stamp(c, x, y + 1, top)

    bar = resource_bar(frame, use_color)
    head = f"{frame['month']}  month {frame['index'] + 1:>2}/73"
    if use_color:
        head = f"{BOLD}{frame['month']}{RESET}" + head[len(frame['month']):]

    body = ["".join(row).rstrip() for row in c]
    parts = [head, *body, "", bar, legend(use_color), deviation(frame, use_color)]
    if frame.get("note"):
        parts.append(frame["note"])
    return ("\033[H" if use_color else "") + "\n".join(parts)


def resource_bar(f: dict, use_color: bool) -> str:
    """Two-sided bars: manpower and morale, axis left, allied right."""
    def row(label, a, b, scale, unit=""):
        aw = max(0, min(20, round(20 * a / scale)))
        bw = max(0, min(20, round(20 * b / scale)))
        left = "\u2588" * aw
        right = "\u2588" * bw
        if use_color:
            left = f"{STYLE['axis'][1]}{left}{RESET}"
            right = f"{STYLE['allied'][1]}{right}{RESET}"
        return (f"  {label:<9} {left:<20}{a:>6.1f}{unit} "
                f"{b:>7.1f}{unit}{right:>20}")

    return "\n".join([
        row("manpower", f["axis_manpower"], f["allied_manpower"], 200.0),
        row("morale", f["axis_morale"], f["allied_morale"], 1.2)])


# When each theatre was historically decided. The map shows whether the model
# runs early, late, or on time -- which a final table cannot.
DECIDED = {
    "Poland": "1939-10", "Finland": "1940-03", "France": "1940-06",
    "Britain": "1941-05", "North Africa": "1943-05", "Balkans": "1941-05",
    "Barbarossa": "1944-12", "Pacific": "1945-08", "Italy": "1945-04",
    "Normandy": "1944-08",
}


def _months_between(a: str, b: str) -> int:
    """b - a in months."""
    return ((int(b[:4]) - int(a[:4])) * 12) + (int(b[5:7]) - int(a[5:7]))


def deviation(frame: dict, use_color: bool) -> str:
    """Compare the model's clock with the record's, for decided theatres."""
    month, parts = frame["month"], []
    for name, decided in DECIDED.items():
        st = frame["states"].get(name)
        if st is None or st[0] in ("contested", "neutral"):
            continue
        d = _months_between(decided, month)
        parts.append(f"{name} on time" if d == 0 else
                     f"{name} {d:+d}mo late" if d > 0 else f"{name} {-d}mo early")
    if not parts:
        return ""
    text = "  decided: " + "; ".join(parts[:3])
    return DIM + text + RESET if use_color else text


def legend(use_color: bool) -> str:
    items = [("# axis", "axis"), ("+ allied", "allied"), ("~ contested", "contested"),
             (". neutral", "neutral")]
    if not use_color:
        return "  " + "   ".join(t for t, _ in items)
    return "  " + "   ".join(f"{STYLE[k][1]}{t}{RESET}" for t, k in items)


def frames(seed: int) -> list[dict]:
    """Run the model once per month by capping MAX_MONTHS.

    Re-running from scratch each month is O(n^2) but keeps this file a pure
    renderer: no rule is duplicated here, so the map cannot drift from the
    model. At 73 months and a millisecond per run, that trade is free.
    """
    original, out = T.MAX_MONTHS, []
    by_name = {th[0]: (th[1], th[2]) for th in T.THEATRES}
    try:
        for upto in range(1, 74):
            T.MAX_MONTHS = upto
            r = T.simulate(seed, verbose=True)
            tl = r["timeline"][-1]
            month = tl["month"]
            # A theatre appears only inside its own window. Using `months > 0`
            # kept Poland on the map through 1945 -- it fought two months.
            shown = {n for n in PLACES
                     if n in by_name and by_name[n][0] <= month <= by_name[n][1]}
            states = {t["name"]: (t["control"], t["contest"])
                      for t in r["theatres"] if t["name"] in shown}
            note = next((("  " + ln) for ln in r["log"][-3:]
                         if ln.startswith(month)), "")
            out.append({"index": upto - 1, "month": month, "states": states,
                        "axis_manpower": tl["axis_manpower"],
                        "allied_manpower": tl["allied_manpower"],
                        "axis_morale": tl["axis_morale"],
                        "allied_morale": tl["allied_morale"],
                        "note": note})
    finally:
        T.MAX_MONTHS = original
    return out


def play(seq: list[dict], use_color: bool, delay: float, step: bool) -> None:
    """Animate. In step mode, wait for a keypress each frame."""
    if use_color:
        sys.stdout.write("\033[?25l\033[2J")   # hide cursor, clear once
    try:
        for f in seq:
            sys.stdout.write(draw(f, use_color) + "\n")
            sys.stdout.flush()
            if step:
                try:
                    input()
                except EOFError:
                    return
            else:
                time.sleep(delay)
    finally:
        if use_color:
            sys.stdout.write("\033[?25h\033[0m")


def self_test() -> int:
    checks, failures = 0, []

    def check(cond, label):
        nonlocal checks
        checks += 1
        if not cond:
            failures.append(label)

    c = canvas()
    check(len(c) == HEIGHT and len(c[0]) == WIDTH, "canvas size")
    stamp(c, WIDTH - 1, 0, "abc")
    check("a" in "".join(c[0]), "stamp clips at the edge")
    for k in ("axis", "allied", "neutral", "contested"):
        check(k in STYLE and len(STYLE[k]) == 2, f"style for {k}")
    for name, (x, y) in PLACES.items():
        check(0 <= x and x + 10 <= WIDTH, f"{name} fits width")
        check(0 <= y and y + 2 <= HEIGHT, f"{name} fits height")
    f = {"index": 0, "month": "1939-09", "states": {"Poland": ("axis", 0.4)},
         "axis_manpower": 45.0, "allied_manpower": 60.0,
         "axis_morale": 1.0, "allied_morale": 1.0, "note": ""}
    plain = draw(f, False)
    check("\033[" not in plain, "no ANSI when colour is off")
    check("1939-09" in plain and "Poland" in plain, "frame shows month+theatre")
    check("\033[" in draw(f, True), "ANSI present when colour is on")
    for m in failures:
        print(f"  FAIL {m}")
    print(f"\n{checks} checks, {len(failures)} failed")
    return 0 if not failures else 1


def main(argv: list[str]) -> int:
    p = argparse.ArgumentParser(prog="warmap",
                                description="Animate the 1939-45 theatre model.")
    p.add_argument("--seed", type=int, default=1939)
    p.add_argument("--frames", type=int, default=0, help="how many months to show")
    p.add_argument("--render", type=int, default=None, metavar="N",
                   help="print frame N and exit")
    p.add_argument("--step", action="store_true", help="one frame per keypress")
    p.add_argument("--delay", type=float, default=0.12)
    p.add_argument("--no-color", action="store_true")
    p.add_argument("--self-test", action="store_true")
    args = p.parse_args(argv)

    if args.self_test:
        return self_test()

    use_color = not args.no_color and sys.stdout.isatty()

    seq = frames(args.seed)
    if args.render is not None:
        if not 0 <= args.render < len(seq):
            print(f"frame {args.render} out of range 0..{len(seq)-1}",
                  file=sys.stderr)
            return 2
        print(draw(seq[args.render], False))
        return 0
    play(seq[: args.frames] if args.frames else seq,
         use_color, args.delay, args.step)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
