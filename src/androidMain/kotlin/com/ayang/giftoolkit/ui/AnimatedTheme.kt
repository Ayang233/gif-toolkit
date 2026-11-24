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

    // **不能把 `target` 直接当 `LaunchedEffect` 的 key。**
    // `ThemeController.currentColors()` 在 Monet 档、且没有 `keyColor` 时走库里的
    // `platformDynamicColors(dark)`，那条路**没有 remember** —— 每次组合都返回一个新的
    // `Colors` 实例。用实例做 key 会让 effect 每帧重启、`progress.snapTo(0f)` 反复清进度：
    // 现象就是"点了深色但界面不变"，只有再动一下别的开关（比如动态取色）才生效。
    // 静态档返回控制器里 `lightColors` / `darkColors` 两个稳定实例，所以这条一直没暴露。
    // 这里改成按**颜色值**做 key：只有真正换了配色才重启动画。
    val targetKey = listOf(
        target.background,
        target.surface,
        target.surfaceContainer,
        target.primary,
        target.onBackground,
    )

    LaunchedEffect(targetKey) {
        if (to != target) {
            // 从"当前看到的颜色"继续，避免连续切换时跳回上一次的终点
            from = lerpColors(from, to, progress.value)
            to = target
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis = 320, easing = FastOutSlowInEasing))
        }
    }

    val animated = lerpColors(from, to, progress.value)
    // 外层 `MiuixTheme(controller)` 只为一件事：提供 `LocalColorSchemeMode`
    // （`ThemeController.kt` 里是 internal，下游无法自己 provide）。
    // 5ff41ba9 起所有 `*Defaults.material()` 都走 `dynamicGlassMaterial`，
    // 它靠 `MiuixTheme.isDynamicColor` 决定要不要加 `surfaceContainer` tint
    // （`Glass.kt`）：少了这一层，Monet 下弹层 / 顶栏按钮 / 底栏 / Rail 的新材质全部不生效，
    // 看起来就像"还是老样式"。
    //
    // 颜色仍然由内层 `MiuixTheme(colors = animated)` 覆盖（它提供 LocalColors 等），
    // CompositionLocal 是继承的 —— 内层不提供的 `LocalColorSchemeMode` 保留外层的值。
    MiuixTheme(controller = controller) {
        // `MiuixTheme(colors = ...)` 这个重载**只**提供 LocalColors / LocalTextStyles / LocalIndication /
        // LocalOverscrollFactory，比 `MiuixTheme(controller = ...)` 少 `LocalContentColor`：
        // 少了它文字色回落到 `Color.Black`，所以下面显式补上。
        MiuixTheme(colors = animated) {
            CompositionLocalProvider(
                LocalContentColor provides animated.onBackground,
                content = content,
            )
        }
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
