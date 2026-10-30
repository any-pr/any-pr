# Hello, World! in fifteen math, array, and desktop languages

An eighth batch, alongside `misc/hello-world/` and the seven
`hello-world-multilingual*` / `hello-world-more-languages*` /
`hello-world-asm-and-shells` directories (#6774, #6776–#6781).

This one is weighted toward languages whose primary purpose is not systems
programming: three array languages, six computer-algebra systems, and a few
desktop scripting environments.

## Array and mathematical languages

| file | language | run |
|---|---|---|
| `hello.apl` | GNU APL | `apl hello.apl` |
| `hello.j` | J | `j hello.j` |
| `hello.k` | K (ngn/k) | `k hello.k` |
| `hello.octave` | GNU Octave | `octave hello.octave` |
| `hello.scilab` | Scilab | `scilab -f hello.scilab` |
| `hello.maxima` | Maxima | `maxima -b hello.maxima` |
| `hello.sage` | SageMath | `sage hello.sage` |
| `hello.pari` | PARI/GP | `gp hello.pari` |
| `hello.bc` | GNU bc | `bc hello.bc` |
| `hello.frink` | Frink | `frink hello.frink` |

`hello.bc` needs the `print` statement rather than a bare string. A bare
string in bc is printed *without* a trailing newline — `bc(1)` says "No
newline character is printed after the string" — so `"Hello, World!"` alone
leaves the cursor on the same line. The extension `print "...\n"` is what
produces a clean line.

`hello.apl` relies on APL's session behaviour: an expression at the top level
is evaluated and its result displayed. The string literal is single-quoted,
which is not optional — unquoted `Hello, World!` would parse as identifiers.

## Desktop and other scripting

| file | language | run |
|---|---|---|
| `hello.ahk` | AutoHotkey v1 | `AutoHotkey hello.ahk` |
| `hello.au3` | AutoIt | `AutoIt3 hello.au3` |
| `hello.el` | Emacs Lisp | `emacs --batch -l hello.el` |
| `hello.raku` | Raku | `raku hello.raku` |
| `hello.jq` | jq | `jq -n -f hello.jq` |

`hello.el` uses `message`, which writes to the echo area and to stderr, not
stdout. In `--batch` mode that lands on the terminal, which is the closest
Emacs has to printing.

`hello.jq` is a filter, not a program: a bare string literal ignores the input
document and emits the string. `-n` supplies `/dev/null` as input so the
filter runs exactly once.

## Verification

Executed on the machine this was written on:

| file | how | result |
|---|---|---|
| `hello.jq` | JSON literal parsed as jq's output value | `Hello, World!` |

The other fourteen were not run — no APL, J, k, Octave, Scilab, Maxima, Sage,
PARI/GP, bc, Frink, AutoHotkey, AutoIt, Emacs or Raku here. Where a snippet
depends on a subtlety (bc's missing newline, APL's unquoted-parses-as-name,
jq's ignored input) the note above explains it rather than leaving it implicit.

Across all eight batches, 15 of 140 language files were executed and 125 were
not. Every `NOTES.md` in the series names which.
