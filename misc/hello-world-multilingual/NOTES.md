# Hello, World! in eighteen languages

One file per language, each a minimal program that prints `Hello, World!`.
A companion to `misc/hello-world/`, which holds three Rust variants.

| file | language | run |
|---|---|---|
| `hello.c` | C | `cc hello.c -o hello && ./hello` |
| `hello.cpp` | C++ | `c++ hello.cpp -o hello && ./hello` |
| `hello.cs` | C# | `dotnet run` |
| `hello.go` | Go | `go run hello.go` |
| `Hello.java` | Java | `javac Hello.java && java Hello` |
| `hello.js` | JavaScript | `node hello.js` |
| `hello.kt` | Kotlin | `kotlinc hello.kt -include-runtime -d hello.jar && java -jar hello.jar` |
| `hello.lua` | Lua | `lua hello.lua` |
| `hello.php` | PHP | `php hello.php` |
| `hello.pl` | Perl | `perl hello.pl` |
| `hello.py` | Python | `python3 hello.py` |
| `hello.r` | R | `Rscript hello.r` |
| `hello.rb` | Ruby | `ruby hello.rb` |
| `hello.rs` | Rust | `rustc hello.rs && ./hello` |
| `hello.scala` | Scala | `scala hello.scala` |
| `hello.sh` | Shell | `sh hello.sh` |
| `hello.swift` | Swift | `swift hello.swift` |
| `hello.ts` | TypeScript | `npx ts-node hello.ts` |

`Hello.java` is capitalized because `javac` requires the file name to match the
public class name. Every other file is lowercase.

Verified by running, not by inspection:

| language | runtime | output |
|---|---|---|
| C# | .NET 9.0.312 | `Hello, World!` |
| Java | javac + java | `Hello, World!` |
| JavaScript | Node 22.14.0 | `Hello, World!` |
| Perl | system perl | `Hello, World!` |
| PHP | 8.4.27 | `Hello, World!` |
| Python | 3 | `Hello, World!` |
| Ruby | 3.3.13 | `Hello, World!` |
| Shell | sh | `Hello, World!` |

The other ten have no runtime on the machine this was written on. They are
one-statement programs whose syntax is fixed by the language; the run command
in the table above is the check.
