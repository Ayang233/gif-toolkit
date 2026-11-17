# 横屏导航、Glass 效果与作品菜单专项复查（第 6 轮）

本轮三件事：横屏导航形态、一级导航的挂载条件、Glass 采样范围与作品菜单。

---

## 0. 基线与取证条件

| 项 | 值 |
| --- | --- |
| 上一轮基线 | `3a980f7c` |
| 本轮基线 | **`d45ca181`**（`origin/main`，2026-11-17，比上轮 +15 个提交） |
| 本地 miuix | 已切到 `d45ca181`；`build-logic/BuildConfig.kt` 的 composite build 补丁（两处 `workingDir = rootDir`）保留，两边都能编译 |
| 官方示例 APK | `Miuix-v1.0.9(1307)-debug.apk`（由 `d45ca181` 现场编译） |
| GIF 助手 APK | 同一基线上 `./gradlew :assembleDebug` |
| 设备 | AVD `android16`，1080×2400 / 420dpi，`-gpu host`；横屏可用宽约 **914dp** |

本轮基线里直接影响本轮的改动：

- `GlassNavigationRail` 重写（+168 行）：折叠态从"竖条"改成**顶部居中胶囊，只显示文字**；新增 `CollapsedTop` / `CollapsedHeight` / `RowProgress`
- `GlassTopAppBar`（+104）、`Scaffold` / `TopAppBar` 结构调整
- `BlurDefaults.MaxBlurRadius` **降到 100f**，以及一串模糊着色器优化（中心采样、两层栈、半精度权重）
- 新增 `GlassFloatingActionButton` / `GlassFloatingToolbar`、`GlassSegmentedTabRow` 支持横向拖动

**模拟器可信度**：结构、布局、尺寸、采样范围、交互行为可信；玻璃厚度 / 高光 / 边缘折射仍需真机复核。

---

## 1. 横屏导航形态：官方到底是什么

**这是本轮记录冲突的核心，先给结论。**

| 排查项 | 内容 |
| --- | --- |
| 你的观察 | 官方 APK 横屏时"底栏调整到顶部显示" |
| 上一轮我的记录 | GIF 助手横屏是左侧 Rail（那是**我们的**实现，不是官方） |
| 官方源码判定 | `utils/AdaptiveUtils.kt`：`shouldShowSplitPane()` = `width ≥ 840dp` 或（`≥600dp` 且 `高/宽 < 1.2`）→ 宽屏；`shouldExpandNavigationRail()` = **`width ≥ 1200dp`** 才展开 |
| 官方 APK 实测（914dp 横屏） | 顶部居中一枚**横向玻璃胶囊**：最左一个折叠按钮（圆角方块图标），右侧 5 个文字页签 `Home / Icon / Color / TextStyle / Settings`，当前项有指示器；内容**满宽**，没有左侧留白 |
| 组件真相 | 那不是"顶部导航栏"，而是 **`GlassNavigationRail` 的折叠形态**。源码注释（`GlassNavigationRail.kt:276`）："Expanded, the rail lists destinations as icon and label. **Collapsed, it shows labels only**"，并带 `CollapsedTop = (52-44)/2 = 4dp`、`CollapsedHeight = 44dp` |
| 结论 | 官方**没有**"底栏→顶栏"的组件替换。窄屏竖屏是 `GlassNavigationBar`（底部胶囊）；宽屏是 `GlassNavigationRail`：宽度不到 1200dp 时折叠成顶部胶囊，达到 1200dp 才展开成左侧面板 |

**GIF 助手当前实现**：`App.kt` 里 `rememberGlassNavigationRailState(initialExpanded = true)` + `showToggle = false` —— 强制常驻展开，所以在 914dp 手机上画出一个 250dp 的左侧面板，比官方"重"得多。这是本轮要改的第一处。

### 1.1 官方在二级页仍然显示折叠胶囊（我们不采用）

实测：官方横屏 → 设置 → About（压栈的二级页），**顶部胶囊仍在**（`off_landscape_about.png`）；而官方**竖屏**二级页没有底栏（`off_portrait_about.png`，上轮也已确认）。

即官方本身在宽屏下并不按栈深隐藏导航。**但按你的要求**，GIF 助手的二级页在任何方向都不显示一级导航 —— 这一点我们**故意偏离官方**，理由记录在此。

---

## 2. 横屏二级页重新出现一级导航（确认，已修）

| 项 | 内容 |
| --- | --- |
| 现象 | 竖屏进二级页（无导航）→ 旋转横屏 → 左侧 Rail 重新出现，内容还被让出 250dp（`gif_landscape_secondary.png`） |
| 原因 | 上轮只给**底栏**加了 `stack.size <= 1`；`Rail` 仍是裸的 `if (wide) { GlassNavigationRail(...) }`，没有读页面栈。旋转后 `wide` 变真 → Rail 重新组合 |
| 附带 | `railInset` 也只看 `wide`，所以二级页在横屏继续让出侧栏宽度，右侧内容变窄 |
| 修法 | 导航挂载统一由一个 `showPrimaryNav = stack.size <= 1` 控制，Rail / 底栏共用；`railInset` 由 Rail 的实际可见性与展开进度决定 |

---

## 3. 底栏收缩动画：本轮实测无缩放

| 项 | 内容 |
| --- | --- |
| 当前代码 | 底栏已是条件挂载（`if (!wide && stack.size <= 1)`），没有 `visible` / `AnimatedVisibility` / `animateContentSize` |
| 官方源码 | `GlassNavigationBar.visible = false` 才会缩放（`showScale` → `NAV_HIDE_SCALE`，`GlassNavigationBar.kt:256`），我们已不再传 |
| 本轮实测 | 录屏抽帧（20fps）逐帧看：点击后**没有任何缩小过程**，底栏在一帧内消失，随后页面才滑入（`stack_push.png` 四帧拼接） |
| 与官方仍有的差别 | 官方的底栏画在页面内部，随页面一起滑走；我们的底栏在 `NavDisplay` 外面，只能"立即消失"。要"跟着页面滑"就必须把它放进录制层内部 → 采样到自己 → libhwui 栈溢出（AGENTS.md 第 1 条）。因此保留"立即消失"，并**不加淡入淡出**（你要求不引入新的自定义过渡） |
| 结论 | 缩放动画已不存在；若仍看到"收起"，最可能是**旋转横屏时 Rail 突然出现/消失**（第 2 条），本轮修掉后会一起消失 |

---

## 4. Glass：横屏 Rail 采样范围（确认，已修）

| 项 | 内容 |
| --- | --- |
| 现象 | 横屏 Rail 左侧约 1/3 是一块**平色**，与右侧的模糊区之间有一条竖直硬边（`gif_landscape_primary.png`）；在二级页上又是另一种错法（`gif_landscape_secondary.png`） |
| 当前实现 | `App.kt`：`Box(Modifier.fillMaxSize().padding(start = railInset).layerBackdrop(backdrop)) { NavDisplay }`，Rail 是它的**兄弟**叠在上面 |
| 官方实现 | `AppContent.kt:300-350`：内容列 `Box(weight(1f).fillMaxHeight())` → 里面套 `Box(Modifier.fillMaxSize().layerBackdrop(sidebarBackdrop))` 录**整列满宽**内容 → 玻璃 Rail 作为它的兄弟叠上去。**录制层不被侧栏挤窄**；页面内容自己用 `appPageHorizontalInsets` 让位 |
| 原因 | 我们把 `railInset` 加在了**录制层和 NavDisplay** 上，录制层从 `x = railInset` 才开始录，左侧那一条没有像素 → 采样到空白，玻璃只剩材质 |
| 修法 | 录制层恢复到满宽（`layerBackdrop` 挂在 `fillMaxSize()` 的 Box 上，不再带 `padding(start)`）；内容让位改由页面内边距承担，跟随 Rail 的展开进度 |

其余 Glass 位置（顶栏 band / progressive、卡片、页面内下拉、Dialog、二级页玻璃控件）本轮按同一份清单逐个核对，结论见 §7。

---

## 5. 作品菜单：确认没有 Glass（已修）

| 项 | 内容 |
| --- | --- |
| 当前实现 | `LibraryScreen.kt:113-140` → `FileActionSheet`（`:180`）→ **`WindowBottomSheet`**（系统窗口）。参数表里**没有 `backdrop`**，所以完全没有玻璃：实拍是一块纯白不透明面板 + 灰色遮罩（`gif_library_sheet.png`） |
| 为什么当初这样写 | `GlassBottomSheet` 依赖 `LocalDialogStates`，由 `Scaffold` 提供并负责渲染；这个面板挂在 `Os4ListPage` **外面**（兄弟节点），拿不到那个 local，实测点了没反应 |
| 官方实现 | `GlassBottomSheet(backdrop, title, ...)`（`GlassBottomSheet.kt:50`），示例 `GlassBottomSheetDemo.kt` 传的是页面的 `backdrop`；官方实拍是**半透明磨砂玻璃**，能透出后面页面的模糊内容，带拖拽把手、标题、行分隔线，内容保持清晰（`off_glass_sheet.png`） |
| 宿主要求 | `LocalDialogStates` 由 `miuix-ui` 的 `Scaffold` 在根节点 `CompositionLocalProvider` 提供（`Scaffold.kt:108-118`），并在 Scaffold 内部绘制。所以只要把弹层**组合在 Scaffold 作用域内**就能生效 |
| 修法 | 给 `Os4ListPage` 加 `pageOverlay` 槽位：放在 Scaffold 内容里、**`ContentBackdropBox` 之外**（兄弟节点）。既拿到 Scaffold 宿主，又不在录制层里 → 不会采样到自己；`backdrop` 用 `LocalPageBackdrop` |
| 业务保持 | 三个菜单项（保存到相册 / 分享 / 删除）、删除的红色标题、图标、回调逻辑原样保留 |

---

## 6. 修改方案（本轮执行）

| # | 文件 | 改动 |
| --- | --- | --- |
| 1 | `App.kt` | 一级导航统一由 `stack.size <= 1` 控制（Rail 与底栏共用）；`rememberGlassNavigationRailState(initialExpanded = 宽度 ≥ 1200dp)`，`showToggle` 交回库默认（true）；录制层恢复满宽，内容让位改走 `LocalRailInset`（跟随展开进度） |
| 2 | `ui/Adaptive.kt` | 新增 `shouldExpandNavigationRail()`（阈值 1200dp，与官方同名函数一致）和 `LocalRailInset` |
| 3 | `ui/PageChrome.kt` | `pageContentPadding` 加上 `railInset` 让位 |
| 4 | `ui/PageChrome.kt` | `Os4ListPage` 新增 `pageOverlay` 槽位（Scaffold 内、录制层外） |
| 5 | `screens/LibraryScreen.kt` | 改用 `GlassBottomSheet` + `LocalPageBackdrop`，走 `pageOverlay`；删除 `WindowBottomSheet` 路径 |

风险与对应措施：

- Rail 录满宽后，iOS/桌面端不需要 —— 只影响 Android 手机/平板。
- `LocalRailInset` 每帧随 `progress` 变化会让页面内边距重组：官方也这么做（`AppContent.kt:219-229`），量级可接受；只在 wide 且有 Rail 时生效。
- `GlassBottomSheet` 换宿主属于结构性改动：必须验证不闪退、能关闭（拖拽 / 外部点击 / 返回），否则退回并记录。

---

## 7. 验收

- 方向：竖屏一级 / 横屏一级（914dp 折叠胶囊）/ 宽屏一级（≥1200dp 展开面板）/ 竖屏二级 / 横屏二级 / 竖→横旋转 / 横→竖旋转 / 反复切换
- 导航：位置形态、tab 状态、返回、二级页始终无一级导航、无收缩残影
- Glass：Rail 胶囊与面板采样、顶栏、卡片、页面内下拉、作品菜单、明暗与玻璃开关
- 作品菜单：打开 / 三项操作 / 点外部关闭 / 返回关闭 / 贴边位置 / 横竖屏
- 业务回归：导入 → 生成 → 结果 → 保存 / 分享

---

## 8. 执行结果（本轮实测）

### 8.1 改动清单

| 文件 | 改动 |
| --- | --- |
| `App.kt` | 一级导航统一由 `showPrimaryNav = stack.size <= 1 && !modalOverlayOpen` 控制（底栏与 Rail 共用）；`railState` 初值改为 `shouldExpandNavigationRail()`（≥1200dp）并用 `LaunchedEffect` 跟随后续尺寸变化；`showToggle` 交回库默认；录制层去掉 `padding(start = railInset)`，恢复满宽；内容让位改由 `LocalRailInset`（= `(ExpandedWidth + Margin) * railState.progress`）下发 |
| `ui/Adaptive.kt` | 新增 `shouldExpandNavigationRail()`（1200dp，与官方同名函数一致）与 `LocalRailInset` |
| `ui/PageChrome.kt` | `pageContentPadding` 叠加 `LocalRailInset`；`Os4ListPage` 新增 `pageOverlay: @Composable (LayerBackdrop?) -> Unit` 槽位（Scaffold 内、录制层外，参数即页面录制层） |
| `ui/Widgets.kt` | `PrimaryActionBar` 左侧让出 `LocalRailInset`（展开 Rail 时不压住按钮） |
| `screens/LibraryScreen.kt` | 作品操作面板改用官方 `GlassBottomSheet`，挂在 `pageOverlay`；删除 `WindowBottomSheet` 路径；打开期间置 `app.modalOverlayOpen` |
| `AppState.kt` | 新增运行期标记 `modalOverlayOpen`（模态弹层打开时让出一层导航） |

### 8.2 验收结果

| 场景 | 结果 | 证据 |
| --- | --- | --- |
| 竖屏一级 | 底部玻璃胶囊，与之前一致 | `final_portrait.png` |
| 横屏一级（914dp） | **官方折叠形态：顶部居中胶囊**（折叠按钮 + 三个文字页签），内容满宽，无左侧留白 | `v2_landscape_primary.png`、`final_landscape.png` |
| 宽屏一级（模拟 1600dp） | **展开式左侧面板**（图标 + 文字），内容右移让位 | `v10_wide_expanded.png` |
| 横屏一级的 Rail 玻璃 | 满宽录制后不再有平色块与竖直硬边 | `v2_landscape_primary.png` |
| 竖屏二级 | 无一级导航 | `final_picker.png` 前后 |
| 横屏二级（直接进入） | 无一级导航，内容满宽 | `v2_landscape_secondary.png` |
| 竖屏二级 → 旋转横屏 | **不再重新出现 Rail/底栏**，内容不让位 | `v2_rotate_secondary.png` |
| 宽屏二级 | 展开面板消失，内容回到满宽 | `v11_wide_secondary.png` |
| 进出二级页动画 | 逐帧核对：无缩放/淡出，底栏一帧内退场 | `stack_push.png`（四帧拼接） |
| 作品操作面板 | 官方 `GlassBottomSheet`：拖拽把手、标题、行、遮罩；**底栏不再压在面板上** | `v4_sheet.png`、`v5_sheet_over_content.png` |
| 面板三项操作 / 关闭 | 保存、分享、删除回调保持原样；点外部与返回都能关闭 | 手动验证 |

### 8.3 未完全解决：作品面板的玻璃"观感"

- 组件与宿主都已换成官方：`GlassBottomSheet` 在 Scaffold 作用域内组合，走的是玻璃分支
  （临时日志确认 `backdrop=LayerBackdrop`、`shader=true`），表面像素从旧系统窗口版的 **255**
  变成 **237**，说明材质层生效了。
- 但**看不到背景内容被模糊**：面板表面是均匀的浅色，透不出后面的列表行。
  对比官方示例的 `GlassBottomSheet`（`off_glass_sheet.png`），能明显看到页面内容透过玻璃。
- 判断：`glass` 的采样区域很可能与面板实际位置存在偏移（对话框宿主与页面录制层属于不同子树），
  采到的是页面空白区而不是面板正下方的行。这与 AGENTS.md 里"弹层跨容器反算"的历史坑同源。
- 该现象**只在模拟器上确认**；真机是否相同需要复核。下一步可试的方向：
  把页面录制层上移到 Scaffold 内容根（顶栏继续用 `contentBackdrop`，避免采样自身），
  让录制层与对话框宿主同源，再比对采样偏移。

### 8.4 与官方的已知差异（有意保留）

| 项 | 官方 | GIF 助手 | 原因 |
| --- | --- | --- | --- |
| 二级页的一级导航 | 宽屏下仍然显示折叠胶囊 | 任何方向都不显示 | 按本轮要求：一级导航必须与页面栈绑定 |
| 底栏退场 | 随页面一起滑走（底栏在页面内部） | 一帧内消失 | 底栏在 `NavDisplay` 外，进录制层会采样自身导致闪退 |
