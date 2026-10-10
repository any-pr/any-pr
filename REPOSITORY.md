# Repository Overview

Snapshot of `any-pr/any-pr` at `de80c7a07` ("Audit issue 7", 2026-10-10).

This repository is a collection of unrelated projects: applications, services,
libraries, tooling, documentation, and experiments — plus three large upstream
source trees imported through the "Revival" batch-PR series. There is no single
product; each top-level directory is an independent project.

## Scale

- **31,192 tracked files**, ~1.94M lines total, ~183 MB working tree
- **5,296 commits** on `main` (squash-merge style history)
- The three imported trees account for ~96% of all content

| Directory | Files | Lines | Contents |
|---|---|---|---|
| `swift/` | 23,911 | 1,201,941 | Apple Swift compiler monorepo (imported via "Swift Revival: batch N" PRs) |
| `vscode/` | 5,060 | 493,797 | VS Code source (partial import via "VSCode Revival" PRs — eslint plugins, `src/`, `test/`, `extensions/`, `cli/`) |
| `neoforbric/` | 2,051 | 224,720 | Forbric — a Minecraft mod-loader kernel that runs Fabric and NeoForge mods together (`forbric-kernel`, `forbric-loader`, `forbric-kernel-installer`, `tools/`); docs in EN + zh-CN |
| `apps/` | 80 | 11,049 | End-user applications |
| `services/` | 12 | 1,633 | HTTP services |
| `libs/` | 12 | 844 | Libraries |
| `tools/` | 24 | 3,235 | CLI/utilities and PR-submission tooling |
| `docs/` | 27 | 1,684 | Repo-wide docs + document-only projects |
| `misc/` | 8 | 479 | Loose samples and experiments |
| `cex/` | 1 | 0* | `help_me.c` — a joke C file linking to an external compiler project |

\* `help_me.c` contains no newline-terminated lines by `wc -l` count.

## Layout

```
apps/                    End-user applications
  AMVC/                  AMV converter (JavaScript + C# CLI/library)
  mc-launcher/           Minecraft launcher (Python, with tests)
  processsh/             WinUI 3 process/shell utility (C#/XAML)
  runprocess-lite/       Process launcher
  RunProcess-Lite-New/   Swift launcher app

services/                HTTP services
  mgtv-api/              MGTV API
  music-api-rs/          Kuwo music API — Rust/Axum rewrite of the original
                         PHP endpoints; keeps .php route names for drop-in
                         compatibility (kw, kggc, kwsc, playlist, playsc)

libs/                    Libraries
  galactic/              Rust crate (workspace member)
  VastNova-Repository/   Library project

tools/                   Tooling
  F-kAgent/              Agent skill + install docs
  jev-translate-judge/   Python translation judge
  regulation-lint.py     Regulation linter (+ REGULATION-LINT.md)
  submit-pr-cs/          C# PR-submission tool (chains/plan/runner)
  submit-pr-gui/         WinForms front-end for submit-pr
  submit-pr-server/      ASP.NET PR-submission service

docs/                    Documentation
  CONTRIBUTING*.md       Contribution guide in 9 languages
  EXPERIMENT-LOG.md, INDEX.md, LEGAL-OPINION.md
  iflytekpad/, jiugong/, naiwa/   Document-only projects

misc/                    Leftovers kept intentionally
  hello-world/, src/ (loose .rs demos), spam/, index.html

cex/                     help_me.c (one-file oddity)
swift/                   Imported Swift compiler tree
vscode/                  Imported VS Code tree
neoforbric/              Imported Forbric mod-loader tree
```

## Build

A Cargo workspace ties together the two Rust crates:

```toml
[workspace]
resolver = "2"
members = ["libs/galactic", "services/music-api-rs"]
```

```sh
cargo check -p music-api-rs   # music API service
cargo check -p galactic
```

Everything else builds per-project (dotnet, npm, cmake, etc.) — see each
project's own docs. There is no repo-wide build.

## Languages

Top by line count: Swift 780k · TypeScript 412k · Java 188k · C headers 141k ·
SIL 81k · C++ 80k · Python 33k · TSX 24k · Rust 8.8k · C# 6.6k.
Non-code (Markdown, JSON, txt, test fixtures) ~110k lines.

## Contribution constraints (auto-merge gate)

`.github/workflows/auto-merge.yml` enforces, per PR:

- ≤ 20 changed files, ≤ 500 counted lines (additions + deletions)
- ≤ 300 lines per file; generated files like `Cargo.lock` excluded
- Root `README.md`, `LICENSE`, `.github/**` are protected
- Passing PRs auto-merge; large features land as sequential `(part N/M)` series
- Pure renames count as 0 lines — bulk moves are still capped by the file count

## History highlights

- The PHP `music-api/` endpoints were rewritten in Rust under
  `services/music-api-rs/` (PRs #284–#290), preserving routes and JSON shapes.
- The top level was reorganized by purpose (PRs #291–#298) into
  `apps/ services/ libs/ tools/ docs/ misc/`.
- The `swift/`, `vscode/`, and `neoforbric/` trees arrived afterwards via the
  Revival batch series; they are imports of external upstream projects and are
  not part of the purpose-grouped layout.
