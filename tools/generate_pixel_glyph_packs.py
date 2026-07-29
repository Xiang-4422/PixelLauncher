from __future__ import annotations

import argparse
import gzip
import hashlib
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
# FontForge 清理 OTF 时会把理论网格坐标取整，允许不超过半个字体单位的误差。
DOT_GRID_COORDINATE_TOLERANCE = 0.02


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
class TtfPackSpec:
    """描述一个由 TTF/OTF 构建的内置字形包。"""

    pack_id: str
    display_name: str
    font_path: Path
    font_size: int
    baseline: int
    default_advance: int
    supported_ranges: list[RangeSpec]
    cell_height: int | None = None
    # MONO 允许拉丁窄格和 CJK 宽格，不应错误要求全部 Unicode advance 相同。
    allowed_advances: tuple[int, ...] | None = None
    # 仅在 catalog 明确声明时把负 bearing 字形整体移入 v1 bitmap。
    shift_negative_bearing: bool = False


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
class DotGridPackSpec:
    """描述一个从点阵矢量轮廓反解原始逻辑点的字形包。"""

    pack_id: str
    display_name: str
    font_path: Path
    grid_height: int
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


@dataclass(frozen=True)
class RenderedTtfGlyph:
    """保存按字体逻辑原点栅格化后的位图和真实 advance。"""

    pixels: bytes
    width: int
    advance_width: int


FUSION_PACKS = [
    TtfPackSpec(
        pack_id="fusion_pixel_8px_monospaced_latin",
        display_name="Fusion Pixel 8px Monospaced (latin)",
        font_path=FONT_DIR / "fusion-pixel-8px-monospaced-latin.ttf",
        font_size=8,
        baseline=7,
        default_advance=8,
        supported_ranges=SUPPORTED_RANGES,
    ),
    TtfPackSpec(
        pack_id="fusion_pixel_8px_monospaced_zh_hans",
        display_name="Fusion Pixel 8px Monospaced (zh_hans)",
        font_path=FONT_DIR / "fusion-pixel-8px-monospaced-zh_hans.ttf",
        font_size=8,
        baseline=7,
        default_advance=8,
        supported_ranges=SUPPORTED_RANGES,
    ),
    TtfPackSpec(
        pack_id="fusion_pixel_8px_proportional_latin",
        display_name="Fusion Pixel 8px Proportional (latin)",
        font_path=FONT_DIR / "fusion-pixel-8px-proportional-latin.ttf",
        font_size=8,
        baseline=7,
        default_advance=4,
        supported_ranges=SUPPORTED_RANGES,
    ),
    TtfPackSpec(
        pack_id="fusion_pixel_8px_proportional_zh_hans",
        display_name="Fusion Pixel 8px Proportional (zh_hans)",
        font_path=FONT_DIR / "fusion-pixel-8px-proportional-zh_hans.ttf",
        font_size=8,
        baseline=7,
        default_advance=8,
        supported_ranges=SUPPORTED_RANGES,
    ),
    TtfPackSpec(
        pack_id="fusion_pixel_10px_monospaced_latin",
        display_name="Fusion Pixel 10px Monospaced (latin)",
        font_path=FONT_DIR / "fusion-pixel-10px-monospaced-latin.ttf",
        font_size=10,
        baseline=9,
        default_advance=10,
        supported_ranges=SUPPORTED_RANGES,
    ),
    TtfPackSpec(
        pack_id="fusion_pixel_10px_monospaced_zh_hans",
        display_name="Fusion Pixel 10px Monospaced (zh_hans)",
        font_path=FONT_DIR / "fusion-pixel-10px-monospaced-zh_hans.ttf",
        font_size=10,
        baseline=9,
        default_advance=10,
        supported_ranges=SUPPORTED_RANGES,
    ),
    TtfPackSpec(
        pack_id="fusion_pixel_10px_proportional_latin",
        display_name="Fusion Pixel 10px Proportional (latin)",
        font_path=FONT_DIR / "fusion-pixel-10px-proportional-latin.ttf",
        font_size=10,
        baseline=9,
        default_advance=6,
        supported_ranges=SUPPORTED_RANGES,
    ),
    TtfPackSpec(
        pack_id="fusion_pixel_10px_proportional_zh_hans",
        display_name="Fusion Pixel 10px Proportional (zh_hans)",
        font_path=FONT_DIR / "fusion-pixel-10px-proportional-zh_hans.ttf",
        font_size=10,
        baseline=9,
        default_advance=10,
        supported_ranges=SUPPORTED_RANGES,
    ),
    TtfPackSpec(
        pack_id="fusion_pixel_12px_monospaced_latin",
        display_name="Fusion Pixel 12px Monospaced (latin)",
        font_path=FONT_DIR / "fusion-pixel-12px-monospaced-latin.ttf",
        font_size=12,
        baseline=10,
        default_advance=12,
        supported_ranges=SUPPORTED_RANGES,
    ),
    TtfPackSpec(
        pack_id="fusion_pixel_12px_monospaced_zh_hans",
        display_name="Fusion Pixel 12px Monospaced (zh_hans)",
        font_path=FONT_DIR / "fusion-pixel-12px-monospaced-zh_hans.ttf",
        font_size=12,
        baseline=10,
        default_advance=12,
        supported_ranges=SUPPORTED_RANGES,
    ),
    TtfPackSpec(
        pack_id="fusion_pixel_12px_proportional_latin",
        display_name="Fusion Pixel 12px Proportional (latin)",
        font_path=FONT_DIR / "fusion-pixel-12px-proportional-latin.ttf",
        font_size=12,
        baseline=11,
        default_advance=8,
        supported_ranges=SUPPORTED_RANGES,
    ),
    TtfPackSpec(
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


def ttf_pack_spec(
    family_id: str,
    display_name: str,
    font_path: Path,
    nominal_size: int,
    cell_height: int,
    baseline: int,
    default_advance: int,
    width_mode: str = "proportional",
) -> TtfPackSpec:
    """创建一个第三方 TTF/OTF 内置包定义。"""

    return TtfPackSpec(
        pack_id=f"{family_id}_{nominal_size}px_{width_mode}",
        display_name=f"{display_name} {nominal_size}px {width_mode.title()}",
        font_path=font_path,
        font_size=nominal_size,
        baseline=baseline,
        default_advance=default_advance,
        supported_ranges=[
            RangeSpec(0x0020, 0xD7FF),
            RangeSpec(0xE000, 0xFFFD),
        ],
        cell_height=cell_height,
    )


# 非 Fusion 的轮廓字体同时生成原生字号和固定 chrome 使用的 10px 字号。
ADDITIONAL_TTF_PACKS = [
    ttf_pack_spec(
        family_id="cubic_11",
        display_name="Cubic 11",
        font_path=FONT_SOURCE_DIR / "cubic_11" / "1.500" / "Cubic_11.ttf",
        nominal_size=10,
        cell_height=10,
        baseline=8,
        default_advance=7,
    ),
    ttf_pack_spec(
        family_id="cubic_11",
        display_name="Cubic 11",
        font_path=FONT_SOURCE_DIR / "cubic_11" / "1.500" / "Cubic_11.ttf",
        nominal_size=11,
        cell_height=14,
        baseline=10,
        default_advance=8,
    ),
    ttf_pack_spec(
        family_id="boutique_7",
        display_name="Boutique Bitmap 7x7",
        font_path=FONT_SOURCE_DIR / "boutique_bitmap_7" / "current-2026.03.30" / "BoutiqueBitmap7x7.ttf",
        nominal_size=7,
        cell_height=8,
        baseline=6,
        default_advance=4,
    ),
    ttf_pack_spec(
        family_id="boutique_7",
        display_name="Boutique Bitmap 7x7",
        font_path=FONT_SOURCE_DIR / "boutique_bitmap_7" / "current-2026.03.30" / "BoutiqueBitmap7x7.ttf",
        nominal_size=10,
        cell_height=10,
        baseline=8,
        default_advance=6,
    ),
    ttf_pack_spec(
        family_id="boutique_9",
        display_name="Boutique Bitmap 9x9",
        font_path=FONT_SOURCE_DIR / "boutique_bitmap_9" / "1.93" / "BoutiqueBitmap9x9_1.93.ttf",
        nominal_size=9,
        cell_height=11,
        baseline=8,
        default_advance=6,
    ),
    ttf_pack_spec(
        family_id="boutique_9",
        display_name="Boutique Bitmap 9x9",
        font_path=FONT_SOURCE_DIR / "boutique_bitmap_9" / "1.93" / "BoutiqueBitmap9x9_1.93.ttf",
        nominal_size=10,
        cell_height=10,
        baseline=7,
        default_advance=6,
    ),
    ttf_pack_spec(
        family_id="gnu_unifont",
        display_name="GNU Unifont",
        font_path=FONT_SOURCE_DIR / "gnu_unifont" / "17.0.04" / "unifont-17.0.04.otf",
        nominal_size=10,
        cell_height=10,
        baseline=8,
        default_advance=5,
        width_mode="monospaced",
    ),
    ttf_pack_spec(
        family_id="pix32",
        display_name="Pix32",
        font_path=FONT_SOURCE_DIR / "pix32" / "1.9.7" / "Pixel32.v1.9.7.ttf",
        nominal_size=10,
        cell_height=10,
        baseline=8,
        default_advance=5,
        width_mode="monospaced",
    ),
    ttf_pack_spec(
        family_id="pix32",
        display_name="Pix32",
        font_path=FONT_SOURCE_DIR / "pix32" / "1.9.7" / "Pixel32.v1.9.7.ttf",
        nominal_size=12,
        cell_height=14,
        baseline=11,
        default_advance=6,
        width_mode="monospaced",
    ),
]


ADDITIONAL_BDF_PACKS = [
    BdfPackSpec(
        pack_id="gnu_unifont_16px_monospaced",
        display_name="GNU Unifont 16px Monospaced",
        font_path=FONT_SOURCE_DIR / "gnu_unifont" / "17.0.04" / "unifont-17.0.04.bdf.gz",
        cell_height=16,
        baseline=14,
        default_advance=8,
        supported_ranges=[
            RangeSpec(0x0020, 0xD7FF),
            RangeSpec(0xE000, 0xFFFD),
        ],
    ),
]


def main(argv: Sequence[str] | None = None) -> None:
    parser = build_argument_parser()
    args = parser.parse_args(argv)
    if args.input is None:
        OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
        ttf_packs, bdf_packs, dot_grid_packs = load_catalog_pack_specs()
        for pack in ttf_packs:
            generate_ttf_builtin_pack(pack)
        for pack in bdf_packs:
            generate_bdf_builtin_pack(pack)
        for pack in dot_grid_packs:
            generate_dot_grid_builtin_pack(pack)
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
    elif suffix == ".bdf" or input_path.name.lower().endswith(".bdf.gz"):
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


def generate_ttf_builtin_pack(spec: TtfPackSpec) -> None:
    """把仓库内置 TTF/OTF 源转换成 engine 的稳定二进制字形包。"""

    generate_ttf_pack(
        font_path=spec.font_path,
        output_dir=OUTPUT_DIR,
        pack_id=spec.pack_id,
        display_name=spec.display_name,
        font_size=spec.font_size,
        cell_height=spec.cell_height or spec.font_size,
        baseline=spec.baseline,
        default_advance=spec.default_advance,
        supported_ranges=spec.supported_ranges,
        allowed_advances=spec.allowed_advances,
        shift_negative_bearing=spec.shift_negative_bearing,
    )


def load_catalog_pack_specs() -> tuple[list[TtfPackSpec], list[BdfPackSpec], list[DotGridPackSpec]]:
    """从唯一字体目录展开全部内置 pack，并校验字体源摘要。"""

    catalog_path = ROOT_DIR / "fonts" / "font_catalog.json"
    catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
    range_sets = {
        key: [parse_range_label(label) for label in labels]
        for key, labels in catalog["rangeSets"].items()
    }
    ttf_packs: list[TtfPackSpec] = []
    bdf_packs: list[BdfPackSpec] = []
    dot_grid_packs: list[DotGridPackSpec] = []
    for family in catalog["families"]:
        for face in family["faces"]:
            allowed_advances = tuple(face["allowedAdvances"]) if "allowedAdvances" in face else None
            for pack in face["packs"]:
                source = ROOT_DIR / pack["source"]
                digest = hashlib.sha256(source.read_bytes()).hexdigest()
                if digest != pack["sourceSha256"]:
                    raise ValueError(f"font source checksum mismatch: {pack['id']}")
                common = {
                    "pack_id": pack["id"],
                    "display_name": f"{family['label']} {face['size']}px {face['width'].title()}",
                    "font_path": source,
                    "cell_height": face["cellHeight"],
                    "baseline": face["baseline"],
                    "default_advance": pack["defaultAdvance"],
                    "supported_ranges": range_sets[pack["rangeSet"]],
                }
                if pack["type"] in {"ttf", "otf"}:
                    ttf_packs.append(
                        TtfPackSpec(
                            font_size=pack["fontSize"],
                            allowed_advances=allowed_advances,
                            shift_negative_bearing=family.get("negativeBearingPolicy") == "shift",
                            **common,
                        ),
                    )
                elif pack["type"] == "bdf":
                    bdf_packs.append(BdfPackSpec(**common))
                elif pack["type"] == "dot_grid_otf":
                    dot_grid_packs.append(
                        DotGridPackSpec(
                            grid_height=pack["gridHeight"],
                            **common,
                        ),
                    )
                else:
                    raise ValueError(f"unsupported catalog pack type: {pack['type']}")
    return ttf_packs, bdf_packs, dot_grid_packs


def parse_range_label(label: str) -> RangeSpec:
    """把 catalog 的十六进制范围文本转换为生成器范围对象。"""

    bounds = label.split("-", maxsplit=1)
    return RangeSpec(start=int(bounds[0], 16), end=int(bounds[-1], 16))


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


def generate_dot_grid_builtin_pack(spec: DotGridPackSpec) -> None:
    """从点阵矢量 OTF 的轮廓中心恢复一源点一像素字形包。"""

    generate_dot_grid_pack(
        font_path=spec.font_path,
        output_dir=OUTPUT_DIR,
        pack_id=spec.pack_id,
        display_name=spec.display_name,
        grid_height=spec.grid_height,
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
    allowed_advances: tuple[int, ...] | None = None,
    shift_negative_bearing: bool = False,
) -> None:
    from PIL import ImageFont
    from fontTools.ttLib import TTFont

    font = ImageFont.truetype(
        str(font_path),
        size=font_size,
        layout_engine=ImageFont.Layout.BASIC,
    )

    # 字体 cmap 中真实存在的 Unicode 码点，避免把 .notdef 方框写成每个缺失字符。
    source_font = TTFont(font_path, lazy=True)
    try:
        source_code_points = set((source_font.getBestCmap() or {}).keys())
    finally:
        source_font.close()

    records = []
    for code_point in iter_code_points(supported_ranges):
        if code_point not in source_code_points:
            continue
        character = chr(code_point)
        rendered = render_font_glyph(
            font=font,
            character=character,
            cell_height=cell_height,
            baseline=baseline,
            shift_negative_bearing=shift_negative_bearing,
        )
        if rendered is None:
            continue
        if not any(rendered.pixels) and not character.isspace():
            continue

        if allowed_advances is not None and rendered.advance_width not in allowed_advances:
            raise ValueError(
                f"{pack_id} U+{code_point:04X} advance {rendered.advance_width} "
                f"is outside declared mono grid {allowed_advances}",
            )

        records.append(
            (
                code_point,
                rendered.advance_width,
                rendered.width,
                pack_bits(rendered.pixels),
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


def generate_dot_grid_pack(
    font_path: Path,
    output_dir: Path,
    pack_id: str,
    display_name: str,
    grid_height: int,
    cell_height: int,
    baseline: int,
    default_advance: int,
    supported_ranges: list[RangeSpec],
) -> None:
    """把每个独立点轮廓恢复到原始整数网格，避免低字号轮廓采样丢点。"""

    from fontTools.pens.recordingPen import RecordingPen
    from fontTools.ttLib import TTFont

    source_font = TTFont(font_path, lazy=False)
    try:
        source_code_points = source_font.getBestCmap() or {}
        glyph_set = source_font.getGlyphSet()
        units_per_em = float(source_font["head"].unitsPerEm)
        grid_unit = units_per_em / grid_height
        grid_top = float(source_font["hhea"].ascent)
        records: list[tuple[int, int, int, bytes]] = []
        for code_point in iter_code_points(supported_ranges):
            glyph_name = source_code_points.get(code_point)
            if glyph_name is None:
                continue
            glyph = glyph_set[glyph_name]
            advance_width = grid_coordinate(glyph.width / grid_unit, pack_id, code_point, "advance")
            advance_width = max(1, advance_width)
            pen = RecordingPen()
            glyph.draw(pen)
            points = dot_grid_points(
                operations=pen.value,
                grid_unit=grid_unit,
                grid_top=grid_top,
                pack_id=pack_id,
                code_point=code_point,
            )
            if not points and not chr(code_point).isspace():
                continue
            max_x = max((point[0] for point in points), default=-1)
            bitmap_width = max(1, advance_width, max_x + 1)
            pixels = bytearray(bitmap_width * cell_height)
            for x, y in points:
                if x < 0 or x >= bitmap_width or y < 0 or y >= cell_height:
                    raise ValueError(
                        f"{pack_id} U+{code_point:04X} dot ({x},{y}) exceeds "
                        f"{bitmap_width}x{cell_height} grid",
                    )
                pixels[(y * bitmap_width) + x] = 1
            records.append((code_point, advance_width, bitmap_width, pack_bits(pixels)))
    finally:
        source_font.close()

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


def dot_grid_points(
    operations: list[tuple[str, tuple[Any, ...]]],
    grid_unit: float,
    grid_top: float,
    pack_id: str,
    code_point: int,
) -> set[tuple[int, int]]:
    """把 RecordingPen 的每个封闭轮廓中心映射为一个逻辑点。"""

    contours: list[list[tuple[float, float]]] = []
    current: list[tuple[float, float]] = []
    for operation, arguments in operations:
        if operation == "moveTo" and current:
            contours.append(current)
            current = []
        if operation in {"moveTo", "lineTo", "curveTo", "qCurveTo"}:
            current.extend(point for point in arguments if isinstance(point, tuple))
        elif operation in {"closePath", "endPath"} and current:
            contours.append(current)
            current = []
        elif operation == "addComponent":
            raise ValueError(f"{pack_id} U+{code_point:04X} contains unsupported component contour")
    if current:
        contours.append(current)

    points: set[tuple[int, int]] = set()
    for contour in contours:
        left = min(point[0] for point in contour)
        right = max(point[0] for point in contour)
        bottom = min(point[1] for point in contour)
        top = max(point[1] for point in contour)
        center_x = (left + right) / 2.0
        center_y = (bottom + top) / 2.0
        x = grid_coordinate((center_x / grid_unit) - 0.5, pack_id, code_point, "x")
        y = grid_coordinate(((grid_top - center_y) / grid_unit) - 0.5, pack_id, code_point, "y")
        if (x, y) in points:
            raise ValueError(f"{pack_id} U+{code_point:04X} has duplicate dot ({x},{y})")
        points.add((x, y))
    return points


def grid_coordinate(value: float, pack_id: str, code_point: int, axis: str) -> int:
    """把接近整数的轮廓坐标收敛到网格，并拒绝非点阵轮廓。"""

    rounded = int(round(value))
    if abs(value - rounded) > DOT_GRID_COORDINATE_TOLERANCE:
        raise ValueError(
            f"{pack_id} U+{code_point:04X} {axis} coordinate {value:.4f} is off the dot grid",
        )
    return rounded


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
    """解析普通或 gzip 压缩的 BDF 源文件。"""

    glyphs: list[BdfGlyph] = []
    current: dict[str, Any] | None = None
    reading_bitmap = False
    if font_path.suffix == ".gz":
        with gzip.open(font_path, mode="rt", encoding="utf-8") as compressed_file:
            source_lines = compressed_file.read().splitlines()
    else:
        source_lines = font_path.read_text(encoding="utf-8").splitlines()
    for raw_line in source_lines:
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
    shift_negative_bearing: bool = False,
) -> RenderedTtfGlyph | None:
    from PIL import Image, ImageDraw

    try:
        ascent, _ = font.getmetrics()
        draw_y = baseline - ascent
        measure_image = Image.new("1", (1, 1), 0)
        measure_draw = ImageDraw.Draw(measure_image)
        measure_draw.fontmode = "1"
        bbox = measure_draw.textbbox((0, draw_y), character, font=font)
        if bbox is None:
            return None
        if bbox[0] < 0 and not shift_negative_bearing:
            raise ValueError(f"negative left bearing {bbox[0]} for U+{ord(character):04X}")
        draw_x = -bbox[0] if bbox[0] < 0 else 0
        advance_width = max(1, int(round(float(font.getlength(character)))))
        bitmap_width = max(1, advance_width, bbox[2] + draw_x)
        image = Image.new("1", (bitmap_width, cell_height), 0)
        draw = ImageDraw.Draw(image)
        draw.fontmode = "1"
        draw.text((draw_x, draw_y), character, font=font, fill=1)
    except OSError:
        return None

    pixels = bytes(
        1 if image.getpixel((x, y)) else 0
        for y in range(cell_height)
        for x in range(bitmap_width)
    )
    return RenderedTtfGlyph(pixels=pixels, width=bitmap_width, advance_width=advance_width)


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
    # 固定按完整 Unicode scalar 排序，供 indexed loader 二分检索并保证输出确定性。
    records = sorted(records, key=lambda record: record[0])
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
