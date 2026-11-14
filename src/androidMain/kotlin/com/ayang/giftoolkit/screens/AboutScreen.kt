package com.ayang.giftoolkit.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ayang.giftoolkit.R
import com.ayang.giftoolkit.ui.GroupCard
import com.ayang.giftoolkit.ui.GroupHeader
import com.ayang.giftoolkit.ui.LeadingIcon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.preference.ArrowPreference
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
    val scrollBehavior = MiuixScrollBehavior()
    val version = remember {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName}（${info.longVersionCode}）"
        }.getOrDefault("—")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "关于",
                largeTitle = "关于",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        top.yukonga.miuix.kmp.basic.Icon(
                            MiuixIcons.Back,
                            "返回",
                            Modifier.size(24.dp),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
        ) {
            // 顶部：图标 + 名称 + 版本，居中摆在页面底色上（不进卡片），与内核管理器的关于页同构
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher),
                        contentDescription = null,
                        modifier = Modifier.size(76.dp).clip(RoundedCornerShape(20.dp)),
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
                        startAction = { LeadingIcon(MiuixIcons.Link) },
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
                        summary = "本应用使用的第三方组件",
                        startAction = { LeadingIcon(MiuixIcons.Notes) },
                        onClick = onOpenLicenses,
                    )
                }
            }
        }
    }
}
