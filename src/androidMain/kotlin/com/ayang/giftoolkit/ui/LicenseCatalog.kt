package com.ayang.giftoolkit.ui

/**
 * 第三方组件清单，供「关于 → 开源许可」使用。
 *
 * 全部是 Apache-2.0 —— 正文只有一份，放在 `assets/licenses/apache-2.0.txt`
 * （直接取自 Miuix 仓库的 LICENSE，不是手抄的）。新增依赖时在这里补一行即可。
 */
data class LicenseEntry(
    val name: String,
    val owner: String,
    val license: String = "Apache-2.0",
    val asset: String = "licenses/apache-2.0.txt",
)

val thirdPartyLibraries = listOf(
    LicenseEntry("Miuix", "compose-miuix-ui contributors"),
    LicenseEntry("Miuix Icons", "compose-miuix-ui contributors"),
    LicenseEntry("Kotlin / Kotlin Multiplatform", "JetBrains"),
    LicenseEntry("Compose Multiplatform", "JetBrains"),
    LicenseEntry("Jetpack Compose", "The Android Open Source Project"),
    LicenseEntry("AndroidX Activity / Core / Lifecycle", "The Android Open Source Project"),
    LicenseEntry("kotlinx.coroutines", "JetBrains"),
)
