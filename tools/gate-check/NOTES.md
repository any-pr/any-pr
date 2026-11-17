# gate-check — run the auto-merge gate before you open the PR

`preflight.py` mirrors the checks in `.github/workflows/auto-merge.yml` against
your working tree. The gate closes a failing PR instead of asking for changes,
so the useful moment to learn that a file is 340 lines is before pushing, not
after.

## Usage

```sh
python tools/gate-check/preflight.py                # against origin/main
python tools/gate-check/preflight.py upstream/main  # against another ref
python tools/gate-check/preflight.py --files a.py   # named paths only
python tools/gate-check/preflight.py --staged       # the index, for hooks
python tools/gate-check/preflight.py --json         # machine-readable
python tools/gate-check/preflight.py --explain      # name the rule on each FAIL
python tools/gate-check/preflight.py --list-rules   # the table itself
python tools/gate-check/preflight.py --selftest     # ten fixed verdicts
python tools/gate-check/preflight.py --ignore '\.md$'   # repeatable
python tools/gate-check/preflight.py --max-lines 200    # stricter than the gate
```

Nothing is pushed, staged or rewritten; the script only reads. `install-hook.sh`
wires it into `pre-commit` with `--staged`.

Exit status: `0` within the gate, `1` would be closed, `2` nothing to check.
With `--json`, the same three states appear as `ok` and `nothing_to_check`.

## What it checks

Same limits and same regexes as the workflow, in the same order: file count,
protected paths (`.github/`, licence instruments, README variants — including
renames into them), prohibited temp/cache/build/binary paths, credential
filenames, counted line totals, per-file line limits, symlinks and submodules by
git mode, and a NUL-byte scan of every file that will exist after the merge.

Lockfiles and vendored/minified/generated files are excluded from the line
counts but still count as files, exactly as Article IV Section 4 says.

## What it cannot know

- Whether the PR is a draft, and whether the head moved while the gate ran.
- Merge conflicts with the base branch beyond a best-effort `git merge-tree`
  probe, which only runs on a clean tree and only with full history.
- GitHub's own `.changes` numbers for renames, which can differ from
  `git diff --numstat` at the margins.

One extra check is not in the workflow: a scan for credential-shaped literals
(`ghp_…`, `AKIA…`, private-key headers) inside otherwise innocuous files. The
workflow only looks at file names, so a key pasted into `notes.md` would sail
through — this script flags it locally.

## FAQ

**My file is text, but it was called binary.** The gate looks for a NUL byte in
the first 50 MB, because that is cheap and hard to fake. UTF-16 text contains NUL
bytes by construction, so it reads as binary. Save as UTF-8.

**The gate merged a file I expected it to reject.** The rules are about names,
sizes and shapes, not content — Article VI says so explicitly. A dangerous shell
script with a harmless name passes.

**A lockfile is exempt from the line count but still counts as a file.** Yes.
Nineteen files under the limit, but `Cargo.lock` is one of them.

**`--ignore` matches something I care about.** `--ignore` changes only the local
report. The gate has no equivalent, so anything ignored here is still seen there;
it filters noise in a scratch tree, it does not make a PR acceptable.

**A test fixture contains a key-shaped string.** Put
`gate-check: allow-secret-fixtures` in the file. The marker is deliberate and
greppable, so a real key cannot pass by accident.

**The conflict probe says it was skipped.** The clone is shallow
(`git fetch --depth 1`), so there is no merge base to reason from. Run
`git fetch --unshallow` if you want that check.

**The line count differs from GitHub's by one or two.** `--numstat` counts what
the diff carries; GitHub's `.changes` occasionally disagrees on renames and on
files it truncates. Near a limit, treat a margin of a few lines as a warning
rather than a pass.
