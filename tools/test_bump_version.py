"""Tests for tools/bump_version.py. Run: python3 -m unittest discover -s tools"""

import unittest

from bump_version import bump

SAMPLE = '''        versionCode = 2
        versionName = "1.0.0-beta.1"
'''


class BumpVersionTest(unittest.TestCase):
    def test_sets_name_and_increments_code(self):
        result = bump(SAMPLE, "0.1.0-beta.1")
        self.assertIn('versionName = "0.1.0-beta.1"', result)
        self.assertIn("versionCode = 3", result)

    def test_rejects_bad_version(self):
        for bad in ["1.0", "v1.0.0", "1.0.0-foo", "1.0.0-beta"]:
            with self.subTest(version=bad):
                with self.assertRaises(ValueError):
                    bump(SAMPLE, bad)

    def test_accepts_plain_and_beta_versions(self):
        for good in ["1.2.3", "1.2.3-rc.1", "0.1.0-beta.2"]:
            with self.subTest(version=good):
                self.assertIn(f'versionName = "{good}"', bump(SAMPLE, good))


if __name__ == "__main__":
    unittest.main()
