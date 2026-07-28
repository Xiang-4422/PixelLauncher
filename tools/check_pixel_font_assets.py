#!/usr/bin/env python3
"""校验 catalog、已生成 glyphpacks 与确定性摘要锁文件。"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any


ROOT_DIR = Path(__file__).resolve().parents[1]
CATALOG_PATH = ROOT_DIR / "fonts" / "font_catalog.json"
LOCK_PATH = ROOT_DIR / "fonts" / "font_assets.lock.json"
PACK_ROOT = ROOT_DIR / "app" / "src" / "main" / "assets" / "glyphpacks"


def main() -> None:
    """生成当前资源快照，或与已提交锁文件做精确比较。"""

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write-lock", action="store_true")
    args = parser.parse_args()
    snapshot = build_snapshot()
    rendered = json.dumps(snapshot, indent=2, sort_keys=True, ensure_ascii=False) + "\n"
    if args.write_lock:
        LOCK_PATH.write_text(rendered, encoding="utf-8")
        return
    current = LOCK_PATH.read_text(encoding="utf-8") if LOCK_PATH.is_file() else ""
    if current != rendered:
        raise SystemExit("字体资源摘要已漂移，请审阅后运行 check_pixel_font_assets.py --write-lock")


def build_snapshot() -> dict[str, Any]:
    """验证每个 face 的 manifest、二进制和孤立资源，并返回摘要。"""

    catalog_bytes = CATALOG_PATH.read_bytes()
    catalog = json.loads(catalog_bytes)
    declared: dict[str, tuple[int, int, int]] = {}
    for family in catalog["families"]:
        for face in family["faces"]:
            for pack in face["packs"]:
                pack_id = pack["id"]
                if pack_id in declared:
                    raise ValueError(f"重复 pack: {pack_id}")
                declared[pack_id] = (face["cellHeight"], face["baseline"], pack["defaultAdvance"])
    actual = {path.name for path in PACK_ROOT.iterdir() if path.is_dir()}
    if actual != set(declared):
        raise ValueError(f"glyphpacks 与 catalog 不一致: missing={set(declared)-actual}, orphan={actual-set(declared)}")
    packs: dict[str, dict[str, str]] = {}
    for pack_id, (cell_height, baseline, default_advance) in sorted(declared.items()):
        directory = PACK_ROOT / pack_id
        manifest_path = directory / "manifest.json"
        binary_path = directory / "glyphs.bin"
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        if (
            manifest["packId"] != pack_id
            or manifest["cellHeight"] != cell_height
            or manifest["baseline"] != baseline
            or manifest["defaultAdvance"] != default_advance
        ):
            raise ValueError(f"manifest metrics 与 catalog 不一致: {pack_id}")
        packs[pack_id] = {
            "manifestSha256": digest(manifest_path),
            "glyphsSha256": digest(binary_path),
        }
    return {
        "catalogSha256": hashlib.sha256(catalog_bytes).hexdigest(),
        "packs": packs,
    }


def digest(path: Path) -> str:
    """返回文件内容 SHA-256 十六进制文本。"""

    return hashlib.sha256(path.read_bytes()).hexdigest()


if __name__ == "__main__":
    main()
