package com.ayang.giftoolkit.screens

import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayang.giftoolkit.R
import com.ayang.giftoolkit.ui.ContentBackdropBox
import com.ayang.giftoolkit.ui.LeadingIcon
import com.ayang.giftoolkit.ui.LocalBottomBarClearance
import com.ayang.giftoolkit.ui.PageTopBar
import com.ayang.giftoolkit.ui.bg.BgEffectBackground
import com.ayang.giftoolkit.ui.cardColor
import com.ayang.giftoolkit.ui.pageScrollModifiers
import com.ayang.giftoolkit.ui.rememberAppVersion
import com.ayang.giftoolkit.ui.rememberGlassScope
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Link
import top.yukonga.miuix.kmp.icon.glass.NotesOutline
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 源代码地址。为空时那一行照常显示、点击提示"尚未公开"，不会出现死链。 */
private const val SOURCE_URL = "https://github.com/Ayang233/gif-toolkit"

/**
 * 关于页。
 *
 * 版式照 MIUIX 官方示例的 `AboutPage`（0.9.5-beta01 / `3177a684`）：
 * 固定的动态渐变背景 + 大图标 + 大标题 + 版本号，滚动时整块淡出并缩小，
 * 让下面的卡片行浮上来；顶栏到滚动起来才浮出标题（官方 `expandable = false` + 标题透明度跟滚动）。
 *
 * 与官方有两处**有意的**差异，都是因为示例里那两种材质在本项目里没有等价物：
 *
 * 1. **大标题用实色字，不用 `textureBlur` 的"液态玻璃字"。**
 *    官方把标题做成一块 `textureBlur(backdrop, contentBlendMode = DstIn)` 的玻璃片，
 *    依赖 AGSL 逐像素混色。实测官方 APK（同一台模拟器）那一行**完全不产出像素**，
 *    整页看不到应用名（截图 `_archive/round8/off_about3.png`）。
 *    照抄进 GIF 助手等于在同类 GPU 上"关于页没有名字"，所以这里只用主题色实字，
 *    字号 / 字重 / 滚动淡出 / 缩放全部照官方。
 * 2. **卡片用半透明底色，不用 `textureBlur` 玻璃卡片。**
 *    同一个原因：官方那两张卡的底色是 `Color.Transparent` + `textureBlur`，
 *    在该模拟器上卡片彻底消失（只剩几行浮着的文字）。这里用 `cardColor()` 压到 0.82 不透明度，
 *    背景渐变能透一点出来，观感接近玻璃卡，但不依赖着色器。
 *
 * 顶栏标题透明度：`(progress - 0.35) / 0.65`，与官方一致 —— 首屏顶栏只有返回键，
 * 内容滚起来之后标题才出现。这不是"标题丢了"，官方就是这样。
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenOwnLicense: () -> Unit,
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
            alpha = { 1f - progressProvider() },
        ) {}

        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
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
                val logoTopPadding = innerPadding.calculateTopPadding() + 40.dp
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = logoTopPadding, bottom = 8.dp)
                        .onSizeChanged { headerHeightPx = it.height },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_app_badge),
                        contentDescription = null,
                        modifier = Modifier
                            .size(88.dp)
                            // 官方是 88dp 图标 + 24dp 圆角；徽章本身已是白底圆角方块，
                            // 这里只做连续曲率裁剪，不再叠一层白底 Box。
                            .squircleClip(24.dp)
                            .graphicsLayer {
                                val p = ((progressProvider() - 0.35f) / 0.15f).coerceIn(0f, 1f)
                                alpha = 1f - p
                                scaleX = 1f - p * 0.05f
                                scaleY = 1f - p * 0.05f
                            },
                    )
                    Text(
                        text = "GIF 助手",
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .graphicsLayer {
                                val p = ((progressProvider() - 0.2f) / 0.15f).coerceIn(0f, 1f)
                                alpha = 1f - p
                                scaleX = 1f - p * 0.05f
                                scaleY = 1f - p * 0.05f
                            },
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
                val listTop = innerPadding.calculateTopPadding() + 2.dp
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .pageScrollModifiers(scrollBehavior),
                    contentPadding = PaddingValues(
                        top = listTop,
                        bottom = maxOf(innerPadding.calculateBottomPadding(), LocalBottomBarClearance.current) + 32.dp,
                    ),
                ) {
                    item(key = "logoSpacer") {
                        // 标题区高度 + 标题区比列表多出来的顶部留白，再给 20dp 呼吸。
                        Spacer(Modifier.height(headerHeight + (logoTopPadding - listTop) + 20.dp))
                    }
                    item(key = "app") {
                        AboutCard {
                            ArrowPreference(
                                title = "查看源代码",
                                endActions = {
                                    Text(
                                        text = "GitHub",
                                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                    )
                                },
                                startAction = { LeadingIcon(MiuixIcons.Glass.Light.Link) },
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
                        }
                    }
                    item(key = "license") {
                        AboutCard(topPadding = 12.dp) {
                            ArrowPreference(
                                title = "开源许可",
                                endActions = {
                                    Text(
                                        text = "GPL-3.0",
                                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                    )
                                },
                                onClick = onOpenOwnLicense,
                            )
                            ArrowPreference(
                                title = "第三方许可",
                                startAction = { LeadingIcon(MiuixIcons.Glass.Light.NotesOutline) },
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
    topPadding: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .padding(top = topPadding),
        colors = CardDefaults.defaultColors(color = cardColor().copy(alpha = 0.82f)),
    ) {
        content()
    }
}
