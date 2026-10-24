#!/usr/bin/env python3
"""Read Gradle's JUnit XML: publish what ran and what skipped, and hold skips to a committed baseline.

Python standard library only, like dev.py.

  summary  prints Markdown (CI appends it to $GITHUB_STEP_SUMMARY): per suite tests, executed, skipped,
           failed, the suites that did not execute, and the most common skip reasons.
  ratchet  compares the skipped test ids with a committed baseline. A skip that is not in the baseline fails,
           and so does a baseline line that no longer skips, so every change in what is skipped is a diff
           line in the same commit. A suite that did not execute counts as one line of its own.

A JUnit skip passes Gradle's build. Without these two, a test that quietly started skipping looks exactly
like one that passed.
"""
import argparse
from collections import Counter
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

NOT_EXECUTED = ('*', '*', 'suite did not execute')
CLASS_LEVEL = 'skipped without a message (a class-level abort, e.g. in @BeforeAll)'
JUNIT_PREFIX = 'Assumption failed: '
HEADER = ('# Skipped tests this CI profile is allowed to have: suite<TAB>class<TAB>test<TAB>reason.\n'
          '# Compared on the first three columns; the reason is for the reader.\n'
          '# Regenerate: ./gradlew check -Pforbric.skipBaseline={profile} -Pforbric.writeSkipBaseline\n'
          '# or copy skips-actual-{profile}.tsv from the CI artifact.\n')


def normalize(message, root=None, home=None):
    """One line, without JUnit's prefix and without machine-specific paths."""
    text = (message or '').strip()
    if text.startswith(JUNIT_PREFIX):
        text = text[len(JUNIT_PREFIX):]
    for path, name in ((root, '$ROOT'), (home, '~')):
        if path:
            text = text.replace(str(path).rstrip('/\\'), name)
    text = re.sub(r'\s+', ' ', text).strip()
    return text[:200] if text else CLASS_LEVEL


def read_suite(directory):
    """Totals plus skipped and failed testcases of one Gradle test task, or None when it left no XML."""
    directory = Path(directory)
    files = sorted(directory.glob('TEST-*.xml')) if directory.is_dir() else []
    if not files:
        return None
    totals = Counter()
    skipped, failed = [], []
    for file in files:
        root = ET.parse(file).getroot()
        for key in ('tests', 'skipped', 'failures', 'errors'):
            totals[key] += int(root.get(key, 0) or 0)
        for case in root.iter('testcase'):
            ident = (case.get('classname', ''), case.get('name', ''))
            skip = case.find('skipped')
            if skip is not None:
                skipped.append(ident + (skip.get('message', ''),))
            for kind in ('failure', 'error'):
                bad = case.find(kind)
                if bad is not None:
                    failed.append(ident + (bad.get('type', kind),))
    return {'totals': totals, 'skipped': skipped, 'failed': failed, 'files': len(files)}


def actual_lines(results, suites, executed, root=None, home=None):
    """Sorted baseline rows (suite, class, test, reason) for this run."""
    rows = set()
    for suite in suites:
        data = read_suite(Path(results) / suite) if suite in executed else None
        if data is None:
            rows.add((suite,) + NOT_EXECUTED)
            continue
        for classname, name, message in data['skipped']:
            rows.add((suite, classname, name, normalize(message, root, home)))
    return sorted(rows)


def read_baseline(path):
    rows = []
    for raw in Path(path).read_text(encoding='utf-8').splitlines():
        if not raw.strip() or raw.startswith('#'):
            continue
        parts = raw.split('\t')
        if len(parts) < 3:
            raise ValueError(f'{path}: malformed line {raw!r}')
        rows.append(tuple(parts[:3]) + ('\t'.join(parts[3:]),))
    return rows


def write_lines(path, rows, profile):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    body = ''.join('\t'.join(row) + '\n' for row in rows)
    path.write_text(HEADER.format(profile=profile) + body, encoding='utf-8', newline='\n')


def ratchet(actual, baseline):
    """(new, stale): skips the baseline does not allow, and allowed skips that no longer happen."""
    key = lambda row: row[:3]
    allowed, seen = {key(r) for r in baseline}, {key(r) for r in actual}
    new = [r for r in actual if key(r) not in allowed]
    stale = [r for r in baseline if key(r) not in seen]
    return new, stale


def render_summary(title, results, suites, root=None, home=None, baseline=None, top=15):
    lines = [f'### {title}', '', '| suite | tests | executed | skipped | skip % | failed |', '| --- | ---: | ---: | ---: | ---: | ---: |']
    missing, reasons, failed = [], Counter(), []
    actual = []
    for suite in suites:
        data = read_suite(Path(results) / suite)
        if data is None:
            missing.append(suite)
            actual.append((suite,) + NOT_EXECUTED)
            continue
        t = data['totals']
        bad = t['failures'] + t['errors']
        executed = t['tests'] - t['skipped']
        share = f"{100.0 * t['skipped'] / t['tests']:.1f}" if t['tests'] else '0.0'
        lines.append(f"| {suite} | {t['tests']} | {executed} | {t['skipped']} | {share} | {bad} |")
        for classname, name, message in data['skipped']:
            reason = normalize(message, root, home)
            reasons[reason] += 1
            actual.append((suite, classname, name, reason))
        failed += [(suite, c, n, kind) for c, n, kind in data['failed']]
    lines.append('')
    if missing:
        lines.append('Did not execute (no results): ' + ', '.join(f'`{s}`' for s in missing))
        lines.append('')
    if failed:
        lines.append(f'**Failed ({len(failed)}):**')
        lines += [f'- `{s}` {c}.{n} ({kind})' for s, c, n, kind in failed[:50]]
        lines.append('')
    if baseline is not None:
        try:
            new, stale = ratchet(sorted(actual), read_baseline(baseline))
            lines.append(f'Skip baseline `{Path(baseline).name}`: {len(new)} new, {len(stale)} no longer skipped'
                         + (' (unchanged)' if not new and not stale else ''))
        except FileNotFoundError:
            lines.append(f'Skip baseline `{Path(baseline).name}`: missing')
        lines.append('')
    if reasons:
        lines += [f'<details><summary>Top {min(top, len(reasons))} of {len(reasons)} skip reasons</summary>', '',
                  '| tests | reason |', '| ---: | --- |']
        for reason, count in reasons.most_common(top):
            lines.append(f"| {count} | {reason.replace('|', '/')} |")
        lines += ['', '</details>', '']
    return '\n'.join(lines)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest='command', required=True)
    for name in ('summary', 'ratchet'):
        sub = commands.add_parser(name)
        sub.add_argument('--results', required=True, help='build/test-results; one directory per suite under it')
        sub.add_argument('--suites', default='test,transferTest')
        sub.add_argument('--root', help='checkout path, replaced by $ROOT in reasons')
        sub.add_argument('--baseline', help='committed skip baseline (.tsv)')
    summary = commands.choices['summary']
    summary.add_argument('--title', default='Tests')
    summary.add_argument('--top', type=int, default=15)
    gate = commands.choices['ratchet']
    gate.add_argument('--profile', required=True)
    gate.add_argument('--executed', default='', help='suites that ran in this invocation; others count as not executed')
    gate.add_argument('--actual', required=True, help='where to write this run\'s skips')
    gate.add_argument('--write', action='store_true', help='make this run the baseline')
    args = parser.parse_args(argv)
    suites = [s for s in args.suites.split(',') if s]
    home = str(Path.home())

    if args.command == 'summary':
        print(render_summary(args.title, args.results, suites, args.root, home, args.baseline, args.top))
        return 0

    if not re.fullmatch(r'[a-z0-9-]+', args.profile):
        parser.error(f'profile must be [a-z0-9-]+: {args.profile!r}')
    if not args.baseline:
        parser.error('ratchet needs --baseline')
    executed = {s for s in args.executed.split(',') if s}
    actual = actual_lines(args.results, suites, executed, args.root, home)
    write_lines(args.actual, actual, args.profile)
    if args.write:
        write_lines(args.baseline, actual, args.profile)
        print(f'skip baseline {args.baseline} rewritten: {len(actual)} line(s)')
        return 0
    if not Path(args.baseline).is_file():
        print(f'skip baseline {args.baseline} does not exist; this run skipped {len(actual)} line(s), written to '
              f'{args.actual}. Commit that file as the baseline, or rerun with -Pforbric.writeSkipBaseline.')
        return 1
    new, stale = ratchet(actual, read_baseline(args.baseline))
    if not new and not stale:
        print(f'skip baseline {Path(args.baseline).name}: {len(actual)} line(s), unchanged')
        return 0
    print(f'skip baseline {Path(args.baseline).name} no longer matches: {len(new)} new skip(s), '
          f'{len(stale)} line(s) that no longer skip.')
    for row in new:
        print('+ ' + '\t'.join(row))
    for row in stale:
        print('- ' + '\t'.join(row))
    print(f'A new skip hides a test that used to run; fix it or explain it by adding its line. A "-" line is '
          f'progress: delete it. This run\'s full list is {args.actual}.')
    return 1


if __name__ == '__main__':
    sys.exit(main())
