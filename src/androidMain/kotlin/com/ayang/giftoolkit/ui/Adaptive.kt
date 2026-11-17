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

/** 宽屏标记，页面骨架据此决定顶栏是否还允许大标题展开。 */
val LocalWideLayout = compositionLocalOf { false }

/**
 * 宽屏下页面要为玻璃侧栏让出的水平宽度（dp）。
 *
 * 由 `App.kt` 单点计算、单点下发：`(ExpandedWidth + Margin) * railState.progress`。
 * 折叠态（手机上横屏）progress 为 0，页面不需要让位；展开态（≥1200dp）才让出整条面板。
 * 官方 `AppContent.kt:219-229` 用的是同一个式子：让位跟着侧栏的形变进度走，而不是"有侧栏就让 250dp"。
 *
 * **录制层不能跟着这个值收窄** —— 那会让侧栏左半段没有像素可采，玻璃只剩一块平色。
 */
val LocalRailInset = compositionLocalOf { 0.dp }

/**
 * 宽屏折叠态下顶栏标题的透明度（官方 `LocalTopAppBarTitleAlpha` 同款）。
 *
 * 折叠式 Rail 是**顶部居中的胶囊**，和居中标题抢同一个位置。官方
 * （`utils/PageUtils.kt:249` + `AppContent.kt:208-214`）的做法是让标题透明度跟着折叠进度走：
 * `{ sidebarState.progress }` —— 折叠态 progress = 0，标题整体不可见；
 * 展开成左侧面板后 progress = 1，标题回来。这样胶囊和标题不会互相盖住。
 *
 * 下发的是**函数**而不是 Float：绘制期读取，折叠动画过程里不触发重组。
 */
val LocalTopBarTitleAlpha = androidx.compose.runtime.staticCompositionLocalOf<() -> Float> { { 1f } }

/**
 * 侧栏是否展开，阈值与官方 `utils/AdaptiveUtils.kt` 的 `shouldExpandNavigationRail()` 一致：
 * **宽度 ≥ 1200dp** 才展开。
 *
 * 这一点很容易记错：宽屏判定（840dp）决定"用 Rail 还是底栏"，展开判定（1200dp）是另一档。
 * 手机横屏大约 900dp，官方在那里是**折叠成顶部胶囊**（只显示文字），不是左侧面板。
 */
@Composable
fun shouldExpandNavigationRail(): Boolean {
    val width = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.width.toDp()
    }
    return width >= 1200.dp
}
