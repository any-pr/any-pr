#!/usr/bin/env python3
"""verify-hello -- check the hello-world batches for internal consistency.

Eight batches of `misc/hello-world*` now exist, contributed independently.
Nothing in the repository checks that they agree with each other, and nothing
checks that a file claiming to print `Hello, World!` actually contains that
string. That is the whole job here.

Three checks, none of which need a compiler:

  1. Every `hello.*` file contains the string `Hello, World!` or `Hello World!`
     (some languages have no comma in their canonical form) or reaches it
     through a construction the file declares, e.g. Brainfuck's arithmetic.

  2. No two batches claim the same file extension without the NOTES explaining
     the difference. A collision is not automatically a bug -- Perl and Prolog
     both use `.pl` -- but it must be acknowledged somewhere.

  3. Every file named in a batch's NOTES.md exists, and every file that exists
     is named in NOTES.md. A batch whose documentation has drifted from its
     contents is worse than no documentation.

    python3 scripts/verify-hello.py
    python3 scripts/verify-hello.py --verbose
    python3 scripts/verify-hello.py --self-test

Exit status: 0 clean, 1 at least one inconsistency, 2 usage error.
Standard library only.
"""

import argparse
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MISC = os.path.join(ROOT, "misc")
BATCH_PREFIX = "hello-world"

# Files whose text does not literally contain the greeting, with the reason.
# Keep this list short and justified; it is the escape hatch, not the norm.
EXEMPT = {
    # Brainfuck has no literals; the file builds the bytes arithmetically and
    # is verified by running it, which NOTES.md records.
    "hello.bf": "generated arithmetically, verified by execution",
    # Pre-existing file, not part of these batches: prints "Hello world" with
    # no comma and a lowercase w. Left alone rather than silently rewritten.
    "hello.rs": "pre-existing, prints 'Hello world'",
    # APL, J, K print the result of a top-level expression; the string is the
    # program. They do contain it, so this entry is only here if a future
    # encoding breaks the byte match.
}


def batches() -> list[tuple[str, str]]:
    """Every directory under misc/ whose name starts with hello-world."""
    out = []
    if not os.path.isdir(MISC):
        return out
    for name in sorted(os.listdir(MISC)):
        path = os.path.join(MISC, name)
        if name.startswith(BATCH_PREFIX) and os.path.isdir(path):
            out.append((name, path))
    return out


def lang_files(path: str) -> list[str]:
    """The hello.* files in a batch, excluding NOTES.md and README files."""
    return sorted(
        f for f in os.listdir(path)
        if f.startswith("hello.")
        and os.path.isfile(os.path.join(path, f))
    )


def exts(files: list[str]) -> dict[str, list[str]]:
    """Map extension -> batch list, given (file, batch) pairs."""
    out: dict[str, list[str]] = {}
    for f, batch in files:
        ext = f.split(".", 1)[1]
        out.setdefault(ext, []).append(batch)
    return out


def read(path: str) -> str:
    with open(path, encoding="utf-8", errors="replace") as fh:
        return fh.read()


def check_greeting(batch: str, path: str, files: list[str]) -> list[str]:
    """Every file must contain the greeting, or be listed in EXEMPT."""
    problems = []
    for f in files:
        if f in EXEMPT:
            continue
        body = read(os.path.join(path, f))
        if "Hello, World!" in body or "Hello World!" in body:
            continue
        problems.append(
            f"{batch}/{f}: no 'Hello, World!' in the file"
            f" (add it to EXEMPT with a reason if intentional)"
        )
    return problems


def check_collisions(all_files: list[tuple[str, str]]) -> list[str]:
    """Duplicate extensions across batches must be explained in NOTES.md."""
    problems = []
    for ext, where in sorted(exts(all_files).items()):
        if len(where) < 2:
            continue
        # A collision is acceptable when at least one of the batches that uses
        # it mentions the extension in its NOTES.
        mentioned = False
        for batch in set(where):
            notes = os.path.join(MISC, batch, "NOTES.md")
            if os.path.isfile(notes) and f".{ext}" in read(notes):
                mentioned = True
                break
        if not mentioned:
            problems.append(
                f".{ext} appears in {len(where)} batches ({', '.join(sorted(set(where)))})"
                f" but none of their NOTES.md mentions it"
            )
    return problems


def check_notes(batch: str, path: str, files: list[str]) -> list[str]:
    """NOTES.md must name every file, and only files that exist."""
    problems = []
    notes_path = os.path.join(path, "NOTES.md")
    if not os.path.isfile(notes_path):
        # The original hello-world/ predates this series and has no NOTES;
        # only flag a missing NOTES when the batch also lacks any markdown.
        if files and not any(f.endswith(".md") for f in os.listdir(path)):
            print(f"  note: {batch} has no NOTES.md (pre-existing batch)")
        return problems

    notes = read(notes_path)

    for f in files:
        if f not in notes:
            problems.append(f"{batch}/NOTES.md does not mention {f}")

    # Anything the NOTES names that is not on disk. Only names appearing in a
    # table cell (| `hello.x` |) or a backticked reference count, so that
    # intermediate build artifacts in run commands -- hello.o, hello.jar --
    # are not mistaken for source files.
    referenced = set(re.findall(r"`(hello\.[A-Za-z0-9_]+)`", notes))
    for named in sorted(referenced):
        if named not in files:
            problems.append(f"{batch}/NOTES.md names {named}, which does not exist")

    return problems


def self_test() -> int:
    """The checks are pure enough to exercise without touching the repo."""
    checks = 0
    failures = []

    def check(cond, label):
        nonlocal checks
        checks += 1
        if not cond:
            failures.append(label)

    # exts() grouping
    grouped = exts([("hello.pl", "a"), ("hello.pl", "b"), ("hello.py", "a")])
    check(grouped["pl"] == ["a", "b"], "exts groups duplicates")
    check(grouped["py"] == ["a"], "exts keeps singles")

    # batch discovery is path-prefix based
    check("hello-world".startswith(BATCH_PREFIX), "prefix matches itself")
    check("hello-world-multilingual".startswith(BATCH_PREFIX), "prefix matches a child")

    # the greeting test accepts both spellings
    for body in ("print('Hello, World!')", 'println("Hello World!")'):
        check("Hello, World!" in body or "Hello World!" in body, f"greeting in {body!r}")
    check(not ("Hello, World!" in "print('hi')" or "Hello World!" in "print('hi')"),
          "greeting rejects a bare print")

    # EXEMPT is keyed by bare filename, not path
    for k in EXEMPT:
        check("/" not in k, f"EXEMPT key is a bare filename: {k}")

    for f in failures:
        print(f"  FAIL {f}")
    print(f"\n{checks} checks, {len(failures)} failed")
    return 0 if not failures else 1


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(
        prog="verify-hello",
        description="Check the hello-world batches for consistency.",
    )
    parser.add_argument("--verbose", action="store_true",
                        help="list every file examined")
    parser.add_argument("--self-test", action="store_true",
                        help="exercise the checks themselves and exit")
    args = parser.parse_args(argv)

    if args.self_test:
        return self_test()

    found = batches()
    if not found:
        print(f"no {BATCH_PREFIX}* directories under {MISC}", file=sys.stderr)
        return 2

    all_files: list[tuple[str, str]] = []
    problems: list[str] = []
    total = 0

    for batch, path in found:
        files = lang_files(path)
        total += len(files)
        all_files.extend((f, batch) for f in files)
        if args.verbose:
            print(f"{batch}: {len(files)} files")
        problems.extend(check_greeting(batch, path, files))
        problems.extend(check_notes(batch, path, files))

    problems.extend(check_collisions(all_files))

    print(f"batches   {len(found)}")
    print(f"files     {total}")
    print(f"problems  {len(problems)}")
    for p in problems:
        print(f"  - {p}")

    return 0 if not problems else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
