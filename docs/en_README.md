<!-- Enacted 2026-10-01T14:57:58Z. This document shall not be amended, -->
<!-- superseded, or repealed. Any pull request that modifies it will be   -->
<!-- closed automatically.                                               -->

# REGULATIONS GOVERNING THE AUTOMATIC MERGER OF PULL REQUESTS

**Repository:** any-pr

**Enforcement Instrument:** `.github/workflows/auto-merge.yml`

---

WHEREAS, this Repository operates under a policy of unrestricted contribution; and

WHEREAS, it is deemed expedient to establish uniform rules under which pull requests shall be received, examined, and merged without human review;

NOW, THEREFORE, the following Regulations are hereby adopted and shall be binding upon all contributors.

## ARTICLE I / GENERAL PROVISIONS

**Section 1. Purpose.** These Regulations prescribe the conditions under which a Pull Request submitted to this Repository shall be merged automatically. No manual review shall be conducted at any stage of the process.

**Section 2. Definitions.** For the purposes of these Regulations:

- (a) **"Pull Request"** or **"PR"** means a proposed set of changes submitted for merger into the base branch;
- (b) **"the Workflow"** means the automated enforcement instrument maintained at `.github/workflows/auto-merge.yml`;
- (c) **"License Instrument"** means any file constituting a license agreement, including `LICENSE`, `LICENCE`, `UNLICENSE`, `COPYING`, `COPYRIGHT`, all suffixed variants thereof (e.g., `LICENSE-MIT`, `LICENSE_APACHE-2.0`), and the contents of any `LICENSES/` directory;
- (d) **"Prohibited Files"** means temporary files, editor or operating-system residue (`*.tmp`, `*.bak`, `*~`, `.DS_Store`, `Thumbs.db`, swap files), caches (`__pycache__/`, `node_modules/`, `.turbo/`, and similar), build output directories (`dist/`, `build/`, `out/`, `target/`, `coverage/`, and similar), files bearing compiled or binary extensions (`*.exe`, `*.so`, `*.o`, `*.class`, `*.jar`, `*.zip`, `*.whl`, and similar), and credential or secret material (`.env` and variants thereof, private keys, certificate stores, credential stores such as `.npmrc`, `.netrc`, `.pgpass`, and similar), it being provided that clearly-marked example templates such as `.env.example` are exempt;
- (e) **"the README"** means this document, namely `README.md` and all variants thereof (`README`, `README.rst`, localized variants such as `README.zh-CN.md`, and files so named at any depth).

## ARTICLE II / AUTOMATIC MERGER

**Section 1. Mandatory Merger.** Any Pull Request not designated as a draft which satisfies the requirements of Article III shall be merged forthwith by means of a squash commit, whereupon its source branch shall be deleted insofar as the Workflow's authority permits.

**Section 2. Draft Pull Requests.** A Pull Request bearing draft designation shall not be merged until such designation is withdrawn.

**Section 3. Failure of Merger.** Where merger cannot proceed by reason of conflict with the base branch, notice thereof shall be posted upon the Pull Request, and the contributor shall rebase and resubmit.

## ARTICLE III / GROUNDS FOR MANDATORY CLOSURE

A Pull Request shall be closed without merger if it:

- (a) modifies, adds, deletes, or renames any file or directory under `.github/`;
- (b) modifies any License Instrument or the README, it being provided that deletion or renaming shall constitute modification;
- (c) introduces one or more Prohibited Files;
- (d) introduces symlinks or submodules, such determination to be made by inspection of git file modes;
- (e) introduces binary content, howsoever named, such determination to be made by inspection of the underlying git objects;
- (f) exceeds the size limitations prescribed in Article IV.

## ARTICLE IV / SIZE LIMITATIONS

**Section 1.** A Pull Request shall not modify more than twenty (20) files.

**Section 2.** A Pull Request shall not contain more than five hundred (500) counted changed lines in aggregate.

**Section 3.** No single file within a Pull Request shall exceed three hundred (300) changed lines.

**Section 4.** Generated and vendored content 鈥� including lockfiles, the contents of `vendor/`, minified files, and snapshots 鈥� shall be exempt from the line counts prescribed in Sections 2 and 3, but shall remain subject to the file count prescribed in Section 1.

## ARTICLE V / PROHIBITION OF DIRECT PUSHES

**Section 1.** No person shall push commits directly to the `main` branch. All changes shall be submitted exclusively through Pull Requests.

**Section 2.** Any commit arriving upon `main` without an associated merged Pull Request shall cause the branch to be restored to its state immediately prior to such push, and a record of the incident shall be made.

## ARTICLE VI / DISCLAIMER

**Section 1. Nature of the Repository.** This Repository is an experiment in automated governance. It is not, and shall not be construed as, a model of sound engineering practice.

**Section 2. Absence of Review.** All content merged herein is unreviewed. Contributors and consumers of this Repository are advised that its contents may include, without limitation, spam, nonsense, conflicting edits, nonfunctional code, and material of questionable taste. The rules enumerated herein filter specified hazards only and make no determination as to quality, utility, or intent.

**Section 3. No Warranty.** No code in this Repository shall be executed, deployed, or relied upon for any purpose. Every file shall be treated as untrusted.

**Section 4. Best-Effort Enforcement.** Enforcement of these Regulations is effected by an automated workflow reacting to repository events. Such enforcement is best-effort in nature, does not constitute an access control, and affords no guarantee as to the state of the Repository at any given moment.

**Section 5. Responsibility of Contributors.** Each contributor shall bear sole responsibility for the content of their Pull Requests. No contributor shall submit material that is illegal, harmful, infringing, or injurious to the contributor's own reputation, the contributor being hereby reminded that submissions are publicly attributable to their author.

**Section 6. Acceptance of Terms.** The act of opening a Pull Request shall constitute acceptance that such Pull Request may be merged, closed, or otherwise altered by subsequent contributors at any time. Such is the purpose of the experiment.
