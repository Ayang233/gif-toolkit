package com.ayang.giftoolkit.ui

import androidx.compose.runtime.compositionLocalOf
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported

/**
 * 玻璃材质的**唯一决策点**。
 *
 * 只有两态：开（顶栏、底栏、卡片采样背景并模糊）/ 关（不采样，用不透明底色）。
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
 * 只看库的 `isRuntimeShaderSupported()`（Android 上是 `SDK_INT >= 33`）——
 * 这正是官方示例的判断点（`LicensePage.kt:53`、`IconPage.kt:178`、`PageUtils.kt` 的
 * `rememberBlurBackdrop()` 都是这一个函数），因为 0.9.5 起玻璃材质
 * （`dynamicGlassMaterial`）、渐进模糊（`progressiveTextureBlur`）与关于页的动态渐变背景
 * 全都依赖 AGSL 运行时着色器。
 *
 * 以前这里还叠了一个 `isRenderEffectSupported()`（`SDK_INT >= 31`），而本工程 `minSdk = 33`，
 * 那个判断恒为 true —— 属于我们自己多写的一层兼容代码，0.9.5-beta02 起已删掉。
 *
 * 仍然**不要**在这里猜渲染器：曾经按 `Build.HARDWARE` 拉黑 ranchu/goldfish，把 `-gpu host`
 * 的硬件加速模拟器也误判成软件渲染，模糊被整段关掉。能力判断一律交给库。
 *
 * 注意 `isRuntimeShaderSupported()` 只能回答"这个系统版本有没有 AGSL"，回答不了
 * "这台 GPU 上模糊会不会翻车"（真机上出现过「内容不糊开、只被压暗」），
 * 所以"关掉玻璃"这个用户开关必须一直留着。
 */
fun isGlassSupported(): Boolean = isRuntimeShaderSupported()

/** 本次组合里玻璃是否实际生效（用户开关 && 设备能力，在 `App.kt` 里算好下发）。 */
val LocalGlassEnabled = compositionLocalOf { false }
