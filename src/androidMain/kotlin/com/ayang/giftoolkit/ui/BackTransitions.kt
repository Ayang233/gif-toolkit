package com.ayang.giftoolkit.ui

import androidx.compose.ui.unit.LayoutDirection
import com.ayang.giftoolkit.BackAnimation
import com.ayang.giftoolkit.BackExitDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.nav.transition.navGraphicsTransition

/**
 * 把设置里的档位映射成实际的页面转场。
 *
 * 五个档位里四个是基于 Miuix 公开的 [navGraphicsTransition] 自己写的
 * （参考的只是"效果长什么样"，不引入对方的代码维护负担）。
 *
 * 共同约定：`relativeDepth` 为负表示正在进入/退出，为正表示被覆盖。
 * 所有读写都发生在 `graphicsLayer` 的延迟读取块里，动画过程不触发重组。
 */
fun backTransition(
    animation: BackAnimation,
    exitDirection: BackExitDirection,
): NavTransition = when (animation) {
    // 库自带预设：进入页从右侧滑入，被覆盖页视差后退四分之一宽
    BackAnimation.Miuix -> NavTransitions.MiuixDefault
    // 「无」只关掉**预测性返回**这一层（手势预览、跟手位移），
    // 页面切换本身仍然走 Miuix 默认转场 —— 不能退化成 NavTransitions.None，
    // 那会让所有二级页面进出变成硬切，看起来像卡住了。
    // 系统级的预测性返回在 GifApplication 里用 hidden API 关，和这里互不影响。
    BackAnimation.None -> NavTransitions.MiuixDefault
    BackAnimation.Aosp -> AospScaleTransition
    BackAnimation.Classic -> ClassicSlideTransition
    BackAnimation.Scale -> scaleTransition(exitDirection)
}

/**
 * AOSP 手感：新页轻微缩小并淡入，旧页再退一层。
 * 位移很小、主要靠缩放与透明度，适合"卡片堆叠"的观感。
 */
private val AospScaleTransition: NavTransition = navGraphicsTransition(opaqueDepth = 1f) { scope ->
    val d = scope.relativeDepth
    if (d <= 0f) {
        val p = (-d).coerceIn(0f, 1f)
        val s = 1f - 0.06f * p
        scaleX = s
        scaleY = s
        alpha = 1f - p
    } else {
        val c = d.coerceIn(0f, 1f)
        val s = 1f - 0.04f * c
        scaleX = s
        scaleY = s
    }
}

/** 经典：通栏平移，被覆盖页完全不动（没有视差，也没有缩放）。 */
private val ClassicSlideTransition: NavTransition = navGraphicsTransition(opaqueDepth = 1f) { scope ->
    val d = scope.relativeDepth
    if (d <= 0f) {
        val w = scope.layoutSize.width.toFloat()
        val toward = if (scope.layoutDirection == LayoutDirection.Rtl) -1f else 1f
        translationX = toward * (-d).coerceIn(0f, 1f) * w
    }
}

/**
 * 缩放：新页从一侧缩放平移到中间。
 * 方向由设置决定——跟随手势时按布局方向取（LTR 从左进、RTL 从右进）。
 */
private fun scaleTransition(direction: BackExitDirection): NavTransition =
    navGraphicsTransition(opaqueDepth = 1f) { scope ->
        val side = when (direction) {
            BackExitDirection.AlwaysRight -> 1f
            BackExitDirection.AlwaysLeft -> -1f
            BackExitDirection.FollowGesture ->
                if (scope.layoutDirection == LayoutDirection.Rtl) -1f else 1f
        }
        val d = scope.relativeDepth
        if (d <= 0f) {
            val p = (-d).coerceIn(0f, 1f)
            val s = 1f - 0.12f * p
            scaleX = s
            scaleY = s
            translationX = side * 0.18f * scope.layoutSize.width.toFloat() * p
            alpha = 1f - 0.3f * p
        } else {
            val c = d.coerceIn(0f, 1f)
            val s = 1f - 0.06f * c
            scaleX = s
            scaleY = s
        }
    }
