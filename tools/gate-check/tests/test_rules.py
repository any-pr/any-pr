"""Rule-table tests for the gate mirror.

Run from the repository root:

    python -m unittest discover -s tools/gate-check/tests

The regular expressions in preflight.py are copies of the ones in
.github/workflows/auto-merge.yml. If one of them drifts, the mirror starts
disagreeing with the gate, and the disagreement is invisible until a PR is
closed. These tests pin the behaviour of both the pattern and the mirror.
"""

import importlib.util
import os
import re
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
SPEC = importlib.util.spec_from_file_location(
    "preflight", os.path.join(HERE, os.pardir, "preflight.py"))
assert SPEC and SPEC.loader, "preflight.py not found next to tests/"
preflight = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(preflight)


def matches(pattern, path):
    """Apply a rule the way the workflow does: case-insensitively."""
    return bool(re.search(pattern, path, re.I))


class ProtectedTree(unittest.TestCase):
    def test_workflow_directory_is_protected(self):
        for path in (".github/workflows/auto-merge.yml", ".GitHub/CODEOWNERS", ".github"):
            self.assertTrue(matches(preflight.PROTECTED, path), path)

    def test_lookalike_paths_are_not(self):
        for path in ("github/x.yml", "docs/.github-notes.md", "src/a.py", "a.github"):
            self.assertFalse(matches(preflight.PROTECTED, path), path)


class GovernanceDocuments(unittest.TestCase):
    def test_instruments_are_caught_at_any_depth(self):
        for path in ("LICENSE", "LICENSE-MIT", "docs/licence.txt", "LICENSES/Apache-2.0",
                     "COPYING", "COPYRIGHT", "README", "README.md", "docs/README.zh-CN.md"):
            self.assertTrue(matches(preflight.DOCS, path), path)

    def test_files_merely_containing_the_word_do_not_count(self):
        for path in ("tools/readme-utils.ts", "src/license_check.py", "docs/CONTRIBUTING.md",
                     "candy/do-not-open.html", "src/readme_gen.c"):
            self.assertFalse(matches(preflight.DOCS, path), path)


class ProhibitedPaths(unittest.TestCase):
    def test_build_output_and_junk_are_caught(self):
        for path in ("a/notes~", "b/x.tmp", "c/y.bak", "d/e.log", "out/x.txt",
                     "pkg/target/debug/f", "web/dist/app.js", "__pycache__/m.pyc",
                     "node_modules/x/y.js", ".DS_Store", "Thumbs.db", "bin/tool.exe",
                     "lib/libx.so", "pkg.zip", "cache.db"):
            self.assertTrue(matches(preflight.PROHIBITED, path), path)

    def test_ordinary_code_is_not(self):
        for path in ("src/main.rs", "tools/gate-check/preflight.py", "docs/notes.md",
                     "cache_stats.py", "targets.yaml", "outlier.py"):
            self.assertFalse(matches(preflight.PROHIBITED, path), path)


class CredentialNames(unittest.TestCase):
    def test_secrets_are_caught(self):
        for path in (".env", "app/.env.local", "id_rsa", "credentials.json",
                     "deploy/key.pem", "certs/store.p12", ".npmrc"):
            self.assertTrue(matches(preflight.SECRET, path), path)

    def test_templates_are_exempt(self):
        for path in (".env.example", ".env.sample", "docs/.env.template"):
            self.assertTrue(matches(preflight.SECRET, path), path)
            self.assertTrue(matches(preflight.SECRET_OK, path), path)

    def test_ordinary_paths_are_not_secrets(self):
        for path in ("environment.py", "keymap.json", "docs/keys.md"):
            self.assertFalse(matches(preflight.SECRET, path), path)


class LineCountExemptions(unittest.TestCase):
    def test_generated_and_vendored_are_exempt(self):
        for path in ("Cargo.lock", "web/package-lock.json", "go.sum", "vendor/x/y.go",
                     "web/app.min.js", "dist/bundle.js.map", "test/__snapshots__/a.snap"):
            self.assertTrue(matches(preflight.EXCLUDED, path), path)

    def test_hand_written_files_are_counted(self):
        for path in ("src/main.rs", "docs/notes.md", "locksmith.py", "mapping.json"):
            self.assertFalse(matches(preflight.EXCLUDED, path), path)


class SecretLiterals(unittest.TestCase):
    def test_live_shaped_credentials_are_caught(self):
        for blob in ("token = ghp_" + "a" * 24, "AKIA" + "Q" * 16,
                     "-----BEGIN RSA PRIVATE KEY-----", "sk-" + "z" * 32):
            self.assertTrue(preflight.SECRET_LITERAL.search(blob), blob)

    def test_prose_is_not(self):
        for blob in ("ghp_ prefix", "AKIA placeholder", "private key goes here", "sk-learn"):
            self.assertFalse(preflight.SECRET_LITERAL.search(blob), blob)


if __name__ == "__main__":
    unittest.main()
