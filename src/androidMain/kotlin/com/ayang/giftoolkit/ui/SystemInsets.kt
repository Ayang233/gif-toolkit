package com.ayang.giftoolkit.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

/**
 * 横向系统安全区（挖孔、侧边系统栏），与页面 Scaffold 的 `contentWindowInsets` 同一套来源：
 * `systemBars ∪ displayCutout`。
 *
 * 横屏时挖孔落在起始边（本机 128px = 64dp）。Scaffold 的 `innerPadding` 已经带了这段，
 * 但**不在 Scaffold 内容槽位里**的控件（底部主操作条、页面根部自绘列表）拿不到 `innerPadding`，
 * 只能自己取一次。抽出来是为了让全项目对"横向安全区"只有一个定义。
 *
 * 屏幕圆角不要在这里再算一遍 —— 直接用库的 `rememberNavSystemCornerRadius()`
 * （`top.yukonga.miuix.kmp.nav.core`，官方 example `AppContent.kt:267` 同款）。
 */
@Composable
fun rememberHorizontalSafeInsets(): PaddingValues =
    WindowInsets.systemBars
        .union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Horizontal)
        .asPaddingValues()

/**
 * 底部系统安全区（导航栏 / captionBar 的较大值）。
 *
 * 官方对"贴着屏幕底的控件该留多少"只有一条公式（`GlassPage.kt:301`、`AppContent.kt`）：
 * `max(24.dp, 安全区 + 8.dp)`。上一版这里用屏幕圆角解方程算侵入量（`safeBottomGap`），
 * 属于自己造了一套几何，已删除，统一回到官方这一条。
 */
@Composable
fun rememberSystemBottomInset(): Dp = maxOf(
    WindowInsets.navigationBars
        .only(WindowInsetsSides.Bottom)
        .asPaddingValues()
        .calculateBottomPadding(),
    WindowInsets.captionBar
        .only(WindowInsetsSides.Bottom)
        .asPaddingValues()
        .calculateBottomPadding(),
)
