package com.ayang.giftoolkit

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import com.ayang.giftoolkit.data.SettingsStore
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * 应用入口只做一件事：把主题设置里的「预测性返回」选择写回 [ApplicationInfo]。
 *
 * 系统级的预测性返回由 manifest 的 `enableOnBackInvokedCallback` 控制，
 * 它没有公开的运行时 API；`ApplicationInfo.setEnableOnBackInvokedCallback` 是隐藏方法，
 * 所以要走 HiddenApiBypass 解开豁免再反射调用（KernelSU 也是这么做的）。
 *
 * 三个前提，缺一不可：
 * 1. API 34+ —— 这个隐藏方法只在那之后存在；
 * 2. 豁免能加上 —— 部分 ROM 会拦；
 * 3. **必须在本进程的 Activity 起来之前调用**，否则本次启动不会生效。
 *    所以放在 `Application.onCreate`，而不是设置页里。
 *
 * 任何一步失败都只是"开关没生效"，不能影响应用启动，因此整段 runCatching。
 */
class GifApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val wanted = SettingsStore(this).backAnimation != BackAnimation.None
        applyPredictiveBack(wanted)
    }

    private fun applyPredictiveBack(enabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        runCatching {
            HiddenApiBypass.addHiddenApiExemptions(
                "Landroid/content/pm/ApplicationInfo;->setEnableOnBackInvokedCallback",
            )
            val method = ApplicationInfo::class.java.getDeclaredMethod(
                "setEnableOnBackInvokedCallback",
                Boolean::class.javaPrimitiveType,
            )
            method.isAccessible = true
            method.invoke(applicationInfo, enabled)
        }.onFailure { e ->
            Log.w(TAG, "预测性返回开关未能应用（系统或 ROM 限制）：${e.message}")
        }
    }

    private companion object {
        const val TAG = "GifPredictiveBack"
    }
}
