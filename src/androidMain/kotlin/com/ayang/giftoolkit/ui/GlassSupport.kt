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

/**
 * 当前是不是**软件光栅化**渲染器（模拟器 SwiftShader、桌面 llvmpipe 之类）。
 *
 * 为什么需要这个判断：`isRenderEffectSupported()` / `isRuntimeShaderSupported()` 都只是
 * 版本号判断（SDK ≥ 31 / 33），查不出"这台 GPU 上模糊会不会翻车"。实测本机 AVD
 * （`-gpu swiftshader_indirect`）上 AGSL 能编译、不抛异常，但产出的是**错位的未模糊副本**：
 * 顶栏那条 `progressiveTextureBlur` 带看起来像"字的影子"，而不是把内容糊住。
 * 官方示例 APK 在同一台机器上是同一个现象，说明是渲染器的问题，不是接线的问题。
 *
 * 用途只有一处：顶栏要不要走 `progressiveBlur`（AGSL 路径）。渲染器不支持时走
 * `GlassTopAppBar` 自己的 `bandBrush` 渐变带 —— 那是库的默认路径（`progressiveBlur` 默认 false），
 * 不是另画一套。真机（硬件 GLES）上照旧走原生渐进模糊。
 *
 * 判据有两条：GPU 自报的 `GL_RENDERER`（真机上的 SwiftShader / llvmpipe 会被这条挡住），
 * 以及 `Build.HARDWARE` 里的 ranchu / goldfish（软件渲染模拟器）。
 * 代价是"模拟器 + 硬件加速"也会落到渐变带那一档 —— 只损失一层模糊，条本身还在；
 * 反过来（明明画错却仍走 AGSL）损失的是整条背景带，滚动内容会直接压到标题上。
 */
val isSoftwareRenderer: Boolean by lazy {
    // 首选 GPU 自报的渲染器名。注意主线程没有当前 EGL 上下文，`glGetString` 通常返回 null
    // （只会打一条 "call to OpenGL ES API with no current context"），所以必须有第二条判据。
    val gles = runCatching {
        val renderer = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_RENDERER).orEmpty()
        val vendor = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_VENDOR).orEmpty()
        "$renderer $vendor"
    }.getOrDefault("")
    // 第二条判据：软件渲染的模拟器（ranchu / goldfish）。宁可在这里多降一档，
    // 也不能让顶栏在已知会画错的环境里"没有背景带"——那条带同时承担着
    // "滚动内容不要和标题撞在一起"的职责。
    val hardware = runCatching { android.os.Build.HARDWARE.orEmpty() }.getOrDefault("")
    val text = "$gles $hardware".lowercase()
    listOf("swiftshader", "llvmpipe", "softpipe", "software", "emulator opengl", "ranchu", "goldfish")
        .any { text.contains(it) }
}

/** 本次组合里玻璃是否实际生效（用户开关 && 设备能力，在 `App.kt` 里算好下发）。 */
val LocalGlassEnabled = compositionLocalOf { false }
