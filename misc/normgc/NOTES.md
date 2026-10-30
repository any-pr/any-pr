# normgc and bytecap

Two small read-only utilities that report numbers the Regulations do not
currently consult. Neither is a gate, neither changes a verdict, and neither
is imported by anything. Where they disagree with `.github/workflows/auto-merge.yml`,
the Workflow is correct and these are wrong (Article VI, Section 4).

## normgc — the name a reader sees

Every name-based gate is a regular expression over the bytes of a path:
`PROTECTED_REGEX`, `DOCS_REGEX`, `PROHIBITED_REGEX`, `SECRET_REGEX`. A reader
does not see bytes. Two strings can render identically and compare unequal:

| name | code point | `unicodedata.normalize("NFKC", ...)` |
|---|---|---|
| `R<U+0415>ADME.md` | CYRILLIC CAPITAL LETTER IE | **unchanged** |
| `<U+FF32>EADME.md` | FULLWIDTH LATIN CAPITAL LETTER R | `README.md` |
| `README<U+2024>md` | ONE DOT LEADER | `README.md` |

NFKC folds *compatibility* characters — alternate renderings of one base
character, which is what fullwidth forms and `ONE DOT LEADER` are. It does not
fold Cyrillic IE, because Cyrillic IE is not a variant of Latin E; it is a
different letter in a different script that happens to look the same.

That distinction is the whole point of `normgc`. `compat()` exposes NFKC
alone, so the limitation is visible; `skeleton()` adds a curated cross-script
confusables map on top.

```
$ python3 misc/normgc/normgc.py --verdict "R<U+0415>ADME.md"
input       R<U+0415>ADME.md
nfkc        R<U+0415>ADME.md
skeleton    README.md
ascii-clean no
verdict     protected document (readme)
```

The table covers Cyrillic and Greek, plus a few Latin Extended letters — what
turns up in practice, not the full UTS #39 space. `--self-test` pins 25 cases.

## bytecap — the number line counts miss

Article IV caps 20 files, 500 counted lines, and 300 lines per file. Section 4
exempts generated and vendored content from the line counts, which is sound: a
lockfile's line count says nothing about review effort. The exemption leaves
the *other* dimension unmeasured. A plan of nineteen ordinary files plus one
generated file passes every gate regardless of that file's size.

`bytecap` reports bytes. Same plan shape `gate-sim` accepts, resolved against
a git tree when one is available.

```
$ python3 misc/bytecap/bytecap.py --base origin/main
  OVER  Cargo.lock                  4.8 MiB
        misc/x/tool.py             12.1 KiB

  files      2
  total      4.8 MiB
  per-file    ceiling 1.0 MiB  -- 1 over
  per-change  ceiling 5.0 MiB  -- ok
```

Default ceilings (1 MiB per file, 5 MiB per change) sit above any hand-written
source file here and below a runaway artifact. Arguments, not constants.

## What this is not

An argument that either gate should change: changing `auto-merge.yml` needs a
PR touching `.github/`, which Article III(a) closes by design. These tools only
make the numbers visible.

For a pre-flight verdict use `tools/gate-sim/gate_sim.py` or
`tools/regulation-lint.py`; they mirror the Workflow, these do not.
