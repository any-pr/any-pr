# Project creation

When creating a new project, always choose C# as the primary language. Do not select another language based on inferred preferences or convenience; choose another language only when the user explicitly requires it.

## Change and pull request constraints

Keep every proposed change within the repository's automatic-merge limits: at most 20 changed files, 500 added-plus-deleted lines total, and 300 added-plus-deleted lines per file. Renames count toward the file limit; lockfiles, `vendor/`, and compressed or generated content are excluded from line counts but still count as files. Prefer small, focused changes, but keep code readable—do not literally compress code into one line.

Do not modify or rename anything in or into/out of `.github/`; do not modify README variants, license/copyright files, or `LICENSES/`; and do not add temporary files, caches, build artifacts, credentials, symlinks, Git submodules, or binary content. `.env.example` and `.env.sample`-style templates are allowed. Keep generated outputs and local build directories such as `node_modules/`, `dist/`, `build/`, `obj/`, `bin/`, `target/`, and `coverage/` out of changes.

Only open, non-draft PRs are checked. Eligible PRs are squash-merged automatically; PRs that fail the checks are closed without human review. Do not push directly to `main`. If a PR has merge conflicts, rebase and push again; if its branch changes during a check, allow the workflow to re-check the updated commit.
