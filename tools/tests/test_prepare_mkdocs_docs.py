"""验证 MkDocs 统一源码树的清理、路径和输入失败语义。"""

from __future__ import annotations

import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path


# 仓库根目录用于动态加载生产脚本。
ROOT = Path(__file__).resolve().parents[2]
# 生产脚本路径不依赖 Python 包安装。
SCRIPT = ROOT / "tools" / "prepare_mkdocs_docs.py"
# 动态模块规格用于直接测试函数契约。
SPEC = importlib.util.spec_from_file_location("prepare_mkdocs_docs", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
# 实际加载的生产模块。
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class PrepareMkdocsDocsTest(unittest.TestCase):
    """覆盖双文档根、旧页面清理和缺失输入。"""

    def test_preserves_repository_relative_paths_and_cleans_output(self) -> None:
        """两个文档根应原路径复制，并删除上一次遗留页面。"""

        with tempfile.TemporaryDirectory() as directory:
            # 临时仓库同时模拟根文档和 SDK 文档。
            root = Path(directory)
            (root / "docs").mkdir()
            (root / "pixel-engine/docs/guides").mkdir(parents=True)
            (root / "docs/project.md").write_text("project\n", encoding="utf-8")
            (root / "pixel-engine/docs/guides/quickstart.md").write_text("sdk\n", encoding="utf-8")
            # 旧页面必须在本轮生成前删除。
            output = root / "build/mkdocs-source"
            output.mkdir(parents=True)
            (output / "stale.md").write_text("stale\n", encoding="utf-8")
            count = MODULE.prepare_document_tree(root, output)
            self.assertEqual(2, count)
            self.assertFalse((output / "stale.md").exists())
            self.assertTrue((output / "docs/project.md").is_file())
            self.assertTrue((output / "pixel-engine/docs/guides/quickstart.md").is_file())

    def test_main_rejects_missing_sdk_document_root(self) -> None:
        """缺少 SDK 文档根时不能生成看似成功的部分站点。"""

        with tempfile.TemporaryDirectory() as directory:
            # 只创建根文档，故意缺少 pixel-engine/docs。
            root = Path(directory)
            (root / "docs").mkdir()
            exit_code = MODULE.main(["--root", str(root)])
            self.assertEqual(2, exit_code)


if __name__ == "__main__":
    unittest.main()
