package com.purride.pixelui.internal

/**
 * PixelHostView 内部的一次触摸序列状态。
 *
 * 该类型不暴露给页面层，只把 pager/list/text input 的候选目标与当前 owner
 * 显式收敛到一个对象里，避免宿主继续散落维护多组 active/candidate 字段。
 */
internal class NestedScrollSession {
    var owner: Owner = Owner.NONE
        private set

    var candidatePagerTarget: PixelPagerTarget? = null
    var candidateListTarget: PixelListTarget? = null
    var candidateTextInputTarget: PixelTextInputTarget? = null
    var focusedTextInputTarget: PixelTextInputTarget? = null
    var consumedDeltaPx: Float = 0f
    var remainingDeltaPx: Float = 0f
    var edgeHandoff: Boolean = false

    var activePagerTarget: PixelPagerTarget? = null
        set(value) {
            field = value
            if (value != null) {
                activeListTarget = null
                owner = Owner.PAGER
            } else if (activeListTarget == null && owner == Owner.PAGER) {
                owner = Owner.NONE
            }
        }

    var activeListTarget: PixelListTarget? = null
        set(value) {
            field = value
            if (value != null) {
                activePagerTarget = null
                owner = Owner.LIST
            } else if (activePagerTarget == null && owner == Owner.LIST) {
                owner = Owner.NONE
            }
        }

    fun markTextInputOwner(target: PixelTextInputTarget) {
        focusedTextInputTarget = target
        owner = Owner.TEXT_INPUT
    }

    fun clearCandidates() {
        candidatePagerTarget = null
        candidateListTarget = null
        candidateTextInputTarget = null
        remainingDeltaPx = 0f
    }

    fun resetGesture() {
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

    fun clearTextInputOwner() {
        focusedTextInputTarget = null
        if (activePagerTarget == null && activeListTarget == null) {
            owner = Owner.NONE
        }
    }

    enum class Owner {
        NONE,
        PAGER,
        LIST,
        TEXT_INPUT,
    }
}
