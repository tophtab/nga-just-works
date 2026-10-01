#!/usr/bin/env python3

import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from scripts.validate_release_notes import validate_release_notes


REPOSITORY_ROOT = Path(__file__).resolve().parent.parent
VALIDATOR = REPOSITORY_ROOT / "scripts" / "validate_release_notes.py"
VALID_NOTES = """## 新增

- 新功能

## 删除

- 无

## 修复

- 修复问题
"""


class ValidateReleaseNotesTest(unittest.TestCase):
    def run_validator(self, path: Path) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [sys.executable, str(VALIDATOR), str(path)],
            check=False,
            capture_output=True,
            text=True,
        )

    def test_committed_release_notes_are_valid(self) -> None:
        version = "4.10.0"
        with self.subTest(version=version):
            result = self.run_validator(REPOSITORY_ROOT / "release-notes" / f"{version}.md")
            self.assertEqual(0, result.returncode, result.stderr)

    def test_missing_notes_are_rejected(self) -> None:
        result = self.run_validator(REPOSITORY_ROOT / "release-notes" / "missing.md")

        self.assertNotEqual(0, result.returncode)
        self.assertIn("cannot read release notes", result.stderr)

    def test_invalid_section_structures_are_rejected(self) -> None:
        invalid_notes = {
            "duplicate": VALID_NOTES + "\n## 修复\n\n- 重复\n",
            "indented-duplicate": VALID_NOTES.replace(
                "## 删除", " ## 新增\n\n- 重复\n\n## 删除", 1
            ),
            "out-of-order": VALID_NOTES.replace("## 新增", "## 临时", 1)
            .replace("## 删除", "## 新增", 1)
            .replace("## 临时", "## 删除", 1),
            "blank": VALID_NOTES.replace("## 删除\n\n- 无", "## 删除\n"),
            "empty-list-item": VALID_NOTES.replace("- 新功能", "- ", 1),
            "indented-code-list": VALID_NOTES.replace("- 新功能", "    - 新功能", 1),
            "fenced-code-list": VALID_NOTES.replace(
                "- 新功能", "```text\n- 新功能\n```", 1
            ),
            "malformed": VALID_NOTES.replace("## 新增", "##新增", 1),
        }

        with tempfile.TemporaryDirectory() as temporary_directory:
            for name, content in invalid_notes.items():
                with self.subTest(case=name):
                    path = Path(temporary_directory) / f"{name}.md"
                    path.write_text(content, encoding="utf-8")
                    with self.assertRaises(ValueError):
                        validate_release_notes(path)


if __name__ == "__main__":
    unittest.main()
