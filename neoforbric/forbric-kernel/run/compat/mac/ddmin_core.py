"""Delta debugging for a failing mod pack: the pure part, with the game behind an oracle.

ddmin() is Zeller and Hildebrandt's ddmin2 ("Simplifying and Isolating Failure-Inducing Input", 2002). An oracle
maps a configuration (a list of jar names) to FAIL, PASS or UNRESOLVED. Only FAIL reduces: a run that broke some
other way says nothing about the failure being minimised, so treating it as either verdict would let the search
walk off to a different bug. The game-running driver (ddmin.py) is a later step; nothing here reads the
environment, the filesystem or a clock.
"""
from dataclasses import dataclass, field
import json
import re

FAIL, PASS, UNRESOLVED = 'FAIL', 'PASS', 'UNRESOLVED'
OUTCOMES = (FAIL, PASS, UNRESOLVED)
# CompatibilityLaunchBoundary.POLICY_STOP: the kernel refused the launch; there is no exception to read.
POLICY_STOP = 78


class NotReproduced(Exception):
    """The configuration minimisation starts from does not FAIL, so there is nothing to minimise."""

    def __init__(self, verdict):
        super().__init__(f'starting configuration is {verdict}, not {FAIL}')
        self.outcome = verdict


def _unique(items):
    return list(dict.fromkeys(items))


def _memo(oracle):
    seen = {}

    def test(config):
        key = frozenset(config)
        if key not in seen:
            verdict = oracle(list(config))
            if verdict not in OUTCOMES:
                raise ValueError(f'oracle returned {verdict!r}; expected one of {OUTCOMES}')
            seen[key] = verdict
        return seen[key]
    return test


def _split(items, n):
    """n contiguous parts whose sizes differ by at most one."""
    n = min(n, len(items))
    size, extra = divmod(len(items), n)
    parts, start = [], 0
    for i in range(n):
        end = start + size + (1 if i < extra else 0)
        parts.append(items[start:end])
        start = end
    return parts


def ddmin(items, oracle, first=None):
    """A 1-minimal failing subset of items.

    `first`, when given, is tried before the whole set; if it FAILs the search starts from it (in its order), which
    is how a crash report's suspects save the halvings that would otherwise find them. The empty configuration is
    never tested. Raises NotReproduced when neither starting point FAILs.
    """
    test = _memo(oracle)
    current = None
    if first is not None:
        allowed = set(items)
        start = [item for item in _unique(first) if item in allowed]
        if start and test(start) == FAIL:
            current = start
    if current is None:
        current = _unique(items)
        verdict = test(current)
        if verdict != FAIL:
            raise NotReproduced(verdict)
    n = 2
    while len(current) >= 2:
        parts = _split(current, n)
        reduced = next((part for part in parts if test(part) == FAIL), None)
        if reduced is not None:
            current, n = reduced, 2
            continue
        # With two parts each complement is the other part, already tested above.
        complements = [] if len(parts) == 2 else \
            [[item for j, part in enumerate(parts) if j != i for item in part] for i in range(len(parts))]
        reduced = next((complement for complement in complements if test(complement) == FAIL), None)
        if reduced is not None:
            current, n = reduced, max(n - 1, 2)
            continue
        if n >= len(current):
            break
        n = min(2 * n, len(current))
    return current


def one_minimal(items, oracle):
    """Remove single items while the rest still FAILs, until no single removal does.

    Linear in the result, so it suits a set that is already small. Unlike ddmin it does try the empty
    configuration, so a failure that needs none of the items comes back as [].
    """
    test = _memo(oracle)
    current = _unique(items)
    verdict = test(current)
    if verdict != FAIL:
        raise NotReproduced(verdict)
    changed = True
    while changed:
        changed = False
        for item in list(current):
            candidate = [other for other in current if other != item]
            if test(candidate) == FAIL:
                current, changed = candidate, True
    return current


def closed(subset, closure):
    """subset plus every jar it needs, transitively; closure maps a jar to its dependency jars (closure.json).

    Dependencies are added to every configuration that is run and are not minimised: a mod tested without its
    required library fails for a reason that has nothing to do with the pack. A jar that is both a candidate and
    someone's dependency can leave the candidates, but comes back with whoever needs it.
    """
    out = _unique(subset)
    present = set(out)
    added, stack = set(), [dep for item in out for dep in closure.get(item, ())]
    while stack:
        dep = stack.pop()
        if dep in present or dep in added:
            continue
        added.add(dep)
        stack.extend(closure.get(dep, ()))
    return out + sorted(added)


def outcome(reference, observed, passed=False):
    """The verdict on one run, given the reference failure's signature and this run's."""
    if passed:
        return PASS
    return FAIL if observed == reference else UNRESOLVED


# --- failure signature --------------------------------------------------------------------------------------------

# A throwable's header line as Throwable.toString prints it; the simple name must look like a throwable, so a
# "Description: ..." or "Time: ..." line in the crash report preamble is never taken for one.
_HEADER = re.compile(r'^((?:[A-Za-z_$][\w$]*\.)+[\w$]*(?:Exception|Error|Throwable)[\w$]*)(?::\s?(.*))?$')
_UNCAUGHT = re.compile(r'Exception in thread "[^"]*" (.*)$')
_CRASH_HEADER = '---- Minecraft Crash Report ----'
# CrashAttribution.CLASHING, same separators.
_SOURCES = re.compile(r'Sources?: ([A-Za-z0-9_\-]+(?:(?:, and |, ?| and )[A-Za-z0-9_\-]+)+)')
_SOURCE_SPLIT = re.compile(r', and |, ?| and ')
_TIMESTAMP = re.compile(r'\d{4}-\d{2}-\d{2}(?:[T _]\d{2}[:.]\d{2}[:.]\d{2}(?:[.,]\d+)?Z?)?|\b\d{1,2}:\d{2}:\d{2}(?:[.,]\d+)?\b')
_PATH = re.compile(r'file:/+[^\s\'"<>\0]*|\b[A-Za-z]:[\\/][^\s\'"<>|\0]*|(?<![\w.$/\-])/[^\s\'"<>()\[\]{},;\0]+')
_HEX = re.compile(r'\b[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}\b|0x[0-9A-Fa-f]+|@[0-9A-Fa-f]{4,}\b|\b(?=[0-9a-fA-F]*[a-fA-F])(?=[0-9a-fA-F]*\d)[0-9a-fA-F]{8,}\b')
# The prefix Mixin allocates a merged handler (handler$zca000$sodium$postInit) depends on what else was loaded,
# so it changes with the very subset being tested.
_MIXIN_PREFIX = re.compile(r'\$[a-z]{3}\d{3}\$')
_DIGITS = re.compile(r'\d+')
_SUSPECT = re.compile(r'^  (?! )(.+?)  \(([^()\s]+)\)\s*$')
_FRAME_JAR = re.compile(r'\[([^\[\]:]+\.jar):[^\[\]]*\]')


def _chain(crash_text):
    """The exception and its causes: everything above the crash report's walkthrough divider."""
    divider = crash_text.find('\n------------')
    return crash_text if divider < 0 else crash_text[:divider]


def _top_exception(text):
    for line in text.splitlines():
        match = _HEADER.match(line.rstrip())
        if match:
            return match.group(1), match.group(2)
    return None


def normalise(message):
    """A message with what differs between two runs of the same failure taken out.

    The ids in a 'Sources: a and b' list are mod ids, which do not vary between runs, so they are sorted and kept
    whole; everything else loses timestamps, paths, Mixin handler prefixes, hex and then every digit.
    """
    sources = []

    def hold(match):
        sources.append('Sources: ' + ' and '.join(sorted(_SOURCE_SPLIT.split(match.group(1)))))
        return '\0'
    text = _SOURCES.sub(hold, message.replace('\0', ''))
    text = _TIMESTAMP.sub('<time>', text)
    text = _PATH.sub('<path>', text)
    text = _MIXIN_PREFIX.sub('$<mixin>$', text)
    text = _HEX.sub('<hex>', text)
    text = _DIGITS.sub('#', text)
    for held in sources:
        text = text.replace('\0', held, 1)
    return ' '.join(text.split())


def _policy_keys(report_json_text):
    try:
        report = json.loads(report_json_text or '{}')
    except ValueError:
        return []
    # CompatibilityFinding.key() of each finding the report counts in confirmedRequired.
    return sorted({f'{finding.get("modId")}:{finding.get("id")}' for finding in report.get('findings', [])
                   if finding.get('confidence') == 'CONFIRMED' and finding.get('required') is True})


def signature(console_text='', crash_text='', report_json_text='', exit_code=None):
    """One line naming a failure, equal for two runs that failed the same way.

    A policy stop is named by the findings that stopped it. Otherwise it is the top exception of the crash
    report, or of the crash report Minecraft also prints to the console, or of an exception that escaped a
    thread; failing all of those, the exit code.
    """
    if exit_code == POLICY_STOP:
        return 'POLICY_STOP:' + ','.join(_policy_keys(report_json_text))
    console_text = console_text or ''
    candidates = [_chain(crash_text or '')]
    crash_at = console_text.find(_CRASH_HEADER)
    if crash_at >= 0:
        candidates.append(_chain(console_text[crash_at:]))
    candidates += [match.group(1) for line in console_text.splitlines() if (match := _UNCAUGHT.search(line))]
    for text in candidates:
        top = _top_exception(text)
        if top:
            name, message = top
            message = normalise(message or '')
            return f'{name}: {message}' if message else name
    return f'EXIT:{exit_code}'


# --- seeds --------------------------------------------------------------------------------------------------------

def _rows(report_json_text):
    try:
        return json.loads(report_json_text or '{}').get('mods', [])
    except ValueError:
        return []


def _jar_of(row, rows, jars):
    """The installed jar a report row belongs to, following bundledBy out of a nested jar."""
    seen = set()
    while row is not None and id(row) not in seen:
        seen.add(id(row))
        if row.get('jar') in jars:
            return row['jar']
        host = row.get('bundledBy')
        row = next((other for other in rows if host and other.get('modId') == host), None)
    return None


def seeds(crash_text='', analysis_text='', report_json_text='', jars=()):
    """Installed jars the failure's own evidence points at, strongest evidence first.

    In order: the mods an error names as clashing ('Sources: a and b'), the suspects crash-analysis.txt lists, jars
    in the exception chain's frames, then every jar whose report row is not OK. A mod id becomes a jar only through
    the report's mods[] rows, never by reading it out of a file name (CrashAttribution's rule). Below the crash
    report's divider every installed mod is listed, so only the chain above it is read.
    """
    jars = set(jars)
    rows = _rows(report_json_text)
    found = []

    def by_id(mod_id):
        row = next((row for row in rows if str(row.get('modId', '')).lower() == mod_id.lower()), None)
        jar = _jar_of(row, rows, jars)
        if jar:
            found.append(jar)
    chain = _chain(crash_text or '')
    for line in chain.splitlines():
        if not line.startswith('\tat '):
            for match in _SOURCES.finditer(line):
                for mod_id in _SOURCE_SPLIT.split(match.group(1)):
                    by_id(mod_id.strip())
    for line in (analysis_text or '').splitlines():
        match = _SUSPECT.match(line)
        if match:
            by_id(match.group(2))
    for match in _FRAME_JAR.finditer(chain):
        name = match.group(1)
        if name in jars:
            found.append(name)
        else:
            jar = _jar_of(next((row for row in rows if row.get('jar') == name), None), rows, jars)
            if jar:
                found.append(jar)
    for row in rows:
        if row.get('status') != 'OK':
            jar = _jar_of(row, rows, jars)
            if jar:
                found.append(jar)
    return _unique(found)


# --- runner -------------------------------------------------------------------------------------------------------

@dataclass
