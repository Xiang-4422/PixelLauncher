#!/usr/bin/env python3
"""校验 Launcher 聚合状态在规范 reducer 外的直接 copy 基线。"""

from __future__ import annotations

import argparse
import json
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Sequence


# 仓库根目录由当前受版本控制脚本的位置稳定推导。
ROOT = Path(__file__).resolve().parents[1]
# Launcher 生产 Kotlin 的默认扫描根。
DEFAULT_SOURCE_ROOT = Path("app/src/main/kotlin")
# 人工审阅后的聚合状态直接 copy 基线。
DEFAULT_BASELINE = Path("tools/launcher-state-copy-baseline.json")
# Gradle 门禁保留的确定性机器报告路径。
DEFAULT_REPORT = Path("app/build/reports/architecture/launcher-state-copy-guard.json")
# 规范 reducer 内部拥有聚合状态写权限，不属于本阶段需要清退的绕过入口。
CANONICAL_REDUCER_FILES = frozenset(
    {
        Path(
            "app/src/main/kotlin/com/purride/pixellauncherv2/launcher/"
            "LauncherStateTransitions.kt"
        ),
    }
)
# Kotlin 标识符允许使用的 ASCII 起始字符；当前 Launcher schema 字段均采用该形式。
IDENTIFIER_START = frozenset(
    "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ_"
)
# Kotlin 标识符后续允许使用的 ASCII 字符。
IDENTIFIER_PART = IDENTIFIER_START | frozenset("0123456789")


@dataclass(frozen=True)
class Token:
    """忽略注释和字面量后保留的 Kotlin 词法单元。"""

    # 词法单元原文。
    text: str
    # 词法单元在源文件中的字符偏移。
    offset: int
    # 一基行号，用于失败报告定位。
    line: int


@dataclass(frozen=True)
class FunctionRange:
    """一个具名 Kotlin 块函数的源码范围。"""

    # 函数名；文件路径已经提供类级身份，因此无需依赖可能变化的嵌套类名。
    name: str
    # 函数声明起始字符偏移。
    start: int
    # 函数块结束字符偏移。
    end: int


@dataclass(frozen=True, order=True)
class CopySignature:
    """baseline 使用的文件、方法和字段集合稳定身份。"""

    # 相对仓库根的 Kotlin 文件路径。
    file: str
    # copy 所在的最内层具名方法。
    method: str
    # copy 顶层命名参数排序后的字段集合。
    fields: tuple[str, ...]


@dataclass(frozen=True)
class CopyOccurrence:
    """一次被识别为 LauncherState 聚合写入的 copy 表达式。"""

    # 不含行号的稳定 baseline 身份。
    signature: CopySignature
    # 表达式所在的一基行号，仅用于人工定位。
    line: int
    # 接收者类型说明，区分直接 state 与 transition 链。
    receiver_kind: str


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    """解析仓库、源码根、baseline 与报告路径。"""

    # 参数解析器允许单元测试使用完全隔离的临时仓库。
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--source-root", type=Path, default=DEFAULT_SOURCE_ROOT)
    parser.add_argument("--baseline", type=Path, default=DEFAULT_BASELINE)
    parser.add_argument("--report", type=Path, default=DEFAULT_REPORT)
    return parser.parse_args(argv)


def resolve_from_root(root: Path, path: Path) -> Path:
    """把相对路径稳定解析到指定仓库根。"""

    return path if path.is_absolute() else root / path


def lex_kotlin(source: str) -> list[Token]:
    """读取 Kotlin 代码词法单元，并完整忽略注释、字符串与字符字面量。"""

    # 输出词法单元只保留结构解析需要的标识符和标点。
    tokens: list[Token] = []
    # 当前源码字符位置。
    index = 0
    # 当前一基行号。
    line = 1
    # 嵌套块注释的当前深度。
    block_comment_depth = 0
    while index < len(source):
        # 当前字符以及二、三字符前瞻。
        character = source[index]
        pair = source[index : index + 2]
        triple = source[index : index + 3]
        if block_comment_depth > 0:
            if pair == "/*":
                block_comment_depth += 1
                index += 2
            elif pair == "*/":
                block_comment_depth -= 1
                index += 2
            else:
                if character == "\n":
                    line += 1
                index += 1
            continue
        if pair == "//":
            # 行注释内容不能制造伪 copy；换行交回主循环维护行号。
            newline = source.find("\n", index + 2)
            index = len(source) if newline < 0 else newline
            continue
        if pair == "/*":
            block_comment_depth = 1
            index += 2
            continue
        if triple == '"""':
            # 三引号字符串整体跳过；模板表达式也不属于待检查生产语句。
            index += 3
            while index < len(source) and source[index : index + 3] != '"""':
                if source[index] == "\n":
                    line += 1
                index += 1
            index = min(index + 3, len(source))
            continue
        if character in {'"', "'"}:
            # 普通字符串和字符字面量共享反斜杠转义跳过逻辑。
            quote = character
            index += 1
            while index < len(source):
                if source[index] == "\n":
                    line += 1
                if source[index] == "\\":
                    index += 2
                    continue
                if source[index] == quote:
                    index += 1
                    break
                index += 1
            continue
        if character == "`":
            # Kotlin 反引号标识符保留内部名称，便于方法范围仍能准确归属。
            identifier_start = index
            identifier_line = line
            index += 1
            while index < len(source) and source[index] != "`":
                if source[index] == "\n":
                    line += 1
                index += 1
            identifier = source[identifier_start + 1 : index]
            index = min(index + 1, len(source))
            tokens.append(Token(identifier, identifier_start, identifier_line))
            continue
        if character in IDENTIFIER_START:
            # ASCII 标识符足以覆盖 LauncherState、方法和全部状态字段。
            identifier_start = index
            identifier_line = line
            index += 1
            while index < len(source) and source[index] in IDENTIFIER_PART:
                index += 1
            tokens.append(
                Token(source[identifier_start:index], identifier_start, identifier_line)
            )
            continue
        if character == "\n":
            line += 1
        if not character.isspace():
            tokens.append(Token(character, index, line))
        index += 1
    if block_comment_depth != 0:
        raise ValueError("Kotlin source contains an unclosed block comment")
    return tokens


def delimiter_pairs(
    tokens: Sequence[Token],
) -> tuple[dict[int, int], dict[int, int]]:
    """构造圆括号和花括号的双向配对索引。"""

    # 每类左定界符各自维护栈，避免不同括号相互干扰。
    stacks: dict[str, list[int]] = {"(": [], "{": [], "[": []}
    # 右定界符到对应左定界符的映射。
    closing_to_opening: dict[int, int] = {}
    # 左定界符到对应右定界符的映射。
    opening_to_closing: dict[int, int] = {}
    # 每种右定界符对应的左定界符。
    opening_for_closing = {")": "(", "}": "{", "]": "["}
    for token_index, token in enumerate(tokens):
        if token.text in stacks:
            stacks[token.text].append(token_index)
        elif token.text in opening_for_closing:
            opening = opening_for_closing[token.text]
            if not stacks[opening]:
                continue
            opening_index = stacks[opening].pop()
            closing_to_opening[token_index] = opening_index
            opening_to_closing[opening_index] = token_index
    return closing_to_opening, opening_to_closing


def find_function_ranges(
    tokens: Sequence[Token],
    opening_to_closing: dict[int, int],
) -> list[FunctionRange]:
    """提取具名块函数范围，供 copy 绑定到稳定方法身份。"""

    # 解析出的函数范围包含成员函数和局部具名函数。
    ranges: list[FunctionRange] = []
    for fun_index, token in enumerate(tokens):
        if token.text != "fun":
            continue
        # 参数左括号必须出现在下一个声明或类型块之前。
        parameter_opening: int | None = None
        cursor = fun_index + 1
        while cursor < len(tokens):
            if tokens[cursor].text == "(":
                parameter_opening = cursor
                break
            if tokens[cursor].text in {"{", "}", "fun", "class", "interface", "object"}:
                break
            cursor += 1
        if parameter_opening is None or parameter_opening not in opening_to_closing:
            continue
        # 扩展函数也以参数左括号之前的最后一个标识符作为方法名。
        name_index = parameter_opening - 1
        if name_index <= fun_index:
            continue
        function_name = tokens[name_index].text
        parameter_closing = opening_to_closing[parameter_opening]
        # 返回类型、where 子句和注解之后的首个花括号是块函数体。
        body_opening: int | None = None
        cursor = parameter_closing + 1
        while cursor < len(tokens):
            if tokens[cursor].text == "{":
                body_opening = cursor
                break
            if tokens[cursor].text in {"=", ";", "fun", "class", "interface", "object"}:
                break
            cursor += 1
        if body_opening is None or body_opening not in opening_to_closing:
            continue
        body_closing = opening_to_closing[body_opening]
        ranges.append(
            FunctionRange(
                name=function_name,
                start=token.offset,
                end=tokens[body_closing].offset,
            )
        )
    return ranges


def containing_function(
    offset: int,
    function_ranges: Sequence[FunctionRange],
) -> str:
    """返回包含给定偏移的最内层具名方法。"""

    # 局部函数可能嵌套于成员函数，起点最大的范围最具体。
    candidates = [
        function_range
        for function_range in function_ranges
        if function_range.start <= offset <= function_range.end
    ]
    if not candidates:
        return "<top-level>"
    return max(candidates, key=lambda function_range: function_range.start).name


def launcher_state_symbols(tokens: Sequence[Token]) -> set[str]:
    """提取显式 LauncherState 符号及从已知状态直接赋值得到的别名。"""

    # 类型标注提供轻量但明确的 LauncherState 类型证据。
    symbols: set[str] = set()
    for token_index in range(0, len(tokens) - 2):
        if (
            tokens[token_index + 1].text == ":"
            and tokens[token_index + 2].text == "LauncherState"
        ):
            symbols.add(tokens[token_index].text)
    # 简单局部别名会在多轮传播后纳入符号表，例如 `val a = state; val b = a`。
    changed = True
    while changed:
        changed = False
        for token_index in range(0, len(tokens) - 3):
            if tokens[token_index].text not in {"val", "var"}:
                continue
            # 声明名称之后允许存在显式类型，向前寻找当前声明的赋值号。
            symbol = tokens[token_index + 1].text
            equals_index: int | None = None
            cursor = token_index + 2
            while cursor < min(len(tokens), token_index + 12):
                if tokens[cursor].text == "=":
                    equals_index = cursor
                    break
                if tokens[cursor].text in {",", ";", "{", "}"}:
                    break
                cursor += 1
            if equals_index is None or equals_index + 1 >= len(tokens):
                continue
            # 右值仅传播无运算的已知符号、host.state 或规范 transition 调用结果。
            right_index = equals_index + 1
            right = tokens[right_index].text
            is_known_symbol = right in symbols
            is_host_state = (
                right == "host"
                and right_index + 2 < len(tokens)
                and tokens[right_index + 1].text == "."
                and tokens[right_index + 2].text == "state"
                and "state" in symbols
            )
            is_transition_result = (
                right == "LauncherStateTransitions"
                and right_index + 3 < len(tokens)
                and tokens[right_index + 1].text == "."
                and tokens[right_index + 3].text == "("
            )
            if (
                is_known_symbol or is_host_state or is_transition_result
            ) and symbol not in symbols:
                symbols.add(symbol)
                changed = True
    return symbols


def transition_call_before(
    closing_index: int,
    tokens: Sequence[Token],
    closing_to_opening: dict[int, int],
) -> bool:
    """判断右括号是否结束 `LauncherStateTransitions.<方法>(...)` 调用。"""

    # 调用参数左括号及其前方的 `<对象>.<方法>` 是链式类型证据。
    opening_index = closing_to_opening.get(closing_index)
    if opening_index is None or opening_index < 3:
        return False
    return (
        tokens[opening_index - 2].text == "."
        and tokens[opening_index - 3].text == "LauncherStateTransitions"
    )


def receiver_kind(
    copy_name_index: int,
    tokens: Sequence[Token],
    closing_to_opening: dict[int, int],
    launcher_symbols: set[str],
) -> str | None:
    """识别 LauncherState 直接接收者或规范 transition 调用链。"""

    # `.copy` 之前必须存在接收者。
    dot_index = copy_name_index - 1
    receiver_index = dot_index - 1
    if dot_index < 1 or tokens[dot_index].text != ".":
        return None
    receiver = tokens[receiver_index].text
    if receiver == ")" and transition_call_before(
        receiver_index,
        tokens,
        closing_to_opening,
    ):
        return "transition-chain"
    if (
        receiver == "state"
        and receiver_index >= 2
        and tokens[receiver_index - 1].text == "."
        and tokens[receiver_index - 2].text == "host"
        and "state" in launcher_symbols
    ):
        return "host-launcher-state"
    if receiver in launcher_symbols:
        return "typed-launcher-state"
    return None


def named_copy_fields(
    opening_index: int,
    closing_index: int,
    tokens: Sequence[Token],
) -> tuple[str, ...]:
    """读取 copy 调用的顶层命名参数字段集合。"""

    # 顶层参数段按逗号切分，嵌套调用、集合与 lambda 中的逗号不参与。
    segments: list[list[Token]] = []
    # 当前参数段。
    current_segment: list[Token] = []
    # 三类嵌套定界符的当前深度。
    depths = {"(": 0, "{": 0, "[": 0}
    # 右定界符对应的左定界符。
    opening_for_closing = {")": "(", "}": "{", "]": "["}
    for token in tokens[opening_index + 1 : closing_index]:
        if token.text in depths:
            depths[token.text] += 1
        elif token.text in opening_for_closing:
            opening = opening_for_closing[token.text]
            depths[opening] = max(0, depths[opening] - 1)
        if token.text == "," and all(depth == 0 for depth in depths.values()):
            segments.append(current_segment)
            current_segment = []
        else:
            current_segment.append(token)
    if current_segment:
        segments.append(current_segment)

    # LauncherState.copy 的业务写入必须使用可审查的具名字段参数。
    fields: list[str] = []
    for segment in segments:
        if not segment:
            continue
        if len(segment) < 2 or segment[1].text != "=":
            raise ValueError(
                f"LauncherState.copy at line {segment[0].line} contains a positional "
                "or unsupported argument"
            )
        fields.append(segment[0].text)
    if len(fields) != len(set(fields)):
        raise ValueError("LauncherState.copy contains duplicate named fields")
    return tuple(sorted(fields))


def scan_kotlin_file(
    root: Path,
    source_file: Path,
) -> list[CopyOccurrence]:
    """扫描单个 Kotlin 文件中的聚合 LauncherState.copy 表达式。"""

    # baseline 和报告统一使用相对仓库根的 POSIX 路径。
    relative_file = source_file.relative_to(root)
    if relative_file in CANONICAL_REDUCER_FILES:
        return []
    # 词法单元不会包含注释、字符串或字符字面量中的伪代码。
    tokens = lex_kotlin(source_file.read_text(encoding="utf-8"))
    # 定界符映射同时服务函数范围、copy 参数和 transition 接收者识别。
    closing_to_opening, opening_to_closing = delimiter_pairs(tokens)
    # copy 归属的具名方法范围。
    function_ranges = find_function_ranges(tokens, opening_to_closing)
    # 当前文件显式声明的 LauncherState 符号。
    launcher_symbols = launcher_state_symbols(tokens)
    # 文件中实际识别的聚合 copy。
    occurrences: list[CopyOccurrence] = []
    for token_index, token in enumerate(tokens):
        if token.text != "copy" or token_index + 1 >= len(tokens):
            continue
        if tokens[token_index + 1].text != "(":
            continue
        # 普通 data class copy 没有 LauncherState 类型证据时必须忽略。
        kind = receiver_kind(
            token_index,
            tokens,
            closing_to_opening,
            launcher_symbols,
        )
        if kind is None:
            continue
        argument_opening = token_index + 1
        argument_closing = opening_to_closing.get(argument_opening)
        if argument_closing is None:
            raise ValueError(
                f"{relative_file.as_posix()}:{token.line} has an unclosed copy call"
            )
        # 方法身份不依赖绝对行号，移动行不会引发无意义 baseline 漂移。
        method = containing_function(token.offset, function_ranges)
        # 字段集合忽略参数顺序，只约束实际写入面。
        fields = named_copy_fields(argument_opening, argument_closing, tokens)
        occurrences.append(
            CopyOccurrence(
                signature=CopySignature(
                    file=relative_file.as_posix(),
                    method=method,
                    fields=fields,
                ),
                line=token.line,
                receiver_kind=kind,
            )
        )
    return occurrences


def load_baseline(path: Path) -> Counter[CopySignature]:
    """读取并严格校验聚合 copy baseline。"""

    # baseline 顶层必须是带版本与条目的 JSON 对象。
    raw = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(raw, dict) or raw.get("schemaVersion") != 1:
        raise ValueError("Launcher state copy baseline schemaVersion must be 1")
    # 每条签名独立声明数量，便于阶段 1 按表达式逐步缩减。
    entries = raw.get("entries")
    if not isinstance(entries, list):
        raise ValueError("Launcher state copy baseline entries must be a list")
    # 相同签名若重复声明会掩盖评审意图，因此必须拒绝。
    baseline: Counter[CopySignature] = Counter()
    for entry_index, entry in enumerate(entries):
        if not isinstance(entry, dict):
            raise ValueError(f"baseline entry {entry_index} must be an object")
        relative_file = entry.get("file")
        method = entry.get("method")
        fields = entry.get("fields")
        count = entry.get("count")
        if not isinstance(relative_file, str) or not relative_file.endswith(".kt"):
            raise ValueError(f"baseline entry {entry_index} has an invalid file")
        if not isinstance(method, str) or not method:
            raise ValueError(f"baseline entry {entry_index} has an invalid method")
        if (
            not isinstance(fields, list)
            or not fields
            or any(not isinstance(field, str) or not field for field in fields)
            or len(fields) != len(set(fields))
        ):
            raise ValueError(f"baseline entry {entry_index} has invalid fields")
        if not isinstance(count, int) or isinstance(count, bool) or count <= 0:
            raise ValueError(f"baseline entry {entry_index} has an invalid count")
        signature = CopySignature(
            file=Path(relative_file).as_posix(),
            method=method,
            fields=tuple(sorted(fields)),
        )
        if signature in baseline:
            raise ValueError(
                f"baseline signature is declared more than once: "
                f"{format_signature(signature)}"
            )
        baseline[signature] = count
    return baseline


def format_signature(signature: CopySignature) -> str:
    """把 baseline 身份格式化成适合失败日志的稳定文本。"""

    # 字段按签名内的确定性顺序展示。
    fields = ", ".join(signature.fields)
    return f"{signature.file}::{signature.method} fields=[{fields}]"


def counter_to_report(counter: Counter[CopySignature]) -> list[dict[str, Any]]:
    """把签名计数器转换成稳定排序的 JSON 条目。"""

    # 报告顺序不受文件系统遍历或 Counter 插入顺序影响。
    return [
        {
            "file": signature.file,
            "method": signature.method,
            "fields": list(signature.fields),
            "count": counter[signature],
        }
        for signature in sorted(counter)
    ]


def pair_field_changes(
    missing: Counter[CopySignature],
    unexpected: Counter[CopySignature],
) -> list[dict[str, Any]]:
    """优先配对同文件同方法的字段变化，并从差异计数中消费。"""

    # 生成候选后按字段对称差最小优先，避免一个方法多种 copy 时错误配对。
    candidates: list[tuple[int, CopySignature, CopySignature]] = []
    for expected in missing:
        for actual in unexpected:
            if expected.file == actual.file and expected.method == actual.method:
                distance = len(set(expected.fields) ^ set(actual.fields))
                candidates.append((distance, expected, actual))
    findings: list[dict[str, Any]] = []
    for _, expected, actual in sorted(candidates):
        while missing[expected] > 0 and unexpected[actual] > 0:
            # 新增和删除字段分别展示，直接告诉维护者写入面如何漂移。
            added = sorted(set(actual.fields) - set(expected.fields))
            removed = sorted(set(expected.fields) - set(actual.fields))
            findings.append(
                {
                    "kind": "field-set-changed",
                    "file": expected.file,
                    "method": expected.method,
                    "expectedFields": list(expected.fields),
                    "actualFields": list(actual.fields),
                    "addedFields": added,
                    "removedFields": removed,
                    "message": (
                        f"字段集合变化: {expected.file}::{expected.method}; "
                        f"expected={list(expected.fields)}, actual={list(actual.fields)}, "
                        f"added={added}, removed={removed}"
                    ),
                }
            )
            missing[expected] -= 1
            unexpected[actual] -= 1
    return findings


def pair_method_changes(
    missing: Counter[CopySignature],
    unexpected: Counter[CopySignature],
) -> list[dict[str, Any]]:
    """配对同文件同字段集合的方法移动，并从差异计数中消费。"""

    # 相同字段写入从一个方法移动到另一方法属于未授权位置变化。
    findings: list[dict[str, Any]] = []
    for expected in sorted(missing):
        for actual in sorted(unexpected):
            if expected.file != actual.file or expected.fields != actual.fields:
                continue
            while missing[expected] > 0 and unexpected[actual] > 0:
                findings.append(
                    {
                        "kind": "method-changed",
                        "file": expected.file,
                        "expectedMethod": expected.method,
                        "actualMethod": actual.method,
                        "fields": list(expected.fields),
                        "message": (
                            f"方法位置变化: {expected.file}; "
                            f"expected={expected.method}, actual={actual.method}, "
                            f"fields={list(expected.fields)}"
                        ),
                    }
                )
                missing[expected] -= 1
                unexpected[actual] -= 1
    return findings


def compare_counters(
    expected: Counter[CopySignature],
    actual: Counter[CopySignature],
) -> list[dict[str, Any]]:
    """比较 baseline 与实际签名，并生成可操作的差异分类。"""

    # 精确签名数量变化应优先报告为重复或删除，而不是泛化成新增/缺失。
    findings: list[dict[str, Any]] = []
    # 尚未配对的缺失签名次数。
    missing: Counter[CopySignature] = Counter()
    # 尚未配对的新增签名次数。
    unexpected: Counter[CopySignature] = Counter()
    for signature in sorted(set(expected) | set(actual)):
        expected_count = expected[signature]
        actual_count = actual[signature]
        if expected_count == actual_count:
            continue
        if expected_count > 0 and actual_count > 0:
            kind = (
                "expression-count-increased"
                if actual_count > expected_count
                else "expression-count-decreased"
            )
            findings.append(
                {
                    "kind": kind,
                    "file": signature.file,
                    "method": signature.method,
                    "fields": list(signature.fields),
                    "expectedCount": expected_count,
                    "actualCount": actual_count,
                    "message": (
                        f"表达式数量变化: {format_signature(signature)}; "
                        f"expected={expected_count}, actual={actual_count}"
                    ),
                }
            )
        elif expected_count > actual_count:
            missing[signature] = expected_count - actual_count
        else:
            unexpected[signature] = actual_count - expected_count

    findings.extend(pair_field_changes(missing, unexpected))
    findings.extend(pair_method_changes(missing, unexpected))
    for signature in sorted(missing):
        if missing[signature] <= 0:
            continue
        findings.append(
            {
                "kind": "missing-copy",
                "file": signature.file,
                "method": signature.method,
                "fields": list(signature.fields),
                "count": missing[signature],
                "message": (
                    f"baseline 尚未缩减或生产表达式被删除: "
                    f"{format_signature(signature)}; missing={missing[signature]}"
                ),
            }
        )
    for signature in sorted(unexpected):
        if unexpected[signature] <= 0:
            continue
        findings.append(
            {
                "kind": "unexpected-copy",
                "file": signature.file,
                "method": signature.method,
                "fields": list(signature.fields),
                "count": unexpected[signature],
                "message": (
                    f"未授权聚合状态 copy: {format_signature(signature)}; "
                    f"extra={unexpected[signature]}"
                ),
            }
        )
    return findings


def scan_repository(root: Path, source_root: Path) -> list[CopyOccurrence]:
    """扫描 Launcher 生产源码根并返回稳定排序的聚合 copy 清单。"""

    # 源码文件按相对路径排序，保证报告和测试跨平台稳定。
    source_files = sorted(
        source_root.rglob("*.kt"),
        key=lambda path: path.relative_to(root).as_posix(),
    )
    # 聚合所有文件的识别结果。
    occurrences: list[CopyOccurrence] = []
    for source_file in source_files:
        occurrences.extend(scan_kotlin_file(root, source_file))
    return sorted(
        occurrences,
        key=lambda occurrence: (
            occurrence.signature.file,
            occurrence.signature.method,
            occurrence.signature.fields,
            occurrence.line,
        ),
    )


def check_repository(
    root: Path,
    source_root: Path,
    baseline_path: Path,
) -> dict[str, Any]:
    """对仓库执行完整检查并返回无时间戳的确定性报告。"""

    # 人工审阅的期望签名计数。
    expected = load_baseline(baseline_path)
    # 带行号的实际表达式清单。
    occurrences = scan_repository(root, source_root)
    # 实际签名计数用于精确比较表达式数量。
    actual = Counter(occurrence.signature for occurrence in occurrences)
    # 失败项明确区分字段、方法、数量、新增和缺失。
    findings = compare_counters(expected, actual)
    return {
        "schemaVersion": 1,
        "status": "passed" if not findings else "failed",
        "sourceRoot": source_root.relative_to(root).as_posix(),
        "canonicalReducerFiles": sorted(
            path.as_posix() for path in CANONICAL_REDUCER_FILES
        ),
        "expectedExpressionCount": sum(expected.values()),
        "actualExpressionCount": len(occurrences),
        "baseline": counter_to_report(expected),
        "observed": [
            {
                "file": occurrence.signature.file,
                "method": occurrence.signature.method,
                "fields": list(occurrence.signature.fields),
                "line": occurrence.line,
                "receiverKind": occurrence.receiver_kind,
            }
            for occurrence in occurrences
        ],
        "findings": findings,
    }


def write_report(path: Path, report: dict[str, Any]) -> None:
    """以稳定格式写出机器可读报告。"""

    # 报告父目录由工具创建，Gradle 可安全声明单个输出文件。
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def main(argv: Sequence[str] | None = None) -> int:
    """执行命令行门禁并用退出码表达通过或失败。"""

    # 命令行路径统一相对指定仓库根解析。
    args = parse_args(argv)
    root = args.root.resolve()
    source_root = resolve_from_root(root, args.source_root).resolve()
    baseline_path = resolve_from_root(root, args.baseline).resolve()
    report_path = resolve_from_root(root, args.report).resolve()
    try:
        # 检查异常也必须写入报告并以门禁失败退出，不能留下模糊堆栈。
        report = check_repository(root, source_root, baseline_path)
    except (OSError, ValueError, json.JSONDecodeError) as error:
        report = {
            "schemaVersion": 1,
            "status": "failed",
            "findings": [
                {
                    "kind": "scanner-error",
                    "message": str(error),
                }
            ],
        }
    write_report(report_path, report)
    if report["status"] != "passed":
        print(f"Launcher state copy guard failed; report: {report_path}")
        for finding in report["findings"]:
            print(f"- {finding['message']}")
        return 1
    print(
        "Launcher state copy guard passed: "
        f"{report['actualExpressionCount']} reviewed expressions; report: {report_path}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
