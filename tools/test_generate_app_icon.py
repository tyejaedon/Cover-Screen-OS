"""Check generated icon sizes and artwork. Requires Pillow."""

import unittest

from PIL import Image

from generate_app_icon import RES, SOURCE, artwork


class AppIconTest(unittest.TestCase):
    def test_original_source_dimensions(self):
        with Image.open(SOURCE) as image:
            self.assertEqual(image.size, (2048, 2048))

    def test_checkerboard_is_outside_artwork_mask(self):
        tile = artwork()
        for corner in [(0, 0), (0, tile.height - 1),
                       (tile.width - 1, 0), (tile.width - 1, tile.height - 1)]:
            self.assertEqual(tile.getpixel(corner)[3], 0)
        self.assertEqual(tile.getpixel((tile.width // 2, tile.height // 2))[3], 255)

    def test_legacy_density_sizes_and_round_transparency(self):
        for density, size in [
            ("mdpi", 48), ("hdpi", 72), ("xhdpi", 96),
            ("xxhdpi", 144), ("xxxhdpi", 192),
        ]:
            for name in ["ic_launcher", "ic_launcher_round"]:
                with self.subTest(density=density, name=name):
                    with Image.open(RES / f"mipmap-{density}" / f"{name}.webp") as icon:
                        self.assertEqual(icon.size, (size, size))
                        if name.endswith("round"):
                            self.assertEqual(icon.convert("RGBA").getpixel((0, 0))[3], 0)

    def test_adaptive_safe_padding_and_colored_mark(self):
        with Image.open(RES / "drawable-xxxhdpi/ic_launcher_artwork.png") as icon:
            self.assertEqual(icon.size, (432, 432))
            left, top, right, bottom = icon.getbbox()
            self.assertGreaterEqual(left, 108)
            self.assertGreaterEqual(top, 108)
            self.assertLessEqual(right, 324)
            self.assertLessEqual(bottom, 324)
            colored = sum(
                1 for red, green, blue, alpha in icon.convert("RGBA").getdata()
                if alpha > 200 and green - red > 50 and blue - red > 30
            )
            self.assertGreater(colored, 2000)


if __name__ == "__main__":
    unittest.main()
