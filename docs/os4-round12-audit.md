# 第十二轮：MIUIX 基线 5ff41ba9 → 0.9.5-beta02（f3829d3d）、渐进模糊默认开启、全项目自造轮子复核

本轮是**纯跟进型**升级：不改设计、不改业务，只把 MIUIX 官方基线推到最新，并把
`v0.9.5-beta02` 的渐进模糊管线接上（默认开启），同时把全项目再过一遍"有没有自造轮子"。

版本：1.11（13）→ **1.12（14）**。GPU 模拟器（`-gpu host`，AMD RX 9070 XT 主机渲染）验证通过。

---

## 0. 基线对照

| 项 | 升级前 | 升级后 |
|---|---|---|
| MIUIX 源码基线 | `5ff41ba9` | **`f3829d3d`**（`perf: improve sidebar performance (#489)`） |
| MIUIX 发布版本 | 0.9.5-beta01 | **0.9.5-beta02**（tag `66248bac`，已推送 Maven Central） |
| Compose Multiplatform | 1.12.2 | **1.13.0-rc01** |
| Gradle wrapper | 9.9.1 | **9.9.2** |
| AGP / Kotlin | 9.5.1 / 2.4.21 | 不变 |
| compileSdk | 37（minorApiLevel 2） | 不变（仍然 ≥ 库要求的 37.2） |
| minSdk / targetSdk | 33 / 36 | 不变 |

### 0.1 为什么源码基线取 `f3829d3d` 而不是 tag `66248bac`

Maven Central 上的 `0.9.5-beta02` 就是 `66248bac`。本项目用的是 **composite build 源码引入**
（`miuix-glass` / `miuix-glass-icons` 至今没发布到 Maven Central），所以"基线"天然是某个 main 提交。
tag 之后 main 还有 3 个提交，逐个看过：

| 提交 | 内容 | 与本项目的关系 |
|---|---|---|
| `b31c60f2` | 修复：显式传 `isContentScrolled` 时，底部过滚（`overscrollOffset < -1f`）也要驱动玻璃材质与渐进模糊 | **直接相关**，我们正是显式传 `isContentScrolled` 的那条路径 |
| `c16dfa12` | 仅 example：TabRow 位置变化时把 GlassPage 滚回顶部 | 无（示例代码） |
| `f3829d3d` | NavDisplay 布局尺寸延迟读取、TabRow 改量测期测量、Rail 密度变化后折叠胶囊居中、Sidebar 逐帧开销 | **直接相关**，我们用 `NavDisplay` + `GlassNavigationRail` |

三个都是修/优化我们实际走的路径，且都带测试；因此源码基线钉在 `f3829d3d`。
若以后要严格对齐"已发布产物"，把 `scripts/setup-miuix.sh` 的 `PINNED_REF` 改回 `66248bac` 即可。

### 0.2 破坏性变更与本项目的处理

官方 0.9.5-beta02 列了三项破坏性变更，逐项核对：

| 变更 | 本项目是否受影响 | 处理 |
|---|---|---|
| `MiuixWindowProperties` 去掉泛型，`onVisibleChange` / `onResizeEdgeChange` 移到 `MiuixWindow` | 否（Android 单端，不用 `miuix-window`） | 无需迁移 |
| `ScrollBar` 重构为 `Modifier.Node` + `ScrollIndicatorState`，`rememberScrollBarAdapter` 只剩 `ScrollableState` 重载 | 编译期兼容（`LazyListState` 本身就是 `ScrollableState`），调用点与官方 example 完全一致 | 保留 `VerticalScrollBar(adapter = rememberScrollBarAdapter(listState))`（官方 `MainPage.kt:346-351` 就是这个写法），零重组由新的 `Modifier.Node` 内部保证 |
| 依赖 CMP 1.13.0-rc01，使用 `computedStateOf` / `CapturedAnimatedVisibility` / `DialogProperties.scrimAlpha` | 是 | Compose 升到 1.13.0-rc01；`derivedStateOf` → `computedStateOf`（见 §2.2） |

---

## 1. 渐进模糊专项

### 1.1 官方这一轮到底改了什么

`0.9.5-beta02` 的性能项原文：*"Optimize progressive blur pipeline with 2x bridge, shader specialization,
`cascade[0]` reuse (`midSharpOverlay`), folded `NoiseDither`, and reduced HWUI overhead"*。
另外 `GlassTopAppBar` 的渐进模糊改成**由 `maskProgress` 单一驱动**（不再每帧从 `visualContentOffset`
现算 ramp），空闲时跳过按钮背景捕获。

这些都是库内部实现，调用方不需要改参数就能吃到 —— 前提是**我们不要把旧路径钉死**。

### 1.2 本项目的现状（审计结果）

| 检查项 | 结论 |
|---|---|
| 用了哪些 MIUIX 玻璃组件 | `GlassTopAppBar`（页面顶栏）、`GlassNavigationBar`（底栏）、`GlassNavigationRail`（宽屏侧栏）、`GlassIconButton`（顶栏动作）、`GlassDropdownPreference` / `GlassBottomSheet` / `GlassDialog`（弹层） |
| 哪些页面用渐进模糊 | 全部走 `Os4ListPage` → `PageTopBar` 的页面（首页 / 作品 / 设置 / 工具页 / 主题 / 关于 / 许可），`progressiveBlur = true` 恒开 |
| 是否有自己实现的模糊 | **没有**。全项目 `textureBlur` / `progressiveTextureBlur` 调用点只有 4 处（顶栏由库内部调；`GlassCard` 与关于页图标/标题用 `textureBlur`），没有自绘 RenderEffect / BlurMaskFilter / 自写 AGSL |
| 是否重复采样背景 | 顶栏一份（`LocalRootBackdrop`/页面正文层/`CombinedBackdrop`），卡片一份（页面层），底栏一份（Pager 外的 root 层）。三者是**不同层**，不是同一层被采两遍 |
| 是否有硬件加速判断 | 只有 `ui/GlassSupport.kt` 一处，且已简化为库的 `isRuntimeShaderSupported()`（§2.1） |
| 是否有为兼容模拟器关模糊 | **没有**。全项目 grep `ranchu|goldfish|emulator|swiftshader|Build.HARDWARE` 只命中注释 |
| 是否有模拟器专用 UI 分支 | **没有** |

### 1.3 默认开启

`progressiveBlur = true` 写死在 `PageTopBar`，与官方 example 的 `AppState.enableBlur = true` 同义：

- 能力判断交给库：`GlassTopAppBar` 内部 `rawBackdrop = backdrop?.takeIf { isRuntimeShaderSupported() }`，
  不支持时自动落回 `bandBrush` 渐变带（同一个组件的另一条官方绘制路径）；
- 库没有暴露"GPUL 白名单"这种概念，我们也没有自建；
- 用户侧只有「玻璃材质」一个开关（`LocalGlassEnabled`），关掉是**整条玻璃**降级，不是"模糊单独关"。

### 1.4 GPU 模拟器实测

`-gpu host`（SurfaceFlinger 报 `Android Emulator OpenGL ES Translator (AMD Radeon RX 9070 XT)`，GLES 3.1），
AGSL 可用。滚动后顶栏确实出现渐进模糊带，内容被糊开、边缘无硬边；
关于页图标与标题继续跟随渐变着色（`textureBlur` 生效）。截图见 `_archive/round12/shots/`。

---

## 2. 自造轮子复核（全项目 36 个源文件，6537 行）

判定口径见 AGENTS.md「判定基准」：**只有 MIUIX 官方源码 + 官方 example 的编译产物算真值**，
第三方下游项目（含上一轮参考过的 Glacier / InstallerX）不算标准。

### 2.1 本轮替换掉的三处

| 审计项目 | 原实现 | MIUIX 官方对应 | 处理 |
|---|---|---|---|
| 设备玻璃能力判断 | `isRenderEffectSupported() && isRuntimeShaderSupported()` | 官方只用 `isRuntimeShaderSupported()`（`LicensePage.kt:53`、`IconPage.kt:178`、`PageUtils.kt` 的 `rememberBlurBackdrop()`）；`isRenderEffectSupported()` 是 `SDK≥31`，而本项目 `minSdk=33`，那一项**恒为 true** | 删掉恒真的一半，只留官方那一个判断 |
| 主操作按钮的容器形状 | `Surface(shape = RoundedCornerShape(16.dp))`（正圆弧） | 官方 `Button` 本体是 `semantics → squircleSurface(color, cornerRadius) → clickable`（`Button.kt:70-90`），四角是连续曲率 | `ui/ProgressButton.kt` 换成官方那一套容器；进度填充仍用 `drawWithContent` 画在形状内部，`squircleSurface` 的遮罩保证填充两端跟随圆角 |
| 滚动判据 / 头部收起判据 | `remember { derivedStateOf { … } }` ×2 | Compose 1.13 的 `computedStateOf`（每次读重算、不缓存，但只在结果变化时失效），官方 `MainPage.kt:104`、`AboutPage.kt:92/118/121`、`ComponentPage.kt:144` 全改成它 | `ui/PageChrome.kt` 两处替换，注释写明官方调用点与语义差别 |

附带一处**保真度**修正：`rememberBottomBarClearance()` 的镜像公式补上 `captionBar`。
上游 `GlassNavigationBar.kt:309-324` 取的是 `maxOf(navigationBars, captionBar)` 再套三档，
我们以前只取 `navigationBars`；手机上没有 `captionBar`（恒 0），但自由窗口 / 桌面模式下会少让一截。

### 2.2 逐项核查后**不需要改**的（分类与依据）

| 审计项目 | 当前实现 | 官方对应 | 结论 |
|---|---|---|---|
| 分组卡片 / 分组标题 / 行 | `Card` + `SmallTitle` + `ArrowPreference` + `BasicComponent` | 同名官方组件 | 已核查，一致 |
| 下拉选择行 | `GlassDropdownPreference`（`backdrop = LocalRootBackdrop ?: LocalPageBackdrop`） | 官方同名 + 官方 `renderInRootScaffold` 语义 | 已核查，一致（采样源选择是业务必需的坐标约束，见 AGENTS 第 1/4 条） |
| 开关 / 滑块行 | `SwitchPreference` / `SliderPreference` | 同名 | 已核查，一致 |
| 玻璃卡片 | `Card` + `textureBlur(shape = RoundedCornerShape(CardDefaults.CornerRadius), blurRadius = 60f)` | 官方 `AboutPage.kt:352-370` 同构造（它用 16dp / 100f，那是 logo 尺寸档） | 已核查；参数差异属于卡片尺寸档，不是自造 |
| 关于页图标 / 标题 | `textureBlur` + 三档 `BlendColorEntry` + `DstIn` | 官方 `AboutPage.kt:273-290` 逐参一致（含 `BlurDefaults.MaxBlurRadius`） | 已核查，一致 |
| 关于页渐变背景 | `ui/bg/BgEffect.kt` + `BgEffectShader.kt`（移植自官方 example `component/effect/*`，Apache-2.0） | 官方**只有 example 实现，没有库模块** | 保留（C 类）。本轮逐行比对 AGSL 源码：70/70 行完全相同；`example/component/effect/*` 本轮无改动 |
| `CombinedBackdrop` | 移植自官方 example `component/liquid/CombinedBackdrop.kt` | 同上，官方无库模块 | 保留（C 类） |
| 主操作按钮的进度填充 | `ProgressButton`（生态移植，Apache-2.0） | 官方**没有**进度按钮 | 保留（C 类）；容器形状已按官方 `Button` 实现（§2.1） |
| 预测性返回转场 | 4 档自写转场，全部基于官方 `navGraphicsTransition` | 官方只有 3 个预设，没有"缩放/经典/AOSP"这几档 | 保留（C 类，用的是官方原语） |
| 安全区 / 内容内边距 | `pageContentPadding` / `rememberHorizontalSafeInsets` / `safeBottomGap` / `railStartInset` | 官方同名工具在 **example** 里（`PageUtils.kt`），不在库里；且我们的页面有 Pager / 浮层底栏，结构与 example 不同 | 保留（C 类）；本轮对齐了底栏留白公式（§2.1） |
| 颜色三层（页面底色 / 次级表面 / 卡片） | `pageBaseColor()` / `pageColor()` / `cardColor()` | 官方没有"AMOLED 纯黑 + 次级表面"这套推导 | 保留（C 类），已有实测依据（`os4-round10-audit.md` §1.3） |
| 圆角 / squircle | 预览舞台、动画预览走 `squircleSurface` | 官方同名 | 已核查，一致 |
| 滚动条 | `VerticalScrollBar(adapter = rememberScrollBarAdapter(listState), trackPadding, topAppBarScrollBehavior)` | 官方 `MainPage.kt:346-351` 同写法 | 已核查，一致 |
| 弹层 | `GlassBottomSheet` + `BasicComponent` 行；`GlassDialog` + `GlassDialogButton(role)` | 官方同名 | 已核查，一致 |
| 图标 | 一律 `MiuixIcons.Glass.*`（28dp 画布），返回键 `Glass.ChevronBackward` | 官方图标集 | 已核查，一致 |
| TextField | `basic.TextField(state = TextFieldState)` | 官方 `GlassTextField` 只用在玻璃弹层里（`SettingsPage.kt:655`），普通卡片内仍是 `TextField`（`TextFieldSection.kt`） | 已核查，一致 |
| 搜索 | **无搜索界面** | 本轮新增 `GlassSearchOverlay` / `GlassTextField` | 不适用（没有搜索功能，不为用而用） |
| Toast / Snackbar / Badge / Divider / Tooltip / PullToRefresh / NumberPicker / ColorPalette / BreadcrumbBar | 项目未使用 | 官方有 | 不适用（没有对应业务） |

**没有发现属于 A 类（官方有完全对应能力）却仍在自造的组件。**

---

## 3. 编译与产物

| 项 | 结果 |
|---|---|
| Debug `:assembleDebug` | 通过（1 个告警：`MediaTools.kt` 里 `COLOR_FormatYUV420SemiPlanar` 是 Java 侧 deprecated 常量，历史遗留，与本轮无关） |
| Release `:assembleRelease` | 通过（R8 + shrinkResources） |
| 依赖树 | `releaseRuntimeClasspath` 全部 Compose 构件收敛到 `1.13.0rc01`，无 `FAILED` / 冲突；`top.yukonga.miuix.kmp:*` 全部被 composite build 的 project 替换（预期） |
| 升级后 Debug | 47,865,117 B，`1.12 / 14`，SHA-256 `86689c0f…7d2c1` |
| 升级后 Release | 3,409,711 B，`1.12 / 14`，SHA-256 `d05f6b55…3e7887` |
| 升级前 Debug（对照） | 45,327,506 B（由 `b74a0e0` 重建），SHA-256 `e4d5ed04…e334ff` |
| 升级前 Release（对照） | 3,360,963 B，SHA-256 `71cbec0a…e4839` |

Debug 包大了约 2.5 MB，原因是 Compose 1.13 引入的代码（`androidx.graphics:graphics-shapes` 参与
`PolygonShape`，dex 里多出一份）——debug 不混淆，体积直接反映代码量；release 只大 48 KB。

---

## 4. GPU 模拟器升级前后对照

同一台 AVD（Android 16 / 1080×2400 / 420dpi / `-gpu host`）、同一组偏好设置、同一操作路径，
逐张截图比对（`_archive/round12/compare.sh`，四轮：浅色静态 / 深色静态 / 浅色 Monet / 横屏）。

| 截图 | 像素差异 (>8/255) | 判定 |
|---|---|---|
| A01 首页 / A02 首页滚动 | 0.01% | 仅状态栏时间 |
| A03 工具页 | 0.02% | 状态栏时间 + 底部按钮四角的 squircle 过渡（放大 6 倍才可见） |
| A04 工具页滚动 | 9.89%（**垂直对齐 31px 后 0.00%**） | 手势落点差异，见下 |
| A05 作品页（空态） / A07 主题页 / A09 关于页滚动 / A10 许可页 | 0.01% | 仅状态栏时间 |
| A06 设置页 | 0.03% | 状态栏 + 副标题版本号（1.11（13）→ 1.12（14）） |
| A08 关于页 | 0.03% | 版本号；其余 0 |
| B01–B07 深色 | 0.01%–0.05% | 状态栏 + 版本号 |
| C01–C04 Monet | 0.02%–0.04% | 状态栏 + 版本号 |
| D01–D02 横屏 | 0.02% | 仅状态栏时间 |

（两组截图都在**同样的空数据状态**下采集：先 `adb uninstall` 清掉上一轮功能测试导出的文件，
再按同一脚本跑完四轮。第一版对照里作品页差 7.6%，是因为那时升级前那份数据里还留着一个
上一轮导出的 `test.gif` —— 属于测试数据差异，不是代码差异，已重采。）

**A04 的 9.89% 不是回归**：两张图在垂直偏移 31px 后**逐像素完全一致（差异 0.00）**，
即同一页面内容只差一个滚动偏移量。滚动偏移来自 `adb input swipe` 的速度估算 ——
用**同一个 APK 连跑两次**同样手势，两次之间也能差 2px，个别次能差到 30px 上下，
属于手势抖动，不是代码差异。

结论：**没有意外视觉回归**。全部差异都能归入 A 类（版本号字符串）与手势噪声。
按钮圆角换 `squircleSurface` 后，同尺寸下与正圆弧的像素差只有 9px/6400（视觉不可分辨），
价值在于不再自维护一套 `Surface + RoundedCornerShape` 组合。

---

## 5. 性能对照（这台模拟器上有一条**需要说明**的回退）

方法：`adb shell dumpsys gfxinfo <pkg> reset` → 首页三次上滑 + 三次下滑（每次 200ms）→ 读数。
Debug 与 Release 各测 2–3 次，方差很小（见 `_archive/round12/perf/`）。

| 指标 | v1.11（CMP 1.12.2） | v1.12（CMP 1.13.0-rc01） |
|---|---|---|
| Janky frames（Debug） | 3.90% / 3.91% / 4.18% | 8.38% / 9.01% / 9.06% |
| Janky frames（Release） | 3.61% / 4.47% | 8.04% / 8.93% |
| 50th percentile | 17 ms | 17 ms |
| 90th percentile | 25–26 ms | 24–25 ms |
| 95th percentile | 28–30 ms | 27 ms |
| 99th percentile | 38 ms | 38 ms |
| Number Slow UI thread | 2 | 2 |
| Number Slow issue draw commands | 11–12 | 26–30 |
| PSS（Release） | 36,010 KB | 33,998–34,185 KB |

### 5.1 归因：不是 MIUIX，也不是我们的代码，是 Compose 1.13.0-rc01

做了三次对照构建（同一台模拟器、同一手势）：

| 组合 | Janky |
|---|---|
| 旧代码 + CMP 1.12.2 + MIUIX `5ff41ba9` | 3.9%–4.2% |
| **旧代码**（`b74a0e0`，独立 worktree）+ **CMP 1.13.0-rc01** + MIUIX `f3829d3d` | **8.31% / 8.71%** |
| 新代码 + CMP 1.13.0-rc01 + **MIUIX `5ff41ba9`**（基线退回） | 8.01% |
| 新代码 + CMP 1.13.0-rc01 + MIUIX `f3829d3d` | 8.4%–9.1% |

只要把 Compose 换成 1.13.0-rc01，无论 MIUIX 是新是旧、无论项目代码是新是旧，都掉到 8% 上下；
换回来就回到 4%。并且：

- **UI 线程没变慢**（`Slow UI thread` 两边都是 2）→ 不是重组变多，`computedStateOf` 没有引入重组风暴；
- 变的是 `Slow issue draw commands`（11 → 26），即 **RenderThread 提交绘制命令**这一段；
- **分位数几乎没变**（p50 17ms、p99 38ms 两边相同）→ 这是"整体贴着 16.7ms 截止线"的尾部放大：
  50% 的帧本来就在 17ms，任何一点绘制开销都会让更多帧越线，而不是整体变卡一倍；
- 关掉玻璃（`glass_enabled=false`）后仍是 8.7% → 与渐进模糊无关。

**结论**：这是 Compose Multiplatform 1.13.0-rc01 在本机（debug/release 都测了，宿主 GPU 的
OpenGL ES 转译层）上的绘制开销抬升，属于上游 RC 的已知风险区间。0.9.5-beta02 **强制要求** 1.13，
没有"留在 1.12.2 同时用 beta02"的选项。处理方式：

1. 接受并记录（本项目当前选择）；
2. 等 CMP 1.13.0 正式版发布后重测这一组数字（`_archive/round12/perf.sh` 可直接复用）；
3. 若届时仍偏高，再把基线退回 `66248bac` + CMP 1.13 以外的组合已不可能 —— 只能等上游。

**未在这一轮做、也不该做**：为了这几个百分点的 janky 帧去关掉渐进模糊或加模拟器专用分支。
那正是任务书明令禁止的处理方式。

---

## 6. 功能 / 稳定性验证

| 项 | 结果 |
|---|---|
| 启动 / 冷启动 | 正常，无 crash |
| GIF 导入 | 系统照片选择器 → 选中 GIF → 「已选择 1 个文件」+ 预览出图 |
| GIF 处理 / 导出 | 「GIF 编辑」→ 开始生成 → `已生成 edit_…gif · 2 KB`，结果卡带动图预览 |
| 作品页 | 新文件出现在列表首行（文件名 / 大小 / 时间） |
| 文件操作面板 | `GlassBottomSheet` 正常：标题居中、三个行内动作（保存 / 分享 / 删除）图标与颜色正确 |
| 删除确认 | `GlassDialog` 正常：标题、说明、取消 / 删除（Destructive 红） |
| 顶栏玻璃（滚动后） | 渐进模糊带出现，内容糊开、无硬边；返回键胶囊正常 |
| ScrollBar | 列表滚动条出现，与顶栏折叠同步（`topAppBarScrollBehavior`） |
| 横屏 | 折叠态侧栏 = 顶部居中胶囊（只显文字），标题避让正常 |
| 四种配色（静态/Monet × 明/暗） | 无配色塌陷；关于页渐变 + 玻璃卡在深色下仍通透 |
| logcat（一次完整交互巡检） | 0 条 `FATAL` / `AndroidRuntime` / SIGSEGV；无 Compose 异常、无 shader 编译失败；仅系统侧 `FrameTracker`（launcher 关闭动画）与 `hiddenapibypass` 类加载提示 |

---

## 7. 仍存在的遗留项

1. **Compose 1.13.0-rc01 的绘制开销回退**（§5）。等正式版重测；不通过关模糊/加分支来"修"。
2. **真机未验证**。本轮全部结论来自 `-gpu host` 模拟器；真机的 AGSL 编译路径、GPU 差异仍需一次实机确认
   （真机历史上出现过"内容不糊开、只被压暗"，那是 `isRuntimeShaderSupported()` 查不出来的东西，
   所以用户开关必须一直留着）。
3. `unused` 告警：`MediaTools.kt` 的 deprecated 常量，与本轮无关，未动。
