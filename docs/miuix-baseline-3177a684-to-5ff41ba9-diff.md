# Miuix 基线 3177a684 → 5ff41ba9 差异分析（面向 GifHelper）

- 分析对象：`../miuix`（git 仓库），区间 `3177a684..5ff41ba9`，共 **34 个提交**，198 个文件、+18435/−4307。
- 取证方式：`git log/log -S/-G`、`git diff --stat/-U0/-U3`、`git show <rev>:<file>`，均只读；未改动 miuix 仓库任何状态。
- 分析实现：`.`（36 个 `.kt`，composite build 通过 `settings.gradle.kts` 的 `includeBuild(../miuix)` 引入）。
- 环境备注（重要）：分析期间该 miuix 仓库实际处于 **detached HEAD = 5ff41ba9**，工作区仅一处本地补丁 `build-logic/src/main/kotlin/BuildConfig.kt`（composite build 必需，与本次差异无关）。任务描述里的"当前 HEAD 3177a684"与现场不符，本报告不依赖 HEAD，全部结论用两个 commit 号对取。
- 版本 pin 现状不一致：`scripts/setup-miuix.sh:21` 的 `PINNED_REF="d45ca181"`、`build.gradle.kts:53` 注释写的是 `0.9.5-beta01（3177a684）`。**两处都应在升级后同步改为 5ff41ba9**，否则重跑 `setup-miuix.sh` 会把源码切回别的提交。

---

## 0. 结论摘要（先看这 10 条）

| # | 结论 | 性质 |
|---|---|---|
| 1 | **GifHelper 引用的 miuix 公开 API 没有一处被删除/重命名/移动**。逐一核对了 36 个源文件里全部 88 条去重后的 `top.yukonga.*` import（basic / blur / glass / material / icon / nav / preference / theme / utils），**本次区间不会产生编译失败**。 | 无影响 |
| 2 | 全部 8 个 glass 组件（`GlassTopAppBar`/`GlassNavigationBar`/`GlassNavigationRail`/`GlassDialog`/`GlassBottomSheet`/`GlassDropdownPreference`/`GlassTabRow`/`GlassSegmentedTabRow`）**公开参数列表逐字未变**；唯一签名变化是 `GlassTopAppBar` **新增** 可选参数 `centerObstructionStart: Dp = Dp.Unspecified`（`GlassTopAppBar.kt:293/:374`）。GifHelper 全部用命名实参，不受影响。 | 无影响（编译期） |
| 3 | `GlassTopAppBarDefaults` 的 blur 半径（80dp）、ramp 距离（48dp）、band 溢出（36dp）、标题模糊（8dp）、按钮尺寸（44dp）等默认值**一个都没改**。 | 无影响（默认值） |
| 4 | 但顶栏渐进模糊 **曲线从 0.5 改成 0.25**（`GlassTopAppBar.kt:460`，commit 49318153），配合新的 2x 桥接/着色器特化：`radius = maxR * (1 - smoothstep(t)^curve)`，curve 0.5→0.25 会让带内中段的模糊半径变小、能量更集中在带顶。**这是本次最可能被肉眼看见的玻璃视觉变化**，必须实机对比。 | 视觉行为变化 |
| 5 | 顶栏新增 **静止时跳过录制层采样**（`GlassTopAppBar.kt:434-448`，commit 4341c5c3）：`bandActive=false` 且未滚动时不挂 `layerBackdrop`/`onGloballyPositioned`。GifHelper 的返回键是 `GlassIconButton(inheritTopBarMaterial=true)`（`GlassScope.kt:226`），会注册为 consumer，但静止时按钮本来就走 fill 兜底（keepMaterial=false），所以**不产生静止态视觉回归**，属于纯性能优化；代价是开始滚动后的第一帧录制层坐标可能还是旧值。 | 性能优化（视觉无变化） |
| 6 | **`GlassNavigationBar` 悬浮底边距规则改了：`systemBottom ≤ 16dp` 分支 28dp → 20dp**（`GlassNavigationBar.kt:321`，commit 9413f690）。GifHelper 在 `PageChrome.kt:70-83` **复刻了这条公式**且仍按 28dp 算，于是升级后底部留白会多出 8dp、胶囊会比内容预留的位置低 8dp。**这是本次唯一确定会错位的"吃默认值"耦合点**，必须改。 | 视觉行为变化（需适配） |
| 7 | `GlassNavigationBar` 新增横向安全区 padding（`displayCutout ∪ navigationBars` 的 Horizontal，`GlassNavigationBar.kt:331-337`），且加在**调用方 modifier 之外**。GifHelper 传的是 `padding(horizontal=24.dp)`（`App.kt:338-342`），横屏/挖孔设备上胶囊可用宽度会再减掉挖孔宽度。 | 视觉行为变化 |
| 8 | 上游所有"动态取色"相关基线变化（`dynamicGlassMaterial`、`GlassTabRow`/`GlassSegmentedTabRow` 配色、`buttonFill`/`containerColor`、`GlassDialogButton` disabled alpha 等）对 GifHelper **全部空转**：GifHelper 用 `MiuixTheme(colors = …)` 重载（`AnimatedTheme.kt:78`），从不提供 internal 的 `LocalColorSchemeMode`，而 `MiuixTheme.isDynamicColor` 的默认值是 `null → false`（`ThemeController.kt` 中 `staticCompositionLocalOf { null }` + `MiuixTheme.kt:87-96`）。**但一旦 GifHelper 改用 `MiuixTheme(controller)`，这批新染色会立刻生效，OS4 玻璃观感会变**。 | 无影响（当前）/ 潜在风险 |
| 9 | `GlassBottomSheet` / `GlassDialog` / `GlassDropdownPreference` 的签名、默认材质**外观**未变（材质工厂外层多包了一层 `dynamicGlassMaterial`，`isDynamicColor=false` 时逐字节等价）。但 `GlassDropdownPreference` 的**弹层锚点语义变了**：从整行 bounds 改为"值+箭头"bounds，且拿到新 bounds 前不弹出（`GlassDropdownPreference.kt:116/157/165`，commit e875daf8）。作品页/设置页的玻璃下拉位置与出现时机可能变化。 | 交互/视觉变化 |
| 10 | 底层模糊管线被重写（7 个 blur 提交：2x 桥接、着色器特化、dither 折叠、cascade 复用、HWUI 降低），**公开 API 一字未改**（`textureBlur`/`progressiveTextureBlur`/`Backdrop`/`rememberLayerBackdrop`/`layerBackdrop`/`BlurDefaults` 全部原样）。GifHelper 的关于页（logo/标题 `textureBlur(150f)`、`GlassCard` 60f）和顶栏/底栏采样都走这条管线，**像素级结果会变**，需截图对比。 | 视觉行为变化 |

---

## 1. 提交分类汇总（34 个，按时间从旧到新）

### 1.1 编译期破坏性变更（公共 API）

**对 GifHelper：0 条。** 对 miuix 库整体：**没有任何公开声明被删除或重命名**。用跨模块声明对比（`miuix-ui` / `miuix-glass` / `miuix-preference` / `miuix-material` / `miuix-blur` / `miuix-icons` / `miuix-nav` 的 `commonMain`+`androidMain`）确认：旧集合去掉的只有 private/internal 实现细节；新增的全部是可选参数、新重载、新组件。

需要记录的两处"源码兼容但语义变了"的 API：

1. `rememberScrollBarAdapter`：旧版 3 个重载（`ScrollState` / `LazyListState` / `LazyGridState`）合并成 1 个 `ScrollableState` 重载（`ScrollBar.kt:96`）；`VerticalScrollBar`/`HorizontalScrollBar` 保留但新增 `isFadeEnabled`、`topAppBarScrollBehavior` 参数（`ScrollBar.kt:261/312`）。`ScrollState`/`LazyListState`/`LazyGridState` 均实现 `ScrollableState`，所以源码兼容；**GifHelper 未使用任何 ScrollBar API**（全文只有 `overScrollVertical` / `scrollEndHaptic`）。
2. `LinearProgressIndicator` / `CircularProgressIndicator` 新增 `progress: () -> Float` 重载并放在首位（`ProgressIndicator.kt:50/157`），旧 `progress: Float?` 重载保留（`:96/215`）。不存在重载歧义。GifHelper 只用 `InfiniteProgressIndicator`，其签名（`ProgressIndicator.kt:303`）与旧版逐字相同。

### 1.2 默认值与视觉行为变更（重点）

| 提交 | 变更点 | 位置 |
|---|---|---|
| 9413f690 | 浮动底栏底部外边距 `≤16dp` 分支 28dp→20dp；`NavigationBar` 基础组件同款 32dp→24dp | `GlassNavigationBar.kt:321`、`basic/NavigationBar.kt` |
| 322500dc | 底栏加横向安全区 `displayCutout ∪ navigationBars`(Horizontal)；`AdaptiveUtils` 只换了尺寸来源、**阈值未变**（840/600/1.2/1200） | `GlassNavigationBar.kt:331-337` |
| 2fa9b2c6 | 顶栏标题对齐改为"放得下居中、放不下动画切到起始边"；新增 `TopAppBarDefaults.TitleControlSpacing = 12.dp`、`MinTitleWidth = 48.dp`、`centerObstructionStart` 参数 | `basic/TopAppBar.kt:434/1068/1320/1362/1364` |
| 5ff41ba9 | `GlassTopAppBar` 顶栏 insets 来源 `statusBars → systemBars`（影响 band 高度计算） | `GlassTopAppBar.kt:556` |
| 49318153 | 顶栏渐进模糊 `ProgressiveBlur.Top.copy(curve = 0.5f → 0.25f)` | `GlassTopAppBar.kt:460` |
| 9dc09697 | 顶栏 band 增加 `enabled = bandActive` 门控；按钮录制层 consumer 机制（`GlassIconButton` 注册/注销） | `GlassTopAppBar.kt:441/463`、`GlassIconButton.kt:82-88` |
| 4341c5c3 | 静止（`keepMaterial==false && bandActive==false`）跳过 `layerBackdrop` 采样 | `GlassTopAppBar.kt:441` |
| e875daf8 | 下拉弹层锚点行→值；`Overscroll` 静止时不套 `placeWithLayer`；`Card`/`BasicComponent`/preference 减少 interactionSource 与 `@NonRestartableComposable` | `GlassDropdownPreference.kt:116/157/165`、`utils/Overscroll.kt:298-310`、`basic/Component.kt:135-175`、`miuix-preference/*` |
| 3a435efc | `SinkFeedback`/`TiltFeedback` 按组件尺寸自适应缩放/倾角（小控件不再过冲） | `utils/PressFeedback.kt` |
| 60c62049 | `GlassNavigationRail` 次级项（secondary）形变几何改用 last-slot 动画几何，分隔线宽度跟随 | `GlassNavigationRail.kt:559-568` |
| 58b0a3dd | `GlassTabRow`/`GlassSegmentedTabRow` 材质出现改为 450ms 线性 ramp 过渡 | `GlassTabRow.kt:475` |
| 27cb8bca | 全部 glass 材质/配色在 `isDynamicColor` 下染色（GifHelper 空转） | `Glass.kt:269-277` 等 |
| 84103050 | 拖动 `GlassNavigationBar` 时徽标不再被裁剪（GifHelper 无 badge） | `GlassNavigationBar.kt` |

### 1.3 性能/实现重写（公开 API 不变）

- **模糊管线**（6bb8e14d / 49318153 / 07a1d6ba / 4e67e39e / 4dad195c / 9dc09697）：2x 桥接层、曲线/轴向着色器特化、dither 折进最后一次模糊、复用 `cascade[0]`、降低 HWUI 开销。涉及 `miuix-blur/{BackdropEffects,DrawBackdropModifier,TextureEffect}.kt`、`internal/{BlurEffect,Shaders}.kt`。**GifHelper 的顶栏/底栏/卡片/关于页全部走这条路，像素结果会变。**
- **ScrollBar 重写**（9155cd2a）：1699 行重写为 `Modifier.Node` + `ScrollIndicatorState` + 顶栏联动（`ScrollBar.kt`）。GifHelper 未用。
- **顶栏空闲回弹**（f897e73a / c1f3c1f8）：`ExitUntilCollapsedScrollBehavior` 在无 `onPostFling` 时 180ms 后自行 snap（`TopAppBar.kt:519-540`），并用 `Scaffold` 里的 `TopAppBarInputTracker` 严格限制为"鼠标滚轮/无按压"（`Scaffold.kt:558-598`、`TopAppBar.kt:1501`）。**Android 触摸路径被 `allowIdleSettle` 门控，GifHelper（仅 Android）行为不变。**
- **ProgressIndicator/ProgressButton**（cb64c8d0）：`Canvas` → `drawWithCache`，动画 State 改为绘制期读取，减少每帧重组（`ProgressIndicator.kt:41-330`）。视觉不变、性能更好。
- **Card/BasicComponent/preference 组合开销**（e875daf8）：无点击且无按压反馈时不再创建 `MutableInteractionSource`（`Card.kt:102`）；去除 `@NonRestartableComposable`（`miuix-preference/{Arrow,Switch,Slider,RangeSlider,Checkbox,RadioButton}Preference.kt`）。语义变化仅一处：`CheckboxPreference`/`RadioButtonPreference` 在回调为 `null` 时不再可点击（GifHelper 都传回调）。
- **Linux 窗口阴影 override**（1926883b）、**MiuixWindow 回调搬迁**（1a054716）：GifHelper 不引用 `miuix-window`。

### 1.4 新增能力

1. `miuix-glass/GlassSearchOverlay.kt`（605 行，416709f9/a1998b0d/47d75e69）：搜索浮层从 example 迁入库，带 `blurEnabled` 回退与滚动淡入。GifHelper 未使用。
2. `Modifier.verticalScrollBar` / `horizontalScrollBar`（`ScrollBar.kt:136/164/195/221`）与 `topAppBarScrollBehavior` 联动。
3. `BackdropPipeline` 接口 + `Modifier.drawBackdrop(pipeline=…)` 值类型重载（`DrawBackdropModifier.kt:145-170`）——旧 `drawBackdrop(...)` 重载原样保留。
4. `GlassNavigationRailState.collapsedStart` / `collapsedWidth`（`GlassNavigationRail.kt:261/265`，`internal set`，只读消费安全）。
5. `GlassTopAppBar` / `TopAppBar` / `SmallTopAppBar` 的 `centerObstructionStart`：上游用它让居中标题避开折叠侧栏（example `PageUtils.kt` 的 `LocalTopAppBarCenterObstructionStart`）。**GifHelper 用的是"标题透明度跟 Rail 进度"方案（`Adaptive.kt:LocalTopBarTitleAlpha`），不需要跟进。**
6. `ProgressIndicator` 的 lambda 进度重载（绘制期读值，避免每帧重组）。

### 1.5 删除的文件

| 删除 | 说明 | 对 GifHelper |
|---|---|---|
| `miuix-blur/src/androidMain/.../internal/RenderEffectCompat.android.kt`、`commonMain/.../internal/RenderEffectCompat.kt`、`skikoMain/.../internal/RenderEffectCompat.skiko.kt` | 均为 **`internal`** expect/actual（`chain` / `runtimeShaderEffect` / `progressiveStackEffect`），被 49318153 内联进 `internal/BlurEffect.kt` 与 `internal/Shaders.kt` | **无影响**：非公开 API，GifHelper 未引用 |
| `example/shared/src/commonMain/kotlin/GlassSearchOverlay.kt` | 迁入 `miuix-glass` 成为公开组件（416709f9） | **无影响**：GifHelper 无 `SearchOverlay` 引用 |
| `miuix-blur/src/androidMain/baselineProfiles/baseline-prof.txt`（1 行） | profile 维护 | 无影响 |

### 1.6 纯内部 / CI / 文档 / 示例

`2140f7f8`（快照发布 + 包清理脚本）、`08d52d45`（CI HEADERS）、`d27cbb0b`（macOS/iOS CI）、`670cf597`（去掉 `smallBinary`、缓存 DerivedData）、`a9156666`（docs 用 wasmJs）、`d0f69485`/`20da9cf6`（example 页面）、`27cb8bca` 的文档部分、`docs/**` 30+ 文件（含新增 `glasssearchoverlay.md`）、`gradle.properties`（`-Xmx8g→4g`、移除小体积二进制与增量开关、配置缓存保留）、`gradle/libs.versions.toml`（仅新增 `hiddenapibypass 6.2`）、`build.gradle.kts`（一个 `forEach { _ -> }` 语法修正）、`miuix-blur/build.gradle.kts`（`withHostTest {}`）。

> 构建工具链（AGP/Kotlin/Compose/compileSdk）在本区间**没有变化**：`libs.versions.toml` 只多了一个 unrelated 的 `hiddenapibypass`。因此 GifHelper 的 `compileSdk 37.2`（`build.gradle.kts:53-59`）要求不变。

---

## 2. GifHelper 实际使用的 API 逐一核对

图例：**编译** = 升级后能否编译；**视觉/交互** = 是否需要肉眼回归；**适配** = 是否要改 GifHelper 源码。

### 2.1 `top.yukonga.miuix.kmp.basic`

| API（GifHelper 使用点） | 签名 | 默认值 | 视觉/交互 | 结论 |
|---|---|---|---|---|
| `Scaffold`（`App.kt:215`、`PageChrome.kt:331/339`） | 未变 | 未变 | 内部新增 `LocalTopAppBarInputTracker`（`Scaffold.kt:117`），只影响桌面滚轮回弹 | 无影响 |
| `Surface`（`ProgressButton.kt:69`）、`Text`、`Icon`、`IconButton`、`Button`/`ButtonDefaults`、`TextField` | 未变 | 未变 | 无 | 无影响 |
| `Card` / `CardDefaults`（`Widgets.kt:155/196`） | 未变（`Card.kt:86-95`） | 未变 | 内部不再无条件创建 interactionSource（`Card.kt:102`）；`pressFeedbackType` 默认 `None` | 无影响 |
| `BasicComponent` / `BasicComponentDefaults`（`LibraryScreen.kt:255-275` 等多处） | 未变 | 未变 | 内部 press 反馈重构（`Component.kt:135-175`），行高 56dp、内边距、8dp 间距未变 | 无影响 |
| `InfiniteProgressIndicator`（`Widgets.kt:593`） | 未变（`ProgressIndicator.kt:303`） | 未变 | 动画改绘制期读取，画面一致 | 无影响 |
| `MiuixScrollBehavior` / `ScrollBehavior`（`PageChrome.kt:224`） | `MiuixScrollBehavior()` 无参签名未变 | 未变 | 新增"仅鼠标滚轮"的 180ms 空闲回弹（`TopAppBar.kt:519-540`） | 无影响（Android 触摸被门控） |
| `TopAppBar`（**非玻璃档**，`GlassScope.kt:290-302`） | 新增 `centerObstructionStart`（`TopAppBar.kt:131`，在 `titlePadding` 之后、`defaultWindowInsetsPadding` 之前） | `TitlePadding 26dp` 未变；**新增 `TitleControlSpacing 12dp`**（`TopAppBar.kt:434`） | **标题对齐规则变了**：放不下时从"居中"动画切到"起始边"（`TopAppBar.kt:1068/1320`）；`MinTitleWidth=48dp` | **视觉变化，需回归**；GifHelper 全命名传参 → 编译不受影响 |

### 2.2 `top.yukonga.miuix.kmp.blur`

| API（使用点） | 签名 | 行为 | 结论 |
|---|---|---|---|
| `Backdrop` + `DrawScope.drawBackdrop(density, coordinates, layerBlock, downscaleFactor)`（`CombinedBackdrop.kt:47-54` 自行实现） | **接口文件完全未变**（`Backdrop.kt` 整文件零 diff） | 无 | 无影响（编译 + 语义） |
| `rememberLayerBackdrop` / `LayerBackdrop` / `Modifier.layerBackdrop`（`GlassScope.kt:106-111/53`、`AboutScreen.kt:118/163`、`App.kt:256`） | `LayerBackdrop.kt`、`LayerBackdropModifier.kt` **零 diff** | 采样端（blur 管线）变了，录制端没变 | 编译无影响；**像素结果需回归** |
| `textureBlur`（`Widgets.kt:202`、`AboutScreen.kt:244/272`） | 未变（`TextureEffect.kt:27/61`） | 底层 `drawBackdrop` 管线重写 → 60f/150f 的实际模糊与边缘会有差异 | 编译无影响；**视觉需回归** |
| `BlurDefaults.blurColors` / `MaxBlurRadius` / `BlendColorEntry` / `BlurBlendMode`（`AboutScreen.kt:141`、`Widgets.kt:207`） | 未变（`BlurDefaults.kt:40/143/155/166`，整文件零 diff） | 无 | 无影响（`MaxBlurRadius` 仍是 100f，GifHelper 注释里"150f 被夹紧"依然成立） |
| `progressiveTextureBlur`（GifHelper 未直接用，经 `GlassTopAppBar` 间接使用） | 公开签名未变；内部改为 `ProgressiveTextureBlurPipeline` 值类型（`TextureEffect.kt:221-247`） | 管线重写 | 编译无影响；视觉需回归 |

### 2.3 `top.yukonga.miuix.kmp.glass`

| 组件（GifHelper 使用点） | 公开参数签名 | 默认值 | 视觉/交互 | 结论 |
|---|---|---|---|---|
| `GlassTopAppBar`（`GlassScope.kt:260-288`） | **新增** `centerObstructionStart: Dp = Dp.Unspecified`（`GlassTopAppBar.kt:293`(派生 overload)、`:374`(主 overload)） | `ProgressiveBlurRadius 80dp`、`ProgressiveBlurRampDistance 48dp`、`BandOverhang 36dp`、`TitleBlurRadius 8dp`、`ButtonSize 44dp`、`HorizontalPadding 12dp` **全部未变**（`GlassTopAppBar.kt:79-100/163`）；`buttonFill()`/`buttonMaterial()` 增加动态取色分支（GifHelper 空转，`:125-133`） | ① curve 0.5→0.25（`:460`）② 静止跳过录制层采样（`:441`）③ insets `statusBars→systemBars`（`:556`）④ 按钮 consumer 机制 | **编译无影响；视觉需回归（曲线+insets）** |
| `GlassNavigationBar`（`App.kt:321-343`） | **逐字未变**（21 个参数完全一致） | `Height 54dp`、`IconSize 25dp`、`ContentPaddingVertical 6dp`、`IndicatorOverhang 5dp`、`PressedAlpha 0.6f`、`indicatorColor()` alpha 0.12/0.06、`selectedColor` 默认仍是 `primary/onBackground` **全部未变** | ① 底边距 28→20dp（`:321`）② 横向安全区 insets（`:331-337`）③ 隐藏动画模糊换实现（`Modifier.blur` → `graphicsLayer.renderEffect`，半径仍 18dp，`:351`）④ 条目行 `heightIn(min=height)` + 行级 alpha（`:371/:527`） | **编译无影响；视觉需回归（底部间距+横屏）** |
| `GlassNavigationRail`（`App.kt:399-413`） | **逐字未变**（24 个参数完全一致） | `ExpandedWidth 250dp`、`Margin 12dp`、`CollapsedHeight = GlassTopAppBarDefaults.ButtonSize(44dp)`、`CollapsedTop 4dp`、`selectedColor` 默认 `primary/onSurface` **未变** | 次级项（`secondaryItems`）几何改为 last-slot 动画几何（`:559-568`）；`state` 新增 `collapsedStart/collapsedWidth`（`:261/:265`）；次级行加 `Role.Tab` 语义 | 无影响（GifHelper 不传 `secondaryItems`，只读 `state.progress`） |
| `rememberGlassNavigationRailState`（`App.kt:126`） | 未变（`GlassNavigationRail.kt:283`） | 未变 | 无 | 无影响 |
| `GlassNavigationItem`（`App.kt:420+`） | 字段 `icon/label/contentDescription/badge` 未变 | — | 无 | 无影响 |
| **`GlassCard`** | **上游没有这个公开组件**。GifHelper 的 `GlassCard` 是自己写的（`ui/Widgets.kt:188-218`），底层 `Card` + `Modifier.textureBlur` | — | 视觉受 blur 管线重写影响 | 编译无影响；视觉需回归 |
| `GlassDialog`（`LibraryScreen.kt:114-147`） | **逐字未变**（22 个参数一致） | `material()` 外层改 `dynamicGlassMaterial(..., alpha=0.72f)`（`GlassDialogSurface.kt:32`）→ `isDynamicColor=false` 时等价 | 无 | 无影响 |
| `GlassDialogButton` / `GlassDialogButtonRole`（`LibraryScreen.kt:126/132/142`） | 未变 | disabled Primary alpha 在动态色下 0.6（`GlassDialogButton.kt:124-136`，GifHelper 空转） | 无 | 无影响 |
| `GlassBottomSheet`（`LibraryScreen.kt:244-281`） | **逐字未变**（24 个参数一致） | `material()` 改 `dynamicGlassMaterial(..., tint=surfaceContainer, alpha=0.72f)`（`GlassBottomSheetSurface.kt:31`）→ 等价 | 无 | 无影响 |
| `GlassDropdownPreference`（`Widgets.kt:312-322`） | **逐字未变**（18 个参数一致，`:63`） | 未变 | **锚点从整行 bounds → 值+箭头 bounds**；未拿到新 bounds 前不弹出；卸载清空 `dropdownValueBounds`（`:116/145/157/165`） | **交互/视觉变化，需回归**（作品页菜单、设置页下拉） |
| `GlassIconButton`（`GlassScope.kt:226`） | **逐字未变**（13 个参数一致） | `inheritTopBarMaterial=true` 未变 | 新增注册为顶栏录制层 consumer（`:82-88`），只影响采集门控 | 无影响 |
| `GlassTabRow` / `GlassSegmentedTabRow` / `GlassTabRowDefaults` / `GlassSegmentedTabRowDefaults` | 参数列表未变（`GlassTabRow.kt:290/582`） | `primaryColors()`/`neutralColors()` 动态色分支（`:160/219`）；`selectedContentColor` 默认在动态色下取 `primary` | 材质出现新增 450ms linear ramp（`:475`）；内部布局/边框改用 `drawGlassShadow` 等 | **GifHelper 当前未使用**（全项目 grep `TabRow` 零命中）→ 现无影响；但 `AGENTS.md` 硬约束 5 要求"做分段控件时用上游 `GlassSegmentedTabRow`"，将来引入时要知道它的材质出现/消失现在是 450ms 过渡，且动态色下选中文字色会取 `primary` |
| `GlassPopup` / `GlassTransformPopup` / `GlassFloatingActionButton` / `GlassFloatingToolbar` / `GlassSearchOverlay` | — | — | — | GifHelper 未使用 → 无影响 |

### 2.4 `material` / `icon` / `nav` / `preference` / `theme` / `utils`

| 包 / API | 本区间状态 | 结论 |
|---|---|---|
| `material.asBrush` / `isRenderEffectSupported` / `isRuntimeShaderSupported` / `RuntimeShader` | **`miuix-material` 整模块零 diff**（`git diff --stat … miuix-material/` 为空） | 无影响 |
| `icon.glass.*`（`Album/All/ChevronBackward/Download/Share/Delete/…`）、`icon.extended.Back`、`MiuixIcons` | **`miuix-icons` / `miuix-glass-icons` 零 diff** | 无影响（`Glass.FolderOutline` 之类的改名发生在本区间之前，GifHelper 注释已适配） |
| `nav.core.{NavBackStack, navBackStackOf, NavDisplay, NavDisplayEffects, NavKey}`、`nav.transition.{navGraphicsTransition, NavTransition, NavTransitions}` | **`miuix-nav` 零 diff** | 无影响 |
| `preference.ArrowPreference` / `SliderPreference` / `SwitchPreference` | 签名/默认值未变；仅去掉 `@NonRestartableComposable`（`miuix-preference/*.kt`），并让 `onClick=null` 时真的不可点 | 无影响（重组成本变化，视觉不变） |
| `theme.Colors` / `ColorSchemeMode` / `LocalContentColor` / `MiuixTheme` | `theme/Colors.kt`、`MiuixTheme.kt` 零 diff；`ThemeController.kt` **只有格式化**（+24/−3 全是折行） | 无影响；GifHelper `AnimatedTheme.kt` 的 `lerpColors`（按 `Colors.copy` 全字段）无需补字段 |
| `utils.overScrollVertical` / `scrollEndHaptic` / `PagerGestureNestedScrollConnection` / `pagerGestureOverride` / `PagerInterceptionMode` / `PagerNavigationSpringSpec` / `springAnimateToPage` | `PagerGestureUtils.kt`、`PagerNonTouchScroll.kt` 未改；`Overscroll.kt:298-310/350-370` 只做"静止不套图形层""delta==0 早退"的优化 | 无影响（行为等价；overscroll 静止帧不再带 `clip=true` 图层，理论上无可见差异，回归时留意） |

---

## 3. GifHelper "吃默认值"的耦合点清单（升级最容易出问题的地方）

| # | GifHelper 位置 | 依赖的上游默认/内部值 | 上游本次变化 | 后果 | 处理 |
|---|---|---|---|---|---|
| **1** | `ui/PageChrome.kt:70-83` `rememberBottomBarClearance()` | 复刻 `GlassNavigationBar` 内部 formula：`navInset ≤ 16dp → 28dp` | 改成 **20dp**（`GlassNavigationBar.kt:321`） | 底部内容预留高度多 8dp（`Height 54 + margin + 12`），胶囊实际下沉 8dp → 内容末尾出现约 8dp 多余留白 | **必须改**：把 `28.dp` 改 `20.dp`（并更新 `PageChrome.kt:63` 注释） |
| **2** | `App.kt:338-342` `GlassNavigationBar(modifier = …padding(horizontal = 24.dp))` | 库不加横向 insets | 新增 `displayCutout ∪ navigationBars` 横向 insets，且在调用方 modifier 之外（`GlassNavigationBar.kt:331-337`） | 横屏/挖孔设备上胶囊再向内缩一个挖孔宽度（本机 128px≈64dp 量级），可能显得过窄 | **人工回归**；必要时把 24dp 降到 12/16dp 或传 `defaultWindowInsetsPadding=false` |
| **3** | `App.kt:318-343` 大量省略参数 | `Height 54dp`、`indicatorColor`、`shape`、`shadow`、`height` | 全部未变 | 无 | 无需动作 |
| **4** | `App.kt:399-413` `GlassNavigationRail` 省略参数 | `ExpandedWidth 250dp`、`Margin 12dp`、`CollapsedHeight 44dp`、`CollapsedTop 4dp` | 全部未变 | 无 | 无需动作 |
| **5** | `App.kt:136` `railInset = (ExpandedWidth + Margin) * progress` | 同 4 | 未变 | 无 | 无需动作 |
| **6** | `ui/GlassScope.kt:260-288` `GlassTopAppBar` 只显式传了 `bandBrush/bandOverhang/progressiveBlur/...` | `ProgressiveBlurRadius 80dp`、`Ramp 48dp`、默认 `style/material/fill/stroke/shadow` | 数值未变，但 **progressive 曲线 0.5→0.25** + 2x 桥接；insets 换 `systemBars` | 顶栏模糊带厚度/梯度看起来会变（尤其大标题收起过程） | **必须截图回归**；如不接受可显式传 `gradient`？—— 不支持，只能靠上游；必要时应评估回退 `progressiveBlur=false`（走 bandBrush 路径，参数 GifHelper 已备好） |
| **7** | `ui/GlassScope.kt:226` `GlassIconButton(onClick = onBack)` | `inheritTopBarMaterial=true` + 顶栏 consumer | 新增 consumer 注册（`GlassIconButton.kt:82-88`） | 静止时顶栏仍会跳过录制层采集，但按钮静止态本来就走 fill 兜底 → 无视觉差 | 无需动作；观察"开始滚动第一帧"顶栏是否闪一下 |
| **8** | `ui/Widgets.kt:312` `GlassDropdownPreference(...)` | 弹层锚点 = 整行 bounds | 锚点 = 值文本+箭头 bounds；且等待新 bounds | 下拉浮层左边界/宽度、出现时机变化 | **必须回归**作品页与设置页下拉 |
| **9** | `screens/LibraryScreen.kt:114/244` `GlassDialog` / `GlassBottomSheet` | 材质 `dialogGlass(...)` / `dialogGlass(...)` | 外层包 `dynamicGlassMaterial`（GifHelper `isDynamicColor=false` → 等价） | 无 | 无需动作；若将来启用 `MiuixTheme(controller)` 则玻璃会被 `surfaceContainer` 染色（alpha 0.72） |
| **10** | `screens/AboutScreen.kt:244/272` `textureBlur(blurRadius = 150f)`；`ui/Widgets.kt:202` `blurRadius = 60f` | `MaxBlurRadius=100f` 夹紧、模糊管线实现 | 夹紧值未变；**管线重写** | logo/标题"液态玻璃"与玻璃卡片的模糊强度、边缘、噪点分布可能变 | **必须截图回归**（真机 + 模拟器两档） |
| **11** | `ui/AnimatedTheme.kt:78` `MiuixTheme(colors = animated)` | `LocalColorSchemeMode` 缺省 → `isDynamicColor=false` | 上游新增大量 `isDynamicColor` 分支 | 当前全部空转 | 无需动作；升级文档里要写明"启用 controller 后玻璃观感会变" |
| **12** | `ui/PageChrome.kt:224` `MiuixScrollBehavior()` | 无参默认 | 新增空闲回弹（仅滚轮） | Android 无变化 | 无需动作 |

---

## 4. 上游删除内容与影响

1. `miuix-blur` 的 3 个 `internal RenderEffectCompat*` expect/actual 文件（130/38/77 行）被删除并内联（49318153）。
   - 均为 `internal`，`chain`/`runtimeShaderEffect`/`progressiveStackEffect` 从未出现在公开 API 中。
   - **GifHelper 影响：无**。GifHelper 只用 `material` 模块的 `isRenderEffectSupported()`/`isRuntimeShaderSupported()`/`RuntimeShader`/`asBrush`，该模块零改动。
2. `example/shared/.../GlassSearchOverlay.kt`（306 行）删除，能力升级为库内 `miuix-glass/GlassSearchOverlay.kt`（605 行）。
   - **GifHelper 影响：无**（无 `SearchOverlay` 引用；需要时可直接用新公开组件）。
3. `miuix-blur` 基线 profile 一行、`example` 的 CI/资源文件调整。无 API 影响。

---

## 5. 升级后必须人工回归的 UI 面（按优先级）

### P0（确定会变 / 有明确默认值错位）

1. **一级页底部悬浮导航栏几何**：竖屏手势导航（navInset 0）、三键（navInset 24/48）、横屏。
   - 关注：胶囊与屏幕底部的距离（应比旧版近 8dp）、页面末尾留白（GifHelper 侧多 8dp）、列表最后一项是否被压住。
   - 依据：`PageChrome.kt:70-83` vs `GlassNavigationBar.kt:321`。
2. **横屏/挖孔设备底栏宽度**（`GlassNavigationBar.kt:331-337`）：胶囊是否被挤得比三个 tab 还窄、24dp 外距 + 挖孔宽后是否贴边。
3. **玻璃顶栏滚动过程**（大标题收起/展开、下拉 overscroll）：
   - 模糊带厚度/梯度是否与旧基线一致（curve 0.25 + 2x 桥接 + `enabled=bandActive`）；
   - 大标题与返回键在顶部的排布（`systemBars` insets）；
   - 开始滚动的第一帧是否出现"空样本"闪色（`GlassTopAppBar.kt:441` 静止跳过采样）。
4. **玻璃下拉（`DropdownRow`）**：作品页筛选/设置页选项。
   - 依据：锚点改"值+箭头"、未拿到 bounds 前不弹（`GlassDropdownPreference.kt:116/157/165`）。
   - 关注：浮层左/右边界、是否与行对齐、点击到出现是否多一帧。

### P1（大概率可见，需对比截图）

5. **关于页**：`textureBlur(150f)` 的图标 + "GIF 助手"标题、`GlassCard(60f)` 卡片、`CombinedBackdrop`（渐变层 + 正文层）叠加后的顶栏采样。
   - 依据：blur 管线 7 个提交重写（`DrawBackdropModifier.kt`、`internal/BlurEffects/Shaders.kt`）；上游同区间还修了 "card seams"（07a1d6ba），说明像素级结果确实在变。
6. **普通（非玻璃）顶栏标题对齐**：`GlassScope.kt:290-302` 的 `TopAppBar`。
   - 依据：`TopAppBar.kt:1068/1320` 的 `canCenter` 判定 + 动画；长标题（含副标题、右侧两个 action 的二级页）会从"居中"切到"起始对齐"，且首次进入不播动画、随后才动。
   - 重点页面：`ToolScreen`（标题 + 2 个 action）、`ThemeScreen`、`LicensesScreen`、`LicenseDetailScreen`、`ButtonColorPreview`。
7. **玻璃 Dialog / BottomSheet**：`LibraryScreen.kt:114` 删除确认、`:244` 文件操作面板。
   - 依据：即使材质等价，`GlassDialogButton` 的 disabled 档、press/入场动效仍建议目视一次。
8. **宽屏 Rail**（≥840dp / 横屏 / ≥1200dp 展开）：折叠态顶部胶囊、展开态面板、内容让位（`railInset`）与标题透明度。
   - 依据：Rail 内部次级项几何/语义重构（`60c62049`），虽然 GifHelper 不传 secondary，仍建议在 1200dp 下确认主流形变。

### P2（低风险，抽查）

9. 关于页/列表的 overscroll 回弹（`Overscroll.kt:298-310` 静止帧不再套 `clip=true` 图层）：快速下拉回弹后是否出现 1px 未裁剪。
10. 设置页 `SwitchPreference`/`SliderPreference`/`ArrowPreference` 的按压与重组成本（`@NonRestartableComposable` 移除）：长列表快速滚动是否出现掉帧或状态丢失。
11. 工具页 `ProgressButton` / `InfiniteProgressIndicator`（`ProgressIndicator.kt` 重写）：进度条与按钮的进度填充、旋转动画是否与旧版一致。

---

## 6. 建议的适配动作（按顺序）

1. `ui/PageChrome.kt:70-83`：`navInset <= 16.dp -> 28.dp` → `20.dp`；同步改 `PageChrome.kt:60-68` 的注释（"≤16dp → 28dp"）。
2. 升级后跑一遍 P0 三组（底栏/横屏/顶栏滚动）截图对比；如 24dp + 挖孔导致胶囊过窄，把 `App.kt:342` 的 `padding(horizontal = 24.dp)` 降到 `12.dp` 或传 `defaultWindowInsetsPadding = false`（需自行补 `navigationBars`/`displayCutout` 处理）。
3. 关于页做一次真机 + 模拟器（`swiftshader_indirect` 已是 GifHelper 的既有分支，`AboutScreen.kt:107`）双档截图，确认 `textureBlur` 结果与 `GlassSupport.isGlassSupported()` 判定仍然匹配。
4. `scripts/setup-miuix.sh:21` 的 `PINNED_REF`、`build.gradle.kts:53` 的注释提交号统一改成 `5ff41ba9`；否则新人重跑脚本会切到 `d45ca181`（脚本注释里那次"侧栏折叠形态重写"基线，与本报告基线不同）。
5. 升级完成后若考虑跟进 `MiuixTheme(controller)`（真 Monet），必须先预期玻璃材质会被 `dynamicGlassMaterial` 用 `surfaceContainer/surfaceContainerHigh` 染色（`Glass.kt:269-277`、`GlassDialogSurface.kt:32`、`GlassBottomSheetSurface.kt:31`、`GlassPopup.kt`）—— 那是一次独立的视觉改版，不要和本次基线升级混在一起。

---

## 附录 A：本次取证的关键命令

```powershell
cd ../miuix
git log --oneline --no-decorate 3177a684..5ff41ba9          # 34 提交
git diff --stat 3177a684..5ff41ba9                          # 198 文件
git diff --diff-filter=D --name-only 3177a684..5ff41ba9     # 删除清单
git diff 3177a684..5ff41ba9 -- miuix-glass/src/commonMain/kotlin/top/yukonga/miuix/kmp/glass/GlassTopAppBar.kt
git diff 3177a684..5ff41ba9 -- miuix-glass/src/commonMain/kotlin/top/yukonga/miuix/kmp/glass/GlassNavigationBar.kt
git diff 3177a684..5ff41ba9 -- miuix-glass/src/commonMain/kotlin/top/yukonga/miuix/kmp/glass/GlassIconButton.kt
git diff 3177a684..5ff41ba9 -- miuix-glass/src/commonMain/kotlin/top/yukonga/miuix/kmp/glass/GlassDropdownPreference.kt
git diff 3177a684..5ff41ba9 -- miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/basic/TopAppBar.kt
git diff 3177a684..5ff41ba9 -- miuix-blur/src/commonMain/kotlin/top/yukonga/miuix/kmp/blur/TextureEffect.kt
git diff --stat 3177a684..5ff41ba9 -- miuix-material/ miuix-icons/ miuix-nav/   # 全为空
git show 4341c5c3   # 静止跳过录制层采样
git show 49318153   # 2x 桥接 + 曲线 0.5→0.25
git show 9413f690   # 底栏 28dp→20dp
git show 322500dc   # 横向安全区 insets
git show 2fa9b2c6   # 顶栏标题对齐
git show e875daf8   # 下拉锚点 / interactionSource / NonRestartable
```

GifHelper 侧使用的 grep：

```powershell
cd .
Select-String -Path (Get-ChildItem -Recurse -Filter *.kt src | % FullName) -Pattern '^import top\.yukonga' | Sort-Object -Unique
Select-String -Path (Get-ChildItem -Recurse -Filter *.kt src | % FullName) -Pattern 'GlassNavigationBarDefaults\.|GlassTopAppBarDefaults\.|isDynamicColor'
```

## 附录 B：区间内被 GifHelper 间接依赖的文件清单（升级时优先看）

- `miuix-glass/.../GlassTopAppBar.kt`（+60/−?，见 §2.3）
- `miuix-glass/.../GlassNavigationBar.kt`（+54）
- `miuix-glass/.../GlassNavigationRail.kt`（+95）
- `miuix-glass/.../GlassDropdownPreference.kt`（+21）
- `miuix-glass/.../GlassIconButton.kt`（+7）、`Glass.kt`（+19，新增 `dynamicGlassMaterial`）
- `miuix-glass/.../GlassDialogSurface.kt`（+7）、`GlassBottomSheetSurface.kt`（+8）、`GlassDialogButton.kt`（+10）
- `miuix-ui/.../basic/TopAppBar.kt`（+427）、`Component.kt`（+45）、`Card.kt`（+16）、`Scaffold.kt`（+44）、`ProgressIndicator.kt`（+346）
- `miuix-ui/.../utils/{Overscroll,OverscrollFactory,PressFeedback}.kt`
- `miuix-blur/.../{BackdropEffects,DrawBackdropModifier,TextureEffect}.kt` + `internal/{BlurEffect,Shaders}.kt`
- `miuix-preference/.../{Arrow,Switch,Slider,Checkbox,RadioButton}Preference.kt`（仅注解删除/可点击性）
