package com.ayang.giftoolkit.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.computedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset

/**
 * 是否进入「宽屏」布局：用侧边导航栏替代底部导航栏。
 *
 * 阈值与官方 `utils/AdaptiveUtils.kt` 的 `shouldShowSplitPane()` 逐字相同：
 * - 宽度 ≥ 840dp 一定算宽屏；
 * - 600~840dp 之间，只有横向（高宽比 < 1.2）才算 —— 竖着拿的折叠屏展开态仍用底栏。
 *
 * 尺寸来源也跟官方一致：`LocalWindowInfo.current.containerSize`，包在
 * `remember(windowInfo, density) { computedStateOf { … } }` 里 —— 容器尺寸是个 state，
 * 旋转 / 分屏变化时只让这一个布尔失效，不带着整页重组。
 *
 * **不要**再自己造 `LocalContainerSize` 注入：本项目 1.14 曾在 `App` 根部用
 * `BoxWithConstraints` 注入过一次，beta04 官方把那一层删回了 `LocalWindowInfo`，
 * 我们跟着删掉（见 `docs/miuix-baseline-b00169ba-to-3c560868-diff.md` §3）。
 */
@Composable
fun rememberWideLayout(): Boolean {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    return remember(windowInfo, density) {
        computedStateOf {
            val size = windowInfo.containerSize
            if (size.width <= 0) return@computedStateOf false
            with(density) {
                val width = size.width.toDp()
                val height = size.height.toDp()
                width >= 840.dp || (width >= 600.dp && height / width < 1.2f)
            }
        }
    }.value
}

/** 宽屏标记，页面骨架据此决定顶栏是否还允许大标题展开。 */
val LocalWideLayout = compositionLocalOf { false }

/**
 * 宽屏下页面要为玻璃侧栏让出的水平宽度（dp），以**函数**形式下发。
 *
 * 由 `App.kt` 单点计算、单点下发：`(ExpandedWidth + Margin) * railState.progress`。
 * 折叠态（手机上横屏）progress 为 0，页面不需要让位；展开态（≥1200dp）才让出整条面板。
 * 官方 `AppContent.kt:219-229` 用的是同一个式子：让位跟着侧栏的形变进度走，而不是"有侧栏就让 250dp"。
 *
 * **下发函数而不是 Dp 值**：`railState.progress` 是逐帧变化的动画值，
 * 在组合期解包会让整页（含 LazyColumn）随侧栏动画每帧重组。
 * 现在是量测/绘制期才读（`PaddingValues.calculate*Padding`、`Modifier.layout`），
 * 只触发重新测量 —— 这正是官方迁移指南第 5.2 条的要求。
 *
 * **录制层不能跟着这个值收窄** —— 那会让侧栏左半段没有像素可采，玻璃只剩一块平色。
 */
val LocalRailInset = androidx.compose.runtime.staticCompositionLocalOf<() -> Dp> { { 0.dp } }

/**
 * 按「起始边基准内边距 + 当前侧栏让位宽度」把内容推到起始边，**在量测期读取**。
 *
 * `Modifier.padding()` 只接受立即值，写 `padding(start = sideGap + LocalRailInset)` 就等于
 * 在组合期解包逐帧值。这里用 `layout` 把读取挪到量测期，行为不变、重组消失。
 *
 * **`base` 必须真的用上。** 之前这个参数被忽略，只让了侧栏宽度：于是窄屏（railInset = 0）
 * 时底部主操作条左端直接贴到屏幕边缘，右边却有 12dp —— 一张截图就能看出来的不对称。
 *
 * 起始边的系统安全区（横屏挖孔、侧边系统栏）由调用方并进 `base`：
 * 这里只负责"让位 = 基准 + 侧栏"，不替调用方猜安全区，避免和 `pageContentPadding`
 * 里的 `innerPadding` 重复计算。
 */
@Composable
fun Modifier.railStartInset(base: Dp = 0.dp): Modifier {
    val railInset = LocalRailInset.current
    val density = LocalDensity.current
    return this.layout { measurable, constraints ->
        val extra = with(density) { (base + railInset()).roundToPx() }.coerceAtLeast(0)
        val placeable = measurable.measure(constraints.offset(horizontal = -extra))
        layout(placeable.width + extra, placeable.height) {
            // placeRelative 自带 RTL 镜像：RTL 下 extra 就是贴在右边（起始边），不用分支。
            placeable.placeRelative(extra, 0)
        }
    }
}

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
 * 折叠态玻璃侧栏（顶部居中胶囊）的起始偏移，用来避免它压住顶栏的居中标题。
 *
 * 官方 `AppContent.kt:226-232` 算好后下发给两档顶栏的 `centerObstructionStart`
 * （`GlassTopAppBar` / `TopAppBar` 同名参数）：横屏折叠态下标题会绕开这块宽度居中。
 * 未在宽屏 / 侧栏展开 / 还没量到胶囊宽度时给 [Dp.Unspecified]（= 不绕开）。
 *
 * **类型是函数**（官方 `utils/PageUtils.kt:59` 的 `LocalTopAppBarCenterObstructionStart` 同款）：
 * 胶囊宽度是逐帧变化的动画值，组合期解包会让顶栏在折叠动画期间每帧重组。
 */
val LocalTopAppBarCenterObstructionStart =
    androidx.compose.runtime.staticCompositionLocalOf<() -> androidx.compose.ui.unit.Dp> {
        { androidx.compose.ui.unit.Dp.Unspecified }
    }

/**
 * 侧栏是否展开，阈值与官方 `utils/AdaptiveUtils.kt` 的 `shouldExpandNavigationRail()` 一致：
 * **宽度 ≥ 1200dp** 才展开。
 *
 * 这一点很容易记错：宽屏判定（840dp）决定"用 Rail 还是底栏"，展开判定（1200dp）是另一档。
 * 手机横屏大约 900dp，官方在那里是**折叠成顶部胶囊**（只显示文字），不是左侧面板。
 */
@Composable
fun shouldExpandNavigationRail(): Boolean {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    return remember(windowInfo, density) {
        computedStateOf {
            with(density) { windowInfo.containerSize.width.toDp() >= 1200.dp }
        }
    }.value
}
