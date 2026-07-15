#!/usr/bin/env python3
"""为 MkDocs 生成保留仓库相对路径的统一 Markdown 源码树。"""

from __future__ import annotations

import argparse
import shutil
import sys
from pathlib import Path
from typing import Sequence


# 只有这两个受审文档根会进入发布站点，避免把构建报告或源码误当成页面。
DOCUMENT_ROOTS = (Path("docs"), Path("pixel-engine/docs"))


def prepare_document_tree(repository_root: Path, output_root: Path) -> int:
    """清理并复制全部 Markdown 页面，返回生成页面数。"""

    # 缺少任一权威文档根都属于配置错误，不能生成部分站点。
    missing_roots = [repository_root / relative for relative in DOCUMENT_ROOTS if not (repository_root / relative).is_dir()]
    if missing_roots:
        raise FileNotFoundError(", ".join(str(path) for path in missing_roots))
    # 构建目录不作为源码，必须每次重建以删除已移除页面。
    if output_root.exists():
        shutil.rmtree(output_root)
    output_root.mkdir(parents=True)
    # 页面数量用于测试和 CI 日志确认站点不是空壳。
    page_count = 0
    for relative_root in DOCUMENT_ROOTS:
        source_root = repository_root / relative_root
        for source in sorted(source_root.rglob("*.md")):
            # 保留仓库相对路径，确保跨 docs/pixel-engine/docs 链接继续指向同一页面。
            destination = output_root / source.relative_to(repository_root)
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, destination)
            page_count += 1
    return page_count


def parse_args(argv: Sequence[str]) -> argparse.Namespace:
    """解析仓库根和生成目录参数。"""

    # 默认值与 mkdocs.yml、CI 和本地脚本保持一致。
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--output", type=Path, default=Path("build/mkdocs-source"))
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    """生成统一文档树，并在输入缺失时明确失败。"""

    # 显式 argv 便于单元测试不依赖进程参数。
    args = parse_args(sys.argv[1:] if argv is None else argv)
    # 相对输出路径以仓库根为基准，避免从其他工作目录执行时漂移。
    output_root = args.output if args.output.is_absolute() else args.root / args.output
    try:
        page_count = prepare_document_tree(args.root.resolve(), output_root.resolve())
    except FileNotFoundError as error:
        print(f"MkDocs source root missing: {error}", file=sys.stderr)
        return 2
    print(f"Prepared {page_count} Markdown pages in {output_root}.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
