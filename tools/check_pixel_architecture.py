#!/usr/bin/env python3
"""校验 Pixel Engine 规模预算与单模块工程文本边界。"""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Any, Sequence


# 仓库根目录由当前受版本控制脚本的位置稳定推导。
ROOT = Path(__file__).resolve().parents[1]
# 已删除模块名称不得重新进入工程治理文件。
REMOVED_MODULE_NAMES = (
    "pixel-android",
    "pixel-benchmark",
    "pixel-benchmark-target",
    "pixel-compose",
    "pixel-compose-sample",
    "pixel-core",
    "pixel-debug",
    "pixel-demo",
    "pixel-microbenchmark",
    "pixel-navigation",
    "pixel-runtime",
    "pixel-testing",
    "pixel-widgets",
)
# 旧九模块叙述不得继续误导单模块维护者。
STALE_ARCHITECTURE_PHRASES = (
    "九个 SDK",
    "九个项目",
    "九个模块",
    "九个 artifact",
    "拆分后九个",
    "旧聚合坐标",
    "兄弟 artifact",
)
# 仅扫描对工程结构作出承诺的源码与治理文件。
GOVERNANCE_ROOTS = (
    Path("build.gradle.kts"),
    Path("settings.gradle.kts"),
    Path("README.md"),
    Path("mkdocs.yml"),
    Path(".github/workflows"),
    Path("docs"),
    Path("pixel-engine/build.gradle.kts"),
    Path("pixel-engine/consumer-rules.pro"),
    Path("pixel-engine/docs"),
    Path("tools"),
)
# 治理扫描只读取这些可审查文本后缀。
TEXT_SUFFIXES = {".kt", ".kts", ".md", ".pro", ".py", ".sh", ".yml", ".yaml"}
# 检查器及其测试必须声明禁止模式本身，因此不参与模式内容扫描。
PATTERN_DEFINITION_FILES = {
    Path("tools/check_pixel_architecture.py"),
    Path("tools/tests/test_check_pixel_architecture.py"),
}


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    """解析仓库、预算和报告路径。"""

    # 命令行解析器允许工具测试使用完全隔离的临时仓库。
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument(
        "--budget",
        type=Path,
        default=Path("pixel-engine/config/architecture-budget.json"),
    )
    parser.add_argument(
        "--report",
        type=Path,
        default=Path("pixel-engine/build/reports/architecture/architecture-governance.json"),
    )
    return parser.parse_args(argv)


def resolve_from_root(root: Path, path: Path) -> Path:
    """把相对路径稳定解析到指定仓库根。"""

    return path if path.is_absolute() else root / path


def load_budget(path: Path) -> dict[str, Any]:
    """读取并校验规模预算的最小 JSON 结构。"""

    # 原始 JSON 数据必须是对象，避免错误配置被当作空预算。
    raw = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(raw, dict):
        raise ValueError("architecture budget must be a JSON object")
    # 新文件使用的统一行数上限必须是正整数。
    default_limit = raw.get("defaultMaxProductionKotlinLines")
    if not isinstance(default_limit, int) or default_limit <= 0:
        raise ValueError("defaultMaxProductionKotlinLines must be a positive integer")
    # 生产函数统一采用的最大行数必须是正整数。
    function_limit = raw.get("maxProductionFunctionLines")
    if not isinstance(function_limit, int) or function_limit <= 0:
        raise ValueError("maxProductionFunctionLines must be a positive integer")
    # 已知热点预算必须是路径到正整数的映射。
    grandfathered = raw.get("grandfatheredProductionKotlinFiles")
    if not isinstance(grandfathered, dict) or any(
        not isinstance(relative, str) or not isinstance(limit, int) or limit <= 0
        for relative, limit in grandfathered.items()
    ):
        raise ValueError("grandfatheredProductionKotlinFiles must map paths to positive integers")
    return raw


def sanitize_kotlin_source(source: str) -> str:
    """移除注释与字符串内容，同时保留换行和代码花括号位置。"""

    # 清理后的字符列表保持与原文相同长度和换行位置。
    output: list[str] = []
    # 当前字符位置。
    index = 0
    # 当前是否位于块注释中。
    block_comment_depth = 0
    # 当前是否位于普通字符串中。
    in_string = False
    # 当前是否位于三引号字符串中。
    in_triple_string = False
    # 当前是否位于字符字面量中。
    in_character = False
    while index < len(source):
        # 当前字符与最多三个字符的前瞻片段。
        character = source[index]
        pair = source[index : index + 2]
        triple = source[index : index + 3]
        if block_comment_depth > 0:
            if pair == "/*":
                block_comment_depth += 1
                output.extend("  ")
                index += 2
            elif pair == "*/":
                block_comment_depth -= 1
                output.extend("  ")
                index += 2
            else:
                output.append("\n" if character == "\n" else " ")
                index += 1
            continue
        if in_triple_string:
            if triple == '\"\"\"':
                in_triple_string = False
                output.extend("   ")
                index += 3
            else:
                output.append("\n" if character == "\n" else " ")
                index += 1
            continue
        if in_string or in_character:
            # 转义字符和其后字符都不能被误判为字符串结束符。
            if character == "\\" and index + 1 < len(source):
                output.extend("  ")
                index += 2
                continue
            # 当前字面量使用的结束符。
            delimiter = "'" if in_character else '\"'
            if character == delimiter:
                in_character = False
                in_string = False
            output.append("\n" if character == "\n" else " ")
            index += 1
            continue
        if pair == "//":
            # 单行注释正文替换为空格，换行由下一轮保留。
            line_end = source.find("\n", index)
            if line_end < 0:
                output.extend(" " * (len(source) - index))
                break
            output.extend(" " * (line_end - index))
            index = line_end
            continue
        if pair == "/*":
            block_comment_depth = 1
            output.extend("  ")
            index += 2
            continue
        if triple == '\"\"\"':
            in_triple_string = True
            output.extend("   ")
            index += 3
            continue
        if character == '\"':
            in_string = True
            output.append(" ")
            index += 1
            continue
        if character == "'":
            in_character = True
            output.append(" ")
            index += 1
            continue
        output.append(character)
        index += 1
    return "".join(output)


def function_ranges(source: str) -> list[tuple[str, int, int]]:
    """返回 Kotlin 块函数的名称、起始行和结束行。"""

    # 去除会干扰括号与花括号匹配的注释和字面量。
    sanitized = sanitize_kotlin_source(source)
    # 可识别普通、扩展和泛型命名函数的起点。
    function_pattern = re.compile(r"\bfun\s+(?:<[^>{}]+>\s*)?(?:[\w.<>?]+\.)?([A-Za-z_]\w*)\s*\(")
    # 当前源码中找到的块函数范围。
    ranges: list[tuple[str, int, int]] = []
    for match in function_pattern.finditer(sanitized):
        # 参数列表从匹配到的最后一个左括号开始。
        parameter_start = sanitized.find("(", match.start())
        # 当前参数括号深度。
        parameter_depth = 0
        # 参数列表之后继续寻找块体或表达式体的游标。
        cursor = parameter_start
        while cursor < len(sanitized):
            # 当前被检查的结构字符。
            character = sanitized[cursor]
            if character == "(":
                parameter_depth += 1
            elif character == ")":
                parameter_depth -= 1
                if parameter_depth == 0:
                    cursor += 1
                    break
            cursor += 1
        # 返回类型之后遇到等号说明是表达式体，不参与块函数行数预算。
        while cursor < len(sanitized) and sanitized[cursor] not in "={\n":
            cursor += 1
        # 多行返回类型允许跨行，因此继续跳过空白直到块体或表达式体。
        while cursor < len(sanitized) and sanitized[cursor].isspace():
            cursor += 1
        if cursor >= len(sanitized) or sanitized[cursor] != "{":
            continue
        # 当前函数块体花括号深度。
        brace_depth = 0
        # 当前函数块体结束位置。
        end = cursor
        while end < len(sanitized):
            # 当前块体字符。
            character = sanitized[end]
            if character == "{":
                brace_depth += 1
            elif character == "}":
                brace_depth -= 1
                if brace_depth == 0:
                    end += 1
                    break
            end += 1
        # 函数声明和结束花括号所在的 1-based 行号。
        start_line = sanitized.count("\n", 0, match.start()) + 1
        end_line = sanitized.count("\n", 0, end) + 1
        ranges.append((match.group(1), start_line, end_line))
    return ranges


def production_function_findings(root: Path, budget: dict[str, Any]) -> list[dict[str, Any]]:
    """返回超过统一行数预算的生产 Kotlin 块函数。"""

    # Pixel Engine 生产 Kotlin 源码根。
    source_root = root / "pixel-engine/src/main/kotlin"
    # 所有生产函数共同遵守的最大行数。
    limit = int(budget["maxProductionFunctionLines"])
    # 当前长函数违规的机器可读列表。
    findings: list[dict[str, Any]] = []
    for source in sorted(source_root.rglob("*.kt")):
        # 当前源码文本只读取一次并复用于全部函数范围。
        text = source.read_text(encoding="utf-8")
        for name, start_line, end_line in function_ranges(text):
            # 包含声明行和结束花括号行的函数总行数。
            actual = end_line - start_line + 1
            if actual > limit:
                findings.append(
                    {
                        "kind": "production-kotlin-function-size",
                        "path": source.relative_to(root).as_posix(),
                        "line": start_line,
                        "function": name,
                        "actualLines": actual,
                        "maxLines": limit,
                    }
                )
    return findings


def production_size_findings(root: Path, budget: dict[str, Any]) -> list[dict[str, Any]]:
    """返回超过默认或已审热点预算的生产 Kotlin 文件。"""

    # Pixel Engine 生产 Kotlin 源码根。
    source_root = root / "pixel-engine/src/main/kotlin"
    # 普通生产文件统一采用的最大行数。
    default_limit = int(budget["defaultMaxProductionKotlinLines"])
    # 已审热点文件使用不允许增长的逐文件上限。
    grandfathered = dict(budget["grandfatheredProductionKotlinFiles"])
    # 当前规模违规的机器可读列表。
    findings: list[dict[str, Any]] = []
    for source in sorted(source_root.rglob("*.kt")):
        # 预算路径以 pixel-engine 为基准，保持跨机器稳定。
        relative = source.relative_to(root / "pixel-engine").as_posix()
        # 已审热点使用精确预算，其余文件使用统一上限。
        limit = int(grandfathered.get(relative, default_limit))
        # splitlines 与审查统计口径一致，不把尾换行计为额外空行。
        actual = len(source.read_text(encoding="utf-8").splitlines())
        if actual > limit:
            findings.append(
                {
                    "kind": "production-kotlin-size",
                    "path": f"pixel-engine/{relative}",
                    "actualLines": actual,
                    "maxLines": limit,
                }
            )
    # 预算中不存在的热点路径说明文件被移动后忘记同步治理配置。
    for relative in sorted(grandfathered):
        # 当前预算条目对应的生产文件。
        source = root / "pixel-engine" / relative
        if not source.is_file():
            findings.append(
                {
                    "kind": "missing-grandfathered-file",
                    "path": f"pixel-engine/{relative}",
                }
            )
    return findings


def governance_files(root: Path) -> list[Path]:
    """枚举需要遵守单模块叙述的治理文本文件。"""

    # 去重后的稳定文本文件集合。
    files: set[Path] = set()
    for relative in GOVERNANCE_ROOTS:
        # 当前治理根可能是单文件或目录。
        candidate = root / relative
        if candidate.is_file():
            files.add(candidate)
        elif candidate.is_dir():
            files.update(
                path
                for path in candidate.rglob("*")
                if path.is_file()
                and path.suffix in TEXT_SUFFIXES
                and "__pycache__" not in path.parts
                and "build" not in path.relative_to(candidate).parts
            )
    return sorted(files)


def stale_text_findings(root: Path) -> list[dict[str, Any]]:
    """返回治理文本中的已删除模块名称和旧架构叙述。"""

    # 全部禁止重新出现的文本模式。
    forbidden = REMOVED_MODULE_NAMES + STALE_ARCHITECTURE_PHRASES
    # 当前文本违规的机器可读列表。
    findings: list[dict[str, Any]] = []
    for path in governance_files(root):
        # 模式定义文件包含字面量本身，不代表工程重新依赖旧模块。
        if path.relative_to(root) in PATTERN_DEFINITION_FILES:
            continue
        # 当前文本按行扫描，以便报告可直接定位。
        lines = path.read_text(encoding="utf-8").splitlines()
        for line_number, line in enumerate(lines, start=1):
            for pattern in forbidden:
                if pattern in line:
                    findings.append(
                        {
                            "kind": "stale-architecture-text",
                            "path": path.relative_to(root).as_posix(),
                            "line": line_number,
                            "pattern": pattern,
                        }
                    )
    return findings


def check_repository(root: Path, budget_path: Path) -> dict[str, Any]:
    """执行全部架构治理检查并返回确定性报告。"""

    # 已验证结构的规模预算。
    budget = load_budget(budget_path)
    # 规模和架构文本违规合并后按稳定字段排序。
    findings = (
        production_size_findings(root, budget)
        + production_function_findings(root, budget)
        + stale_text_findings(root)
    )
    findings.sort(key=lambda item: (str(item.get("path", "")), int(item.get("line", 0)), str(item["kind"])))
    return {
        "schemaVersion": 1,
        "status": "passed" if not findings else "failed",
        "findingCount": len(findings),
        "findings": findings,
    }


def main(argv: Sequence[str] | None = None) -> int:
    """写出架构治理报告，并以退出码表达是否通过。"""

    # 已解析且规范化的命令行参数。
    args = parse_args(argv)
    # 当前受检仓库根。
    root = args.root.resolve()
    # 当前规模预算文件。
    budget_path = resolve_from_root(root, args.budget).resolve()
    # 当前机器可读报告文件。
    report_path = resolve_from_root(root, args.report).resolve()
    # 完整架构治理结果。
    report = check_repository(root, budget_path)
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    if report["status"] != "passed":
        print(f"Pixel architecture governance failed; see {report_path}")
        return 1
    print(f"Pixel architecture governance passed; report: {report_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
