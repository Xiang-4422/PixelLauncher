from __future__ import annotations

import json
import struct
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

from PIL import Image, ImageDraw, ImageFont


ROOT_DIR = Path(__file__).resolve().parents[1]
ASSETS_DIR = ROOT_DIR / "app" / "src" / "main" / "assets"
FONT_DIR = ASSETS_DIR / "fonts"
OUTPUT_DIR = ASSETS_DIR / "glyphpacks"

MAGIC = 0x50474C59  # PGLY
VERSION = 1


@dataclass(frozen=True)
class RangeSpec:
    start: int
    end: int

    @property
    def label(self) -> str:
        return f"{self.start:04X}-{self.end:04X}"


SUPPORTED_RANGES = [
    RangeSpec(0x0020, 0x007E),
    RangeSpec(0x00A0, 0x00FF),
    RangeSpec(0x0100, 0x024F),
    RangeSpec(0x0370, 0x03FF),
    RangeSpec(0x0400, 0x04FF),
    RangeSpec(0x2000, 0x206F),
    RangeSpec(0x2070, 0x209F),
    RangeSpec(0x2100, 0x214F),
    RangeSpec(0x2460, 0x24FF),
    RangeSpec(0x3000, 0x303F),
    RangeSpec(0x3040, 0x309F),
    RangeSpec(0x30A0, 0x30FF),
    RangeSpec(0x3100, 0x312F),
    RangeSpec(0x3130, 0x318F),
    RangeSpec(0x3200, 0x32FF),
    RangeSpec(0x3300, 0x33FF),
    RangeSpec(0x3400, 0x4DBF),
    RangeSpec(0x4E00, 0x9FFF),
    RangeSpec(0xFF00, 0xFFEF),
]


@dataclass(frozen=True)
class FusionPackSpec:
    pack_id: str
    display_name: str
    font_path: Path
    font_size: int
    baseline: int
    default_advance: int
    supported_ranges: list[RangeSpec]


FUSION_PACKS = [
    FusionPackSpec(
        pack_id="fusion_pixel_8px_monospaced_latin",
        display_name="Fusion Pixel 8px Monospaced (latin)",
        font_path=FONT_DIR / "fusion-pixel-8px-monospaced-latin.ttf",
        font_size=8,
        baseline=7,
        default_advance=8,
        supported_ranges=SUPPORTED_RANGES,
    ),
    FusionPackSpec(
        pack_id="fusion_pixel_8px_monospaced_zh_hans",
        display_name="Fusion Pixel 8px Monospaced (zh_hans)",
        font_path=FONT_DIR / "fusion-pixel-8px-monospaced-zh_hans.ttf",
        font_size=8,
        baseline=7,
        default_advance=8,
        supported_ranges=SUPPORTED_RANGES,
    ),
    FusionPackSpec(
        pack_id="fusion_pixel_8px_proportional_latin",
        display_name="Fusion Pixel 8px Proportional (latin)",
        font_path=FONT_DIR / "fusion-pixel-8px-proportional-latin.ttf",
        font_size=8,
        baseline=7,
        default_advance=4,
        supported_ranges=SUPPORTED_RANGES,
    ),
    FusionPackSpec(
        pack_id="fusion_pixel_8px_proportional_zh_hans",
        display_name="Fusion Pixel 8px Proportional (zh_hans)",
        font_path=FONT_DIR / "fusion-pixel-8px-proportional-zh_hans.ttf",
        font_size=8,
        baseline=7,
        default_advance=8,
        supported_ranges=SUPPORTED_RANGES,
    ),
    FusionPackSpec(
        pack_id="fusion_pixel_10px_monospaced_latin",
        display_name="Fusion Pixel 10px Monospaced (latin)",
        font_path=FONT_DIR / "fusion-pixel-10px-monospaced-latin.ttf",
        font_size=10,
        baseline=9,
        default_advance=10,
        supported_ranges=SUPPORTED_RANGES,
    ),
    FusionPackSpec(
        pack_id="fusion_pixel_10px_monospaced_zh_hans",
        display_name="Fusion Pixel 10px Monospaced (zh_hans)",
        font_path=FONT_DIR / "fusion-pixel-10px-monospaced-zh_hans.ttf",
        font_size=10,
        baseline=9,
        default_advance=10,
        supported_ranges=SUPPORTED_RANGES,
    ),
    FusionPackSpec(
        pack_id="fusion_pixel_10px_proportional_latin",
        display_name="Fusion Pixel 10px Proportional (latin)",
        font_path=FONT_DIR / "fusion-pixel-10px-proportional-latin.ttf",
        font_size=10,
        baseline=9,
        default_advance=6,
        supported_ranges=SUPPORTED_RANGES,
    ),
    FusionPackSpec(
        pack_id="fusion_pixel_10px_proportional_zh_hans",
        display_name="Fusion Pixel 10px Proportional (zh_hans)",
        font_path=FONT_DIR / "fusion-pixel-10px-proportional-zh_hans.ttf",
        font_size=10,
        baseline=9,
        default_advance=10,
        supported_ranges=SUPPORTED_RANGES,
    ),
    FusionPackSpec(
        pack_id="fusion_pixel_12px_monospaced_latin",
        display_name="Fusion Pixel 12px Monospaced (latin)",
        font_path=FONT_DIR / "fusion-pixel-12px-monospaced-latin.ttf",
        font_size=12,
        baseline=10,
        default_advance=12,
        supported_ranges=SUPPORTED_RANGES,
    ),
    FusionPackSpec(
        pack_id="fusion_pixel_12px_monospaced_zh_hans",
        display_name="Fusion Pixel 12px Monospaced (zh_hans)",
        font_path=FONT_DIR / "fusion-pixel-12px-monospaced-zh_hans.ttf",
        font_size=12,
        baseline=10,
        default_advance=12,
        supported_ranges=SUPPORTED_RANGES,
    ),
    FusionPackSpec(
        pack_id="fusion_pixel_12px_proportional_latin",
        display_name="Fusion Pixel 12px Proportional (latin)",
        font_path=FONT_DIR / "fusion-pixel-12px-proportional-latin.ttf",
        font_size=12,
        baseline=11,
        default_advance=8,
        supported_ranges=SUPPORTED_RANGES,
    ),
    FusionPackSpec(
        pack_id="fusion_pixel_12px_proportional_zh_hans",
        display_name="Fusion Pixel 12px Proportional (zh_hans)",
        font_path=FONT_DIR / "fusion-pixel-12px-proportional-zh_hans.ttf",
        font_size=12,
        baseline=11,
        default_advance=12,
        supported_ranges=SUPPORTED_RANGES,
    ),
]


def main() -> None:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    for pack in FUSION_PACKS:
        generate_fusion_pack(pack)


def generate_fusion_pack(spec: FusionPackSpec) -> None:
    font = ImageFont.truetype(
        str(spec.font_path),
        size=spec.font_size,
        layout_engine=ImageFont.Layout.BASIC,
    )

    records = []
    for code_point in iter_code_points(spec.supported_ranges):
        character = chr(code_point)
        glyph_pixels = render_font_glyph(
            font=font,
            character=character,
            cell_height=spec.font_size,
            baseline=spec.baseline,
        )
        if glyph_pixels is None:
            continue
        if not any(glyph_pixels) and not character.isspace():
            continue

        width = detect_glyph_width(glyph_pixels, spec.font_size, spec.font_size)
        if width <= 0:
            width = spec.default_advance

        records.append(
            (
                code_point,
                width,
                width,
                pack_bits(crop_glyph_pixels(glyph_pixels, spec.font_size, spec.font_size, width)),
            ),
        )

    write_pack(
        pack_id=spec.pack_id,
        display_name=spec.display_name,
        cell_height=spec.font_size,
        baseline=spec.baseline,
        default_advance=spec.default_advance,
        supported_ranges=summarize_ranges([code_point for code_point, *_ in records]),
        records=records,
    )


def render_font_glyph(
    font: ImageFont.FreeTypeFont,
    character: str,
    cell_height: int,
    baseline: int,
) -> bytes | None:
    image = Image.new("1", (cell_height, cell_height), 0)
    draw = ImageDraw.Draw(image)
    draw.fontmode = "1"

    try:
        ascent, _ = font.getmetrics()
        draw_y = baseline - ascent
        bbox = draw.textbbox((0, draw_y), character, font=font)
        if bbox is None:
            return None
        glyph_width = bbox[2] - bbox[0]
        x = ((cell_height - glyph_width) // 2) - bbox[0]
        draw.text((x, draw_y), character, font=font, fill=1)
    except OSError:
        return None

    return bytes(1 if image.getpixel((x, y)) else 0 for y in range(cell_height) for x in range(cell_height))


def detect_glyph_width(pixels: bytes, canvas_width: int, canvas_height: int) -> int:
    rightmost = -1
    for y in range(canvas_height):
        row_start = y * canvas_width
        for x in range(canvas_width - 1, -1, -1):
            if pixels[row_start + x]:
                rightmost = max(rightmost, x)
                break
    return rightmost + 1


def crop_glyph_pixels(pixels: bytes, canvas_width: int, canvas_height: int, width: int) -> bytes:
    if width >= canvas_width:
        return pixels
    cropped = bytearray(width * canvas_height)
    for y in range(canvas_height):
        row_start = y * canvas_width
        cropped_row_start = y * width
        cropped[cropped_row_start : cropped_row_start + width] = pixels[row_start : row_start + width]
    return bytes(cropped)


def pack_bits(pixels: bytes | bytearray) -> bytes:
    packed = bytearray((len(pixels) + 7) // 8)
    for index, pixel in enumerate(pixels):
        if pixel:
            packed[index // 8] |= 1 << (7 - (index % 8))
    return bytes(packed)


def iter_code_points(ranges: Iterable[RangeSpec]) -> Iterable[int]:
    for range_spec in ranges:
        for code_point in range(range_spec.start, range_spec.end + 1):
            yield code_point


def summarize_ranges(code_points: list[int]) -> list[str]:
    if not code_points:
        return []

    sorted_points = sorted(code_points)
    ranges = []
    start = sorted_points[0]
    end = start

    for code_point in sorted_points[1:]:
        if code_point == end + 1:
            end = code_point
            continue
        ranges.append(format_range(start, end))
        start = code_point
        end = code_point

    ranges.append(format_range(start, end))
    return ranges


def format_range(start: int, end: int) -> str:
    return f"{start:04X}-{end:04X}"


def write_pack(
    pack_id: str,
    display_name: str,
    cell_height: int,
    baseline: int,
    default_advance: int,
    supported_ranges: list[str],
    records: list[tuple[int, int, int, bytes]],
) -> None:
    pack_dir = OUTPUT_DIR / pack_id
    pack_dir.mkdir(parents=True, exist_ok=True)

    manifest = {
        "packId": pack_id,
        "displayName": display_name,
        "cellHeight": cell_height,
        "baseline": baseline,
        "defaultAdvance": default_advance,
        "supportedRanges": supported_ranges,
    }
    (pack_dir / "manifest.json").write_text(
        json.dumps(manifest, indent=2, ensure_ascii=False),
        encoding="utf-8",
    )

    with (pack_dir / "glyphs.bin").open("wb") as output:
        output.write(struct.pack(">IIII", MAGIC, VERSION, cell_height, len(records)))
        for code_point, advance_width, width, packed_pixels in records:
            output.write(struct.pack(">IIII", code_point, advance_width, width, len(packed_pixels)))
            output.write(packed_pixels)

    print(f"Generated {pack_id}: {len(records)} glyphs")


if __name__ == "__main__":
    main()
