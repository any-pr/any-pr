"""The developer docs describe CI with the command CI actually runs."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]


def kernel_command(workflow):
    """The command the kernel job's 'Build and check' step runs inside forbric-kernel/."""
    match = re.search(r'- name: Build and check[^\n]*\n(?:[ \t]+[^\n]*\n)*?[ \t]+run: cd forbric-kernel && ([^\n]+)\n', workflow)
    if not match:
        raise AssertionError("build.yml has no 'Build and check' step running in forbric-kernel")
    return match.group(1).strip()


def ci_bullet(document):
    """The '- **CI**' bullet of the testing section, with its continuation lines joined."""
    match = re.search(r'^- \*\*CI\*\*.*?(?=^\S|^- |\Z)', document, re.M | re.S)
    if not match:
        raise AssertionError('no "- **CI**" bullet')
    return ' '.join(match.group(0).split())


class CiContractTest(unittest.TestCase):
    def test_docs_name_the_command_the_kernel_job_runs(self):
        command = kernel_command((ROOT / '.github/workflows/build.yml').read_text(encoding='utf-8'))
        for name in ('introduction.md', 'introduction.zh-CN.md'):
            with self.subTest(document=name):
                bullet = ci_bullet((ROOT / name).read_text(encoding='utf-8'))
                self.assertIn(f'`{command}`', bullet, f'{name} describes CI with a command build.yml does not run')

    def test_the_contract_can_tell_a_stale_description(self):
        workflow = ('      - name: Build and check (boot only)\n        shell: bash\n'
                    '        run: cd forbric-kernel && ./gradlew build -Pforbric.skipBaseline=ci-unstaged\n')
        command = kernel_command(workflow)
        self.assertEqual(command, './gradlew build -Pforbric.skipBaseline=ci-unstaged')
        stale = '- **CI** (`build.yml`): job `kernel` runs `./gradlew jar test` in\n  `forbric-kernel/`.\n\n## 17.'
        self.assertNotIn(f'`{command}`', ci_bullet(stale))


if __name__ == '__main__':
    unittest.main()
