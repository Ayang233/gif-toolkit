package com.ayang.giftoolkit.data

import android.content.Context
import com.ayang.giftoolkit.ThemeMode

/**
 * 外观设置的持久层。
 *
 * 之前这些开关只活在 `AppState` 里（`remember {}`），进程一死就回到默认值 ——
 * 用户看到的现象是「设置完退出重进就失效」。这里用 SharedPreferences 落盘，
 * 启动时同步读取，改动即写。
 *
 * 只存三个纯外观开关，不放业务数据；没有引入 DataStore 是为了不额外拉协程/序列化依赖。
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("gifhelper_settings", Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = prefs.getString(KEY_THEME, null)
            ?.let { saved -> ThemeMode.entries.firstOrNull { it.name == saved } }
            ?: ThemeMode.System
        set(value) {
            prefs.edit().putString(KEY_THEME, value.name).apply()
        }

    /**
     * 动态取色默认**关闭**。
     *
     * 打开后整套配色会从壁纸重新生成，观感更接近 Material You —— 主色变成青绿/紫，
     * `surface` 与 `surfaceContainer` 还会被映射成同一个值，卡片于是糊进页面背景。
     * Miuix 的本体是静态色（主色 #3482FF、页面 #F7F7F7、卡片纯白），
     * 所以默认走 Miuix 原生配色，想要 Monet 的用户可以在设置里自己开。
     */
    var monetEnabled: Boolean
        get() = prefs.getBoolean(KEY_MONET, false)
        set(value) {
            prefs.edit().putBoolean(KEY_MONET, value).apply()
        }

    var glassEnabled: Boolean
        get() = prefs.getBoolean(KEY_GLASS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_GLASS, value).apply()
        }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_MONET = "monet_enabled"
        const val KEY_GLASS = "glass_enabled"
    }
}
