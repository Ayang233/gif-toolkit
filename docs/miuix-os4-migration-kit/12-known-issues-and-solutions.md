# 12 · 真实问题案例库（GIF 助手 1.10）

> 45 个真实案例。来源：`GifHelper/AGENTS.md`、`docs/os4-round5~11-audit.md`、
> `docs/os4-deep-audit.md`、`docs/os4-reuse-audit.md`、`docs/OS4-Glass-UI-Plan.md`、`docs/ui-audit.md`。
> **每条都标注"可迁移性"**：是 MIUIX 通用约束，还是 GIF 助手的结构选择。
>
> 编号规则：`C01`~`C45`，与审计轮次无关，按主题分组。

## 0. 按症状速查

| 你看到的现象 | 看哪条 |
|---|---|
| 直接闪退 / `prepareTreeImpl` | C10 |
| 弹层没有背景模糊、只剩材质 | C16、C17 |
| 弹层下半截发灰 + 横向硬边 | C12 |
| 玻璃面板上有"糊影"/脏斑 | C13 |
| 页面上有一块比页面亮的方块/色带 | C14 |
| 顶栏一条深色带 + 返回键错位色斑 | C15 |
| 侧栏压住内容 / 横屏被压 52dp | C01 |
| 内容被压成竖带、右侧空 250dp | C02 |
| 侧栏展开时整页卡 | C09 |
| 底栏"闪没"或浮在退出页之上 | C20、C21 |
| 底栏"缩一下" | C21 |
| 侧栏在、内容全白（0 宽） | C28 |
| 手机横屏变成 250dp 常驻面板 | C06 |
| 竖屏进二级页后旋转，导航又出现 | C05 |
| 顶栏标题不跟着让位 | C04 |
| 主操作条左端贴屏幕边缘 | C03 |
| 横屏预览区像一块空白 | C07 |
| 关于页顶栏下方一条灰白硬边 | C08 |
| 关于页卡片压住图标/标题 | C23 |
| 关于页图标与标题整块不见 | C24 |
| 关于页图标偏小/带底板 | C25 |
| 禁用按钮几乎读不出字 | C37 |
| 按钮内进度居中/换色难看 | C38 |
| 首尾页还能拖动 | C39 |
| 按钮下面多一层渐变 | C40 |
| 下拉深色下比顶栏厚一档 | C34 |
| 面板行标题不对齐、最后一行贴导航条 | C35 |
| 旋转滑块出现 67.5° | C31 |
| Monet 下选中项不是主色 | C32 |
| 色板无标签、选中时整行跳 | C33 |
| 升级后 AGP 配置期报 37.2 | C41 |
| composite build 配置期 exit 128 | C42 |
| release 包里有调试字符串 | C43 |
| 文档说有个组件但代码里没有 | C44 |
| 抄了库常量/多补间距/自拼箭头 | C45 |

---

# 第一部分：详细案例（14 个）

## C10 · backdrop 自采样 → `libhwui` 原生栈溢出闪退

| 字段 | 内容 |
|---|---|
| 轮次 | 1.1 起成为硬约束；R6、R8 §6.3 各复现一次 |
| 现象 | 直接闪退（`SIGSEGV`）。logcat 里 `RenderNode::prepareTreeImpl` 递归几百层 |
| 根因 | 玻璃容器是 `layerBackdrop` 录制层的**子节点** → 采样到自己 |
| 涉及源码 | `App.kt:219`、`:256`；`ui/GlassScope.kt:149`、`:278` |
| 修复 | 录制层与所有玻璃消费者保持**兄弟**关系；录制层不上移到 `Scaffold` |
| 验证 | 反复进出页面 20 次；logcat 搜 `Fatal signal`/`prepareTreeImpl` 零命中 |
| 可迁移性 | ✅ **MIUIX 通用约束**（库的采样机制决定）。GIF 助手特定之处只是把它写进了 `Os4ListPage` |
| 出处 | `AGENTS.md` 第 1 条；`docs/os4-deep-audit.md` §8；R6 §3；R8 §6.3 |

两种具体触发（新项目同样会遇到）：

1. 把页面录制层上移到 `Scaffold` 自身，而顶栏与内容**共用**同一个 backdrop。
2. 为了让顶栏采到渐变，在页面根部给渐变套一层满屏 `layerBackdrop`，再把顶栏采样源指过去。

> 官方 `GlassPage.kt` 敢把录制层上移，是因为它顶栏采的是 `LocalTopAppBarBackdrop`（**另一个** backdrop，
> example-only）。**采样源不同，结构就不同。**

---

## C12 · 卡片局部录制层 → 弹层下半截无像素 + 横向硬边

| 字段 | 内容 |
|---|---|
| 轮次 | R5，1.5 |
| 现象 | 「预测性返回动画」下拉（1 行卡片 ≈ 56dp、5 项弹层）弹出后，伸出卡片下边界的部分**发灰**，能看到一条**横向硬边**；同页「主题模式」下拉（3 行卡片 ≈ 170dp、弹层落在卡内）看起来正常——同一个组件 |
| 根因 | `GroupCard` 上挂的 `Modifier.layerBackdrop(cardBackdrop)` 只录卡片区域，弹层越界部分没有像素 |
| 排除项 | 材质、`style`、宿主层级、坐标偏移（库用 `layerCoordinates.localPositionOf`，跨容器精确） |
| 修复 | 改传**整页**录制层；新增 `LocalPageBackdrop` 由 `ContentBackdropBox` 下发；`GroupCard` 不再自建录制层 |
| 验证 | 静态/Monet × 明/暗/AMOLED/关玻璃，两个菜单各截一次 |
| 可迁移性 | ✅ **MIUIX 通用约束**（"采样源必须在组件整个绘制区域都有像素"）；🏠 "卡片不自带录制层"是项目选择 |
| 出处 | R5 §1.3；`AGENTS.md` 第 4 条 |

---

## C14 · 垫底色用 `pageColor()` → 深色页面出现亮 30 灰阶色带

| 字段 | 内容 |
|---|---|
| 轮次 | R10，1.8 |
| 现象 | 深色静态配色、竖屏工具页，沿 `x = 40` 竖扫：卡片色 `#242424` 到 `y = 2170` 结束，往下整块变 `#1E1E1E`，而同屏页面底色是 `#000000` |
| 根因 | `pageColor()`（由卡片色反推的次级表面）被当成"页面底色"用在录制层垫色与底部操作条遮挡色上；深色下与真实 `surface` 差 **30 个灰阶**（浅色只差 1） |
| 涉及源码 | `ui/Widgets.kt:134`（新增 `pageBaseColor()`）、`ui/GlassScope.kt:110`、`ui/Widgets.kt:502`、`ui/PageChrome.kt:343`、`App.kt:219` |
| 修复 | 新增 `pageBaseColor()` = 页面真正铺在屏幕上的那层（`surface`，AMOLED 下纯黑）；五处统一 |
| 验证 | 深色工具页竖向逐点取色：`y=2170` `#1E1E1E`→`#000000`；`y=2300` `#040404`→`#000000` |
| 可迁移性 | ✅ 通用（玻璃垫色必须等于页面真实底色）+ 🏠 三层派生色体系是自建 |
| 出处 | R10 §1.3；`AGENTS.md` 第 2 条 |

---

## C15 · 关于页顶栏采到空像素 → 真机深色带 + 返回键错位色斑

| 字段 | 内容 |
|---|---|
| 轮次 | R11，1.9 |
| 现象 | 真机截图：顶栏下方一条边缘生硬的**深色带**；返回键左侧一小块**错位玻璃色斑** |
| 根因 | `GlassTopAppBar` 内部建一层 `progressiveBackdrop`，并通过 `LocalGlassTopAppBarContext` 把 `buttonBackdrop` 交给返回键 —— **顶栏模糊层与返回键胶囊采同一份像素**。而关于页正文层 `opaqueBase = false`（不垫底色）、顶部 40dp 是图标留白、首项是透明占位 → 顶栏区域**什么都没画** |
| 涉及源码 | `screens/AboutScreen.kt:147-151`、`:167`；`ui/GlassScope.kt:201`；新增 `ui/CombinedBackdrop.kt:60` |
| 修复 | 新增 `CombinedBackdrop` 叠加两层；`PageTopBar` 增加 `backdrop` 参数；关于页传 `rememberCombinedBackdrop(渐变层, 正文层)` |
| 验证 | 模拟器只能验"无崩溃、卡片不压标题"；**顶栏玻璃观感必须真机** 🔴 |
| 可迁移性 | ✅ **通用**（采样源必须在所有状态下都有像素） |
| 出处 | R11 §1.1-1.3、§4.2；`AGENTS.md` 第 1 条 |

---

## C16 · `HorizontalPager` 槽位坐标 → 弹层采样整块落空（实测 −1080）

| 字段 | 内容 |
|---|---|
| 轮次 | R6 记录症状，R7 探针实测并修复，1.5 |
| 现象 | 作品操作面板（`GlassBottomSheet`）表面均匀浅色、透不出后面的列表；表面像素 255 → 237（材质生效但背景模糊不可见） |
| 根因 | 页面录制层在 Pager 的页面里，`positionInWindow()` 报**槽位坐标**（第 2 个 tab x = 1080）；弹层由最外层 Scaffold 绘制、报**真实坐标**（x = 0）；相减 = `−1080`，采样矩形整块在录制层外 |
| 探针输出 | 修复前 `panelWin=(0,1777) recWin=(1080,0) offset=(-1080,1777)`；修复后 `offset=(0,1777)`；`panelSize = 1080×623`，`layer = 1080×2400` |
| 修复 | 最外层 `Scaffold(Transparent, insets=0)` 作弹层宿主；录制层下沉到一级页、**录在 Pager 外**；新增 `LocalRootBackdrop`；`pageOverlay` 一处覆盖所有页面级弹层；删除 `modalOverlayOpen` |
| 验证 | 探针坐标；纵向亮度剖面（同区域 x 300..800、y 1800..2400）：修复前全程 **238.4**（min=max），修复后 **234.0 → 239.0**；官方示例同段有 **12 级**起伏 |
| 可迁移性 | ✅ **通用**（`localPositionOf` 只在同一坐标空间自洽） |
| 出处 | R7 §3；`AGENTS.md` 第 1、4 条 |

> 这条最值得记住：**浅色内容 + 低对比 + 材质本身半透明**，会让人误判"玻璃没生效"。
> 必须用探针或高对比壁纸验证。

---

## C01 · 1200dp 横屏侧栏遮挡内容 52dp

| 字段 | 内容 |
|---|---|
| 轮次 | R9，1.7 |
| 现象 | 1200×540dp 横屏侧栏展开后，面板右边界压住卡片；图标只露一半、被糊成灰斑；分组标题被盖 |
| 实测 | 面板右边界 652px/326dp；卡片可见起点 650px/325dp；卡片真实左边界 548px/274dp；被遮 **104px = 52dp** |
| 根因（三重） | ① 面板起点 = 安全区 64dp + Margin 12dp，右边界 = 326dp；② `pageContentPadding` 只用了 `innerPadding` 的纵向，横向安全区被丢掉，内容左边界 = 12 + 262 = 274dp；③ 竖屏挖孔在顶部、横向安全区 = 0，`12+250=262` 与 `262` 刚好相等 → 竖屏测不出 |
| 修复 | 左右改为 `horizontal + innerPadding.calculate{Left,Right}Padding + railInset()`；安全区组合期取一次，`railInset()` 仍在量测期求值 |
| 验证 | 2400×1080 截图逐点（÷2=dp）；修复后卡片左边界 **338dp** = 64 + 262 + 12；面板右边界 324.5dp；间隙 13.5dp |
| 可迁移性 | ✅ **通用**（Rail 吃安全区、内容不吃 → 差值 = 起始边挖孔 inset）；🏠 `railInset` 下发机制是自建 |
| 出处 | R9 §1.1-1.5；[05](05-navigation-and-adaptive.md) §5 |

---

## C09 · 侧栏展开动画逐帧整页重组

| 字段 | 内容 |
|---|---|
| 轮次 | R8，1.6 |
| 现象 | 侧栏展开/折叠的 300ms 里滚动位置与滑动状态被打断（整页重组 + 列表重排） |
| 根因 | `LocalRailInset` 原是**值**类型，`App.kt` 在**组合期**算 `(250+12).dp * railState.progress`；`progress` 每帧变化 → `LazyColumn.contentPadding` 每帧变 → 整个列表重排 |
| 修复 | 下发**函数** `() -> Dp`；`pageContentPadding` 返回自定义 `PaddingValues`；新增 `Modifier.railStartInset(base)` 用 `layout{}` 在量测期读并 `placeRelative`（自带 RTL 镜像） |
| 验证 | 1200dp 展开态让位 262dp；914dp 折叠让位 0；**无逐帧像素证据**（R9 §1.5 明确记录未录逐帧） |
| 可迁移性 | ✅ **通用**（`progress` 是动画值；官方迁移指南红线之一） |
| 出处 | R8 §1-§2；[06](06-glass-architecture.md) R8 |

---

## C20 · 底栏用 `stack.size` 挂载 → "闪没" + 浮在退出页之上

| 字段 | 内容 |
|---|---|
| 轮次 | R5 发现，R7 修复，1.5 |
| 现象 | 进二级页时底栏**立即卸载**（新页约 250ms 后才盖上来，第一帧就能看到"闪没"）；返回时 `stack.size` 变回 1，底栏立即挂载并画在 `NavDisplay` 之上 → 浮在退出中的二级页上 |
| 根因 | 把"底栏在场"绑在页面栈深度上，而底栏位于 `NavDisplay` 之外；`modalOverlayOpen` 又引入跨页面运行期状态 |
| 修复 | 底栏成为一级页自己的浮层（`entry<Route.Root>` 内、录制层兄弟位），**不判断**栈深度；删除 `modalOverlayOpen` |
| 验证 | 60fps 录屏逐帧：底栏「作品」文字中心 x≈359 → 170（**−189px**），同时新页左边缘 x≈318；`−189/270 ≈ 0.70`，与 `318 = 1080×(1−0.70)` 互为印证 |
| 可迁移性 | 🏠 **项目结构选择**（官方底栏天然在一级页内部，所以"不需要判断"）；但"被盖住/被揭开"是官方行为，值得照抄 |
| 出处 | R5 §1.4；R7 §2.1-2.2；R8 §3；`AGENTS.md` 第 12 条 |

---

## C24 · `DstIn` 的 `textureBlur` 在软件渲染上不产出像素

| 字段 | 内容 |
|---|---|
| 轮次 | R8 首测（1.6），R10 定为"保留原生实现、真机待验证"（1.8），1.10 加跳过 |
| 现象 | 玻璃开时关于页图标与软件名称**整块不可见**；关玻璃时正常（黑色剪影 + 35sp 粗体） |
| 根因 | `textureBlur(..., contentBlendMode = DstIn)` 是"用采样结果**替换**内容"；`swiftshader_indirect` 上编译得出但不产出像素 → 内容被空结果替换；`ColorFilter.tint` 救不回来 |
| 修复 | R8 曾改自制渐变（`drawContent` + `drawRect(brush, SrcIn)`）；R10 **整段删除**回到原生；1.10 在软件渲染时跳过 `textureBlur` |
| 验证 | 玻璃开/关对照截图；官方示例 APK 同机对照（标题与两张卡都不显示）；`grep gradientFilled|accentBrush|BgPalette` 零命中 |
| 可迁移性 | ✅ **通用**（`contentBlendMode` 语义决定）；**真机表现未验证** 🔴 |
| 出处 | R8 §6.3；R9 §3.3；R10 §2.1/§2.5/§8.2；`AGENTS.md` 第 15 条 |

---

## C37 · 禁用态跨档拼装 → 对比度 1.12:1

| 字段 | 内容 |
|---|---|
| 轮次 | R5 调研，R9 考证，R10 落地方案 A（1.8） |
| 现象 | 无素材时按钮 `#F2F2F2` 底 + `#FFFFFF` 字，几乎不可读 |
| 根因 | **不是"灰色选错了"，而是轨道色与文字色来自两套不同的档位**：`disabledSecondaryVariant`（次档底）+ `disabledOnPrimaryButton`（主档字） |
| 修复 | 同档配对：`disabledPrimaryButton` + `disabledOnPrimaryButton`（方案 A，保留"这是主 CTA"的蓝底语义） |
| 实测 | 修前浅色 **1.12:1**；修后静态浅 **1.43:1**、静态深 **2.40:1**、Monet 浅 **1.47:1** |
| 可迁移性 | ✅ **通用**（库按档成对配色：主档 1.43:1、次档 1.89:1） |
| 出处 | R5 §1.1、§3.1；R9 §5；R10 §3 |

---

## C41 · `compileSdk 37.2` 强制要求导致配置期失败

```text
Dependency ':miuix:miuix-material' requires libraries and applications that
depend on it to compile against version 37.2 or later of the Android APIs.
: is currently compiled against android-37.0.
```

- 根因 📌：新基线 `BuildConfig.kt` 把 `COMPILE_SDK = 37 / COMPILE_SDK_MINOR = 2`、`TARGET_SDK = 37`。
- 修复：AGP 9 块语法

```kotlin
compileSdk { version = release(37) { minorApiLevel = 2 } }
```

- `targetSdk` 可以继续停在 36（只影响编译期 API，不改运行时行为）。
- 可迁移性：✅ **通用**，任何依赖 `miuix-material` 的下游都必须同步。
- 出处：R8 §0.1；`AGENTS.md` 构建坑 1；[02](02-version-and-dependencies.md) §6 #1。

---

## C42 · `miuix-glass` 未在 Central + composite build `exit 128`

| 字段 | 内容 |
|---|---|
| 现象 | 没有源码时 Gradle 同步失败；只克隆不打补丁时配置阶段 `exit 128` |
| 根因 | ① 两个 glass 模块当时未发布（须现场核实）；② `build-logic` 在配置阶段调 git 取版本号，而 composite build 的子构建工作目录是宿主工程（不是 git 仓库） |
| 修复 | `includeBuild` + 给 `miuix/build-logic/src/main/kotlin/BuildConfig.kt` 的 `getGitVersionCode()`/`getGitHashShort()` 各加 `workingDir = rootDir`（2 处，`1 file changed, 4 insertions(+)`）；脚本可重复执行、已打过跳过 |
| 验证 | 每轮 `git reset --hard` 后重新 `git apply` 不丢；`scripts/setup-miuix.sh` 可重建 |
| 可迁移性 | ✅ 通用（源码依赖 MIUIX 的工程都要这处补丁） |
| 出处 | R5/R6/R8/R9 §0；`AGENTS.md` 构建坑 2；[02](02-version-and-dependencies.md) §4.3 |

---

## C28 · `GlassNavigationRail` 放进 `Row + weight` → 内容 0 宽

| 字段 | 内容 |
|---|---|
| 轮次 | 1.3（方案期错误，实施时被实测推翻） |
| 现象 | 「侧栏在、右侧一片空白」；内容宽度为 0 |
| 根因 | Rail 根节点是 `BoxWithConstraints(fillMaxSize())`，自己决定面板位置（左 12dp + 宽 250dp）。放进 `Row` 当 `weight` 兄弟会吃掉整行宽度 |
| 修复 | 改为**叠放在内容之上**：`Box { Box(fillMaxSize + padding(start=侧栏宽) + layerBackdrop) { NavDisplay }; Rail }`；后演化为 `LocalRailInset` |
| 可迁移性 | ✅ **通用**（凡是内部 `fillMaxSize` 的 Dock/Rail 类组件都不能参与 Row 的宽度分配） |
| 出处 | `OS4-Glass-UI-Plan.md` §1/§6 #3；`AGENTS.md` 第 8 条 |

---

## C22 · 一级页返回被自加 `BackHandler` 拦截 → 出"确认退出"框

| 字段 | 内容 |
|---|---|
| 轮次 | R5，1.5 |
| 现象 | 一级页 tab 0 按返回时弹「退出 GIF 助手？」；预测性返回的"预览退出"不发生 |
| 根因 | 自加 `BackHandler(enabled = stack.size <= 1)`，系统拿不到返回事件。官方 `MainScreenBackHandler`（`AppContent.kt:910`）的条件含 `selectedPage != 0` |
| 修复 | `BackHandler(enabled = stack.size <= 1 && tab != 0)`；删掉确认框；tab 0 交还系统 → `finish()` |
| 验证 | 官方 APK 连续三次返回：About → 设置 → 工具 → **直接回桌面**（`dumpsys` 确认前台变 launcher） |
| 可迁移性 | ✅ 通用（官方返回语义）+ 🏠 多出来的那层拦截是本项目自加 |
| 出处 | R5 §1.5；`AGENTS.md` 第 12 条 |

---

# 第二部分：表格案例（31 个）

## 2.1 布局 / 安全区 / 让位

| 编号 | 现象 | 根因 | 修复 | 可迁移性 | 出处 |
|---|---|---|---|---|---|
| **C02** | 1200dp 展开侧栏时右侧空出 250dp，内容压成竖带 | `pageContentPadding` 把 `railInset` 同时加在 start 与 end | 只让 start 一侧 | 🏠 结构选择 | R7 §5；R9 §1.5 |
| **C03** | 主操作条左端贴屏幕边缘、横屏压挖孔 | `railStartInset(base)` 的 `base` 被忽略；控件不在 Scaffold 槽位拿不到 `innerPadding` | `base` 真正参与；新增 `rememberHorizontalSafeInsets()` | ✅ 通用 + 🏠 适配层 | R9 §1.4 |
| **C04** | 宽屏时顶栏标题不跟着让位 | 未接官方的"只挪前景"通道 | 玻璃档传 `contentModifier`，非玻璃档挂 `modifier` | ✅ 通用 | R9 §1.4；R10 §5.1 |
| **C05** | 竖屏进二级页 → 旋转横屏 → 一级导航又出现并让出 250dp | Rail 只判 `wide`，没读页面栈 | `showPrimaryNav = stack.size <= 1`；`railVisible = wide && showPrimaryNav` | 🏠 产品决定（官方宽屏二级页保留 Rail） | R6 §2 |
| **C06** | 914dp 手机横屏变成 250dp 常驻侧栏 | 写死 `initialExpanded = true` + `showToggle = false` | `initialExpanded = width >= 1200dp`；`showToggle` 交回默认 | ✅ 通用（阈值是 example 的） | R6 §1 |
| **C07** | 横屏工具页预览区像一块空白 | 预览高按 16:9 = 620dp，远超窗口 411dp | 高度上限按窗口高度 42%/32% | 🏠 业务特定 | R7 §5 |
| **C08** | 关于页顶栏下方一条灰白硬边 | 渐变放进 Scaffold 内容区，没铺到状态栏下 | 渐变铺在页面根部、Scaffold 之外 | ✅ 通用 | CHANGELOG 1.6 |

## 2.2 录制层 / 采样

| 编号 | 现象 | 根因 | 修复 | 可迁移性 | 出处 |
|---|---|---|---|---|---|
| **C11** | 底栏无法复刻"被新页盖住"，只能整块消失 | 底栏在 `NavDisplay` 之外，跟着转场就会进录制层 → 自采样 | 底栏下沉为一级页浮层 | ✅ 通用红线 + 🏠 结构 | R5 §1.4；R7 §2.1 |
| **C13** | 顶栏模糊带有"糊影/脏斑" | 裸 `rememberLayerBackdrop` 只录内容，空白处透明 | 先 `drawRect(pageBaseColor())` 再 `drawContent()`；抽成 `rememberPageBackdrop` | ✅ 通用（对齐官方 `rememberBlurBackdrop`） | CHANGELOG 1.3；deep-audit P1-4 |
| **C17** | 一级页下拉采样差一整页宽（潜伏缺陷） | `DropdownRow` 固定取页面层，而弹层由根 Scaffold 绘制 | 改 `LocalRootBackdrop ?: LocalPageBackdrop` | ✅ 通用 + 🏠 封装 | R10 §4.2 #5 |
| **C18** | 同一类弹层多轮反复出现不同症状 | 三层录制层（root/page/card）各有坐标与覆盖范围，选错即落空 | 一级页弹层用 root 层；二级页回落页面层；`pageOverlay` 槽位统一选择 | ✅ 通用（两级宿主） | R7 §3.4；R10 §5 |
| **C19** | 作品面板是纯白不透明面板 | `GlassBottomSheet` 缺 Scaffold 宿主 → 静默不渲染；`WindowBottomSheet` 没有 `backdrop` 参数 | 加 `pageOverlay` 槽位；改用 `GlassBottomSheet(backdrop)` | ✅ 通用 | deep-audit P1-5；R6 §5 |

## 2.3 组件误用 / 几何

| 编号 | 现象 | 根因 | 修复 | 可迁移性 | 出处 |
|---|---|---|---|---|---|
| **C21** | 底栏"缩一下" | `visible = false` 是"缩小+淡出+自身模糊一起收" | 不挂载，不用 `visible=false` | ✅ 通用 | R5 §1.4；R6 §3 |
| **C23** | 关于页卡片压住图标/标题；标题区永不淡出 | 首帧 `headerHeightPx = 0` → 占位 0；进度判据 `> 0` 不成立 | 首帧兜底 **300dp**（与官方 `logoHeightDp` 同值） | 🏠 版式来自参考项目 | R11 §2 |
| **C25** | 关于页图标偏小 / 带白底板 | 88dp 容器 + `cardColor(α .72)` 底板 | 百 dp 容器 + 主体 132dp + 无底板（实测主体 98×49dp） | 🏠 由素材 2:1 比例决定 | R9 §4；R10 §2.4 |
| **C26** | 关于页是第二套骨架，顶栏采样源要手写 | 自建 Scaffold | 给骨架加 `background`/`largeHeader`/`topBarBackdrop`，并回骨架 | 🏠 骨架参数化 | R10 §4.3；R11 §7.1 |
| **C27** | 关于页卡片从"不透明"改"玻璃"又改回 | `textureBlur` 空采样时卡片连入口一起消失 | 玻璃卡 + `backdrop == null` 时不透明卡回退 | ✅ 通用（空采样）+ 🏠 回退策略 | R10 §2.7；R11 §7.2 |
| **C29** | 升级后 4 处编译失败 | Glass 图标改名 | `Folder`→`FolderOutline` 等 15 组 | ✅ 通用 | `OS4-Glass-UI-Plan.md` §3 |
| **C30** | Glass 图标塞 24dp 槽偏小；返回键错用 extended 族 | Glass 族是 28dp 画布；两族线宽不同 | 26dp 列表 / 28dp 动作；返回键玻璃档用 `Glass.ChevronBackward` | ✅ 通用 | deep-audit P2-10 |
| **C31** | 旋转滑块出现 67.5°，画面被斜切 | `steps = 3` 表示"中间 3 档" = 5 档（0/67.5/135/202.5/270） | `steps = 2`（0/90/180/270） | ✅ 通用（`steps` 语义）+ 🏠 下游按浮点旋转 | R10 §4.1 |
| **C32** | Monet 下底栏/侧栏选中项不是主色 | `MiuixTheme(colors=)` 少了 `LocalColorSchemeMode`（internal，补不了） | 调用点显式传 `selectedColor` | ✅ 通用（两个重载不等价） | R10 §4.2 #2 |
| **C33** | 色板 TalkBack 读空标签；选中时整行跳动 | 自绘 `Box.clickable` 无 role/selected；槽位尺寸随选中变化 | `selectable(role = RadioButton)` + `stateDescription` + 固定 28dp 槽位 | 🏠 版式 + ✅ 无障碍通用 | R10 §4.2 #3 |
| **C34** | 深色下下拉弹层比顶栏厚一档 | 显式传 `visuals = GlassPopupDefaults.visuals(style = GlassDefaults.style(isDark))` | 删掉 `visuals`，回到库默认 `GlassDefaults.Style` | ✅ 通用 | deep-audit P2-9；R10 §4.2 #4 |
| **C35** | 面板行标题与面板标题不同线；最后一行贴导航条 | 面板 24dp + `BasicComponent` 16dp 双计；内容末尾无导航条留白 | 行内 `horizontal = 0`；末尾 `Spacer(navigationBarsPadding().height(8.dp))` | ✅ 通用 | R10 §4.2 #6 |
| **C45** | 抄库常量、多补 4dp 间距、冗余 `insideMargin`、自拼箭头 | 薄包装层抄默认值；28dp 画布图形自带边距 | 改用 `GlassNavigationBarDefaults.Height`；去掉多余 padding；`FileRow` 改 `ArrowPreference` | ✅ 通用 | reuse-audit §3.1 |

## 2.4 按钮 / 进度 / 手势

| 编号 | 现象 | 根因 | 修复 | 可迁移性 | 出处 |
|---|---|---|---|---|---|
| **C38** | 两条进度条；填充居中错位；64% 时文字读不出 | 进度块画在居中 `Box` 里、不在 shape 内；45% 换色是拍出来的常数 | 只留按钮内一条；填充画进 shape；45% 阈值仍未根治 🔴 | 🏠 生态移植件 | ui-audit #1/#11；R5 §1.2 |
| **C39** | 首尾页还能拖动；首次横滑白帧 | 原生 Pager 边界位移交给 `MiuixOverscrollFactory` 做过冲；`beyondViewportPageCount = 0` | 接官方三件套（`pagerGestureOverride` + `userScrollEnabled=false` + `PagerGestureNestedScrollConnection`）；预组合 0→1 | ✅ 通用（OverscrollFactory 是全局注入的） | deep-audit P0-1/P1-6 |
| **C40** | 二级页按钮下面多出一层渐变 | `PrimaryActionBar` 上有历史遗留的 `verticalGradient` 遮罩 | 改同色纯色底（`pageBaseColor()`） | 🏠 结构差异（官方 bottomBar 里是玻璃胶囊） | deep-audit P0-2 |

## 2.5 触觉 / 构建 / 文档

| 编号 | 现象 | 根因 | 修复 | 可迁移性 | 出处 |
|---|---|---|---|---|---|
| **C36** | 自绘色板无任何反馈；不确定哪些组件自带 | 库在 8 个组件族内部已有反馈 | 只补色板一处 `ToggleOn`（仅状态变化时） | ✅ 通用 | R8 §4；[09](09-interaction-and-haptics.md) |
| **C43** | release 里残留调试页字符串 | 运行时 `FLAG_DEBUGGABLE` 不能被 R8 折叠；`BuildConfig.DEBUG` 也不是字面量 | 自定义 `buildConfigField("boolean","DEBUG_UI","true/false")` 两道门 | 🏠 调试页 + ✅ 通用工程约束 | R10 §8.3 |
| **C44** | 文档说有 `SegmentedControl`，代码里没有 | 把"计划删除"写成"曾经存在" | 改写文档：分段控件用官方 `GlassSegmentedTabRow` | 🏠 纯文档 | R10 §9 |

---

# 附录 A · 实测数字汇总（引用时连环境一起写）

## A.1 布局

| 数值 | 含义 |
|---|---|
| **52dp（104px）** | C01 修复前遮挡宽度 |
| 326dp / 324.5dp | 侧栏面板右边界（修复前/后） |
| 274dp / 338dp | 卡片真实左边界（修复前/后） |
| 13.5dp（27px） | 修复后侧栏与卡片间隙 |
| `338 = 64 + 262 + 12` | 安全区 + 侧栏 + 外边距 |
| 262dp | 1200dp 下 Rail 展开让位（250 + 12） |
| 1200dp / 840dp /（600dp 且高宽比<1.2） | 展开 / 分栏 / 分栏阈值 |
| 64dp（128px） | 横屏挖孔起始边 inset |
| −189px | C20 过渡帧底栏文字位移（359→170） |
| x=1080 / offset −1080 | C16 Pager 槽位坐标 / 修复前采样偏移 |
| 56dp / ~170dp | C12 单行卡片 / 三行卡片高度 |
| 620dp / 411dp | C07 预览原高 / 横屏窗口高 |
| 42% / 32% | C07 高度封顶比例 |
| 300dp | C23 首帧头部兜底 |
| 40dp / 58dp | 头部比列表多的顶部留白 / 实际占位 = 头部高 + 58dp |
| 0.05 / 0.20 / 0.35 | 版本 / 名称 / 图标淡出起点（各 0.15 宽） |
| 300ms | 侧栏展开动画时长 |

## A.2 关于页

| 数值 | 含义 |
|---|---|
| 100dp / 132dp | 图标容器 / 采样尺寸 |
| 98 × 49dp（196 × 98 px @density 320） | 实测可见主体 |
| 108 单位 / 74.1% | 画布 / 主体占比 |
| 150f / 60f | 图标与标题 / 玻璃卡的 `blurRadius` |
| 35sp Bold / 14sp | 标题 / 版本字号 |

## A.3 色值

| 色值 | 含义 |
|---|---|
| `#F7F7F7` / `#FFFFFF` | 静态浅色页面 / 卡片 |
| `#000000` / `#242424` | 静态深色页面 / 卡片 |
| `#F6F6F6` / `#1E1E1E` | `pageColor()` 浅 / 深 |
| `#F9F9FF` / `#EDEDF4` | Monet 浅色页面 / 卡片 |
| `#131316` / `#1F1F22` | Monet 深色页面 / 卡片 |
| **30 灰阶** | 深色下 `pageColor()` 与页面差（浅色只差 1） |
| `#F2F2F2` + `#FFFFFF` | C37 修前禁用态（1.12:1） |
| `#C2D9FF` / `#253E64` / `#B5BED6` | `disabledPrimaryButton`（浅/深/Monet 浅） |
| 1.12 / 1.43 / 1.89 / 2.40 : 1 | 修前 / 方案 A 浅 / 次档 / 方案 A 深 |
| `0xcc4a4a4a`、`0xff4f4f4f`、`0xff1af200` | 浅色 `logoBlend`（ColorBurn / LinearLight / Lab） |
| `0xe6a1a1a1`、`0x4de6e6e6`、`0xff1af500` | 深色 `logoBlend`（ColorDodge / LinearLight / Lab） |
| `(26,28,32)` → `(71,93,146)`，各 906 像素 | C32 修前/修后选中图标 tint |
| 238.4（min=max）→ 234.0..239.0 | C16 面板纵向亮度剖面 |
| 12 级 / 3 级 | 官方示例 / 本项目下拉剖面起伏 |

## A.4 包体与构建

| 数值 | 含义 |
|---|---|
| 3,102,836 B | release 唯一 `classes.dex` |
| 3.2~3.3 MB | release APK |
| ~45 MB | debug APK |
| 17,073,862 B | 官方示例 APK |
| 0 / 0 / 0（release）；1 / 1 / 1（debug） | C43 dex 字符串搜索命中 |

---

# 附录 B · 文档之间的结论冲突（引用时要注明"以哪轮为准"）

| # | 议题 | 结论 A | 结论 B | 采信 |
|---|---|---|---|---|
| B1 | Monet 是否把 `surface`/`surfaceContainer` 压成同值 | 会（reuse-audit C6、旧注释） | **不会**，四套都不同值（R10 §1.3 实测） | B（有实测表） |
| B2 | 禁用态对比度 | 1.04:1（R5） | 1.12:1（R9/R10） | 以"同档配对"规则为准 |
| B3 | 方案 C 对比度 | 5.61:1（R9） | 5.64:1（R10，先 alpha 合成再算亮度） | B3 采信 R10 |
| B4 | `pageColor()` 浅色值 | `#F7F7F7`（R9 §5.2） | `#F6F6F6`（R9 §1.3、R10 实测） | `#F6F6F6` |
| B5 | 顶栏"能看出边界"的原因 | AGSL 编译失败（deep-audit P0-3） | 没传 `bandBrush`/`bandOverhang`（同文 §8）；R10 说 swiftshader 产出错位副本 | 环境不同，三种表现 |
| B6 | 弹层该不该采样背景 | 不采样（`backdrop = null`，Plan §1） | 必须采样（deep-audit P1-4、R5 起） | B6 采信"必须采样" |
| B7 | 卡片局部录制层是否可用 | 建议用（deep-audit P1-4） | 实测翻车（R5 §1.3） | B7 采信 R5 |
| B8 | 面板"不模糊"的原因 | 推断坐标偏移（R6） | 探针实测 −1080（R7） | B8 采信 R7 |
| B9 | 底栏行为 | 只能立即消失（R5/R6，改前） | 被盖住/揭开（R7/R8，改后） | 结构不同，都对 |
| B11 | "1200dp 竖屏通过"是否足够 | 通过（R8 §2.2） | 覆盖不足，横屏才暴露（R9 §1.3） | B11 采信 R9 |
| B12 | About 卡片是否用 `textureBlur` | 不用（R10 §2.7） | 用 `GlassCard`（1.10） | B12 采信 1.10 |
| B13 | About 是否自建骨架 | 有意保留差异（R10） | 并回 `Os4ListPage`（1.10） | B13 采信 1.10 |
| B14 | 图标/标题上色路径 | 自制 `SrcIn` 渐变（1.7） | 原生 `textureBlur + DstIn`（1.8 起） | B14 采信 1.8 |
| B15 | swiftshader 上 AGSL | 编译失败（API 34 时期） | 能编译但产出错位副本（R10） | 同环境两种形态 |
| B16 | `textureBlur` 真机是否正常 | README 说"真机正常" | 各轮报告写"真机待验证" | **采信"未验证"**（README 缺证据） |
| B17 | `SegmentedControl` 是否存在过 | AGENTS 旧文说有 | 源码零命中 | B17 采信"从未存在" |
| B18 | `railStartInset` 的 `base` | 设计意图是基准 | 实际被忽略（R9） | 已在 R9 修复 |

---

# 附录 C · 只是推断 / 未验证的结论（不要当事实传播）

| 结论 | 状态 |
|---|---|
| "弹层脏斑的成因更可能是录制层没铺底色" | 原文用"更可能"；恢复采样后是否复现脏斑当时列为待验证 |
| "采样区域很可能与面板位置存在偏移"（R6） | 次轮被探针替换 |
| "顶栏边界是 AGSL 编译失败所致"（deep-audit P0-3） | 同文 §8 实测推翻 |
| "底栏占位按 54dp 算，大字体时底栏变高会压住列表末尾" | 代码路径 + 推断，无实测 |
| "`progress == 1f` 时卡片材质退成 surface 0.6，可能出硬边" | 真机观察项 |
| "RTL 下 Rail 面板与 railInset 是否同向" | 未切语言验证 |
| "一级页多层录制 → 真机发热/掉帧" | 代码路径事实 + 影响未测 |
| 真机玻璃观感 / `textureBlur` / 顶栏渐进模糊 / 触觉手感 / 预测性返回跟手 / CrossAxis 误触率 / 面板底部导航条留白 / 大圆角安全区 / Monet 跟随真实壁纸 / 窄屏 <360dp / 进程死亡完整恢复 | 全部 **🔴 未验证** |

---

# 附录 D · 从案例反推的 12 条检查项（贴在 PR 模板里）

1. 每个 `layerBackdrop` 列出的子节点里，有没有玻璃消费者？
2. 每个玻璃消费者的 `backdrop` 在该组件整个绘制区域都有像素吗？
3. 录制层先 `drawRect(页面真实底色)` 了吗？
4. 跨「分页容器」的弹层，采样源录在容器外面吗？
5. 顶栏让位用 `contentModifier` 了吗？录制层没跟着收窄吧？
6. 有没有在组合期解包 `progress` / `offset` 这类动画值？
7. 导航组件是叠放还是进了 `Row`？
8. 禁用态的颜色是**同一档**配对吗？
9. 图标是同一族、同一尺寸来源吗？
10. `backdrop = null` 时功能入口还在吗？
11. 有没有为了让模拟器"看起来对"而自创视觉？
12. 有没有在文档里写"我们有个组件叫 X"却 grep 不到？
