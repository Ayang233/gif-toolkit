# Miuix 基线 `3c560868` → `e7c108ee` 差异分析（面向 GifToolkit 1.16）

- 起点：`3c560868`（`v0.9.5-beta04`，2026-11-24）
- 终点：`e7c108ee`（`v0.9.5-rc01` + 10 个提交，2026-11-26，`example: Add new icon for iOS`）
- 区间：**22 个提交**，其中 library 相关 16 个、example/docs 6 个
- 工具链：**AGP 9.5.2 → 9.5.3**（`6d784f19`），Kotlin 2.4.21 / CMP 1.13.0-rc01 / Gradle 9.9.2 / compileSdk 37.2 不变

对照方式：`git diff 3c560868..e7c108ee -- "miuix-*/src/*"`，再逐条落到本项目的调用点。

## 1. 结论摘要

| # | 变化 | 对 GifToolkit 的影响 | 处理 |
| --- | --- | --- | --- |
| 1 | `GlassTopAppBarDefaults.BandOverhang` 36dp → 32dp → **10dp**（`ae3be934` / `f633ad2c`） | 顶栏非渐进档的渐变带明显变薄（`fadeHeight = bandOverhang + 36dp`，72dp → 46dp） | 吃默认值，不覆盖；观感属官方行为，验收时看一眼 |
| 2 | `GlassTopAppBarDefaults.ProgressiveBlurRadius` 80dp → **32dp**，渐进曲线改成线性 raw（`8d3d0bb4`） | 顶栏渐进模糊带更窄、渐隐更线性 | 吃默认值 |
| 3 | 顶栏新增 `bottomContent` / `progressiveStartFraction`（`8d3d0bb4`） | 本项目没有 `bottomContent` | 无影响 |
| 4 | 非渐进档的带子改为从条底往上淡，接缝消除（`e5338080`） | AGSL 不可用时的回退路径观感变化 | 无需改代码 |
| 5 | 顶栏/弹层顶部加 smooth scrim（`c217b445`）、`heightOffset` 夹紧（`d2e52284`） | 顶栏顶部不再闪、不再有高对比横带 | 无需改代码 |
| 6 | `GlassBottomSheet` 新增 `progressiveBlur`（默认 **false**）、`startAction`/`endAction`/`headerBottomContent`、`content` 收 `PaddingValues`（`c262ff7c` / `6aa83241`） | 作品页操作面板没传该参数 → 头部回到 body 之上、头部 padding 为 0dp（上一版头部是叠在内容上的） | 保持官方默认；`content` 参数已兼容（lambda 无参仍可写） |
| 7 | `GlassTabRow` 新增 `GlassScrollableTabRow` + `MinTabWidth`（`04e9b818`） | 本项目没有页签 | 无影响，将来加页签时用 |
| 8 | 下拉弹层加非交互滚动指示条（`588d029f`，`ListPopup.kt`） | 本项目下拉 3~5 项，不触发 | 无影响 |
| 9 | `GlassCard` / `GlassDialogButton` 的 Monet 混色重写（`a09d4f32`） | 关于页卡片、删除确认框在 Monet 明/暗下颜色变化 | 需四套配色复核 |
| 10 | `GlassIconButton` 改读新的 `LocalGlassHeaderContext`（顶栏与弹层共用） | 调用点签名不变 | 无影响 |
| 11 | `miuix-blur` 曲线与掩膜阈值重写（`8d3d0bb4`、`DrawBackdropModifier`、`BlurEffect`） | 所有模糊的像素结果变化（顶栏、底栏、Rail、关于页图标/标题） | 无需改代码，需真机/模拟器看一眼 |
| 12 | `ScrollBar` 新增 `interactive` 参数（`588d029f`） | 本项目用具名参数调用 | 无影响 |
| 13 | `b5d2d698` / `0f2f05a9` / `03d15786`：预测性返回状态复位、弹层上滑时表面延伸、锚点按钮回弹 | 弹层/返回手感 | 无需改代码 |

## 2. 编译面

- 用 `3c560868` 的全部 import 与调用点在 `e7c108ee` 上**直接编译通过**（`assembleDebug` 成功），没有删除/重命名公开 API。
- 唯一必须动的构建项是 **AGP 版本**：composite build 里两个构建只允许一个 AGP，
  上游 `gradle/libs.versions.toml` 已到 `9.5.3`，`build.gradle.kts` 必须同步，否则依赖解析期报
  `Using multiple versions of the Android Gradle plugin (9.5.3, 9.5.2) ...`。

## 3. 本项目"吃默认值"的耦合点复查

| 位置 | 依赖的上游默认值 | 本次是否变化 |
| --- | --- | --- |
| `ui/GlassScope.kt` `GlassTopAppBar` | `BandOverhang` / `ProgressiveBlurRadius` / 曲线 | **变了**（见 §1 #1/#2） |
| `ui/PageChrome.kt` 的底栏结构 | `GlassNavigationBarDefaults.Height` 54dp、Rail 250+12dp | 未变 |
| `App.kt` `PageNavigationBar` | 底栏 `defaultWindowInsetsPadding` / 槽位高度 | 未变（结构改官方 `bottomBar` 槽位，见 1.16 CHANGELOG） |
| `screens/LibraryScreen.kt` `GlassBottomSheet` | `progressiveBlur` 默认值 | **变了**（false） |
| `ui/Widgets.kt` `GlassDropdownPreference` | `GlassPopupDefaults.visuals()` | 未变 |
| `ui/AnimatedTheme.kt` | `Colors` 字段清单 | 未变（`lerpColors` 无需补字段） |
| `ui/bg/BgEffect.kt` | 例子里 `component/effect/*` | 未变（仍是 example-only） |

## 4. 构建注意

- 若 `../miuix/*/build` 里残留上一基线的 class，composite build 可能报
  `The given file ... is located outside the root directory ... bundleLibRuntimeToDirAndroidMain`，
  删掉这些 `build/` 目录重编即可（不是代码问题）。
- `../miuix` 需要自己的 `local.properties`（`sdk.dir=...` 或 `ANDROID_HOME`）；
  `setup-miuix.sh` 不生成它，克隆后第一次构建前补一行。

## 5. 验收关注点

1. 顶栏玻璃带厚度/渐变（`BandOverhang` 10dp + 渐进 32dp 的组合是官方新默认值）。
2. 作品页文件操作面板：头部不再压在内容上，首行内容与头部的间距。
3. Monet 明/暗下：关于页卡片、删除确认框按钮对比度。
4. 一级页底栏位置与列表末尾留白（结构从浮层改成 `Scaffold.bottomBar` 槽位）。
