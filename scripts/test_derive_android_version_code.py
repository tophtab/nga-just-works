#!/usr/bin/env python3

import subprocess
import sys
import unittest
from pathlib import Path

from scripts.derive_android_version_code import derive_android_version_code


REPOSITORY_ROOT = Path(__file__).resolve().parent.parent
DERIVER = REPOSITORY_ROOT / "scripts" / "derive_android_version_code.py"


class DeriveAndroidVersionCodeTest(unittest.TestCase):
    def run_deriver(self, version: str, build_slot: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [sys.executable, str(DERIVER), version, build_slot],
            check=False,
            capture_output=True,
            text=True,
        )

    def test_semantic_stable_bases(self) -> None:
        expected_codes = {
            "5.5.0": 50_500_000,
            "5.5.1": 50_501_000,
            "5.6.0": 50_600_000,
        }

        for version, expected_code in expected_codes.items():
            with self.subTest(version=version):
                self.assertEqual(expected_code, derive_android_version_code(version, 0))

    def test_preview_slots_use_the_stable_semantic_prefix(self) -> None:
        self.assertEqual(50_500_001, derive_android_version_code("5.5.0", 1))
        self.assertEqual(50_600_027, derive_android_version_code("5.6.0", 27))
        self.assertEqual(51_012_345, derive_android_version_code("5.10.12", 345))

    def test_preview_range_orders_before_the_next_patch(self) -> None:
        last_preview = derive_android_version_code("5.5.0", 999)
        next_patch = derive_android_version_code("5.5.1", 0)

        self.assertEqual(50_500_999, last_preview)
        self.assertEqual(last_preview + 1, next_patch)
        self.assertGreater(derive_android_version_code("5.5.0", 1), 4069)

    def test_two_digit_minor_and_patch_fields_are_supported(self) -> None:
        self.assertEqual(59_999_999, derive_android_version_code("5.99.99", 999))

    def test_invalid_versions_and_field_overflow_are_rejected(self) -> None:
        invalid_versions = ("5.5", "v5.5.0", "5.5.0-debug.1", "5.-1.0")
        for version in invalid_versions:
            with self.subTest(version=version):
                with self.assertRaises(ValueError):
                    derive_android_version_code(version, 0)

        for version in ("5.100.0", "5.0.100"):
            with self.subTest(version=version):
                with self.assertRaises(ValueError):
                    derive_android_version_code(version, 0)

        for build_slot in (-1, 1000):
            with self.subTest(build_slot=build_slot):
                with self.assertRaises(ValueError):
                    derive_android_version_code("5.5.0", build_slot)

    def test_android_version_code_range_is_enforced(self) -> None:
        self.assertEqual(2_100_000_000, derive_android_version_code("210.0.0", 0))

        for version, build_slot in (("0.0.0", 0), ("210.0.0", 1), ("211.0.0", 0)):
            with self.subTest(version=version, build_slot=build_slot):
                with self.assertRaises(ValueError):
                    derive_android_version_code(version, build_slot)

    def test_cli_prints_only_the_derived_code(self) -> None:
        result = self.run_deriver("5.10.12", "345")

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("51012345\n", result.stdout)
        self.assertEqual("", result.stderr)

    def test_cli_reports_invalid_build_slots(self) -> None:
        result = self.run_deriver("5.5.0", "not-a-number")

        self.assertEqual(1, result.returncode)
        self.assertIn("build slot must be a decimal integer", result.stderr)


if __name__ == "__main__":
    unittest.main()
