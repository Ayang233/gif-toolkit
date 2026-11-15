package com.ayang.giftoolkit.data

import android.content.Context
import com.ayang.giftoolkit.BackAnimation
import com.ayang.giftoolkit.BackExitDirection
import com.ayang.giftoolkit.ThemeMode
import com.ayang.giftoolkit.ui.SurfaceTier

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

    /**
     * 三个组件的材质档位。
     *
     * 粒度和 HyperOS「桌面设置里 Dock 与搜索栏各自配质感」一致：
     * 用户可能想要顶栏玻璃、但觉得弹层模糊太花。
     * 默认都是 [SurfaceTier.Glass]，实际渲染前会被设备能力钳制一次。
     */
    var topBarTier: SurfaceTier
        get() = readTier(KEY_TOP_TIER)
        set(value) {
            prefs.edit().putString(KEY_TOP_TIER, value.name).apply()
        }

    var navBarTier: SurfaceTier
        get() = readTier(KEY_NAV_TIER)
        set(value) {
            prefs.edit().putString(KEY_NAV_TIER, value.name).apply()
        }

    var panelTier: SurfaceTier
        get() = readTier(KEY_PANEL_TIER)
        set(value) {
            prefs.edit().putString(KEY_PANEL_TIER, value.name).apply()
        }

    private fun readTier(key: String): SurfaceTier = prefs.getString(key, null)
        ?.let { saved -> SurfaceTier.entries.firstOrNull { it.name == saved } }
        ?: SurfaceTier.Glass

    /** AMOLED 纯黑：深色模式下页面底用纯黑，省电且和 HyperOS 的 AMOLED 开关一致。 */
    var amoledEnabled: Boolean
        get() = prefs.getBoolean(KEY_AMOLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_AMOLED, value).apply()
        }

    var backAnimation: BackAnimation
        get() = prefs.getString(KEY_BACK_ANIM, null)
            ?.let { saved -> BackAnimation.entries.firstOrNull { it.name == saved } }
            ?: BackAnimation.Miuix
        set(value) {
            prefs.edit().putString(KEY_BACK_ANIM, value.name).apply()
        }

    var backExitDirection: BackExitDirection
        get() = prefs.getString(KEY_BACK_DIRECTION, null)
            ?.let { saved -> BackExitDirection.entries.firstOrNull { it.name == saved } }
            ?: BackExitDirection.FollowGesture
        set(value) {
            prefs.edit().putString(KEY_BACK_DIRECTION, value.name).apply()
        }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_MONET = "monet_enabled"
        const val KEY_GLASS = "glass_enabled"
        const val KEY_AMOLED = "amoled_enabled"
        const val KEY_BACK_ANIM = "back_animation"
        const val KEY_BACK_DIRECTION = "back_exit_direction"
        const val KEY_TOP_TIER = "surface_tier_top"
        const val KEY_NAV_TIER = "surface_tier_nav"
        const val KEY_PANEL_TIER = "surface_tier_panel"
    }
}
