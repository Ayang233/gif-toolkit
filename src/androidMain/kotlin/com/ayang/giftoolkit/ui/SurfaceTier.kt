package com.ayang.giftoolkit.ui

import androidx.compose.runtime.compositionLocalOf
import top.yukonga.miuix.kmp.material.isRenderEffectSupported
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported

/**
 * 表面（顶栏 / 底栏 / 弹层）的材质档位。
 *
 * **这是全工程唯一的形态判断入口。** 不要在页面里直接调
 * `isRenderEffectSupported()` 或自己判 SDK 版本 —— 之前 `App.kt`、`GlassScope`、
 * `Widgets.kt` 三处各判各的，结果"顶栏是玻璃、底栏是实心"这种割裂迟早出现，
 * 而且六个月后没人说得清某个页面到底走哪条路径（AGENTS.md 硬约束）。
 *
 * 三档的来源是 InstallerX 的 `FloatingBottomBarMode`（LiquidGlass / Blur / None）
 * 与 KernelSU 的「浮起底栏 + 是否模糊」两个开关，收成一条有序枚举：
 * 序号越小效果越重，钳制时取小值。
 */
enum class SurfaceTier(val label: String, val summary: String) {

    /** 采样背景 + 模糊 + 玻璃材质（AGSL 边缘光/折射）。观感最完整，最费 GPU。 */
    Glass("玻璃", "采样背景并模糊，带边缘光"),

    /**
     * 不用渐进模糊带：顶栏改为「渐变遮罩压掉内容」，底栏去掉玻璃材质层。
     *
     * 这一档要解决的是**模糊失效**而不是"模糊太浓"：渐进带依赖 runtime shader，
     * 个别机型上它会静默退化，底下的图标/标题就变成一块硬边色斑。
     * 轻玻璃完全绕开那条路径，零 AGSL 依赖。
     */
    Light("轻玻璃", "不模糊，只用渐变把内容压掉"),

    /** 不采样背景。仍然是浮起胶囊 + 阴影 + 主色指示器，不是塌成通栏。 */
    Solid("实色", "不采样背景，用不透明底色"),
}

/**
 * 本机能力上限。
 *
 * 两个门槛是**互相独立**的：模糊/投影看 `isRenderEffectSupported()`（API 31+ 的 RenderEffect），
 * 玻璃材质看 `isRuntimeShaderSupported()`（AGSL）。只判前者会在"能模糊、AGSL 不可用"
 * 的机型上把材质层画成黑色块 —— 真机黑块现象的候选原因之一。
 */
fun deviceSurfaceCeiling(): SurfaceTier = when {
    !isRenderEffectSupported() -> SurfaceTier.Solid
    !isRuntimeShaderSupported() -> SurfaceTier.Light
    else -> SurfaceTier.Glass
}

/** 用户选择不能超过硬件能力；两者取"效果更轻"的那个。 */
fun SurfaceTier.clampedBy(ceiling: SurfaceTier): SurfaceTier =
    if (ordinal > ceiling.ordinal) ceiling else this

/**
 * 当前全局档位。
 *
 * 一个开关就够：曾经想过给顶栏 / 底栏 / 弹层各配一档，实测是过度设计 ——
 * 三个下拉互相牵连，用户要理解"为什么顶栏玻璃、底栏实色才好看"，
 * 而真正需要区分的场景（工具页底部操作区太花）在实践里几乎不出现。
 * 所以收敛成一个「玻璃质感」，用户选完就完事。
 */
val LocalSurfaceTier = compositionLocalOf { SurfaceTier.Glass }

/** 还在采样背景，页面就需要挂录制层。 */
val LocalGlassEnabled = compositionLocalOf { false }
