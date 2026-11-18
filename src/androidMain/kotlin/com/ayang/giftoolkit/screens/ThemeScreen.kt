package com.ayang.giftoolkit.screens

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.ayang.giftoolkit.AppState
import com.ayang.giftoolkit.BackAnimation
import com.ayang.giftoolkit.BackExitDirection
import com.ayang.giftoolkit.ThemeMode
import com.ayang.giftoolkit.ui.DropdownRow
import com.ayang.giftoolkit.ui.GroupCard
import com.ayang.giftoolkit.BuildConfig
import com.ayang.giftoolkit.ui.GroupHeader
import com.ayang.giftoolkit.ui.Os4ListPage
import com.ayang.giftoolkit.ui.SwitchRow
import com.ayang.giftoolkit.ui.rememberGlassScope
import top.yukonga.miuix.kmp.preference.ArrowPreference

/**
 * 主题设置（独立页）。
 *
 * 结构参照 KernelSU / InstallerX 的主题页，但**去掉了它们的选择项**：
 * GIF 助手只有 Miuix 一套引擎、只做 OS4 一版 UI，不做 OS3 / 引擎切换，
 * 所以这里没有「UI 引擎」「Miuix UI 版本」两行。
 *
 * 预测性返回整组只在 API 34+ 显示：低版本系统没有这个交互，露出来只会让人困惑。
 */
@Composable
fun ThemeScreen(
    app: AppState,
    onBack: () -> Unit,
    /** 仅 debug 包：按钮配色对比页的入口回调（release 里恒为 null，那一组不渲染）。 */
    onOpenButtonColors: (() -> Unit)? = null,
) {
    val glass = rememberGlassScope()
    val modes = ThemeMode.entries
    val animations = BackAnimation.entries

    Os4ListPage(
        title = "主题设置",
        glass = glass,
        onBack = onBack,
        reserveNavBar = false,
        extraBottom = 24.dp,
    ) {
        item { GroupHeader("Miuix") }
        item {
            GroupCard {
                // 三档是离散值，用锚定下拉而不是分段控件：
                // 和系统「主题模式」的交互一致，也避免三个字长不一的分段里字被压扁。
                DropdownRow(
                    items = modes.map { it.label },
                    selectedIndex = modes.indexOf(app.themeMode).coerceAtLeast(0),
                    title = "主题模式",
                    onSelect = { app.themeMode = modes[it] },
                )
                SwitchRow(
                    title = "AMOLED 纯黑背景",
                    summary = "深色模式下页面用纯黑，卡片略亮",
                    checked = app.amoledEnabled,
                    onChange = { app.amoledEnabled = it },
                )
                // 原来叫"自定义颜色"，但这个功能并不自定义 —— 是让系统从壁纸取色。
                // 与系统设置里"动态取色"的叫法统一。
                SwitchRow(
                    title = "动态取色",
                    summary = "从壁纸提取配色（Monet）",
                    checked = app.monetEnabled,
                    onChange = { app.monetEnabled = it },
                )
            }
        }

        item { GroupHeader("界面") }
        item {
            GroupCard {
                // 只有一个开关：顶栏与底栏采样背景做模糊，关掉就是不透明胶囊 + 渐变遮罩。
                // 曾经试过三档、甚至按组件分三组，都不如一个开关 ——
                // 中间档属于库的渲染路径，不该让用户去理解。
                SwitchRow(
                    title = "玻璃材质",
                    summary = "顶栏与底栏使用模糊玻璃",
                    checked = app.glassEnabled,
                    onChange = { app.glassEnabled = it },
                )
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            item { GroupHeader("预测性返回") }
            item {
                GroupCard {
                    DropdownRow(
                        items = animations.map { it.label },
                        selectedIndex = animations.indexOf(app.backAnimation).coerceAtLeast(0),
                        title = "预测性返回动画",
                        summary = app.backAnimation.summary,
                        onSelect = { app.backAnimation = animations[it] },
                    )
                    // 退出方向只对「缩放」有意义：其余档位要么跟随手势物理方向，
                    // 要么根本没有横向位移，给了选项也不会生效。
                    if (app.backAnimation == BackAnimation.Scale) {
                        val directions = BackExitDirection.entries
                        DropdownRow(
                            items = directions.map { it.label },
                            selectedIndex = directions.indexOf(app.backExitDirection).coerceAtLeast(0),
                            title = "退出方向",
                            onSelect = { app.backExitDirection = directions[it] },
                        )
                    }
                }
            }
        }

        // 两道门，都是编译期可折叠的：
        //   - `BuildConfig.DEBUG_UI` 在 release 里被内联成 false（Kotlin 对 Java 编译期常量会内联），
        //     于是 R8 把整块连同里面的文案字符串一起删掉；
        //   - `onOpenButtonColors != null` 是给调用方的契约（release 传 null）。
        // 只留后者时文案字符串仍会留在 dex 里（R8 不会为了一个 null 判断去内联整个页面），
        // 验收方式是"release dex 里搜不到这些字"（docs/os4-round10-audit.md §8.3）。
        if (BuildConfig.DEBUG_UI && onOpenButtonColors != null) {
            item { GroupHeader("调试（仅调试包可见）") }
            item {
                GroupCard {
                    ArrowPreference(
                        title = "开始按钮配色对比",
                        summary = "无素材禁用态 A / B / C / D 四种配色的真实按钮",
                        onClick = onOpenButtonColors,
                    )
                }
            }
        }
    }
}
