package com.ayang.giftoolkit.ui

/**
 * 许可清单。关于页只有一个「开源许可」入口，进来是这一页，按分组列出：
 *
 * 1. **本项目** —— GifToolkit自身的 GPL-3.0 正文；
 * 2. **第三方组件** —— 依赖清单，全部 Apache-2.0（正文只有一份，
 *    取自 Miuix 仓库的 LICENSE，不是手抄的）；
 * 3. **归属** —— 仓库根目录的 `NOTICE`（含 Miuix 示例代码的改编归属）。
 *
 * [licenseEntries] 的下标就是正文页的路由参数（`Route.License(index)`），
 * 不再为"本项目 / 第三方"各开一个 Route —— 那正是上一轮两个入口的由来。
 */
data class LicenseEntry(
    val name: String,
    val owner: String,
    val license: String = "Apache-2.0",
    val asset: String = "licenses/apache-2.0.txt",
)

/** 分组：同一组内的行连成一张卡片。 */
data class LicenseGroup(
    val title: String,
    val entries: List<LicenseEntry>,
)

/** 本项目自身许可证（GPL-3.0 全文）。 */
val projectLicense = LicenseEntry(
    name = "GifToolkit",
    owner = "gif-toolkit contributors",
    license = "GPL-3.0",
    asset = "licenses/gpl-3.0.txt",
)

/** 归属声明与改编说明（NOTICE）。 */
val noticeEntry = LicenseEntry(
    name = "NOTICE 与归属声明",
    owner = "gif-toolkit contributors",
    license = "声明",
    asset = "licenses/notice.txt",
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

/** 页面上的分组顺序。 */
val licenseGroups: List<LicenseGroup> = listOf(
    LicenseGroup("本项目", listOf(projectLicense)),
    LicenseGroup("第三方组件", thirdPartyLibraries),
    LicenseGroup("归属", listOf(noticeEntry)),
)

/** 扁平化后的清单，`Route.License(index)` 用它取正文。 */
val licenseEntries: List<LicenseEntry> = licenseGroups.flatMap { it.entries }
