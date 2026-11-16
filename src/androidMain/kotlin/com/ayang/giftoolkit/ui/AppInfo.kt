package com.ayang.giftoolkit.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** 「1.3（5）」这种版本串，关于页和设置页副标题共用一处，避免两处格式不一致。 */
@Composable
fun rememberAppVersion(): String {
    val context: Context = LocalContext.current
    return remember(context) {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName}（${info.longVersionCode}）"
        }.getOrDefault("—")
    }
}
