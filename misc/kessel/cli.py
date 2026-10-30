"""cli -- the command line for kessel."""

import argparse
import sys

import kessel as K
from pockets import POCKETS


def main(argv: list[str]) -> int:
    ap = argparse.ArgumentParser(
        prog="kessel", description="Encircled pockets as a problem of frontage.")
    ap.add_argument("--pocket", default=None, choices=sorted(POCKETS) or None)
    ap.add_argument("--verbose", action="store_true")
    ap.add_argument("--self-test", action="store_true")
    a = ap.parse_args(argv)
    if a.self_test:
        return K.self_test()

    results = [K.run(n) for n in ([a.pocket] if a.pocket else POCKETS)]
    print(f"{'pocket':12s} {'days':>5s} {'record':>7s} {'front':>7s} {'airlift':>8s}")
    for r in results:
        print(f"{r['pocket']:12s} {r['days']:>5d} {r['record_days']:>7d} "
              f"{r['perimeter_km']:>7.0f} {r['covered_pct']:>7.0f}%")
    if a.verbose:
        for r in results:
            print(f"\n  {r['pocket']}: {r['note']}")
            for day, line in r["log"][:8]:
                print(f"    day {day:>3d}  {line}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
