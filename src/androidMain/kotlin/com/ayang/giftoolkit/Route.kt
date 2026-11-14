package com.ayang.giftoolkit

import top.yukonga.miuix.kmp.nav.core.NavKey

/**
 * 四个目的地：根页面（内含三个 tab）、工具页、关于页、开源许可（列表 / 全文）。
 *
 * 关于页与许可是 2.1 新增：设置里不再把「版本 / 隐私 / 导出位置」摊在同一页，
 * 而是收成一行入口，点进去才是独立的关于页（参考 KernelSU 的做法）。
 *
 * 没有用 `@Serializable` + rememberNavBackStack：进程死亡后的栈恢复留到后续版本。
 */
sealed interface Route : NavKey {
    data object Root : Route
    data class Tool(val id: String) : Route
    data object About : Route
    data object Licenses : Route
    data class License(val index: Int) : Route
}
