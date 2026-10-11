# Swift/Rust language balance audit

This dependency-free Rust utility measures regular Swift and Rust source blobs
in a Git repository's `HEAD`. It uses Git's exact blob byte sizes, with NUL-delimited
paths so spaces, Unicode, tabs, and newlines do not break the audit.

```sh
cargo run --manifest-path tools/language-balance/Cargo.toml -- .
cargo test --manifest-path tools/language-balance/Cargo.toml
```

The optional argument is a repository path; the default is the current directory.
Git and Rust are required. The package has its own workspace and no dependencies.
It executes only Git's tree and attribute queries, never repository scripts.

Each language reports total, counted, and excluded file counts and bytes, plus the
percentage of that language's bytes excluded. Swift matches `.swift` and
`.swift.gyb` (this repository maps the latter to Swift); Rust matches `.rs`.
Symlinks and submodules are skipped. Uncommitted source edits and new files are
absent from `HEAD`, while uncommitted `.gitattributes` changes affect the audit.

Exclusion uses `git check-attr` for `linguist-vendored` and `linguist-generated`.
`set` and `true` exclude a file; `unset`, `false`, and `unspecified` do not.
Git's normal attribute precedence applies, including local/global overrides.

This is a Swift/Rust source audit, not a full GitHub Linguist percentage prediction.
It does not reproduce Linguist's automatic vendoring/generated-file detection,
binary filtering, language overrides, or other languages. GitHub may use different
attributes than this working tree. The excluded percentage is within each
language, not its share of all repository languages.
