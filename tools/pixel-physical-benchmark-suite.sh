#!/usr/bin/env bash
set -euo pipefail

# 所有工具和报告相对路径都以仓库根目录为锚点。
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# adb 允许在工具测试中注入替身，生产默认使用 Android SDK adb。
ADB_BIN="${PIXEL_ADB_BIN:-adb}"
# connected benchmark wrapper 允许测试注入，生产默认使用仓库正式入口。
CONNECTED_BENCHMARK_SCRIPT="${PIXEL_CONNECTED_BENCHMARK_SCRIPT:-$ROOT_DIR/tools/pixel-connected-benchmark.sh}"
# 实体采集必须显式绑定唯一序列号，禁止选择 adb 默认设备。
TARGET_SERIAL="${PIXEL_BENCHMARK_SERIAL:-}"
# 每个正式矩阵点必须显式声明目标刷新率，单位为 Hz。
TARGET_REFRESH_RATE_HZ="${PIXEL_PHYSICAL_REFRESH_RATE_HZ:-}"
# watchdog 超时负责主机失联后的最终自动恢复，默认一小时。
MAGISK_RESTORE_TIMEOUT_SECONDS="${PIXEL_MAGISK_RESTORE_TIMEOUT_SECONDS:-3600}"
# Magisk 策略切换可以在纯接线测试中显式禁用，正式实体采集保持自动检测。
MAGISK_POLICY_MODE="${PIXEL_MAGISK_POLICY_MODE:-auto}"
# Magisk 26.1 的 su_info 缓存有效三秒；探针间隔必须更长，避免探针自身续期旧策略。
MAGISK_POLICY_SETTLE_SECONDS="${PIXEL_MAGISK_POLICY_SETTLE_SECONDS:-4}"
# 设备唤醒和 Keyguard 解除必须在有限时间内得到 dumpsys 证据，禁止盲目继续采集。
DEVICE_READY_TIMEOUT_SECONDS="${PIXEL_DEVICE_READY_TIMEOUT_SECONDS:-15}"
# 实体性能趋势要求超大核频率上限未被 OEM 温控/省电策略压低，默认最多等待五分钟自然恢复。
CPU_CEILING_TIMEOUT_SECONDS="${PIXEL_CPU_CEILING_TIMEOUT_SECONDS:-300}"
# root 可用时只锁定 AndroidX Microbenchmark 实际选择的超大核；off 用于不允许 sysfs 写入的设备。
PRIME_CPU_LOCK_MODE="${PIXEL_PRIME_CPU_LOCK_MODE:-auto}"
# 设备端频率看门狗覆盖完整 Gradle 执行窗口，超时表示采样证据不完整并拒收本批次。
CPU_FREQUENCY_WATCHDOG_TIMEOUT_SECONDS="${PIXEL_CPU_FREQUENCY_WATCHDOG_TIMEOUT_SECONDS:-$MAGISK_RESTORE_TIMEOUT_SECONDS}"
# 唯一运行令牌把本轮 watchdog 与历史 sentinel 完全隔离。
RUN_TOKEN="$(date -u +%Y%m%dT%H%M%SZ)-$$-$RANDOM"
# 设备临时前缀只包含受控字符，可安全传给 root watchdog。
REMOTE_RUN_PREFIX="/data/local/tmp/pixel-magisk-benchmark-${RUN_TOKEN}"
# watchdog 在设备上的本轮独立脚本路径。
REMOTE_WATCHDOG_SCRIPT="${REMOTE_RUN_PREFIX}.sh"
# ready 标记证明 Shell deny 已写入并由 root 复核。
REMOTE_READY_MARKER="${REMOTE_RUN_PREFIX}.ready"
# restore 标记由主机 EXIT trap 创建，要求 watchdog 立即恢复。
REMOTE_RESTORE_MARKER="${REMOTE_RUN_PREFIX}.restore"
# done 标记证明 Shell allow 已恢复并由 root 复核。
REMOTE_DONE_MARKER="${REMOTE_RUN_PREFIX}.done"
# BusyBox start-stop-daemon 的唯一 pidfile 用于完成双重 fork，避免 Magisk su 等待后台子进程。
REMOTE_PID_FILE="${REMOTE_RUN_PREFIX}.pid"
# 频率看门狗使用独立前缀，避免 Magisk 策略恢复脚本误删其证据。
REMOTE_CPU_WATCHDOG_PREFIX="/data/local/tmp/pixel-cpu-frequency-${RUN_TOKEN}"
# 频率看门狗脚本在设备上的唯一临时路径。
REMOTE_CPU_WATCHDOG_SCRIPT="${REMOTE_CPU_WATCHDOG_PREFIX}.sh"
# ready 标记证明设备端已经开始逐秒采样超大核频率。
REMOTE_CPU_READY_MARKER="${REMOTE_CPU_WATCHDOG_PREFIX}.ready"
# stop 标记要求频率看门狗完成最后一次采样并退出。
REMOTE_CPU_STOP_MARKER="${REMOTE_CPU_WATCHDOG_PREFIX}.stop"
# done 标记证明频率采样窗口已经完整关闭。
REMOTE_CPU_DONE_MARKER="${REMOTE_CPU_WATCHDOG_PREFIX}.done"
# violation 文件保存采集期间第一次频率不变量破坏。
REMOTE_CPU_VIOLATION_MARKER="${REMOTE_CPU_WATCHDOG_PREFIX}.violation"
# pidfile 让 BusyBox start-stop-daemon 脱离 Magisk su 会话运行。
REMOTE_CPU_PID_FILE="${REMOTE_CPU_WATCHDOG_PREFIX}.pid"
# 设备准备标记决定 EXIT trap 是否需要恢复原系统设置。
DEVICE_PREPARED=0
# 性能模式准备标记决定频率等待失败时是否仍需恢复用户原始功耗档位。
POWER_MODE_PREPARED=0
# 超大核锁定标记决定 EXIT trap 是否需要恢复原 governor 与 cpufreq 边界。
PRIME_CPU_LOCK_ACTIVE=0
# 频率看门狗激活标记决定 EXIT trap 是否必须等待设备端完成。
CPU_FREQUENCY_WATCHDOG_ACTIVE=0
# Magisk workaround 激活标记决定 EXIT trap 是否需要等待 root 恢复。
MAGISK_WATCHDOG_ACTIVE=0
# 清理失败计数确保成功 benchmark 不能掩盖环境恢复失败。
CLEANUP_FAILURES=0
# 原始刷新率与常亮设置在准备阶段读取，清理阶段逐项恢复。
ORIGINAL_PEAK_REFRESH_RATE=""
ORIGINAL_MIN_REFRESH_RATE=""
ORIGINAL_USER_REFRESH_RATE=""
ORIGINAL_STAY_ON_WHILE_PLUGGED_IN=""
# 原始屏幕是否清醒决定清理阶段是否需要重新休眠，避免烟测改变用户设备状态。
ORIGINAL_DEVICE_WAS_AWAKE=1
# 原始性能与均衡模式设置必须成对恢复，避免实体采集改变用户功耗偏好。
ORIGINAL_POWER_PERFORMANCE_MODE=""
ORIGINAL_POWER_BALANCED_MODE=""
# 超大核编号与原始 governor/min/max 只在成功读取后用于可逆恢复。
PRIME_CPU_INDEX=""
ORIGINAL_PRIME_CPU_GOVERNOR=""
ORIGINAL_PRIME_CPU_MIN_KHZ=""
ORIGINAL_PRIME_CPU_MAX_KHZ=""
# 超大核目标频率由硬件上限快照确定，并传给设备端看门狗。
PRIME_CPU_TARGET_KHZ=""

# 通过显式序列号调用 adb，所有设备动作都集中经过该入口。
adb_target() {
  "$ADB_BIN" -s "$TARGET_SERIAL" "$@"
}

# 读取一个 Android setting；不存在的键保留为字符串 null 以便精确删除恢复。
read_setting() {
  # namespace 区分 system/global/secure，禁止在调用点拼接 shell 命令。
  local namespace="$1"
  # setting_name 是需要保存并最终恢复的系统键。
  local setting_name="$2"
  adb_target shell settings get "$namespace" "$setting_name" | tr -d '\r' | xargs
}

# 按采集前值恢复一个 Android setting，原值 null 时删除临时新增键。
restore_setting() {
  # namespace 与读取阶段保持一致。
  local namespace="$1"
  # setting_name 是本轮曾临时修改的系统键。
  local setting_name="$2"
  # original_value 是准备阶段保存的原始文本值。
  local original_value="$3"
  if [[ "$original_value" == "null" || -z "$original_value" ]]; then
    adb_target shell settings delete "$namespace" "$setting_name" >/dev/null
  else
    adb_target shell settings put "$namespace" "$setting_name" "$original_value"
  fi
}

# 判断当前 Shell 是否已经获得 root；失败只表示无需使用 Magisk workaround。
shell_has_root() {
  # root_probe_output 同时要求零退出码和明确 uid=0，拒绝仅靠命令存在判断。
  local root_probe_output
  root_probe_output="$(adb_target shell su -c id 2>/dev/null || true)"
  [[ "$root_probe_output" == *"uid=0(root)"* ]]
}

# 读取 Magisk 数据库中的 Shell 策略行，供严格的标准策略检查使用。
read_magisk_shell_policy() {
  adb_target shell "su -c 'magisk --sqlite \"SELECT uid,policy,until,logging,notification FROM policies WHERE uid=2000;\"'" | tr -d '\r'
}

# 等待一个设备标记出现；固定短轮询避免 adb shell 长时间阻塞主机清理。
wait_for_remote_marker() {
  # marker_path 是本轮唯一 ready/done 文件。
  local marker_path="$1"
  # timeout_seconds 是主机等待标记的上限。
  local timeout_seconds="$2"
  # elapsed_seconds 记录已经执行的轮询次数。
  local elapsed_seconds=0
  while [[ "$elapsed_seconds" -lt "$timeout_seconds" ]]; do
    if adb_target shell test -e "$marker_path"; then
      return 0
    fi
    sleep 1
    elapsed_seconds=$((elapsed_seconds + 1))
  done
  return 1
}

# 在 watchdog 写回数据库后轮询 Magisk daemon 的 Shell 授权缓存，不能只相信 done 文件。
wait_for_shell_root() {
  # timeout_seconds 是等待 `su -c id` 重新返回 uid=0 的上限。
  local timeout_seconds="$1"
  # elapsed_seconds 记录已经执行的 root 探针次数。
  local elapsed_seconds=0
  while [[ "$elapsed_seconds" -lt "$timeout_seconds" ]]; do
    # 必须先静默等待旧 deny 缓存过期；立即探针会把旧缓存再续期三秒。
    sleep "$MAGISK_POLICY_SETTLE_SECONDS"
    elapsed_seconds=$((elapsed_seconds + MAGISK_POLICY_SETTLE_SECONDS))
    if shell_has_root; then
      return 0
    fi
  done
  return 1
}

# 在 watchdog 发布 ready 后轮询 Magisk daemon，直到新的 Shell su 请求确实被拒绝。
wait_for_shell_root_denied() {
  # timeout_seconds 是等待 allow 缓存失效的上限。
  local timeout_seconds="$1"
  # elapsed_seconds 记录已经执行的 deny 探针次数。
  local elapsed_seconds=0
  while [[ "$elapsed_seconds" -lt "$timeout_seconds" ]]; do
    # 必须先静默等待旧 allow 缓存过期；立即探针会把旧缓存再续期三秒。
    sleep "$MAGISK_POLICY_SETTLE_SECONDS"
    elapsed_seconds=$((elapsed_seconds + MAGISK_POLICY_SETTLE_SECONDS))
    if ! shell_has_root; then
      return 0
    fi
  done
  return 1
}

# 根据跨 Android 版本常见的 policy 字段判断 Keyguard 已明确解除；未知输出按未就绪处理。
keyguard_is_unlocked() {
  # window_policy_output 是本轮 `dumpsys window policy` 的完整文本快照。
  local window_policy_output="$1"
  if [[ "$window_policy_output" == *"showing=true"* ||
        "$window_policy_output" == *"mIsShowing=true"* ||
        "$window_policy_output" == *"mShowingLockscreen=true"* ||
        "$window_policy_output" == *"keyguardShowing=true"* ]]; then
    return 1
  fi
  [[ "$window_policy_output" == *"showing=false"* ||
     "$window_policy_output" == *"mIsShowing=false"* ||
     "$window_policy_output" == *"mShowingLockscreen=false"* ||
     "$window_policy_output" == *"keyguardShowing=false"* ]]
}

# 读取 PowerManager 与 WindowManager 证据，要求屏幕清醒、AC/USB 常亮生效且 Keyguard 已解除。
device_is_ready() {
  # power_output 证明设备不会在 Gradle 安装阶段重新休眠。
  local power_output
  # window_policy_output 证明测试 Activity 不会被锁屏策略拒绝启动。
  local window_policy_output
  power_output="$(adb_target shell dumpsys power | tr -d '\r')"
  window_policy_output="$(adb_target shell dumpsys window policy | tr -d '\r')"
  [[ "$power_output" == *"mWakefulness=Awake"* ]] &&
    [[ "$power_output" == *"mStayOn=true"* ]] &&
    [[ "$power_output" == *"mStayOnWhilePluggedInSetting=7"* ]] &&
    keyguard_is_unlocked "$window_policy_output"
}

# 在限定时间内等待真实设备状态稳定；每次都重新读取 dumpsys，不能复用命令成功码。
wait_for_device_ready() {
  # timeout_seconds 是等待屏幕和 Keyguard 达到可采集状态的上限。
  local timeout_seconds="$1"
  # elapsed_seconds 记录已等待的完整秒数。
  local elapsed_seconds=0
  while [[ "$elapsed_seconds" -lt "$timeout_seconds" ]]; do
    if device_is_ready; then
      return 0
    fi
    sleep 1
    elapsed_seconds=$((elapsed_seconds + 1))
  done
  return 1
}

# 读取所有在线 CPU 中硬件最高频率核心的编号、硬件上限和当前策略上限。
read_prime_cpu_ceiling() {
  # cpu_count 是内核公开的逻辑 CPU 总数，无法读取时拒绝伪造频率就绪状态。
  local cpu_count
  cpu_count="$(adb_target shell getconf _NPROCESSORS_CONF 2>/dev/null | tr -d '\r' | xargs)"
  if [[ ! "$cpu_count" =~ ^[0-9]+$ || "$cpu_count" -lt 1 ]]; then
    return 1
  fi
  # prime_cpu_index 保存 cpuinfo_max_freq 最大的在线核心编号。
  local prime_cpu_index=-1
  # prime_hardware_max_khz 保存目标核心的硬件最高频率，单位为 kHz。
  local prime_hardware_max_khz=-1
  # prime_scaling_max_khz 保存同一核心当前 cpufreq 策略允许的最高频率，单位为 kHz。
  local prime_scaling_max_khz=-1
  # cpu_index 遍历内核公开的每个逻辑 CPU。
  local cpu_index
  for ((cpu_index = 0; cpu_index < cpu_count; cpu_index += 1)); do
    # cpu_root 是当前核心的 sysfs 根目录。
    local cpu_root="/sys/devices/system/cpu/cpu${cpu_index}"
    # online_value 缺失时表示不可热插拔的 CPU，按在线处理。
    local online_value
    online_value="$(adb_target shell cat "$cpu_root/online" 2>/dev/null | tr -d '\r' | xargs || true)"
    if [[ "$online_value" == "0" ]]; then
      continue
    fi
    # hardware_max_khz 是当前核心不可由 governor 提高的 cpuinfo 上限。
    local hardware_max_khz
    hardware_max_khz="$(adb_target shell cat "$cpu_root/cpufreq/cpuinfo_max_freq" 2>/dev/null | tr -d '\r' | xargs || true)"
    # scaling_max_khz 是温控、功耗和 governor 共同限制后的当前策略上限。
    local scaling_max_khz
    scaling_max_khz="$(adb_target shell cat "$cpu_root/cpufreq/scaling_max_freq" 2>/dev/null | tr -d '\r' | xargs || true)"
    if [[ ! "$hardware_max_khz" =~ ^[0-9]+$ || ! "$scaling_max_khz" =~ ^[0-9]+$ ]]; then
      continue
    fi
    if [[ "$hardware_max_khz" -gt "$prime_hardware_max_khz" ]]; then
      prime_cpu_index="$cpu_index"
      prime_hardware_max_khz="$hardware_max_khz"
      prime_scaling_max_khz="$scaling_max_khz"
    fi
  done
  if [[ "$prime_cpu_index" -lt 0 ]]; then
    return 1
  fi
  printf '%s|%s|%s\n' "$prime_cpu_index" "$prime_hardware_max_khz" "$prime_scaling_max_khz"
}

# 判断超大核当前策略上限是否已经恢复到硬件上限，避免 AndroidX cpuLocked 假阳性。
cpu_ceiling_is_ready() {
  # ceiling_snapshot 固定为 core|hardwareMaxKHz|scalingMaxKHz 三字段格式。
  local ceiling_snapshot
  ceiling_snapshot="$(read_prime_cpu_ceiling)" || return 1
  # prime_cpu_index 仅用于验证字段完整，诊断函数会输出该编号。
  local prime_cpu_index
  # prime_hardware_max_khz 是同配置基线要求达到的硬件上限。
  local prime_hardware_max_khz
  # prime_scaling_max_khz 是本轮读取的实际策略上限。
  local prime_scaling_max_khz
  IFS='|' read -r prime_cpu_index prime_hardware_max_khz prime_scaling_max_khz <<< "$ceiling_snapshot"
  [[ "$prime_cpu_index" =~ ^[0-9]+$ &&
     "$prime_hardware_max_khz" =~ ^[0-9]+$ &&
     "$prime_scaling_max_khz" =~ ^[0-9]+$ &&
     "$prime_scaling_max_khz" -ge "$prime_hardware_max_khz" ]]
}

# 在屏幕仍可休眠降温时等待超大核解除频率限制，超时后明确拒绝不可比采集。
wait_for_cpu_ceiling() {
  # timeout_seconds 是允许 OEM 温控/功耗策略自然恢复的最长时间。
  local timeout_seconds="$1"
  # elapsed_seconds 记录已经等待的完整秒数。
  local elapsed_seconds=0
  while [[ "$elapsed_seconds" -lt "$timeout_seconds" ]]; do
    if cpu_ceiling_is_ready; then
      return 0
    fi
    sleep 1
    elapsed_seconds=$((elapsed_seconds + 1))
  done
  return 1
}

# 输出超大核频率与电池温度诊断，区分 SDK 回退和设备频率受限。
print_cpu_ceiling_diagnostics() {
  # ceiling_snapshot 保留最后一次可读取的核心、硬件上限与策略上限。
  local ceiling_snapshot
  ceiling_snapshot="$(read_prime_cpu_ceiling 2>/dev/null || true)"
  echo "实体设备 prime CPU ceiling：${ceiling_snapshot:-unreadable}（core|hardwareMaxKHz|scalingMaxKHz）" >&2
  adb_target shell dumpsys battery | grep -E 'status:|level:|temperature:' >&2 || true
}

# 保存用户功耗档位并临时启用 MIUI 性能模式，让同设备趋势具备相同超大核上限。
prepare_power_mode() {
  ORIGINAL_POWER_PERFORMANCE_MODE="$(read_setting system POWER_PERFORMANCE_MODE_OPEN)"
  ORIGINAL_POWER_BALANCED_MODE="$(read_setting system POWER_BALANCED_MODE_OPEN)"
  POWER_MODE_PREPARED=1
  adb_target shell settings put system POWER_PERFORMANCE_MODE_OPEN 1
  adb_target shell settings put system POWER_BALANCED_MODE_OPEN 0
}

# root 可用时把 AndroidX 实际选择的超大核固定在硬件最高频率，并保存完整恢复状态。
lock_prime_cpu_at_hardware_max_if_possible() {
  if [[ "$PRIME_CPU_LOCK_MODE" == "off" ]]; then
    return
  fi
  if ! shell_has_root; then
    echo "设备 Shell 当前没有 root；保留频率上限门禁但跳过超大核固定。"
    return
  fi
  # ceiling_snapshot 固定为 core|hardwareMaxKHz|scalingMaxKHz 三字段格式。
  local ceiling_snapshot
  ceiling_snapshot="$(read_prime_cpu_ceiling)" || {
    echo "无法读取超大核 cpufreq 信息；拒绝执行频率固定。" >&2
    exit 1
  }
  # prime_hardware_max_khz 是本轮要写入 userspace setspeed 的目标频率。
  local prime_hardware_max_khz
  # observed_scaling_max_khz 仅用于解析并验证快照字段完整。
  local observed_scaling_max_khz
  IFS='|' read -r PRIME_CPU_INDEX prime_hardware_max_khz observed_scaling_max_khz <<< "$ceiling_snapshot"
  PRIME_CPU_TARGET_KHZ="$prime_hardware_max_khz"
  # prime_cpu_root 是本轮唯一允许写入的超大核 sysfs 根目录。
  local prime_cpu_root="/sys/devices/system/cpu/cpu${PRIME_CPU_INDEX}/cpufreq"
  ORIGINAL_PRIME_CPU_GOVERNOR="$(adb_target shell cat "$prime_cpu_root/scaling_governor" | tr -d '\r' | xargs)"
  ORIGINAL_PRIME_CPU_MIN_KHZ="$(adb_target shell cat "$prime_cpu_root/scaling_min_freq" | tr -d '\r' | xargs)"
  ORIGINAL_PRIME_CPU_MAX_KHZ="$(adb_target shell cat "$prime_cpu_root/scaling_max_freq" | tr -d '\r' | xargs)"
  if [[ -z "$ORIGINAL_PRIME_CPU_GOVERNOR" ||
        ! "$ORIGINAL_PRIME_CPU_MIN_KHZ" =~ ^[0-9]+$ ||
        ! "$ORIGINAL_PRIME_CPU_MAX_KHZ" =~ ^[0-9]+$ ||
        ! "$prime_hardware_max_khz" =~ ^[0-9]+$ ]]; then
    echo "超大核原始 governor/min/max 不完整；拒绝执行频率固定。" >&2
    exit 1
  fi
  PRIME_CPU_LOCK_ACTIVE=1
  adb_target shell "su -c 'echo userspace > $prime_cpu_root/scaling_governor'"
  adb_target shell "su -c 'echo $prime_hardware_max_khz > $prime_cpu_root/scaling_max_freq'"
  adb_target shell "su -c 'echo $prime_hardware_max_khz > $prime_cpu_root/scaling_setspeed'"
  if ! prime_cpu_runtime_is_ready; then
    echo "超大核未能固定到 ${prime_hardware_max_khz}kHz；拒绝启动性能采集。" >&2
    print_prime_cpu_runtime_diagnostics
    exit 1
  fi
}

# 在 root 权限仍可用时启动设备端逐秒频率采样，避免主机 adb 轮询扰动 benchmark。
start_cpu_frequency_watchdog_if_needed() {
  if [[ "$PRIME_CPU_LOCK_ACTIVE" -ne 1 ]]; then
    return
  fi
  adb_target push "$ROOT_DIR/tools/pixel-cpu-frequency-watchdog.sh" "$REMOTE_CPU_WATCHDOG_SCRIPT" >/dev/null
  # BusyBox 双重 fork 让监控覆盖后续 Magisk Shell deny 和完整 Gradle 执行窗口。
  adb_target shell "su -c 'chmod 0700 $REMOTE_CPU_WATCHDOG_SCRIPT && BB=\$(magisk --path)/.magisk/busybox/busybox && test -x \$BB && \$BB start-stop-daemon -S -b -m -p $REMOTE_CPU_PID_FILE -x /system/bin/sh -- $REMOTE_CPU_WATCHDOG_SCRIPT $REMOTE_CPU_WATCHDOG_PREFIX $PRIME_CPU_INDEX $PRIME_CPU_TARGET_KHZ $CPU_FREQUENCY_WATCHDOG_TIMEOUT_SECONDS'"
  CPU_FREQUENCY_WATCHDOG_ACTIVE=1
  if ! wait_for_remote_marker "$REMOTE_CPU_READY_MARKER" 15; then
    echo "CPU 频率看门狗未在 15 秒内发布 ready 标记。" >&2
    exit 1
  fi
}

# 请求设备端频率看门狗停止并等待最后一次采样落盘；可由主流程和 EXIT trap 重复调用。
stop_cpu_frequency_watchdog() {
  if [[ "$CPU_FREQUENCY_WATCHDOG_ACTIVE" -ne 1 ]]; then
    return 0
  fi
  adb_target shell touch "$REMOTE_CPU_STOP_MARKER" >/dev/null 2>&1 || return 1
  wait_for_remote_marker "$REMOTE_CPU_DONE_MARKER" 15
}

# 读取频率拒收证据；无 violation 文件时返回成功且不输出占位文本。
read_cpu_frequency_violation() {
  adb_target shell cat "$REMOTE_CPU_VIOLATION_MARKER" 2>/dev/null | tr -d '\r'
}

# 验证被锁定超大核的 governor、当前频率、setspeed 和上限仍等于硬件最高频率。
prime_cpu_runtime_is_ready() {
  if [[ "$PRIME_CPU_LOCK_ACTIVE" -ne 1 ]]; then
    return 1
  fi
  # prime_cpu_root 是已保存超大核的 cpufreq sysfs 根目录。
  local prime_cpu_root="/sys/devices/system/cpu/cpu${PRIME_CPU_INDEX}/cpufreq"
  # prime_hardware_max_khz 是硬件不可提升的最高频率。
  local prime_hardware_max_khz
  prime_hardware_max_khz="$(adb_target shell cat "$prime_cpu_root/cpuinfo_max_freq" 2>/dev/null | tr -d '\r' | xargs || true)"
  # prime_governor 必须保持 userspace，避免 OEM governor 动态改频。
  local prime_governor
  prime_governor="$(adb_target shell cat "$prime_cpu_root/scaling_governor" 2>/dev/null | tr -d '\r' | xargs || true)"
  # prime_current_khz 是本轮实际运行频率，而非仅配置上限。
  local prime_current_khz
  prime_current_khz="$(adb_target shell cat "$prime_cpu_root/scaling_cur_freq" 2>/dev/null | tr -d '\r' | xargs || true)"
  # prime_setspeed_khz 是 userspace governor 的固定目标频率。
  local prime_setspeed_khz
  prime_setspeed_khz="$(adb_target shell cat "$prime_cpu_root/scaling_setspeed" 2>/dev/null | tr -d '\r' | xargs || true)"
  # prime_scaling_max_khz 是温控/功耗策略仍可能压低的策略上限。
  local prime_scaling_max_khz
  prime_scaling_max_khz="$(adb_target shell cat "$prime_cpu_root/scaling_max_freq" 2>/dev/null | tr -d '\r' | xargs || true)"
  [[ "$prime_hardware_max_khz" =~ ^[0-9]+$ &&
     "$prime_governor" == "userspace" &&
     "$prime_current_khz" == "$prime_hardware_max_khz" &&
     "$prime_setspeed_khz" == "$prime_hardware_max_khz" &&
     "$prime_scaling_max_khz" == "$prime_hardware_max_khz" ]]
}

# 有锁时验证实际固定频率，无锁设备仍使用超大核策略上限门禁。
cpu_runtime_is_ready() {
  if [[ "$PRIME_CPU_LOCK_ACTIVE" -eq 1 ]]; then
    prime_cpu_runtime_is_ready
  else
    cpu_ceiling_is_ready
  fi
}

# 输出超大核实际 governor/current/setspeed/min/max，供频率竞态拒收记录复核。
print_prime_cpu_runtime_diagnostics() {
  if [[ -z "$PRIME_CPU_INDEX" ]]; then
    echo "实体设备 prime CPU runtime：unavailable" >&2
    return
  fi
  # prime_cpu_root 是诊断读取使用的超大核 cpufreq sysfs 根目录。
  local prime_cpu_root="/sys/devices/system/cpu/cpu${PRIME_CPU_INDEX}/cpufreq"
  # frequency_field 遍历频率锁定必须同时成立的六个 sysfs 字段。
  local frequency_field
  for frequency_field in scaling_governor scaling_cur_freq scaling_setspeed scaling_min_freq scaling_max_freq cpuinfo_max_freq; do
    echo "prime cpu${PRIME_CPU_INDEX} ${frequency_field}=$(adb_target shell cat "$prime_cpu_root/$frequency_field" 2>/dev/null | tr -d '\r' | xargs || true)" >&2
  done
}

# 输出有限的设备就绪诊断，帮助区分休眠、常亮未生效和 Keyguard 未解除。
print_device_readiness_diagnostics() {
  adb_target shell dumpsys power | grep -E 'mWakefulness=|mStayOn=|mStayOnWhilePluggedInSetting=' >&2 || true
  adb_target shell dumpsys window policy | grep -E 'showing=|mIsShowing=|mShowingLockscreen=|keyguardShowing=|interactiveState=' >&2 || true
}

# 保存设备设置并切换到本轮声明的刷新率、常亮、经验证解锁和 HOME 初态。
prepare_device() {
  # original_power_output 只用于保存进入采集前的 Wakefulness，不复用为就绪证据。
  local original_power_output
  original_power_output="$(adb_target shell dumpsys power | tr -d '\r')"
  if [[ "$original_power_output" == *"mWakefulness=Awake"* ]]; then
    ORIGINAL_DEVICE_WAS_AWAKE=1
  else
    ORIGINAL_DEVICE_WAS_AWAKE=0
  fi
  ORIGINAL_PEAK_REFRESH_RATE="$(read_setting system peak_refresh_rate)"
  ORIGINAL_MIN_REFRESH_RATE="$(read_setting system min_refresh_rate)"
  ORIGINAL_USER_REFRESH_RATE="$(read_setting system user_refresh_rate)"
  ORIGINAL_STAY_ON_WHILE_PLUGGED_IN="$(read_setting global stay_on_while_plugged_in)"
  DEVICE_PREPARED=1
  adb_target shell settings put system peak_refresh_rate "$TARGET_REFRESH_RATE_HZ"
  adb_target shell settings put system min_refresh_rate "$TARGET_REFRESH_RATE_HZ"
  adb_target shell settings put system user_refresh_rate "$TARGET_REFRESH_RATE_HZ"
  adb_target shell settings put global stay_on_while_plugged_in 7
  adb_target shell input keyevent KEYCODE_WAKEUP
  adb_target shell wm dismiss-keyguard
  adb_target shell input keyevent KEYCODE_HOME
  if ! wait_for_device_ready "$DEVICE_READY_TIMEOUT_SECONDS"; then
    echo "实体设备未在 ${DEVICE_READY_TIMEOUT_SECONDS} 秒内保持清醒并解除 Keyguard；拒绝启动性能采集。" >&2
    print_device_readiness_diagnostics
    exit 1
  fi
}

# 在标准 Magisk allow 策略上启动 root watchdog，并确认普通 Shell 已被临时拒绝。
start_magisk_watchdog_if_needed() {
  if [[ "$MAGISK_POLICY_MODE" == "off" ]]; then
    return
  fi
  if ! shell_has_root; then
    echo "设备 Shell 当前没有 root；跳过 Magisk 交互式 su workaround。"
    return
  fi
  # current_policy 必须与用户原始标准策略精确一致，避免覆盖自定义授权。
  local current_policy
  current_policy="$(read_magisk_shell_policy)"
  if [[ "$current_policy" != *"uid=2000"* || "$current_policy" != *"policy=2"* || "$current_policy" != *"until=0"* || "$current_policy" != *"logging=1"* || "$current_policy" != *"notification=1"* ]]; then
    echo "拒绝修改非标准 Magisk Shell 策略：$current_policy" >&2
    exit 2
  fi
  adb_target push "$ROOT_DIR/tools/pixel-magisk-shell-policy-watchdog.sh" "$REMOTE_WATCHDOG_SCRIPT" >/dev/null
  # Magisk BusyBox start-stop-daemon 会双重 fork；仅使用 `&`/setsid 在 Magisk 26.1 上仍会占住 su 会话。
  adb_target shell "su -c 'chmod 0700 $REMOTE_WATCHDOG_SCRIPT && BB=\$(magisk --path)/.magisk/busybox/busybox && test -x \$BB && \$BB start-stop-daemon -S -b -m -p $REMOTE_PID_FILE -x /system/bin/sh -- $REMOTE_WATCHDOG_SCRIPT $REMOTE_RUN_PREFIX $MAGISK_RESTORE_TIMEOUT_SECONDS'"
  MAGISK_WATCHDOG_ACTIVE=1
  if ! wait_for_remote_marker "$REMOTE_READY_MARKER" 15; then
    echo "Magisk watchdog 未在 15 秒内发布 ready 标记。" >&2
    exit 1
  fi
  if ! wait_for_shell_root_denied 30; then
    echo "Magisk watchdog 已 ready，但 Shell root 在 30 秒内仍未被拒绝。" >&2
    exit 1
  fi
}

# 恢复 Magisk、刷新率与常亮设置；任一失败都会改变原成功命令的最终退出码。
cleanup_environment() {
  # benchmark_status 由 trap 显式传入，避免 local 声明覆盖进入 trap 前的真实退出码。
  local benchmark_status="$1"
  trap - EXIT INT TERM
  set +e
  if [[ "$CPU_FREQUENCY_WATCHDOG_ACTIVE" -eq 1 ]]; then
    if ! stop_cpu_frequency_watchdog; then
      echo "CPU 频率看门狗未在 15 秒内确认停止。" >&2
      CLEANUP_FAILURES=$((CLEANUP_FAILURES + 1))
    fi
  fi
  if [[ "$MAGISK_WATCHDOG_ACTIVE" -eq 1 ]]; then
    adb_target shell touch "$REMOTE_RESTORE_MARKER" >/dev/null 2>&1
    if ! wait_for_remote_marker "$REMOTE_DONE_MARKER" 30; then
      echo "Magisk watchdog 未在 30 秒内确认策略恢复；设备端超时仍会继续兜底。" >&2
      CLEANUP_FAILURES=$((CLEANUP_FAILURES + 1))
    fi
    if ! wait_for_shell_root 60; then
      echo "清理后 Shell root 未恢复。" >&2
      CLEANUP_FAILURES=$((CLEANUP_FAILURES + 1))
    else
      # restored_policy 必须重新满足采集前标准 allow 不变量。
      local restored_policy
      restored_policy="$(read_magisk_shell_policy 2>/dev/null || true)"
      if [[ "$restored_policy" != *"uid=2000"* || "$restored_policy" != *"policy=2"* || "$restored_policy" != *"until=0"* || "$restored_policy" != *"logging=1"* || "$restored_policy" != *"notification=1"* ]]; then
        echo "清理后 Magisk Shell 策略不符合标准 allow：$restored_policy" >&2
        CLEANUP_FAILURES=$((CLEANUP_FAILURES + 1))
      fi
      adb_target shell "su -c 'rm -f ${REMOTE_RUN_PREFIX}.*'" >/dev/null 2>&1 || CLEANUP_FAILURES=$((CLEANUP_FAILURES + 1))
    fi
  fi
  if [[ "$CPU_FREQUENCY_WATCHDOG_ACTIVE" -eq 1 ]]; then
    if shell_has_root; then
      adb_target shell "su -c 'rm -f ${REMOTE_CPU_WATCHDOG_PREFIX}.*'" >/dev/null 2>&1 || CLEANUP_FAILURES=$((CLEANUP_FAILURES + 1))
    else
      echo "清理 CPU 频率看门狗文件时 Shell root 不可用。" >&2
      CLEANUP_FAILURES=$((CLEANUP_FAILURES + 1))
    fi
  fi
  if [[ "$PRIME_CPU_LOCK_ACTIVE" -eq 1 ]]; then
    # prime_cpu_root 是需要恢复用户原始 governor/min/max 的唯一核心目录。
    local prime_cpu_root="/sys/devices/system/cpu/cpu${PRIME_CPU_INDEX}/cpufreq"
    adb_target shell "su -c 'echo $ORIGINAL_PRIME_CPU_MIN_KHZ > $prime_cpu_root/scaling_min_freq'" || CLEANUP_FAILURES=$((CLEANUP_FAILURES + 1))
    adb_target shell "su -c 'echo $ORIGINAL_PRIME_CPU_MAX_KHZ > $prime_cpu_root/scaling_max_freq'" || CLEANUP_FAILURES=$((CLEANUP_FAILURES + 1))
    adb_target shell "su -c 'echo $ORIGINAL_PRIME_CPU_GOVERNOR > $prime_cpu_root/scaling_governor'" || CLEANUP_FAILURES=$((CLEANUP_FAILURES + 1))
  fi
  if [[ "$DEVICE_PREPARED" -eq 1 ]]; then
    restore_setting system peak_refresh_rate "$ORIGINAL_PEAK_REFRESH_RATE" || CLEANUP_FAILURES=$((CLEANUP_FAILURES + 1))
    restore_setting system min_refresh_rate "$ORIGINAL_MIN_REFRESH_RATE" || CLEANUP_FAILURES=$((CLEANUP_FAILURES + 1))
    restore_setting system user_refresh_rate "$ORIGINAL_USER_REFRESH_RATE" || CLEANUP_FAILURES=$((CLEANUP_FAILURES + 1))
    restore_setting global stay_on_while_plugged_in "$ORIGINAL_STAY_ON_WHILE_PLUGGED_IN" || CLEANUP_FAILURES=$((CLEANUP_FAILURES + 1))
  fi
  if [[ "$POWER_MODE_PREPARED" -eq 1 ]]; then
    restore_setting system POWER_PERFORMANCE_MODE_OPEN "$ORIGINAL_POWER_PERFORMANCE_MODE" || CLEANUP_FAILURES=$((CLEANUP_FAILURES + 1))
    restore_setting system POWER_BALANCED_MODE_OPEN "$ORIGINAL_POWER_BALANCED_MODE" || CLEANUP_FAILURES=$((CLEANUP_FAILURES + 1))
  fi
  if [[ "$DEVICE_PREPARED" -eq 1 && "$ORIGINAL_DEVICE_WAS_AWAKE" -eq 0 ]]; then
    adb_target shell input keyevent KEYCODE_SLEEP || CLEANUP_FAILURES=$((CLEANUP_FAILURES + 1))
  fi
  if [[ "$CLEANUP_FAILURES" -ne 0 && "$benchmark_status" -eq 0 ]]; then
    exit 1
  fi
  exit "$benchmark_status"
}

# 信号只转换为稳定退出码，真正恢复统一由 EXIT trap 执行。
handle_interrupt() {
  exit 130
}

if [[ -z "$TARGET_SERIAL" ]]; then
  echo "缺少 PIXEL_BENCHMARK_SERIAL；实体性能采集不会选择默认设备。" >&2
  exit 2
fi
if [[ -z "$TARGET_REFRESH_RATE_HZ" || ! "$TARGET_REFRESH_RATE_HZ" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
  echo "PIXEL_PHYSICAL_REFRESH_RATE_HZ 必须是显式正数。" >&2
  exit 2
fi
if [[ "$MAGISK_RESTORE_TIMEOUT_SECONDS" =~ [^0-9] || "$MAGISK_RESTORE_TIMEOUT_SECONDS" -lt 60 || "$MAGISK_RESTORE_TIMEOUT_SECONDS" -gt 14400 ]]; then
  echo "PIXEL_MAGISK_RESTORE_TIMEOUT_SECONDS 必须位于 60..14400。" >&2
  exit 2
fi
if [[ "$MAGISK_POLICY_MODE" != "auto" && "$MAGISK_POLICY_MODE" != "off" ]]; then
  echo "PIXEL_MAGISK_POLICY_MODE 只允许 auto 或 off。" >&2
  exit 2
fi
if [[ "$MAGISK_POLICY_SETTLE_SECONDS" =~ [^0-9] || "$MAGISK_POLICY_SETTLE_SECONDS" -lt 1 || "$MAGISK_POLICY_SETTLE_SECONDS" -gt 10 ]]; then
  echo "PIXEL_MAGISK_POLICY_SETTLE_SECONDS 必须位于 1..10。" >&2
  exit 2
fi
if [[ "$DEVICE_READY_TIMEOUT_SECONDS" =~ [^0-9] || "$DEVICE_READY_TIMEOUT_SECONDS" -lt 1 || "$DEVICE_READY_TIMEOUT_SECONDS" -gt 60 ]]; then
  echo "PIXEL_DEVICE_READY_TIMEOUT_SECONDS 必须位于 1..60。" >&2
  exit 2
fi
if [[ "$CPU_CEILING_TIMEOUT_SECONDS" =~ [^0-9] || "$CPU_CEILING_TIMEOUT_SECONDS" -lt 1 || "$CPU_CEILING_TIMEOUT_SECONDS" -gt 900 ]]; then
  echo "PIXEL_CPU_CEILING_TIMEOUT_SECONDS 必须位于 1..900。" >&2
  exit 2
fi
if [[ "$PRIME_CPU_LOCK_MODE" != "auto" && "$PRIME_CPU_LOCK_MODE" != "off" ]]; then
  echo "PIXEL_PRIME_CPU_LOCK_MODE 只允许 auto 或 off。" >&2
  exit 2
fi
if [[ "$CPU_FREQUENCY_WATCHDOG_TIMEOUT_SECONDS" =~ [^0-9] || "$CPU_FREQUENCY_WATCHDOG_TIMEOUT_SECONDS" -lt 60 || "$CPU_FREQUENCY_WATCHDOG_TIMEOUT_SECONDS" -gt 14400 ]]; then
  echo "PIXEL_CPU_FREQUENCY_WATCHDOG_TIMEOUT_SECONDS 必须位于 60..14400。" >&2
  exit 2
fi
if [[ "$#" -eq 0 ]]; then
  echo "用法：PIXEL_BENCHMARK_SERIAL=<serial> PIXEL_PHYSICAL_REFRESH_RATE_HZ=<hz> tools/pixel-physical-benchmark-suite.sh <Gradle task> [参数...]" >&2
  exit 2
fi

# 连接身份和 qemu 属性在任何设备写操作前复核，禁止把模拟器当作实体 baseline。
TARGET_STATE="$(adb_target get-state 2>/dev/null || true)"
if [[ "$TARGET_STATE" != "device" ]]; then
  echo "目标设备不可用：${TARGET_SERIAL}（状态：${TARGET_STATE:-missing}）。" >&2
  exit 2
fi
# qemu_identity 为空或非 1 才表示实体设备。
QEMU_IDENTITY="$(adb_target shell getprop ro.kernel.qemu | tr -d '\r' | xargs)"
if [[ "$QEMU_IDENTITY" == "1" ]]; then
  echo "拒绝把模拟器 ${TARGET_SERIAL} 作为实体性能采集目标。" >&2
  exit 2
fi
trap 'cleanup_environment "$?"' EXIT
trap handle_interrupt INT TERM
# 性能模式先于频率等待启用，屏幕仍保持休眠以便 OEM 温控自然解除上限。
prepare_power_mode
# 频率门禁先于亮屏与设置修改执行，让受温控限制的设备保持休眠并自然降温。
if ! wait_for_cpu_ceiling "$CPU_CEILING_TIMEOUT_SECONDS"; then
  echo "实体设备超大核频率上限未在 ${CPU_CEILING_TIMEOUT_SECONDS} 秒内恢复；拒绝启动不可比较的性能采集。" >&2
  print_cpu_ceiling_diagnostics
  exit 1
fi
lock_prime_cpu_at_hardware_max_if_possible
start_cpu_frequency_watchdog_if_needed
prepare_device
# MIUI 可能在唤醒屏幕后重新施加性能档位；设备写入后必须再次拒绝频率竞态。
if ! cpu_runtime_is_ready; then
  echo "实体设备在唤醒与刷新率准备后重新限制超大核频率；拒绝启动不可比较的性能采集。" >&2
  print_cpu_ceiling_diagnostics
  print_prime_cpu_runtime_diagnostics
  exit 1
fi
start_magisk_watchdog_if_needed
# Magisk 缓存等待完成后再次读取设备状态，防止准备与 Gradle 启动之间重新落入锁屏。
if ! device_is_ready; then
  echo "实体设备在 benchmark 启动前不再满足清醒、常亮和 Keyguard 解除条件。" >&2
  print_device_readiness_diagnostics
  exit 1
fi
# Gradle 启动前最后复核超大核上限，避免 Magisk settle 窗口内发生 OEM 降频。
if ! cpu_runtime_is_ready; then
  echo "实体设备在 benchmark 启动前重新限制超大核频率；拒绝启动不可比较的性能采集。" >&2
  print_cpu_ceiling_diagnostics
  print_prime_cpu_runtime_diagnostics
  exit 1
fi

cd "$ROOT_DIR"
# benchmark_status 保留底层 Gradle/instrumentation 原始非零退出码。
benchmark_status=0
PIXEL_BENCHMARK_ALLOW_PHYSICAL=1 \
PIXEL_BENCHMARK_SERIAL="$TARGET_SERIAL" \
  bash "$CONNECTED_BENCHMARK_SCRIPT" "$@" || benchmark_status=$?
# watchdog_status 证明监控窗口正常关闭；失败时成功 benchmark 也不得被接纳。
watchdog_status=0
stop_cpu_frequency_watchdog || watchdog_status=$?
# cpu_frequency_violation 保存设备端第一次不变量破坏的完整键值证据。
cpu_frequency_violation="$(read_cpu_frequency_violation 2>/dev/null || true)"
if [[ -n "$cpu_frequency_violation" ]]; then
  echo "实体设备在 benchmark 期间破坏超大核频率不变量；拒收本批次：" >&2
  echo "$cpu_frequency_violation" >&2
  watchdog_status=1
fi
if [[ "$benchmark_status" -eq 0 && "$watchdog_status" -ne 0 ]]; then
  benchmark_status=1
fi
exit "$benchmark_status"
