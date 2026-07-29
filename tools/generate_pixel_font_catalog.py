#!/usr/bin/env python3
"""校验唯一字体目录，并生成 Launcher 只读 Kotlin catalog。"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path
from typing import Any


ROOT_DIR = Path(__file__).resolve().parents[1]
CATALOG_PATH = ROOT_DIR / "fonts" / "font_catalog.json"
OUTPUT_PATH = (
    ROOT_DIR
    / "app"
    / "src"
    / "main"
    / "kotlin"
    / "com"
    / "purride"
    / "pixellauncherv2"
    / "launcher"
    / "GeneratedPixelFontCatalog.kt"
)
ID_PATTERN = re.compile(r"^[a-z][a-z0-9_]*$")


def main() -> None:
    """解析命令行，生成或校验 Kotlin catalog。"""

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalog", type=Path, default=CATALOG_PATH)
    parser.add_argument("--output", type=Path, default=OUTPUT_PATH)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    catalog = load_and_validate(args.catalog)
    rendered = render_kotlin(catalog)
    if args.check:
        current = args.output.read_text(encoding="utf-8") if args.output.is_file() else ""
        if current != rendered:
            raise SystemExit("GeneratedPixelFontCatalog.kt 已过期，请运行 generatePixelFontCatalog")
        return
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(rendered, encoding="utf-8")


def load_and_validate(path: Path) -> dict[str, Any]:
    """读取目录并验证所有稳定 ID、默认 face、资源源文件和组件字号。"""

    root = json.loads(path.read_text(encoding="utf-8"))
    if root.get("catalogVersion") != 1:
        raise ValueError("catalogVersion 必须为 1")
    range_sets = root.get("rangeSets")
    families = root.get("families")
    if not isinstance(range_sets, dict) or not isinstance(families, list) or not families:
        raise ValueError("rangeSets 和 families 必须非空")
    family_ids: set[str] = set()
    pack_ids: set[str] = set()
    for family in families:
        family_id = require_id(family.get("id"), "family id")
        if family_id in family_ids:
            raise ValueError(f"重复 family id: {family_id}")
        family_ids.add(family_id)
        faces = family.get("faces")
        if not isinstance(faces, list) or not faces:
            raise ValueError(f"{family_id} 没有 face")
        face_keys: set[tuple[str, int]] = set()
        for face in faces:
            width = face.get("width")
            size = face.get("size")
            if width not in {"proportional", "monospaced"} or not isinstance(size, int) or size <= 0:
                raise ValueError(f"{family_id} face width/size 非法")
            key = (width, size)
            if key in face_keys:
                raise ValueError(f"{family_id} 重复 face: {key}")
            face_keys.add(key)
            validate_metrics(family_id, face)
            packs = face.get("packs")
            if not isinstance(packs, list) or not packs:
                raise ValueError(f"{family_id} {key} 没有 pack")
            for pack in packs:
                pack_id = require_id(pack.get("id"), "pack id")
                if pack_id in pack_ids:
                    raise ValueError(f"pack 只能属于一个 face: {pack_id}")
                pack_ids.add(pack_id)
                source = ROOT_DIR / str(pack.get("source", ""))
                if not source.is_file():
                    raise ValueError(f"字体源不存在: {source}")
                digest = hashlib.sha256(source.read_bytes()).hexdigest()
                if digest != pack.get("sourceSha256"):
                    raise ValueError(f"字体源摘要不匹配: {pack_id}")
                if pack.get("rangeSet") not in range_sets:
                    raise ValueError(f"未知 rangeSet: {pack_id}")
                pack_type = pack.get("type")
                if pack_type not in {"ttf", "otf", "bdf", "dot_grid_otf"}:
                    raise ValueError(f"未知字体源类型: {pack_id}/{pack_type}")
                if pack_type == "dot_grid_otf":
                    grid_height = pack.get("gridHeight")
                    if not isinstance(grid_height, int) or grid_height <= 0:
                        raise ValueError(f"点阵轮廓必须声明正整数 gridHeight: {pack_id}")
                    expected_counts = pack.get("expectedPixelCounts")
                    if not isinstance(expected_counts, dict) or not expected_counts:
                        raise ValueError(f"点阵轮廓必须声明逐像素审阅样例: {pack_id}")
                    for code_point, pixel_count in expected_counts.items():
                        if not re.fullmatch(r"[0-9A-F]{4,6}", code_point):
                            raise ValueError(f"逐像素样例码点非法: {pack_id}/{code_point}")
                        if not isinstance(pixel_count, int) or pixel_count <= 0:
                            raise ValueError(f"逐像素样例数量非法: {pack_id}/{code_point}")
        default_key = (family.get("defaultWidth"), family.get("defaultSize"))
        if default_key not in face_keys or not next(
            face["settingsVisible"] for face in faces if (face["width"], face["size"]) == default_key
        ):
            raise ValueError(f"{family_id} 默认 face 不可设置")
        for width in {face["width"] for face in faces if face["settingsVisible"]}:
            if not any(face["width"] == width and "chrome" in face.get("roles", []) for face in faces):
                raise ValueError(f"{family_id}/{width} 缺少 chrome face")
    return root


def require_id(value: Any, label: str) -> str:
    """返回符合跨版本稳定格式的字符串 ID。"""

    if not isinstance(value, str) or not ID_PATTERN.fullmatch(value):
        raise ValueError(f"{label} 非法: {value!r}")
    return value


def validate_metrics(family_id: str, face: dict[str, Any]) -> None:
    """验证 face 的像素尺寸、基线和缺字 advance。"""

    cell_height = face.get("cellHeight")
    baseline = face.get("baseline")
    narrow = face.get("narrowAdvance")
    wide = face.get("wideAdvance")
    if not all(isinstance(value, int) and value > 0 for value in (cell_height, narrow, wide)):
        raise ValueError(f"{family_id} metrics 必须为正整数")
    if not isinstance(baseline, int) or baseline < 0 or baseline >= cell_height:
        raise ValueError(f"{family_id} baseline 越界")


def render_kotlin(root: dict[str, Any]) -> str:
    """把已验证目录渲染为确定性的 Kotlin 描述符。"""

    lines = [
        "// 由 tools/generate_pixel_font_catalog.py 生成；禁止手工修改。",
        "package com.purride.pixellauncherv2.launcher",
        "",
        "/** 由 fonts/font_catalog.json 生成的只读字体目录。 */",
        "internal object GeneratedPixelFontCatalog {",
        "    /** 按设置页展示顺序排列的全部字体家族。 */",
        "    val families: List<FontFamilyDescriptor> = listOf(",
    ]
    for family in root["families"]:
        family_id = family["id"]
        lines.extend(
            [
                "        FontFamilyDescriptor(",
                f'            id = LauncherFontFamily("{family_id}"),',
                f'            constantName = "{family["constant"]}",',
                f'            displayLabel = {quote(family["label"])},',
                f'            assetFamilyId = "{family["assetFamilyId"]}",',
                f'            sourceVersion = "{family["sourceVersion"]}",',
                f'            licenseId = "{family["licenseId"]}",',
                "            defaultKey = FontFaceKey(",
                f'                family = LauncherFontFamily("{family_id}"),',
                f'                widthMode = {width_constant(family["defaultWidth"])},',
                f'                size = PixelFontSize({family["defaultSize"]}),',
                "            ),",
                "            faces = listOf(",
            ],
        )
        for face in family["faces"]:
            roles = ", ".join("LauncherTextRole.CHROME" for role in face.get("roles", []) if role == "chrome")
            lines.extend(
                [
                    "                FontFaceDescriptor(",
                    "                    key = FontFaceKey(",
                    f'                        family = LauncherFontFamily("{family_id}"),',
                    f'                        widthMode = {width_constant(face["width"])},',
                    f'                        size = PixelFontSize({face["size"]}),',
                    "                    ),",
                    f'                    settingsVisible = {str(face["settingsVisible"]).lower()},',
                    f"                    roles = setOf({roles}),",
                    "                    metrics = PixelFontMetrics(",
                    f'                        size = PixelFontSize({face["size"]}),',
                    f'                        cellHeight = {face["cellHeight"]},',
                    f'                        baseline = {face["baseline"]},',
                    f'                        narrowAdvanceWidth = {face["narrowAdvance"]},',
                    f'                        wideAdvanceWidth = {face["wideAdvance"]},',
                    "                    ),",
                    "                    packs = listOf(",
                ],
            )
            for pack in face["packs"]:
                lines.extend(
                    [
                        "                        FontPackDescriptor(",
                        f'                            id = "{pack["id"]}",',
                        f'                            assetDirectory = "glyphpacks/{pack["id"]}",',
                        f'                            sourceType = "{pack["type"]}",',
                        f'                            sourcePath = {quote(pack["source"])},',
                        f'                            sourceSha256 = "{pack["sourceSha256"]}",',
                        f'                            rangeSet = "{pack["rangeSet"]}",',
                        f'                            defaultAdvance = {pack["defaultAdvance"]},',
                        "                        ),",
                    ],
                )
            lines.extend(["                    ),", "                ),"])
        lines.extend(["            ),", "        ),"])
    lines.extend(["    )", "}", ""])
    return "\n".join(lines)


def width_constant(width: str) -> str:
    """把目录宽度 ID 映射到稳定 Kotlin 枚举。"""

    return (
        "LauncherFontWidthMode.PROPORTIONAL"
        if width == "proportional"
        else "LauncherFontWidthMode.MONOSPACED"
    )


def quote(value: str) -> str:
    """返回可直接写入 Kotlin 的 JSON 字符串字面量。"""

    return json.dumps(value, ensure_ascii=False)


if __name__ == "__main__":
    main()
