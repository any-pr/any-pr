#!/usr/bin/env python3
"""Run nightly, on the developer's Mac, what CI cannot run: the client gates (they open a macOS window), the
gates that need third-party mod packs, and the soak.

launchd starts it (com.forbric.nightly.plist; README.md beside this file says how to install it). Python
standard library only, like tools/dev.py. One night:

  1. fetch, and put a dedicated worktree (--work) detached at origin/main. The main checkout is only read:
     its HEAD, branch, index and files are never changed.
  2. link into it, from the main checkout, the fixtures the gates and the staged tests read (FIXTURES).
  3. run `tools/dev.py integration` (strict: a skipped test fails it), then `gates-all.sh`. gate-m34-soak takes
     two hours or more, so it runs only on Sundays, and a Sunday run is a --release acceptance run.
  4. write results/<YYYY-MM-DD>/summary.md and latest.md on the orphan branch ci-results, in a worktree of its
     own (<work>-results), and push that branch and nothing else.
  5. set the commit status nightly/dev-mac on the tested commit.

--dry-run prints every command and runs only the ones that change nothing: no worktree is made or cleaned, no
test runs, nothing is committed, pushed or posted.
"""
import argparse
from collections import Counter
import datetime
import os
from pathlib import Path
import shlex
import signal
import subprocess
import sys
import time

REPO_SLUG = 'Ray-T-r/Minecraft-Forbric-mod-loader'
STATUS_CONTEXT = 'nightly/dev-mac'
RESULTS_BRANCH = 'ci-results'
SOAK_GATE = 'gate-m34-soak.sh'
SUNDAY = 6  # datetime.date.weekday()
# Everything the gates and the staged tests read from a checkout that is not in git, found by reading
# forbric-kernel/run/gate-m*.sh, gates-all.sh, gates-parallel.py and the tests' fixture paths. The rest of what
# they read comes from FORBRIC_OLD (the staged game jars, the downloaded mods and canaries in
# forbric-loader/run) and MC_DIR, which stay in the main checkout and are passed by environment instead.
FIXTURES = (
    # Pack installs. The client gates get their own copy of client-merged-pack from gates-parallel.py, so no
    # gate writes through these links; the others are only read (m8 reads client-kernel/mods, the staged tests
    # read client-popular and client-kernel).
    'forbric-kernel/run/client-merged-pack',
    'forbric-kernel/run/client-popular',
    'forbric-kernel/run/client-neo-pack',
    'forbric-kernel/run/client-kernel',
    # The third-party mod sets the bytecode tests read (sweep90, carpet, create-fly, ...).
    'forbric-kernel/build/compat-inputs',
    # Two more the tests read by path (GuiItemCaptureMixinAdapterTest, KernelClientHookMixinAnchorsTest,
    # CompatPluginPlatformInjectorTest). Without them the first nightly's strict run failed those tests as skipped.
    'forbric-kernel/build/sweep80-mac',
    'forbric-kernel/build/sweep100-mac-network',
    # The fabric-loader substrate ./bootstrap.sh checks out (gitignored). forbric-loader compiles its sources, so the
    # installer build (gate-m17) and gate-m0's bundled-baseline check cannot run without it; the first nightly
    # reported both as failures of the code under test.
    'fabric-loader',
    # tools/dev.py's state: the pinned fabric-api and energy jars and the natives.
    'forbric-kernel/.dev',
)
GIT = ('git',)
GH = ('gh',)
BASH = ('bash',)
PYTHON = (sys.executable,)


class NightlyError(Exception):
    pass


class Step:
    """One long command of the night: what ran, how it ended, where its output is."""

    def __init__(self, name, display, ran=False, returncode=None, seconds=0.0, timed_out=False, limit=0, note=''):
        self.name, self.display, self.ran = name, display, ran
        self.returncode, self.seconds, self.timed_out, self.limit, self.note = returncode, seconds, timed_out, limit, note

    @property
    def ok(self):
        return self.ran and not self.timed_out and self.returncode == 0

    def describe(self):
        if not self.ran:
            return 'not run' + (f' ({self.note})' if self.note else '')
        if self.timed_out:
            return f'TIMED OUT after {minutes(self.seconds)} (limit {minutes(self.limit)})'
        if self.returncode == 0:
            return f'passed in {minutes(self.seconds)}'
        return f'FAILED (exit {self.returncode}) in {minutes(self.seconds)}'

    def short(self):
        if not self.ran:
            return 'not run'
        if self.timed_out:
            return 'TIMED OUT'
        return 'passed' if self.returncode == 0 else f'FAILED (exit {self.returncode})'


class Night:
    def __init__(self, date, soak, dry_run):
        self.date, self.soak, self.dry_run = date, soak, dry_run
        self.started = datetime.datetime.now().astimezone()
        self.sha, self.subject, self.ref = '', '', ''
        self.errors, self.missing_fixtures = [], []
        self.integration = Step('integration', 'python3 tools/dev.py integration')
        self.gates = Step('gates', 'bash forbric-kernel/run/compat/gates-all.sh')
        self.junit = ''
        self.gate_results = []
        self.logs = ''

    @property
    def passed(self):
        return (not self.errors and self.integration.ok and self.gates.ok and bool(self.gate_results)
                and not any(verdict == 'RED' for _, verdict, _ in self.gate_results))


def minutes(seconds):
    return f'{seconds / 60:.0f} min' if seconds >= 60 else f'{seconds:.0f} s'


def soak_tonight(date, mode='auto'):
    """The soak is >= 7200 s of simulation on top of the half hour the rest takes: once a week is enough."""
    return mode == 'always' or (mode == 'auto' and date.weekday() == SUNDAY)


def gates_arguments(soak, jobs):
    # A weekday run skips the soak, so it cannot be --release: a release run fails on any --skip by design.
    # Sunday's run has nothing skipped and is the full acceptance run.
    return ['-j', str(jobs)] + (['--release'] if soak else ['--skip', SOAK_GATE])


def default_minecraft_dir():
    # The launch scripts' own default, so the tests and the gates of one night read one Minecraft install.
    if sys.platform == 'darwin':
        return Path.home() / 'Library/Application Support/minecraft'
    return Path.home() / '.minecraft'


class Runner:
    """Prints every command before running it. Under --dry-run it runs only the ones that change nothing."""

    def __init__(self, dry_run, out=None):
        self.dry_run = dry_run
        self.out = out or sys.stdout

    def say(self, text):
        print(text, file=self.out, flush=True)

    def show(self, command, cwd=None, env=None, skipped=False):
        text = shlex.join(str(part) for part in command)
        if env:
            text = ' '.join(f'{key}={shlex.quote(str(value))}' for key, value in env.items()) + ' ' + text
        if cwd:
            text = f'(cd {shlex.quote(str(cwd))} && {text})'
        self.say(('[dry-run] ' if skipped else '+ ') + text)

    def run(self, command, writes=True, check=True):
        skipped = self.dry_run and writes
        self.show(command, skipped=skipped)
        if skipped:
            return subprocess.CompletedProcess(command, 0, '', '')
        result = subprocess.run([str(part) for part in command], capture_output=True, text=True,
                stdin=subprocess.DEVNULL)
        if check and result.returncode:
            raise NightlyError(f'{shlex.join(str(p) for p in command)} exited {result.returncode}: '
                    + (result.stderr or result.stdout).strip()[-500:])
        return result

    def git(self, directory, *arguments, writes=True, check=True):
        return self.run(GIT + ('-C', str(directory)) + arguments, writes=writes, check=check)

    def symlink(self, source, target):
        self.show(['ln', '-s', source, target], skipped=self.dry_run)
        if not self.dry_run:
            target.parent.mkdir(parents=True, exist_ok=True)
            os.symlink(source, target, target_is_directory=source.is_dir())

    def unlink(self, path):
        self.show(['rm', path], skipped=self.dry_run)
        if not self.dry_run:
            path.unlink()

    def write_text(self, path, text):
        self.say(f'{"[dry-run] " if self.dry_run else ""}write {path} ({len(text.encode("utf-8"))} bytes)')
        if not self.dry_run:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding='utf-8', newline='\n')

    def step(self, step, command, cwd, env_overrides, timeout, log):
        """A long command with its output in a log file, stopped with everything it started at the timeout."""
        self.show(command, cwd=cwd, env=env_overrides, skipped=self.dry_run)
        step.limit = timeout
        if self.dry_run:
            step.note = 'dry run'
            return step
        log.parent.mkdir(parents=True, exist_ok=True)
        started = time.monotonic()
        with log.open('wb') as stream:
            try:
                # Its own session, so the timeout reaches gradle's test JVMs and a gate's game processes, not
                # only the shell or python at the top.
                process = subprocess.Popen([str(part) for part in command], cwd=cwd,
                        env=dict(os.environ, **env_overrides), stdout=stream, stderr=subprocess.STDOUT,
                        stdin=subprocess.DEVNULL, start_new_session=os.name == 'posix')
            except OSError as error:
                step.note = f'could not start: {error}'
                return step
            try:
                process.wait(timeout=timeout)
            except subprocess.TimeoutExpired:
                step.timed_out = True
                stop(process)
        step.ran, step.returncode, step.seconds = True, process.returncode, time.monotonic() - started
        self.say(f'  {step.name}: {step.describe()}; log {log}')
        return step


def stop(process, grace=60):
    if os.name != 'posix':
        # /T takes the whole tree; Popen.kill() alone would leave what the step started running.
        subprocess.run(['taskkill', '/F', '/T', '/PID', str(process.pid)], capture_output=True)
        process.wait()
        return
    # PermissionError as well as ProcessLookupError: once the leader has been reaped, macOS can answer EPERM for a
    # group that has nothing left this user may signal. Either way nothing of ours is left to stop.
    try:
        os.killpg(process.pid, signal.SIGTERM)
    except (ProcessLookupError, PermissionError):
        pass
    try:
        process.wait(timeout=grace)
    except subprocess.TimeoutExpired:
        pass
    # Whatever is still in the group (a game JVM that ignored TERM) goes now; the leader may already be gone.
    try:
        os.killpg(process.pid, signal.SIGKILL)
    except (ProcessLookupError, PermissionError):
        pass
    process.wait()


def toplevel(runner, path):
    result = runner.git(path, 'rev-parse', '--show-toplevel', writes=False, check=False)
    return Path(result.stdout.strip()).resolve() if result.returncode == 0 else None


def common_dir(runner, path):
    text = runner.git(path, 'rev-parse', '--git-common-dir', writes=False).stdout.strip()
    directory = Path(text)
    return (directory if directory.is_absolute() else path / directory).resolve()


def registered_worktrees(runner, repo):
    text = runner.git(repo, 'worktree', 'list', '--porcelain', writes=False).stdout
    return {Path(line[len('worktree '):]).resolve() for line in text.splitlines() if line.startswith('worktree ')}


def own_worktree(runner, repo, path, what):
    """path must be the top of a worktree of this repository: never the main checkout, never a folder in it."""
    if path.resolve() == repo.resolve():
        raise NightlyError(f'{what} {path} is the main checkout itself')
    if repo.resolve() in path.resolve().parents:
        raise NightlyError(f'{what} {path} is inside the main checkout; put it beside it')
    if not path.exists():
        if path.resolve() in registered_worktrees(runner, repo):
            # Deleted by hand but still registered: git refuses to add it again until the stale entry goes.
            runner.git(repo, 'worktree', 'prune')
        return False
    if toplevel(runner, path) != path.resolve() or common_dir(runner, path) != common_dir(runner, repo):
        raise NightlyError(f'{what} {path} exists but is not a worktree of {repo}; move it away or pass another --work')
    return True


def prepare_worktree(runner, repo, work, sha):
    if own_worktree(runner, repo, work, 'the nightly worktree'):
        runner.git(work, 'checkout', '--quiet', '--force', '--detach', sha)
        # Yesterday's build outputs, rundirs and links are not evidence about today's commit.
        runner.git(work, 'clean', '-ffdxq')
    else:
        runner.git(repo, 'worktree', 'add', '--quiet', '--detach', str(work), sha)


def link_fixtures(runner, repo, work):
    """Link each fixture the main checkout has; return the ones it does not have."""
    missing = []
    for relative in FIXTURES:
        source, target = repo / relative, work / relative
        if not source.exists():
            missing.append(relative)
            continue
        if target.is_symlink():
            if Path(os.readlink(target)) == source:
                continue
            runner.unlink(target)
        elif target.exists():
            raise NightlyError(f'{target} is a real file or directory in the tested commit; refusing to replace it with a link')
        runner.symlink(source, target)
    return missing


