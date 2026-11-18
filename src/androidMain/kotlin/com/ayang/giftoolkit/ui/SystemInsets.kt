package com.ayang.giftoolkit.ui

import android.os.Build
import android.view.RoundedCorner
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt

/**
 * 横向系统安全区（挖孔、侧边系统栏），与页面 Scaffold 的 `contentWindowInsets` 同一套来源：
 * `systemBars ∪ displayCutout`。
 *
 * 横屏时挖孔落在起始边（本机 128px = 64dp）。Scaffold 的 `innerPadding` 已经带了这段，
 * 但**不在 Scaffold 内容槽位里**的控件（底部主操作条、页面根部自绘列表）拿不到 `innerPadding`，
 * 只能自己取一次。抽出来是为了让全项目对"横向安全区"只有一个定义。
 */
@Composable
fun rememberHorizontalSafeInsets(): PaddingValues =
    WindowInsets.systemBars
        .union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Horizontal)
        .asPaddingValues()

/**
 * 设备屏幕底部圆角半径。
 *
 * 优先用公开的 [RoundedCorner] 窗口内嵌 API（API 31+），否则退回 framework 的
 * `rounded_corner_radius_bottom` dimen，最后给 0（直屏）。不要写死数值：
 * 同一套布局在直屏机和大圆角机型上的安全边距差一倍以上。
 */
@Composable
fun rememberDeviceCornerRadius(): Dp {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    return remember(context, view, density) {
        val fromInsets = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.rootWindowInsets
                ?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)
                ?.radius
                ?.takeIf { it > 0 }
        } else {
            null
        }
        val px = fromInsets ?: run {
            val id = context.resources.getIdentifier("rounded_corner_radius_bottom", "dimen", "android")
            if (id > 0) context.resources.getDimensionPixelSize(id) else 0
        }
        with(density) { px.toDp() }
    }
}

/**
 * 通栏按钮贴底时需要的底部留白。
 *
 * 屏幕圆角是一段半径 R 的圆弧，距屏幕底部 d 处的水平侵入量是
 * `R - sqrt(2Rd - d²)`。按钮左右各留 [sideGap]，只要侵入量不超过它就切不到按钮，
 * 于是解出最小 d：
 *
 *     d = R - sqrt(2R·sideGap - sideGap²)
 *
 * 再减掉系统导航栏已经占掉的高度，就是这一层自己还要补的留白。
 * 这比"给个固定 12dp 再祈祷"可靠：R=80dp 的机型需要 32dp 的 d，而直屏机 12dp 就够。
 */
@Composable
fun safeBottomGap(
    corner: Dp,
    sideGap: Dp,
    minimum: Dp = 12.dp,
): Dp {
    val navBottom = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    if (corner <= sideGap) return minimum
    val r = corner.value
    val g = sideGap.value
    val discriminant = 2f * r * g - g * g
    if (discriminant <= 0f) return minimum
    val needed = r - sqrt(discriminant)
    return (needed - navBottom.value).coerceAtLeast(minimum.value).dp
}
