package com.ayang.giftoolkit.ui

import androidx.compose.runtime.compositionLocalOf
import top.yukonga.miuix.kmp.material.isRenderEffectSupported
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported

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
 * 5ff41ba9 的玻璃材质（`dynamicGlassMaterial`）与关于页的动态渐变背景都依赖 AGSL 运行时着色器：
 * `isRenderEffectSupported()` 只表示 SDK≥31，而 `isRuntimeShaderSupported()` 才是 SDK≥33。
 * 官方示例 `rememberBlurBackdrop()` 就是后者兜底（`PageUtils.kt:299-308`），
 * 否则 SDK 31/32 上会出现"录制层根本不存在、`textureBlur` 采到全透明"的情况
 * （关于页图标/标题整块消失、渐变也不画）。
 *
 * 两个能力都要满足才算"能画玻璃"；用户开关仍然是最可靠的降级手段。
 * 不要在这里猜渲染器（曾经按 `Build.HARDWARE` 拉黑 ranchu/goldfish，把 `-gpu host`
 * 的硬件加速模拟器也误判成软件渲染，模糊被整段关掉）。
 */
fun isGlassSupported(): Boolean = isRenderEffectSupported() && isRuntimeShaderSupported()

/** 本次组合里玻璃是否实际生效（用户开关 && 设备能力，在 `App.kt` 里算好下发）。 */
val LocalGlassEnabled = compositionLocalOf { false }
