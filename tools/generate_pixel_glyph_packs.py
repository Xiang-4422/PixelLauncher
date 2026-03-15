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
ARK_GLYPHS_ROOT_DIR = ROOT_DIR / ".codex_tmp" / "ark-pixel-font" / "assets" / "glyphs"

MAGIC = 0x50474C59  # PGLY
VERSION = 1
CELL_HEIGHT = 16
ASCII_WIDTH = 8
WIDE_WIDTH = 16


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
    RangeSpec(0xAC00, 0xD7A3),
    RangeSpec(0xFF00, 0xFFEF),
]


@dataclass(frozen=True)
class FontPackSpec:
    pack_id: str
    display_name: str
    font_path: Path
    baseline: int
    supported_ranges: list[RangeSpec]


@dataclass(frozen=True)
class ArkPackSpec:
    pack_id: str
    display_name: str
    font_size: int
    width_mode: str
    language_flavor: str
    baseline: int


FONT_PACKS = [
    FontPackSpec(
        pack_id="wenquanyi_bitmap_song_16px",
        display_name="WenQuanYi Bitmap Song 16px",
        font_path=FONT_DIR / "wenquanyi_bitmap_song_16px.ttf",
        baseline=13,
        supported_ranges=SUPPORTED_RANGES,
    ),
    FontPackSpec(
        pack_id="gnu_unifont_17_0_03",
        display_name="GNU Unifont 17.0.03",
        font_path=FONT_DIR / "unifont_17_0_03.otf",
        baseline=13,
        supported_ranges=SUPPORTED_RANGES,
    ),
]


ARK_PACKS = [
    ArkPackSpec(
        pack_id="ark_pixel_10px_monospaced_latin",
        display_name="Ark Pixel 10px Monospaced (latin)",
        font_size=10,
        width_mode="monospaced",
        language_flavor="latin",
        baseline=8,
    ),
    ArkPackSpec(
        pack_id="ark_pixel_16px_monospaced_zh_cn",
        display_name="Ark Pixel 16px Monospaced (zh_CN)",
        font_size=16,
        width_mode="monospaced",
        language_flavor="zh_cn",
        baseline=13,
    ),
    ArkPackSpec(
        pack_id="ark_pixel_16px_monospaced_zh_hk",
        display_name="Ark Pixel 16px Monospaced (zh_HK)",
        font_size=16,
        width_mode="monospaced",
        language_flavor="zh_hk",
        baseline=13,
    ),
    ArkPackSpec(
        pack_id="ark_pixel_16px_monospaced_zh_tw",
        display_name="Ark Pixel 16px Monospaced (zh_TW)",
        font_size=16,
        width_mode="monospaced",
        language_flavor="zh_tw",
        baseline=13,
    ),
    ArkPackSpec(
        pack_id="ark_pixel_16px_monospaced_zh_tr",
        display_name="Ark Pixel 16px Monospaced (zh_TR)",
        font_size=16,
        width_mode="monospaced",
        language_flavor="zh_tr",
        baseline=13,
    ),
    ArkPackSpec(
        pack_id="ark_pixel_16px_proportional_zh_cn",
        display_name="Ark Pixel 16px Proportional (zh_CN)",
        font_size=16,
        width_mode="proportional",
        language_flavor="zh_cn",
        baseline=14,
    ),
    ArkPackSpec(
        pack_id="ark_pixel_16px_proportional_zh_hk",
        display_name="Ark Pixel 16px Proportional (zh_HK)",
        font_size=16,
        width_mode="proportional",
        language_flavor="zh_hk",
        baseline=14,
    ),
    ArkPackSpec(
        pack_id="ark_pixel_16px_proportional_zh_tw",
        display_name="Ark Pixel 16px Proportional (zh_TW)",
        font_size=16,
        width_mode="proportional",
        language_flavor="zh_tw",
        baseline=14,
    ),
    ArkPackSpec(
        pack_id="ark_pixel_16px_proportional_zh_tr",
        display_name="Ark Pixel 16px Proportional (zh_TR)",
        font_size=16,
        width_mode="proportional",
        language_flavor="zh_tr",
        baseline=14,
    ),
]


def main() -> None:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    for font_pack in FONT_PACKS:
        generate_font_pack(font_pack)

    for ark_pack in ARK_PACKS:
        generate_ark_pack(ark_pack)


def generate_font_pack(spec: FontPackSpec) -> None:
    font = ImageFont.truetype(
        str(spec.font_path),
        size=CELL_HEIGHT,
        layout_engine=ImageFont.Layout.BASIC,
    )

    records = []
    for code_point in iter_code_points(spec.supported_ranges):
        character = chr(code_point)
        cell_width = ASCII_WIDTH if is_ascii_printable(code_point) else WIDE_WIDTH
        glyph_pixels = render_font_glyph(
            font=font,
            character=character,
            cell_width=cell_width,
            cell_height=CELL_HEIGHT,
            baseline=spec.baseline,
        )

        if glyph_pixels is None:
            continue

        if not any(glyph_pixels) and not character.isspace():
            continue

        records.append(
            (
                code_point,
                cell_width,
                cell_width,
                pack_bits(glyph_pixels),
            ),
        )

    write_pack(
        pack_id=spec.pack_id,
        display_name=spec.display_name,
        cell_height=CELL_HEIGHT,
        baseline=spec.baseline,
        default_advance=WIDE_WIDTH,
        supported_ranges=[range_spec.label for range_spec in spec.supported_ranges],
        records=records,
    )


def generate_ark_pack(spec: ArkPackSpec) -> None:
    records_by_code_point = {}

    ark_glyphs_dir = ARK_GLYPHS_ROOT_DIR / str(spec.font_size)
    if ark_glyphs_dir.exists():
        collect_ark_pngs(
            base_dir=ark_glyphs_dir / "common",
            language_flavor=spec.language_flavor,
            cell_height=spec.font_size,
            records_by_code_point=records_by_code_point,
            priority_base=100,
        )
        collect_ark_pngs(
            base_dir=ark_glyphs_dir / spec.width_mode,
            language_flavor=spec.language_flavor,
            cell_height=spec.font_size,
            records_by_code_point=records_by_code_point,
            priority_base=200,
        )
    else:
        font_path = FONT_DIR / f"{spec.pack_id}.ttf"
        font = ImageFont.truetype(
            str(font_path),
            size=spec.font_size,
            layout_engine=ImageFont.Layout.BASIC,
        )
        for code_point in iter_code_points(SUPPORTED_RANGES):
            character = chr(code_point)
            cell_width = ark_cell_width(spec.font_size, code_point)
            glyph_pixels = render_font_glyph(
                font=font,
                character=character,
                cell_width=cell_width,
                cell_height=spec.font_size,
                baseline=spec.baseline,
            )
            if glyph_pixels is None or (not any(glyph_pixels) and not character.isspace()):
                continue
            records_by_code_point[code_point] = (
                0,
                ark_advance_width(cell_width),
                cell_width,
                pack_bits(glyph_pixels),
            )

    records = [
        (code_point, advance_width, width, packed_pixels)
        for code_point, (_, advance_width, width, packed_pixels) in sorted(records_by_code_point.items())
    ]
    supported_ranges = summarize_ranges([code_point for code_point, *_ in records])

    write_pack(
        pack_id=spec.pack_id,
        display_name=spec.display_name,
        cell_height=spec.font_size,
        baseline=spec.baseline,
        default_advance=ark_default_advance(spec.font_size),
        supported_ranges=supported_ranges,
        records=records,
    )


def collect_ark_pngs(
    base_dir: Path,
    language_flavor: str,
    cell_height: int,
    records_by_code_point: dict[int, tuple[int, int, int, bytes]],
    priority_base: int,
) -> None:
    if not base_dir.exists():
        return

    for path in sorted(base_dir.rglob("*.png")):
        parse_result = parse_ark_file_name(path.stem)
        if parse_result is None:
            continue

        code_point, variants = parse_result
        variant_priority = variant_score(variants, language_flavor)
        if variant_priority < 0:
            continue

        image = Image.open(path)
        image = trim_transparent_bounds(image)
        width, height = image.size
        max_width = 5 if cell_height == 10 else WIDE_WIDTH
        if width > max_width or height > cell_height:
            continue

        raw_pixels = image.getdata()
        glyph_pixels = bytearray(width * cell_height)
        vertical_offset = max(0, cell_height - height)
        for y in range(height):
            for x in range(width):
                pixel = raw_pixels[(y * width) + x]
                alpha = pixel[3] if isinstance(pixel, tuple) and len(pixel) == 4 else int(pixel)
                if alpha > 0:
                    glyph_pixels[((y + vertical_offset) * width) + x] = 1

        priority = priority_base + variant_priority
        advance_width = ark_advance_width(width)
        packed_pixels = pack_bits(glyph_pixels)

        current = records_by_code_point.get(code_point)
        if current is None or priority >= current[0]:
            records_by_code_point[code_point] = (priority, advance_width, width, packed_pixels)


def parse_ark_file_name(stem: str) -> tuple[int, list[str]] | None:
    if " " in stem:
        code_point_text, variants_text = stem.split(" ", 1)
        variants = [token.strip() for token in variants_text.split(",") if token.strip()]
    else:
        code_point_text = stem
        variants = []

    try:
        return int(code_point_text, 16), variants
    except ValueError:
        return None


def variant_score(variants: list[str], language_flavor: str) -> int:
    if not variants:
        return 1
    if language_flavor in variants:
        return 2
    return -1


def render_font_glyph(
    font: ImageFont.FreeTypeFont,
    character: str,
    cell_width: int,
    cell_height: int,
    baseline: int,
) -> bytes | None:
    image = Image.new("1", (cell_width, cell_height), 0)
    draw = ImageDraw.Draw(image)
    draw.fontmode = "1"

    try:
        ascent, _ = font.getmetrics()
        draw_y = baseline - ascent
        bbox = draw.textbbox((0, draw_y), character, font=font)
        if bbox is None:
            return None
        x = ((cell_width - (bbox[2] - bbox[0])) // 2) - bbox[0]
        draw.text((x, draw_y), character, font=font, fill=1)
    except OSError:
        return None

    return bytes(1 if image.getpixel((x, y)) else 0 for y in range(cell_height) for x in range(cell_width))


def trim_transparent_bounds(image: Image.Image) -> Image.Image:
    rgba = image.convert("RGBA")
    alpha = rgba.getchannel("A")
    bbox = alpha.getbbox()
    if bbox is None:
        return rgba
    return rgba.crop(bbox)


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
    if start == end:
        return f"{start:04X}-{end:04X}"
    return f"{start:04X}-{end:04X}"


def is_ascii_printable(code_point: int) -> bool:
    return 0x20 <= code_point <= 0x7E


def ark_cell_width(font_size: int, code_point: int) -> int:
    if font_size == 10:
        return 5
    return ASCII_WIDTH if is_ascii_printable(code_point) else WIDE_WIDTH


def ark_advance_width(width: int) -> int:
    return width + 1 if width < 16 else width


def ark_default_advance(font_size: int) -> int:
    return 6 if font_size == 10 else 16


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
