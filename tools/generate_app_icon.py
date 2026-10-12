"""Generate Android launcher assets from the supplied CoverOS artwork.

Requires Pillow. Run from the repository root: python3 tools/generate_app_icon.py
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageOps


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "assets/coveros-icon.jpeg"
RES = ROOT / "app/src/main/res"


def artwork():
    with Image.open(SOURCE) as source:
        source = ImageOps.exif_transpose(source).convert("RGBA")
        if source.size != (2048, 2048):
            raise ValueError("Expected the supplied 2048 x 2048 CoverOS artwork")
        # The JPEG has a baked-in checkerboard, not a transparent background.
        tile = source.crop((440, 420, 1608, 1578))
    mask = Image.new("L", tile.size, 0)
    drawing = ImageDraw.Draw(mask)
    pixels = tile.load()
    # Follow the white outline so no baked-in checkerboard survives at corners.
    for y in range(tile.height):
        outline = [
            x for x in range(tile.width)
            if min(pixels[x, y][:3]) >= 225
        ]
        if len(outline) >= 2:
            drawing.line((outline[0], y, outline[-1], y), fill=255)
    tile.putalpha(mask)
    return tile


def render(tile, size, fraction, circular=False):
    canvas = Image.new("RGBA", (size, size), "#202020")
    edge = round(size * fraction)
    scaled = tile.resize((edge, edge), Image.Resampling.LANCZOS)
    canvas.alpha_composite(scaled, ((size - edge) // 2, (size - edge) // 2))
    if circular:
        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
        canvas.putalpha(mask)
    return canvas


def main():
    tile = artwork()
    for density, size in [
        ("mdpi", 48), ("hdpi", 72), ("xhdpi", 96),
        ("xxhdpi", 144), ("xxxhdpi", 192),
    ]:
        directory = RES / f"mipmap-{density}"
        render(tile, size, 0.84).save(directory / "ic_launcher.webp", lossless=True)
        render(tile, size, 0.70, circular=True).save(
            directory / "ic_launcher_round.webp", lossless=True
        )

    # 54dp on a 108dp layer keeps the mark inside adaptive icon masks.
    foreground = Image.new("RGBA", (432, 432))
    scaled = tile.resize((216, 216), Image.Resampling.LANCZOS)
    foreground.alpha_composite(scaled, (108, 108))
    directory = RES / "drawable-xxxhdpi"
    directory.mkdir(exist_ok=True)
    foreground.save(directory / "ic_launcher_artwork.png", optimize=True)


if __name__ == "__main__":
    main()
