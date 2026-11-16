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
import com.ayang.giftoolkit.ui.GroupHeader
import com.ayang.giftoolkit.ui.Os4ListPage
import com.ayang.giftoolkit.ui.SwitchRow
import com.ayang.giftoolkit.ui.rememberGlassScope

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
                    summary = "深色模式下页面底用纯黑，卡片仍比页面亮一档",
                    checked = app.amoledEnabled,
                    onChange = { app.amoledEnabled = it },
                )
                SwitchRow(
                    title = "自定义颜色",
                    summary = "用 Monet 从壁纸生成整套配色",
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
    }
}
