#!/usr/bin/env python3
"""校验 Kotlin 显式 public/protected 声明的有效 KDoc 覆盖率。"""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Sequence


# Kotlin 中可出现在可见性与声明关键字之间的修饰符。
DECLARATION_MODIFIERS = frozenset(
    {
        "actual",
        "abstract",
        "annotation",
        "companion",
        "const",
        "crossinline",
        "data",
        "enum",
        "expect",
        "external",
        "final",
        "infix",
        "inline",
        "inner",
        "lateinit",
        "noinline",
        "open",
        "operator",
        "override",
        "reified",
        "sealed",
        "suspend",
        "tailrec",
        "value",
    }
)

# 这些关键字代表需要独立 KDoc 的源码声明。
DECLARATION_KEYWORDS = frozenset(
    {"class", "constructor", "fun", "interface", "object", "typealias", "val", "var"}
)

# 遇到这些符号说明当前可见性不再可能修饰后续声明。
DECLARATION_BOUNDARIES = frozenset({"{", "}", ";", "=", ",", "(", ")"})

# 空模板即使形式上是 KDoc 也不得计入有效覆盖率。
INVALID_KDOC_MARKERS = (
    "todo",
    "fixme",
    "tbd",
    "template",
    "待补充",
    "待完善",
)


@dataclass(frozen=True)
class Token:
    """保存扫描所需的最小 Kotlin token 及源码位置。"""

    # token 类型：identifier、symbol 或 kdoc。
    kind: str
    # token 的原始文本；字符串和普通注释不会进入 token 流。
    value: str
    # token 在源码中的起始字符偏移。
    start: int
    # token 在源码中的结束字符偏移。
    end: int
    # token 起始位置的一基行号。
    line: int


@dataclass(frozen=True)
class Declaration:
    """描述一个显式 public/protected Kotlin 声明及其关联文档。"""

    # 声明所在源码文件。
    path: Path
    # 可见性 token 的一基行号。
    line: int
    # 可见性 token 在源码中的字符偏移，仅供精确诊断或迁移工具使用。
    offset: int
    # public 或 protected。
    visibility: str
    # class、fun、val 等声明种类。
    kind: str
    # 面向报告展示的声明名称。
    name: str
    # 与声明直接关联的 KDoc；缺失时为 None。
    kdoc: str | None

    @property
    def location(self) -> str:
        """返回稳定的文件与行号定位文本。"""

        return f"{self.path.as_posix()}:{self.line}"

    @property
    def label(self) -> str:
        """返回适合人工审阅的可见性、种类和名称。"""

        return f"{self.visibility} {self.kind} {self.name}"


@dataclass(frozen=True)
class CoverageResult:
    """汇总 KDoc 覆盖数量以及缺失或无效声明。"""

    # 扫描到的显式 public/protected 声明总数。
    total: int
    # 具有有效 KDoc 的声明数量。
    documented: int
    # 完全没有关联 KDoc 的声明。
    missing: tuple[Declaration, ...]
    # 只有空模板、签名复述或待办文本的声明。
    invalid: tuple[Declaration, ...]

    @property
    def percent(self) -> float:
        """返回覆盖率百分比；空源码集按完全覆盖处理。"""

        return 100.0 if self.total == 0 else self.documented * 100.0 / self.total


def _skip_quoted(source: str, start: int, quote: str) -> int:
    """跳过 Kotlin 普通字符串或字符字面量并返回结束偏移。"""

    # 三引号字符串不处理转义，只寻找下一个三引号边界。
    if quote == '"' and source.startswith('"""', start):
        end = source.find('"""', start + 3)
        return len(source) if end < 0 else end + 3
    cursor = start + 1
    while cursor < len(source):
        if source[cursor] == "\\":
            cursor += 2
            continue
        if source[cursor] == quote:
            return cursor + 1
        cursor += 1
    return len(source)


def _skip_block_comment(source: str, start: int) -> int:
    """跳过 Kotlin 允许嵌套的块注释并返回外层结束偏移。"""

    # Kotlin 与 Java 不同，块注释内部可以继续嵌套块注释。
    depth = 1
    cursor = start + 2
    while cursor < len(source) and depth > 0:
        if source.startswith("/*", cursor):
            depth += 1
            cursor += 2
            continue
        if source.startswith("*/", cursor):
            depth -= 1
            cursor += 2
            continue
        cursor += 1
    return cursor


def tokenize_kotlin(source: str) -> list[Token]:
    """生成足以识别声明和 KDoc 归属的 Kotlin token 流。"""

    # 最终 token 列表不保留空白、字符串和普通注释。
    tokens: list[Token] = []
    # 当前字符偏移。
    cursor = 0
    # 当前一基行号。
    line = 1
    while cursor < len(source):
        char = source[cursor]
        if char.isspace():
            line += char == "\n"
            cursor += 1
            continue
        if source.startswith("//", cursor):
            end = source.find("\n", cursor + 2)
            cursor = len(source) if end < 0 else end
            continue
        if source.startswith("/*", cursor):
            end = _skip_block_comment(source, cursor)
            comment = source[cursor:end]
            if source.startswith("/**", cursor):
                tokens.append(Token("kdoc", comment, cursor, end, line))
            line += comment.count("\n")
            cursor = end
            continue
        if char in {'"', "'"}:
            end = _skip_quoted(source, cursor, char)
            line += source[cursor:end].count("\n")
            cursor = end
            continue
        if char == "`":
            end = source.find("`", cursor + 1)
            end = len(source) if end < 0 else end + 1
            tokens.append(Token("identifier", source[cursor:end], cursor, end, line))
            line += source[cursor:end].count("\n")
            cursor = end
            continue
        if char == "_" or char.isalpha():
            end = cursor + 1
            while end < len(source) and (source[end] == "_" or source[end].isalnum()):
                end += 1
            tokens.append(Token("identifier", source[cursor:end], cursor, end, line))
            cursor = end
            continue
        # 双字符符号作为一个 token，避免把 ::class 误当作声明边界。
        symbol = source[cursor : cursor + 2]
        if symbol not in {"::", "->", "?.", "!!", "<=", ">=", "==", "!="}:
            symbol = char
        tokens.append(Token("symbol", symbol, cursor, cursor + len(symbol), line))
        cursor += len(symbol)
    return tokens


def _skip_balanced(tokens: Sequence[Token], start: int, opening: str, closing: str) -> int:
    """越过一个已知起始符号的平衡 token 区间。"""

    # 嵌套深度从当前 opening 开始计数。
    depth = 0
    cursor = start
    while cursor < len(tokens):
        if tokens[cursor].value == opening:
            depth += 1
        elif tokens[cursor].value == closing:
            depth -= 1
            if depth == 0:
                return cursor + 1
        cursor += 1
    return len(tokens)


def _skip_annotation(tokens: Sequence[Token], start: int) -> int:
    """跳过 Kotlin 注解名称、可选 use-site target 和参数。"""

    # 起始位置必须是 @。
    cursor = start + 1
    # use-site target、限定名和泛型名称都只影响关联，不构成声明。
    while cursor < len(tokens) and tokens[cursor].value not in {"(", "@"}:
        if tokens[cursor].value in DECLARATION_BOUNDARIES - {"("}:
            return cursor
        # 注解名称之后遇到普通修饰符或可见性时结束。
        if tokens[cursor].value in {"public", "protected"} | DECLARATION_MODIFIERS:
            return cursor
        cursor += 1
    if cursor < len(tokens) and tokens[cursor].value == "(":
        cursor = _skip_balanced(tokens, cursor, "(", ")")
    return cursor


def _find_declaration_keyword(tokens: Sequence[Token], visibility_index: int) -> int | None:
    """返回可见性实际修饰的声明关键字索引。"""

    # 最多检查有限 token，防止畸形源码把可见性串到远处声明。
    cursor = visibility_index + 1
    limit = min(len(tokens), visibility_index + 64)
    while cursor < limit:
        value = tokens[cursor].value
        if value == "@":
            cursor = _skip_annotation(tokens, cursor)
            continue
        if value in DECLARATION_MODIFIERS:
            cursor += 1
            continue
        # fun interface 的 fun 是类型修饰符，真正声明关键字是 interface。
        if value == "fun" and cursor + 1 < len(tokens) and tokens[cursor + 1].value == "interface":
            return cursor + 1
        if value in DECLARATION_KEYWORDS:
            return cursor
        if value in DECLARATION_BOUNDARIES:
            return None
        return None
    return None


def _declaration_name(tokens: Sequence[Token], keyword_index: int) -> str:
    """从声明关键字之后提取稳定、可读的声明名称。"""

    # 构造器与匿名 companion object 使用固定显示名称。
    kind = tokens[keyword_index].value
    if kind == "constructor":
        return "<constructor>"
    cursor = keyword_index + 1
    if kind == "object" and cursor < len(tokens) and tokens[cursor].value in {"{", ":"}:
        return "<companion>"
    # class/interface/object/typealias 的首个标识符就是声明名。
    if kind in {"class", "interface", "object", "typealias"}:
        while cursor < len(tokens):
            token = tokens[cursor]
            if token.kind == "identifier":
                return token.value
            if token.value in {"{", "}", "=", ";"}:
                break
            cursor += 1
        return "<anonymous>"
    # 函数名是参数列表之前最后一个标识符，可兼容扩展接收者和泛型。
    if kind == "fun":
        # 泛型参数可能在 fun 后出现，先越过完整尖括号块。
        if cursor < len(tokens) and tokens[cursor].value == "<":
            cursor = _skip_balanced(tokens, cursor, "<", ">")
        candidate = "<anonymous>"
        while cursor < len(tokens):
            token = tokens[cursor]
            if token.value == "(":
                return candidate
            if token.kind == "identifier":
                candidate = token.value
            if token.value in {"{", "}", "=", ";"}:
                break
            cursor += 1
        return candidate
    # 属性名位于冒号、委托、赋值或参数分隔符之前的最后一个标识符。
    candidate = "<anonymous>"
    while cursor < len(tokens):
        token = tokens[cursor]
        if token.value in {":", "=", ",", ")", "by", "get", "set", "{", "}"}:
            return candidate
        if token.kind == "identifier":
            candidate = token.value
        cursor += 1
    return candidate


def _associated_kdoc(tokens: Sequence[Token], visibility_index: int) -> str | None:
    """查找声明前允许跨注解和修饰符关联的最近 KDoc。"""

    # 从可见性向前找到最近的 KDoc。
    kdoc_index = visibility_index - 1
    while kdoc_index >= 0 and tokens[kdoc_index].kind != "kdoc":
        kdoc_index -= 1
    if kdoc_index < 0:
        return None
    # 距离过远的 KDoc 不可能属于当前声明。
    if tokens[visibility_index].line - tokens[kdoc_index].line > 40:
        return None
    cursor = kdoc_index + 1
    while cursor < visibility_index:
        value = tokens[cursor].value
        if value == "@":
            cursor = _skip_annotation(tokens, cursor)
            continue
        if value in DECLARATION_MODIFIERS:
            cursor += 1
            continue
        return None
    return tokens[kdoc_index].value


def _primary_constructor_ranges(
    tokens: Sequence[Token],
) -> list[tuple[int, int, str | None]]:
    """返回公开类主构造头的 token 范围及其类 KDoc。"""

    # 每个范围覆盖类名之后到主构造右括号，便于识别参数属性和显式 constructor。
    ranges: list[tuple[int, int, str | None]] = []
    for visibility_index, visibility in enumerate(tokens):
        if visibility.value not in {"public", "protected"}:
            continue
        keyword_index = _find_declaration_keyword(tokens, visibility_index)
        if keyword_index is None or tokens[keyword_index].value != "class":
            continue
        # 跳过类名和可选泛型参数，随后只在继承冒号或类体之前寻找主构造括号。
        cursor = keyword_index + 1
        while cursor < len(tokens) and tokens[cursor].kind != "identifier":
            cursor += 1
        cursor += 1
        if cursor < len(tokens) and tokens[cursor].value == "<":
            cursor = _skip_balanced(tokens, cursor, "<", ">")
        header_start = cursor
        while cursor < len(tokens) and tokens[cursor].value not in {"{", "}", ":", ";"}:
            if tokens[cursor].value == "(":
                end = _skip_balanced(tokens, cursor, "(", ")")
                ranges.append(
                    (header_start, end, _associated_kdoc(tokens, visibility_index))
                )
                break
            cursor += 1
    return ranges


def _primary_constructor_kdoc(
    tokens: Sequence[Token],
    visibility_index: int,
    kind: str,
    name: str,
    ranges: Sequence[tuple[int, int, str | None]],
) -> str | None:
    """按 Kotlin @property 约定把类 KDoc 关联到主构造声明。"""

    for start, end, class_kdoc in ranges:
        if not (start <= visibility_index < end) or class_kdoc is None:
            continue
        # 主构造器本身由类 KDoc 的职责和构造参数标签共同说明。
        if kind == "constructor":
            return class_kdoc
        if kind not in {"val", "var"}:
            return None
        # Kotlin KDoc 允许在类注释中用 @property（兼容旧 @param）说明参数属性。
        escaped_name = re.escape(name.strip("`"))
        if re.search(rf"@(property|param)\s+`?{escaped_name}`?\b", class_kdoc):
            return class_kdoc
        return None
    return None


def scan_kotlin_file(path: Path, display_path: Path | None = None) -> list[Declaration]:
    """扫描单个 Kotlin 文件中的显式 public/protected 声明。"""

    # display_path 用于让临时测试目录也能生成稳定相对报告。
    report_path = display_path or path
    # UTF-8 是 Kotlin 源码与中文 KDoc 的仓库约定。
    source = path.read_text(encoding="utf-8")
    # token 化一次后同时用于声明识别和 KDoc 关联。
    tokens = tokenize_kotlin(source)
    # 类 KDoc 中的 @property 是主构造属性的官方文档归属方式。
    constructor_ranges = _primary_constructor_ranges(tokens)
    # 当前文件的声明结果。
    declarations: list[Declaration] = []
    for index, token in enumerate(tokens):
        if token.value not in {"public", "protected"}:
            continue
        keyword_index = _find_declaration_keyword(tokens, index)
        if keyword_index is None:
            continue
        # 直接 KDoc 优先；主构造属性可回退到类 KDoc 的同名 @property/@param 标签。
        kdoc = _associated_kdoc(tokens, index)
        if kdoc is None:
            kdoc = _primary_constructor_kdoc(
                tokens,
                index,
                tokens[keyword_index].value,
                _declaration_name(tokens, keyword_index),
                constructor_ranges,
            )
        declarations.append(
            Declaration(
                path=report_path,
                line=token.line,
                offset=token.start,
                visibility=token.value,
                kind=tokens[keyword_index].value,
                name=_declaration_name(tokens, keyword_index),
                kdoc=kdoc,
            )
        )
    return declarations


def is_valid_kdoc(kdoc: str, declaration: Declaration) -> bool:
    """判断 KDoc 是否包含可审阅说明，而非空模板或签名复述。"""

    # 去除注释边界、每行星号和标签，仅用首段职责说明判断有效性。
    body = kdoc.removeprefix("/**").removesuffix("*/")
    cleaned_lines: list[str] = []
    for raw_line in body.splitlines():
        line = raw_line.strip().removeprefix("*").strip()
        if line.startswith("@"):
            break
        if line:
            cleaned_lines.append(line)
    summary = " ".join(cleaned_lines).strip()
    normalized = re.sub(r"[^\w\u3400-\u9fff]+", "", summary).lower()
    normalized_name = re.sub(r"[^\w\u3400-\u9fff]+", "", declaration.name).lower()
    if any(marker in summary.lower() for marker in INVALID_KDOC_MARKERS):
        return False
    # 仓库 AGENTS 约束要求公开职责至少有中文说明；可继续保留英文参数和兼容细节。
    if re.search(r"[\u3400-\u9fff]", summary) is None:
        return False
    if len(normalized) < 4:
        return False
    if normalized in {normalized_name, f"{declaration.kind}{normalized_name}"}:
        return False
    return True


def collect_coverage(source_roots: Iterable[Path]) -> CoverageResult:
    """汇总多个源码根的声明、缺失 KDoc 和无效 KDoc。"""

    # 全部声明按源码根、路径和行号确定性排序。
    declarations: list[Declaration] = []
    for source_root in source_roots:
        for path in sorted(source_root.rglob("*.kt")):
            declarations.extend(scan_kotlin_file(path, path.relative_to(source_root.parent)))
    # 完全缺少 KDoc 的声明。
    missing = tuple(item for item in declarations if item.kdoc is None)
    # 有注释但内容不合格的声明。
    invalid = tuple(
        item for item in declarations if item.kdoc is not None and not is_valid_kdoc(item.kdoc, item)
    )
    documented = len(declarations) - len(missing) - len(invalid)
    return CoverageResult(len(declarations), documented, missing, invalid)


def write_report(result: CoverageResult, report_path: Path, minimum_percent: float) -> None:
    """同时写出便于机器和人工审阅的稳定 JSON 报告。"""

    # 声明转成无源码内容泄漏的精确定位对象。
    def entry(item: Declaration) -> dict[str, object]:
        """把单个声明转换为 JSON 可序列化结构。"""

        return {
            "path": item.path.as_posix(),
            "line": item.line,
            "visibility": item.visibility,
            "kind": item.kind,
            "name": item.name,
        }

    # 报告字段顺序保持稳定，方便 CI diff。
    payload = {
        "schemaVersion": 1,
        "publicProtectedDeclarations": result.total,
        "documentedDeclarations": result.documented,
        "coveragePercent": round(result.percent, 2),
        "minimumPercent": round(minimum_percent, 2),
        "missing": [entry(item) for item in result.missing],
        "invalid": [entry(item) for item in result.invalid],
    }
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def parse_args(argv: Sequence[str]) -> argparse.Namespace:
    """解析命令行参数并返回受检源码根、报告和门槛。"""

    # 参数解析器只暴露构建门禁需要的稳定接口。
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", action="append", required=True, type=Path)
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--minimum-percent", default=100.0, type=float)
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    """执行扫描、写报告，并在覆盖率低于门槛时返回非零。"""

    # 显式 argv 便于单元测试隔离进程参数。
    args = parse_args(sys.argv[1:] if argv is None else argv)
    # 每个源码根必须存在，否则不能把路径错误误判为 100%。
    missing_roots = [path for path in args.source if not path.is_dir()]
    if missing_roots:
        for path in missing_roots:
            print(f"KDoc source root does not exist: {path}", file=sys.stderr)
        return 2
    result = collect_coverage(args.source)
    write_report(result, args.report, args.minimum_percent)
    print(
        "KDoc coverage: "
        f"{result.documented}/{result.total} ({result.percent:.2f}%), "
        f"minimum {args.minimum_percent:.2f}%"
    )
    if result.percent + 1e-9 < args.minimum_percent:
        print(
            f"Missing {len(result.missing)} and invalid {len(result.invalid)} KDoc declarations; "
            f"see {args.report}",
            file=sys.stderr,
        )
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
