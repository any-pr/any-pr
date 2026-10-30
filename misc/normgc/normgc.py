#!/usr/bin/env python3
"""normgc — reduce a filename to the ASCII skeleton a human reads.

Every name-based gate in the Workflow (`PROTECTED_REGEX`, `DOCS_REGEX`,
`PROHIBITED_REGEX`, `SECRET_REGEX`) is a regular expression over the bytes of
a path. A reader does not see bytes; they see glyphs. Two strings can render
identically while comparing unequal:

    R<U+0415>ADME.md    Cyrillic capital IE -- NOT folded by NFKC
    <U+FF32>EADME.md    fullwidth LATIN CAPITAL R -- folded by NFKC
    README<U+2024>md    ONE DOT LEADER -- folded by NFKC
    README.md           baseline

`unicodedata.normalize("NFKC", ...)` handles the second and third rows: those
are *compatibility* characters, defined as alternate renderings of a base
character. It does NOT handle the first row. Cyrillic IE is not a
compatibility variant of Latin E; it is a different letter in a different
script that happens to look the same. NFKC is therefore necessary but not
sufficient, which is the point this tool exists to make.

So `skeleton()` does two things:

1. NFKC-fold, which catches width, ligature, and punctuation lookalikes.
2. Map a curated set of cross-script confusables (Cyrillic, Greek, and a few
   Latin Extended letters) down to ASCII.

The curated map is deliberately small and reviewable. It covers the
characters that actually appear in practice, not all of Unicode's confusable
space -- see UTS #39 for the full treatment. A name this tool reports as
ASCII-clean is a name the gates can test reliably.

    python3 normgc.py README.md
    python3 normgc.py --verdict "R<U+0415>ADME.md"
    python3 normgc.py --self-test

Standard library only. Exit status: 0 name is ASCII-clean, 1 it is not (or a
verdict flipped), 2 usage error.
"""

import argparse
import sys
import unicodedata

# Cross-script confusables that NFKC leaves alone. Keys are the lookalike,
# values the ASCII character a reader perceives. Grouped by script so the
# table stays reviewable; extend it when a new case shows up, not
# speculatively.
CONFUSABLES = {
    # Cyrillic capitals that render as Latin capitals
    "\u0410": "A", "\u0412": "B", "\u0415": "E", "\u041a": "K", "\u041c": "M",
    "\u041d": "H", "\u041e": "O", "\u0420": "P", "\u0421": "C", "\u0422": "T",
    "\u0423": "Y", "\u0425": "X", "\u0405": "S", "\u0406": "I", "\u0408": "J",
    # Cyrillic lowercase
    "\u0430": "a", "\u0435": "e", "\u043e": "o", "\u0440": "p", "\u0441": "c",
    "\u0443": "y", "\u0445": "x", "\u0455": "s", "\u0456": "i", "\u0458": "j",
    # Greek capitals
    "\u0391": "A", "\u0392": "B", "\u0395": "E", "\u0396": "Z", "\u0397": "H",
    "\u0399": "I", "\u039a": "K", "\u039c": "M", "\u039d": "N", "\u039f": "O",
    "\u03a1": "P", "\u03a4": "T", "\u03a5": "Y", "\u03a7": "X",
    # Greek lowercase
    "\u03b1": "a", "\u03bf": "o", "\u03c1": "p", "\u03bd": "v", "\u03b9": "i",
    "\u03ba": "k", "\u03c5": "u", "\u03c7": "x",
    # Latin Extended that reads as plain ASCII
    "\u0130": "I", "\u0131": "i", "\u0142": "l", "\u00f8": "o", "\u0111": "d",
    # Digits and punctuation lookalikes
    "\u0417": "3", "\u04bb": "h",
}

PROTECTED_PREFIXES = (".github",)
PROTECTED_DOCS = ("readme", "license", "licence", "copying", "copyright")


def compat(name: str) -> str:
    """NFKC only. Catches width, ligature, and punctuation lookalikes."""
    return unicodedata.normalize("NFKC", name)


def skeleton(name: str) -> str:
    """NFKC, then map cross-script confusables to ASCII.

    Idempotent: skeleton(skeleton(x)) == skeleton(x).
    """
    return "".join(CONFUSABLES.get(ch, ch) for ch in compat(name))


def describe(name: str) -> str:
    """Render a name with non-ASCII code points spelled out."""
    return "".join(ch if 32 <= ord(ch) < 127 else f"<U+{ord(ch):04X}>"
                   for ch in name)


def is_ascii_clean(name: str) -> bool:
    """True when the name needs no folding -- every byte is plain ASCII."""
    return name.isascii()


def looks_protected(skel: str) -> str | None:
    """Best-effort label for why a folded path would be refused.

    Coarse on purpose: reports what a reader would assume, not what the
    Workflow decides. The Workflow remains the sole authority
    (Article VI, Section 4).
    """
    low = skel.lower().lstrip("./")
    head = low.split("/", 1)[0]
    if head in PROTECTED_PREFIXES or low.startswith(".github/"):
        return "protected path (.github)"
    base = low.rsplit("/", 1)[-1]
    stem = base.split(".", 1)[0]
    for doc in PROTECTED_DOCS:
        if stem == doc or stem.startswith((doc + "-", doc + "_")):
            return f"protected document ({doc})"
    return None


def self_test() -> int:
    """Cases are real bypasses plus negative controls. All must hold."""
    checks = 0
    failures = []

    def check(cond, label):
        nonlocal checks
        checks += 1
        if not cond:
            failures.append(label)

    # NFKC alone does NOT fold Cyrillic -- this is the documented limitation.
    check(compat("R\u0415ADME.md") != "README.md", "NFKC must not fold Cyrillic")
    check(skeleton("R\u0415ADME.md") == "README.md", "skeleton must fold Cyrillic")
    check(skeleton("\u0415") == "E", "Cyrillic IE -> E")

    # NFKC DOES fold compatibility characters; skeleton inherits that.
    check(compat("\uFF32EADME.md") == "README.md", "NFKC folds fullwidth R")
    check(skeleton("\uFF32EADME.md") == "README.md", "skeleton folds fullwidth R")
    check(skeleton("README\u2024md") == "README.md", "ONE DOT LEADER -> .")
    check(skeleton("\uFF2CICENSE") == "LICENSE", "fullwidth capital L -> L")
    check(skeleton("\uFF4CICENSE") == "lICENSE", "fullwidth small l -> l")

    # Greek and Latin Extended coverage.
    check(skeleton("\u0391PPLE") == "APPLE", "Greek Alpha -> A")
    check(skeleton("L\u0130CENSE") == "LICENSE", "dotted I -> I")

    # Idempotence and ASCII passthrough.
    for raw in ("R\u0415ADME.md", "\uFF32EADME.md", "\u0391PPLE", "README.md"):
        check(skeleton(skeleton(raw)) == skeleton(raw), f"idempotent: {describe(raw)}")
    for plain in ("README.md", "src/main.rs", "misc/x.py", "docs/A-B_c.1.md"):
        check(skeleton(plain) == plain, f"ASCII unchanged: {plain}")
        check(is_ascii_clean(plain), f"ASCII clean: {plain}")

    # The bypass must be visible as such.
    check(not is_ascii_clean("R\u0415ADME.md"), "homoglyph is not ASCII-clean")
    check(looks_protected(skeleton("R\u0415ADME.md")) is not None,
          "folded homoglyph README is recognized as protected")
    check(looks_protected(skeleton("misc/normgc/normgc.py")) is None,
          "ordinary path is not protected")

    for f in failures:
        print(f"  FAIL {f}")
    print(f"\n{checks} checks, {len(failures)} failed")
    return 0 if not failures else 1


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(
        prog="normgc",
        description="Reduce a path to the ASCII skeleton a reader perceives, "
                    "so name-based gates can test it reliably.",
    )
    parser.add_argument("path", nargs="?", help="one path to inspect")
    parser.add_argument("--verdict", action="store_true",
                        help="also report whether the skeleton looks protected")
    parser.add_argument("--self-test", action="store_true",
                        help="run the built-in cases and exit")
    args = parser.parse_args(argv)

    if args.self_test:
        return self_test()
    if not args.path:
        parser.error("give a path, or --self-test")

    raw = args.path
    folded = compat(raw)
    skel = skeleton(raw)

    print(f"input       {describe(raw)}")
    print(f"nfkc        {describe(folded)}")
    print(f"skeleton    {describe(skel)}")
    print(f"ascii-clean {'yes' if is_ascii_clean(raw) else 'no'}")
    if args.verdict:
        label = looks_protected(skel)
        print(f"verdict     {label or 'not a protected name after folding'}")

    return 0 if is_ascii_clean(raw) else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
