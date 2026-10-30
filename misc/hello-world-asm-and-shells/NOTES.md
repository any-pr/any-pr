# Hello, World! across eight instruction sets and eleven tool languages

A seventh batch, alongside `misc/hello-world/` (three Rust variants) and the
six `hello-world-multilingual*` / `hello-world-more-languages*` directories
(#6774, #6776, #6777, #6778, #6779, #6780).

Unlike the earlier batches, most of this one is not "a language" in the usual
sense: eight entries are the same program in eight different machine
instruction sets, and the rest are shells, build tools, and command languages.

## Instruction sets

| file | ISA | environment | syscall / ROM entry |
|---|---|---|---|
| `hello.s` | MIPS32 | SPIM | `syscall` 4 (print string), 10 (exit) |
| `hello.riscv` | RISC-V 64 | Linux | `ecall`, a7=64 (write), a7=93 (exit) |
| `hello.aarch64` | AArch64 | Linux | `svc #0`, x8=64 (write), x8=93 (exit) |
| `hello.asmx86` | x86 32-bit | Linux | `int 0x80`, eax=4 (write), eax=1 (exit) |
| `hello.sparc` | SPARC V8 | Linux | `ta 0`, g1=4 (write) |
| `hello.ppc` | PowerPC 32 | Linux | `sc`, r0=4 (write), r0=1 (exit) |
| `hello.asm6502` | 6502 | Commodore 64 KERNAL | `jsr $FFD2` (CHROUT) |
| `hello.z80` | Z80 | ZX Spectrum ROM | `rst $10` (print character) |

The 6502 and Z80 entries call into firmware rather than an OS: neither chip
has a syscall instruction, so the program jumps to a ROM routine whose
address is fixed by the machine. `$FFD2` is the C64 KERNAL character output
routine; `RST $10` is the Spectrum's. Both are the idiomatic way to print on
those machines, and both require the accompanying hardware or emulator.

The six Linux entries differ only in how the kernel is entered. Each packs the
same two operations — write 14 bytes to fd 1, then exit 0 — into whatever
register convention its ABI prescribes.

## Tool and shell languages

| file | language / tool | run |
|---|---|---|
| `hello.bats` | Bats (bash test harness) | `bats hello.bats` |
| `hello.cmake` | CMake script mode | `cmake -P hello.cmake` |
| `hello.elv` | Elvish | `elvish hello.elv` |
| `hello.fish` | fish | `fish hello.fish` |
| `hello.gdb` | GDB command script | `gdb -q -batch -x hello.gdb` |
| `hello.ksh` | KornShell | `ksh hello.ksh` |
| `hello.make` | Make | `make -f hello.make` |
| `hello.nu` | Nushell | `nu hello.nu` |
| `hello.sql` | SQL | `sqlite3 :memory: < hello.sql` |
| `hello.xonsh` | Xonsh | `xonsh hello.xonsh` |
| `hello.zsh` | zsh | `zsh hello.zsh` |

`hello.cmake` uses `message(STATUS ...)`, which writes to stdout in script
mode. `hello.make` is a one-target Makefile whose recipe echoes; the `@`
suppresses make's own echo of the command line, leaving only the greeting.

`hello.gdb` is a debugger script rather than a program. `echo` inside a GDB
command file prints to GDB's stdout; `quit` ends the session.

## Verification

Executed on the machine this was written on:

| file | result |
|---|---|
| `hello.sql` | `Hello, World!` (sqlite3 CLI, piped) |

The other nineteen were not run: no assembler for any of the eight ISAs, and
no fish, zsh, ksh, elvish, ion, xonsh, cmake, make, gdb or bats installed.
Every snippet follows the documented convention for its target, and the
notes above name the syscall or ROM entry each one uses so the claim is
checkable rather than asserted.

Across all seven batches, 14 of the 125 language files were executed and 111
were not. Each `NOTES.md` names which.
