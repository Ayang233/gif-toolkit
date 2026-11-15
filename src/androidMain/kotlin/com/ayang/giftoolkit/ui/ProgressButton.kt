// 移植自 compose-miuix-ui 生态的 ProgressButton（InstallerX Revived
// `ui/page/miuix/widgets/ProgressButton.kt`，该文件头部声明 Apache-2.0，
// 版权归 compose-miuix-ui contributors）。本工程整体为 GPL-3.0，与 Apache-2.0 兼容。
// 改动：仅保留本项目用得到的参数，未改绘制逻辑。

package com.ayang.giftoolkit.ui

import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 自带进度填充的按钮。
 *
 * 关键在于「轨道是按钮本身、填充画在 shape 内部」：
 * 用 `drawWithContent` 先铺一条 `width * progress` 的矩形再画内容，
 * 矩形会被 [Surface] 的 shape 裁掉，所以填充两端自然跟随圆角，
 * 不会出现直角切口 —— 之前那版把填充做成 `Box.fillMaxWidth(p)` 的子节点，
 * 既被父 Box 的 `contentAlignment = Center` 居中，又不受圆角约束，才变成中间一块方块。
 *
 * @param progress 0f..1f
 * @param colors 轨道 / 填充 / 文字三色，文字色建议随进度切换（见 [progressButtonColors]）
 */
@Composable
@NonRestartableComposable
fun ProgressButton(
    progress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cornerRadius: Dp = 16.dp,
    minHeight: Dp = 50.dp,
    colors: ProgressButtonColors = ProgressButtonDefaults.progressButtonColors(),
    insideMargin: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    interactionSource: MutableInteractionSource? = null,
    indication: Indication? = LocalIndication.current,
    content: @Composable RowScope.() -> Unit,
) {
    val progressCoerced = progress.coerceIn(0f, 1f)
    val shape = RoundedCornerShape(cornerRadius)
    val trackColor = if (enabled) colors.trackColor else colors.disabledTrackColor
    val progressColor = if (enabled) colors.progressColor else colors.disabledProgressColor
    val contentColor = if (enabled) colors.contentColor else colors.disabledContentColor

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.semantics { role = Role.Button },
        shape = shape,
        color = trackColor,
        contentColor = contentColor,
        interactionSource = interactionSource,
        indication = indication,
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minWidth = ButtonDefaults.MinWidth, minHeight = minHeight)
                .drawWithContent {
                    if (progressCoerced > 0f) {
                        drawRect(
                            color = progressColor,
                            size = Size(width = size.width * progressCoerced, height = size.height),
                        )
                    }
                    drawContent()
                }
                .padding(insideMargin),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** 进度按钮配色。 */
@Immutable
data class ProgressButtonColors(
    val trackColor: Color,
    val progressColor: Color,
    val contentColor: Color,
    val disabledTrackColor: Color,
    val disabledProgressColor: Color,
    val disabledContentColor: Color,
)

object ProgressButtonDefaults {

    @Composable
    fun progressButtonColors(
        trackColor: Color = MiuixTheme.colorScheme.secondaryVariant,
        progressColor: Color = MiuixTheme.colorScheme.primary,
        contentColor: Color = MiuixTheme.colorScheme.onPrimary,
        disabledTrackColor: Color = MiuixTheme.colorScheme.disabledSecondaryVariant,
        disabledProgressColor: Color = MiuixTheme.colorScheme.disabledPrimaryButton,
        disabledContentColor: Color = MiuixTheme.colorScheme.disabledOnPrimaryButton,
    ): ProgressButtonColors = remember(
        trackColor,
        progressColor,
        contentColor,
        disabledTrackColor,
        disabledProgressColor,
        disabledContentColor,
    ) {
        ProgressButtonColors(
            trackColor = trackColor,
            progressColor = progressColor,
            contentColor = contentColor,
            disabledTrackColor = disabledTrackColor,
            disabledProgressColor = disabledProgressColor,
            disabledContentColor = disabledContentColor,
        )
    }
}
