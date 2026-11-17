# gate-check — run the auto-merge gate before you open the PR

`preflight.py` mirrors the checks in `.github/workflows/auto-merge.yml` against
your working tree. The gate closes a failing PR instead of asking for changes,
so the useful moment to learn that a file is 340 lines is before pushing, not
after.

## Usage

```sh
python tools/gate-check/preflight.py                # against origin/main
python tools/gate-check/preflight.py upstream/main  # against another ref
python tools/gate-check/preflight.py --files a.py b.md
```

Exit status `0` means the workflow should merge the PR; `1` means it would close
it, and each reason is printed as a `FAIL` line. Nothing is pushed, staged or
rewritten; the script only reads.

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
  probe, which only runs on a clean tree.
- GitHub's own `.changes` numbers for renames, which can differ from
  `git diff --numstat` at the margins.

One extra check is not in the workflow: a scan for credential-shaped literals
(`ghp_…`, `AKIA…`, private-key headers) inside otherwise innocuous files. The
workflow only looks at file names, so a key pasted into `notes.md` would sail
through — this script flags it locally.
