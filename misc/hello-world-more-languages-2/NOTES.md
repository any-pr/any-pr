# Hello, World! in sixteen more languages

A third batch, alongside `misc/hello-world/` (three Rust variants),
`misc/hello-world-multilingual/` (eighteen languages) and
`misc/hello-world-more-languages/` (seventeen).

| file | language | run |
|---|---|---|
| `hello.adb` | Ada | `gnatmake hello.adb && ./hello` |
| `hello.asm` | x86-64 assembly (NASM) | `nasm -f elf64 hello.asm && ld hello.o -o hello` |
| `hello.clj` | Clojure | `clojure hello.clj` |
| `hello.cob` | COBOL | `cobc -x hello.cob && ./hello` |
| `hello.elm` | Elm | `elm make hello.elm` |
| `hello.erl` | Erlang | `escript hello.erl` |
| `hello.f` | Fortran 77 | `gfortran hello.f -o hello && ./hello` |
| `hello.f90` | Fortran | `gfortran hello.f90 -o hello && ./hello` |
| `hello.hx` | Haxe | `haxe -main Hello --interp` |
| `hello.lisp` | Common Lisp | `sbcl --script hello.lisp` |
| `hello.pl` | Prolog | `swipl hello.pl` |
| `hello.raku` | Raku | `raku hello.raku` |
| `hello.rkt` | Racket | `racket hello.rkt` |
| `hello.scm` | Scheme | `guile hello.scm` |
| `hello.sol` | Solidity | `solc hello.sol` |
| `hello.vala` | Vala | `valac hello.vala && ./hello` |

Two notes on the extensions:

`hello.pl` is Prolog, not Perl. Both languages use `.pl`; the Perl file lives
in `misc/hello-world-multilingual/`, so the two do not collide on disk, but a
reader sorting by extension will not be able to tell them apart.

`hello.erl` is an escript rather than a plain module, so it carries a shebang
line and an unused argument in `main/1`. Run it with `escript`, or `chmod +x`
and execute directly on a system that honours the shebang.

None of these sixteen have a runtime installed on the machine this was written
on, so none were executed. Every snippet is the canonical minimal program for
its language; where that is not the case, the note above says so.
