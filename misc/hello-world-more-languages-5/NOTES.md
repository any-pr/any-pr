# Hello, World! in sixteen more languages

A sixth batch, alongside `misc/hello-world/` (three Rust variants) and the five
`hello-world-multilingual*` / `hello-world-more-languages*` directories
(#6774, #6776, #6777, #6778, #6779).

| file | language | run |
|---|---|---|
| `hello.agda` | Agda | `agda --compile hello.agda` |
| `hello.bqn` | BQN | `bqn hello.bqn` |
| `hello.chapel` | Chapel | `chpl hello.chapel` |
| `hello.coffee` | CoffeeScript | `coffee hello.coffee` |
| `hello.dc` | dc | `dc hello.dc` |
| `hello.ec` | eC | `ecp hello.ec` |
| `hello.gml` | GML | GameMaker, as a script file |
| `hello.lsp` | AutoLISP | AutoCAD command line |
| `hello.purs` | PureScript | `purs hello.purs` |
| `hello.q` | Q | `q hello.q` |
| `hello.sas` | SAS | `sas hello.sas` |
| `hello.snobol` | SNOBOL4 | `snobol4 hello.snobol` |
| `hello.stan` | Stan | `stanc hello.stan` |
| `hello.tex` | TeX | `tex hello.tex` |
| `hello.xquery` | XQuery | `basex hello.xquery` |
| `hello.xsl` | XSLT | `xsltproc hello.xsl hello.xsl` |

## Notes on the odd ones

`hello.dc` is a calculator language, not a general-purpose one. `[Hello,
World!]p` pushes a string and prints it; `dc` has no string literals in the
usual sense, only a stack.

`hello.tex` uses `\message`, which writes to the terminal rather than the
typeset page. A TeX file that prints `Hello, World!` on paper would need a
document skeleton; this one is the terminal-printable core.

`hello.xsl` matches `/` on any input and emits a text node, so it is run
against itself.

`hello.stan` prints from `generated quantities`, the only block in Stan that
can produce output at runtime.

`hello.agda` is the one snippet here that is not a one-liner in spirit. Agda
has no primitive string I/O, so the file postulates `putStrLn` and supplies a
GHC implementation through `COMPILE` pragmas. That is the standard recipe, and
it is why the file is longer than the others.

## Verification

Nothing here was executed — no Agda, BQN, Chapel, CoffeeScript, dc, eC,
GameMaker, AutoCAD, PureScript, kdb+/q, SAS, SNOBOL4, Stan, TeX, BaseX or
xsltproc on the machine this was written on. `NOTES.md` in each batch of this
series says which files were run and which were not, so the six directories
read consistently.
