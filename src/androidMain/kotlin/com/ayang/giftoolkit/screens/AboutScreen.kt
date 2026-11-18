package com.ayang.giftoolkit.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayang.giftoolkit.R
import com.ayang.giftoolkit.ui.ContentBackdropBox
import com.ayang.giftoolkit.ui.GroupHeader
import com.ayang.giftoolkit.ui.LocalBottomBarClearance
import com.ayang.giftoolkit.ui.LocalGlassEnabled
import com.ayang.giftoolkit.ui.PageTopBar
import com.ayang.giftoolkit.ui.bg.BgEffectBackground
import com.ayang.giftoolkit.ui.cardColor
import com.ayang.giftoolkit.ui.pageScrollModifiers
import com.ayang.giftoolkit.ui.rememberAppVersion
import com.ayang.giftoolkit.ui.rememberGlassScope
import com.ayang.giftoolkit.ui.rememberHorizontalSafeInsets
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.graphics.BlendMode as ComposeBlendMode

/** 源代码地址。为空时那一行照常显示、点击提示"尚未公开"，不会出现死链。 */
private const val SOURCE_URL = "https://github.com/Ayang233/gif-toolkit"

/**
 * 图标主体在 backdrop 里的采样尺寸。
 *
 * 不是随手给的数：`ic_app_mark` 是 108×108 画布，实际图形只占 x=14..94（80 单位宽，
 * 占画布 74.1%）。要让**看得见的胶片条**正好落在 100dp 的容器里（KSU 是
 * `245dp` 图形 / `100dp` 容器、InstallerX 是 `160dp` / `88dp`，两者都靠图形自带的透明
 * 留白把主体收进容器），需要的采样尺寸 = 100dp × 108/80 ≈ 135dp，这里取 132dp
 * （左右各留 1dp 余量，避免圆角被裁）。可见主体约 97.8 × 48.9dp。
 */
private val MarkSampleSize = 132.dp

/** 图标外层容器。与 KSU 的 `Modifier.size(100.dp)` 同档，比第九轮的 88dp 底板大一圈。 */
private val MarkBoxSize = 100.dp

/**
 * 关于页。
 *
 * 版式对齐 InstallerX Revived 的 Miuix 关于页（`MiuixAboutPage.kt`，26.11.caccb78）、
 * KernelSU 原版管理器的 `AboutMiuix.kt` 与 MIUIX 官方示例 `AboutPage.kt`：
 * 整块 OS3 动态渐变 + 大图标 + 大标题 + 版本号，滚动时这三块按不同斜率淡出并缩到 0.95；
 * 下面是一张「关于」卡片。
 *
 * 图标与软件名称走的是参考项目**同一套原生机制**，没有第二套绘制路径：
 *
 * ```
 * textureBlur(backdrop = 背景录制层,
 *             blurRadius = 150f,
 *             colors = BlurColors(blendColors = logoBlend),
 *             contentBlendMode = DstIn)   ← 采样背景 → 混色 → 按内容 alpha 裁形
 * ```
 *
 * 采样源是 [bgBackdrop]——只录 `BgEffectBackground` 那层渐变（`bgModifier`），
 * 所以图标、标题、背景三者同源：同一帧、同一色段、同一组混合色，不会各跑一套动画。
 *
 * **不要改成 `gradientFilled` / `SrcIn` / ShaderBrush 之类的自制渐变**（第九轮做过，
 * 本轮已删除）：那是另一条绘制路径，颜色与背景不同步，而且不是"液态玻璃"。
 *
 * **本机模拟器（`swiftshader_indirect`）上实测：图标与标题整块不可见。**
 * 原因是 `textureBlur` 在这个 GPU 上编译得出来、但不产出像素，
 * 而 `DstIn` 是用采样结果去替换内容 —— 采样为空，内容就一起没了。
 * 官方示例 APK 在同一台机器上关于页同样没有标题。
 * 这条**不是接线错误**：把玻璃开关关掉（`blurEnabled = false`，走参考项目的
 * "不模糊就正常画"分支）时图标与标题立刻正常显示 —— 截图见
 * `_archive/round10/final/final_glassoff_07_about.png`。
 * 所以图标上的 `ColorFilter.tint(onBackground)`（与 KSU 一致）只是让内容层带上正确颜色，
 * **不要**指望它在模糊失败时兜出一个可见剪影。
 * 真机验证前保持原生实现不动。
 *
 * 三处按参考应用固定的写法：
 *
 * 1. **图标不带白底/底板**：参考项目只有"单色剪影 + 透明背景"，主体靠 `requiredSize`
 *    撑大、再按容器裁剪。这里容器 100dp（KSU 同值）、采样 132dp，
 *    可见主体实测 **98 × 49dp**（2026-11-18 截图量测），横向占满容器的 98%。
 *    KSU 的 245dp、InstallerX 的 160dp 之所以更大，是因为它们的
 *    `ic_launcher_foreground` / `ic_launcher_monochrome` 是近方形图标、留白多，
 *    撑到 245dp 后**可视主体仍会被 100dp 容器裁掉一圈**；本项目的 `ic_app_mark`
 *    是 2:1 的胶片条，横向再放大就会把两端齿孔裁掉，所以取"刚好填满"。
 * 2. **入口行不带前置图标**：InstallerX 用的是 `ArrowPreference(title, summary)`（无 `startAction`）。
 * 3. **只有一个许可入口**：本项目自身许可证与第三方组件许可合成一页（[LicensesScreen]）。
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    val context = LocalContext.current
    // 顶栏的采样源：页面正文录制层。垫底色会把整块渐变盖掉（见 rememberPageBackdrop）。
    val glass = rememberGlassScope(opaqueBase = false)
    val blurEnabled = LocalGlassEnabled.current
    val version = rememberAppVersion()
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    val density = LocalDensity.current

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

    // 大标题区高度：先量再对齐，不写死 —— 字体缩放、横屏都会改变它。
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerHeight = with(density) { headerHeightPx.toDp() }

    // 0..1 的滚动进度，只由列表首项的滚动偏移算出。
    // **不要在组合期直接读它**：下面一律以 lambda 传给 graphicsLayer 阶段读取，
    // 否则整个关于页会随滚动每帧重组（官方 `AboutPage` 也是 `scrollProgressProvider`）。
    val scrollProgress by remember {
        derivedStateOf {
            when {
                listState.firstVisibleItemIndex > 0 -> 1f
                headerHeightPx > 0 -> (listState.firstVisibleItemScrollOffset.toFloat() / headerHeightPx).coerceIn(0f, 1f)
                else -> 0f
            }
        }
    }
    val progressProvider: () -> Float = remember { { scrollProgress } }
    // 布尔量在 1f 处翻转，derivedStateOf 只在翻转时重组顶栏，不跟帧。
    val collapsed by remember { derivedStateOf { scrollProgress == 1f } }

    // 渐变铺在**页面根部**、Scaffold 之外：官方关于页的渐变是一直延伸到状态栏下面去的，
    // 只有把背景放在顶栏外面才做得到（顶栏自己不上色，滚动前是透明的）。
    // Scaffold 因此必须透明，正文也不再有背景 —— 它看到的像素就是这层渐变。
    Box(Modifier.fillMaxSize()) {
        BgEffectBackground(
            modifier = Modifier.fillMaxSize(),
            // 录制层挂在这一层（只画渐变的那一层），不是内容外面。
            bgModifier = if (blurEnabled) Modifier.layerBackdrop(bgBackdrop) else Modifier,
            alpha = { 1f - progressProvider() },
        ) {}

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0),
            topBar = {
                PageTopBar(
                    title = "关于",
                    glass = glass,
                    isScrolled = collapsed,
                    scrollBehavior = scrollBehavior,
                    onBack = onBack,
                    expandable = false,
                    titleAlpha = { ((progressProvider() - 0.35f) / 0.65f).coerceIn(0f, 1f) },
                    // 玻璃关闭时退回普通 TopAppBar：它的默认底色是 surface（不透明白），
                    // 会在页面顶部切出一条白带、把渐变盖掉。参考项目的写法是
                    // "没滚到收起状态就透明，收起后才给实色"，这里照抄。
                    background = if (collapsed) MiuixTheme.colorScheme.surface else Color.Transparent,
                )
            },
        ) { innerPadding ->
            // 录制层照旧只包正文：顶栏是它的兄弟节点，采样它又不在它里面（AGENTS.md 第 1 条）。
            ContentBackdropBox(glass) {
                // 这一页不用 Os4ListPage，横向安全区得自己取（横屏挖孔在起始边）。
                val safeInsets = rememberHorizontalSafeInsets()
                val layoutDirection = LocalLayoutDirection.current
                val safeStart = safeInsets.calculateLeftPadding(layoutDirection)
                val safeEnd = safeInsets.calculateRightPadding(layoutDirection)
                val logoTopPadding = innerPadding.calculateTopPadding() + 40.dp
                val listTop = innerPadding.calculateTopPadding() + 2.dp

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = safeStart, end = safeEnd)
                        .padding(top = logoTopPadding, bottom = 8.dp)
                        .onSizeChanged { headerHeightPx = it.height },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // 图标：纯剪影，无底板。容器 100dp 只负责裁剪与定位，主体按 132dp 采样后放大。
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(MarkBoxSize)
                            .graphicsLayer {
                                val p = ((progressProvider() - 0.35f) / 0.15f).coerceIn(0f, 1f)
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
                            // 与 KSU `AboutMiuix.kt:313` 一致：始终 tint，
                            // 模糊不产出像素时仍是一个正常着色的剪影（回退不是另一套实现）。
                            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onBackground),
                            modifier = Modifier
                                .requiredSize(MarkSampleSize)
                                .then(
                                    if (blurEnabled) {
                                        Modifier.textureBlur(
                                            backdrop = bgBackdrop,
                                            // 形状只裁"采样区域"；可见结果由 DstIn 的 alpha 决定。
                                            // 参考项目传 RoundedCornerShape(0.dp)，等价于矩形。
                                            shape = RectangleShape,
                                            // 150f 与 KSU 图标同值（库内部按 BlurDefaults.MaxBlurRadius=100 夹紧）。
                                            blurRadius = 150f,
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
                        text = "GIF 助手",
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .graphicsLayer {
                                val p = ((progressProvider() - 0.2f) / 0.15f).coerceIn(0f, 1f)
                                alpha = 1f - p
                                scaleX = 1f - p * 0.05f
                                scaleY = 1f - p * 0.05f
                            }
                            .then(
                                if (blurEnabled) {
                                    Modifier.textureBlur(
                                        backdrop = bgBackdrop,
                                        shape = RectangleShape,
                                        blurRadius = 150f,
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
                                val p = ((progressProvider() - 0.05f) / 0.15f).coerceIn(0f, 1f)
                                alpha = 1f - p
                            },
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                }

                // 列表盖在标题区之上，首项是一块透明占位，把卡片推到标题区下面。
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .pageScrollModifiers(scrollBehavior),
                    contentPadding = PaddingValues(
                        start = safeStart,
                        end = safeEnd,
                        top = listTop,
                        bottom = maxOf(innerPadding.calculateBottomPadding(), LocalBottomBarClearance.current) + 32.dp,
                    ),
                ) {
                    item(key = "logoSpacer") {
                        // 标题区高度 + 标题区比列表多出来的顶部留白，再给 20dp 呼吸。
                        Spacer(Modifier.height(headerHeight + (logoTopPadding - listTop) + 20.dp))
                    }
                    item(key = "about") {
                        GroupHeader("关于")
                        AboutCard {
                            // 入口不带前置图标、描述写在副标题里 —— 与 InstallerX 的
                            // `MiuixNavigationItemWidget`（内部就是 ArrowPreference）一致。
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
}

/**
 * 关于页的卡片。
 *
 * 底色取 `cardColor()` 再压到 0.82 不透明度：渐变背景能透一层上来。
 *
 * 这一处**故意不跟**参考项目用 `textureBlur`：参考项目整张卡（含两行入口）都由模糊层画出，
 * 而本机 GPU 上模糊层不产出像素时，卡片会连同两个入口一起消失 —— 那是功能性回退，
 * 不是观感问题。图标与标题可以等真机验证，入口必须一直可点。
 */
@Composable
private fun AboutCard(
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
        colors = CardDefaults.defaultColors(color = cardColor().copy(alpha = 0.82f)),
    ) {
        content()
    }
}
