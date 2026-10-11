# Prime atlas

A dependency-free Rust library containing a sorted prefix of all primes.
Each prime uses eight decimal ASCII digits; records have no runtime separators.
`count()` and zero-based `prime(index)` provide indexed access.
`max_prime()` defines the inclusive coverage bound. `is_prime(n)` and
`prime_count(n)` use binary search and return `None` above that bound.
Zero and one are nonprime; their prime counts are zero.
The committed atlas contains 1,245,123 primes, from 2 through 19,572,743.

```sh
cargo test --manifest-path libs/prime-atlas/Cargo.toml
node libs/prime-atlas/generate.mjs
node libs/prime-atlas/generate.mjs 9961224
```

The optional generator argument budgets `src/data.rs` source bytes, including
syntax and newlines. A sieve up to 22,000,000 produces actual primes and reports
the size, count, largest prime and SHA-256; unused budget stays below one record.
The generated Rust data is intentionally included in GitHub Languages via
the repository's `.gitattributes`, contributing Rust bytes to the language mix.
