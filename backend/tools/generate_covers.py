"""Generate the ten local template covers used by the demo seed.

The covers are deterministic abstract compositions, not third-party artwork.
Requires Pillow with WebP support:

    python backend/tools/generate_covers.py
"""

from __future__ import annotations

import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src" / "main" / "resources" / "static" / "assets" / "templates"
SIZE = (1200, 1600)


def gradient(top: tuple[int, int, int], bottom: tuple[int, int, int]) -> Image.Image:
    image = Image.new("RGB", SIZE)
    draw = ImageDraw.Draw(image)
    for y in range(SIZE[1]):
        t = y / (SIZE[1] - 1)
        color = tuple(round(a + (b - a) * t) for a, b in zip(top, bottom))
        draw.line((0, y, SIZE[0], y), fill=color)
    return image


def add_glow(
    image: Image.Image,
    center: tuple[int, int],
    radius: int,
    color: tuple[int, int, int],
) -> Image.Image:
    layer = Image.new("RGBA", SIZE, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    for step in range(radius, 0, -8):
        alpha = max(0, round(52 * (1 - step / radius)))
        draw.ellipse(
            (center[0] - step, center[1] - step, center[0] + step, center[1] + step),
            fill=(*color, alpha),
        )
    layer = layer.filter(ImageFilter.GaussianBlur(30))
    return Image.alpha_composite(image, layer)


def add_motif(
    image: Image.Image,
    rng: random.Random,
    accent: tuple[int, int, int],
    motif: str,
) -> Image.Image:
    layer = Image.new("RGBA", SIZE, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    ink = (*accent, 150)
    faint = (*accent, 48)
    white = (255, 255, 255, 80)

    if motif == "horizon":
        draw.ellipse((760, 300, 1040, 580), outline=ink, width=4)
        draw.line((0, 1080, 1200, 1080), fill=ink, width=3)
        for x in range(120, 1100, 130):
            draw.line((x, 1120, x + 40, 1120), fill=faint, width=10)
    elif motif == "frame":
        draw.rounded_rectangle((110, 150, 1090, 1450), radius=60, outline=ink, width=4)
        draw.rounded_rectangle((150, 190, 1050, 1410), radius=40, outline=white, width=2)
    elif motif == "waves":
        for offset in range(0, 900, 150):
            draw.arc((-300, 700 + offset, 1500, 1500 + offset), 200, 340, fill=ink, width=4)
    elif motif == "snow":
        for _ in range(70):
            x, y = rng.randint(60, 1140), rng.randint(80, 1500)
            r = rng.randint(2, 8)
            draw.ellipse((x - r, y - r, x + r, y + r), fill=(255, 255, 255, rng.randint(90, 190)))
    elif motif == "orbit":
        draw.ellipse((440, 360, 1140, 1060), outline=ink, width=4)
        draw.ellipse((160, 760, 620, 1220), outline=white, width=3)
        draw.ellipse((880, 180, 1040, 340), fill=faint)
    elif motif == "blobs":
        for _ in range(6):
            x, y = rng.randint(-100, 900), rng.randint(100, 1300)
            w, h = rng.randint(240, 620), rng.randint(200, 520)
            draw.ellipse((x, y, x + w, y + h), fill=faint)
    elif motif == "rings":
        draw.ellipse((300, 300, 980, 980), outline=ink, width=6)
        draw.ellipse((560, 560, 1240, 1240), outline=white, width=3)
    elif motif == "minimal":
        draw.line((600, 150, 600, 1450), fill=ink, width=4)
        draw.ellipse((520, 700, 680, 860), outline=white, width=3)
        draw.line((160, 1240, 1040, 1240), fill=faint, width=12)
    elif motif == "cup":
        draw.ellipse((360, 480, 840, 960), outline=ink, width=5)
        draw.arc((300, 900, 900, 1200), 180, 360, fill=ink, width=4)
        draw.ellipse((520, 640, 680, 800), fill=faint)
    elif motif == "blocks":
        draw.rounded_rectangle((140, 260, 620, 740), radius=30, outline=ink, width=4)
        draw.rounded_rectangle((560, 700, 1080, 1220), radius=30, fill=faint)
        draw.line((160, 1360, 1040, 1360), fill=white, width=3)

    return Image.alpha_composite(image, layer)


def vignette(image: Image.Image) -> Image.Image:
    mask = Image.new("L", SIZE, 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse((-260, -200, 1460, 1800), fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(220))
    black = Image.new("RGB", SIZE, (0, 0, 0))
    return Image.composite(image.convert("RGB"), black, mask)


def grain(image: Image.Image, strength: float = 0.08) -> Image.Image:
    noise = Image.effect_noise(SIZE, 34).convert("RGB")
    return Image.blend(image, noise, strength)


def cover(template_id: str, palette: tuple[str, str], motif: str, seed: int) -> None:
    rng = random.Random(seed)
    top, bottom = (tuple(int(p[i : i + 2], 16) for i in (1, 3, 5)) for p in palette)
    image = gradient(top, bottom).convert("RGBA")
    accent = tuple(min(255, c + 70) for c in bottom)

    glow_x = rng.randint(240, 960)
    glow_y = rng.randint(260, 1180)
    image = add_glow(image, (glow_x, glow_y), rng.randint(240, 440), accent)
    image = add_motif(image, rng, accent, motif)
    image = grain(vignette(image))
    image.save(OUT / f"{template_id}.webp", "WEBP", quality=82, method=6)


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    specs = {
        "travel-postcard": ("#1D3B3A", "#D8A96A", "horizon", 11),
        "film-memory": ("#2A2118", "#8C7A4E", "frame", 12),
        "summer-coast": ("#0F4C5C", "#7ED6C8", "waves", 13),
        "christmas-letter": ("#14251B", "#A33B33", "snow", 14),
        "new-year-card": ("#3A0E12", "#D6A84B", "orbit", 15),
        "pet-portrait": ("#3B3328", "#D9C7A7", "blobs", 16),
        "wedding-invite": ("#3A2D31", "#E7D4C4", "rings", 17),
        "minimal-portrait": ("#101012", "#C9C6BA", "minimal", 18),
        "coffee-launch": ("#2A1A12", "#C08A5A", "cup", 19),
        "brand-story": ("#1A1E19", "#A6B86A", "blocks", 20),
    }
    for template_id, (top, bottom, motif, seed) in specs.items():
        cover(template_id, (top, bottom), motif, seed)


if __name__ == "__main__":
    main()
