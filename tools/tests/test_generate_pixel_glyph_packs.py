from __future__ import annotations

import gzip
import json
import struct
import tempfile
import unittest
from pathlib import Path

from tools import generate_pixel_glyph_packs as converter


SAMPLE_BDF = """STARTFONT 2.1
FONT sample
SIZE 8 75 75
FONTBOUNDINGBOX 6 8 0 0
CHARS 1
STARTCHAR LATIN_CAPITAL_LETTER_A
ENCODING 65
SWIDTH 500 0
DWIDTH 6 0
BBX 5 7 0 0
BITMAP
70
88
88
F8
88
88
88
ENDCHAR
ENDFONT
"""


class GlyphPackConverterTest(unittest.TestCase):
    def test_parse_ranges_accepts_singletons_and_intervals(self) -> None:
        ranges = converter.parse_ranges("0041,4E00-4E02")

        self.assertEqual([(0x41, 0x41), (0x4E00, 0x4E02)], [(item.start, item.end) for item in ranges])

    def test_bdf_cli_generates_parser_compatible_pack(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            source = root / "sample.bdf"
            output = root / "output"
            source.write_text(SAMPLE_BDF, encoding="ascii")

            converter.main(
                [
                    "--input",
                    str(source),
                    "--output",
                    str(output),
                    "--pack-id",
                    "sample_bdf",
                    "--display-name",
                    "Sample BDF",
                    "--cell-height",
                    "8",
                    "--baseline",
                    "7",
                    "--default-advance",
                    "6",
                    "--ranges",
                    "0041-0041",
                ],
            )

            pack_dir = output / "sample_bdf"
            manifest = json.loads((pack_dir / "manifest.json").read_text(encoding="utf-8"))
            binary = (pack_dir / "glyphs.bin").read_bytes()

            self.assertEqual("sample_bdf", manifest["packId"])
            self.assertEqual("Sample BDF", manifest["displayName"])
            self.assertEqual(8, manifest["cellHeight"])
            self.assertEqual(["0041-0041"], manifest["supportedRanges"])

            magic, version, cell_height, glyph_count = struct.unpack_from(">IIII", binary, 0)
            code_point, advance, offset_x, offset_y, width, height, data_length = struct.unpack_from(
                ">IiiiiiI", binary, 16,
            )
            packed = binary[44 : 44 + data_length]
            pixels = unpack_bits(packed, width * height)

            self.assertEqual(0x50474C59, magic)
            self.assertEqual(2, version)
            self.assertEqual(8, cell_height)
            self.assertEqual(1, glyph_count)
            self.assertEqual(0x41, code_point)
            self.assertEqual(6, advance)
            self.assertEqual(0, offset_x)
            self.assertEqual(0, offset_y)
            self.assertEqual(5, width)
            self.assertEqual(7, height)
            self.assertEqual("01110", pixels[0:5])
            self.assertEqual("10001", pixels[5:10])

    def test_bdf_rejects_incomplete_bitmap_rows(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            source = Path(temp_dir) / "broken.bdf"
            source.write_text(SAMPLE_BDF.replace("88\nENDCHAR", "ENDCHAR"), encoding="ascii")

            with self.assertRaisesRegex(ValueError, "bitmap rows"):
                converter.parse_bdf(source)

    def test_parse_bdf_accepts_gzip_source(self) -> None:
        """压缩 BDF 应与普通 BDF 产生相同码点和度量。"""

        with tempfile.TemporaryDirectory() as temp_dir:
            # GNU Unifont 官方仅需保存压缩 BDF，避免源码体积无谓膨胀。
            source = Path(temp_dir) / "sample.bdf.gz"
            with gzip.open(source, mode="wt", encoding="utf-8") as compressed_file:
                compressed_file.write(SAMPLE_BDF)

            glyphs = converter.parse_bdf(source)

            self.assertEqual(1, len(glyphs))
            self.assertEqual(65, glyphs[0].code_point)
            self.assertEqual(6, glyphs[0].advance_width)

    def test_ttf_render_preserves_real_advance_and_origin(self) -> None:
        """TTF 字形不能再被居中，也不能把墨迹宽度误当 advance。"""

        from fontTools.ttLib import TTFont

        font = TTFont("tools/font_sources/boutique_bitmap_7/current-2026.03.30/BoutiqueBitmap7x7.ttf")
        cmap = font.getBestCmap() or {}
        rendered = converter.render_outline_glyph(
            glyph_set=font.getGlyphSet(),
            glyph_name=cmap[ord("i")],
            units_per_em=font["head"].unitsPerEm,
            pixels_per_em=8,
            baseline=6,
            pack_id="boutique_7_test",
            code_point=ord("i"),
        )
        self.assertEqual(2, rendered.advance_width)
        self.assertTrue(any(rendered.pixels))
        self.assertEqual(0, rendered.bitmap_offset_x)
        font.close()

    def test_negative_bearing_is_preserved_as_v2_placement(self) -> None:
        """负 bearing 必须写入 placement，不能平移到 advance 单元内部。"""

        from fontTools.ttLib import TTFont

        font = TTFont("tools/font_sources/cubic_11/1.500/Cubic_11.ttf")
        cmap = font.getBestCmap() or {}
        negative = None
        for code_point, glyph_name in sorted(cmap.items()):
            rendered = converter.render_outline_glyph(
                glyph_set=font.getGlyphSet(),
                glyph_name=glyph_name,
                units_per_em=font["head"].unitsPerEm,
                pixels_per_em=12,
                baseline=10,
                pack_id="cubic_test",
                code_point=code_point,
            )
            if rendered.bitmap_offset_x < 0:
                negative = rendered
                break
        font.close()
        self.assertIsNotNone(negative)

    def test_outline_off_grid_requires_reviewed_exception(self) -> None:
        """原生网格之外的轮廓必须逐码点声明，禁止整个字体静默放宽。"""

        from fontTools.ttLib import TTFont

        font = TTFont("tools/font_sources/boutique_bitmap_9/1.93/BoutiqueBitmap9x9_1.93.ttf")
        cmap = font.getBestCmap() or {}
        arguments = {
            "glyph_set": font.getGlyphSet(),
            "glyph_name": cmap[0x8646],
            "units_per_em": font["head"].unitsPerEm,
            "pixels_per_em": 10,
            "baseline": 8,
            "pack_id": "boutique_9_test",
            "code_point": 0x8646,
        }
        with self.assertRaisesRegex(ValueError, "off the native grid"):
            converter.render_outline_glyph(**arguments)
        reviewed = converter.render_outline_glyph(**arguments, allow_off_grid=True)
        font.close()

        self.assertTrue(reviewed.used_reviewed_exception)
        self.assertTrue(any(reviewed.pixels))

    def test_dot_grid_otf_restores_every_source_dot_without_rasterization(self) -> None:
        """Dotted 的 A/中应按轮廓数恢复全部逻辑点，且重复生成字节一致。"""

        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            source = Path("tools/font_sources/dotted_songti/0.1/DottedSongtiSquareRegular.otf")
            ranges = [converter.RangeSpec(0x41, 0x41), converter.RangeSpec(0x4E2D, 0x4E2D)]
            for output_name in ("first", "second"):
                converter.generate_dot_grid_pack(
                    font_path=source,
                    output_dir=root / output_name,
                    pack_id="dotted_test",
                    display_name="Dotted Test",
                    grid_height=13,
                    cell_height=14,
                    baseline=10,
                    default_advance=9,
                    supported_ranges=ranges,
                )

            first = (root / "first" / "dotted_test" / "glyphs.bin").read_bytes()
            second = (root / "second" / "dotted_test" / "glyphs.bin").read_bytes()
            records = unpack_records(first)

            self.assertEqual(first, second)
            self.assertEqual((9, 9, 22), records[0x41])
            self.assertEqual((13, 13, 38), records[0x4E2D])

    def test_dot_grid_rejects_non_grid_coordinate(self) -> None:
        """无法映射到整数点阵的轮廓必须失败，禁止静默近似。"""

        with self.assertRaisesRegex(ValueError, "off the dot grid"):
            converter.grid_coordinate(2.25, "broken", 0x41, "x")


def unpack_bits(packed: bytes, pixel_count: int) -> str:
    return "".join(
        "1" if packed[index // 8] & (1 << (7 - (index % 8))) else "0"
        for index in range(pixel_count)
    )


def unpack_records(binary: bytes) -> dict[int, tuple[int, int, int]]:
    """返回测试关心的 code point、advance、宽度和亮点数量。"""

    _, version, _, glyph_count = struct.unpack_from(">IIII", binary, 0)
    if version != 2:
        raise AssertionError(f"expected PGLY v2, got {version}")
    offset = 16
    records: dict[int, tuple[int, int, int]] = {}
    for _ in range(glyph_count):
        code_point, advance, _, _, width, height, data_length = struct.unpack_from(">IiiiiiI", binary, offset)
        offset += 28
        packed = binary[offset : offset + data_length]
        offset += data_length
        pixels = unpack_bits(packed, width * height)
        records[code_point] = (advance, width, pixels.count("1"))
    return records


if __name__ == "__main__":
    unittest.main()
