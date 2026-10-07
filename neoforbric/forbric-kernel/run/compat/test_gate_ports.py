"""Every gate binds a port the scheduler chose, and says so when it did not get it.

gates-parallel.py hands each concurrent slot its own port block, because two servers on one port is a failure
that does not name itself: the loser prints "FAILED TO BIND TO PORT", writes a crash report and then still prints
"Stopping server" and "All dimensions are saved", so the gate fails on whatever needed a running server and reads
as a kernel regression. Three things keep that from happening, and each is a rule here because each one was
broken in a checked-in gate before it was a rule:

  * a *_PORT knob a gate reads from the environment is one the scheduler exports (PORT_VARS). A knob it does not
    export is never set, the gate keeps its own literal, and on the slot where that literal meets an exported one
    two gates share a port. gate-m32 read M32_PORT, which nothing exported, and fell back to slot 10's M16_PORT.
  * no gate writes its port into server.properties as a literal. gate-m36, gate-m37 and gate-m38 did, so a second
    sweep started with its own GATE_PORT still met them on the same three ports.
  * every gate that starts a server checks that server's log for a lost port (lib.sh's port_was_free, which
    await_server runs), so the next collision is named instead of read as the kernel failing to boot -- and a gate
    that gives up on its server early ("never reached Done", then exit) names it before it leaves, because a server
    that lost its port is exactly the one that never reaches Done. gate-m12 to gate-m16 left without it.

The gates are read as text, the way the scheduler reads them: through gates-parallel.py's own Gate class, so a
gate the scheduler would parse differently is parsed differently here too. Text cannot prove that every path
through a gate reaches the check; the third rule holds the call and the one early-exit idiom the gates share.
"""
import importlib.util
import re
import tempfile
import unittest
from pathlib import Path

COMPAT = Path(__file__).resolve().parent
RUN = COMPAT.parent

_spec = importlib.util.spec_from_file_location('gates_parallel', COMPAT / 'gates-parallel.py')
scheduler = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(scheduler)

# A whole-line comment is prose about a port, not a use of one.
COMMENT = re.compile(r'^\s*#.*$', re.M)
# `set -u`, so a knob that may be unset is read with a fallback; a name read bare that the gate never assigns
# can only have come from the environment too.
READ_WITH_FALLBACK = re.compile(r'\$\{([A-Za-z0-9_]+_PORT):?[-=]')
READ = re.compile(r'\$\{([A-Za-z0-9_]+_PORT)\b|\$([A-Za-z0-9_]+_PORT)\b')
# A Python heredoc inside a gate reads its knob from os.environ instead.
PY_ENV = re.compile(r'''(?:environ(?:\.get\(|\[)|getenv\()\s*['"]([A-Za-z0-9_]+_PORT)['"]''')
ASSIGNED = re.compile(r'(?:^|[\s;(])(?:export\s+|local\s+|readonly\s+)?([A-Za-z0-9_]+_PORT)=', re.M)
# A port spelled out where a server is told to bind it, or a client to dial it.
LITERAL_PORT = re.compile(r'(?:server-port=|127\.0\.0\.1:|localhost:)[0-9]+')
# What starts a dedicated server, and what checks that it got its port.
STARTS_SERVER = re.compile(r'launch-kernel-server|launch-vanilla-server|--nogui')
CHECKS_PORT = re.compile(r'\bawait_server\b|\bport_was_free\b|FAILED TO BIND TO PORT')


def gates(run_dir=None):
    """Every checked-in gate, parsed by the scheduler, with its comment lines removed."""
    found = []
    for gate in scheduler.discover(run_dir if run_dir is not None else RUN):
        found.append((gate, COMMENT.sub('', gate.source)))
    return found


def environment_knobs(code):
    """The *_PORT names a gate reads from its environment rather than computes itself."""
    read = {one or two for one, two in READ.findall(code)}
    assigned = set(ASSIGNED.findall(code))
    return sorted(set(READ_WITH_FALLBACK.findall(code)) | set(PY_ENV.findall(code)) | (read - assigned))


def unexported_knobs(run_dir=None):
    found = {gate.name: [k for k in environment_knobs(code) if k not in scheduler.PORT_VARS]
             for gate, code in gates(run_dir)}
    return {name: knobs for name, knobs in found.items() if knobs}


def literal_ports(run_dir=None):
    found = {gate.name: sorted({m.group(0) for m in LITERAL_PORT.finditer(code)}) for gate, code in gates(run_dir)}
    return {name: hits for name, hits in found.items() if hits}


def unchecked_servers(run_dir=None):
    return sorted(gate.name for gate, code in gates(run_dir)
                  if STARTS_SERVER.search(code) and not CHECKS_PORT.search(code))


GAVE_UP = re.compile(r'FAIL.*never reached Done')
EXIT = re.compile(r'\bexit\b')


def early_exits_without_port(run_dir=None):
    """Gates that report a server never reached Done and exit without asking whether it lost its port."""
    found = []
    for gate, code in gates(run_dir):
        lines = code.splitlines()
        for i, line in enumerate(lines):
            if not GAVE_UP.search(line):
                continue
            span = lines[i:i + 12]
            end = next((j for j, l in enumerate(span) if EXIT.search(l)), None)
            if end is not None and not any('port_was_free' in l for l in span[:end + 1]):
                found.append(f'{gate.name}:{i + 1}')
    return found


class GatePortTest(unittest.TestCase):
    def test_every_knob_a_gate_reads_is_one_the_scheduler_exports(self):
        self.assertEqual({}, unexported_knobs(),
                         'a gate reads a port knob the scheduler never sets, so it keeps its own literal, and on the '
                         'slot where that literal meets an exported one two gates share a port. Add the knob to '
                         'PORT_VARS in gates-parallel.py, or have the gate read GATE_PORT like the rest')

    def test_no_gate_spells_out_its_port(self):
        self.assertEqual({}, literal_ports(),
                         'a literal port is the same port in every slot and every sweep: write the knob instead '
                         '(seed_server_properties writes GATE_PORT)')

    def test_every_gate_that_starts_a_server_checks_it_got_its_port(self):
        self.assertEqual([], unchecked_servers(),
                         'a server that lost its port fails the gate for a reason the gate does not name: wait for '
                         'it with await_server, or call port_was_free on its log')

    def test_a_gate_that_gives_up_on_its_server_names_a_lost_port_first(self):
        self.assertEqual([], early_exits_without_port(),
                         'this exit reports "never reached Done" -- which is what a server that lost its port does -- '
                         'and leaves before anything reads the log for it: call port_was_free on the server log first')

    def test_the_walk_sees_the_checked_in_gates(self):
        # A wrong directory finds nothing and passes all three rules above, so check it found the tree.
        found = gates()
        self.assertGreater(len(found), 50, f'only {len(found)} gates found under {RUN}')
        self.assertGreater(sum(1 for gate, _ in found if gate.declared), 40)
        self.assertGreater(sum(1 for _, code in found if STARTS_SERVER.search(code)), 40)


class ControlTest(unittest.TestCase):
    """Each rule, shown to fire on a gate that breaks it and to stay quiet on one that does not."""

    def gate(self, run, name, body):
        (run / name).write_text('#!/usr/bin/env bash\n# GATE-PARALLEL: rundirs=probe mem=10\n' + body, encoding='utf-8')

    def test_an_unexported_knob_is_caught_and_an_exported_one_is_not(self):
        with tempfile.TemporaryDirectory(prefix='gate-ports-') as scratch:
            run = Path(scratch)
            self.gate(run, 'gate-m98-probe.sh', 'PORT="${M99_PORT:-25999}"\nGATE_PORT="${GATE_PORT:-25565}"\n')
            self.gate(run, 'gate-m99-probe.sh', 'PORT="${M16_PORT:-25604}"\n')
            self.gate(run, 'gate-m97-probe.sh', "python3 - <<'PY'\nport=os.environ['M97_PORT']\nPY\n")
            self.assertEqual({'gate-m97-probe.sh': ['M97_PORT'], 'gate-m98-probe.sh': ['M99_PORT']}, unexported_knobs(run))

    def test_a_port_the_gate_computes_is_not_a_knob(self):
        # Derived from a knob, assigned here and read bare: nothing in the environment was ever going to set it.
        with tempfile.TemporaryDirectory(prefix='gate-ports-') as scratch:
            run = Path(scratch)
            self.gate(run, 'gate-m98-probe.sh',
                      'PORT="${GATE_PORT:-25565}"\nVANILLA_PORT=$((PORT + 1))\necho "$VANILLA_PORT"\n')
            self.assertEqual({}, unexported_knobs(run))
            # ...but the same name read bare and never assigned can only be coming from the environment.
            self.gate(run, 'gate-m99-probe.sh', 'echo "$VANILLA_PORT"\n')
            self.assertEqual({'gate-m99-probe.sh': ['VANILLA_PORT']}, unexported_knobs(run))

    def test_prose_about_a_port_is_not_a_use_of_one(self):
        with tempfile.TemporaryDirectory(prefix='gate-ports-') as scratch:
            run = Path(scratch)
            self.gate(run, 'gate-m98-probe.sh', '# it used to read ${M99_PORT:-25805} and write server-port=25805\n')
            self.assertEqual(({}, {}), (unexported_knobs(run), literal_ports(run)))

    def test_a_literal_port_is_caught_in_every_spelling(self):
        with tempfile.TemporaryDirectory(prefix='gate-ports-') as scratch:
            run = Path(scratch)
            self.gate(run, 'gate-m97-probe.sh', "python3 -c \"open('s','w').write('server-port=25596')\"\n")
            self.gate(run, 'gate-m98-probe.sh', 'connect 127.0.0.1:25601\n')
            self.gate(run, 'gate-m99-probe.sh', 'printf "server-port=%s" "$GATE_PORT"\nconnect "127.0.0.1:$PORT"\n')
            self.assertEqual(['gate-m97-probe.sh', 'gate-m98-probe.sh'], sorted(literal_ports(run)))

    def test_a_server_nobody_checks_is_caught(self):
        with tempfile.TemporaryDirectory(prefix='gate-ports-') as scratch:
            run = Path(scratch)
            self.gate(run, 'gate-m96-probe.sh', '"$KERNEL/run/launch-kernel-server.sh" > log &\nwait\n')
            self.gate(run, 'gate-m97-probe.sh', '"$KERNEL/run/launch-kernel-server.sh" > log &\nawait_server $! log\n')
            self.gate(run, 'gate-m98-probe.sh', '"$KERNEL/run/launch-kernel-server.sh" > log\nport_was_free log\n')
            self.gate(run, 'gate-m99-probe.sh', 'echo no server here\n')
            self.gate(run, 'gate-m95-probe.sh', '( exec java -jar paper.jar --nogui ) > log &\nwait\n')
            self.assertEqual(['gate-m95-probe.sh', 'gate-m96-probe.sh'], unchecked_servers(run))

    def test_an_early_exit_that_skips_the_port_is_caught(self):
        give_up = ('if [ "$READY" -ne 1 ]; then\n  echo "[kernel] FAIL server never reached Done"; FAIL=1\n'
                   '  kill_tree "$SRVPID"\n{check}  echo "[kernel] GATE RED"; exit 1\nfi\nawait_server "$SRVPID" "$SLOG"\n')
        with tempfile.TemporaryDirectory(prefix='gate-ports-') as scratch:
            run = Path(scratch)
            self.gate(run, 'gate-m98-probe.sh', give_up.format(check=''))
            self.gate(run, 'gate-m99-probe.sh', give_up.format(check='  port_was_free "$SLOG"\n'))
            self.assertEqual(['gate-m98-probe.sh:4'], early_exits_without_port(run))


if __name__ == '__main__':
    unittest.main()
