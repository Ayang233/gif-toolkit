# GifHelper OS4 / Glass 定制面与基线耦合点清点（升级准备）

> **⚠️ 部分结论已在 1.15 失效（2026-11-24，基线 `3c560868` / v0.9.5-beta04）。** 本文保留为审计原文，读时以下面几条为准：
>
> - `Widgets.kt` 的自造 `GlassCard`（U14）**已删除**；关于页改用 `miuix-glass` 的官方 `GlassCard`
>   （官方 `AboutPage.kt:360` 同款，不采样、不模糊）。
> - `App.kt` 的 `selectedColor` 补偿（U04/U07/F07）**已删除**：外层 `AnimatedMiuixTheme` 用
>   `MiuixTheme(controller)` 提供了 `LocalColorSchemeMode`，库默认值本来就能判 `isDynamicColor`。
> - `LocalContainerSize`（1.14 引入）已随官方 `utils/AdaptiveUtils.kt` 一起删除，改回
>   `LocalWindowInfo.current.containerSize` + `computedStateOf`。
> - `AnimatedTheme` 的动画 key 已改为按颜色值（修掉"Monet 下切主题不生效"的缺陷）。
> - 卡片内自定义内容的内边距改为 `Card(insideMargin = …)`，预览画布圆角改用 `CardDefaults.CornerRadius`。
>
> 现状与逐条依据见 `docs/audit-self-built-wheels.md` §14 与 `docs/miuix-baseline-b00169ba-to-3c560868-diff.md`。

> 性质：**只读审计**。本报告不改任何源码、文档或 git 状态，唯一产物是本文件。
> 审计对象：`.`（HEAD `060c804`，`versionName 1.10` / `versionCode 12`，工作区干净）。
> 审计时间：2026-11-20。
> 证据方式：逐文件精读 + 全项目 grep + 与本地 `../miuix` 源码交叉核对。**未执行构建**，所有「编译级」判断都来自签名/默认值比对，§9 会区分「编译失败」与「不报错但观感变化」。
> 行号约定：仓库内相对路径的行号来自本仓库源码；`miuix@…` 的行号来自本地 `../miuix` 源码，**默认按 HEAD `5ff41ba9` 标注**（见 §1，本地 miuix 已不是 GifHelper 文档写的基线）。

---

## 0. 结论速览

1. **GifHelper 有 36 个 `.kt`、6513 行**；其中真正与 Miuix 发生耦合的是 20 个 UI/装配文件，`core/` 6 个文件与 Miuix **零 import**，基线升级对业务算法无影响。
2. **定制面高度集中在 5 个文件**：`ui/Widgets.kt`（607 行）、`screens/ToolScreen.kt`（487）、`App.kt`（433）、`ui/PageChrome.kt`（349）、`ui/GlassScope.kt`（322）——升级时 80% 的返工在这 5 个文件里。
3. **本仓库对上游的依赖不是「调 API」，而是「吃默认值 + 复刻内部公式 + 复制示例代码」三层叠加**。最危险的不是编译报错，而是 §10-U 表里那 20 处「不传参数、行为跟着上游变」的位置。
4. **一处已经过期的复刻**：`ui/PageChrome.kt:67-81` 的 `rememberBottomBarClearance()` 复刻了 `GlassNavigationBar` 内部未公开的 `floatingMargin` 公式（`<=16dp → 28.dp`）。miuix 在 `9413f690`（3177a684 之后）已把该值改成 `20.dp`（`miuix@5ff41ba9 GlassNavigationBar.kt:321`），升级后底栏占位会多预留 8dp，列表末尾出现空带。
5. **主题层有一个「永久性残缺」**：`ui/AnimatedTheme.kt:63` 用 `MiuixTheme(colors = …)` 重载，该重载不提供 `LocalColorSchemeMode`（`miuix@5ff41ba9 MiuixTheme.kt:55-72`，而 `LocalColorSchemeMode` 在 `ThemeController.kt:298` 是 `internal`）。后果是 `MiuixTheme.isDynamicColor` 恒为 false。上游 34 个提交里新增的「动态取色感知默认值」（`dynamicGlassMaterial`、`buttonFill()` 的 Monet 分支、导航选中色默认值）在 GifHelper 里**全部走静态分支**。
6. **玻璃导航的默认值在升级区间内被改过**：`GlassNavigationBar` 底部悬浮边距 28→20（上条）、`GlassTopAppBar` 顶栏 inset 由 `statusBars` 改成 `systemBars`、渐进模糊曲线 `0.5→0.25`、材质默认由静态 `GlassMaterials.*` 改为 `dynamicGlassMaterial(...)`。GifHelper 对这四处**全部吃默认值或半吃默认值**。
7. **复制的上游代码有 4 份**：`ui/bg/BgEffect.kt`、`ui/bg/BgEffectShader.kt`（miuix example `component/effect`）、`ui/CombinedBackdrop.kt`（example `component/liquid`）、`ui/ProgressButton.kt`（InstallerX 生态）。其中 `CombinedBackdrop` 直接 **implements 上游 `Backdrop` 接口**，上游接口一加成员就编译失败；`BgEffect*` 依赖 `RuntimeShader`/`setFloatUniform` 与着色器 uniform 名称，是升级后最可能「编译过但画面错」的一块。
8. **没有任何自动化验证**：无 `src/test`、无 `androidTest`、无 CI、`scripts/` 只有 `setup-miuix.sh`；`docs/screenshots/` 是人工截图。升级后只能靠「编译 + 模拟器四配色走查 + 真机玻璃走查」，而真机项在文档里至今标 🔴。
9. **版本三处漂移**（必须先对齐，否则审计对象都不一致）：`AGENTS.md:266` / 迁移知识库写基线 `3177a684`；`scripts/setup-miuix.sh:21` pin 的是 `d45ca181`（比 `3177a684` **旧 14 个提交**，`git rev-list --count d45ca181..3177a684 = 14`）；本地 `../miuix` 实际 HEAD 是 `5ff41ba9`（比 `3177a684` **新 34 个提交**）。
10. **升级动作可归成 3 类**：①编译错误（签名/重命名/图标名）→ 按 `assembleDebug` 报错逐个修；②默认值变化（导航/顶栏/材质/底栏占位）→ 按 §10-U 表逐项人工确认；③复制代码（4 份 + 若干复刻辅助函数）→ 决定「继续维护副本」还是「改为调用上游新 API」，必须逐份决策。

---

## 1. 基线与版本漂移（先对齐再升级）

| 项 | 值 | 证据 |
|---|---|---|
| GifHelper 代码版本 | `060c804`，1.10（versionCode 12） | `git log --oneline -1`；`build.gradle.kts:67-68` |
| AGENTS/知识库声明的 Miuix 基线 | `3177a684`（2026-11-18，`library: Refine glass floating action button`，0.9.5 开发线） | `AGENTS.md:266`；`docs/miuix-os4-migration-kit/README.md:5`；`docs/miuix-os4-migration-kit/02-version-and-dependencies.md:9-10` |
| `scripts/setup-miuix.sh` 实际 pin | `d45ca181`（2026-11-17，`library: Lower BlurDefaults.MaxBlurRadius to 100f`） | `scripts/setup-miuix.sh:21` |
| pin 与声明基线的关系 | **pin 落后 14 个提交**（`d45ca181` 是 `3177a684` 的祖先） | `git -C ../miuix rev-list --count d45ca181..3177a684` = 14 |
| 本地 `../miuix` 实际 HEAD | `5ff41ba9`（`fix: Align GlassTopAppBar progressive blur insets with systemBars`）**比声明基线新 34 个提交** | `git -C ../miuix rev-parse HEAD`；`git rev-list --count 3177a684..HEAD` = 34 |
| 本地 miuix 工作区状态 | 只有 `build-logic/src/main/kotlin/BuildConfig.kt` 被 `setup-miuix.sh` 打了 `workingDir = rootDir` 补丁（预期内） | `git -C ../miuix status --porcelain` |
| 结论 | **「文档基线 = 3177a684」「脚本 pin = d45ca181」「实际源码 = 5ff41ba9」三者不一致。** 升级前先决定统一到哪个提交，并把 `AGENTS.md:266`、知识库 `README.md:5`/`02-*.md:9`、`setup-miuix.sh:21` 一起改掉，否则后续每次审计都要重新解释 | — |

> 本报告 §3/§10 中标注 `miuix@5ff41ba9` 的默认值/行号，代表「按当前本地源码核对」；若最终升级目标不是 `5ff41ba9`，这些具体数值需按目标提交重核一次（方法同 §10 表格）。

---

## 2. 目录与文件清单（36 个 `.kt`，6513 行）

### 2.1 统计

| 分类 | 文件数 | 行数 | 说明 |
|---|---:|---:|---|
| 基础骨架（App 装配 / 路由 / 状态 / 系统入口） | 7 | 681 | `App.kt`、`Route.kt`、`AppState.kt`、`MainActivity.kt`、`GifApplication.kt`、`data/SettingsStore.kt`、`ui/AppInfo.kt` |
| OS4 化（配色/排版/组件替换/动效） | 8 | 1672 | `ui/Widgets.kt`、`ui/AnimatedTheme.kt`、`ui/Adaptive.kt`、`ui/SystemInsets.kt`、`ui/ProgressButton.kt`、`ui/BackTransitions.kt`、`screens/ThemeScreen.kt`、`ui/ButtonColorPreview.kt` |
| Glass 化（决策点 / 作用域 / 复制层 / 特效） | 6 | 1310 | `ui/GlassSupport.kt`、`ui/GlassScope.kt`、`ui/CombinedBackdrop.kt`、`ui/PageChrome.kt`、`ui/bg/BgEffect.kt`、`ui/bg/BgEffectShader.kt` |
| 业务功能（页面） | 8 | 1467 | `screens/HomeScreen.kt`、`ToolScreen.kt`、`LibraryScreen.kt`、`SettingsScreen.kt`、`AboutScreen.kt`、`LicensesScreen.kt`；`ui/ToolCatalog.kt`、`ui/LicenseCatalog.kt` 为页面元数据 |
| 业务功能（任务/核心） | 7 | 1383 | `task/TaskRunner.kt`；`core/GifCodec.kt`、`GifTasks.kt`、`MediaTools.kt`、`Stabilizer.kt`、`ColorKey.kt`、`MotionPhoto.kt` |

### 2.2 逐文件定性（按路径排序）

| # | 文件 | 行数 | 定性 | 与 Miuix 的关系 |
|---:|---|---:|---|---|
| 1 | `App.kt` | 433 | 基础骨架 + Glass 化 | **强耦合**：Nav、Pager、玻璃底栏/侧栏、弹层宿主、主题装配 |
| 2 | `Route.kt` | 36 | 基础骨架 | 依赖 `nav.core.NavKey`（`Route.kt:3`） |
| 3 | `AppState.kt` | 184 | 数据层 / 状态 | 仅 Compose runtime，无 Miuix |
| 4 | `MainActivity.kt` | 20 | 基础骨架 | 无 Miuix（`enableEdgeToEdge`，`isNavigationBarContrastEnforced=false`） |
| 5 | `GifApplication.kt` | 53 | 基础骨架（系统行为） | 反射隐藏 API，无 Miuix |
| 6 | `data/SettingsStore.kt` | 95 | 数据层（持久化） | 无 Miuix |
| 7 | `ui/AppInfo.kt` | 18 | 数据层（版本串） | 无 Miuix |
| 8 | `ui/AnimatedTheme.kt` | 130 | OS4 化（主题） | **强耦合**：`MiuixTheme(colors=)` + 53 个 `Colors` 角色逐项 `copy()` |
| 9 | `ui/Adaptive.kt` | 105 | OS4 化（自适应） | 依赖官方阈值约定 + 自建 `Layout` 修饰符 |
| 10 | `ui/SystemInsets.kt` | 95 | 基础骨架（Insets） | 复刻 `Scaffold` 默认 insets 公式 + platform 圆角 API |
| 11 | `ui/GlassSupport.kt` | 63 | Glass 化（决策点） | **耦合**：`isRenderEffectSupported()`（版本号判定）+ 渲染器字符串启发 |
| 12 | `ui/GlassScope.kt` | 322 | Glass 化（页面作用域/顶栏） | **强耦合**：`GlassTopAppBar`/`TopAppBar`/`GlassIconButton`/`LayerBackdrop` |
| 13 | `ui/CombinedBackdrop.kt` | 61 | Glass 化（**复制件**） | **强耦合**：implements 上游 `Backdrop` 接口 |
| 14 | `ui/PageChrome.kt` | 349 | 基础骨架 + Glass 化 | **强耦合**：`Scaffold`/`MiuixScrollBehavior`/`overScrollVertical`/`scrollEndHaptic`/`GlassNavigationBarDefaults.Height` |
| 15 | `ui/Widgets.kt` | 607 | OS4 化 + Glass 化（自有组件库） | **强耦合**：Card/CardDefaults/textureBlur/BlurDefaults/GlassDropdownPreference/Slider/Switch/ProgressButton |
| 16 | `ui/ProgressButton.kt` | 137 | OS4 化（**复制件**） | **强耦合**：上游 `Surface`/`ButtonDefaults`/主题色角色 |
| 17 | `ui/ButtonColorPreview.kt` | 202 | OS4 化（debug 工具页） | 依赖 `MiuixTheme.colorScheme` 的 6 个 disabled 角色 |
| 18 | `ui/BackTransitions.kt` | 91 | OS4 化（动效） | **耦合**：`NavTransitions.MiuixDefault` + `navGraphicsTransition` 作用域语义 |
| 19 | `ui/bg/BgEffect.kt` | 420 | Glass 化 / 特效（**复制件**） | **强耦合**：`RuntimeShader`/`asBrush`/`isRuntimeShaderSupported` |
| 20 | `ui/bg/BgEffectShader.kt` | 96 | Glass 化 / 特效（**复制件**） | AGSL 字符串 + uniform 名称契约 |
| 21 | `ui/ToolCatalog.kt` | 76 | 数据层（工具元数据） | **图标名耦合**：10 个 `MiuixIcons.Glass.*` |
| 22 | `ui/LicenseCatalog.kt` | 61 | 数据层（许可清单） | 无 Miuix |
| 23 | `screens/HomeScreen.kt` | 48 | 业务功能 + OS4 | `ArrowPreference` + `Os4ListPage` |
| 24 | `screens/ToolScreen.kt` | 487 | 业务功能 + OS4（最大页面） | `BasicComponent`/`Button`/`IconButton`/`TextField`/Slider/Switch/Dropdown |
| 25 | `screens/LibraryScreen.kt` | 299 | 业务功能 + Glass（弹层最集中） | `GlassBottomSheet`/`GlassDialog`/`GlassDialogButton`/`BasicComponent` |
| 26 | `screens/SettingsScreen.kt` | 64 | 业务功能 + OS4 | `ArrowPreference` + `Glass.Light.Theme/Info` |
| 27 | `screens/ThemeScreen.kt` | 134 | 业务功能 + OS4 | `DropdownRow`/`SwitchRow`/`ArrowPreference` |
| 28 | `screens/AboutScreen.kt` | 303 | 业务功能 + Glass/特效 | `textureBlur`/`BlurDefaults`/`LayerBackdrop`/`GlassCard`/`Os4ListPage` |
| 29 | `screens/LicensesScreen.kt` | 142 | 业务功能 | `ArrowPreference`/`Text`（含 `LicenseTextScreen`） |
| 30 | `task/TaskRunner.kt` | 280 | 业务功能（任务分发/进度/取帧） | 无 Miuix（grep 无 `top.yukonga` import） |
| 31 | `core/GifCodec.kt` | 439 | 业务功能（GIF 编解码，纯 Kotlin） | 无 |
| 32 | `core/GifTasks.kt` | 187 | 业务功能（十个任务实现） | 无 |
| 33 | `core/MediaTools.kt` | 310 | 业务功能（系统媒体 API/SAF/分享） | 无 |
| 34 | `core/Stabilizer.kt` | 94 | 业务功能（防抖算法） | 无 |
| 35 | `core/ColorKey.kt` | 39 | 业务功能（色度抠图） | 无 |
| 36 | `core/MotionPhoto.kt` | 33 | 业务功能（MVIMG 解析） | 无 |

> 佐证：`grep -r '^import top.yukonga' core/` 零命中；`Core/`、`task/` 里 Compose 仅用于不相关的协程/状态。

### 2.3 非 `.kt` 产物（对升级的影响）

| 路径 | 作用 | 升级相关性 |
|---|---|---|
| `src/commonMain/` | 空（只声明依赖） | 无 |
| `src/androidMain/assets/licenses/{gpl-3.0,apache-2.0,notice}.txt` | 许可正文 | 无 |
| `src/androidMain/res/values/themes.xml` | `Theme.Miuix`（manifest 引用，`AndroidManifest.xml:12`） | 依赖上游主题资源名，若上游改主题名会编译/运行期失败 |
| `src/androidMain/res/drawable/ic_app_mark.xml` | 关于页图标（108×108 画布，`AboutScreen.kt:52-60` 采样尺寸按它算） | 换包结构/图标资源时影响关于页尺寸 |
| `gradle/wrapper/gradle-wrapper.properties:3` | Gradle `9.9.1` | 上游若要求更高 Gradle/AGP，需同步 |
| `scripts/setup-miuix.sh` | 克隆 + pin + 打补丁 | **版本漂移源头**（§1） |

---

## 3. 与 Miuix 组件交互的文件：API / 参数 / 吃默认值

> 读法：**「显式传参」列**是升级时不会静默变化的；**「吃上游默认值 / 内部行为」列**是升级后可能「不报错但观感变了」的。带 `Uxx` 编号的项汇总在 §10-U 表。

### 3.1 依赖的 Miuix 模块与 API 面（按模块）

| 模块（Gradle 坐标） | GifHelper 实际用到的符号 | 出现文件 |
|---|---|---|
| `miuix-ui`（`basic/`） | `Scaffold`、`TopAppBar`、`Card`/`CardDefaults`、`Text`、`Icon`、`IconButton`、`Surface`、`Button`/`ButtonDefaults`、`BasicComponent`/`BasicComponentDefaults`、`TextField`、`Sliding`（`SliderPreference` 经 preference）、`InfiniteProgressIndicator`、`SmallTitle`、`MiuixScrollBehavior`/`ScrollBehavior` | `App.kt`、`PageChrome.kt`、`GlassScope.kt`、`Widgets.kt`、`ToolScreen.kt`、`LibraryScreen.kt`、`ProgressButton.kt` |
| `miuix-ui`（`utils/`） | `overScrollVertical`、`scrollEndHaptic`、`pagerGestureOverride`、`PagerInterceptionMode`、`PagerGestureNestedScrollConnection`、`PagerNavigationSpringSpec`、`springAnimateToPage` | `App.kt`、`PageChrome.kt` |
| `miuix-preference` | `ArrowPreference`、`SwitchPreference`、`SliderPreference` | `Widgets.kt`、全部 `screens/*` |
| `miuix-glass` | `GlassTopAppBar`(+`Defaults`)、`GlassIconButton`、`GlassNavigationBar`(+`Defaults`)、`GlassNavigationRail`(+`Defaults`/`State`)、`GlassDropdownPreference`、`GlassBottomSheet`、`GlassDialog`、`GlassDialogButton`/`Role` | `App.kt`、`GlassScope.kt`、`Widgets.kt`、`LibraryScreen.kt` |
| `miuix-blur` | `Backdrop`、`LayerBackdrop`、`layerBackdrop`、`rememberLayerBackdrop`、`textureBlur`、`BlurDefaults`、`BlendColorEntry`、`BlurBlendMode` | `GlassScope.kt`、`CombinedBackdrop.kt`、`Widgets.kt`、`AboutScreen.kt`、`App.kt` |
| `miuix-material` | `isRenderEffectSupported`、`isRuntimeShaderSupported`、`RuntimeShader`、`asBrush`、`GlassDefaults`（只吃默认，不 import） | `GlassSupport.kt`、`bg/BgEffect.kt` |
| `miuix-nav` | `NavKey`、`NavDisplay`、`NavDisplayEffects`、`navBackStackOf`、`NavBackStack`、`NavTransition`/`NavTransitions`/`navGraphicsTransition` | `Route.kt`、`App.kt`、`BackTransitions.kt` |
| `miuix-theme` | `MiuixTheme`（`colorScheme`/`textStyles`/`isDynamicColor` 注释）、`Colors`、`ThemeController`、`ColorSchemeMode`、`LocalContentColor` | `AnimatedTheme.kt`、`App.kt`、全部 UI |
| `miuix-icons` / `miuix-glass-icons` | `MiuixIcons.Glass.*`、`MiuixIcons.Glass.Light.*`、`MiuixIcons.extended.Back`（仅非玻璃返回键） | `App.kt`、`ToolCatalog.kt`、`GlassScope.kt`、`LibraryScreen.kt`、`SettingsScreen.kt`、`ToolScreen.kt`、`Widgets.kt` |

### 3.2 逐文件明细

#### A. `App.kt`（装配 / 导航 / 一级页）

| 位置 | Miuix API 与关键参数 | 吃默认值 / 内部行为（升级风险） |
|---|---|---|
| `App.kt:105` | `ThemeController(colorSchemeMode = mode)`；`ColorSchemeMode.{System,Light,Dark,MonetSystem,MonetLight,MonetDark}`（`:92-104`） | 依赖 `ThemeController.currentColors()`（`AnimatedTheme.kt:38`）；枚举名/重载变化 = 编译错误 |
| `App.kt:215-218` | `Scaffold(containerColor = Transparent, contentWindowInsets = WindowInsets(0))` | **U01**：只当弹层宿主，依赖 `Scaffold` 内部提供 `LocalRootDialogStates` + `MiuixPopupHost`（`miuix@5ff41ba9 Scaffold.kt:88,105-118`）。上游若改宿主 local/绘制层，`GlassDialog`/`GlassBottomSheet`/`GlassDropdownPreference` 会静默不渲染（本项目 1.4 前真实踩过） |
| `App.kt:227-234` | `NavDisplayEffects(enableCornerClip = true, cornerClipRadius = deviceCornerRadius, dimAmount = 0.5f, backdropColor = pageBaseColor())` | 参数均为公开参数；依赖 `cornerClipRadius` 语义与默认 `enableCornerClip` 的交互 |
| `App.kt:235-240` | `NavDisplay(backStack, onBack, transition, effects)` | 依赖 `NavDisplay` 栈语义与 `effects` 必须在 `NavDisplay` 内生效 |
| `App.kt:269-272` | `PagerDefaults.flingBehavior(state, snapAnimationSpec = PagerNavigationSpringSpec)` | **U02**：`PagerNavigationSpringSpec` 是库常量（`miuix@5ff41ba9 utils/PagerGestureUtils.kt:80`），吸附手感随上游改 |
| `App.kt:286-290` | `Modifier.pagerGestureOverride(pagerState, flingBehavior, mode = CrossAxis)` | 依赖 `mode` 枚举（`PagerGestureUtils.kt:67-74`）与「CrossAxis 会移除 Pager 自身 scrollable」这一内部实现（`App.kt:277-285` 注释） |
| `App.kt:293-300` | `userScrollEnabled = false`、`pageNestedScrollConnection = PagerGestureNestedScrollConnection`、`verticalAlignment = Top`、`beyondViewportPageCount = 1` | **U03**：`PagerGestureNestedScrollConnection` 单例（`PagerGestureUtils.kt:127`）与 `CrossAxis` 组合是官方 example 的用法，但「必须同时关 `userScrollEnabled`」是库内部约束 |
| `App.kt:321-343` | `GlassNavigationBar(items, selectedIndex, onSelect, backdrop, selectedColor, modifier=align+padding(horizontal=24dp))` | **U04 大项**：不传 `height/style/shape/material/indicatorColor/indicatorPressedColor/unselectedColor/visible/defaultWindowInsetsPadding`。默认值含 `Height=54dp`、胶囊 shape、`visible=true`、`defaultWindowInsetsPadding=true`，以及**未暴露的 `floatingMargin`**（见 U06）。`selectedColor` 为绕过 `isDynamicColor` 的显式补偿（`App.kt:333-337`） |
| `App.kt:126-127` | `rememberGlassNavigationRailState(initialExpanded = expandRail)` + `LaunchedEffect { railState.expanded = expandRail }` | 依赖 `GlassNavigationRailState.expanded` 为可写 `MutableState`、`progress` 为逐帧动画值（`miuix@5ff41ba9 GlassNavigationRail.kt:246-258`） |
| `App.kt:133-141` | `LocalRailInset` 用 `GlassNavigationRailDefaults.ExpandedWidth + Margin` 乘 `progress` | **U05**：`ExpandedWidth=250dp`、`Margin=12dp`（`GlassNavigationRail.kt:208,211`）。若上游改折叠几何，内容让位量会错 |
| `App.kt:150` | `LocalTopBarTitleAlpha = { railState.progress }` | 依赖「折叠态 = 顶部居中胶囊、与居中标题抢位」这一**布局假设**（`AGENTS.md:163-168`；上游已把 Rail 布局拆到 `GlassNavigationRail.kt` 内部，HEAD 结构变化见 §9） |
| `App.kt:153` / `157-164` | `AnimatedMiuixTheme(controller)`；`MiuixTheme.colorScheme.background.luminance()` 判系统栏图标 | 依赖配色角色亮度语义（AMOLED/Monet 下由 GifHelper 自己保证，`Widgets.kt:134-138`） |
| `App.kt:398-413` | `GlassNavigationRail(items, selectedIndex, onSelect, backdrop, selectedColor, state = railState)` | **U07**：不传 `showToggle`（默认 `true`，`GlassNavigationRail.kt:341`，本项目刻意吃默认）、`expandedWidth/collapsedTop/style/material/unselectedColor/indicator*/labelStyle/scrollState`。`collapsedTop` 默认值本身由 `TopAppBarDefaults.CollapsedHeight` 推导（`GlassNavigationRail.kt:217`） |
| `App.kt:428-432` | `GlassNavigationItem(icon, label, contentDescription)` + `MiuixIcons.Glass.{All,FolderOutline,SettingsOutline}` | 图标名/画布尺寸（28dp 画布）随上游图标集变化 |

#### B. `ui/GlassScope.kt`（页面玻璃作用域 + 顶栏）

| 位置 | Miuix API 与关键参数 | 吃默认值 / 内部行为 |
|---|---|---|
| `GlassScope.kt:111-116` | `rememberLayerBackdrop { drawRect(surfaceColor); drawContent() }` | 依赖 `rememberLayerBackdrop` 的 `onDraw` 接收 `ContentDrawScope` 且能在 `drawContent()` 前垫色（`miuix@5ff41ba9 LayerBackdrop.kt:26-45`） |
| `GlassScope.kt:226` | `GlassIconButton(onClick = onBack) { Icon(...) }` | **U08**：不传 `size/style/shape/fill/stroke/shadow`，全部继承 `LocalGlassTopAppBarContext`（`miuix@5ff41ba9 GlassIconButton.kt:70-76`）。上游改上下文内容/默认 44dp 时，按钮尺寸/材质跟着变 |
| `GlassScope.kt:228-230` | 非玻璃档 `IconButton` + `MiuixIcons.Back` 24dp | 图标只此一处用 extended 族（有意，`AGENTS.md:141-142`） |
| `GlassScope.kt:245-288` | `GlassTopAppBar(title, isContentScrolled, expandable, progressiveBlur, backdrop, contentModifier, largeTitle, subtitle, scrollBehavior, backgroundAlpha, bandBrush, bandOverhang, navigationIcon, actions, titleAlpha)` | **U09 大项**：不传 `style`（默认 `GlassDefaults.Style`，`miuix-material/GlassDefaults.kt:35`）、`material`（默认 `GlassTopAppBarDefaults.buttonMaterial()`，HEAD 已改为 `dynamicGlassMaterial`）、`titleBlurRadius`(8dp)、`alpha`、`buttonSize/Shape/fill/stroke/buttonShadow`、`defaultWindowInsetsPadding=true`、`bottomContent`，以及 HEAD 新增的 `centerObstructionStart`（未传 = `Dp.Unspecified`）。**另外**：`bandBrush` 必须来自 `GlassTopAppBarDefaults.bandBrush()`，因为库用 `private class TopAppBarBandBrush` 的类型判断决定是否走 `TopAppBarFadeBrush`（`GlassTopAppBar.kt:160,170,203-204,423,470`）——这是「吃私有类型行为」的典型 |
| `GlassScope.kt:269` | `progressiveBlur = !isSoftwareRenderer` | 依赖库两条绘制路径（AGSL 渐进 vs `bandBrush` 渐变）的默认/降级行为 |
| `GlassScope.kt:290-302` | 非玻璃档 `TopAppBar(title, modifier, color, expandable, largeTitle, largeTitleColor, titleColor, subtitle, scrollBehavior, navigationIcon, actions)` | 吃 `TopAppBar` 的大标题折叠动画与 `TopAppBarDefaults.CollapsedHeight`（`miuix@5ff41ba9 TopAppBar.kt:443`）；`color` 由调用方给 |
| `GlassScope.kt:313-321` | `ContentBackdropBox`：`CompositionLocalProvider(LocalPageBackdrop provides backdrop)` + `Modifier.layerBackdrop` | 依赖 local 在页面内被 `DropdownRow` 读取（`Widgets.kt:311`） |

#### C. `ui/PageChrome.kt`（页面骨架 / 滚动 / 内边距 / 底栏占位）

| 位置 | Miuix API 与关键参数 | 吃默认值 / 内部行为 |
|---|---|---|
| `PageChrome.kt:224` | `MiuixScrollBehavior()` | **U10**：`state=rememberTopAppBarState()`、`canScroll={true}`、`snapAnimationSpec=spring(stiffness=2500f)`、`flingAnimationSpec=rememberSplineBasedDecay()`（`miuix@5ff41ba9 TopAppBar.kt:266-273`）。顶栏收起/回弹手感全部来自这里 |
| `PageChrome.kt:92-105` | `Modifier.scrollEndHaptic()`（默认 `TextHandleMove`，`ScrollEndHaptic.kt:124-126`）、`.overScrollVertical(onOffsetChanged = { state.overscrollOffset = it })`、`.nestedScroll(scrollBehavior.nestedScrollConnection)` | **U11**：直接写上游公开字段 `ScrollState.overscrollOffset`（`TopAppBar.kt:354-362`）。HEAD 的 ScrollBar 重构（`9155cd2a`）尚未改这个字段，但语义是「临时阻尼位移、弹簧帧也上报」，上游若改上报节奏，顶栏 band 会抖 |
| `PageChrome.kt:67-81` | `rememberBottomBarClearance()` = `GlassNavigationBarDefaults.Height(54dp) + floatingMargin + 12dp`，其中 floatingMargin **镜像库内部未公开公式** | **U06（已过期）**：镜像公式为 `navInset<=16dp → 28dp`（`PageChrome.kt:73-77`），而 `miuix@5ff41ba9 GlassNavigationBar.kt:321` 已是 `20.dp`（提交 `9413f690`）。另外「按 54dp 最小值算」在大字体下会偏小（`docs/os4-round10-audit.md:812` §7.3#3） |
| `PageChrome.kt:113-163` | `pageContentPadding(...)`：自定义 `PaddingValues`，在 `calculate*Padding` 里读 `railInset()`（`:140-162`） | 依赖 `Scaffold.innerPadding` 的横向分量 = `systemBars ∪ displayCutout`（与 `SystemInsets.kt:32-36` 同一公式，**两处独立实现**） |
| `PageChrome.kt:339-347` | `Scaffold(containerColor = pageBaseColor(), topBar, bottomBar, content)`（不传 `contentWindowInsets`） | **U12**：吃 `Scaffold` 默认 `contentWindowInsets = WindowInsets.systemBars.union(displayCutout)`（`miuix@5ff41ba9 Scaffold.kt:90`）。上游改默认 inset，全项目顶部/横向留白一起变 |
| `PageChrome.kt:322-324` | `pageOverlay(LocalRootBackdrop.current ?: glass.contentBackdrop.takeIf { enabled })` | 依赖「弹层由最外层 Scaffold 绘制、上报真实坐标」这一内部宿主行为（与 U01 同源） |
| `PageChrome.kt:242-267` | `PageTopBar(...)` 组装：`expandable = largeHeader == null && !LocalWideLayout`（`:251`）、`titleAlpha`/`background` 按进度 | 依赖 `TopAppBar`/`GlassTopAppBar` 的 `titleAlpha`/`backgroundAlpha` 语义 |
| `PageChrome.kt:55` | `LocalBottomBarClearance` 由 `App.kt:151` 单点下发 | — |

#### D. `ui/Widgets.kt`（自有组件库，OS4/Glass 定制最集中）

| 位置 | Miuix API 与关键参数 | 吃默认值 / 内部行为 |
|---|---|---|
| `Widgets.kt:84-89` | `cardColor()` = `MiuixTheme.colorScheme.surfaceContainer`（AMOLED → `#0D0D0D`） | 依赖 `surfaceContainer` 角色语义（Monet 下与 `surface` 不同值的前提，`docs/os4-round10-audit.md:550-570`） |
| `Widgets.kt:99-110` | `pageColor()`：由 `cardColor()` 合成 3.5%/18% 黑 | 自派生物理，无 upstream 依赖 |
| `Widgets.kt:133-138` | `pageBaseColor()` = `MiuixTheme.colorScheme.surface`（AMOLED 黑） | 依赖 `surface` 是页面真实底色这一约定 |
| `Widgets.kt:149-167` | `Card(modifier = padding(12dp)+padding(bottom), colors = CardDefaults.defaultColors(color = cardColor()))` | **U13**：不传 `cornerRadius`/`insideMargin`/`contentColor`。默认 `CornerRadius=16dp`、`InsideMargin=PaddingValues(0.dp)`、`contentColor=onSurfaceContainer`（`miuix@5ff41ba9 Card.kt:192-205`）。`InsideMargin` 从 16dp 改 0 是上游过去的一次变更，本项目已按新值适配——**若再变，所有卡片行内边距一起变** |
| `Widgets.kt:187-219` | `GlassCard`：`Card` + `Modifier.textureBlur(backdrop, shape = RoundedCornerShape(CardDefaults.CornerRadius), blurRadius = 60f, colors = BlurDefaults.blurColors(blendColors=[BlendColorEntry(surface 0.6f)]))` + `CardDefaults.defaultColors(Transparent)` | **U14**：`textureBlur` 参数名/语义（`miuix@5ff41ba9 TextureEffect.kt:27-34`）与 `BlurDefaults.blurColors` 的 remember 行为；`blurRadius` 被 `MaxBlurRadius` 夹紧。本组件不含录制层（有意） |
| `Widgets.kt:240-246` | `Icon(imageVector, contentDescription=null, modifier.size(size), tint)` | 依赖图标 28dp 画布与 `IconSizes.Row=26dp` 的实测匹配（`:249-274`） |
| `Widgets.kt:312-322` | `GlassDropdownPreference(onSelectedIndexChange, items, selectedIndex, title, backdrop = LocalRootBackdrop ?: LocalPageBackdrop, summary, enabled)` | **U15 大项**：不传 `renderInRootScaffold`（默认 `true`，`miuix@5ff41ba9 GlassDropdownPreference.kt:72`）、不传 `visuals`（默认 `GlassPopupDefaults.visuals()`）、不传 `titleColor/summaryColor`。`backdrop` 的坐标空间正确性完全依赖「库把 popup 交给最外层 Scaffold 画」 |
| `Widgets.kt:334-341` | `SliderPreference(value, onValueChange, title, valueText, valueRange, steps)` | **U16**：吃 `insideMargin`(16dp)、`sliderColors`、`hapticEffect`、`showKeyPoints=false`、`sliderHeight`；`steps` 语义（中间档位数）已按库源码核对（`ToolScreen.kt:272-275` 注释） |
| `Widgets.kt:358-364` | `SwitchPreference(checked, onCheckedChange, title, summary, startAction)` | **U17**：吃 `switchColors` 默认（其中未选中拇指色依赖 `MiuixTheme.isDynamicColor`/`primary`，见 U18；本项目未显式传色） |
| `Widgets.kt:376-381` | `Text(style = footnote1, color = onSurfaceVariantSummary)` + 28dp/10dp padding | 依赖 `textStyles.footnote1` 与字号表（无自定义 Typography） |
| `Widgets.kt:391-449` | `BoxWithConstraints` + `clip(RoundedCornerShape(12dp))` + `background(pageColor())` + `LocalWindowInfo.current.containerSize` | 依赖 window info 尺寸语义；12dp 圆角是本地值（与卡片 16dp 不一致，已记录为债务） |
| `Widgets.kt:469-517` | `PrimaryActionBar`：`pageBaseColor()` 遮挡 + `navigationBarsPadding()` + `safeBottomGap(corner, sideGap)` + `railStartInset(base)` | 依赖 `railStartInset` 自建 `Layout`（`Adaptive.kt:67-78`）与屏幕圆角 API |
| `Widgets.kt:551-606` | `ProgressButton` + `ProgressButtonDefaults.progressButtonColors(...)` + `InfiniteProgressIndicator(color, size=16dp, strokeWidth=2dp, orbitingDotSize=1.5dp)` + `Text(style=button)` | **U19**：禁用态角色 `disabledPrimaryButton/disabledOnPrimaryButton/disabledSecondaryVariant` 等 6 个颜色名（`ButtonColorPreview.kt:59-87` 也枚举同一批），上游改颜色角色名会编译失败；改默认值会静默改观感 |

#### E. `ui/ProgressButton.kt`（复制件）

| 位置 | 内容 | 风险 |
|---|---|---|
| `ProgressButton.kt:1-4` | 文件头声明移植自 InstallerX Revived `ProgressButton.kt`（Apache-2.0） | 不属于 Miuix 官方 API，上游不会替我们维护 |
| `ProgressButton.kt:50-97` | 自建 `Surface(onClick=…, enabled, shape=RoundedCornerShape(16dp), color, contentColor, interactionSource, indication)` + `Row(defaultMinSize(minWidth=ButtonDefaults.MinWidth, minHeight=50dp))` + `drawWithContent` 画进度 | **U20**：依赖上游 `Surface` 的 `onClick` 重载参数（`miuix@5ff41ba9 Surface.kt:89-99`）与 `ButtonDefaults.MinWidth`；几何（16dp 圆角/50dp/12dp 内边距）与官方 Button（16dp/40dp/13dp）**不同源**，已列为债务 |
| `ProgressButton.kt:110-137` | `ProgressButtonDefaults.progressButtonColors(...)` 默认色角色 | 同 U19 |

#### F. `ui/AnimatedTheme.kt`（主题装配）

| 位置 | 内容 | 风险 |
|---|---|---|
| `AnimatedTheme.kt:38` | `controller.currentColors()` | 依赖 `ThemeController` 公开方法（`miuix@5ff41ba9 ThemeController.kt:246`） |
| `AnimatedTheme.kt:54-68` | `MiuixTheme(colors = animated)` + 自补 `LocalContentColor` | **U18（结构性）**：该重载只提供 `LocalColors/LocalTextStyles/LocalIndication/LocalOverscrollFactory`（`miuix@5ff41ba9 MiuixTheme.kt:55-72`），**不提供 `LocalColorSchemeMode`**（`ThemeController.kt:298` 为 `internal`，调用方无法补）→ `MiuixTheme.isDynamicColor` 恒 false（`MiuixTheme.kt:87-96`）。所有「按 isDynamicColor 分支」的上游默认值在 GifHelper 内都不会生效 |
| `AnimatedTheme.kt:72-129` | `lerpColors` 手工枚举 **53 个** `Colors.copy()` 参数（当前 `Colors` 类恰好 53 个角色，`miuix@5ff41ba9 Colors.kt:68-120`） | **U21**：上游**新增**角色时 `copy()` 有默认值 → 编译通过但新角色不参与过渡（静默）；**改名/删除**角色 → 编译失败（可发现） |

#### G. 其他 UI 文件

| 文件:行 | API / 参数 | 吃默认值 / 内部行为 |
|---|---|---|
| `ui/Adaptive.kt:23-32` | `LocalWindowInfo.containerSize` + 阈值 `≥840dp` / `≥600dp 且 高宽<1.2` | 数值照抄官方 `AdaptiveUtils`（**example 代码，非发布 API**），上游改阈值需人工同步（不报错） |
| `ui/Adaptive.kt:51,67-78` | `LocalRailInset: () -> Dp`；`Modifier.railStartInset(base)` 用 `layout` 在量测期读 | 自建实现，避免组合期解包；依赖 `placeRelative` RTL 语义 |
| `ui/Adaptive.kt:90` | `LocalTopBarTitleAlpha: () -> Float` | 与上游 `LocalTopAppBarTitleAlpha` 同款机制（本项目只做到「数据形状一致」） |
| `ui/SystemInsets.kt:31-36` | `WindowInsets.systemBars.union(displayCutout).only(Horizontal)` | **U12 的孪生实现**：与 `Scaffold` 默认 insets 同一公式，两处独立维护 |
| `ui/SystemInsets.kt:45-65` | `RoundedCorner` API / framework dimen 兜底 | platform 行为，非 Miuix |
| `ui/BackTransitions.kt:24-33` | `NavTransitions.MiuixDefault`、`navGraphicsTransition(opaqueDepth=1f){ scope -> … }` | 依赖 `NavTransitionScope.relativeDepth/layoutSize/layoutDirection` 语义（`miuix-nav` 公开 API，但相对深度定义变化会静默改观感） |
| `screens/HomeScreen.kt:42-47` | `ArrowPreference(title, summary, startAction, onClick)` | 吃行高/内边距/箭头色默认 |
| `screens/ToolScreen.kt:142-169` | `BasicComponent(title, summary, endActions, onClick)` | **U22**：吃 `InsideMargin=16dp`、`min height=56dp`、`spacerPx=8dp`、标题 `headline1`（`miuix@5ff41ba9 Component.kt:171,211,292`） |
| `screens/ToolScreen.kt:414-422` | `TextField(state, label, useLabelAsPlaceholder, modifier, keyboardOptions)` | **U23**：吃 `TextFieldDefaults.InsideMargin`（16dp，`TextField.kt:85`）；注释里明确按 16dp 对齐卡片 28dp 左边线 |
| `screens/ToolScreen.kt:476-477` | `Button(onClick, modifier=weight(1f)) { Text(...) }` | 吃 `ButtonDefaults` 的 MinWidth/MinHeight/CornerRadius/colors |
| `screens/ToolScreen.kt:341-405` | 自绘色板：`selectable(role=RadioButton)` + `HapticFeedbackType.ToggleOn` | 自定义控件，依赖 Compose 语义 API；触觉只在 `!selected` 时（与库规范一致） |
| `screens/LibraryScreen.kt:244-250` | `GlassBottomSheet(show, title, backdrop, onDismissRequest, onDismissFinished)` | **U24**：不传 `insideMargin`(DpSize(24,0))、`cornerRadius`、`material/stroke/containerColor`、`renderInRootScaffold=true`、`enableNestedScroll`、`allowDismiss`（`miuix@5ff41ba9 GlassBottomSheet.kt:50-72`）。KDoc 要求「内容自己让导航条」，本项目用 `navigationBarsPadding()` 补（`:280`） |
| `screens/LibraryScreen.kt:114-147` | `GlassDialog(show, backdrop, title, summary, onDismissRequest, onDismissFinished)` + `GlassDialogButton(onClick, modifier, role = Destructive)` | **U25**：不传 `insideMargin`(DialogDefaults)、`material/stroke/containerColor`、`renderInRootScaffold=true`、`cornerRadius/maxWidth`；按钮 `role` 默认 `Normal`、本项目的「取消」吃默认 |
| `screens/LibraryScreen.kt:255-275` | `BasicComponent(title, startAction, insideMargin=PaddingValues(horizontal=0, vertical=16), onClick)`；删除行 `titleColor = BasicComponentDefaults.titleColor(color=error)` | 依赖 `BasicComponentDefaults.titleColor` 工厂签名；显式覆盖 insideMargin 是因为面板已给 24dp |
| `screens/AboutScreen.kt:118,161-165` | `rememberLayerBackdrop()` + `BgEffectBackground(bgModifier = Modifier.layerBackdrop(bgBackdrop), alpha = …)` | 依赖 `BgEffectBackground`（复制件）与 `layerBackdrop` 节点关系 |
| `screens/AboutScreen.kt:123-151` | `BlendColorEntry(color, BlurBlendMode.{ColorBurn,ColorDodge,LinearLight,Lab})`、`BlurDefaults.blurColors(blendColors)`、`rememberCombinedBackdrop(bgBackdrop, contentBackdrop)` | **U26**：`BlurBlendMode` 枚举值/`blurColors` 工厂/`CombinedBackdrop` 的接口实现三处耦合；混合色三档是**逐字复制的 KSU/InstallerX 值**，上游不会变但 GPU 表现会 |
| `screens/AboutScreen.kt:244-253,272-279` | `Modifier.textureBlur(backdrop, shape=RectangleShape, blurRadius=150f, colors=logoColors, contentBlendMode=ComposeBlendMode.DstIn, enabled=true)` ×2 | **U27**：`textureBlur` 参数与 `DstIn` 合成语义；`150f` 被 `MaxBlurRadius` 夹紧（HEAD 已是 100f，见 §9-③） |
| `screens/AboutScreen.kt:179` | `GlassCard(backdrop = if (blurEnabled) bgBackdrop else null)` | 见 U14 |
| `screens/LicensesScreen.kt:52-57,124-130` | `ArrowPreference` / `Text(style=footnote1)` | 同上 |
| `ui/ButtonColorPreview.kt:59-87,144-172` | 6 个 disabled 颜色角色 + `ProgressButton` 真按钮 | 仅 debug 包；角色改名会编译失败（好事） |

#### H. `ui/bg/BgEffect.kt` + `BgEffectShader.kt`（复制件）

| 位置 | 内容 | 风险 |
|---|---|---|
| `BgEffect.kt:1-48` | 文件头声明移植自 miuix example `component/effect/{BgEffectBackground,BgEffectPainter,BgEffectModifier,BgEffectConfig}.kt` | 上游 example 改动**不会**自动同步到本仓库 |
| `BgEffect.kt:138,159-162` | `isRuntimeShaderSupported()`（`miuix@5ff41ba9 RuntimeShader.kt:123`）；不支持时退化为普通 Box | 依赖 expect/actual 能力判定；上游改判定口径会改变退化时机 |
| `BgEffect.kt:163,308-317` | `remember { BgEffectPainter() }`；`RuntimeShader(OS3_BG_FRAG).also { setFloatUniform(...) }`；`brush = runtimeShader.asBrush()` | **U28**：`RuntimeShader(…)/asBrush()/setFloatUniform(name, values)` 的公开形态（`RuntimeShader.kt:19-57,130-141`）。uniform 名称必须与 `BgEffectShader.kt:12-24` 的 AGSL 声明逐字一致 |
| `BgEffect.kt:145,174` | 底色用 `pageColor()`（本项目约定，非上游） | 与 `Widgets.pageColor()` 强绑定 |
| `BgEffect.kt:266-281` | 自建 `Modifier.Node` + `withFrameNanos` 逐帧 `invalidateDraw` | 只依赖 Compose runtime，不依赖 Miuix |
| `BgEffectShader.kt:11-96` | 大段 AGSL（uniform `uResolution/uAnimTime/uBound/uPoints/uPointsAnim/uColors/uAlphaMulti/uNoiseScale/uPointRadiusMulti/uSaturateOffset/uLightOffset`） | 着色器源码级复制；若上游 material 的 `RuntimeShader` 包装改变编译方式（如加 header、改 `main` 约定），会**运行期**失败而非编译失败 |

---

## 4. OS4 化成果清单（落到文件与函数）

| 成果 | 实现位置 | 要点 / 证据 |
|---|---|---|
| **三层配色体系** | `ui/Widgets.kt:84-138`：`cardColor()` / `pageColor()` / `pageBaseColor()`；`LocalAmoled`（`:75`） | `pageBaseColor` = `surface`（页面真实底色，玻璃垫底/遮挡/Scaffold 容器色三处同源）；`pageColor` = 卡片色反推的次级表面；`cardColor` = `surfaceContainer`。第十轮实测四套配色不同值（`docs/os4-round10-audit.md:550-570`、`AGENTS.md:79-88`） |
| **静态 + Monet 双主题、明暗双模式** | `App.kt:92-105`（`ColorSchemeMode` 映射）、`AnimatedTheme.kt:33-69`（53 角色逐色插值 320ms） | `ThemeMode` 枚举在 `AppState.kt:16-20`；`SettingsStore` 持久化（`SettingsStore.kt:22-28`） |
| **AMOLED 纯黑** | `Widgets.kt:75,84-89,100-110,133-138` | 只在深色生效；页面纯黑、卡片 `#0D0D0D` |
| **排版收敛到语义角色** | 全项目只用 `MiuixTheme.textStyles.*`（`Widgets.kt:379,603`；`ToolScreen.kt:367,468,471`；`LicensesScreen.kt:126` 等）；`GroupHeader` 包 `SmallTitle`（`Widgets.kt:64-67`） | grep `fontFamily|Typography|textStyles\s*=` 无自定义字号表——**排版没有自造**，这是升级友好项 |
| **卡片视觉（16dp 圆角、无分割线、56dp 行高）** | `Widgets.kt:149-167`（`GroupCard`）、`AGENTS.md:118`（硬约束 6） | 圆角来自 `CardDefaults.CornerRadius`（不吃本地数值）；分割线由「不用 `HorizontalDivider`」保证 |
| **组件替换（Material → Miuix）** | 全项目 `grep androidx.compose.material` **零命中**；UI 全部 `top.yukonga.miuix.kmp.*`（§3.1） | `AGENTS.md:119` 硬约束 7；`docs/miuix-os4-migration-kit/18-*.md:36` 记录「material3 零 import」 |
| **导航骨架：Pager + NavDisplay** | `App.kt:119`（`navBackStackOf`）、`:172-201`（3 tab Pager + `BackHandler`）、`:235-392`（`NavDisplay` 路由） | 底栏属于一级页（录制层兄弟，`:320-343`）；二级页压栈盖住底栏（`AGENTS.md:151-162`） |
| **宽屏自适应（Rail）** | `ui/Adaptive.kt:23-32,99-105`；`App.kt:112-141,398-413` | 阈值 840/600/1200dp 与官方一致；内容让位 `LocalRailInset` 函数式下发、量测期读取 |
| **尺寸/安全区体系** | `ui/SystemInsets.kt:31-95`（横向安全区、设备圆角、`safeBottomGap` 几何推导）；`PageChrome.kt:113-163` | 横屏挖孔 64dp、大圆角机型底部留白 12→32dp 的几何解 |
| **动效** | `ui/BackTransitions.kt:19-91`（5 档转场）；`App.kt:227-234`（圆角裁剪 + dim）；`App.kt:186-189`（`springAnimateToPage`） | 「无」档仍走 `MiuixDefault` 而非 `NavTransitions.None`（`:26-30` 注释） |
| **无障碍/触觉** | `ToolScreen.kt:341-405`（色板 `Role.RadioButton`+颜色名+`ToggleOn`）；`PageChrome.kt:101`（`scrollEndHaptic`） | 全项目业务手写触觉只有 1 处；其余来自库 |
| **图标规范** | `ui/ToolCatalog.kt:46-58`（10 个 `Glass` 定色）；`App.kt:428-432`（`Glass.*Outline`）；`SettingsScreen.kt:44,58` / `LibraryScreen.kt:214,257-272`（`Glass.Light.*`）；`Widgets.kt:249-274`（`IconSizes`） | 玻璃档顶栏用 `Glass.ChevronBackward` 28dp、非玻璃档 extended `Back` 24dp（`GlassScope.kt:210-235`） |
| **调试专用页（release 可折叠）** | `build.gradle.kts:71-85`（`DEBUG_UI` 字面量）、`App.kt:384-391`、`ThemeScreen.kt:121-132`、`ui/ButtonColorPreview.kt` | release dex 搜不到调试文案（`docs/os4-round10-audit.md:844-859`） |

> 注：`miuix-squircle` 在 `build.gradle.kts:32-35` 有依赖与注释，但**全项目 grep `squircle` 零命中**（OS4「连续曲率圆角」实际只由 Miuix 组件内部（`Card` 的 `squircleSurface`）与 `NavDisplayEffects` 圆角裁剪间接提供）。升级时这条依赖可以顺手复核是否还需要显式声明。

---

## 5. Glass 化成果清单（决策点 / 录制层 / 采样源 / 消费者）

### 5.1 唯一决策点

| 项 | 位置 | 说明 |
|---|---|---|
| `isGlassSupported()` | `ui/GlassSupport.kt:25` | 直接调 `isRenderEffectSupported()`（`miuix-material/RenderEffect.kt:7`）。**注意**：它只是 `SDK_INT >= 31`，查不出 GPU 模糊翻车（`AGENTS.md:92-94`） |
| `LocalGlassEnabled` | `ui/GlassSupport.kt:63`（默认 false） | 全局唯一开关；`App.kt:110` 计算 `app.glassEnabled && isGlassSupported()`，`App.kt:143-152` 下发 |
| `isSoftwareRenderer` | `ui/GlassSupport.kt:45-60` | 用 `GL_RENDERER/GL_VENDOR` + `Build.HARDWARE` 字符串启发（swiftshader/llvmpipe/ranchu/goldfish…）。只用于「顶栏是否走 AGSL 渐进模糊」与「关于页 `textureBlur` 是否启用」（`AboutScreen.kt:111`），**不是第二套视觉** |

### 5.2 录制层（`layerBackdrop`）共 4 个挂载点，两类

| # | 录制层 | 创建 | 挂载位置 | 覆盖范围 | 消费者（必须是它的兄弟节点） |
|---|---|---|---|---|---|
| R1 | `rootBackdrop`（一级页，**Pager 外**） | `App.kt:170` `rememberPageBackdrop()` | `App.kt:253-256` `Box.fillMaxSize().layerBackdrop(...)`，包住 `HorizontalPager` | 满宽，不随 Rail 收窄 | `GlassNavigationBar`（`App.kt:327`）、`GlassNavigationRail`（`App.kt:403`）、所有页面级弹层（经 `LocalRootBackdrop`，`App.kt:262-264`）、`DropdownRow`（`Widgets.kt:311`） |
| R2 | `contentBackdrop`（页面正文） | `GlassScope.kt:130`（`rememberGlassScope` → `rememberPageBackdrop`） | `GlassScope.kt:320`（`ContentBackdropBox`，落在 `PageChrome.kt:278` 的 Scaffold 内容槽） | 满宽 | 顶栏（`PageChrome.kt:243` → `GlassScope.kt:270`）、二级页下拉（`LocalPageBackdrop`，`GlassScope.kt:319`） |
| R3 | `bgBackdrop`（关于页渐变层） | `AboutScreen.kt:118` `rememberLayerBackdrop()` | `AboutScreen.kt:163` `BgEffectBackground(bgModifier = Modifier.layerBackdrop(...))`，只罩渐变 Spacer | 整屏 | 关于页图标 / 标题（`textureBlur`，`:244-253,272-279`）、`GlassCard`（`:179`）、顶栏（经 `rememberCombinedBackdrop`，`:146-151`） |
| R4 | （无）弹层自身 | — | 弹层只消费 backdrop，不录制 | — | — |

**兄弟关系（硬约束 1）落地检查**：

- `App.kt:250` 的 `Box` 里：录制层 `:253-256` 与底栏（`:320-343`）是兄弟；Rail 在 `:398-413`，位于 `entry<Route.Root>` **外**、也在 `rootBackdrop` 外 → 兄弟/外部。
- `PageChrome.kt:269-325`：`ContentBackdropBox`（`:278`）在 Scaffold 内容槽；`topBar`（`:242-267`）在 `topBar` 槽；`pageOverlay`（`:322-324`）在内容槽之后、`ContentBackdropBox` 之外 → 顶栏与弹层都是录制层兄弟。
- 关于页：渐变层与 `content()` 是兄弟（`BgEffect.kt:164-181`：Spacer 挂 `bgModifier`，`content()` 另画），`AGENTS.md:203-204` 明确禁止把录制层套到内容外。
- 录制层禁止上移到 `Scaffold` 自身（`AGENTS.md:58-60`）：现状满足（R1 在 `entry<Route.Root>` 内、R2 在内容槽）。

### 5.3 采样源选择规则（两类坐标空间）

| 场景 | 规则 | 位置 | 原因 |
|---|---|---|---|
| 页面级 Dialog / BottomSheet | `LocalRootBackdrop ?: 页面录制层` | `PageChrome.kt:322-324`；`LibraryScreen.kt:82-149` | 弹层由最外层 Scaffold 绘制、上报**真实坐标**；Pager 内页面层上报**槽位坐标**（第 2 tab x=1080），差一整页宽 → 采样落空（`AGENTS.md:62-68`） |
| 下拉 `GlassDropdownPreference` | `LocalRootBackdrop ?: LocalPageBackdrop` | `Widgets.kt:309-311` | 同上；库默认 `renderInRootScaffold = true` |
| 顶栏 | 页面录制层；关于页用 `CombinedBackdrop(渐变, 正文)` | `GlassScope.kt:270`；`AboutScreen.kt:146-151` | 关于页正文层顶部空、渐变层滚到底淡出，两层互补（`AGENTS.md:199-204`） |
| 底栏 / Rail | `rootBackdrop` | `App.kt:327,403` | 录在 Pager 外，坐标真实 |

### 5.4 `textureBlur` 使用点（全部 3 处 + 1 处玻璃卡）

| 使用点 | 参数 | 采样源 |
|---|---|---|
| 关于页图标 `AboutScreen.kt:244-253` | `shape=RectangleShape`、`blurRadius=150f`、`colors=BlurDefaults.blurColors(logoBlend)`、`contentBlendMode=DstIn`、`enabled=true` | `bgBackdrop` |
| 关于页标题 `AboutScreen.kt:272-279` | 同上 | `bgBackdrop` |
| 玻璃卡 `Widgets.kt:202-210` | `shape=RoundedCornerShape(CardDefaults.CornerRadius)`、`blurRadius=60f`、`colors=BlurDefaults.blurColors([BlendColorEntry(surface 0.6)])` | 调用方传入（一级/二级页层） |

### 5.5 玻璃组件清单（7 个官方组件 + 1 个合成器）

| 组件 | 调用点 | 采样源 | 降级 |
|---|---|---|---|
| `GlassTopAppBar` | `GlassScope.kt:260-288` | 页面层 / `CombinedBackdrop` | `backdrop=null` → `bandBrush` 渐变带（`GlassScope.kt:258-259` 显式备份 band 参数） |
| `GlassIconButton` | `GlassScope.kt:226` | 继承顶栏按钮 backdrop | 库默认 |
| `GlassNavigationBar` | `App.kt:321-343` | `rootBackdrop` | `backdrop=null` → 库 `containerColor` 胶囊 |
| `GlassNavigationRail` | `App.kt:399-413` | `rootBackdrop` | 同上 |
| `GlassDropdownPreference` | `Widgets.kt:312-322` | `LocalRootBackdrop ?: LocalPageBackdrop` | `backdrop=null` → 不透明 popup |
| `GlassBottomSheet` | `LibraryScreen.kt:244-250` | `pageOverlay` 传入 | 同上 |
| `GlassDialog` + `GlassDialogButton` | `LibraryScreen.kt:114-147` | 同上 | 同上 |
| `CombinedBackdrop` | `AboutScreen.kt:149`（+`ui/CombinedBackdrop.kt`） | 叠 `bgBackdrop` + `contentBackdrop` | 仅当 `blurEnabled` 时构造 |

### 5.6 玻璃关闭时的降级路径（升级时容易破坏的隐式契约）

- `LocalGlassEnabled=false` → `App.kt:256` 不挂 `layerBackdrop`；`GlassScope.enabled=false`（`GlassScope.kt:127-131`）→ 顶栏走 `TopAppBar`、`backdrop=null`。
- 底栏/Rail 仍传 `backdrop = null`（`App.kt:327,403`）→ 库退回不透明胶囊 + 阴影，**不会塌成通栏白底条**（`AGENTS.md:325-327`）。
- 关于页 `blurEnabled=false` 时 `textureBlur` 不挂、`GlassCard(backdrop=null)` 退回 `cardColor()` 不透明卡（`AboutScreen.kt:179`）——入口不会消失。
- 这些降级都依赖「上游 `backdrop=null` 的兜底实现」，属于 §10-U 的 U24/U25/U14。

---

## 6. 构建与工具链

| 项 | 值 / 机制 | 位置 |
|---|---|---|
| 插件 | AGP `9.5.1`、Kotlin `2.4.21`（KMP + compose plugin）、Compose Multiplatform `1.12.2` | `build.gradle.kts:3-8` |
| 单模块 KMP | `androidTarget`（JVM 21）；实现全在 `androidMain`，`commonMain` 只声明依赖（`src/commonMain/` 为空目录） | `build.gradle.kts:10-49`、`AGENTS.md:7` |
| Miuix 依赖 | `miuix-ui` / `-preference` / `-icons` / `-material` / `-squircle` / `-glass` / `-glass-icons` / `-nav`（均无版本号，由 composite build 提供） | `build.gradle.kts:25-40` |
| 其他依赖 | `hiddenapibypass:6.1`、`navigationevent-compose:1.2.0`、`kotlinx-serialization-core:1.13.0`、activity/core/lifecycle/coroutines | `build.gradle.kts:22-23,37-38,42-47` |
| compileSdk | `release(37) { minorApiLevel = 2 }`（上游 0.9.5-beta01 起要求 37.2，否则 AGP 配置期拒绝） | `build.gradle.kts:56-60`、`AGENTS.md:266-271` |
| minSdk / targetSdk / 版本 | `33` / `36` / `versionCode 12`、`versionName 1.10` | `build.gradle.kts:62-69` |
| 构建类型 | debug/release 各注入字面量 `DEBUG_UI`；release `isMinifyEnabled`+`isShrinkResources`+debug 签名 | `build.gradle.kts:71-85` |
| **composite build 机制** | `MIUIX_DIR` 环境变量 → 否则 `../miuix`；目录不存在时 `error(...)` 给出克隆命令；存在则 `includeBuild(miuixDir)` | `settings.gradle.kts:38-57` |
| **setup-miuix.sh pin 机制** | ①克隆/复用 `../miuix`；②`checkout $PINNED_REF`（默认 `d45ca181`），本地没有则尝试 `fetch origin main`，**网络受限时保留当前 HEAD 继续**；③给 `build-logic/BuildConfig.kt` 的 `getGitVersionCode`/`getGitHashShort` 补 `workingDir = rootDir`（composite build 下子构建工作目录不是 git 仓库 → 否则 git exit 128） | `scripts/setup-miuix.sh:17-73` |
| gradle.properties | `-Xmx4g`、parallel、caching、`android.useAndroidX`、`nonTransitiveRClass`、`android.newDsl=false`、`android.builtInKotlin=false`、`kotlin.mpp.applyDefaultHierarchyTemplate=false`（无 Miuix 版本锁） | `gradle.properties:1-9` |
| Gradle Wrapper | `9.9.1` | `gradle/wrapper/gradle-wrapper.properties:3` |
| Manifest | `android:enableOnBackInvokedCallback="true"`、`Theme.Miuix`、`configChanges` 全量、`adjustResize`、FileProvider | `AndroidManifest.xml:4-33` |
| 预测性返回开关 | `GifApplication` 通过 HiddenApiBypass 反射 `ApplicationInfo.setEnableOnBackInvokedCallback`（API 34+，失败只打日志） | `GifApplication.kt:25-48` |

**工具链层面与升级直接相关的三点**：

1. `compileSdk 37.2` 是上游硬门槛，升级后若上游再抬高 minor（如 37.3），`build.gradle.kts:56-60` 必须同步，否则配置期直接失败。
2. `setup-miuix.sh` 的 pin 必须与 `AGENTS.md`/知识库声明一致（当前不一致，见 §1）。
3. `miuix-material`/`miuix-squircle` 是**显式依赖**（`build.gradle.kts:29-35` 注释说明 glass 模块不透出），升级后若上游把 `GlassDefaults` 挪到别的模块，需同步改依赖与 import。

---

## 7. 现有验证能力盘点

### 7.1 自动化能力：基本为零

| 项 | 现状 | 证据 |
|---|---|---|
| 单元测试 | **无**（无 `src/test`、`src/androidTest`、`commonTest`） | 目录遍历只有 `src/commonMain`（空）与 `src/androidMain`；`docs/miuix-os4-migration-kit/18-*.md:296` 记为 D6 |
| UI/截图测试 | **无**（无 screenshot/benchmark 源集与脚本） | `scripts/` 只有 `setup-miuix.sh`；`docs/screenshots/` 是人工截图（6 张 + icon 预览） |
| CI | **无**（无 `.github/workflows`） | `.github` 目录不存在 |
| 基线 profile | 无 | — |
| 手工验证手段 | ①`./gradlew :assembleDebug/:assembleRelease`；②模拟器四配色走查（静态/Monet × 明/暗）；③debug 包专属「设置→主题→开始按钮配色对比」页（`ui/ButtonColorPreview.kt`，`ThemeScreen.kt:121-132`）；④release dex 搜文案验证 R8 折叠（`docs/os4-round10-audit.md:844-859`）；⑤截图与像素取色记录在 `docs/os4-round*-audit.md` | 上列文档 |
| 已知验证缺口（文档自述） | 真机玻璃厚度/高光/折射、`textureBlur+DstIn`、顶栏 AGSL 渐进模糊、触觉、屏幕圆角、手势导航遮挡、RTL、大字体、性能 —— 全部 🔴 | `docs/miuix-os4-migration-kit/18-*.md:259-283`；`docs/os4-round10-audit.md:732-741` |

> 升级影响：**没有回归测试兜底**，任何「不报错但观感变了」的默认值变化（§10-U）只能靠人工走查发现。建议升级前先补一个最小冒烟清单（见 §11）。

### 7.2 文档资产（`docs/` 共 39 个文件：20 篇知识库 + 11 篇审计/计划 + 5 篇 release notes + 截图/图标）

#### A. `docs/miuix-os4-migration-kit/` 20 篇（一句话摘要）

| # | 文件 | 摘要 |
|---:|---|---|
| 00 | `README.md` | 知识库总览：从 GifHelper 提炼的「OS4 + Glass 跨项目适配」方法与红线 |
| 01 | `01-project-assessment.md` | 新项目扫描与兼容性评估：只做判断不改码，产出基线报告与可行性结论 |
| 02 | `02-version-and-dependencies.md` | Miuix 版本与依赖选择：不要照抄坐标，给出确认方法、决策表与检查表 |
| 03 | `03-os4-theme-system.md` | OS4 主题系统：颜色/字号/圆角/背景收敛到语义角色，四种配色组合不塌 |
| 04 | `04-component-mapping.md` | 原生 UI → Miuix 组件映射与四种处置分类（A 直接替换 / 其余按判据） |
| 05 | `05-navigation-and-adaptive.md` | 导航、大屏与 Insets：形态选择、摆放、内容让位、安全区计算 |
| 06 | `06-glass-architecture.md` | Glass 架构重点：录制层/采样源/消费者模型与 R1–R8 红线（本知识库核心） |
| 07 | `07-glass-component-guide.md` | Glass 组件使用指南：各组件签名、默认值、是否必须传 `backdrop`、`renderInRootScaffold` |
| 08 | `08-custom-component-migration.md` | 自定义组件迁移判定流程 + 四个真实组件拆解（最易破坏玻璃层级/坐标） |
| 09 | `09-interaction-and-haptics.md` | 交互、触觉与无障碍：库自带的不要重复加、只在状态真变化时震 |
| 10 | `10-about-and-special-effects.md` | About 页、动态渐变与 `textureBlur`+`DstIn`：前提是采样源全程有像素 |
| 11 | `11-testing-and-acceptance.md` | 测试与验收：L1 代码接线 / L2 运行时能力 / L3 像素表现三级证据模型 |
| 12 | `12-known-issues-and-solutions.md` | 45 个真实问题案例库（C01–C45），按症状速查并标注可迁移性 |
| 13 | `13-project-adaptation-workflow.md` | 从 L0 到 L6：9 阶段 / 7 等级适配工作流与门禁（未验证不算完成） |
| 14 | `14-new-project-audit-template.md` | 新项目审计模板（阶段 0，只读，每条事实须带 `路径:行号`） |
| 15 | `15-new-project-migration-template.md` | 新项目实施计划模板（阶段 1~8，含完成判据与门） |
| 16 | `16-new-project-code-assistant-prompt.md` | 给代码助手的两阶段标准提示词（先审计模式 A，再实施模式 B） |
| 17 | `17-source-reference-index.md` | 官方文档、源码、提交与参考项目索引（每条结论可追溯） |
| 18 | `18-gif-helper-baseline-audit.md` | GifHelper 1.10 真实架构基线审计（本报告的前身与交叉校验源） |
| 19 | `19-l0-to-l6-walkthrough.md` | 虚拟项目从 L0 到 L6 的完整推演，用于自检知识库缺漏 |

#### B. 其他审计/计划文档（升级时值得当回归依据）

| 文件 | 一句话摘要 |
|---|---|
| `docs/os4-deep-audit.md` | 深度对照排查：P0/P1/P2 问题清单与实施顺序（首尾页越界拖动、底部操作区背景、顶栏降级链等） |
| `docs/OS4-Glass-UI-Plan.md` | OS4 Glass 全面适配实施方案：现状盘点、组件复用对照、图标规范、页面清单、验收清单 |
| `docs/os4-reuse-audit.md` | 「自造轮子」清理与官方复用报告：哪些自建被官方能力替换、哪些明确不采用 |
| `docs/os4-round5-audit.md` … `docs/os4-round11-audit.md`（7 篇） | 逐轮修复审计：玻璃弹层/下拉/顶栏空像素/Pager 坐标/配色/关于页 textureBlur 等真实事故与实测数字 |
| `docs/ui-audit.md` | UI 巡检记录（覆盖矩阵、已修复、记录未改、需真机项） |
| `docs/release-notes-1.4/1.7/1.8/1.9/1.10.md` | 各版本对外说明（可作为「行为基线」比对） |

---

## 8. 定制代码与官方代码的耦合度评估

> 分类口径：**S 安全**＝只组合官方公开组件、参数齐全，上游改默认值最多影响「我们不关心的部分」；**F 脆弱**＝依赖上游默认值、内部公式、布局/坐标假设，升级会「不报错但变样」；**C 复制件**＝把上游/生态代码搬进本仓库，需自行维护。

### 8.1 S 类（安全：组合官方组件）

| 项 | 位置 | 说明 |
|---|---|---|
| 列表行 / 分组标题 / 卡片骨架 | `HomeScreen.kt`、`Widgets.kt:64-67,149-167`、`LicensesScreen.kt` | `ArrowPreference`/`SmallTitle`/`Card` 都只覆盖必要参数 |
| 滑块/开关/文本框/进度指示 | `Widgets.kt:334-364`、`ToolScreen.kt:414-422,593-598` | 参数透传；几何吃默认（行高/内边距变化属于「全局统一变」，不影响功能） |
| 非玻璃顶栏 | `GlassScope.kt:290-302` | 官方 `TopAppBar` 公开参数齐全 |
| 页面骨架/内边距/滚动修饰 | `PageChrome.kt:173-349` | 组合方式官方（除 U06/U10/U11/U12 的默认值依赖） |
| 导航转场 | `BackTransitions.kt:24-33` 的 `NavTransitions.MiuixDefault` | 使用官方预设；自定义三档用公开 `navGraphicsTransition` |
| 弹层宿主 Scaffold | `App.kt:215-218` | 官方 `GlassPageHost` 同款用法 |
| 导航路由 | `App.kt:235-392`、`Route.kt` | `NavKey`/`NavDisplay` 公开 API |
| 数据/元数据/核心算法 | `AppState.kt`、`SettingsStore.kt`、`ToolCatalog.kt`、`LicenseCatalog.kt`、`core/*`、`task/TaskRunner.kt` | 与 Miuix 零耦合（`core/` 无 import） |

### 8.2 F 类（脆弱：依赖默认值 / 内部行为 / 布局假设）

| ID | 位置 | 依赖的具体上游行为 | 升级风险 |
|---|---|---|---|
| F01 | `PageChrome.kt:67-81` | `GlassNavigationBar` 内部未公开的 `floatingMargin` 公式（`<=16dp→28dp` 等）+ `Height=54dp` | **已过期**（HEAD 改为 20dp）→ 底栏占位多 8dp，列表末尾空带 |
| F02 | `App.kt:321-343,399-413` | 底栏/Rail 的 `height/style/shape/material/indicator*/unselectedColor/visible/showToggle` 全部默认 | 材质已改 `dynamicGlassMaterial`（Monet 下颜色/厚度变化）；`showToggle` 默认值变化会改变折叠入口 |
| F03 | `App.kt:133-150` | Rail 折叠形态是「顶部居中胶囊」、`progress` 语义、标题让位机制 | 上游 Rail 布局已拆文件（`GlassNavigationRail.kt` + `GlassMotion`/`NavigationRailInteraction`），折叠几何变化会同时影响让位与标题透明度 |
| F04 | `GlassScope.kt:245-288` | `GlassTopAppBar` 的 `style/material/titleBlurRadius/alpha/button*/defaultWindowInsetsPadding/bottomContent/centerObstructionStart` 默认；`bandBrush` 私有类型的双路径判断 | 顶栏 inset（statusBars→systemBars）、渐变曲线 0.5→0.25、材质 dynamic 化 → 观感变化；`defaultWindowInsetsPadding=true` 在刘海/横屏下多缩进（`docs/os4-deep-audit.md:306` P2-8） |
| F05 | `GlassScope.kt:226` | `GlassIconButton` 从 `LocalGlassTopAppBarContext` 继承 size/fill/stroke/shadow | 顶栏按钮尺寸/材质与顶栏上下文强绑定 |
| F06 | `PageChrome.kt:224,92-105` | `MiuixScrollBehavior()` 默认物理参数 + 直接写 `ScrollState.overscrollOffset` + `nestedScrollConnection` | 顶栏收起/回弹手感与 band 跟手；上游改吸附/上报节奏会「手感变了」 |
| F07 | `AnimatedTheme.kt:54-68` + `App.kt:333-337,405-409` | `MiuixTheme(colors=)` 缺 `LocalColorSchemeMode`（internal 不可补）→ `isDynamicColor` 恒 false；靠调用点显式 `selectedColor` 补偿 | 上游新增的 dynamic-color 感知默认值对 GifHelper 全部失效；补偿点只有底栏/Rail，Switch 拇指色等仍可能偏 |
| F08 | `AnimatedTheme.kt:72-129` | 53 个 `Colors` 角色手工枚举 | 新增角色静默不插值；改名/删除编译失败 |
| F09 | `Widgets.kt:312-322` + `PageChrome.kt:322-324` | `renderInRootScaffold=true`（默认）→ popup 由最外层 Scaffold 绘制、真实坐标 | 宿主机制/坐标语义变化 → 弹层「只剩材质、看不到背景」回归 |
| F10 | `LibraryScreen.kt:244-250,114-147` | `GlassBottomSheet`/`GlassDialog` 的 `insideMargin/material/containerColor/renderInRootScaffold` 默认 | 面板/对话框边距、材质、宿主变化 |
| F11 | `PageChrome.kt:339-347` + `SystemInsets.kt:31-36` | `Scaffold` 默认 `contentWindowInsets`，并在项目内**另写一份同样的公式** | 上游改默认 insets → 两处同时偏，或一处偏一处不偏 |
| F12 | `ui/Adaptive.kt:23-32,99-105` | 官方 example `AdaptiveUtils` 的阈值（840/600/1200dp） | 上游若调整阈值，本项目不会报错但切换时机不一致 |
| F13 | `App.kt:269-272,286-300` | `PagerNavigationSpringSpec`、`pagerGestureOverride` 的 `CrossAxis` 内部约束（须 `userScrollEnabled=false` + `PagerGestureNestedScrollConnection`） | 上游改手势接管实现，首尾页越界/手势冲突回归 |
| F14 | `GlassSupport.kt:25,45-60` | `isRenderEffectSupported()` 的「版本号判定」口径；渲染器字符串启发 | 上游改能力判定或新增能力探测 → 降级链错位 |
| F15 | `AboutScreen.kt:123-151,244-279` | `BlurDefaults.blurColors`、`BlurBlendMode` 枚举、`textureBlur` 的 `DstIn`/`MaxBlurRadius`、`CombinedBackdrop` 接口实现 | 混合色/半径/合成语义变化 → 关于页图标标题观感变化；接口加成员 → 编译失败 |
| F16 | 全项目图标 import（`ToolCatalog.kt:46-58`、`App.kt:68-71`、`SettingsScreen.kt:11-13`、`LibraryScreen.kt:36-41`、`ToolScreen.kt:72-73`、`Widgets.kt:52-53`、`GlassScope.kt:19-21`） | Glass 图标命名/画布（28dp）与 `Light` 档扩展属性 | 改名 → 编译失败（可发现）；画布变化 → 尺寸需重调（不报错） |
| F17 | `ui/ProgressButton.kt:50-97` + `Widgets.kt:551-576` + `ButtonColorPreview.kt:59-87` | `Surface(onClick…)` 重载参数、`ButtonDefaults.MinWidth`、6 个 disabled 颜色角色 | 角色改名编译失败；角色默认值变化静默改主按钮观感 |
| F18 | `ToolScreen.kt:142-169`、`TextField`（`:414-422`） | `BasicComponent` 的 8dp spacer / 56dp 行高 / 16dp 内边距 / `headline1`；`TextFieldDefaults.InsideMargin=16dp` | 行内对齐与「卡片 28dp 左边线」的整套结论失效 |
| F19 | `App.kt:227-234`、`SystemInsets.kt:45-95` | `NavDisplayEffects` 圆角裁剪 + platform 圆角/`RoundedCorner` API | 圆角裁剪半径与底部安全留白联动，设备 API 行为变化影响观感 |
| F20 | `AboutScreen.kt:52-60` + `res/drawable/ic_app_mark.xml` | 图标画布 108×108、可见主体 80 单位 → 采样尺寸 132dp | 换图标资源/上游 About 参考实现尺寸变化 → 图标视觉大小偏离 |

### 8.3 C 类（复制件：把上游/生态代码搬进本仓库）

| ID | 文件 | 来源 / 许可 | 维护风险 |
|---|---|---|---|
| C01 | `ui/bg/BgEffect.kt`（420 行） | miuix example `component/effect/*`（Apache-2.0，NOTICE 已声明 `NOTICE:15-18`） | 上游 example 的 OS3 背景可能继续演进而本副本停留；依赖 `RuntimeShader/asBrush/setFloatUniform` 与自定义 `Modifier.Node` 生命周期 |
| C02 | `ui/bg/BgEffectShader.kt`（96 行） | 同上（`OS3BgFrag.kt`） | AGSL 字符串与 uniform 名称是**跨文件契约**（`BgEffect.kt:308-317` 设值 → 本文件声明）；上游 material 若改着色器编译约定，运行期才暴露 |
| C03 | `ui/CombinedBackdrop.kt`（61 行） | miuix example `component/liquid/CombinedBackdrop.kt` | **直接 implements `Backdrop` 接口**（`miuix@5ff41ba9 Backdrop.kt:16-46`，含 `drawBackdrop(density, coordinates, layerBlock, downscaleFactor)`）→ 接口加成员/改签名即编译失败；语义变化（如下采样行为）则静默错 |
| C04 | `ui/ProgressButton.kt`（137 行） | InstallerX Revived `ProgressButton.kt`（Apache-2.0，文件头声明） | 几何/交互独立于官方 Button；上游 `Surface`/`ButtonDefaults`/颜色角色变化会传导；`docs/os4-round10-audit.md:763-765` 已记录几何不同源债务 |
| C05 | 近似复制（辅助函数） | `rememberPageBackdrop`（对标官方 `rememberBlurBackdrop`，`GlassScope.kt:96-117`）、`rememberBottomBarClearance`（镜像内部公式）、`pageContentPadding`/`pageScrollModifiers`（对标官方 `PageUtils`，`PageChrome.kt:84-163`）、`railStartInset`（对标官方 Rail 让位布局，`Adaptive.kt:53-78`） | 属于「为规避重组/坐标问题重写」而非逐字复制；但上游这些辅助函数若再演进，本仓库不会自动获得修复 |

> **许可/文档一致性小缺口**：`NOTICE:15-18` 只点名了 `ui/bg/BgEffect.kt`、`ui/bg/BgEffectShader.kt`；`CombinedBackdrop.kt` 与 `ProgressButton.kt` 只在文件头声明来源。升级整理 NOTICE 时可一并补齐（不影响编译）。

---

## 9. 基线升级冲突预判（按文件，按优先级）

> 「类型」列：**编译**＝签名/名称变化导致编译失败（安全，能发现）；**静默**＝编译通过但观感/行为变化（危险）；**运行**＝编译通过但运行期表现变化或降级。
> 「参照」列：`HEAD`＝本地 `miuix@5ff41ba9`；`区间`＝`3177a684..5ff41ba9` 已发生的 34 个提交中与本文件相关的变化。

| 优先级 | 文件 | 类型 | 原因 / 已观测到的变化（含参照） | 升级动作 |
|---|---|---|---|---|
| **P0** | `ui/PageChrome.kt` | 静默 | ①`rememberBottomBarClearance` 镜像公式已过期（HEAD `GlassNavigationBar.kt:321` 改 20dp）；②`MiuixScrollBehavior()`/`overscrollOffset` 依赖 ScrollState 语义（HEAD `TopAppBar.kt:358`；区间 `9155cd2a` 重构 ScrollBar+TopAppBar sync）；③`Scaffold` 默认 insets（`Scaffold.kt:90`） | 用 HEAD 公式改 20dp；把 `overscrollOffset` 写入点回归验证（顶栏 band 跟手）；检查 `contentWindowInsets` 是否仍为预期 |
| **P0** | `App.kt` | 静默 + 编译（低概率） | ①`GlassNavigationBar` 材质 dynamic 化、`selectedColor` 默认逻辑（`GlassNavigationBar.kt:238`）；②Rail 布局/折叠形态内部重写（`GlassNavigationRail.kt` 95 行变更、新增 `GlassMotion`/`NavigationRailInteraction`）；③`NavDisplayEffects`/Pager 手势栈 | 底栏/Rail 四态（窄/宽、展开/折叠）逐像素回归；确认 `selectedColor` 补偿是否仍必要（上游仍未提供 LocalColorSchemeMode）；Rail 让位 262dp 与标题淡出联动重测 |
| **P0** | `ui/GlassScope.kt` | 静默 + 编译（低概率） | `GlassTopAppBar`：顶栏 inset `statusBars→systemBars`（`GlassTopAppBar.kt:556`）、渐进模糊曲线 `0.5→0.25` 且新增 `enabled=bandActive` 门控（`:460-463`）、新增 `centerObstructionStart`（`:293/374`）、`buttonMaterial/buttonFill` dynamic 化 | 顶栏玻璃/非玻璃两态 × 明暗 × 横竖屏走查；确认 `bandBrush` 仍走 `TopAppBarFadeBrush`（`is TopAppBarBandBrush` 判断）；考虑是否传 `centerObstructionStart` |
| **P0** | `ui/AnimatedTheme.kt` | 编译（改名时）+ 静默 | `Colors` 53 角色枚举；`MiuixTheme(colors=)` 仍缺 `LocalColorSchemeMode`（HEAD `MiuixTheme.kt:55-72`、`ThemeController.kt:298` internal） | 编译若报 `copy()` 参数错误→按 HEAD `Colors.kt:68-120` 补/删；确认无新增角色漏插值；接受 `isDynamicColor=false` 并在调用点继续显式传色 |
| **P0** | `ui/Widgets.kt` | 编译（中概率）+ 静默 | `CardDefaults`（16dp/0dp/onSurfaceContainer）、`textureBlur`/`BlurDefaults`、`GlassDropdownPreference(renderInRootScaffold)`、`Slider/SwitchPreference` 参数、`InfiniteProgressIndicator` | 逐一核对 HEAD 签名；玻璃卡/下拉/主操作条/预览舞台四类组件回归 |
| **P1** | `ui/CombinedBackdrop.kt` | 编译（中概率） | implements `Backdrop`；HEAD 接口未变（`Backdrop.kt:16-46`），但区间内 blur 管线被大改（progressive blur 多提交） | 优先改成调用官方 `rememberCombinedBackdrop`（若 example 能力已进库），否则按接口逐项对齐 |
| **P1** | `ui/bg/BgEffect.kt` + `BgEffectShader.kt` | 运行 | `RuntimeShader/asBrush/setFloatUniform` 公开面（HEAD `RuntimeShader.kt:19-57,123-141`）；AGSL uniform 契约 | 真机/模拟器跑关于页渐变；确认着色器仍能编译（失败是静默退化） |
| **P1** | `screens/AboutScreen.kt` | 静默 + 运行 | `textureBlur` 的 `MaxBlurRadius`（HEAD/基线均为 `100f`，`BlurDefaults.kt:155`；`150f` 会被夹紧——这是既有事实，升级需确认该夹紧值未再变）；`DstIn` 行为；`rememberCombinedBackdrop` 两层叠加；`GlassCard` | 关于页图标/标题/卡片/顶栏四块一起回归；确认夹紧半径未变 |
| **P1** | `screens/LibraryScreen.kt` | 静默 | `GlassBottomSheet`/`GlassDialog` 默认材质/边距/宿主（HEAD 签名见 §3.2-F）；面板行内边距 0 的修正依赖 `BottomSheetDefaults.insideMargin=24dp` | 打开/关闭动画、遮罩、宿主层级、行内对齐回归 |
| **P2** | `ui/ProgressButton.kt` | 编译（低）+ 静默 | `Surface(onClick)` 重载、`ButtonDefaults.MinWidth`、6 个颜色角色 | 主操作条 busy/idle/disabled 三态 + 配色对比页对照 |
| **P2** | `ui/SystemInsets.kt` | 静默 | 与 `Scaffold` 默认 insets 同一公式的两处实现；`RoundedCorner` API | 横屏挖孔、大圆角机型底部留白回归 |
| **P2** | `ui/Adaptive.kt` | 静默 | 官方 example 阈值（840/600/1200dp）与 Rail 内部几何 | 三档宽度（竖屏/900dp 横屏/1200dp）切换时机与让位量回归 |
| **P2** | `ui/BackTransitions.kt` | 静默 | `NavTransitionScope.relativeDepth/layoutSize/layoutDirection` 语义 | 5 档转场 + 预测性返回跟手回归 |
| **P2** | `ui/ToolCatalog.kt`、`App.kt`、`SettingsScreen.kt`、`LibraryScreen.kt`、`ToolScreen.kt`、`Widgets.kt`、`GlassScope.kt` 的图标 import | 编译（高概率，但好修） | Glass 图标命名（`Folder→FolderOutline` 已发生过一次）；`Glass.Light.*` 扩展属性 | 按编译错误逐条改名；核对 28dp 画布与 `IconSizes` |
| **P3** | `screens/ToolScreen.kt` | 编译（低）+ 静默 | `BasicComponent`/`TextField`/`Button`/`SliderPreference`/`SwitchPreference` 默认 | 十个工具页逐页走查（行内对齐、滑块档位、结果卡） |
| **P3** | `ui/GlassSupport.kt` | 静默 | `isRenderEffectSupported()` 口径、渲染器字符串启发 | 确认降级链（玻璃开/关、软件渲染器）仍按预期 |
| **P3** | `screens/ThemeScreen.kt`、`screens/SettingsScreen.kt`、`screens/LicensesScreen.kt`、`screens/HomeScreen.kt` | 静默 | 纯组合，风险最低 | 随全量走查覆盖 |
| **—** | `core/*`、`task/TaskRunner.kt`、`AppState.kt`、`data/SettingsStore.kt`、`Route.kt`、`MainActivity.kt`、`GifApplication.kt`、`ui/AppInfo.kt`、`ui/LicenseCatalog.kt` | 无 | 与 Miuix 零耦合（`Route.kt` 仅 `NavKey` 接口） | 无需改动；`Route.kt` 只在 `NavKey`/`NavDisplay` 签名变化时受影响 |

---

## 10. 特别标注（升级时必须逐条核对）

### 10.1 U 表：依赖上游默认值 / 内部行为的位置

> 用法：升级后**不要只看编译是否通过**，逐条在真机/模拟器上确认「观感是否仍符合本项目设计」。`HEAD` 列 = 本地 `miuix@5ff41ba9` 当前值。

| ID | GifHelper 位置 | 依赖的上游默认值 / 内部行为 | HEAD 现状 | 变化后果 |
|---|---|---|---|---|
| U01 | `App.kt:215-218` | `Scaffold` 提供 `LocalRootDialogStates`/`MiuixPopupHost` | `Scaffold.kt:88,105-118` | 弹层/下拉静默不显示 |
| U02 | `App.kt:271` | `PagerNavigationSpringSpec` 手感 | `PagerGestureUtils.kt:80` | tab 吸附手感变化 |
| U03 | `App.kt:286-300` | `CrossAxis` 手势接管的内部约束（须关 Pager 原生 scrollable） | `PagerGestureUtils.kt:162-169` + example 用法 | 首尾页越界/手势冲突 |
| U04 | `App.kt:321-343` | `GlassNavigationBar` 的 `height(54)/style/shape/material/indicator*/visible/defaultWindowInsetsPadding` | `GlassNavigationBar.kt:221-241,119-152` | 底栏外观整体变化 |
| U05 | `App.kt:133-141` | `GlassNavigationRailDefaults.{ExpandedWidth=250,Margin=12}` | `GlassNavigationRail.kt:208,211` | 内容让位宽度错 |
| U06 | `PageChrome.kt:67-81` | `GlassNavigationBar` 内部 `floatingMargin`（未公开） | **已过期**：HEAD `321-323` 用 `20.dp`（本项目镜像 28dp） | 列表末尾多 8dp 空带/被底栏压住 |
| U07 | `App.kt:398-413` | Rail `showToggle=true`、`collapsedTop`、`labelStyle`、`defaultWindowInsetsPadding` | `GlassNavigationRail.kt:333-357`（`showToggle` 默认 true；`collapsedTop` 由 `TopAppBarDefaults.CollapsedHeight` 推导） | 折叠态形态/入口位置变化 |
| U08 | `GlassScope.kt:226` | `GlassIconButton` 从 `LocalGlassTopAppBarContext` 继承 | `GlassIconButton.kt:70-76` | 返回键按钮尺寸/材质变化 |
| U09 | `GlassScope.kt:260-288` | `GlassTopAppBar` 的 style/material/titleBlurRadius/alpha/button*/defaultWindowInsetsPadding/centerObstructionStart；`bandBrush` 私有类型判断 | `GlassTopAppBar.kt:268-297,423,470,556` | 顶栏观感/inset/模糊路径变化 |
| U10 | `PageChrome.kt:224` | `MiuixScrollBehavior()` 物理参数 | `TopAppBar.kt:266-273` | 顶栏收起/回弹手感 |
| U11 | `PageChrome.kt:92-105` | `ScrollState.overscrollOffset` 可写 + `overScrollVertical` 上报节奏 | `TopAppBar.kt:354-362`；`Overscroll.kt:48-53` | band 跟手/回弹抖动 |
| U12 | `PageChrome.kt:339-347` + `SystemInsets.kt:31-36` | `Scaffold` 默认 `contentWindowInsets`（并另写一份同公式） | `Scaffold.kt:90` | 全项目顶部/横向留白一起变 |
| U13 | `Widgets.kt:155-163` | `CardDefaults.{CornerRadius=16dp,InsideMargin=0dp,defaultColors(surfaceContainer)}` | `Card.kt:192-205` | 卡片圆角/行内边距/内容色 |
| U14 | `Widgets.kt:196-215` | `textureBlur` 参数语义、`BlurDefaults.blurColors` 的 remember、`MaxBlurRadius` 夹紧 | `TextureEffect.kt:27-34` | 玻璃卡质感（半径被夹紧） |
| U15 | `Widgets.kt:312-322` | `GlassDropdownPreference.renderInRootScaffold=true` + `visuals` 默认 | `GlassDropdownPreference.kt:63-80` | 下拉弹层材质/宿主/坐标 |
| U16 | `Widgets.kt:334-341` | `SliderPreference.insideMargin/sliderColors/hapticEffect/showKeyPoints=false` | `SliderPreference.kt:82-107` | 滑块行内边距/触觉/档位显示 |
| U17 | `Widgets.kt:358-364` | `SwitchPreference.switchColors` 默认（受 `isDynamicColor` 影响） | `SwitchPreference.kt:44-58` | Monet 下未选中拇指色 |
| U18 | `AnimatedTheme.kt:54-68` | `MiuixTheme(colors=)` 不提供 `LocalContentColor`/`LocalColorSchemeMode` | `MiuixTheme.kt:55-72`；`ThemeController.kt:298` | `isDynamicColor` 恒 false；所有 dynamic 分支失效 |
| U19 | `Widgets.kt:551-576`、`ButtonColorPreview.kt:59-87` | 6 个 disabled 颜色角色 | `Colors.kt:68-120` | 主按钮禁用态对比度 |
| U20 | `ProgressButton.kt:50-97` | `Surface(onClick…)` 重载、`ButtonDefaults.MinWidth` | `Surface.kt:89-99`；`Button.kt`（`MinWidth`） | 按钮几何/交互 |
| U21 | `AnimatedTheme.kt:72-129` | 53 个 `Colors` 角色清单 | `Colors.kt:68-120` | 新角色不插值（静默） |
| U22 | `ToolScreen.kt:142-169` | `BasicComponent` 16dp inside / 56dp min / 8dp spacer / `headline1` | `Component.kt:171,211,292` | 行高/对齐 |
| U23 | `ToolScreen.kt:414-422` | `TextFieldDefaults.InsideMargin=16dp` | `TextField.kt:85` | 与卡片 28dp 左边线对齐 |
| U24 | `LibraryScreen.kt:244-250` | `GlassBottomSheet` 的 insideMargin(24)/cornerRadius/material/containerColor/renderInRootScaffold | `GlassBottomSheet.kt:50-72` | 面板边距/材质/宿主 |
| U25 | `LibraryScreen.kt:114-147` | `GlassDialog` 的 insideMargin/material/containerColor/renderInRootScaffold；`GlassDialogButton.role` 默认 `Normal` | `GlassDialog.kt:48-81`；`GlassDialogButton.kt:71` | 对话框观感/按钮语义 |
| U26 | `AboutScreen.kt:123-151` | `BlurDefaults.blurColors`、`BlurBlendMode` 枚举、`CombinedBackdrop` | `BlurDefaults.kt`；`BlendColorEntry` | 关于页混合材质 |
| U27 | `AboutScreen.kt:244-279` | `textureBlur(DstIn)` + `MaxBlurRadius` | `TextureEffect.kt:27-34` | 图标/标题玻璃质感（真机项） |
| U28 | `BgEffect.kt:308-317` | `RuntimeShader/asBrush/setFloatUniform` + uniform 名称契约 | `RuntimeShader.kt:19-57,123-141` | 关于页渐变静默失效/花屏 |
| U29 | `GlassSupport.kt:25` | `isRenderEffectSupported()` 口径 | `RenderEffect.kt:7` | 玻璃能力判定 |
| U30 | `App.kt:227-234`、`SystemInsets.kt:45-95` | `NavDisplayEffects` 圆角裁剪 + platform 圆角 API | `NavDisplayEffects.kt:63-69` | 页面圆角裁剪/底部留白 |

### 10.2 C 表：复制进本仓库的上游代码（必须指定维护人/策略）

| ID | 文件 | 复制来源 | 上游是否还在演进 | 建议策略 |
|---|---|---|---|---|
| C01 | `ui/bg/BgEffect.kt` | miuix example `component/effect/*` | 是（example 不是发布 API） | 升级后 diff 一次 example 对应文件；若上游把背景能力做成组件，优先改用官方 |
| C02 | `ui/bg/BgEffectShader.kt` | miuix example `OS3BgFrag.kt` | 是 | 与 C01 同步；改动必须两端一起（uniform 名） |
| C03 | `ui/CombinedBackdrop.kt` | miuix example `component/liquid/CombinedBackdrop.kt` | 是（blur 管线 34 个提交里被多次改） | 优先改用官方 `rememberCombinedBackdrop`（若已入 `miuix-glass`/`miuix-blur`）；否则按 `Backdrop.kt:16-46` 接口逐项对齐 |
| C04 | `ui/ProgressButton.kt` | InstallerX Revived | 是（生态项目） | 决定是否改用 `Button` + 自绘进度（官方仍无进度语义）；至少同步 `Surface`/`ButtonDefaults`/颜色角色 |
| C05 | 复刻辅助函数（`rememberPageBackdrop`/`rememberBottomBarClearance`/`pageContentPadding`/`pageScrollModifiers`/`railStartInset`） | 官方 `rememberBlurBackdrop`/`PageUtils`/Rail 让位布局（非逐字复制） | 是 | 每个函数标注「对应上游文件」；升级后对上游对应文件 diff 一次，尤其 U06 已确认过期 |

---

## 11. 升级执行建议（可执行的顺序）

1. **对齐版本**：决定目标提交（若沿用当前工作区即 `5ff41ba9`），同步修改 `AGENTS.md:266`、`docs/miuix-os4-migration-kit/README.md:5`、`02-version-and-dependencies.md:9-10`、`scripts/setup-miuix.sh:21`；确认 `build.gradle.kts:56-60` 的 compileSdk 仍满足上游要求。
2. **先编译，拿错误清单**：`./gradlew :assembleDebug`。把错误归到两类：①图标/命名的直接改名；②`Backdrop`/`Colors.copy()`/参数签名变化的结构性改动。**不要**在同一轮里顺带改视觉。
3. **修 §9 P0 的「静默项」中的确定性错误**：至少先修 U06（底栏占位 28→20dp，`PageChrome.kt:73-77`），否则所有列表页底部都会出现可量测的空带/压盖。
4. **跑最小冒烟矩阵（无自动化，必须人工）**：
   - 竖屏 540×1200：静态浅/深、Monet 浅/深 × 玻璃开/关，各看 4 个一级页 + 工具页 + 关于页；
   - 横屏 1200×540：Rail 折叠/展开两态，检查让位 262dp、顶栏标题淡出、录制层满宽；
   - 弹层：作品操作面板（BottomSheet）、删除确认（Dialog）、主题页下拉（Dropdown）各开关一次；
   - 关于页：玻璃开/关、滚到底、顶栏渐变带是否连续；
   - release 包：搜「按钮配色对比」确认 R8 仍折叠。
5. **逐项过 §10-U 表**：在冒烟中把每一条「预期观感」写进验收记录；尤其 U04/U07/U09/U13/U24/U25（外观类）与 U01/U06/U11/U12/U15（几何/宿主类）。
6. **对 C 表做一次决策**：C03（`CombinedBackdrop`）优先评估「改用官方实现」；C01/C02 评估是否已被官方组件取代；C04 决定是否继续维护副本。
7. **补验证兜底**：至少新增一个 `androidTest`/纯函数单测覆盖「阈值/圆角安全区/`steps` 语义」这类纯计算（当前为零，`docs/miuix-os4-migration-kit/18-*.md:296`），并把本报告 §11.4 的冒烟矩阵固化成 checklist 放 `docs/`。
8. **更新文档**：升级完成后回填 `AGENTS.md` 的「构建环境的坑」与知识库 02/18/07（组件默认值表），否则下一轮升级又会从「文档说 3177a684」开始。

---

## 附：本次审计的取证边界（诚实声明）

- 只读：未构建、未运行模拟器、未改任何源码/配置/文档（本文件除外）。
- 「编译失败/静默变化」判断依据：GifHelper 调用点参数 vs 本地 `miuix@5ff41ba9` 声明/默认值逐项比对；**未用编译器验证**，最终以 `assembleDebug` 报错为准。
- 上游行号均为本地 `miuix@5ff41ba9`；`AGENTS.md`/知识库引用的 `3177a684` 行号未逐一重核（若目标提交不同，§10 表中「HEAD 现状」列需按目标提交重查）。
- 真机项（玻璃厚度/折射/`textureBlur`/AGSL/触觉/RTL/大字体）本次无法验证，沿用文档中的 🔴 状态。
