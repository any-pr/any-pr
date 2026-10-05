# Galactic Mission Control

Deterministic, zero-dependency Rust crate: star charts and jump routing.

```bash
cargo run --manifest-path galactic/Cargo.toml -- --seed epic --stars 48 --jump 35
cargo run --manifest-path galactic/Cargo.toml -- --seed epic --jump 150 --json
cargo test --manifest-path galactic/Cargo.toml
```

## Mission parameters

`--seed` picks the galaxy, `--stars` sets the count (2–200), `--jump`
caps the per-hop distance, `--origin`/`--destination` select endpoints,
`--json` emits the full report. Exit code is 0 for a completed
mission, 2 for an unreachable mission or invalid arguments.

Each report carries a SHA-256 `fingerprint` over its canonical JSON as
proof of authenticity. Reproducibility is intended within this
implementation only: the seeded PRNG is not stable across ports.
