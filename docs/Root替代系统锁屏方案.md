# Root 替代系统锁屏方案（独立评估）

日期：2026-06-20

状态：归档评估，暂不实施。

当前产品决策：root 替代系统锁屏不要在 PixelLauncher / Launcher 主 App 中实现，暂不纳入 Launcher 迭代路线。本文只记录“设备已有 root 权限时，技术上如何替代系统原生锁屏页面”的方案，供未来独立项目、独立模块或系统级实验参考。

明确不做：

- 不在 Launcher 主 App 中新增 `PixelLockActivity`。
- 不在 Launcher 主 App 中新增 `PixelLockService`。
- 不在 Launcher 主 App 中新增 `RootKeyguardController`。
- 不在 Launcher Settings 中增加 `ROOT REPLACE` 开关。
- 不让 Idle 页面承担系统锁屏替代职责。

## 1. 结论

技术上可行，但当前不在 Launcher 中实现。实现方式分层：

1. **视觉覆盖**：独立锁屏模块显示一个锁屏/Idle 风格页面，盖在系统锁屏上方。
2. **Root 替代**：通过 root、Magisk、LSPosed 或 priv-app 能力抑制系统 Keyguard，让用户只看到独立锁屏模块。
3. **SystemUI 深度替换**：修改或替换 SystemUI 的 Keyguard 实现，最彻底但最依赖 ROM。

当前不执行实现路线。如果未来单独立项，可参考下面路线：

```text
独立锁屏 Activity + SCREEN_ON 拉起
-> root/LSPosed hook SystemUI Keyguard
-> 独立实验设置里做 ROOT REPLACE 开关
-> 后续再考虑 SystemUI 深度替换
```

如果未来恢复该方向，也不要第一版直接替换 SystemUI。应先用独立项目把锁屏页面、亮屏拉起、熄屏状态、异常回退跑稳定，再讨论 root hook。

## 2. 目标定义

如果未来单独立项，目标可以是：

- 屏幕亮起时，不显示系统原生锁屏。
- 显示独立实验模块自己的像素锁屏页面。
- 锁屏页面可以参考 PixelLauncher 的 Idle 视觉系统，但不直接放进 Launcher 主 App。
- 用户通过点击、滑动或指定手势进入 Home。
- 可选 root 模式负责压制 SystemUI Keyguard。

当前非目标：

- 不在 Launcher 中实现。
- 不实现 PIN / password / pattern。
- 不实现 biometric 认证。
- 不接管 Android FBE 解密流程。
- 不做通用 ROM 级安全锁屏。
- 不承诺跨所有 Android 版本和 ROM 稳定。

## 3. Android 锁屏相关事实

普通 App 能做：

- 使用 `Activity.setShowWhenLocked(true)` 显示在锁屏上。
- 使用 `Activity.setTurnScreenOn(true)` 点亮屏幕后显示 Activity。
- 使用 `KeyguardManager.requestDismissKeyguard(...)` 请求系统解除 Keyguard。

普通 App 不能可靠做：

- 完整禁用系统 Keyguard。
- 替代 SystemUI 内部 Keyguard 生命周期。
- 接管系统安全认证链路。

root 后可以进一步做，但不应放在 Launcher 主 App 里：

- 让独立实验模块以系统/priv-app 方式安装。
- 通过 Magisk module 注入权限或覆盖系统文件。
- 通过 LSPosed hook SystemUI 的 Keyguard 显示逻辑。
- 替换或修改 SystemUI APK。

参考：

- Android `KeyguardManager`：https://developer.android.com/reference/android/app/KeyguardManager
- Android `Activity.setShowWhenLocked`：https://developer.android.com/reference/android/app/Activity#setShowWhenLocked(boolean)
- AOSP `KeyguardViewMediator`：https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/SystemUI/src/com/android/systemui/keyguard/KeyguardViewMediator.java
- Android privileged permission allowlist：https://source.android.com/docs/core/permissions/perms-allowlist

## 4. 独立项目参考架构

以下架构只适用于未来独立锁屏项目或系统级实验，不是 PixelLauncher 当前实现方案。

```text
PixelLockService
  - 监听 BOOT / SCREEN_ON / SCREEN_OFF / USER_PRESENT
  - 管理 locked/unlocked 状态
  - 负责拉起 PixelLockActivity

PixelLockActivity
  - pixel-engine 渲染锁屏 UI
  - 显示时间、电量、天气、通知摘要、充电态
  - 点击/上滑/按键进入 Home

RootKeyguardController
  - 检测 root
  - 检测 hook 是否生效
  - 控制 root 替代模式
  - 失败时回退普通覆盖模式

独立实验 Settings
  - LOCKSCREEN MODE: OFF / OVERLAY / ROOT REPLACE
  - ROOT STATUS
  - HOOK STATUS
  - FAILSAFE
```

## 5. Phase 1：PixelLock 页面

未来如果单独立项，先实现独立锁屏页面，不碰系统 Keyguard。

推荐独立 `PixelLockActivity`，原因：

- 生命周期更接近锁屏。
- 可独立设置 `showWhenLocked` / `turnScreenOn`。
- 和 Launcher 页面状态解耦。
- 崩溃或退出时更容易回到 Home。

Activity 行为：

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setShowWhenLocked(true)
    setTurnScreenOn(true)
    windowModeController.hideSystemBars()
    // setContentView(pixel-engine lock host)
}
```

锁屏 UI 内容：

- 大时间。
- 日期。
- 电量。
- 充电状态。
- 天气/降雨风险。
- 未接来电/未读短信摘要。
- 可选低亮度夜间模式。

交互：

- 点击或上滑进入 Home。
- 左右滑可切换少量状态页，但不建议第一版做。
- 长按进入紧急回退或设置入口。

## 6. Phase 2：亮屏自动拉起

在独立实验 App 中新增 `PixelLockService` 或同等 receiver。

监听：

- `Intent.ACTION_SCREEN_ON`
- `Intent.ACTION_SCREEN_OFF`
- `Intent.ACTION_USER_PRESENT`
- `Intent.ACTION_BOOT_COMPLETED`

状态：

```text
SCREEN_OFF -> locked = true
SCREEN_ON  -> if locked then start PixelLockActivity
USER_PRESENT -> locked = false
```

启动方式：

```kotlin
val intent = Intent(context, PixelLockActivity::class.java).apply {
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
}
context.startActivity(intent)
```

注意：

- Android 后台启动 Activity 有限制。root 模式下可以通过 shell / appops / priv-app 缓解。
- 普通模式下如果系统限制后台启动，需要前台 service、通知或 root/priv-app 能力配合。
- 即使进程常驻，也仍要处理进程被杀。

## 7. Phase 3：普通覆盖模式

此模式不禁用系统 Keyguard，只让 PixelLock 显示在锁屏上。

优点：

- 最稳定。
- 适合调试 PixelLock 生命周期。
- 不依赖 LSPosed 或 ROM 细节。

缺点：

- 系统 Keyguard 仍可能短暂出现。
- 某些 ROM 会先显示系统锁屏，再显示 Activity。
- 用户仍可能看到系统解锁界面。

实现点：

- `setShowWhenLocked(true)`
- `setTurnScreenOn(true)`
- 全屏隐藏系统栏。
- 在 `onResume` 反复确认 UI 已覆盖。
- 熄屏/亮屏状态用 `PixelLockService` 管理。

## 8. Phase 4：Root 替代模式

Root 替代模式目标是不让系统原生锁屏显示。

### 8.1 推荐方案：LSPosed / Magisk hook SystemUI

思路：

```text
SystemUI 准备 show keyguard
-> hook 拦截
-> 阻止原生 keyguard 显示
-> 启动 PixelLockActivity
```

常见 hook 对象：

- `com.android.systemui.keyguard.KeyguardViewMediator`
- `StatusBarKeyguardViewManager`
- `KeyguardBouncer`
- `CentralSurfaces`
- ROM 自定义 keyguard manager 类

常见 hook 方法方向：

- `showLocked(...)`
- `doKeyguardLocked(...)`
- `setKeyguardEnabled(...)`
- `showBouncer(...)`
- `show(...)`
- `reset(...)`

伪代码：

```kotlin
findAndHookMethod(
    "com.android.systemui.keyguard.KeyguardViewMediator",
    classLoader,
    "doKeyguardLocked",
    Bundle::class.java,
    object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            if (PixelLockConfig.rootReplaceEnabled()) {
                PixelLockBridge.startPixelLock()
                param.result = null
            }
        }
    }
)
```

实际项目中不能只依赖一个方法，需要按 ROM 建立 hook profile：

- AOSP / Pixel-like
- MIUI / HyperOS
- ColorOS
- OneUI
- LineageOS

第一版可以只支持当前测试机 ROM。

### 8.2 替代触发方式：root shell 拉起

如果后台启动 Activity 被系统限制，可由 root shell 拉起：

```bash
su -c 'am start -n com.example.pixellock/.PixelLockActivity'
```

或独立实验包：

```bash
su -c 'am start -n com.example.pixellock.debug/.PixelLockActivity'
```

可以由 hook 进程或 root daemon 调用。

### 8.3 priv-app 方案

通过 Magisk module 将独立锁屏模块作为 priv-app 挂载：

```text
system_ext/priv-app/PixelLock/PixelLock.apk
system_ext/etc/permissions/privapp-permissions-pixellock.xml
```

用途：

- 获取更高权限。
- 更容易在后台启动。
- 尝试持有 privileged permissions。

限制：

- Android 对 priv-app 权限有 allowlist 限制。
- 权限 XML 必须和 APK 位于同一分区。
- 即使是 priv-app，也不一定能绕过 SystemUI 的 Keyguard 内部逻辑。

因此 priv-app 更适合作为辅助，不是主替代方案。

### 8.4 SystemUI 替换方案

最彻底方式是修改目标 ROM 的 SystemUI：

- 禁用原生 Keyguard view。
- 让 SystemUI 直接启动 PixelLockActivity。
- 或把 PixelLock 的渲染嵌入 SystemUI。

缺点：

- 强绑定 ROM 和 Android 版本。
- OTA 后容易失效。
- 调试失败可能导致 SystemUI 崩溃循环。

建议只作为长期实验，不作为第一版。

## 9. RootKeyguardController 设计

职责：

- 检测 root。
- 检测 LSPosed hook 状态。
- 读取用户设置。
- 切换锁屏模式。
- 暴露状态给独立实验设置 / Diagnostics。

状态模型：

```kotlin
enum class LockReplacementMode {
    OFF,
    OVERLAY,
    ROOT_REPLACE,
}

data class RootLockState(
    val rootAvailable: Boolean,
    val hookInstalled: Boolean,
    val hookActive: Boolean,
    val mode: LockReplacementMode,
    val lastError: String = "",
)
```

独立实验设置展示：

```text
LOCK MODE      <OFF / OVERLAY / ROOT>
ROOT           READY / MISSING
HOOK           ACTIVE / MISSING
FAILSAFE       ON
```

Diagnostics 展示：

- 最近一次 screen event。
- 最近一次 PixelLock 启动时间。
- SystemUI hook 状态。
- root shell 可用性。
- fallback 次数。

## 10. Failsafe

即使不考虑安全性，也必须考虑设备可用性。root 锁屏替代最容易造成黑屏或循环启动。

建议至少做：

1. 连续崩溃回退
   - PixelLock 连续崩溃 3 次，自动关闭 ROOT_REPLACE。

2. 音量键回退
   - 开机或亮屏后 10 秒内长按音量上/下，禁用 root 锁屏替代。

3. ADB 回退命令
   - 提供明确命令：

```bash
adb shell su -c 'settings put global pixel_lock_mode off'
```

或：

```bash
adb shell su -c 'pm disable com.example.pixellock.debug/.PixelLockService'
```

4. Magisk module 可禁用
   - 模块名清晰。
   - 文档写明如何从 recovery 或 adb 移除。

5. 超时回退
   - PixelLock 启动失败后允许系统 Keyguard 恢复。

## 11. 与 Launcher 的关系

当前决策是不集成到 PixelLauncher。也就是说：

- 不在 `app/src/main/.../pixellauncherv2` 下新增锁屏 Activity、Service 或 root controller。
- 不扩展 `SettingsMenuModel` 增加 lock/root 分组。
- 不扩展 `DiagnosticsScreen` 承载 root lock 状态。
- 不把 lock mode 持久化进 Launcher 设置。
- 不修改 Launcher `AndroidManifest.xml` 增加锁屏替代入口。

如果未来恢复该方向，建议另开独立包名和独立模块，例如：

```text
com.example.pixellock
  PixelLockActivity
  PixelLockService
  RootKeyguardController
  LockReplacementMode
  PixelLockScreen
```

可参考但不直接耦合的 Launcher 能力：

- Idle 的视觉方向。
- 像素字体与主题 token。
- 设备状态、通信状态、天气、闹钟等数据源思路。

独立实验模块的 Manifest 方向：

```xml
<activity
    android:name=".PixelLockActivity"
    android:excludeFromRecents="true"
    android:showWhenLocked="true"
    android:turnScreenOn="true" />

<service
    android:name=".PixelLockService"
    android:exported="false" />
```

## 12. 未来独立项目参考里程碑

以下里程碑暂不执行，只作为未来独立项目恢复时的拆分参考。

### M1：普通 PixelLock 页面

- 新建 PixelLockActivity。
- 复用 pixel-engine 渲染。
- 支持 showWhenLocked / turnScreenOn。
- 点击进入 Home。
- 不做 root。

验收：

- 锁屏状态下能显示 PixelLock。
- 熄屏再亮屏能拉起 PixelLock。
- 返回 Home 不闪退。

### M2：PixelLockService

- 监听 screen on/off/user present。
- 管理 locked 状态。
- 后台拉起 PixelLock。
- 加入独立实验设置开关：OFF / OVERLAY。

验收：

- 熄屏后再亮屏自动出现 PixelLock。
- 关闭开关后恢复系统默认行为。

### M3：Root 检测与 root shell 拉起

- 检测 `su`。
- root shell 执行 `am start`。
- 独立实验设置显示 root 状态。
- Diagnostics 显示最近 root 命令结果。

验收：

- 普通后台启动失败时，root shell 仍能拉起 PixelLock。

### M4：LSPosed hook 原生 Keyguard

- 针对当前测试机 ROM 建立 hook profile。
- 拦截 SystemUI Keyguard show。
- 启动 PixelLock。
- 加入 hook 状态回传。

验收：

- 亮屏不显示原生系统锁屏。
- 直接显示 PixelLock。
- 关闭 ROOT_REPLACE 后系统锁屏恢复。

### M5：failsafe 与稳定性

- 连续崩溃回退。
- 音量键回退。
- ADB 回退命令。
- 设置页风险提示。

验收：

- PixelLock 崩溃不会让设备不可用。
- 可以从 adb 或手势恢复普通模式。

## 13. 风险清单

技术风险：

- ROM 差异导致 hook 点失效。
- Android 版本升级后 SystemUI 类名或方法变化。
- 后台启动 Activity 被系统限制。
- SystemUI 崩溃循环。
- PixelLock 崩溃后黑屏。
- root 权限管理器弹窗影响锁屏流程。

产品风险：

- 用户误以为这是安全锁屏。
- 通知、来电、闹钟等系统级场景被覆盖。
- 紧急电话入口缺失。

工程风险：

- Debug 包和 release 包 packageName 不同，root hook 需要区分。
- Magisk module 与 app version 不匹配。
- 多 ROM 适配成本持续增加。

## 14. 当前决策

当前不执行，不在 Launcher 中实现。

如果未来另起独立项目，才按以下顺序评估：

1. 先做独立 `PixelLockActivity`。
2. 再做独立 `PixelLockService` 亮屏拉起。
3. 再做 root shell 拉起。
4. 最后做 LSPosed hook 当前测试 ROM 的 Keyguard。

不建议第一步就替换 SystemUI。

未来独立项目的可能产品形态：

```text
LOCK MODE: OFF
LOCK MODE: OVERLAY
LOCK MODE: ROOT REPLACE
```

其中 `ROOT REPLACE` 明确是实验模式，只保证当前测试设备优先可用，不进入 PixelLauncher 当前路线。
