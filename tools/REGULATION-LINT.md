# regulation-lint

A local pre-flight check for the Regulations governing this repository.

The Regulations (`README.md`, Articles III and IV) enumerate the grounds on
which a pull request is closed without merger. Those grounds are enforced by
the Workflow at `.github/workflows/auto-merge.yml`, which is a protected
instrument: no pull request may amend it. `regulation-lint.py` reproduces the
Workflow's gates on your own machine, so you can learn the verdict *before*
you open a pull request rather than after.

This is a convenience, not an authority. The Workflow is the sole enforcement
instrument, and it acts on the pull request as GitHub sees it — which may
differ from your local view (Article VI, Section 4).

## Usage

```
python tools/regulation-lint.py                    # origin/main...HEAD
python tools/regulation-lint.py --staged           # what git commit would take
python tools/regulation-lint.py --base main~5      # an arbitrary range
python tools/regulation-lint.py --quiet            # failures and summary only
```

Requires Python 3.9 or newer and nothing else. No installation, no
dependencies, no network access.

Exit status: `0` all gates pass, `1` at least one gate fails, `2` usage error
(bad ref, not a repository).

## Gates

| Gate | Source | Limit |
|---|---|---|
| Protected paths | Article III(a) | nothing under `.github/` |
| Protected documents | Article III(b) | no `README` / license instrument, any depth |
| Prohibited filenames | Article III(c) | no temp files, caches, build output, binaries |
| Credential material | Article III(c) | no `.env`, keys, or credential stores |
| Symlinks, submodules | Article III(d) | none (git modes 120000 / 160000) |
| File count | Article IV §1 | at most 20 changed files |
| Counted lines | Article IV §2 | at most 500, excluding generated/vendored |
| Per-file lines | Article IV §3 | at most 300 per file |
| Binary content | Article III(e) | no NUL bytes in any blob |

"Counted lines" excludes lockfiles, `vendor/`, `*.min.js/css`, `*.map`,
`*.snap`, and `*.lock` — these are exempt from the line counts but still count
toward the 20-file limit (Article IV, Section 4).

Renames are checked on both sides: renaming a file *into* a protected path or
*away* from a protected document counts as a modification (Article III(b)).

## Example

```
$ python tools/regulation-lint.py --staged
regulation-lint -- any-pr Regulations pre-flight check
scope: staged changes
files changed: 2

[ 1/9] protected paths (.github/)              PASS
[ 2/9] protected documents (license/README)    PASS
[ 3/9] prohibited filenames                    PASS
[ 4/9] credential/secret material              PASS
[ 5/9] symlinks and submodules                 PASS
[ 6/9] file count <= 20                        PASS
[ 7/9] counted lines <= 500                    PASS
[ 8/9] per-file lines <= 300                   PASS
[ 9/9] binary content                          PASS

VERDICT: all gates passed -- this PR is eligible for automatic merger,
subject to merger succeeding against the base branch (Article II, Section 3).
```

## As a git hook

```sh
# .git/hooks/pre-commit
exec python tools/regulation-lint.py --staged --quiet
```

A non-zero exit aborts the commit. Note that the Workflow cannot be modified
by pull request, so there is no CI hook to add here; contributors wire this up
locally if they want it.

## Caveats

- The Workflow pins the pull request head SHA and re-inspects after every
  push. This script inspects whatever you point it at; nothing more.
- Merger may still fail on a conflict with the base branch
  (Article II, Section 3). This script does not predict that.
- Passage here is not a guarantee of merger, and failure here is not a
  closure. The Workflow decides.
