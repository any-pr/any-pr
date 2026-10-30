# Hello, World! in nineteen more languages

A fourth batch, alongside `misc/hello-world/` (three Rust variants),
`misc/hello-world-multilingual/` (eighteen), `misc/hello-world-more-languages/`
(seventeen) and `misc/hello-world-more-languages-2/` (sixteen).

| file | language | run |
|---|---|---|
| `hello.applescript` | AppleScript | `osascript hello.applescript` |
| `hello.au3` | AutoIt | `AutoIt3 hello.au3` |
| `hello.bf` | Brainfuck | any Brainfuck interpreter |
| `hello.chpl` | Chapel | `chpl hello.chpl` |
| `hello.d` | D | `dmd hello.d` |
| `hello.factor` | Factor | `factor hello.factor` |
| `hello.forth` | Forth | `gforth hello.forth` |
| `hello.idr` | Idris | `idris hello.idr` |
| `hello.io` | Io | `io hello.io` |
| `hello.janet` | Janet | `janet hello.janet` |
| `hello.lol` | LOLCODE | any LOLCODE interpreter |
| `hello.m4` | m4 | `m4 hello.m4` |
| `hello.mat` | MATLAB | `matlab -batch hello` |
| `hello.mojo` | Mojo | `mojo hello.mojo` |
| `hello.nix` | Nix | `nix-instantiate --eval hello.nix` |
| `hello.odin` | Odin | `odin run hello.odin` |
| `hello.pike` | Pike | `pike hello.pike` |
| `hello.rexx` | REXX | `rexx hello.rexx` |
| `hello.sml` | Standard ML | `sml hello.sml` |

## On the outliers

`hello.bf` is Brainfuck, which has no literals, so every character is a run of
`+` or `-` from the previous cell value. The widely copied 61-op snippet
circulating online emits `Hello World!` — no comma — because it was written for
a different string. This one was generated and checked by running it in a
2048-cell interpreter; `hello.bf` prints `Hello, World!` followed by a newline.
Trailing `'H'`-style annotations are comments, which every common interpreter
ignores.

`hello.m4` is a macro file with no macro in it. `m4` copies non-macro text to
stdout, so an unadorned `Hello, World!` is the whole program.

`hello.lol` is LOLCODE, whose syntax is deliberately absurd; `VISIBLE` prints.

`hello.nix` evaluates to the string rather than printing it, because Nix is a
pure expression language. `nix-instantiate --eval` prints the value.

## Verification

None of these nineteen were executed — the machine this was written on has no
runtime for any of them. The snippets are the canonical minimal programs for
their languages. Where a snippet deviates from "canonical" the reason is in
the section above.
