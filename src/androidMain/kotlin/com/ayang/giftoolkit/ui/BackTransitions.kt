package com.ayang.giftoolkit.ui

import com.ayang.giftoolkit.BackAnimation
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.NavTransitions

/**
 * 把设置里的档位映射成官方转场预设。
 *
 * 上一版这里自己写了三套 `navGraphicsTransition`（AOSP 缩放 / 经典平移 / 跟随手势缩放），
 * 属于自造动画：曲线、位移比例、被覆盖页的处理都是我们手调的，跟 MIUIX 的动效语言不一致，
 * 上游一改 `NavTransitions` 也不会跟着动。现在**只做映射**，一格动画都不自己写。
 *
 * 官方当前提供的预设（`miuix-nav/.../NavTransitions.kt`）：
 * - [NavTransitions.MiuixDefault]：新页从右侧滑入、被覆盖页四分之一宽视差后退；
 * - [NavTransitions.Modal]：新页从底部升起，下层完全不动（`opaqueDepth = 2f`）；
 * - [NavTransitions.None]：恒等变换、直接换页。**本项目不使用** —— 见下。
 *
 * **`BackAnimation.None` 不等于"没有动画"。** 它只关掉系统级预测性返回
 * （手势跟手预览），页面切换本身仍然走 MIUIX 默认转场；系统级那层由
 * `GifApplication` 反射置 `enableOnBackInvokedCallback = false` 实现。
 * 所以这里 `None` 映射到 [NavTransitions.MiuixDefault]，而不是 [NavTransitions.None] ——
 * 后者会让所有二级页进出变成硬切，看起来像卡住了。
 *
 * `NavTransitions.Expansion` 需要页面自己用 `Modifier.navTransitionSource(route)` 绑定源，
 * 本项目没有这种"从卡片展开成整页"的入口，绑不到源时它会退回 MiuixDefault，所以不列进选项。
 */
fun backTransition(animation: BackAnimation): NavTransition = when (animation) {
    BackAnimation.Miuix -> NavTransitions.MiuixDefault
    BackAnimation.Modal -> NavTransitions.Modal
    BackAnimation.None -> NavTransitions.MiuixDefault
}
