#!/system/bin/sh
set -eu

# 本脚本只允许由 root watchdog 运行，避免普通 shell 误以为策略已经受保护。
if [ "$(id -u)" != "0" ]; then
  echo "Magisk Shell 策略 watchdog 必须以 root 运行。" >&2
  exit 2
fi

# 每次采集使用唯一前缀，防止旧 sentinel 或并发采集提前触发恢复。
RUN_PREFIX="${1:-}"
# 超时是主机消失时的最终恢复边界，单位为秒。
RESTORE_TIMEOUT_SECONDS="${2:-}"
if [ -z "$RUN_PREFIX" ] || [ -z "$RESTORE_TIMEOUT_SECONDS" ]; then
  echo "用法：pixel-magisk-shell-policy-watchdog.sh <run-prefix> <timeout-seconds>" >&2
  exit 2
fi
case "$RUN_PREFIX" in
  /data/local/tmp/pixel-magisk-benchmark-[A-Za-z0-9_-]*) ;;
  *)
    echo "拒绝不安全的 watchdog 路径：$RUN_PREFIX" >&2
    exit 2
    ;;
esac
case "$RESTORE_TIMEOUT_SECONDS" in
  *[!0-9]*|'')
    echo "watchdog 超时必须是正整数。" >&2
    exit 2
    ;;
esac
if [ "$RESTORE_TIMEOUT_SECONDS" -le 0 ]; then
  echo "watchdog 超时必须大于零。" >&2
  exit 2
fi

# ready 标记只在拒绝策略已经写入并复核后创建。
READY_MARKER="${RUN_PREFIX}.ready"
# restore 标记由主机 trap 创建，用于请求立即恢复。
RESTORE_MARKER="${RUN_PREFIX}.restore"
# done 标记证明允许策略已经恢复并再次通过数据库复核。
DONE_MARKER="${RUN_PREFIX}.done"
# 恢复幂等标记避免显式恢复和 EXIT trap 重复创建完成证据。
POLICY_RESTORED=0

# 读取 Shell 的完整 Magisk 策略行，供写入后的严格复核使用。
read_shell_policy() {
  magisk --sqlite "SELECT uid,policy,until,logging,notification FROM policies WHERE uid=2000;"
}

# 把 Shell 恢复为采集前已验证的标准允许策略，并发布完成标记。
restore_shell_policy() {
  if [ "$POLICY_RESTORED" -eq 1 ]; then
    return
  fi
  # 先置位可避免恢复命令自身异常时由 EXIT trap 无限递归。
  POLICY_RESTORED=1
  magisk --sqlite "UPDATE policies SET policy=2, until=0, logging=1, notification=1 WHERE uid=2000;"
  # 恢复后的完整行必须保持标准 allow 不变量。
  RESTORED_POLICY_ROW="$(read_shell_policy)"
  case "$RESTORED_POLICY_ROW" in
    *"uid=2000"*"policy=2"*"until=0"*"logging=1"*"notification=1"*|*"logging=1"*"notification=1"*"policy=2"*"uid=2000"*"until=0"*) ;;
    *)
      echo "无法复核 Magisk Shell allow 策略：$RESTORED_POLICY_ROW" >&2
      exit 1
      ;;
  esac
  : > "$DONE_MARKER"
  chmod 0644 "$DONE_MARKER"
}

# 无论正常结束、信号还是中间命令失败，都必须先恢复 Shell allow 策略。
trap restore_shell_policy EXIT HUP INT TERM
rm -f "$READY_MARKER" "$RESTORE_MARKER" "$DONE_MARKER"

# AndroidX Benchmark 初始化前必须暂时拒绝 Shell root，避免 `su root id` 进入交互 shell。
magisk --sqlite "UPDATE policies SET policy=1, until=0, logging=1, notification=1 WHERE uid=2000;"
# 拒绝后的完整行必须保持标准 deny 不变量，不能只依赖 UPDATE 的零退出码。
DENIED_POLICY_ROW="$(read_shell_policy)"
case "$DENIED_POLICY_ROW" in
  *"uid=2000"*"policy=1"*"until=0"*"logging=1"*"notification=1"*|*"logging=1"*"notification=1"*"policy=1"*"uid=2000"*"until=0"*) ;;
  *)
    echo "无法复核 Magisk Shell deny 策略：$DENIED_POLICY_ROW" >&2
    exit 1
    ;;
esac
: > "$READY_MARKER"
chmod 0644 "$READY_MARKER"

# 已等待秒数用于保证主机失联时仍能在固定上限内恢复授权。
ELAPSED_SECONDS=0
while [ ! -e "$RESTORE_MARKER" ] && [ "$ELAPSED_SECONDS" -lt "$RESTORE_TIMEOUT_SECONDS" ]; do
  sleep 1
  ELAPSED_SECONDS=$((ELAPSED_SECONDS + 1))
done

restore_shell_policy
trap - EXIT HUP INT TERM
