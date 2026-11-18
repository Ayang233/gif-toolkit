package com.ayang.giftoolkit

import top.yukonga.miuix.kmp.nav.core.NavKey

/**
 * 目的地：根页面（内含三个 tab）、工具页、主题设置、关于页、开源许可（统一入口 + 正文）。
 *
 * 主题设置是独立页：设置页只留一行入口，和 KernelSU / InstallerX 的组织方式一致。
 *
 * **许可只有一个入口**：`Licenses` 一页里按「本项目 / 第三方组件 / 归属」分组，
 * `License(index)` 的 index 是 `ui/LicenseCatalog.kt` 里扁平清单的下标。
 * 上一轮那个只承载 GPL-3.0 的 `OwnLicense` 已合并进来，不再保留 ——
 * 两个入口、两个页面、内容各占一处，用户要多点一次才能看全。
 *
 * 没有用 `@Serializable` + rememberNavBackStack：进程死亡后的栈恢复留到后续版本。
 */
sealed interface Route : NavKey {
    data object Root : Route
    data class Tool(val id: String) : Route
    data object Theme : Route
    data object About : Route

    /** 统一的许可列表：本项目许可证 + 第三方组件 + NOTICE。 */
    data object Licenses : Route

    /** 许可正文，index 指向 `licenseEntries`。 */
    data class License(val index: Int) : Route

    /**
     * **仅 debug 包**：开始按钮禁用态四种配色的实机对比页。
     *
     * 只在 `ApplicationInfo.FLAG_DEBUGGABLE` 为真时注册（`App.kt`）与显示入口（`ThemeScreen`），
     * release 包里既没有入口也进不来。目的只是让四种方案并排看效果，不属于产品界面。
     */
    data object ButtonColors : Route
}
