#!/system/bin/sh
set -u

# run_prefix 为本轮唯一设备临时文件前缀，避免并发或历史运行相互污染。
run_prefix="${1:-}"
# cpu_index 是 AndroidX Microbenchmark 实际绑定的超大核编号。
cpu_index="${2:-}"
# expected_frequency_khz 是本轮要求全程保持的硬件最高频率。
expected_frequency_khz="${3:-}"
# timeout_seconds 限制看门狗最长运行时间，超时本身视为监控证据不完整。
timeout_seconds="${4:-}"

# ready_marker 证明看门狗已完成参数校验并开始采样。
ready_marker="${run_prefix}.ready"
# stop_marker 由主机在 benchmark 结束或清理时创建。
stop_marker="${run_prefix}.stop"
# done_marker 证明看门狗已停止且最后一次采样结果已经落盘。
done_marker="${run_prefix}.done"
# violation_marker 保存第一次频率不变量破坏的可读证据。
violation_marker="${run_prefix}.violation"
# cpu_root 是本轮唯一读取的 cpufreq sysfs 目录。
cpu_root="/sys/devices/system/cpu/cpu${cpu_index}/cpufreq"

# 把参数或运行时错误写成固定键值格式，便于主机日志和归档工具直接保留。
write_violation() {
  # violation_reason 描述本轮拒收的稳定机器原因。
  violation_reason="$1"
  # observed_governor 是拒收时读取到的 governor。
  observed_governor="${2:-unreadable}"
  # observed_current_khz 是拒收时的实际运行频率。
  observed_current_khz="${3:-unreadable}"
  # observed_setspeed_khz 是 userspace governor 的目标频率。
  observed_setspeed_khz="${4:-unreadable}"
  # observed_scaling_max_khz 是温控或功耗策略施加后的频率上限。
  observed_scaling_max_khz="${5:-unreadable}"
  {
    printf 'reason=%s\n' "$violation_reason"
    printf 'observedAtUtc=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date)"
    printf 'cpuIndex=%s\n' "$cpu_index"
    printf 'expectedFrequencyKHz=%s\n' "$expected_frequency_khz"
    printf 'governor=%s\n' "$observed_governor"
    printf 'scalingCurFreqKHz=%s\n' "$observed_current_khz"
    printf 'scalingSetSpeedKHz=%s\n' "$observed_setspeed_khz"
    printf 'scalingMaxFreqKHz=%s\n' "$observed_scaling_max_khz"
  } > "$violation_marker"
  chmod 0644 "$violation_marker" 2>/dev/null || true
}

# publish_done 在所有退出路径发布只读完成标记。
publish_done() {
  : > "$done_marker"
  chmod 0644 "$done_marker" 2>/dev/null || true
}

# 四个参数都来自受控主机 wrapper；无效参数必须生成可诊断失败而不是静默退出。
case "$cpu_index:$expected_frequency_khz:$timeout_seconds" in
  *[!0-9:]*|*:0)
    write_violation "invalid-watchdog-arguments"
    publish_done
    exit 0
    ;;
esac
if [ -z "$run_prefix" ] ||
   [ -z "$cpu_index" ] ||
   [ -z "$expected_frequency_khz" ] ||
   [ -z "$timeout_seconds" ] ||
   [ ! -d "$cpu_root" ]; then
  write_violation "missing-watchdog-runtime"
  publish_done
  exit 0
fi

rm -f "$ready_marker" "$stop_marker" "$done_marker" "$violation_marker"
: > "$ready_marker"
chmod 0644 "$ready_marker" 2>/dev/null || true

# elapsed_seconds 记录已经完成的全秒采样周期。
elapsed_seconds=0
while [ ! -e "$stop_marker" ]; do
  # observed_governor 必须在整个 benchmark 期间保持 userspace。
  observed_governor="$(cat "$cpu_root/scaling_governor" 2>/dev/null || printf unreadable)"
  # observed_current_khz 是每秒读取的真实运行频率。
  observed_current_khz="$(cat "$cpu_root/scaling_cur_freq" 2>/dev/null || printf unreadable)"
  # observed_setspeed_khz 证明 OEM 没有改写固定频率目标。
  observed_setspeed_khz="$(cat "$cpu_root/scaling_setspeed" 2>/dev/null || printf unreadable)"
  # observed_scaling_max_khz 证明温控没有在采集中压低策略上限。
  observed_scaling_max_khz="$(cat "$cpu_root/scaling_max_freq" 2>/dev/null || printf unreadable)"
  if [ "$observed_governor" != "userspace" ] ||
     [ "$observed_current_khz" != "$expected_frequency_khz" ] ||
     [ "$observed_setspeed_khz" != "$expected_frequency_khz" ] ||
     [ "$observed_scaling_max_khz" != "$expected_frequency_khz" ]; then
    write_violation \
      "prime-cpu-frequency-invariant-broken" \
      "$observed_governor" \
      "$observed_current_khz" \
      "$observed_setspeed_khz" \
      "$observed_scaling_max_khz"
    publish_done
    exit 0
  fi
  sleep 1
  elapsed_seconds=$((elapsed_seconds + 1))
  if [ "$elapsed_seconds" -ge "$timeout_seconds" ]; then
    write_violation \
      "watchdog-timeout" \
      "$observed_governor" \
      "$observed_current_khz" \
      "$observed_setspeed_khz" \
      "$observed_scaling_max_khz"
    publish_done
    exit 0
  fi
done

publish_done
