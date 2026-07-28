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
            code_point, advance, width, data_length = struct.unpack_from(">IIII", binary, 16)
            packed = binary[32 : 32 + data_length]
            pixels = unpack_bits(packed, width * cell_height)

            self.assertEqual(0x50474C59, magic)
            self.assertEqual(1, version)
            self.assertEqual(8, cell_height)
            self.assertEqual(1, glyph_count)
            self.assertEqual(0x41, code_point)
            self.assertEqual(6, advance)
            self.assertEqual(6, width)
            self.assertEqual("011100", pixels[0:6])
            self.assertEqual("100010", pixels[6:12])
            self.assertEqual("000000", pixels[-6:])

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

        from PIL import ImageFont

        font = ImageFont.truetype(
            "app/src/main/assets/fonts/fusion-pixel-8px-monospaced-latin.ttf",
            size=8,
            layout_engine=ImageFont.Layout.BASIC,
        )
        rendered = converter.render_font_glyph(font, "i", cell_height=8, baseline=7)

        self.assertIsNotNone(rendered)
        assert rendered is not None
        self.assertEqual(4, rendered.advance_width)
        self.assertEqual(4, rendered.width)
        self.assertTrue(any(rendered.pixels))
        self.assertTrue(
            any(rendered.pixels[row * rendered.width] for row in range(8)),
            "logical origin column should retain the glyph's left-side ink",
        )

    def test_negative_bearing_requires_explicit_catalog_policy(self) -> None:
        """负 bearing 默认失败，显式 shift 策略才允许写入 v1 bitmap。"""

        from PIL import ImageFont

        font = ImageFont.truetype(
            "tools/font_sources/cubic_11/1.500/Cubic_11.ttf",
            size=10,
            layout_engine=ImageFont.Layout.BASIC,
        )
        with self.assertRaisesRegex(ValueError, "negative left bearing"):
            converter.render_font_glyph(font, "+", cell_height=10, baseline=8)

        shifted = converter.render_font_glyph(
            font,
            "+",
            cell_height=10,
            baseline=8,
            shift_negative_bearing=True,
        )
        self.assertIsNotNone(shifted)


def unpack_bits(packed: bytes, pixel_count: int) -> str:
    return "".join(
        "1" if packed[index // 8] & (1 << (7 - (index % 8))) else "0"
        for index in range(pixel_count)
    )


if __name__ == "__main__":
    unittest.main()
