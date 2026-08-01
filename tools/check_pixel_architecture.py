#!/usr/bin/env python3
"""校验 Pixel Engine 函数规模、工程模块契约与历史文本边界。"""

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
# 旧九模块叙述不得继续误导统一 Engine 模块的维护者。
STALE_ARCHITECTURE_PHRASES = (
    "九个 SDK",
    "九个项目",
    "九个模块",
    "九个 artifact",
    "拆分后九个",
    "旧聚合坐标",
    "兄弟 artifact",
)
# 当前主工程允许存在的 Gradle 模块；变更模块结构时必须显式更新此契约。
EXPECTED_GRADLE_MODULES = (
    ":app",
    ":pixel-engine",
    ":showcase",
    ":showcase-desktop",
    ":lockscreen-module",
    ":pixel-design",
)
# 模块清单受控区段必须出现在所有关键架构入口中。
MODULE_CONTRACT_DOCUMENTS = (
    Path("README.md"),
    Path("docs/项目总览.md"),
    Path("pixel-engine/README.md"),
    Path("pixel-engine/docs/架构与设计.md"),
    Path("pixel-engine/docs/长期规划.md"),
)
# 总览文档还必须维护完整依赖图，避免只同步模块数量而遗漏实际消费方式。
DEPENDENCY_CONTRACT_DOCUMENTS = (
    Path("README.md"),
    Path("docs/项目总览.md"),
)
# 文档模块清单区段的稳定边界标记。
MODULE_CONTRACT_START = "<!-- architecture-contract:modules:start -->"
MODULE_CONTRACT_END = "<!-- architecture-contract:modules:end -->"
# 文档依赖图区段的稳定边界标记。
DEPENDENCY_CONTRACT_START = "<!-- architecture-contract:dependencies:start -->"
DEPENDENCY_CONTRACT_END = "<!-- architecture-contract:dependencies:end -->"
# 当前依赖图同时表达 Android project 依赖与桌面宿主的特殊二进制/源码消费方式。
EXPECTED_DEPENDENCY_CONTRACT_LINES = (
    ":app -> :pixel-engine",
    ":lockscreen-module -> :pixel-engine",
    ":app -> :pixel-design -> :pixel-engine",
    ":showcase -> :pixel-engine",
    ":showcase-desktop --debug classes.jar--> :pixel-engine",
    ":showcase-desktop --shared scene sources--> :showcase",
)
# settings.gradle.kts 中顶层 include 调用及其字符串参数的最小解析模式。
GRADLE_INCLUDE_PATTERN = re.compile(
    r"^[ \t]*include\s*\((?P<arguments>[^)]*)\)",
    re.MULTILINE,
)
GRADLE_MODULE_ARGUMENT_PATTERN = re.compile(r"[\"'](?P<module>:[^\"']+)[\"']")
# 模块契约区段只接受反引号包裹的完整 Gradle project path。
DOCUMENTED_MODULE_PATTERN = re.compile(r"`(?P<module>:[a-z0-9][a-z0-9:-]*)`")
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
    # 生产函数统一采用的最大行数必须是正整数。
    function_limit = raw.get("maxProductionFunctionLines")
    if not isinstance(function_limit, int) or function_limit <= 0:
        raise ValueError("maxProductionFunctionLines must be a positive integer")
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
            if triple == '"""':
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
        if triple == '"""':
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


def governance_files(root: Path) -> list[Path]:
    """枚举需要遵守当前工程架构叙述的治理文本文件。"""

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


def strip_kotlin_dsl_comments(source: str) -> str:
    """移除 Kotlin DSL 行/块注释，同时保留字符串及原始换行。"""

    # 输出保持原文长度，避免注释移除后相邻 token 意外拼接。
    output: list[str] = []
    # 当前扫描位置。
    index = 0
    # Kotlin 允许嵌套块注释，深度为零时才解析代码。
    block_comment_depth = 0
    # 当前普通字符串或字符字面量的结束符。
    delimiter: str | None = None
    # 三引号字符串单独处理，避免其中的注释符被误删。
    in_triple_string = False
    while index < len(source):
        # 当前字符及注释/三引号所需的前瞻片段。
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
                output.extend(triple)
                index += 3
            else:
                output.append(character)
                index += 1
            continue
        if delimiter is not None:
            output.append(character)
            if character == "\\" and index + 1 < len(source):
                output.append(source[index + 1])
                index += 2
                continue
            if character == delimiter:
                delimiter = None
            index += 1
            continue
        if pair == "//":
            # 行注释内容替换为空格，换行交由下一轮保留。
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
            output.extend(triple)
            index += 3
            continue
        if character in ('"', "'"):
            delimiter = character
        output.append(character)
        index += 1
    return "".join(output)


def declared_gradle_modules(settings_text: str) -> tuple[str, ...]:
    """按声明顺序返回 settings.gradle.kts 中未被注释的 include 模块路径。"""

    # 多个 include 调用和单次调用中的多个参数统一展平。
    modules: list[str] = []
    # 注释先被清空，避免已移除模块仍被正则计入实际工程清单。
    uncommented_text = strip_kotlin_dsl_comments(settings_text)
    for include_match in GRADLE_INCLUDE_PATTERN.finditer(uncommented_text):
        # 当前 include 调用中的字符串模块参数。
        arguments = include_match.group("arguments")
        modules.extend(
            argument.group("module")
            for argument in GRADLE_MODULE_ARGUMENT_PATTERN.finditer(arguments)
        )
    return tuple(modules)


def controlled_contract_block(
    text: str,
    start_marker: str,
    end_marker: str,
) -> tuple[int, str] | None:
    """提取唯一且闭合的受控文档区段，返回起始行与正文。"""

    # 标记必须各出现一次，避免检查器在多个候选区段间猜测权威内容。
    if text.count(start_marker) != 1 or text.count(end_marker) != 1:
        return None
    # 起止字符位置用于校验顺序并计算报告行号。
    start_index = text.index(start_marker)
    content_start = start_index + len(start_marker)
    end_index = text.index(end_marker)
    if end_index < content_start:
        return None
    # 1-based 行号指向区段起始标记，方便审查者直接定位。
    start_line = text.count("\n", 0, start_index) + 1
    return start_line, text[content_start:end_index]


def normalized_contract_lines(block: str) -> tuple[str, ...]:
    """移除空行与 Markdown fence，返回受控依赖区段的有效行。"""

    return tuple(
        line.strip()
        for line in block.splitlines()
        if line.strip() and not line.strip().startswith("```")
    )


def module_contract_findings(root: Path) -> list[dict[str, Any]]:
    """返回 Gradle include、文档模块清单和依赖图之间的契约漂移。"""

    # 当前模块契约违规的机器可读列表。
    findings: list[dict[str, Any]] = []
    # settings.gradle.kts 是 Gradle 实际项目清单的事实来源。
    settings_path = root / "settings.gradle.kts"
    actual_modules = (
        declared_gradle_modules(settings_path.read_text(encoding="utf-8"))
        if settings_path.is_file()
        else ()
    )
    # Gradle include 顺序没有架构含义，但重复声明和集合差异都属于契约漂移。
    if len(actual_modules) != len(EXPECTED_GRADLE_MODULES) or set(actual_modules) != set(
        EXPECTED_GRADLE_MODULES
    ):
        findings.append(
            {
                "kind": "gradle-module-list",
                "path": "settings.gradle.kts",
                "expected": list(EXPECTED_GRADLE_MODULES),
                "actual": list(actual_modules),
            }
        )

    for relative in MODULE_CONTRACT_DOCUMENTS:
        # 当前关键文档必须包含唯一模块契约区段。
        path = root / relative
        text = path.read_text(encoding="utf-8") if path.is_file() else ""
        contract = controlled_contract_block(text, MODULE_CONTRACT_START, MODULE_CONTRACT_END)
        if contract is None:
            findings.append(
                {
                    "kind": "documented-module-list",
                    "path": relative.as_posix(),
                    "expected": list(EXPECTED_GRADLE_MODULES),
                    "actual": [],
                    "reason": "missing-or-ambiguous-contract-block",
                }
            )
            continue
        # 区段中的反引号模块路径必须与预期清单及顺序完全一致。
        start_line, block = contract
        documented_modules = tuple(
            match.group("module") for match in DOCUMENTED_MODULE_PATTERN.finditer(block)
        )
        if documented_modules != EXPECTED_GRADLE_MODULES:
            findings.append(
                {
                    "kind": "documented-module-list",
                    "path": relative.as_posix(),
                    "line": start_line,
                    "expected": list(EXPECTED_GRADLE_MODULES),
                    "actual": list(documented_modules),
                }
            )

    for relative in DEPENDENCY_CONTRACT_DOCUMENTS:
        # 两份工程总览必须给出同一份可机器核对的依赖图。
        path = root / relative
        text = path.read_text(encoding="utf-8") if path.is_file() else ""
        contract = controlled_contract_block(
            text,
            DEPENDENCY_CONTRACT_START,
            DEPENDENCY_CONTRACT_END,
        )
        if contract is None:
            findings.append(
                {
                    "kind": "documented-module-dependencies",
                    "path": relative.as_posix(),
                    "expected": list(EXPECTED_DEPENDENCY_CONTRACT_LINES),
                    "actual": [],
                    "reason": "missing-or-ambiguous-contract-block",
                }
            )
            continue
        # 依赖关系顺序固定，便于两份总览产生可读且确定的差异。
        start_line, block = contract
        documented_dependencies = normalized_contract_lines(block)
        if documented_dependencies != EXPECTED_DEPENDENCY_CONTRACT_LINES:
            findings.append(
                {
                    "kind": "documented-module-dependencies",
                    "path": relative.as_posix(),
                    "line": start_line,
                    "expected": list(EXPECTED_DEPENDENCY_CONTRACT_LINES),
                    "actual": list(documented_dependencies),
                }
            )
    return findings


def check_repository(root: Path, budget_path: Path) -> dict[str, Any]:
    """执行全部架构治理检查并返回确定性报告。"""

    # 已验证结构的规模预算。
    budget = load_budget(budget_path)
    # 函数规模和架构文本违规合并后按稳定字段排序。
    findings = (
        production_function_findings(root, budget)
        + module_contract_findings(root)
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
