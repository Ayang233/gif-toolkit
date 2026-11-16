# OS4 Glass 全面适配 · 实施方案

> 基线：Miuix `ac0af467`（2026-11-16）。本轮锁定此提交，不随上游漂移。
> 参考实物：`_archive/screenshots/os4-ref/`（自编译 example APK 实机截图，非他人截图推测）。

## 0. 基线与参考物的对账结果

| 对象 | 提交 | 说明 |
| --- | --- | --- |
| 用户提供的 APK | `2dae81bd` | 官方 example，versionName `1.0.9.2dae81bd` |
| 本地源码（改造前） | `d8efe18` | 比 APK 旧 1 个提交 |
| **本轮基线** | **`ac0af467`** | 比 APK 新 3 个提交，含 `GlassNavigationRail` 与新底栏间距 |

已完成的同步：`miuix/` 硬切到 `ac0af467`，并重打 composite-build 必需的
`workingDir = rootDir` 补丁（`build-logic/src/main/kotlin/BuildConfig.kt`，两处 `providers.exec`）。
旧状态留 tag `pre-os4-sync`（= `632928c2`），可回滚。

参考 APK 已自行编译并实机运行（`top.yukonga.miuix.uitest.debug`，versionName `1.0.9.ac0af467`），
截图见 `_archive/screenshots/os4-ref/`：`01_home` / `02_scrolled` / `03_settings` / `04_wide`。

### 从实机观察到的、与文档描述互为印证的事实

1. 顶栏未滚动时**看不到**图标胶囊，只有图标本身；滚动后才浮出圆形玻璃胶囊（带投影）。
2. 滚动时顶栏下方有**渐进模糊带**，内容以软糊形式透出（`02_scrolled` 顶部可见彩色糊斑）。
3. 底栏是**悬浮胶囊**：左右 24dp 内缩、底部留白由库按导航栏高度算、选中态是浅色药丸 + 图标变深，**不是**主色块。
4. 内容**从底栏下面穿过**（`03_settings` 底部能看到被遮住的下一行文字），这正是玻璃存在的前提。
5. 设置页的行**没有前置图标**，纯文字 + 右侧控件；下拉行右侧是「值 + 上下双箭头」。
6. 大标题下可直接带副标题（`Settings` / `v1.0.9 (1291)`）。
7. 分组标题左缩进与卡片内文字对齐（卡片 12dp + 内边距 16dp = 28dp）。
8. 卡片之间**有 12dp 下间距**，卡片内无分割线。
9. 宽屏（≥840dp）：左侧浮起**玻璃 Rail**（可展开成图标+文字），**底栏消失**，顶栏变居中窄标题、大标题折叠。
10. 玻璃图标是 **28dp 画布、5 档权重**（Light / Normal / Regular / Medium / Demibold），单色可染色，共 **512 个**。

## 1. 现状盘点：保留 / 改造 / 新增 / 删除

### 保留（已验证正确，不动结构）

- `ui/GlassSupport.kt` —— 玻璃唯一决策点。**页面里不得出现第二处能力判断**。
- 玻璃容器与录制层的**兄弟关系**（AGENTS.md 硬约束 1，历史上真崩过）。
- 弹层不采样背景（`DropdownRow` 的 `backdrop = null`）。
- `WindowBottomSheet` / `ExitConfirm` 的用法。
- `AnimatedTheme` 53 色插值、AMOLED、`BackTransitions` 五档、`rememberDeviceCornerRadius`。
- `ProgressButton` + `PrimaryActionBar`（业务核心，进度画在按钮内）。

### 改造

| 位置 | 现状 | 目标 |
| --- | --- | --- |
| `App.kt` 底栏 | 手算 `maxOf(24, inset+8)`、`defaultWindowInsetsPadding=false`、高度 60dp、主色 15%/22% 指示器 | 全交库：`defaultWindowInsetsPadding=true`、`height` 用默认 54dp、指示器用官方 `indicatorColor()` |
| `App.kt` Pager | 手写 `animateToTab`（EaseInOut + 100ms/页） | `PagerState.springAnimateToPage` + `PagerNavigationSpringSpec`，fling 同步换弹簧 |
| `App.kt` 布局 | `Box` 内 NavDisplay + 底栏 align | `Row`：宽屏放 Rail，窄屏放底栏；录制层结构不变 |
| `ui/GlassScope.kt` | `PageTopBar` 只有 title/subtitle/scrollBehavior | 增 `expandable = !isWide`、`bandBrush`、`bandOverhang`、`titleAlpha` |
| 各页滚动 | `Modifier.nestedScroll(behavior.nestedScrollConnection)` | 统一 `Modifier.pageScrollModifiers()`（+ `overScrollVertical` 喂 `overscrollOffset`、`scrollEndHaptic`） |
| 各页内边距 | 手算 `+2.dp` 顶、固定 `128/132dp` 底 | 统一 `pageContentPadding()`，底部取 `max(Scaffold inner, LocalBottomBarClearance)` |
| `ui/ToolCatalog.kt` | `extended` 线框图标 10 个 | 全部换官方 Glass 图标（见 §3） |
| `ui/Widgets.kt` | `GroupCard` 无下间距 | 加 `bottomPadding`（默认 12dp） |
| `screens/SettingsScreen.kt` | 大标题无副标题 | 副标题显示版本号（对齐官方设置页） |
| `scripts/setup-miuix.sh` | `PINNED_REF=d8efe18` | `ac0af467` |

### 新增

- `ui/PageChrome.kt`：`pageScrollModifiers()` / `pageContentPadding()` / `LocalBottomBarClearance` / `Os4Page`（把「Scaffold + 顶栏 + 录制层」固定成一种写法，杜绝自采样）。
- `ui/Adaptive.kt`：`shouldShowSplitPane()`（≥840dp，或 ≥600dp 且高宽比 <1.2）、`shouldExpandNavigationRail()`（≥1200dp）——阈值照抄官方。
- `ui/AppIcon.kt`：图标规范的唯一出口（尺寸/权重/染色），列表、顶栏、底栏都从它取。

### 删除

- `ui/Widgets.kt` 的 `SegmentedControl`：**全项目零调用**（死代码）。官方 `GlassTabRow` 是「各自独立药丸」，不是分段控件，也没有替代必要。
- `App.kt` 的 `animateToTab` 扩展函数（被 `springAnimateToPage` 取代）。

## 2. 组件复用对照表

| 我们的需求 | 直接复用的官方实现 | 不自己写 |
| --- | --- | --- |
| 玻璃底栏 | `GlassNavigationBar`（`visible` 自带缩放+模糊退场） | — |
| 宽屏导航 | `GlassNavigationRail` + `rememberGlassNavigationRailState` | — |
| 玻璃顶栏 | `GlassTopAppBar(expandable=…)` | — |
| 顶栏图标 | `GlassIconButton`（`LocalGlassTopAppBarContext` 继承尺寸/材质） | — |
| 页签 | 不需要（业务无多页签）；如需用 `GlassTabRow` | 自绘分段控件 |
| 说明浮层 | `TooltipBox` | — |
| 操作面板 | `WindowBottomSheet`（在用） | — |
| 下拉 | `GlassDropdownPreference`（在用，`backdrop=null`） | — |
| 列表行 | `BasicComponent` / `ArrowPreference` / `SwitchPreference` / `SliderPreference`（在用） | — |
| 图标 | `MiuixIcons.Glass.*`（512 个，5 权重） | 自绘矢量 |
| 滚动 | `pageScrollModifiers` 等价的 `overScrollVertical` + `scrollEndHaptic` | 手写 nestedScroll |

## 3. 图标规范（本轮统一口径）

- **底栏**：`Glass.All` / `Glass.FolderOutline` / `Glass.SettingsOutline`，渲染 25dp（库内 `IconSize`）。
  > 注意：`Glass.Folder`、`Glass.Settings` 在新版已改名，旧的 4 处引用已编译失败，必须同步改。
- **首页工具入口**：Glass 定色族，28dp 画布按 26dp 渲染：
  `ConvertToGif`（视频转 GIF）、`MotionPhoto`（动态照片）、`Album`（图片合成）、`Edit`（GIF 编辑）、
  `Compress`（压缩）、`Merge`（拼接）、`GridView`（拼图）、`VerticalSplit`（分解）、
  `ConvertFile`（转视频）、`Play`（播放）。全部为官方资源，无需自绘。
- **次级/设置/关于/许可**：`Glass.Light` 权重（同一套图形的细线档），26dp。
  > 比混用 `extended` 线框更统一；若实机对比后 Light 档在小尺寸下发虚，才退回 `extended`。
- **顶栏动作**：`GlassIconButton` 内 28dp（官方 `GlassTopAppBarDefaults.ButtonSize = 44dp`）。
- 染色：一律 `tint = onBackground` / 选中 `primary`（动态取色时）；Glass 图标是单色 `SolidColor(Black)`，可安全 tint。

## 4. 页面级清单

| 页面 | 改动 |
| --- | --- |
| 首页 | `pageScrollModifiers`；`pageContentPadding`；工具行图标换 Glass；卡片下间距 12dp |
| 工具页 | 同上；参数区保持 `SliderPreference`；结果卡保留；`NoteText` 收敛为 `BasicComponent` 的 summary |
| 作品页 | 同上；空态沿用居中图标+两行说明；`WindowBottomSheet` 不动 |
| 设置页 | 大标题副标题=版本；行图标统一 Light 26dp；分组顺序不变 |
| 主题页 | 仅内边距/顶栏参数对齐 |
| 关于/许可 | 仅内边距/顶栏参数对齐 |
| 宽屏全部页面 | 顶栏 `expandable=false`（居中窄标题）；内容不再拉伸到全宽而是让位 Rail |

## 5. 依赖顺序（为什么这么排）

```
A 基建（PageChrome / Adaptive / AppIcon）
   ↓ 所有页面都依赖
B 顶栏参数（GlassScope.PageTopBar）
   ↓ 页面骨架依赖顶栏签名
C 骨架迁移（6 个页面 → Os4Page + pageScrollModifiers）
   ↓ 底栏高度/留白会反向影响页面 padding
D 底栏对齐（参数 + Pager 弹簧）
   ↓
E 图标体系（ToolCatalog + 各页 icon 引用）
   ↓
F 宽屏 Rail（依赖 A/B/D 全部稳定）
```

## 6. 冲突与风险检查（先解决问题，再动手）

| # | 风险 | 处置 |
| --- | --- | --- |
| 1 | **已发生**：`Glass.Folder/Settings` 改名 → 4 处编译失败 | 已在方案内，改 `FolderOutline/SettingsOutline` |
| 2 | 底栏改 `defaultWindowInsetsPadding=true` 后高度变化（54dp + 库算底部留白），若页面仍写死 128dp 会出现空隙跳变 | 底部留白统一走 `LocalBottomBarClearance`，由 App.kt 单点计算下发 |
| 3 | 底栏若误入录制层 → 自采样 → `RenderNode::prepareTreeImpl` 栈溢出闪退 | 保持「底栏与录制层是兄弟」；新增 `Os4Page` 就是为了固化这一点 |
| 4 | 玻璃图标 28dp 画布塞进 24dp 槽位会因 outline 留白而显小 | 列表用 26dp、底栏用库内置 25dp，实机逐个核对 |
| 5 | Pager 换弹簧后，`selectedIndex` 与 `settledPage` 同步时序变化，可能闪高亮 | 沿用官方 `MainPagerState`（`selectedPage` + `isNavigating`），不用 `currentPage` 直读 |
| 6 | 宽屏 Rail 采样背景依赖 `localPositionOf` 反算（与当年弹层发糊同源风险） | 宽屏单独验收；若采样偏移，Rail 退 `backdrop=null`（不透明胶囊），功能优先 |
| 7 | `springAnimateToPage` 需要配套 `PagerDefaults.flingBehavior(snapAnimationSpec = PagerNavigationSpringSpec)`，否则滑动与点击两套手感 | 一并替换 |
| 8 | 1.2.1 的两处修复尚未提交，混进来会让 diff 难以审阅 | 先在本地提交 1.2.1，再开 1.3 分支式推进（都不推送） |
| 9 | 玻璃观感真机与模拟器不一致（历史结论：swiftshader 只会软糊） | 视觉验收以模拟器「结构/布局/颜色」为准，模糊质感标注需真机确认 |

## 7. 本轮明确不做

| 项 | 理由 |
| --- | --- |
| 搜索 / 筛选 | 现有页面没有该功能；文档亦要求不为参考第三方而新增业务 |
| FAB | 工具页底部已被 `PrimaryActionBar` 占据，再加悬浮按钮会打架；作品页无「新建」语义 |
| FloatingToolbar | 现有业务无「多选后批量操作」场景 |
| 底栏风格切换设置 | 你已定：单底栏，不向用户暴露 |
| 侧滑返回（`swipeDismiss`） | 官方默认关闭，且与现有预测性返回五档存在手势竞争 |
| Badge | 无未读/计数语义 |
| 参照第三方更新日志的「桌面 Dock / 搜索栏质感」 | 那是启动器专属，与 GIF 助手无关 |

## 8. 验收清单

1. 窄屏 × {浅色, 深色} × {静态, Monet} × {玻璃开, 玻璃关}：8 组，检查页面层级不塌陷。
2. 逐页：顶栏大标题→小标题折叠、band 渐进模糊、底栏不遮内容、滚动到底部最后一行完整可见。
3. 交互：底栏点击跟手、左右滑 tab、返回回第一个 tab、工具页生成/取消、作品页底部面板。
4. 宽屏（`wm size 2560x1600` + `wm density 240`）：Rail 出现、底栏消失、内容不拉伸、Rail 玻璃不偏移。
5. 业务回归：10 个工具的生成链路各跑一次（至少视频转 GIF / 图片合成 / 压缩 / 转视频）。
6. 崩溃回归：连续滚动 + 开关玻璃 + 进出工具页 20 次，观察 logcat 无 `prepareTreeImpl`。

---

## 9. 实施结果与方案偏差（2026-11-16 回填）

已按方案完成，实机（API 34 / swiftshader，1080×2400@420、2560×1600@240）逐页验证。
截图见 `_archive/screenshots/os4-gif/`。

### 与方案不一致的地方（都是实测推翻了纸面判断）

1. **`GlassNavigationRail` 不能放进 `Row`。** 方案里写的是「Row 里放 Rail + 内容」，
   实际它的根节点是 `BoxWithConstraints(fillMaxSize())`，自己决定面板位置 —— 放进 Row 会吃掉整行宽度，
   内容被挤成 0 宽，实机表现为「侧栏在、右侧一片空白」。改为**叠放在内容之上**，
   内容用 `padding(start = ExpandedWidth + Margin)` 让位。官方 `AppContent` 也是这个结构。
2. **`ui/AppIcon.kt` 没有单独建。** 图标规范只有「尺寸表 + LeadingIcon」两件事，
   放进 `ui/Widgets.kt` 的 `IconSizes` 与 `LeadingIcon` 更省事，不值得多一个文件。
3. **删除 `SegmentedControl` 已执行**（确认零调用）。
4. **工具页 `extraBottom = 20.dp`**：结果卡原本贴着主操作条，补一点呼吸。

### 实测确认的关键事实

- 顶栏玻璃胶囊**只在 `isContentScrolled` 为真时出现**（库的 `floating = isContentScrolled`）。
  第一次巡检没看到胶囊，是因为滑动起点落在滑块上被吃掉、页面其实没滚 —— 不是实现问题。
- 浅色下渐进模糊带清楚可见（内容被糊成软块）；深色下内容本身就是深色，糊了也接近纯色，
  看起来"没有带子"是正常的。
- 玻璃关掉后底栏仍是浮起胶囊 + 描边 + 阴影，不会塌回通栏白底条。
- 业务链路复测：推入两张 JPG → 图片合成 GIF → 生成 `images2gif_*.gif`（3 KB）→ 作品页收录。

### 仍未验证

- 真机上的模糊质感与底栏跟手感（模拟器是 swiftshader，只能判结构/布局/配色）。
- 12 个方向样本的视频转 GIF（缺素材，1.2 起就挂着）。
