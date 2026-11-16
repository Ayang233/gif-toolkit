package com.ayang.giftoolkit.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ayang.giftoolkit.R
import com.ayang.giftoolkit.ui.GroupCard
import com.ayang.giftoolkit.ui.GroupHeader
import com.ayang.giftoolkit.ui.LeadingIcon
import com.ayang.giftoolkit.ui.Os4ListPage
import com.ayang.giftoolkit.ui.rememberAppVersion
import com.ayang.giftoolkit.ui.rememberGlassScope
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Link
import top.yukonga.miuix.kmp.icon.glass.NotesOutline
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 源代码地址。
 *
 * 仓库地址。为空时按钮照常显示、点击提示"尚未公开"，不会出现死链。
 */
private const val SOURCE_URL = "https://github.com/Ayang233/gif-toolkit"

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    val context = LocalContext.current
    val glass = rememberGlassScope()
    val version = rememberAppVersion()

    Os4ListPage(
        title = "关于",
        glass = glass,
        onBack = onBack,
        reserveNavBar = false,
        extraBottom = 32.dp,
    ) {
        // 顶部：图标 + 名称 + 版本，居中摆在页面底色上（不进卡片），与内核管理器的关于页同构
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_app_badge),
                    contentDescription = null,
                    // 用库的 squircleClip 而不是 clip(RoundedCornerShape(20.dp))：
                    // OS4 的图标圆角是连续曲率（四角不是正圆弧），普通圆角在大尺寸下能看出
                    // "角上有点方"。squircleClip 在不支持 RuntimeShader 的设备上内部自动退回
                    // 普通圆角，不需要调用方判断（SquircleBackground.kt:108）。
                    modifier = Modifier.size(76.dp).squircleClip(20.dp),
                )
                Text("GIF 助手", style = MiuixTheme.textStyles.title3)
                Text(
                    text = version,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }

        item { GroupHeader("应用") }
        item {
            GroupCard {
                ArrowPreference(
                    title = "查看源代码",
                    summary = SOURCE_URL.ifBlank { "仓库尚未公开" },
                    startAction = { LeadingIcon(MiuixIcons.Glass.Light.Link) },
                    onClick = {
                        if (SOURCE_URL.isBlank()) {
                            Toast.makeText(context, "源代码仓库还没公开", Toast.LENGTH_SHORT).show()
                        } else {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(SOURCE_URL),
                                    ),
                                )
                            }
                        }
                    },
                )
                ArrowPreference(
                    title = "开源许可",
                    startAction = { LeadingIcon(MiuixIcons.Glass.Light.NotesOutline) },
                    onClick = onOpenLicenses,
                )
            }
        }
    }
}
