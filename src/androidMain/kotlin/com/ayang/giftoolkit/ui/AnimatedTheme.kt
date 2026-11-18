package com.ayang.giftoolkit.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.lerp
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * 切换主题时让整套配色逐色过渡，而不是硬切。
 *
 * Miuix 的 `MiuixTheme(controller)` 是"取到目标配色 → 立刻换掉"，
 * 所以深色/浅色、静态/Monet 之间切换时所有颜色会瞬变。
 * 这里改用第二个重载 `MiuixTheme(colors = ...)`：自己维护「起点色 → 目标色 → 进度」，
 * 每帧把 53 个颜色角色插值一次再交给库。
 *
 * 为什么逐色插值而不是整体 Crossfade：Crossfade 会把整棵界面画两遍再溶解，
 * 滚动位置、玻璃录制层都会跟着抖；逐色插值只改颜色，布局与状态完全不动。
 *
 * 注意：`Colors.copy()` 的形参就是权威字段清单，Miuix 以后加字段时
 * 这里的 [lerpColors] 需要同步补一行（编译器不会报错，只是新字段不参与过渡）。
 */
@Composable
fun AnimatedMiuixTheme(
    controller: ThemeController,
    content: @Composable () -> Unit,
) {
    val target = controller.currentColors()
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(target) {
        if (to != target) {
            // 从"当前看到的颜色"继续，避免连续切换时跳回上一次的终点
            from = lerpColors(from, to, progress.value)
            to = target
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis = 320, easing = FastOutSlowInEasing))
        }
    }

    val animated = lerpColors(from, to, progress.value)
    // `MiuixTheme(colors = ...)` 这个重载**只**提供 LocalColors / LocalTextStyles / LocalIndication /
    // LocalOverscrollFactory，比 `MiuixTheme(controller = ...)` 少两样：
    //   - `LocalContentColor`：少了它文字色回落到 `Color.Black`（`ContentColor.kt:19`）。
    //     目前界面里每个 Text 都显式给色、或包在 Card 里（Card 自己提供 contentColor），
    //     所以还没露馅 —— 但那是碰巧，补上才是对的。
    //   - `LocalColorSchemeMode`：它决定 `MiuixTheme.isDynamicColor`（`MiuixTheme.kt:87-96`）。
    //     补不了（`ThemeController.kt` 里是 internal），所以动态取色下依赖它的组件
    //     （底栏/侧栏选中色、Switch 未选中拇指色）要在调用点显式传参 —— 见 `App.kt` 的
    //     `selectedColor`。这一条写进 `docs/os4-round10-audit.md` §5.2。
    MiuixTheme(colors = animated) {
        CompositionLocalProvider(
            LocalContentColor provides animated.onBackground,
            content = content,
        )
    }
}

/** 按 [Colors.copy] 的字段顺序逐项插值；t = 1 时直接返回目标色，省掉一次构造。 */
private fun lerpColors(from: Colors, to: Colors, t: Float): Colors {
    if (t >= 1f) return to
    if (t <= 0f) return from
    return to.copy(
        primary = lerp(from.primary, to.primary, t),
        onPrimary = lerp(from.onPrimary, to.onPrimary, t),
        primaryVariant = lerp(from.primaryVariant, to.primaryVariant, t),
        onPrimaryVariant = lerp(from.onPrimaryVariant, to.onPrimaryVariant, t),
        error = lerp(from.error, to.error, t),
        onError = lerp(from.onError, to.onError, t),
        errorContainer = lerp(from.errorContainer, to.errorContainer, t),
        onErrorContainer = lerp(from.onErrorContainer, to.onErrorContainer, t),
        disabledPrimary = lerp(from.disabledPrimary, to.disabledPrimary, t),
        disabledOnPrimary = lerp(from.disabledOnPrimary, to.disabledOnPrimary, t),
        disabledPrimaryButton = lerp(from.disabledPrimaryButton, to.disabledPrimaryButton, t),
        disabledOnPrimaryButton = lerp(from.disabledOnPrimaryButton, to.disabledOnPrimaryButton, t),
        disabledPrimarySlider = lerp(from.disabledPrimarySlider, to.disabledPrimarySlider, t),
        primaryContainer = lerp(from.primaryContainer, to.primaryContainer, t),
        onPrimaryContainer = lerp(from.onPrimaryContainer, to.onPrimaryContainer, t),
        secondary = lerp(from.secondary, to.secondary, t),
        onSecondary = lerp(from.onSecondary, to.onSecondary, t),
        secondaryVariant = lerp(from.secondaryVariant, to.secondaryVariant, t),
        onSecondaryVariant = lerp(from.onSecondaryVariant, to.onSecondaryVariant, t),
        disabledSecondary = lerp(from.disabledSecondary, to.disabledSecondary, t),
        disabledOnSecondary = lerp(from.disabledOnSecondary, to.disabledOnSecondary, t),
        disabledSecondaryVariant = lerp(from.disabledSecondaryVariant, to.disabledSecondaryVariant, t),
        disabledOnSecondaryVariant = lerp(from.disabledOnSecondaryVariant, to.disabledOnSecondaryVariant, t),
        secondaryContainer = lerp(from.secondaryContainer, to.secondaryContainer, t),
        onSecondaryContainer = lerp(from.onSecondaryContainer, to.onSecondaryContainer, t),
        secondaryContainerVariant = lerp(from.secondaryContainerVariant, to.secondaryContainerVariant, t),
        onSecondaryContainerVariant = lerp(from.onSecondaryContainerVariant, to.onSecondaryContainerVariant, t),
        tertiaryContainer = lerp(from.tertiaryContainer, to.tertiaryContainer, t),
        onTertiaryContainer = lerp(from.onTertiaryContainer, to.onTertiaryContainer, t),
        tertiaryContainerVariant = lerp(from.tertiaryContainerVariant, to.tertiaryContainerVariant, t),
        background = lerp(from.background, to.background, t),
        onBackground = lerp(from.onBackground, to.onBackground, t),
        onBackgroundVariant = lerp(from.onBackgroundVariant, to.onBackgroundVariant, t),
        surface = lerp(from.surface, to.surface, t),
        onSurface = lerp(from.onSurface, to.onSurface, t),
        surfaceVariant = lerp(from.surfaceVariant, to.surfaceVariant, t),
        onSurfaceSecondary = lerp(from.onSurfaceSecondary, to.onSurfaceSecondary, t),
        onSurfaceVariantSummary = lerp(from.onSurfaceVariantSummary, to.onSurfaceVariantSummary, t),
        onSurfaceVariantActions = lerp(from.onSurfaceVariantActions, to.onSurfaceVariantActions, t),
        disabledOnSurface = lerp(from.disabledOnSurface, to.disabledOnSurface, t),
        surfaceContainer = lerp(from.surfaceContainer, to.surfaceContainer, t),
        onSurfaceContainer = lerp(from.onSurfaceContainer, to.onSurfaceContainer, t),
        onSurfaceContainerVariant = lerp(from.onSurfaceContainerVariant, to.onSurfaceContainerVariant, t),
        surfaceContainerHigh = lerp(from.surfaceContainerHigh, to.surfaceContainerHigh, t),
        onSurfaceContainerHigh = lerp(from.onSurfaceContainerHigh, to.onSurfaceContainerHigh, t),
        surfaceContainerHighest = lerp(from.surfaceContainerHighest, to.surfaceContainerHighest, t),
        onSurfaceContainerHighest = lerp(from.onSurfaceContainerHighest, to.onSurfaceContainerHighest, t),
        outline = lerp(from.outline, to.outline, t),
        dividerLine = lerp(from.dividerLine, to.dividerLine, t),
        windowDimming = lerp(from.windowDimming, to.windowDimming, t),
        sliderKeyPoint = lerp(from.sliderKeyPoint, to.sliderKeyPoint, t),
        sliderKeyPointForeground = lerp(from.sliderKeyPointForeground, to.sliderKeyPointForeground, t),
        sliderBackground = lerp(from.sliderBackground, to.sliderBackground, t),
    )
}
