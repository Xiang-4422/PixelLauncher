#!/usr/bin/env python3
"""在显式 Android 模拟器上验证历史明文偏好的升级、卸载与 Auto Backup 恢复边界。"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shlex
import subprocess
import sys
import time
from pathlib import Path
from typing import Sequence


# 只用于本验收的独立 applicationId，不触碰日常 debug 或实体设备数据。
TEST_PACKAGE = "com.purride.pixellauncherv2.backupfixture"
# 历史夹具中负责同步写入两份 SharedPreferences 的 Activity。
FIXTURE_COMPONENT = (
    f"{TEST_PACKAGE}/com.purride.pixellauncherv2.backupfixture.LegacySeedActivity"
)
# 当前应用的真实 Activity；启动它会先执行 PixelLauncherApp 的同步清理。
CURRENT_COMPONENT = f"{TEST_PACKAGE}/com.purride.pixellauncherv2.app.MainActivity"
# 历史明文偏好文件的设备内相对路径。
LEGACY_PREFERENCE_PATH = "shared_prefs/pixel_launcher_ai_prefs.xml"
# 非敏感控制偏好用于证明备份传输和升级数据保留确实发生。
CONTROL_PREFERENCE_PATH = "shared_prefs/pixel_launcher_backup_control.xml"
# 历史明文占位值不具备任何真实凭据格式。
LEGACY_MARKER = "fixture-plaintext-not-a-credential"
# 控制值只有在数据保留/恢复成功时才会出现。
CONTROL_MARKER = "fixture-control-must-restore"
# 官方 Android 测试流程使用的本地备份 transport。
LOCAL_TRANSPORT = "com.android.localtransport/.LocalTransport"
# 当前测试 APK 必须高于历史夹具版本，才能构成真实升级/恢复版本关系。
FIXTURE_VERSION_CODE = 1
CURRENT_VERSION_CODE = 2


def parse_arguments(arguments: Sequence[str]) -> argparse.Namespace:
    """解析模拟器序列号、仓库根和证据目录。"""

    # 默认仓库根由当前工具文件位置确定，避免依赖调用目录。
    default_root = Path(__file__).resolve().parents[1]
    # 命令行解析器只接受显式设备，禁止 adb 自动选择。
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True, help="必须是 emulator-* 形式的模拟器序列号。")
    parser.add_argument("--root", type=Path, default=default_root)
    parser.add_argument(
        "--report-directory",
        type=Path,
        default=default_root / "build/reports/security/m0-1-backup-restore",
    )
    parser.add_argument("--skip-build", action="store_true", help="复验现有 APK 时跳过 Gradle 构建。")
    return parser.parse_args(arguments)


def sha256_file(path: Path) -> str:
    """按块计算 APK 的 SHA-256。"""

    # 强摘要对象用于把设备结果绑定到精确 APK。
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while True:
            # 1 MiB 块避免一次读取大型 APK。
            block = stream.read(1024 * 1024)
            if not block:
                break
            digest.update(block)
    return digest.hexdigest()


def run_command(
    command: Sequence[str],
    command_log: list[str],
    *,
    check: bool = True,
    cwd: Path | None = None,
) -> subprocess.CompletedProcess[str]:
    """执行命令、记录完整输出，并在要求时保留非零失败。"""

    # 可复核的 shell 风格命令文本不包含任何真实凭据。
    command_text = shlex.join(str(part) for part in command)
    print(f"[m0-1] {command_text}", flush=True)
    # 所有输出都捕获到证据日志，避免只依赖终端滚动缓冲。
    result = subprocess.run(
        [str(part) for part in command],
        cwd=cwd,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )
    command_log.append(
        f"$ {command_text}\nexitCode={result.returncode}\n{result.stdout.rstrip()}\n",
    )
    if check and result.returncode != 0:
        raise RuntimeError(f"Command failed ({result.returncode}): {command_text}\n{result.stdout}")
    return result


def run_adb(
    serial: str,
    arguments: Sequence[str],
    command_log: list[str],
    *,
    check: bool = True,
) -> subprocess.CompletedProcess[str]:
    """执行始终带 `-s emulator-*` 的 adb 命令。"""

    return run_command(["adb", "-s", serial, *arguments], command_log, check=check)


def run_shell(
    serial: str,
    arguments: Sequence[str],
    command_log: list[str],
    *,
    check: bool = True,
) -> subprocess.CompletedProcess[str]:
    """执行显式模拟器上的 Android shell 子命令。"""

    return run_adb(serial, ["shell", *arguments], command_log, check=check)


def preference_content(
    serial: str,
    relative_path: str,
    command_log: list[str],
) -> str | None:
    """读取测试包的一个 SharedPreferences XML，不存在时返回空。"""

    # run-as 仅能读取同签名 debuggable 测试 APK 的私有目录。
    result = run_shell(
        serial,
        ["run-as", TEST_PACKAGE, "cat", relative_path],
        command_log,
        check=False,
    )
    return result.stdout if result.returncode == 0 else None


def require_marker(content: str | None, marker: str, label: str) -> None:
    """要求指定偏好内容包含测试标记。"""

    if content is None or marker not in content:
        raise AssertionError(f"{label} is missing marker {marker!r}")


def require_absent(content: str | None, marker: str, label: str) -> None:
    """要求指定偏好文件不存在或不再包含历史明文标记。"""

    if content is not None and marker in content:
        raise AssertionError(f"{label} still contains retired plaintext marker")


def wait_for_control_restore(serial: str, command_log: list[str], timeout_seconds: float = 30.0) -> str:
    """等待安装触发的异步 Auto Restore 写回非敏感控制偏好。"""

    # 单调截止时间避免系统时间变化延长等待。
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        # 当前轮读取到的控制 XML。
        content = preference_content(serial, CONTROL_PREFERENCE_PATH, command_log)
        if content is not None and CONTROL_MARKER in content:
            return content
        time.sleep(0.5)
    raise AssertionError("Auto Restore did not restore the non-sensitive control preference")


def installed_version_code(serial: str, command_log: list[str]) -> int:
    """从 PackageManager 输出读取测试包的实际 versionCode。"""

    # dumpsys 输出同时证明当前安装的是夹具还是新版本 APK。
    output = run_shell(
        serial,
        ["dumpsys", "package", TEST_PACKAGE],
        command_log,
    ).stdout
    # versionCode 行可能带 minSdk/targetSdk 等后缀。
    match = re.search(r"\bversionCode=(\d+)\b", output)
    if match is None:
        raise AssertionError("Unable to read installed versionCode")
    return int(match.group(1))


def install_apk(
    serial: str,
    apk: Path,
    command_log: list[str],
    *,
    replace: bool,
) -> None:
    """把一个已绑定 SHA-256 的测试 APK 安装到显式模拟器。"""

    # `-t` 允许仅用于迁移验收的 debuggable 夹具，`-r` 表示真实包升级。
    flags = ["install", "-t"]
    if replace:
        flags.append("-r")
    flags.append(str(apk))
    # 安装输出必须显式包含 Success，不能只依赖 adb 退出码。
    output = run_adb(serial, flags, command_log).stdout
    if "Success" not in output:
        raise AssertionError(f"APK install did not report Success: {apk}")


def seed_legacy_fixture(serial: str, fixture_apk: Path, command_log: list[str]) -> None:
    """安装并启动版本 1 夹具，确认历史和控制数据都真实落盘。"""

    install_apk(serial, fixture_apk, command_log, replace=False)
    if installed_version_code(serial, command_log) != FIXTURE_VERSION_CODE:
        raise AssertionError("Historical fixture versionCode is not 1")
    run_shell(serial, ["am", "start", "-W", "-n", FIXTURE_COMPONENT], command_log)
    require_marker(
        preference_content(serial, LEGACY_PREFERENCE_PATH, command_log),
        LEGACY_MARKER,
        "Historical fixture preference",
    )
    require_marker(
        preference_content(serial, CONTROL_PREFERENCE_PATH, command_log),
        CONTROL_MARKER,
        "Backup control preference",
    )


def uninstall_test_package(serial: str, command_log: list[str]) -> None:
    """卸载独立测试包；未安装状态不视为失败。"""

    run_adb(serial, ["uninstall", TEST_PACKAGE], command_log, check=False)


def select_local_transport(serial: str, command_log: list[str]) -> None:
    """启用官方本地 transport 和自动恢复，并关闭测试数据加密。"""

    run_shell(serial, ["bmgr", "enable", "true"], command_log)
    run_shell(serial, ["bmgr", "autorestore", "true"], command_log)
    # transport 选择输出用于确认本地测试后端真正生效。
    output = run_shell(serial, ["bmgr", "transport", LOCAL_TRANSPORT], command_log).stdout
    if "Selected transport" not in output and LOCAL_TRANSPORT not in output:
        raise AssertionError("Unable to select Android local backup transport")
    run_shell(
        serial,
        ["settings", "put", "secure", "backup_local_transport_parameters", "is_encrypted=false"],
        command_log,
    )


def run_upgrade_scenario(
    serial: str,
    fixture_apk: Path,
    current_apk: Path,
    command_log: list[str],
) -> dict[str, object]:
    """验证 versionCode 1 到 2 的覆盖安装会在首次启动时删除历史明文。"""

    print("[m0-1] scenario=version-upgrade", flush=True)
    uninstall_test_package(serial, command_log)
    run_shell(serial, ["bmgr", "wipe", LOCAL_TRANSPORT, TEST_PACKAGE], command_log, check=False)
    seed_legacy_fixture(serial, fixture_apk, command_log)
    install_apk(serial, current_apk, command_log, replace=True)
    if installed_version_code(serial, command_log) != CURRENT_VERSION_CODE:
        raise AssertionError("Current upgrade APK versionCode is not 2")
    run_shell(serial, ["am", "start", "-W", "-n", CURRENT_COMPONENT], command_log)
    require_marker(
        preference_content(serial, CONTROL_PREFERENCE_PATH, command_log),
        CONTROL_MARKER,
        "Upgrade control preference",
    )
    require_absent(
        preference_content(serial, LEGACY_PREFERENCE_PATH, command_log),
        LEGACY_MARKER,
        "Upgrade legacy preference",
    )
    run_shell(serial, ["am", "force-stop", TEST_PACKAGE], command_log)
    return {
        "status": "passed",
        "fromVersionCode": FIXTURE_VERSION_CODE,
        "toVersionCode": CURRENT_VERSION_CODE,
        "controlDataPreserved": True,
        "legacyPlaintextAbsentAfterFirstStartup": True,
    }


def run_auto_restore_scenario(
    serial: str,
    fixture_apk: Path,
    current_apk: Path,
    command_log: list[str],
) -> dict[str, object]:
    """验证历史 Auto Backup 在卸载后恢复时不会把明文留给新版本。"""

    print("[m0-1] scenario=auto-backup-uninstall-restore", flush=True)
    uninstall_test_package(serial, command_log)
    run_shell(serial, ["bmgr", "wipe", LOCAL_TRANSPORT, TEST_PACKAGE], command_log, check=False)
    seed_legacy_fixture(serial, fixture_apk, command_log)
    # backupnow 输出必须逐包报告 Success，不能只依赖进程退出码。
    backup_output = run_shell(
        serial,
        ["bmgr", "backupnow", "--monitor", TEST_PACKAGE],
        command_log,
    ).stdout
    # 精确成功行必须属于专用测试包，避免其他包结果造成假绿。
    expected_success = f"Package {TEST_PACKAGE} with result: Success"
    if expected_success not in backup_output:
        raise AssertionError(f"Auto Backup did not report success: {backup_output}")
    # 当前 restore set 进入报告，证明安装恢复使用了真实 transport 数据集。
    restore_sets = run_shell(serial, ["bmgr", "list", "sets"], command_log).stdout.strip()
    uninstall_test_package(serial, command_log)
    install_apk(serial, current_apk, command_log, replace=False)
    if installed_version_code(serial, command_log) != CURRENT_VERSION_CODE:
        raise AssertionError("Restored current APK versionCode is not 2")
    wait_for_control_restore(serial, command_log)
    require_absent(
        preference_content(serial, LEGACY_PREFERENCE_PATH, command_log),
        LEGACY_MARKER,
        "Auto Restore legacy preference before Activity startup",
    )
    run_shell(serial, ["am", "start", "-W", "-n", CURRENT_COMPONENT], command_log)
    require_absent(
        preference_content(serial, LEGACY_PREFERENCE_PATH, command_log),
        LEGACY_MARKER,
        "Auto Restore legacy preference after Activity startup",
    )
    run_shell(serial, ["am", "force-stop", TEST_PACKAGE], command_log)
    return {
        "status": "passed",
        "backupResult": "Success",
        "restoreSets": restore_sets,
        "controlDataRestoredBeforeActivityStartup": True,
        "legacyPlaintextAbsentBeforeActivityStartup": True,
        "legacyPlaintextAbsentAfterActivityStartup": True,
    }


def restore_secure_setting(
    serial: str,
    key: str,
    original_value: str,
    command_log: list[str],
) -> None:
    """把测试前的 secure setting 原样恢复。"""

    if original_value in {"", "null"}:
        run_shell(serial, ["settings", "delete", "secure", key], command_log, check=False)
    else:
        run_shell(serial, ["settings", "put", "secure", key, original_value], command_log, check=False)


def write_json_atomic(path: Path, document: dict[str, object]) -> None:
    """原子写出设备验收报告，失败不会覆盖为半份通过文件。"""

    # 同目录临时文件只在完整 JSON 成功序列化后替换目标。
    temporary_path = path.with_name(path.name + ".tmp")
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary_path.write_text(
        json.dumps(document, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    temporary_path.replace(path)


def main(arguments: Sequence[str] | None = None) -> int:
    """构建两代 APK，在模拟器上执行升级和 Auto Backup/restore，并持久化证据。"""

    # 已解析的命令行参数。
    options = parse_arguments(arguments if arguments is not None else sys.argv[1:])
    # 设备序列号必须在任何 adb 操作前拒绝实体设备。
    serial = options.serial.strip()
    if not serial.startswith("emulator-"):
        raise SystemExit("--serial must target emulator-*; physical devices are forbidden for this gate")
    # 仓库和报告目录统一转为绝对路径。
    root = options.root.resolve()
    report_directory = options.report_directory.resolve()
    report_directory.mkdir(parents=True, exist_ok=True)
    # 全部外部命令和输出的审计日志。
    command_log: list[str] = []
    # 最终报告路径即使失败也会写入失败原因。
    report_path = report_directory / "backup-restore-emulator.json"
    # 命令日志路径用于诊断 transport 或 PackageManager 差异。
    command_log_path = report_directory / "commands.log"
    # 本轮结果先按失败初始化，只在两个场景都通过后改为 passed。
    report: dict[str, object] = {
        "schemaVersion": 1,
        "status": "failed",
        "serial": serial,
        "package": TEST_PACKAGE,
    }

    # 测试前系统状态用于 finally 精确恢复，不影响模拟器后续任务。
    original_transport = ""
    original_backup_enabled = False
    original_auto_restore = ""
    original_local_parameters = ""
    try:
        # 在线状态和 qemu 属性形成双重模拟器身份校验。
        if run_adb(serial, ["get-state"], command_log).stdout.strip() != "device":
            raise AssertionError(f"Emulator is not ready: {serial}")
        # ro.kernel.qemu 必须为 1，不能只信任可伪造的 serial 前缀。
        qemu = run_shell(serial, ["getprop", "ro.kernel.qemu"], command_log).stdout.strip()
        if qemu != "1":
            raise AssertionError(f"Target is not an Android emulator: ro.kernel.qemu={qemu!r}")
        # 当前 API 与设备指纹写入证据。
        api_level = int(run_shell(serial, ["getprop", "ro.build.version.sdk"], command_log).stdout.strip())
        device_fingerprint = run_shell(
            serial,
            ["getprop", "ro.build.fingerprint"],
            command_log,
        ).stdout.strip()

        # 保存原 transport、备份开关和 secure settings。
        transport_output = run_shell(serial, ["bmgr", "list", "transports"], command_log).stdout
        transport_match = re.search(r"^\s*\*\s+(\S+)\s*$", transport_output, flags=re.MULTILINE)
        if transport_match is None:
            raise AssertionError("Unable to determine the original backup transport")
        original_transport = transport_match.group(1)
        original_backup_enabled = "currently enabled" in run_shell(
            serial,
            ["bmgr", "enabled"],
            command_log,
        ).stdout
        original_auto_restore = run_shell(
            serial,
            ["settings", "get", "secure", "backup_auto_restore"],
            command_log,
        ).stdout.strip()
        original_local_parameters = run_shell(
            serial,
            ["settings", "get", "secure", "backup_local_transport_parameters"],
            command_log,
        ).stdout.strip()

        # 历史和当前 APK 的固定输出路径。
        fixture_apk = root / "app-backup-fixture/build/outputs/apk/debug/app-backup-fixture-debug.apk"
        current_apk = root / "app/build/outputs/apk/debug/app-debug.apk"
        if not options.skip_build:
            # 当前 APK 用独立包后缀和更高 versionCode，普通 debug 默认值不变。
            run_command(
                [
                    str(root / "gradlew"),
                    ":app-backup-fixture:assembleDebug",
                    ":app:assembleDebug",
                    "-PpixelAppVersionCode=2",
                    "-PpixelAppDebugApplicationIdSuffix=.backupfixture",
                    "--no-build-cache",
                    "--no-daemon",
                ],
                command_log,
                cwd=root,
            )
        if not fixture_apk.is_file() or not current_apk.is_file():
            raise FileNotFoundError("Backup fixture/current APK output is missing")

        select_local_transport(serial, command_log)
        # 两个场景使用同一对精确 APK，但每轮重新卸载和清理 transport 数据。
        # 覆盖安装报告证明普通版本迁移会清理历史明文。
        upgrade_report = run_upgrade_scenario(serial, fixture_apk, current_apk, command_log)
        # 卸载重装报告证明历史 Auto Backup 恢复也会清理明文。
        auto_restore_report = run_auto_restore_scenario(serial, fixture_apk, current_apk, command_log)
        report = {
            "schemaVersion": 1,
            "status": "passed",
            "serial": serial,
            "emulatorVerified": True,
            "apiLevel": api_level,
            "deviceFingerprint": device_fingerprint,
            "package": TEST_PACKAGE,
            "transport": LOCAL_TRANSPORT,
            "fixtureApk": {
                "path": fixture_apk.relative_to(root).as_posix(),
                "versionCode": FIXTURE_VERSION_CODE,
                "sha256": sha256_file(fixture_apk),
            },
            "currentApk": {
                "path": current_apk.relative_to(root).as_posix(),
                "versionCode": CURRENT_VERSION_CODE,
                "sha256": sha256_file(current_apk),
            },
            "versionUpgrade": upgrade_report,
            "autoBackupUninstallRestore": auto_restore_report,
        }
        print(f"Pixel backup/restore emulator gate passed: {report_path}", flush=True)
        # 两个场景全部完成后才返回成功。
        return_code = 0
    except Exception as error:
        report["error"] = str(error)
        print(f"Pixel backup/restore emulator gate FAILED: {error}", file=sys.stderr, flush=True)
        # 任意设备、transport 或断言异常都必须阻断门禁。
        return_code = 1
    finally:
        # 无论成功失败都卸载独立测试包并清除它的本地备份集。
        uninstall_test_package(serial, command_log)
        run_shell(serial, ["bmgr", "wipe", LOCAL_TRANSPORT, TEST_PACKAGE], command_log, check=False)
        if original_transport:
            run_shell(serial, ["bmgr", "transport", original_transport], command_log, check=False)
        run_shell(
            serial,
            ["bmgr", "enable", "true" if original_backup_enabled else "false"],
            command_log,
            check=False,
        )
        restore_secure_setting(
            serial,
            "backup_auto_restore",
            original_auto_restore,
            command_log,
        )
        restore_secure_setting(
            serial,
            "backup_local_transport_parameters",
            original_local_parameters,
            command_log,
        )
        # 命令日志在报告前写出，报告只在完整场景结果明确后落盘。
        command_log_path.write_text("\n".join(command_log), encoding="utf-8")
        write_json_atomic(report_path, report)
    return return_code


if __name__ == "__main__":
    raise SystemExit(main())
