package com.ayang.giftoolkit.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayang.giftoolkit.R
import com.ayang.giftoolkit.ui.GroupHeader
import com.ayang.giftoolkit.ui.LocalGlassEnabled
import com.ayang.giftoolkit.ui.Os4ListPage
import com.ayang.giftoolkit.ui.bg.BgEffectBackground
import com.ayang.giftoolkit.ui.rememberAppVersion
import com.ayang.giftoolkit.ui.rememberCombinedBackdrop
import com.ayang.giftoolkit.ui.rememberGlassScope
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.glass.GlassCard
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.graphics.BlendMode as ComposeBlendMode

/** 源代码地址。为空时那一行照常显示、点击提示"尚未公开"，不会出现死链。 */
private const val SOURCE_URL = "https://github.com/Ayang233/gif-toolkit"

/**
 * 图标容器与主体尺寸，**严格取官方** `AboutPage.kt:249-262`：容器 `88.dp`、圆角
 * `RoundedCornerShape(24.dp)`、`clip = true`，主体 `Image`。
 *
 * 主体尺寸这里取 88dp 而不是官方的 74dp，原因只在资源：官方那张是 launcher 图，画面几乎铺满；
 * 本项目的 `ic_app_mark` 是 108 画布、可见图形只占 x=14..94（74.1%），
 * 取 88dp 后可见主体约 65dp，和官方 74dp 图的可见主体（约 66–70dp）同一档。
 * 换成不留白的图之后，把这里改回 74dp 即是逐数字对齐。
 */
private val MarkSampleSize = 88.dp

/** 图标外层容器：官方 `88.dp`（`AboutPage.kt:249`）。 */
private val MarkBoxSize = 88.dp

/**
 * 关于页。
 *
 * **页面骨架走共用的 [Os4ListPage]，不再自己搭 Scaffold。**
 * 之前这里是"Scaffold + ContentBackdropBox + LazyColumn + 手取安全区"的第二套骨架，
 * 结果顶栏采样源要手写、首帧头部高度要手算，两处都出过问题（`docs/os4-round11-audit.md`）。
 * 现在把"整屏背景 + 固定头部"做成骨架的两个参数：
 *
 * - `background`：页面根部的整屏渐变（Scaffold 因此在它上面透明）；
 * - `largeHeader`：固定在列表下面、随滚动淡出的图标 / 名称 / 版本；
 * - `topBarBackdrop`：顶栏采样源（两层叠加，见下）。
 *
 * 版式与交互对齐参考项目 InstallerX Revived 的 `MiuixAboutPage.kt`：
 * 整屏动态渐变 + 大图标 + 35sp 标题 + 版本号，上滑时这三块按不同斜率淡出并缩到 0.95，
 * 内容列表盖着它们往上走；内容末尾用一块撑满父高度的空白保证"总能滑动"，头部才收得干净。
 *
 * 图标与软件名称走参考项目**同一套原生机制**，没有第二套绘制路径：
 *
 * ```
 * textureBlur(backdrop = 背景录制层,
 *             blurRadius = 150f,
 *             colors = BlurColors(blendColors = logoBlend),
 *             contentBlendMode = DstIn)   ← 采样背景 → 混色 → 按内容 alpha 裁形
 * ```
 *
 * 背景录制层只录 `BgEffectBackground` 那层渐变（`bgModifier`），所以图标、标题、背景三者同源。
 *
 * **不要改成 `gradientFilled` / `SrcIn` / ShaderBrush 之类的自制渐变**（第九轮做过，已删除）。
 *
 * 玻璃能力只看库的 `isRenderEffectSupported()`（见 `ui/GlassSupport.kt`）：之前这里还叠了一条
 * "软件渲染器就跳过 textureBlur"的判据，把 `-gpu host` 的硬件加速模拟器也一起判掉了，
 * 结果图标/标题退化成纯色剪影、不跟随渐变。现在不做渲染器猜判，能力交给库；
 * 观感验证请用 `-gpu host` 启动的模拟器或真机。
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    val context = LocalContext.current
    // 正文录制层不垫页面底色：这一页的背景是整块渐变，垫了就把渐变盖住了（见 rememberPageBackdrop）。
    val glass = rememberGlassScope(opaqueBase = false)
    val glassEnabled = LocalGlassEnabled.current
    // 玻璃开关 && 库的能力判断（`isGlassSupported`），不再按"模拟器"降级。
    val blurEnabled = glassEnabled
    val version = rememberAppVersion()

    // 背景录制层：只录渐变那一层，给图标/标题的 textureBlur 当采样源。
    // 官方 `AboutPage.kt:141` 的 `backdrop = rememberBlurBackdrop()` + `bgModifier = layerBackdrop(backdrop)`
    // 是同一处接线；KSU 在内容里另建一份 `rememberLayerBackdrop()` 挂在 BgEffectBackground 上。
    // **不能**把录制层套到内容外面：内容是渐变层的兄弟，套进去就成"采样到自己"（AGENTS.md 第 1 条）。
    val bgBackdrop = rememberLayerBackdrop()

    // 深浅主题各一套混合色，取自 KSU `AboutMiuix.kt:189-203` / InstallerX `MiuixAboutPage.kt:420-434`，
    // 与 MIUIX 官方 `AboutPage.kt` 的 logoBlend 完全相同（三档：ColorBurn/ColorDodge、LinearLight、Lab）。
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val logoBlend = remember(isDark) {
        if (isDark) {
            listOf(
                BlendColorEntry(Color(0xe6a1a1a1), BlurBlendMode.ColorDodge),
                BlendColorEntry(Color(0x4de6e6e6), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af500), BlurBlendMode.Lab),
            )
        } else {
            listOf(
                BlendColorEntry(Color(0xcc4a4a4a), BlurBlendMode.ColorBurn),
                BlendColorEntry(Color(0xff4f4f4f), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af200), BlurBlendMode.Lab),
            )
        }
    }
    // 用 BlurDefaults 的工厂而不是直接构造：List 对 Compose 不稳定，
    // 直接构造会让 textureBlur 每帧重建 effect（官方注释里专门写了这一条）。
    // 它本身是 @Composable（内部按参数 remember），所以不能再套一层 remember。
    val logoColors = BlurDefaults.blurColors(blendColors = logoBlend)

    // 顶栏的采样源：渐变层 + 正文层叠加。
    // 正文层在顶栏那块区域是空的（顶部 40dp 图标留白），渐变层滚到底又会淡出
    // —— 单采哪一层都会有一段时间采到空像素，真机表现为深色横带 + 返回键胶囊花屏。
    val contentBackdrop = glass.contentBackdrop
    val topBarBackdrop: top.yukonga.miuix.kmp.blur.Backdrop? = when {
        !blurEnabled -> null
        contentBackdrop != null -> rememberCombinedBackdrop(bgBackdrop, contentBackdrop)
        else -> bgBackdrop
    }

    Os4ListPage(
        title = "关于",
        glass = glass,
        onBack = onBack,
        extraBottom = 24.dp,
        // 整屏渐变铺在 Scaffold 外面（骨架里就是页面根部），随头部收起进度淡出。
        background = { progress ->
            BgEffectBackground(
                modifier = Modifier.fillMaxSize(),
                bgModifier = if (blurEnabled) Modifier.layerBackdrop(bgBackdrop) else Modifier,
                alpha = { 1f - progress() },
            ) {}
        },
        topBarBackdrop = topBarBackdrop,
        largeHeader = { progress -> AboutHeader(progress, blurEnabled, bgBackdrop, logoColors, version) },
    ) {
        item(key = "about") {
            // `fillParentMaxHeight` 撑满一屏：内容再短也保证列表可滚，头部才收得回去。
            // 官方 `AboutPage.kt` 与 InstallerX `MiuixAboutPage.kt:604-608` 都是这么写的。
            Box {
                Spacer(Modifier.fillParentMaxHeight())
                Column {
                    GroupHeader("关于")
                    // 玻璃卡走库的 `miuix-glass` GlassCard（0.9.5-beta04 新增）：
                    // 它给"放在玻璃/半透明表面上的内容"提供官方的半透明填充，
                    // 官方 `AboutPage.kt:360/386` 现在就是 `GlassCard(padding(horizontal = 12.dp)) { ArrowPreference… }`。
                    // 之前这里是自造的"Card + textureBlur 采样背景"（InstallerX 写法），
                    // beta04 起官方有了同名组件，自造那份已删除。
                    GlassCard(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                    ) {
                        ArrowPreference(
                            title = "查看源代码",
                            summary = "在 GitHub 上查看源代码",
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse(SOURCE_URL),
                                        ),
                                    )
                                }
                            },
                        )
                        ArrowPreference(
                            title = "开源许可",
                            summary = "本应用自身许可证与所用第三方开源库的许可条款",
                            onClick = onOpenLicenses,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 关于页的头部：图标 + 软件名称 + 版本号。
 *
 * [progress] 由 `Os4ListPage` 的 `largeHeader` 给出（0..1，列表首项滚过头部高度的比例）。
 * 三块按不同斜率淡出并缩到 0.95：版本先淡（0.05 起）、标题次之（0.20 起）、图标最后（0.35 起），
 * 与参考项目 `MiuixAboutPage.kt:482/517/546` 的阈值一致。
 */
@Composable
private fun AboutHeader(
    progress: () -> Float,
    blurEnabled: Boolean,
    bgBackdrop: top.yukonga.miuix.kmp.blur.LayerBackdrop,
    logoColors: top.yukonga.miuix.kmp.blur.BlurColors,
    version: String,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(MarkBoxSize)
            .graphicsLayer {
                val p = ((progress() - 0.35f) / 0.15f).coerceIn(0f, 1f)
                alpha = 1f - p
                scaleX = 1f - p * 0.05f
                scaleY = 1f - p * 0.05f
                // 与 KSU 的 `clipToBounds()` 同义：主体溢出容器的部分是透明的，
                // 但裁一下更稳（换图标资源时不会突然露到外面）。
                clip = true
            },
    ) {
        Image(
            painter = painterResource(R.drawable.ic_app_mark),
            contentDescription = null,
            // 与 KSU `AboutMiuix.kt:313` 一致：始终 tint。
            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onBackground),
            modifier = Modifier
                .requiredSize(MarkSampleSize)
                .then(
                    if (blurEnabled) {
                        Modifier.textureBlur(
                            backdrop = bgBackdrop,
                            // 采样区形状与图标容器的圆角观感一致（官方图标容器是 RoundedCornerShape(24.dp)）。
                            shape = RoundedCornerShape(24.dp),
                            // 100f 就是 `BlurDefaults.MaxBlurRadius`，与官方 AboutPage 同值
                            // （写 150f 也会被库夹紧到这个上限）。
                            blurRadius = BlurDefaults.MaxBlurRadius,
                            noiseCoefficient = BlurDefaults.NoiseCoefficient,
                            colors = logoColors,
                            contentBlendMode = ComposeBlendMode.DstIn,
                            enabled = true,
                        )
                    } else {
                        Modifier
                    },
                ),
        )
    }
    Text(
        text = "GifToolkit",
        modifier = Modifier
            .padding(top = 12.dp)
            .graphicsLayer {
                val p = ((progress() - 0.2f) / 0.15f).coerceIn(0f, 1f)
                alpha = 1f - p
                scaleX = 1f - p * 0.05f
                scaleY = 1f - p * 0.05f
            }
            .then(
                if (blurEnabled) {
                    Modifier.textureBlur(
                        backdrop = bgBackdrop,
                        // 与官方 `AboutPage.kt:273-282` 逐参一致：shape 16dp / 半径 100f。
                        shape = RoundedCornerShape(16.dp),
                        blurRadius = BlurDefaults.MaxBlurRadius,
                        noiseCoefficient = BlurDefaults.NoiseCoefficient,
                        colors = logoColors,
                        contentBlendMode = ComposeBlendMode.DstIn,
                        enabled = true,
                    )
                } else {
                    Modifier
                },
            ),
        // 与参考项目一致：字形只提供 alpha，颜色由采样结果决定；
        // 模糊不可用时这行就是主题色实字（回退，不是第二套渐变）。
        color = MiuixTheme.colorScheme.onBackground,
        fontWeight = FontWeight.Bold,
        fontSize = 35.sp,
    )
    Text(
        text = version,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 5.dp)
            .graphicsLayer {
                val p = ((progress() - 0.05f) / 0.15f).coerceIn(0f, 1f)
                alpha = 1f - p
            },
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        // 字号走官方的排版档位（`body2` = 14sp），不再写裸 `fontSize = 14.sp`：
        // 上游调整字号时这一行跟着变，和同一页其它正文保持同一套比例。
        style = MiuixTheme.textStyles.body2,
        textAlign = TextAlign.Center,
    )
}
