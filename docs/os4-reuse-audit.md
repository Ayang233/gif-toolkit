# GIF 助手 MIUIX OS4 自定义实现清理与复用报告

对全项目 30 个源文件做自造轮子扫描，逐项对照 MIUIX `ac0af467` 的公开 API。
结论：**没有发现"官方有完整实现、项目却用自绘 Canvas / 自写 Blur 重复实现"的情况**；
主要问题集中在**薄包装层抄了库的常量与样式**、以及**一处该用官方 Preference 的行自己拼了箭头**。

---

## 1. MIUIX 基线与同步情况

| 项 | 值 |
| --- | --- |
| 本轮确认的 main HEAD | `ac0af4671bc9e4489d0960685a46a5b1ffd557da` |
| 提交时间 | 2026-11-16T07:43:58Z（`fix: Adjust navigation bar bottom spacing on iOS`） |
| 与上一轮（1.4）比较 | **无变化**，未重复同步 |
| 查询方式 | `git ls-remote` 被网络拦截（`CONNECT tunnel failed, response 502`），改走 GitHub REST API 确认 |
| 本地工作树 | 仅 `build-logic/src/main/kotlin/BuildConfig.kt` 的 `workingDir = rootDir` 补丁（composite build 必需） |

既然 SHA 相同，上一轮所有源码级结论仍然有效，本轮直接在其上做复用审查。

---

## 2. 检查范围

全部 `src/androidMain/kotlin/com/ayang/giftoolkit/` 下的文件（4595 行），逐类对照：

| 层 | 文件 |
| --- | --- |
| 外壳与路由 | `App.kt`、`Route.kt`、`AppState.kt`、`GifApplication.kt` |
| 页面 | `HomeScreen`、`ToolScreen`、`LibraryScreen`、`SettingsScreen`、`ThemeScreen`、`AboutScreen`、`LicensesScreen`（含 `LicenseDetailScreen`） |
| UI 基建 | `Widgets.kt`、`PageChrome.kt`、`GlassScope.kt`、`GlassSupport.kt`、`Adaptive.kt`、`SystemInsets.kt`、`BackTransitions.kt`、`AnimatedTheme.kt`、`ProgressButton.kt`、`AppInfo.kt`、`ToolCatalog.kt`、`LicenseCatalog.kt` |
| 业务内核 | `TaskRunner.kt`、`GifCodec`、`GifTasks`、`MediaTools`、`Stabilizer`、`MotionPhoto`、`ColorKey`（非 UI，仅确认没有手搓动画/渲染） |

---

## 3. 自造轮子对照清单

分类按任务文档的 A/B/C/D 四档。

### 3.1 A 类：完全重复 → **已替换**

| # | 当前实现 | 位置 | 自定义原因 | 官方方案 | 差异 | 处理 |
| --- | --- | --- | --- | --- | --- | --- |
| A1 | `val GlassBarHeight: Dp = 54.dp` | `ui/PageChrome.kt:40` | 想给底栏留白一个语义化名字 | `GlassNavigationBarDefaults.Height`（公开，也是 54dp） | 抄了库的常量值；上游一改就对不上 | **改引用库常量** |
| A2 | `Modifier.clip(RoundedCornerShape(20.dp))` 裁应用图标 | `screens/AboutScreen.kt:65` | 当时的直觉做法 | `Modifier.squircleClip(20.dp)`（`miuix-squircle`） | OS4 图标是**连续曲率**，四角不是正圆弧；普通圆角在 76dp 尺寸下能看出"角上有点方" | **改用 squircleClip**（新增 `miuix-squircle` 依赖） |
| A3 | `BasicComponent` + 手拼 `ChevronForward`(16dp, `onSurfaceVariantActions`) | `screens/LibraryScreen.kt` 的 `FileRow` | 当时其余页面还没统一到 `ArrowPreference` | `ArrowPreference` | 箭头尺寸/颜色/行尾间距都抄进了业务代码，上游一调就只剩这行不一致 | **改用 ArrowPreference** |
| A4 | `LeadingIcon` 里叠 `padding(end = 4.dp)` | `ui/Widgets.kt` | 24dp 线框图标时代，官方 8dp 间距显得贴字 | `BasicComponent` 的 `spacerPx = 8.dp`（写死，无参数） | 叠了之后行间距变 12dp，**与同应用其它行不一致**；换成 28dp 画布的 Glass 图标后，图形自带 2~3dp 边距，实际已 10dp 上下，再补就过宽 | **去掉 padding，回归 8dp** |
| A5 | `Card(insideMargin = PaddingValues(0.dp), …)` | `ui/Widgets.kt` 的 `GroupCard` | 以为要显式关掉内边距 | `CardDefaults.InsideMargin` 本来就是 `PaddingValues(0.dp)` | 把库默认值抄进调用点，读者会以为此处有特殊设定 | **删掉该参数** |

### 3.2 B 类：部分重复 → **已统一公共实现**

| # | 当前实现 | 位置 | 官方方案 | 处理 |
| --- | --- | --- | --- | --- |
| B1 | `rememberLayerBackdrop { drawRect(pageColor()); drawContent() }` 写了两遍 | `App.kt:136`、`ui/GlassScope.kt:59` | `rememberLayerBackdrop` 本身是官方的，"垫底色再录"是与官方 `rememberBlurBackdrop` 对齐的约定 | **抽出 `rememberPageBackdrop()`**，两处共用 |

### 3.3 C 类：业务特有，底层已用官方 → **保留**

| # | 实现 | 位置 | 为什么官方覆盖不了 |
| --- | --- | --- | --- |
| C1 | `AnimatedMiuixTheme`（53 个颜色角色逐项插值，320ms） | `ui/AnimatedTheme.kt` | 官方 `MiuixTheme(controller)` 是"取目标配色立刻换掉"，没有颜色过渡版本。这里用的是官方 `MiuixTheme(colors = …)` 重载，只是自己算过渡帧。整体 Crossfade 会让玻璃录制层跟着抖，所以必须逐色插值 |
| C2 | `ProgressButton`（进度填充画在按钮内部） | `ui/ProgressButton.kt` | 官方 `Button` 没有进度能力。移植自 InstallerX Revived（Apache-2.0，文件头已声明），未改绘制逻辑 |
| C3 | `backTransition` 的 Aosp / Classic / Scale 三档 | `ui/BackTransitions.kt` | 官方只有 `MiuixDefault` / `Modal` / `None` 三个预设；这里的三档是设置项，基于官方 `navGraphicsTransition` 实现，`Miuix` 档直接用 `NavTransitions.MiuixDefault` |
| C4 | `rememberWideLayout`（840dp / 600dp + 比例） | `ui/Adaptive.kt` | 阈值逻辑在官方 **example 侧** `utils/AdaptiveUtils.kt`，**不在库的公开 API 里**，无法依赖。阈值照抄，保证同一设备上与生态内其它应用同一时刻切换 |
| C5 | `pageScrollModifiers` / `pageContentPadding` / `Os4ListPage` | `ui/PageChrome.kt` | 官方 example 有同名工具，同样不在库里。`Os4ListPage` 还把"玻璃顶栏必须是录制层兄弟节点"这条硬约束固化成一个入口，避免每个页面各写一遍写错 |
| C6 | `cardColor()` / `pageColor()` | `ui/Widgets.kt` | Miuix 在 Monet 下会把 `surface` 与 `surfaceContainer` 压成同一个值，直接用主题角色会让页面和卡片糊成一片。必须由卡片色反推页面色才能保住四套配色下的层次 |
| C7 | `PreviewStage` / `ResultCard` | `ui/Widgets.kt`、`screens/ToolScreen.kt` | GIF 预览画布与结果卡是业务形态，官方无对应 |
| C8 | `SwitchRow` / `SliderRow` / `DropdownRow` | `ui/Widgets.kt` | 官方 Preference 的**薄封装**：统一行首图标与玻璃 style，避免 10+ 个调用点重复传同样的参数。不是重新实现控件 |
| C9 | `rememberAppVersion` / `ToolCatalog` / `LicenseCatalog` | `ui/` | 纯业务数据与格式化 |

### 3.4 D 类：确实需要自写 → **保留**

| # | 实现 | 位置 | 限制 |
| --- | --- | --- | --- |
| D1 | `rememberDeviceCornerRadius` / `safeBottomGap` | `ui/SystemInsets.kt` | 库没有"设备屏幕圆角"的读取与圆弧安全区推导；这里用公开的 `RoundedCorner` API + 圆弧几何解 |
| D2 | `rememberBottomBarClearance` 里复刻的 `floatingMargin` | `ui/PageChrome.kt` | `GlassNavigationBar` 内部 `floatingMargin` 是**函数内局部变量**，没有暴露成 `*Defaults`。高度已改用库常量，只剩这一段规则是复刻的，已在注释标注"上游若改要跟着改" |
| D3 | `GlassScope` / `isGlassSupported` | `ui/GlassScope.kt`、`ui/GlassSupport.kt` | 为两条硬约束服务：①录制层必须是玻璃容器的兄弟节点（否则 `RenderNode::prepareTreeImpl` 递归 SIGSEGV）②玻璃开关必须单点决策 |

### 3.5 顺手清掉的死代码

| # | 位置 | 说明 |
| --- | --- | --- |
| E1 | `ui/Widgets.kt` 的 `InfoRow` | 全项目零调用（各页面都已改用 `ArrowPreference` / `BasicComponent`） |
| E2 | `ui/Widgets.kt` 的 `Gap` | 全项目零调用（页面留白走 `extraBottom` 或 `GroupCard` 的 `bottomPadding`） |

---

## 4. 上一轮"保留原实现"的复核结果

| 项 | 复核结论 | 处理 |
| --- | --- | --- |
| 作品页底部面板 | 重新看了 Scaffold 层级：`LibraryScreen` 的 `Box` 与 `Os4ListPage` 内部的 `Scaffold` 是**兄弟**，面板拿不到 `LocalDialogStates` 宿主。要造宿主只能在页面外包一层 Scaffold —— 那会改变 `contentWindowInsets` 与背景录制层，属于"为玻璃效果引入不稳定渲染" | **保留 `WindowBottomSheet`** |
| 退出确认对话框 | `WindowDialog` 实测：底部滑入、窗口 dim、左右并排按钮（含 error 色），符合 OS4 对话框形态 | **保留** |
| 底部操作区 | 现在是最小可行解：与页面同色的纯色底（视觉上等于无背景）+ 官方 24dp 下限 + 圆角安全区。官方 `GlassPage` 的 `bottomBar` 敢不铺背景，是因为它里面是带材质的玻璃导航胶囊；二级页只有一个按钮时，不铺就会透出列表内容 | **保留** |
| 玻璃录制层 | 未上移，遵守 `AGENTS.md` 硬约束 1。本轮反而把两处重复的构造抽成了一处 | **保留 + 统一** |

---

## 5. 官方有、但本轮**明确不采用**的能力

记录下来，避免下一轮重复排查：

| 官方能力 | 不采用的原因 |
| --- | --- |
| `Tooltip` / `Modifier.tooltip`（`basic/Tooltip.kt`） | 项目没有需要长按/悬停解释的紧凑控件；说明文字都走 `summary` 行内副标题 |
| `PullToRefresh` | 作品页可以加下拉刷新，但那属于**功能增强**，不是复用清理 |
| `FloatingActionButton` / `FloatingToolbar` | 无对应交互需求；工具页主操作用的是底部通栏按钮 |
| `Badge` / `BadgedBox` | 底栏与列表都没有计数徽章需求 |
| `GlassTabRow` / `GlassSegmentedTabRow` | 项目没有分段页签；主题页的离散选项用 `DropdownRow`（两三个选项时分段控件会把字压扁） |
| `RangeSliderPreference` | 工具页的"起始时间 + 截取时长"用两个独立滑块，比区间滑块更直观（区间滑块需要用户理解两端语义） |
| `WindowSpinnerPreference` / `OverlaySpinnerPreference` | 与 `GlassDropdownPreference` 同类，只是呈现形态不同；选下拉是为了和系统设置页一致 |
| `squircleBackground` / `absoluteSquircleClip` | 只给应用图标用了 `squircleClip`；GIF 预览画布是内容区，普通圆角更合适，不该套图标曲率 |

---

## 6. 本轮修改涉及的文件

| 文件 | 改动 |
| --- | --- |
| `build.gradle.kts` | 新增 `miuix-squircle` 依赖 |
| `ui/PageChrome.kt` | `GlassBarHeight` 常量删除，改用 `GlassNavigationBarDefaults.Height` |
| `ui/Widgets.kt` | `LeadingIcon` 去掉 4dp padding；`GroupCard` 删冗余 `insideMargin`；删 `InfoRow`、`Gap`；清理无用 import |
| `ui/GlassScope.kt` | 新增 `rememberPageBackdrop()`；`rememberGlassScope` 复用它 |
| `App.kt` | 全局 backdrop 改用 `rememberPageBackdrop()` |
| `screens/AboutScreen.kt` | 图标 `clip(RoundedCornerShape)` → `squircleClip` |
| `screens/LibraryScreen.kt` | `FileRow` 改 `ArrowPreference`，删自拼箭头与相关 import |

---

## 7. 编译与运行验收

| 项 | 结果 |
| --- | --- |
| `:assembleDebug` | BUILD SUCCESSFUL |
| `:assembleRelease` | BUILD SUCCESSFUL，`GifHelper-release.apk` **3,249,774 B**（与上轮完全一致 —— 本轮只删代码、换等价实现，R8 后体积无变化） |
| 版本 | `1.4` / versionCode 6（沿用，未打 tag、未发布） |
| 崩溃 | debug / release 全程 0 次 `Fatal signal` / `FATAL EXCEPTION` |
| 已验收 | 首页列表（图标-文字间距回归 8dp 后不贴字）、关于页图标（连续曲率、无白边）、作品页行（箭头与设置页一致、点击正常弹出面板）、顶栏滚动模糊（抽公共录制层后无糊影、无崩溃）、底栏高度与位置 |
| 未覆盖 | 真机（模糊质感、CrossAxis 跟手）、宽屏 Rail、AMOLED、大圆角机型 —— 与上轮相同，属环境限制 |

---

## 8. 仍然存在、但本轮不处理的问题

1. **`rememberBottomBarClearance` 的 `floatingMargin` 仍是复刻的**。库没暴露这个值，只要上游改规则就得手工跟。已在注释标注，并在 `docs/os4-deep-audit.md` 留了同样的提醒。
2. **`GlassDialog` / `GlassBottomSheet` 在 `GifApp` 层不可用**（无 Scaffold 宿主）。这是结构性限制，不是本轮能顺手解决的；如果将来给 `GifApp` 加根 Scaffold，要连带重新验证 inset 与录制层。
3. **真机未验证项**：模糊质感、CrossAxis 手势跟手与误触率、大圆角机型的安全区。这三项从 1.3 起就在待验证清单里。
4. **`ToolScreen` 的参数区在空素材时就占满一屏**（`ui-audit.md` 第 35 行"记录但未改"）。本轮复核仍认为这是可用状态，不是缺陷。
