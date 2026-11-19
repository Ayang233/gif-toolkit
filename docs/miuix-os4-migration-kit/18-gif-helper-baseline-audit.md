# 18 · GIF 助手 1.10 真实架构基线（本次审计）

> 这是"第一阶段：全面审计 GIF 助手"的产出。**全部基于当前工作区源码重新检查**，
> 不引用以前的审计报告作为事实来源（旧报告只用于定位)。
> 审计时间：2026-11-19；代码版本：`c2108c6`（`versionName 1.10` / `versionCode 12`）。

---

## 1. 审计方法与边界

| 项 | 内容 |
|---|---|
| 范围 | `GifHelper/src/**`（全部 Kotlin + XML + Manifest）、`build.gradle.kts`、`settings.gradle.kts`、`gradle.properties`、`AGENTS.md`、`docs/os4-*.md` |
| 方法 | 逐文件精读 + 全项目 grep + 与 `../miuix` 源码交叉核对 |
| 未做 | 未构建、未跑模拟器、未真机（本轮只做源码审计）；未修改任何文件 |
| 对照基线 | MIUIX `3177a684`（`miuix/`，2026-11-18） |

---

## 2. 技术架构

| 项 | 值 | 依据 |
|---|---|---|
| 语言 / 平台 | Kotlin Multiplatform（**单模块**，实现全在 `androidMain`） | `build.gradle.kts:5-6`；`AGENTS.md:7` |
| AGP | `9.5.1` | `build.gradle.kts:4` |
| Kotlin | `2.4.21` | `:5` |
| Compose Multiplatform | `1.12.2` | `:7`、`:19-21` |
| JVM target | 21 | `:14` |
| compileSdk | `release(37) { minorApiLevel = 2 }` | `:56-60` |
| minSdk / targetSdk | `33` / `36` | `:65-66` |
| 导航 | **`miuix-nav`** 的 `NavDisplay` + `navBackStackOf` | `App.kt:72-75`、`:119`、`:235` |
| UI 状态 | `AppState`（`mutableStateOf`）+ 每个工具一份 `ToolForm` + `SettingsStore`（SharedPreferences） | `AppState.kt`、`data/SettingsStore.kt` |
| 主题 | `ThemeController` + **自建逐色插值** `AnimatedMiuixTheme` | `ui/AnimatedTheme.kt` |
| 页面组织 | 单 Activity + 一级页 `HorizontalPager` + `NavDisplay` 二级页 | `App.kt:241-346` |
| Insets | `enableEdgeToEdge()` + Scaffold `innerPadding` + 自建 `rememberHorizontalSafeInsets` | `MainActivity.kt:12`；`ui/SystemInsets.kt` |
| material3 | **零 import**（全项目 grep `androidx.compose.material` 无命中） | 硬约束 7 |
| 构建特性 | `BuildConfig.DEBUG_UI` 字面量布尔，release 里 R8 删掉整个调试页 | `build.gradle.kts:71-85` |

### 2.1 依赖

📌 `build.gradle.kts:17-48`：

- `commonMain`：compose runtime/foundation/ui 1.12.2、`navigationevent-compose:1.2.0`、
  `kotlinx-serialization-core:1.13.0`
- MIUIX：`miuix-ui` / `-preference` / `-icons` / `-material` / `-squircle` / `-glass` / `-glass-icons` / `-nav`
- `hiddenapibypass:6.1`（反射 `ApplicationInfo.setEnableOnBackInvokedCallback`）
- `androidMain`：`activity-compose:1.13.0`、`core-ktx:1.17.0`、`lifecycle-runtime-compose:2.9.4`、
  `coroutines-android:1.10.2`

MIUIX 通过 **composite build** 引入（`settings.gradle.kts:56-63` 的 `includeBuild(miuixDir)`），
并给上游 `build-logic` 打了 `workingDir = rootDir` 补丁。

---

## 3. 源码地图（30 个 Kotlin 文件）

| 文件 | 角色 |
|---|---|
| `App.kt`（433 行） | 主题装配、CompositionLocal 下发、弹层宿主 Scaffold、`NavDisplay`、一级页 Pager、玻璃底栏/侧栏 |
| `MainActivity.kt` | `enableEdgeToEdge()` + `isNavigationBarContrastEnforced = false` |
| `GifApplication.kt` | 预测性返回开关（反射隐藏 API） |
| `Route.kt` / `AppState.kt` | 路由（`NavKey`）/ 全局状态 |
| `ui/AnimatedTheme.kt` | 逐色插值主题（53 个角色） |
| `ui/Adaptive.kt` | 宽屏判定、`LocalRailInset`、`railStartInset`、`LocalTopBarTitleAlpha`、1200dp 阈值 |
| `ui/SystemInsets.kt` | 横向安全区、设备圆角、`safeBottomGap` |
| `ui/GlassSupport.kt` | **玻璃唯一决策点**、`isSoftwareRenderer` |
| `ui/GlassScope.kt` | `GlassScope`、`LocalPageBackdrop`、`LocalRootBackdrop`、`rememberPageBackdrop`、`PageTopBar`、`ContentBackdropBox` |
| `ui/CombinedBackdrop.kt` | 两层 backdrop 叠加 |
| `ui/PageChrome.kt` | `Os4ListPage` 骨架、`pageContentPadding`、`pageScrollModifiers`、`LocalBottomBarClearance` |
| `ui/Widgets.kt` | 三层配色、`GroupCard`/`GlassCard`、`DropdownRow`、`PrimaryActionBar`、`PreviewStage`、`IconSizes` |
| `ui/ProgressButton.kt` | 按钮内嵌进度（移植，Apache-2.0） |
| `ui/ButtonColorPreview.kt` | 仅 debug 的按钮配色对比页 |
| `ui/BackTransitions.kt` | 5 档返回转场 |
| `ui/bg/BgEffect.kt` + `BgEffectShader.kt` | OS3 动态渐变（AGSL 运行时着色器） |
| `ui/ToolCatalog.kt` / `ui/LicenseCatalog.kt` / `ui/AppInfo.kt` | 元数据 |
| `screens/`（8 个） | Home / Tool / Library / Settings / Theme / About / Licenses / LicenseDetail |
| `task/TaskRunner.kt`、`core/` | 业务逻辑（与 UI 无关） |

---

## 4. 顶层装配顺序（`GifApp`）

```text
GifApp                                    App.kt:86
├─ AppState(SettingsStore)                :88
├─ mode = ThemeMode × monet → ColorSchemeMode   :92-104
├─ ThemeController(mode)                  :105
├─ glassEnabled = app.glassEnabled && isGlassSupported()   :110
├─ wide = rememberWideLayout()            :114
├─ bottomClearance = rememberBottomBarClearance()   :116
├─ stack = navBackStackOf(Route.Root)     :119
├─ showPrimaryNav = stack.size <= 1       :121
├─ expandRail = shouldExpandNavigationRail()  :125
├─ railState = rememberGlassNavigationRailState(initialExpanded = expandRail)  :126
├─ railVisible = wide && showPrimaryNav   :128
├─ railInset: () -> Dp                    :133-141
├─ CompositionLocalProvider(Glass/Wide/Amoled/RailInset/TitleAlpha/BottomBarClearance)  :143-152
└─ AnimatedMiuixTheme(controller)         :153
   ├─ SideEffect：系统栏图标反色          :157-164
   ├─ rootBackdrop = rememberPageBackdrop()   :170
   ├─ Pager（3 个一级页）+ tab/BackHandler   :172-201
   └─ Scaffold(Transparent, insets=0) ← 弹层宿主   :215-218
      └─ Box(background(pageBaseColor()))   :219
         ├─ NavDisplay(transition, effects) :235
         │  └─ entry<Route.Root>            :241
         │     └─ Box                     :250
         │        ├─ Box.layerBackdrop(rootBackdrop) ★   :253-256
         │        │  └─ Provider(LocalRootBackdrop)  :262
         │        │     └─ HorizontalPager(CrossAxis)  :273
         │        │        └─ 三个一级页 → Os4ListPage
         │        └─ GlassNavigationBar(backdrop=rootBackdrop)  :321
         └─ GlassNavigationRail(backdrop=rootBackdrop)  :399
```

**为什么底栏在 `entry<Route.Root>` 里面**：为了让底栏跟着一级页被二级页**盖住**，
而不是靠 `stack.size` 挂载/卸载（那会导致"闪没"，案例 C20）。这是**本项目结构选择**。

---

## 5. Glass 接线全景

### 5.1 三个采样源

| 名称 | 类型 | 创建 | 提供者 | 消费者 |
|---|---|---|---|---|
| `rootBackdrop` | `LayerBackdrop`（一级页整屏，录在 Pager 外） | `App.kt:170` | `App.kt:263`（`LocalRootBackdrop`） | 底栏、侧栏、**所有页面级弹层**、下拉 |
| `contentBackdrop` | `LayerBackdrop`（页面正文） | `GlassScope.kt:130` → `rememberPageBackdrop()` | `GlassScope.kt:319`（`LocalPageBackdrop`） | 顶栏、页面内下拉（二级页回退） |
| `bgBackdrop` | `LayerBackdrop`（只录渐变层） | `AboutScreen.kt:118` | 直接传参 | 关于页图标/标题/玻璃卡；与正文层叠加后给顶栏 |

### 5.2 录制层挂载点（全部 4 处）

| 位置 | 挂载 | 兄弟是谁 |
|---|---|---|
| 一级页整屏 | `App.kt:256` `Modifier.layerBackdrop(rootBackdrop)` | 底栏（`:321`）、Rail（`:399`）、弹层（最外层 Scaffold） |
| 页面正文 | `GlassScope.kt:320`（`ContentBackdropBox`） | 顶栏（`PageChrome.kt:333` 的 `topBar` 槽） |
| 关于页渐变 | `AboutScreen.kt:163` `bgModifier = Modifier.layerBackdrop(bgBackdrop)` | 顶栏、图标、卡片、正文层 |
| 弹层 | 无（弹层本身**不录**，只消费） | — |

### 5.3 采样源选择规则

```kotlin
// 页面级弹层（PageChrome.kt:322-324）
pageOverlay(LocalRootBackdrop.current ?: glass.contentBackdrop.takeIf { glass.enabled })

// 下拉（Widgets.kt:311）
val backdrop = LocalRootBackdrop.current ?: LocalPageBackdrop.current
```

规则：**一级页（有 Pager）必须用 root 层；二级页（无 Pager）才回落页面层。**
原因是坐标空间不同（C16：`offset.x = -1080`）。

### 5.4 关键红线在本项目的落地

| 红线（[06](06-glass-architecture.md)） | GIF 助手实现 |
|---|---|
| R1 消费者不能是录制层后代 | 底栏/侧栏/顶栏/弹层全部是兄弟 |
| R2 录制层不上移 Scaffold | 只包 Pager；正文层在内容槽 |
| R3 采样区域覆盖 | 关于页用 `CombinedBackdrop` 叠两层 |
| R4 先垫底色 | `rememberPageBackdrop` 先 `drawRect(pageBaseColor())`；关于页 `opaqueBase = false` |
| R5 不重复录制 | 卡片不自带录制层；`beyondViewportPageCount = 1`（官方 4） |
| R6 前景用 `contentModifier` | `GlassScope.kt:271` |
| R7 录制层不收窄 | `App.kt:253-256` 满宽 |
| R8 组合期不解包 | `LocalRailInset` 是 `() -> Dp`；`railStartInset` 量测期读 |

---

## 6. 主题系统

- `AnimatedMiuixTheme` 用 `MiuixTheme(colors = …)` 重载 + 53 个角色逐色插值
  （`tween(320, FastOutSlowInEasing)`），并补 `LocalContentColor`（`AnimatedTheme.kt:63-68`）。
- **丢了 `LocalColorSchemeMode`**（库内 `internal`，补不了）→ 底栏/侧栏在调用点显式传 `selectedColor`
  （`App.kt:333-337`、`:405-409`）。这是 `MiuixTheme(colors=)` 的已知代价。
- 三层配色：`pageBaseColor()`（`surface`）/ `pageColor()`（卡片色反推）/ `cardColor()`（`surfaceContainer`）。
  AMOLED 经 `LocalAmoled` 生效（`Widgets.kt:75-138`）。
- 无自定义字体/字号表（grep `Typography|fontFamily|textStyles =` 零命中）。
- 圆角散落在调用点（`CardDefaults.CornerRadius`、`RoundedCornerShape(12.dp)` 等），没有主题级 shape。

---

## 7. 导航与自适应

| 项 | 实现 | 位置 |
|---|---|---|
| 宽屏判定 | `width ≥ 840dp` 或（`≥600dp` 且高宽比 < 1.2） | `Adaptive.kt:30` |
| Rail 展开 | `width ≥ 1200dp` | `Adaptive.kt:104` |
| 让位 | `(ExpandedWidth 250 + Margin 12) * progress` → 262dp | `App.kt:133-141` |
| 只让起始边 + 横向安全区 | `pageContentPadding` 的自定义 `PaddingValues` | `PageChrome.kt:146-162` |
| 量测期读取 | `Modifier.railStartInset(base)`（`layout` + `placeRelative`） | `Adaptive.kt:67-78` |
| 底栏占位 | `Height(54) + floatingMargin + 12`（镜像库内部公式） | `PageChrome.kt:67-81` |
| 横向安全区 | `systemBars ∪ displayCutout` | `SystemInsets.kt:32-36` |
| 设备圆角 | `getRoundedCorner(POSITION_BOTTOM_LEFT)` → framework dimen → 0 | `SystemInsets.kt:45-64` |
| 圆角安全区 | `d = R − sqrt(2R·g − g²)`，减导航栏、下限 12dp | `SystemInsets.kt:79-95` |
| 转场 | `NavDisplayEffects(enableCornerClip, cornerClipRadius, dimAmount=.5f, backdropColor)` | `App.kt:227-234` |
| 返回 | `BackHandler(enabled = stack.size <= 1 && tab != 0)`；tab 0 交还系统 | `App.kt:199-201` |

---

## 8. 组件使用清单

### 8.1 Glass（6 个）

`GlassTopAppBar`（`GlassScope.kt:260`）、`GlassIconButton`（`:226`）、`GlassNavigationBar`（`App.kt:321`）、
`GlassNavigationRail`（`:399`）、`GlassDropdownPreference`（`Widgets.kt:312`）、
`GlassBottomSheet`（`LibraryScreen.kt:244`）、`GlassDialog` + `GlassDialogButton`（`:114`、`:126`）。

### 8.2 普通 MIUIX

`Scaffold`、`TopAppBar`（非玻璃档）、`Card`/`CardDefaults`、`Text`、`Icon`、`IconButton`、`Button`、
`BasicComponent`、`TextField`、`InfiniteProgressIndicator`、`Surface`、`SmallTitle`、
`MiuixScrollBehavior`、`overScrollVertical`、`scrollEndHaptic`、`pagerGestureOverride`、`springAnimateToPage`。

### 8.3 Preference

`SwitchPreference`、`SliderPreference`、`ArrowPreference`、`SmallTitle`（均经自建薄封装）。

### 8.4 图标

全部 **Glass 族**（`MiuixIcons.Glass.*` + `MiuixIcons.Glass.Light.*`），唯一例外是
非玻璃档顶栏返回键用 extended `MiuixIcons.Back`（官方同款分档）。尺寸统一走 `IconSizes`
（Row 26 / Action 28 / Inline 19 / Empty 44 / Media 28）。

### 8.5 未使用但相关的官方组件

`GlassTabRow`、`GlassSegmentedTabRow`、`GlassFloatingActionButton`、`GlassFloatingToolbar`、
`GlassPopup` 系列、`GlassTextField`、`GlassIconDropdownMenu`、`glassSurface`、`Modifier.glassPanel`、`Modifier.glass`、
`RadioButtonPreference`、`CheckboxPreference`、`WindowDropdownPreference`。

---

## 9. 触觉

全项目 `haptic` 命中 8 处，其中**业务手写只有 1 处**：
色板选色时 `HapticFeedbackType.ToggleOn`（`ToolScreen.kt:378-385`，且仅 `!selected` 时）。
其余来自库：`scrollEndHaptic`（`PageChrome.kt:101`）、`Switch`/`Slider`/`GlassDropdownPreference` 内部。

---

## 10. 关于页与特效

- 骨架：`Os4ListPage`（`background` + `largeHeader` + `topBarBackdrop`）。
- 渐变：`BgEffectBackground`（AGSL；`isRuntimeShaderSupported()` 为假时退化为 Box）。
- 图标/标题：`textureBlur(backdrop = bgBackdrop, shape = RectangleShape, blurRadius = 150f,
  colors = BlurDefaults.blurColors(logoBlend), contentBlendMode = DstIn)`。
- 尺寸：容器 100dp、主体 `requiredSize(132dp)`、容器 35sp Bold / 版本 14sp。
- 卡片：`GlassCard(backdrop = bgBackdrop)`；`backdrop == null` 时退回不透明卡。
- 淡出阈值：版本 0.05 / 名称 0.20 / 图标 0.35（各 0.15 宽，缩到 0.95）。
- 软件渲染：`blurEnabled = glassEnabled && !isSoftwareRenderer`，跳过 `textureBlur`。

---

## 11. 自定义组件

`ProgressButton`（移植）、`PrimaryActionBar`、`PreviewStage`、`GroupCard`、`GlassCard`、
`DropdownRow`、`SwitchRow`、`SliderRow`、`NoteText`、`LeadingIcon`、`GroupHeader`、`IconSizes`、
自绘色板（`ToolScreen` 内）、`BgEffectBackground`（移植）。

---

## 12. 验证状态矩阵（诚实版）

| 项 | 状态 | 证据 |
|---|---|---|
| 构建 debug/release | ✅ | 多轮 `assembleDebug`/`assembleRelease` 通过 |
| release 无调试字符串 | ✅ | dex 逐字节搜索 0/0/0（debug 对照 1/1/1） |
| 页面结构与布局 | ✅ | 多尺寸模拟器截图 + 像素测量 |
| 四种配色层次 | 🟡 | 模拟器逐点取色（静态/Monet × 明/暗） |
| 侧栏让位（1200dp 横屏） | ✅ | 338/324.5/13.5dp 实测 |
| 无自采样闪退 | ✅ | 反复进出 + logcat |
| 玻璃**模糊是否存在** | 🟡 | 仅模拟器（`swiftshader` 不可信） |
| 玻璃厚度/高光/折射 | 🔴 | 未真机 |
| `textureBlur` + `DstIn` | 🔴 | 真机未验；模拟器不可见 |
| 顶栏渐进模糊（AGSL） | 🔴 | 真机未验 |
| 触觉手感 | 🔴 | 模拟器无马达 |
| 屏幕圆角安全区 | 🔴 | 模拟器圆角 = 0 |
| 手势导航条遮挡 | 🔴 | 未验 |
| RTL | 🔴 | 未切语言 |
| 大字体（≥1.6） | 🔴 | 推断有压内容风险 |
| 性能/掉帧/发热 | 🔴 | 未测 |
| 1.10 交互回归 | 🔴 | 审计文档明确"没有在 1.10 上重跑模拟器交互" |

> 结论：**L1 源码接线 ✅ / L2 运行时能力 ✅（API 36）/ L3 像素表现 部分 🟡、关键项 🔴**。
> 按 [13](13-project-adaptation-workflow.md) §11.5，GIF 助手当前评级为 **L5（模拟器级）**，
> L6 缺真机证据。

---

## 13. 已知债务与文档漂移（本次审计新发现）

| # | 问题 | 证据 | 影响 |
|---|---|---|---|
| D1 | `miuix-squircle` 被声明为依赖，但**源码零使用**（grep `squircle` 零命中） | `build.gradle.kts:32-35` 注释称"关于页的应用图标用它" | 注释与代码不一致；且 `miuix-ui` 已 `api(squircle)`，该依赖冗余 |
| D2 | `rememberPageBackdrop` 的注释写"已补上 `drawRect(pageColor())`"，实际是 `pageBaseColor()` | `GlassScope.kt:68-70` vs `:110-114` | 注释会误导后来者用错颜色（正是 C14 的坑） |
| D3 | README 写"`textureBlur` 真机正常"，但各轮审计均记"真机待验证" | `README.md:143-145` vs R8 §9 / R9 §7 / R10 §8.4 / R11 | 对外断言缺证据（见 [12](12-known-issues-and-solutions.md) B16） |
| D4 | 历史上 `AGENTS.md` 第 5 条写"分段控件用自绘 `SegmentedControl`"，该组件从未存在 | 已在 R10 更正；全项目 grep 零命中 | 已修，但说明"文档说有"必须 grep 验证 |
| D5 | 关于页 1.10 合并回骨架后，**未做交互回归** | R11 §7.4 自述 | L5 结论建立在未回归的代码上 |
| D6 | 无测试源集（`src/**/test`、`androidTest` 均不存在） | 目录检查 | 纯函数（阈值、圆角安全区、`steps`）无单测 |

---

## 14. 与知识库的对应关系

| 基线章节 | 对应方法文档 |
|---|---|
| §2 技术架构 | [01](01-project-assessment.md)、[02](02-version-and-dependencies.md) |
| §4 顶层装配 | [13](13-project-adaptation-workflow.md) 阶段 2/4 |
| §5 Glass 接线 | [06](06-glass-architecture.md)、[07](07-glass-component-guide.md) |
| §6 主题 | [03](03-os4-theme-system.md) |
| §7 导航与 Insets | [05](05-navigation-and-adaptive.md) |
| §9 触觉 | [09](09-interaction-and-haptics.md) |
| §10 关于页 | [10](10-about-and-special-effects.md) |
| §11 自定义组件 | [08](08-custom-component-migration.md) |
| §12 验证状态 | [11](11-testing-and-acceptance.md)、[13](13-project-adaptation-workflow.md) §11 |
| §13 债务 | [12](12-known-issues-and-solutions.md) |

---

## 15. 审计结论

1. **GIF 助手是一个"结构正确"的参考工程**：Glass 的四类宿主、两类录制层、单一决策点、
   量测期读取、三层配色都已落地，并且有真实事故与实测数字支撑。
2. **它的价值在于"红线"而不是"代码"**：`Os4ListPage` / `GlassScope` / `LocalRootBackdrop` 是为
   "Pager + 玻璃底栏 + 二级页"这套结构定制的，**新项目不能照抄**。
3. **它的短板是"真机证据"**：所有玻璃观感与触觉仍是 🔴；`textureBlur` 只在软件渲染上被观察为"不可用"。
4. 因此本知识库把结论分成三级证据，并在 [13](13-project-adaptation-workflow.md) §11 用
   "未验证不算完成"来约束评级 —— 这正是从 GIF 助手的经验里学到的最重要一条。
