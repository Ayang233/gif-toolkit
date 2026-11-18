package com.ayang.giftoolkit.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.graphicsLayer
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
import com.ayang.giftoolkit.ui.PageTopBar
import com.ayang.giftoolkit.ui.bg.BgEffectBackground
import com.ayang.giftoolkit.ui.bg.accentBrush
import com.ayang.giftoolkit.ui.bg.gradientFilled
import com.ayang.giftoolkit.ui.bg.rememberBgPalette
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
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 源代码地址。为空时那一行照常显示、点击提示"尚未公开"，不会出现死链。 */
private const val SOURCE_URL = "https://github.com/Ayang233/gif-toolkit"

/**
 * 关于页。
 *
 * 版式对齐 InstallerX Revived 的 Miuix 关于页（`MiuixAboutPage.kt`，26.11.caccb78）
 * 与 MIUIX 官方示例 `AboutPage.kt`：整块 OS3 动态渐变 + 大图标 + 大标题 + 版本号，
 * 滚动时这三块按不同斜率淡出并缩到 0.95；下面是一张「关于」卡片。
 *
 * 三处按参考应用固定的写法：
 *
 * 1. **入口行不带前置图标**：InstallerX 用的是 `ArrowPreference(title, summary)`（无 `startAction`），
 *    描述写在副标题里（「在 GitHub 上查看源代码」）。这里照抄，不再塞 `Glass.Light.Link` 这类小图标。
 * 2. **图标与软件名称跟随背景渐变**：参考实现是
 *    `textureBlur(backdrop, colors = BlurColors(blendColors = …), contentBlendMode = DstIn)` ——
 *    采样已渲染的背景、用混合色叠一层，再裁进字形。本机 GPU 上 `textureBlur` 静默不产出像素
 *    （官方示例 APK 同一台模拟器上关于页干脆没有标题、没有卡片，见 `_archive/round8/off_about3.png`），
 *    所以这里改用与背景**同一套色段**的渐变 + `SrcIn` 掩膜（`ui/bg` 的 `gradientFilled`）：
 *    颜色与相位同源，不依赖 AGSL，任何 GPU 都有像素。
 * 3. **只有一个许可入口**：本项目自身许可证与第三方组件许可合成一页（[LicensesScreen]），
 *    关于页不再分「开源许可 / 第三方许可」两个入口。
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    val context = LocalContext.current
    // 录制层不垫页面底色：这一页的背景是整块渐变，垫了就把渐变盖住了（见 rememberPageBackdrop）。
    val glass = rememberGlassScope(opaqueBase = false)
    // 注意：渐变**不能**再套一层 `layerBackdrop` 当顶栏采样源。
    // 试过在页面根部录一条满屏层给顶栏用，结果顶栏成了录制层的**子节点**（Scaffold 在它里面），
    // 采样到自己 → `RenderNode::prepareTreeImpl` 原生栈溢出闪退（logcat 见
    // `_archive/round8/crash_about_libhwui.txt`）。录制层只能是消费者的兄弟节点（AGENTS.md 第 1 条）。
    val version = rememberAppVersion()
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    // 背景与"跟随背景"的图标/标题共用一份配色状态：一个色段、一套相位。
    val palette = rememberBgPalette()
    val markBrush = { palette.takeIf { it.gradientAvailable }?.accentBrush() }

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
            palette = palette,
            alpha = { 1f - progressProvider() },
        ) {}

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
            topBar = {
                PageTopBar(
                    title = "关于",
                    glass = glass,
                    isScrolled = collapsed,
                    scrollBehavior = scrollBehavior,
                    onBack = onBack,
                    expandable = false,
                    titleAlpha = { ((progressProvider() - 0.35f) / 0.65f).coerceIn(0f, 1f) },
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
                    // 图标：底板 + 跟随背景色段的剪影（参考 KSU / InstallerX 的做法，见文件头）。
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(88.dp)
                            // 官方是 88dp 图标 + 24dp 圆角；这里用连续曲率裁剪，和卡片圆角同一套观感。
                            .squircleClip(24.dp)
                            .background(cardColor().copy(alpha = 0.72f))
                            .graphicsLayer {
                                val p = ((progressProvider() - 0.35f) / 0.15f).coerceIn(0f, 1f)
                                alpha = 1f - p
                                scaleX = 1f - p * 0.05f
                                scaleY = 1f - p * 0.05f
                            },
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_app_mark),
                            contentDescription = null,
                            modifier = Modifier
                                .size(88.dp)
                                .gradientFilled(markBrush),
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
                            // 渐变填充靠字形 alpha：颜色本身会被 SrcIn 替换掉，这里给实白即可。
                            .gradientFilled(markBrush),
                        color = Color.White,
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
 * 底色取 `cardColor()` 再压到 0.82 不透明度：渐变背景能透一层上来，
 * 又不像官方那样把整张卡的底色交给 `textureBlur`（那条路径在部分 GPU 上不产出像素，见文件头）。
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
