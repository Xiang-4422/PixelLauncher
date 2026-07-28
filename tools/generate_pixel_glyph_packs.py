from __future__ import annotations

import argparse
import json
import struct
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable, Sequence


ROOT_DIR = Path(__file__).resolve().parents[1]
ASSETS_DIR = ROOT_DIR / "app" / "src" / "main" / "assets"
FONT_DIR = ASSETS_DIR / "fonts"
OUTPUT_DIR = ASSETS_DIR / "glyphpacks"
FONT_SOURCE_DIR = ROOT_DIR / "tools" / "font_sources"

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


@dataclass(frozen=True)
class BdfPackSpec:
    """描述一个随仓库保存、由 BDF 构建的内置字形包。"""

    pack_id: str
    display_name: str
    font_path: Path
    cell_height: int
    baseline: int
    default_advance: int
    supported_ranges: list[RangeSpec]


@dataclass(frozen=True)
class BdfGlyph:
    code_point: int
    advance_width: int
    width: int
    height: int
    x_offset: int
    y_offset: int
    bitmap_rows: tuple[str, ...]


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

def ark_pack_spec(
    size: int,
    width_mode: str,
    baseline: int,
    default_advance: int,
) -> BdfPackSpec:
    """创建一个 Ark Pixel 大陆简体 BDF 内置包定义。"""

    return BdfPackSpec(
        pack_id=f"ark_pixel_{size}px_{width_mode}_zh_cn",
        display_name=f"Ark Pixel {size}px {width_mode.title()} (zh_cn)",
        font_path=(
            FONT_SOURCE_DIR
            / "ark_pixel"
            / "2026.07.20"
            / f"ark-pixel-{size}px-{width_mode}-zh_cn.bdf"
        ),
        cell_height=size,
        baseline=baseline,
        default_advance=default_advance,
        supported_ranges=[
            RangeSpec(0x0020, 0xD7FF),
            RangeSpec(0xE000, 0xFFFD),
        ],
    )


# Ark Pixel 官方提供的字号与宽度模式矩阵；每个选择只加载对应家族资源。
ARK_PACKS = [
    ark_pack_spec(size=10, width_mode="proportional", baseline=8, default_advance=5),
    ark_pack_spec(size=10, width_mode="monospaced", baseline=9, default_advance=5),
    ark_pack_spec(size=12, width_mode="proportional", baseline=10, default_advance=6),
    ark_pack_spec(size=12, width_mode="monospaced", baseline=10, default_advance=6),
    ark_pack_spec(size=16, width_mode="proportional", baseline=13, default_advance=7),
    ark_pack_spec(size=16, width_mode="monospaced", baseline=13, default_advance=8),
]


def main(argv: Sequence[str] | None = None) -> None:
    parser = build_argument_parser()
    args = parser.parse_args(argv)
    if args.input is None:
        OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
        for pack in FUSION_PACKS:
            generate_fusion_pack(pack)
        for pack in ARK_PACKS:
            generate_bdf_builtin_pack(pack)
        return

    try:
        ranges = parse_ranges(args.ranges)
    except ValueError as error:
        parser.error(str(error))
    input_path = Path(args.input)
    if not input_path.is_file():
        parser.error(f"input font does not exist: {input_path}")
    if args.baseline >= args.cell_height:
        parser.error("baseline must be smaller than cell height")
    output_dir = Path(args.output)
    common = {
        "font_path": input_path,
        "output_dir": output_dir,
        "pack_id": args.pack_id,
        "display_name": args.display_name,
        "cell_height": args.cell_height,
        "baseline": args.baseline,
        "default_advance": args.default_advance,
        "supported_ranges": ranges,
    }
    suffix = input_path.suffix.lower()
    if suffix in {".ttf", ".otf"}:
        generate_ttf_pack(font_size=args.font_size or args.cell_height, **common)
    elif suffix == ".bdf":
        generate_bdf_pack(**common)
    else:
        parser.error(f"unsupported font extension '{suffix}'; expected .ttf, .otf, or .bdf")


def build_argument_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Convert TTF/OTF/BDF fonts into pixel-engine glyph packs.",
    )
    parser.add_argument("--input", help="Input .ttf, .otf, or .bdf file. Omit to regenerate built-in packs.")
    parser.add_argument("--output", default=str(OUTPUT_DIR), help="Output directory containing the pack folder.")
    parser.add_argument("--pack-id", default="custom_font", help="Stable glyph pack id.")
    parser.add_argument("--display-name", default="Custom Font", help="Display name stored in manifest.json.")
    parser.add_argument("--cell-height", type=positive_int, default=8, help="Output cell height in pixels.")
    parser.add_argument("--baseline", type=non_negative_int, default=7, help="Baseline row measured from cell top.")
    parser.add_argument("--default-advance", type=positive_int, default=8, help="Fallback glyph advance width.")
    parser.add_argument("--font-size", type=positive_int, help="TTF/OTF rasterization size; defaults to cell height.")
    parser.add_argument("--ranges", default="0020-007E", help="Comma-separated Unicode ranges, e.g. 0020-007E,4E00-9FFF.")
    return parser


def positive_int(value: str) -> int:
    parsed = int(value)
    if parsed <= 0:
        raise argparse.ArgumentTypeError("value must be > 0")
    return parsed


def non_negative_int(value: str) -> int:
    parsed = int(value)
    if parsed < 0:
        raise argparse.ArgumentTypeError("value must be >= 0")
    return parsed


def parse_ranges(value: str) -> list[RangeSpec]:
    ranges: list[RangeSpec] = []
    for raw_part in value.split(","):
        part = raw_part.strip()
        if not part:
            continue
        bounds = part.split("-", maxsplit=1)
        start = int(bounds[0], 16)
        end = int(bounds[1], 16) if len(bounds) == 2 else start
        if start < 0 or end < start or end > 0x10FFFF:
            raise ValueError(f"invalid Unicode range: {part}")
        ranges.append(RangeSpec(start, end))
    if not ranges:
        raise ValueError("at least one Unicode range is required")
    return ranges


def generate_fusion_pack(spec: FusionPackSpec) -> None:
    generate_ttf_pack(
        font_path=spec.font_path,
        output_dir=OUTPUT_DIR,
        pack_id=spec.pack_id,
        display_name=spec.display_name,
        font_size=spec.font_size,
        cell_height=spec.font_size,
        baseline=spec.baseline,
        default_advance=spec.default_advance,
        supported_ranges=spec.supported_ranges,
    )


def generate_bdf_builtin_pack(spec: BdfPackSpec) -> None:
    """把仓库内置 BDF 源转换成 engine 的稳定二进制字形包。"""

    generate_bdf_pack(
        font_path=spec.font_path,
        output_dir=OUTPUT_DIR,
        pack_id=spec.pack_id,
        display_name=spec.display_name,
        cell_height=spec.cell_height,
        baseline=spec.baseline,
        default_advance=spec.default_advance,
        supported_ranges=spec.supported_ranges,
    )


def generate_ttf_pack(
    font_path: Path,
    output_dir: Path,
    pack_id: str,
    display_name: str,
    font_size: int,
    cell_height: int,
    baseline: int,
    default_advance: int,
    supported_ranges: list[RangeSpec],
) -> None:
    from PIL import ImageFont

    font = ImageFont.truetype(
        str(font_path),
        size=font_size,
        layout_engine=ImageFont.Layout.BASIC,
    )

    records = []
    for code_point in iter_code_points(supported_ranges):
        character = chr(code_point)
        glyph_pixels = render_font_glyph(
            font=font,
            character=character,
            cell_height=cell_height,
            baseline=baseline,
        )
        if glyph_pixels is None:
            continue
        if not any(glyph_pixels) and not character.isspace():
            continue

        width = detect_glyph_width(glyph_pixels, cell_height, cell_height)
        if width <= 0:
            width = default_advance

        records.append(
            (
                code_point,
                width,
                width,
                pack_bits(crop_glyph_pixels(glyph_pixels, cell_height, cell_height, width)),
            ),
        )

    write_pack(
        output_dir=output_dir,
        pack_id=pack_id,
        display_name=display_name,
        cell_height=cell_height,
        baseline=baseline,
        default_advance=default_advance,
        supported_ranges=summarize_ranges([code_point for code_point, *_ in records]),
        records=records,
    )


def generate_bdf_pack(
    font_path: Path,
    output_dir: Path,
    pack_id: str,
    display_name: str,
    cell_height: int,
    baseline: int,
    default_advance: int,
    supported_ranges: list[RangeSpec],
) -> None:
    selected = set(iter_code_points(supported_ranges))
    records: list[tuple[int, int, int, bytes]] = []
    for glyph in parse_bdf(font_path):
        if glyph.code_point not in selected:
            continue
        advance = glyph.advance_width if glyph.advance_width > 0 else default_advance
        output_width = max(1, advance, glyph.x_offset + glyph.width)
        pixels = rasterize_bdf_glyph(glyph, output_width, cell_height, baseline)
        if not any(pixels) and glyph.code_point != 0x20:
            continue
        records.append((glyph.code_point, advance, output_width, pack_bits(pixels)))

    write_pack(
        output_dir=output_dir,
        pack_id=pack_id,
        display_name=display_name,
        cell_height=cell_height,
        baseline=baseline,
        default_advance=default_advance,
        supported_ranges=summarize_ranges([code_point for code_point, *_ in records]),
        records=records,
    )


def parse_bdf(font_path: Path) -> list[BdfGlyph]:
    glyphs: list[BdfGlyph] = []
    current: dict[str, Any] | None = None
    reading_bitmap = False
    for raw_line in font_path.read_text(encoding="ascii").splitlines():
        line = raw_line.strip()
        if line.startswith("STARTCHAR "):
            current = {"bitmap_rows": []}
            reading_bitmap = False
        elif current is None:
            continue
        elif line.startswith("ENCODING "):
            current["code_point"] = int(line.split()[1])
        elif line.startswith("DWIDTH "):
            current["advance_width"] = int(line.split()[1])
        elif line.startswith("BBX "):
            _, width, height, x_offset, y_offset = line.split()[:5]
            current.update(
                width=int(width),
                height=int(height),
                x_offset=int(x_offset),
                y_offset=int(y_offset),
            )
        elif line == "BITMAP":
            reading_bitmap = True
        elif line == "ENDCHAR":
            reading_bitmap = False
            required = {"code_point", "width", "height", "x_offset", "y_offset"}
            missing = sorted(required.difference(current))
            if missing:
                raise ValueError(f"BDF glyph is missing fields: {', '.join(missing)}")
            code_point = int(current["code_point"])
            if code_point >= 0:
                rows = tuple(current["bitmap_rows"])
                height = int(current["height"])
                if len(rows) != height:
                    raise ValueError(
                        f"BDF glyph U+{code_point:04X} has {len(rows)} bitmap rows, expected {height}",
                    )
                glyphs.append(
                    BdfGlyph(
                        code_point=code_point,
                        advance_width=int(current.get("advance_width", 0)),
                        width=int(current["width"]),
                        height=height,
                        x_offset=int(current["x_offset"]),
                        y_offset=int(current["y_offset"]),
                        bitmap_rows=rows,
                    ),
                )
            current = None
        elif reading_bitmap and line:
            current["bitmap_rows"].append(line)
    if current is not None:
        raise ValueError("BDF ended before ENDCHAR")
    return glyphs


def rasterize_bdf_glyph(glyph: BdfGlyph, output_width: int, cell_height: int, baseline: int) -> bytes:
    pixels = bytearray(output_width * cell_height)
    top = baseline - (glyph.y_offset + glyph.height)
    for source_y, encoded_row in enumerate(glyph.bitmap_rows):
        row_value = int(encoded_row, 16) if encoded_row else 0
        encoded_bits = len(encoded_row) * 4
        target_y = top + source_y
        if target_y < 0 or target_y >= cell_height:
            continue
        for source_x in range(glyph.width):
            bit_index = encoded_bits - 1 - source_x
            if bit_index < 0 or not (row_value & (1 << bit_index)):
                continue
            target_x = glyph.x_offset + source_x
            if 0 <= target_x < output_width:
                pixels[target_y * output_width + target_x] = 1
    return bytes(pixels)


def render_font_glyph(
    font: Any,
    character: str,
    cell_height: int,
    baseline: int,
) -> bytes | None:
    from PIL import Image, ImageDraw

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
    output_dir: Path,
    pack_id: str,
    display_name: str,
    cell_height: int,
    baseline: int,
    default_advance: int,
    supported_ranges: list[str],
    records: list[tuple[int, int, int, bytes]],
) -> None:
    pack_dir = output_dir / pack_id
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
