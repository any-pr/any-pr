"""What one client run of the macOS sweep says, read from its evidence alone.

Standard library only, and nothing here reads a file, the environment or a clock: per-mod.py, mixed.py and ddmin.py
hand in the texts and the parsed report, so a run means the same thing to all three.
"""


def classify_run(driver, console, crashes):
    """PASS (joined, clean disconnect, exit 0, frame drawn) | CRASH | STALL | STALL_IN_WORLD | NO_WORLD | NOT_DRAWN | FAIL

    driver is the driver's log, console the client's, crashes the crash reports the run left.
    """
    if crashes or 'Game crashed' in console or 'Preparing crash report' in console:
        return 'CRASH'
    if 'PASS client' in driver:
        return 'PASS'
    if 'stopped producing output' in driver:
        return 'STALL' if 'joined world via quick-play' not in console else 'STALL_IN_WORLD'
    if 'joined world via quick-play' not in console:
        return 'NO_WORLD'
    if 'drew=False' in driver:
        return 'NOT_DRAWN'
    return 'FAIL'


def mod_status(jar, report):
    """The jar's own status in compatibility-report.json: OK | DEGRADED | FAILED | ABSENT (the kernel never listed it).

    The jar's rows are those it is the file of plus those it bundles; the worst of them wins. Returns the status
    and the 'modId=STATUS' of every row that is not OK.
    """
    rows = [m for m in report.get('mods', []) if m.get('jar') == jar]
    own = {m['modId'] for m in rows}
    rows += [m for m in report.get('mods', []) if m.get('bundledBy') in own and m not in rows]
    if not rows:
        return 'ABSENT', []
    worst = 'FAILED' if any(m['status'] == 'FAILED' for m in rows) else \
            'DEGRADED' if any(m['status'] != 'OK' for m in rows) else 'OK'
    return worst, [f"{m['modId']}={m['status']}" for m in rows if m['status'] != 'OK']


def bad_rows(report):
    """Every mods[] row of the report whose status is not OK, whoever it belongs to."""
    return [row for row in report.get('mods', []) if row.get('status') != 'OK']


def missing_subjects(subjects, report):
    """The subjects whose own status is not OK, ABSENT included."""
    return [name for name in subjects if mod_status(name, report)[0] != 'OK']


def subject_strict(run, status, report, unresolved):
    """per-mod.py's strict pass: the run passed, the subject and every other row are OK, nothing is unresolved, the
    kernel listed the mods, no catalog entry failed and no confirmed required finding was recorded."""
    return (run == 'PASS' and status == 'OK' and bool(report.get('mods')) and
            not bad_rows(report) and not unresolved and not report.get('catalogFailures') and
            report.get('confirmedRequired', 0) == 0)


def pack_strict(returncode, run, report, missing, saved):
    """mixed.py's strict pass for a whole pack: the driver exited 0, the run passed, every row is OK, no subject is
    missing, the world was saved, the kernel listed the mods, no catalog entry failed and nothing confirmed required."""
    return (returncode == 0 and run == 'PASS' and bool(report.get('mods')) and not bad_rows(report) and
            not missing and saved and not report.get('catalogFailures') and report.get('confirmedRequired', 0) == 0)
