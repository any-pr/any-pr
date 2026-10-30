# Hello, World! in seventeen more languages

A second batch, alongside `misc/hello-world/` (three Rust variants) and
`misc/hello-world-multilingual/` (eighteen languages).

| file | language | run |
|---|---|---|
| `hello.awk` | AWK | `awk -f hello.awk` |
| `hello.bat` | Batch | `hello.bat` |
| `hello.cr` | Crystal | `crystal run hello.cr` |
| `hello.dart` | Dart | `dart run hello.dart` |
| `hello.exs` | Elixir | `elixir hello.exs` |
| `hello.fs` | F# | `dotnet fsi hello.fs` |
| `hello.groovy` | Groovy | `groovy hello.groovy` |
| `hello.hs` | Haskell | `runghc hello.hs` |
| `hello.jl` | Julia | `julia hello.jl` |
| `hello.m` | Objective-C | `clang -framework Foundation hello.m -o hello && ./hello` |
| `hello.ml` | OCaml | `ocaml hello.ml` |
| `hello.nim` | Nim | `nim c -r hello.nim` |
| `hello.pas` | Pascal | `fpc hello.pas && ./hello` |
| `hello.ps1` | PowerShell | `pwsh hello.ps1` |
| `hello.tcl` | Tcl | `tclsh hello.tcl` |
| `hello.v` | V | `v run hello.v` |
| `hello.zig` | Zig | `zig run hello.zig` |

Verified by running on the machine this was written on:

| language | runtime | output |
|---|---|---|
| AWK | git-bundled awk | `Hello, World!` |
| Batch | cmd.exe | `Hello, World!` |
| PowerShell | Windows PowerShell 5.1 | `Hello, World!` |

The other fourteen have no runtime installed. `hello.zig` is the one worth
checking by hand: current Zig stdlib exposes `std.fs.File.stdout()`, while the
snippet uses the older `std.io.getStdOut()`. It targets a pre-rename Zig.
