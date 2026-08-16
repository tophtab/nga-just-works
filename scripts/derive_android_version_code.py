#!/usr/bin/env python3

import re
import sys


ANDROID_VERSION_CODE_MAX = 2_100_000_000
VERSION_PATTERN = re.compile(
    r"^(?P<major>[0-9]+)\.(?P<minor>[0-9]+)\.(?P<patch>[0-9]+)$"
)


def derive_android_version_code(version: str, build_slot: int) -> int:
    match = VERSION_PATTERN.fullmatch(version)
    if match is None:
        raise ValueError("version must match X.Y.Z using decimal numeric fields")

    major = int(match.group("major"))
    minor = int(match.group("minor"))
    patch = int(match.group("patch"))
    if not 0 <= minor <= 99:
        raise ValueError("minor version must be in the range 0..99")
    if not 0 <= patch <= 99:
        raise ValueError("patch version must be in the range 0..99")
    if not 0 <= build_slot <= 999:
        raise ValueError("build slot must be in the range 0..999")

    version_code = (
        major * 10_000_000
        + minor * 100_000
        + patch * 1_000
        + build_slot
    )
    if not 1 <= version_code <= ANDROID_VERSION_CODE_MAX:
        raise ValueError(
            "derived versionCode must be in Android's supported range "
            f"1..{ANDROID_VERSION_CODE_MAX}: {version_code}"
        )
    return version_code


def parse_build_slot(value: str) -> int:
    if re.fullmatch(r"[0-9]+", value) is None:
        raise ValueError("build slot must be a decimal integer")
    return int(value)


def main(arguments: list[str]) -> int:
    if len(arguments) != 2:
        print(
            "usage: derive_android_version_code.py <X.Y.Z> <build-slot>",
            file=sys.stderr,
        )
        return 2

    version, build_slot_value = arguments
    try:
        build_slot = parse_build_slot(build_slot_value)
        version_code = derive_android_version_code(version, build_slot)
    except ValueError as error:
        print(f"Cannot derive Android versionCode: {error}", file=sys.stderr)
        return 1

    print(version_code)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
