# Hello, World! in nineteen more languages

A fifth batch, alongside `misc/hello-world/` (three Rust variants) and the four
`hello-world-multilingual*` / `hello-world-more-languages*` directories
(#6774, #6776, #6777, #6778).

| file | language | run |
|---|---|---|
| `hello.as` | ActionScript | Flash Player / Ruffle |
| `hello.bal` | Ballerina | `bal run hello.bal` |
| `hello.c3` | C3 | `c3c compile-run hello.c3` |
| `hello.cb` | Carbon | `carbon run hello.cb` |
| `hello.e` | Eiffel | `ec hello.e` |
| `hello.fth` | Forth (gforth) | `gforth hello.fth` |
| `hello.gleam` | Gleam | `gleam run` |
| `hello.hy` | Hy | `hy hello.hy` |
| `hello.icn` | Icon | `icont hello.icn && ./hello` |
| `hello.lean` | Lean | `lean --run hello.lean` |
| `hello.mercury` | Mercury | `mmc --make hello` |
| `hello.moon` | MoonScript | `moon hello.moon` |
| `hello.nu` | Nushell | `nu hello.nu` |
| `hello.pony` | Pony | `ponyc . && ./hello` |
| `hello.roc` | Roc | `roc run hello.roc` |
| `hello.rsc` | Rascal | `rascal hello.rsc` |
| `hello.st` | Smalltalk | `gst hello.st` |
| `hello.vb` | Visual Basic | `dotnet run` |
| `hello.wren` | Wren | `wren hello.wren` |

## Two notes

`hello.vb` uses a module named `Hello` with a `RootNamespace` left empty. A
project that sets a root namespace puts `Main` behind that prefix, and
`StartupObject` has to name the full path; leaving the root namespace empty is
the version that actually runs. Verified:

```
$ dotnet run
Hello, World!
```

`hello.roc` pins a platform tarball URL in the header. Roc requires an explicit
platform for a runnable app. The URL is `roc-lang/basic-cli` release 0.26.0,
asset `GddgvsGjiNXrHFW6au5i5dUDbTs8FinQdrgE2Gqmw53t.tar.zst`, confirmed to
resolve (302 to a signed asset URL) rather than pasted from a template.

## Verification

Only `hello.vb` was executed. The other eighteen have no runtime on the
machine this was written on. `NOTES.md` here and in the other batches states
which files were run and which were not, so the four directories can be read
together without guessing.
