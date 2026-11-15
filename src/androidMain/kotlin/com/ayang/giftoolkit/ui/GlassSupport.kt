package com.ayang.giftoolkit.ui

import androidx.compose.runtime.compositionLocalOf
import top.yukonga.miuix.kmp.material.isRenderEffectSupported

/**
 * 玻璃材质的**唯一决策点**。
 *
 * 只有两态：开（顶栏、底栏采样背景并模糊）/ 关（不采样，用不透明底色）。
 * 中间那个「轻玻璃」被砍掉了 —— 参考实现 InstallerX 也只有 LiquidGlass / Blur / None
 * 三档渲染路径，但**用户侧只有一个开关**，档位由设备能力自动决定，不交给用户选。
 * 我们之前让用户在三档里挑，等于把库的内部实现细节暴露给了用户。
 *
 * 以后要加档位，只改这里和 [isGlassSupported]，页面里不要再出现第二个判断 ——
 * 之前 `App.kt`、`GlassScope`、`Widgets.kt` 各判各的，就出过「顶栏玻璃、底栏实心」的割裂。
 */

/**
 * 设备能不能画玻璃。
 *
 * 注意 Miuix 的实现是纯版本号判断（`SDK_INT >= 31`），检测不出某台 GPU 上
 * 模糊或 AGSL 材质会不会翻车。所以"关掉玻璃"必须一直留给用户 ——
 * 这是唯一可靠的降级手段。
 */
fun isGlassSupported(): Boolean = isRenderEffectSupported()

/** 本次组合里玻璃是否实际生效（用户开关 && 设备能力，在 `App.kt` 里算好下发）。 */
val LocalGlassEnabled = compositionLocalOf { false }
