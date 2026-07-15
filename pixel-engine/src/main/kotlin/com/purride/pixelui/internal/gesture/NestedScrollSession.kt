package com.purride.pixelui.internal

/**
 * PixelHostView 内部的一次触摸序列状态。
 *
 * 该类型不暴露给页面层，只把 pager/list/text input 的候选目标与当前 owner
 * 显式收敛到一个对象里，避免宿主继续散落维护多组 active/candidate 字段。
 */
public class NestedScrollSession {
    /** 记录 `NestedScrollSession` 的 `owner` 配置或运行值，读取与更新均遵守所属类型约束；写入后由所属对象在下一次状态同步时生效。 */
    public var owner: Owner = Owner.NONE
        private set

    /** 表示 `NestedScrollSession` 当前是否满足 `candidatePagerTarget` 对应条件；写入后由所属对象在下一次状态同步时生效。 */
    public var candidatePagerTarget: PixelPagerTarget? = null
    /** 表示 `NestedScrollSession` 当前是否满足 `candidateListTarget` 对应条件；写入后由所属对象在下一次状态同步时生效。 */
    public var candidateListTarget: PixelListTarget? = null
    /** 表示 `NestedScrollSession` 当前是否满足 `candidateTextInputTarget` 对应条件；写入后由所属对象在下一次状态同步时生效。 */
    public var candidateTextInputTarget: PixelTextInputTarget? = null
    /** 保存 `NestedScrollSession` 对外传递的 `focusedTextInputTarget` 数据；写入后由所属对象在下一次状态同步时生效。 */
    public var focusedTextInputTarget: PixelTextInputTarget? = null
    /** 记录 `NestedScrollSession` 的 `consumedDeltaPx` 配置或运行值，读取与更新均遵守所属类型约束；写入后由所属对象在下一次状态同步时生效。 */
    public var consumedDeltaPx: Float = 0f
    /** 记录 `NestedScrollSession` 的 `remainingDeltaPx` 配置或运行值，读取与更新均遵守所属类型约束；写入后由所属对象在下一次状态同步时生效。 */
    public var remainingDeltaPx: Float = 0f
    /** 表示 `NestedScrollSession` 当前是否满足 `edgeHandoff` 对应条件；写入后由所属对象在下一次状态同步时生效。 */
    public var edgeHandoff: Boolean = false

    /** 提供 `NestedScrollSession` 当前管理的 `activePagerTarget` 内容；写入后由所属对象在下一次状态同步时生效。 */
    public var activePagerTarget: PixelPagerTarget? = null
        set(value) {
            field = value
            if (value != null) {
                activeListTarget = null
                owner = Owner.PAGER
            } else if (activeListTarget == null && owner == Owner.PAGER) {
                owner = Owner.NONE
            }
        }

    /** 记录 `NestedScrollSession` 的 `activeListTarget` 配置或运行值，读取与更新均遵守所属类型约束；写入后由所属对象在下一次状态同步时生效。 */
    public var activeListTarget: PixelListTarget? = null
        set(value) {
            field = value
            if (value != null) {
                activePagerTarget = null
                owner = Owner.LIST
            } else if (activePagerTarget == null && owner == Owner.LIST) {
                owner = Owner.NONE
            }
        }

    /** 向 `NestedScrollSession` 提交 `markTextInputOwner` 数据或事件，并按所属类型的顺序与所有权规则保存。 */
    public fun markTextInputOwner(target: PixelTextInputTarget) {
        focusedTextInputTarget = target
        owner = Owner.TEXT_INPUT
    }

    /** 从 `NestedScrollSession` 释放 `clearCandidates` 对应内容；重复调用按既有幂等约束处理。 */
    public fun clearCandidates() {
        candidatePagerTarget = null
        candidateListTarget = null
        candidateTextInputTarget = null
        remainingDeltaPx = 0f
    }

    /** 把 `NestedScrollSession` 的 `resetGesture` 运行状态恢复到可再次使用的初始边界。 */
    public fun resetGesture() {
        candidatePagerTarget = null
        candidateListTarget = null
        candidateTextInputTarget = null
        activePagerTarget = null
        activeListTarget = null
        consumedDeltaPx = 0f
        remainingDeltaPx = 0f
        edgeHandoff = false
        if (owner != Owner.TEXT_INPUT) {
            owner = Owner.NONE
        }
    }

    /** 从 `NestedScrollSession` 释放 `clearTextInputOwner` 对应内容；重复调用按既有幂等约束处理。 */
    public fun clearTextInputOwner() {
        focusedTextInputTarget = null
        if (activePagerTarget == null && activeListTarget == null) {
            owner = Owner.NONE
        }
    }

    /** 定义 `Owner` 在 `NestedScrollSession` 中承担的数据或执行职责，并保持公开不变量稳定。 */
    public enum class Owner {
        NONE,
        PAGER,
        LIST,
        TEXT_INPUT,
    }
}
