package com.ayang.giftoolkit.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

/**
 * 是否进入「宽屏」布局：用侧边导航栏替代底部导航栏。
 *
 * 阈值照抄官方 `utils/AdaptiveUtils.kt`：
 * - 宽度 ≥ 840dp 一定算宽屏；
 * - 600~840dp 之间，只有横向（高宽比 < 1.2）才算 —— 竖着拿的折叠屏展开态仍用底栏。
 *
 * 抄同一组数字，是为了让 GIF 助手和 Miuix 生态里其它应用在同一台设备上**同一时刻**切换布局，
 * 不然并排放着看会觉得两块屏幕切换时机不一致。
 */
@Composable
fun rememberWideLayout(): Boolean {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    return with(density) {
        val width = windowInfo.containerSize.width.toDp()
        val height = windowInfo.containerSize.height.toDp()
        width >= 840.dp || (width >= 600.dp && height / width < 1.2f)
    }
}

/** 侧边栏是否默认展开成「图标 + 文字」。阈值同官方 `shouldExpandNavigationRail`。 */
@Composable
fun rememberExpandNavigationRail(): Boolean {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    return with(density) { windowInfo.containerSize.width.toDp() >= 1200.dp }
}

/** 宽屏标记，页面骨架据此决定顶栏是否还允许大标题展开。 */
val LocalWideLayout = compositionLocalOf { false }
