# gate-sim

A pre-flight planner for the Regulations governing this repository.

`tools/regulation-lint.py` answers *"does this change pass?"* — it inspects a
git range, so the change has to exist first. `gate-sim` answers the earlier
question, *"would this change pass?"* It takes a plan — the files you intend to
add, modify, remove, or rename, with their changed-line counts — and reports
the verdict the Workflow would reach, before any of those files are written.

That matters mainly for Article IV. A plan that exceeds twenty files, five
hundred counted lines, or three hundred lines in a single file is cheap to
split now and expensive to discover after the Workflow has closed the pull
request.

This is a convenience, not an authority. The Workflow
(`.github/workflows/auto-merge.yml`) is the sole enforcement instrument, and it
acts on the pull request as GitHub sees it — which may differ from your plan
(Article VI, Section 4).

## Usage

```
python tools/gate-sim/gate_sim.py --plan plan.json
python tools/gate-sim/gate_sim.py --add misc/demo.py=40 --modify docs/index.md=12
python tools/gate-sim/gate_sim.py --rename docs/a.md:docs/b.md --remove old.tmp
cat plan.json | python tools/gate-sim/gate_sim.py
```

Requires Python 3.9 or newer and nothing else. No installation, no
dependencies, no network access.

Exit status: `0` all gates pass, `1` at least one gate fails, `2` usage error
(unreadable or malformed plan, empty plan).

## Plan format

JSON, either a list or an object with a `files` list. Every field but `path` is
optional.

```json
{"files": [
  {"path": "misc/demo.py", "status": "added", "lines": 40},
  {"path": "docs/new.md", "status": "renamed", "lines": 0,
   "previous_path": "docs/old.md"},
  {"path": "junk.tmp", "status": "removed"}
]}
```

| Field | Meaning |
|---|---|
| `path` | Repository-relative path the file will have after the merge |
| `status` | `added`, `modified`, `removed`, or `renamed` (default `modified`) |
| `lines` | Changed lines GitHub will report, additions + deletions (default `0`) |
| `previous_path` | Pre-rename path; checked by the rename-aware gates |
| `mode` | Git file mode — `120000` symlink, `160000` submodule (default `100644`) |
| `binary` | Declared binary content (default `false`) |

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
| Binary content | Article III(e) | declared, not detected — see below |

Renames are checked on both sides: renaming a file *into* a protected path or
*away* from a protected document counts as a modification (Article III(b)).
Removals are skipped by the gates that inspect surviving content
(Article III(c) applies to files that will exist after the merge).

## Example

```
$ python tools/gate-sim/gate_sim.py --add misc/demo.py=40 --modify docs/index.md=12
gate-sim -- any-pr Regulations pre-flight planner
plan: 2 file(s), 52 counted line(s)

[ 1/9] protected paths (.github/)                 PASS no path under .github/
[ 2/9] protected documents (license/README)       PASS no license instrument or README, any depth
[ 3/9] prohibited filenames                       PASS no temp files, caches, build output, or binaries
[ 4/9] credential/secret material                 PASS no .env, private keys, or credential stores
[ 5/9] symlinks and submodules                    PASS no symlinks or submodules
[ 6/9] file count <= 20                           PASS 2/20 files
[ 7/9] counted lines <= 500                       PASS 52/500 counted lines
[ 8/9] per-file lines <= 300                      PASS no file over 300 changed lines
[ 9/9] binary content (declared)                  PASS no binary blobs

VERDICT: all gates passed -- this plan is eligible for automatic merger,
subject to merger succeeding against the base branch (Article II, Section 3).
```

A failing plan names the offending paths and exits `1`:

```
$ python tools/gate-sim/gate_sim.py --add junk.tmp=3
gate-sim -- any-pr Regulations pre-flight planner
plan: 1 file(s), 3 counted line(s)

[ 1/9] protected paths (.github/)                 PASS no path under .github/
[ 2/9] protected documents (license/README)       PASS no license instrument or README, any depth
[ 3/9] prohibited filenames                       FAIL junk.tmp
[ 4/9] credential/secret material                 PASS no .env, private keys, or credential stores
[ 5/9] symlinks and submodules                    PASS no symlinks or submodules
[ 6/9] file count <= 20                           PASS 1/20 files
[ 7/9] counted lines <= 500                       PASS 3/500 counted lines
[ 8/9] per-file lines <= 300                      PASS no file over 300 changed lines
[ 9/9] binary content (declared)                  PASS no binary blobs

VERDICT: 1 gate(s) failed -- the Workflow would close this PR:
  - prohibited filenames: junk.tmp

Split or amend the plan, then run gate-sim again.
```

`--quiet` prints failures and the verdict only.

## Caveats

- A plan declares its own facts. `gate-sim` never opens a blob, so symlinks,
  submodules, and binary content come from the `mode` and `binary` fields
  rather than from the git objects the Workflow inspects. Declare them
  honestly or the simulation is worth nothing.
- Counted-line figures are whatever you put in `lines`. GitHub's own
  `additions + deletions` is authoritative.
- Merger may still fail on a conflict with the base branch (Article II,
  Section 3). `gate-sim` does not predict that.
- Passage here is not a guarantee of merger, and failure here is not a
  closure. The Workflow decides.
