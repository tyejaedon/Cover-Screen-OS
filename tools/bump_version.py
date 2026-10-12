"""Set the app version and bump versionCode.

Usage: python3 tools/bump_version.py 0.1.0-beta.1
"""

import re
import sys
from pathlib import Path

GRADLE_FILE = Path(__file__).resolve().parent.parent / "app" / "build.gradle.kts"
VERSION_PATTERN = re.compile(r"^\d+\.\d+\.\d+(-(alpha|beta|rc)\.\d+)?$")
VERSION_CODE_PATTERN = re.compile(r"versionCode = (\d+)")
VERSION_NAME_PATTERN = re.compile(r'versionName = "[^"]*"')


def bump(text: str, version: str) -> str:
    if not VERSION_PATTERN.match(version):
        raise ValueError(
            f"Bad version '{version}'. Use X.Y.Z or X.Y.Z-beta.N (for example 0.1.0-beta.1)."
        )
    code_match = VERSION_CODE_PATTERN.search(text)
    if not code_match:
        raise ValueError("versionCode not found in app/build.gradle.kts")
    new_code = int(code_match.group(1)) + 1
    text = VERSION_CODE_PATTERN.sub(f"versionCode = {new_code}", text, count=1)
    return VERSION_NAME_PATTERN.sub(f'versionName = "{version}"', text, count=1)


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print(__doc__)
        return 2
    version = argv[1]
    try:
        new_text = bump(GRADLE_FILE.read_text(), version)
    except ValueError as error:
        print(error)
        return 1
    GRADLE_FILE.write_text(new_text)
    print(f"Set versionName to {version} in {GRADLE_FILE.name}")
    print(f"Next: commit, merge, then push tag v{version}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
