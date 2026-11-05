package com.cx330.toolbox.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * 极速交互反馈：
 * - 震动直接在「手指按下」的触摸事件里触发（pointerInput），不等 Compose 重组；
 * - 缩放是瞬时切换（不做 60ms 动画），避免“页面都跳了动画才播”；
 * - 动画交给系统水波纹，按下即可见。
 */
@Composable
fun Modifier.instantHaptic(): Modifier {
    val haptic = LocalHapticFeedback.current
    return this.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
}

@Composable
fun Modifier.hapticPress(interactionSource: MutableInteractionSource): Modifier =
    this.instantHaptic()

@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.95f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    // 不做补间：按下立刻变小、松手立刻恢复，避免任何“慢了半拍”。
    return this
        .scale(if (pressed) pressedScale else 1f)
        .instantHaptic()
}
