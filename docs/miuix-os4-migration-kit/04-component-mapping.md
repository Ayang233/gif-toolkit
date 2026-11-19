# 04 · 原生 UI → MIUIX 组件映射

> 用法：先按 §2 的表格找到"旧控件"，再看 §3 的处置分类决定怎么做。
> **不是所有控件都要换**；换错的代价比不换大（丢语义、丢无障碍、丢业务状态）。

---

## 1. 四种处置

| 处置 | 含义 | 判据 | 例子 |
|---|---|---|---|
| **A 直接替换** | 语义一一对应，参数几乎全等 | 官方组件能覆盖原有全部交互 | `Text` / `Icon` / `Card` / `Button` |
| **B 业务适配** | 组件能换，但要接业务状态/配色/尺寸 | 交互一致、只是"长相"或"默认值"不同 | `TopAppBar`（大标题折叠要注意宽屏）、`Switch`（受控状态） |
| **C 重新设计** | 没有等价组件，要按 OS4 交互重做 | 官方只有近似物 | 手机+平板的导航形态切换、分段控件、玻璃下拉 |
| **D 保留自定义** | 官方组件语义不匹配，硬换会丢功能 | 有独特交互或数据可视化 | 进度填充按钮、预览画布、色板 |

规则：**先 A → 再 B → 不得已 C → 尽量少 D**。D 类必须满足两个条件：
继承 `MiuixTheme`、不破坏 Glass 层级（见 [08](08-custom-component-migration.md)）。

---

## 2. 主映射表

> 「官方组件」列的包名省略前缀 `top.yukonga.miuix.kmp.`。
> 「GIF 助手」列写的是它实际用了什么（🏠 = 自建封装）。

| # | 原有功能 | 官方组件 | 处置 | GIF 助手 | 迁移检查 |
|---|---|---|---|---|---|
| 1 | 页面容器 `Scaffold` | `basic.Scaffold` | A | `Os4ListPage` 🏠 封装 | `containerColor` 用页面底色；`contentWindowInsets` 与宿主区分 |
| 2 | 顶部标题栏 | `basic.TopAppBar` / `glass.GlassTopAppBar` | B | `PageTopBar` 🏠 二选一 | 大标题是否可展开（宽屏要关）；返回键玻璃/非玻璃两档 |
| 3 | 底部导航 | `basic.NavigationBar` / `glass.GlassNavigationBar` | B | `GlassNavigationBar` | 高度取 `Defaults.Height`；`visible=false` 禁用 |
| 4 | 侧边导航 / Drawer | `basic.NavigationRail` / `glass.GlassNavigationRail` | C | `GlassNavigationRail` | 不能进 `Row`；折叠态是顶部胶囊 |
| 5 | 主按钮 | `basic.Button` | A/B | `ProgressButton` 🏠（D） | 禁用态同档配对 |
| 6 | 图标按钮 | `basic.IconButton` / `glass.GlassIconButton` | B | 两者都用（玻璃档/非玻璃档） | 玻璃档必须包 `GlassIconButton` 才能拿材质 |
| 7 | 卡片 | `basic.Card` | A | `GroupCard` / `GlassCard` 🏠 | 用 `CardDefaults.CornerRadius`；卡片内不放分割线 |
| 8 | 表面/背景块 | `basic.Surface` | A | `Surface`（ProgressButton 内） | — |
| 9 | 设置开关 | `preference.SwitchPreference` | A | `SwitchRow` 🏠 封装 | 受控状态；点击整行有反馈、点文字区无反馈（库行为） |
| 10 | 设置下拉 | `glass.GlassDropdownPreference` | C | `DropdownRow` 🏠 封装 | 必须传 backdrop；`renderInRootScaffold` 默认 true |
| 11 | 设置滑杆 | `preference.SliderPreference` | A | `SliderRow` 🏠 封装 | **`steps` 语义 = 中间档数**（见 §3.4） |
| 12 | 单选 | `preference.RadioButtonPreference` | A | 未使用（色板自绘） | 库自带触觉反馈 |
| 13 | 多选 | `preference.CheckboxPreference` | A | 未使用 | 库自带触觉反馈 |
| 14 | 设置跳转行 | `preference.ArrowPreference` | A | 多处 | 描述放 `summary`，不要自己拼箭头 |
| 15 | 分组标题 | `basic.SmallTitle` | A | `GroupHeader` 🏠 包装 | 不要自写 `Text`（缩进/字号不同） |
| 16 | 弹窗 | `glass.GlassDialog` / `basic.WindowDialog` | B | `GlassDialog` | 页面级用 Glass，系统级用 `WindowDialog` |
| 17 | 底部面板 | `glass.GlassBottomSheet` | B | `GlassBottomSheet` | 宿主、内边距双计、导航条留白 |
| 18 | 下拉菜单（行内） | `glass.GlassDropdownPopup` / `GlassPopup` | C | 由 `GlassDropdownPreference` 内部使用 | 锚点坐标同作用域 |
| 19 | 输入框 | `basic.TextField` / `glass.GlassTextField` | A | `TextField` | 焦点环/光标对比度 |
| 20 | 进度（不确定态） | `basic.InfiniteProgressIndicator` | A | 按钮内的 16dp 指示器 | 尺寸/描边显式传 |
| 21 | 进度（确定态） | `basic.LinearProgressIndicator` / `CircularProgressIndicator` | A | 未用（进度画在按钮里） | 默认 6dp 高、圆头 |
| 22 | 提示条 / Snackbar | `basic.Snackbar`（若有）/ 自绘 | B | 未用 | 不要用 Material 的 |
| 23 | 标签页 | `basic.TabRow` / `glass.GlassTabRow` | C | 未用 | 见 §3.5：页签 ≠ 分段控件 |
| 24 | 分段控件 | `glass.GlassSegmentedTabRow` | C | 未使用（离散选项走下拉） | `backdrop` 非空 |
| 25 | 悬浮按钮 | `glass.GlassFloatingActionButton` | B | 未用 | 单动作才用 |
| 26 | 悬浮工具条 | `glass.GlassFloatingToolbar` | B | 未用 | 多动作才用 |
| 27 | 徽标 | `basic.Badge` | A | 未用 | — |
| 28 | 分割线 | `basic.HorizontalDivider` | A | 未用（卡片内不放） | 见 [03](03-os4-theme-system.md) §8 |
| 29 | 图标 | `icon.MiuixIcons.*` / `icon.glass.MiuixIcons.Glass.*` | B | Glass 族 | 见 §4 |
| 30 | 主题 | `theme.MiuixTheme` + `ThemeController` | B | `AnimatedMiuixTheme` 🏠 | 见 [03](03-os4-theme-system.md) §1 |

> 这张表只覆盖常见项。**必须以 `miuix/docs/components/` 的当前组件列表为准继续扩展**
> （`basic/` 40 篇、`extended/` 17 篇、`glass/` 16 篇）。

---

## 3. 逐个组件的迁移要点

### 3.1 页面容器

```kotlin
// 页面级
Scaffold(
    containerColor = pageBaseColor(),        // 必须与录制层垫色一致
    topBar = { … },
    bottomBar = { … },
) { padding -> … }

// 弹层宿主级（最外层，只当宿主不管布局）
Scaffold(
    containerColor = Color.Transparent,
    contentWindowInsets = WindowInsets(0),
) { … }
```

📌 `Scaffold` 的 `popupHost` 默认 `MiuixPopupHost`（`basic/Scaffold.kt:88`），
它会挂 `LocalRootDialogStates`（`:104`/`:115`）。这是弹层的宿主机制。

要点：

- **两层 Scaffold 是正常的**：一层宿主（透明、无 insets），一层页面（有底色、有顶栏）。
- 页面层的 `bottomBar` 如果是"浮层"（如玻璃底栏），它**不占** Scaffold 的槽位，
  内容底部留白要自己算（见 [05](05-navigation-and-adaptive.md) §4.3）。

### 3.2 顶栏（最容易做错的一个）

| 场景 | 用哪个 | 关键参数 |
|---|---|---|
| 普通页面，要滚动折叠 | `TopAppBar` | `expandable = true`、`largeTitle`、`scrollBehavior` |
| 宽屏 / 平板 | `TopAppBar` | `expandable = false`（官方 `AdaptiveTopAppBar` 同款判断） |
| 要玻璃 | `GlassTopAppBar` | `backdrop`、`contentModifier`、`bandBrush` |
| 大标题页面（About） | `GlassTopAppBar` / `TopAppBar` | 自己算 `titleAlpha` 跟随滚动进度 |

要点：

- 玻璃档 **只挪前景用 `contentModifier`**，非玻璃档没有这个参数，只能挂 `modifier`
  （见 [06](06-glass-architecture.md) R6）。
- 返回键：玻璃档 `GlassIconButton` + `MiuixIcons.Glass.ChevronBackward`（28dp）；
  非玻璃档 `IconButton` + `MiuixIcons.Back`（24dp）。官方 `component/BackNavigationIcon.kt` 就是这么分。
- 不要给顶栏叠 `padding` —— 大标题折叠动画按顶栏自身高度计算，叠了会错位。

### 3.3 导航条

见 [05](05-navigation-and-adaptive.md) §3–§5（有完整阈值与让位公式）。

### 3.4 设置行（Preference 族）

📌 行高与内边距由 `BasicComponentDefaults` 决定（最小行高 56dp、内边距 16dp、图标前间距 8dp）。
**不要自己在行内叠 padding 来"微调间距"** —— GIF 助手曾给 `LeadingIcon` 补 `padding(end = 4.dp)`，
结果这一行和别的行不一致（`docs/os4-reuse-audit.md` §3.1）。

| 组件 | 签名要点 | 坑 |
|---|---|---|
| `SwitchPreference` | `checked`、`onCheckedChange`、`title`、`summary`、`startAction`、`endActions` | `summary` 可空；没有增量信息就不要传 |
| `SliderPreference` | `value`、`onValueChange`、`title`、`valueText`、`valueRange`、`steps` | **`steps` = 中间档数**：`steps = 2` 才是四档（0/1/2/3 位置）；`steps = 3` 会得到 5 档（见 C31） |
| `ArrowPreference` | `title`、`summary`、`startAction`、`onClick` | 不要自拼箭头（`ChevronForward` + `onSurfaceVariantActions`） |
| `RadioButtonPreference` | `selected`、`onClick`、`title`、`summary` | 库自带 `ToggleOn/ToggleOff` 触觉 |
| `CheckboxPreference` | `checked`、`onCheckedChange` | 同上 |
| `DropdownPreference`（普通） | `items`、`selectedIndex`、`onSelectedIndexChange` | 弹白底卡片，不是 OS4；要做玻璃用 `GlassDropdownPreference` |

### 3.5 页签 vs 分段控件（GIF 助手真实文档漂移）

三个页签组件都是**可横向滚动的页签**，不是"一整条凹槽 + 一个滑块"：

| 组件 | 形态 |
|---|---|
| `TabRow` | 可横滑页签 |
| `TabRowWithContour` | 可横滑页签 + 额外 5dp 轮廓 padding（看起来像一排小按钮） |
| `GlassTabRow` | 可横滑玻璃页签 |
| **`GlassSegmentedTabRow`** | **真正的分段控件**（凹槽 + 滑块 + 拖拽） |

GIF 助手的 `AGENTS.md` 曾写"分段控件用自绘的 `SegmentedControl`"，
但源码里从来没有这个组件（全项目 grep 零命中）—— 属于文档漂移，
后来上游补了 `GlassSegmentedTabRow` 才解决（`docs/os4-round10-audit.md` §9）。
**教训：写文档时不要写"我们有个组件叫 X"，要去 grep。**

### 3.6 弹层

| 场景 | 组件 | 宿主 | 采样源 |
|---|---|---|---|
| 页面内确认框 | `GlassDialog` | 页面 Scaffold（内容槽内、录制层外） | 一级页整屏层 |
| 操作面板 | `GlassBottomSheet` | 同上 | 同上 |
| 设置项下拉 | `GlassDropdownPreference` | 最外层 Scaffold（`renderInRootScaffold = true`） | 一级页整屏层 |
| 系统级窗口（如"关于"里的强提示） | `WindowDialog` | 独立窗口 | 无 |

规则（来自 [06](06-glass-architecture.md) §1.3）：**弹层一律用"录在分页容器外面"的那层**。
一级页分页容器内上报槽位坐标，与弹层的真实坐标差一整页宽（实测 `offset.x = -1080`）。

### 3.7 输入与进度

- `TextField`：`state: TextFieldState`（新 API）或 `value/onValueChange`（旧 API）都支持；
  字幕/多行用 `lineLimits`。玻璃场景可用 `GlassTextField`，但普通表单建议保持 `TextField`。
- `InfiniteProgressIndicator`：`size`、`strokeWidth`、`orbitingDotSize` 都可调；
  放在按钮内时取 16dp / 2dp / 1.5dp（GIF 助手实测好看的比例）。
- `LinearProgressIndicator`：默认 6dp 高、圆头；`progress = null` 是不定态。

---

## 4. 图标迁移

| 族 | 包 | 特点 | 用在哪 |
|---|---|---|---|
| **Glass** | `icon.glass.MiuixIcons.Glass.*` | 28dp 画布、定色档、512 个 | 主入口、底栏、核心卡片 |
| **Glass.Light** | `MiuixIcons.Glass.Light.*` | 同族细线档 | 设置、关于、作品等次级入口 |
| **extended** | `icon.extended.MiuixIcons.*` | 24dp 画布、156 个 | 仅非玻璃档的顶栏返回键等 |

规则：

1. **不要混族**：同一屏里 Glass 与 extended 的线宽差一档，一眼能看出来。
   例外是官方自己的做法：玻璃档用 `Glass.ChevronBackward`，非玻璃档用 extended `Back`。
2. **Glass 族是 28dp 画布**（`buildHyperOSIcon` 的 `defaultWidth = 28.dp`），
   塞进 24dp 槽会明显偏小。GIF 助手的换算：列表 26dp、动作 28dp、行内 19dp、空态 44dp、媒体 28dp。
3. **尺寸只留一个出处**（GIF 助手是 `IconSizes` 对象），不要在调用点写 `28.dp`。
4. **改名要跟**：`Glass.Folder` → `FolderOutline`、`Glass.Settings` → `SettingsOutline`，
   共 15 组（清单见 [17](17-source-reference-index.md) §4.2）。
5. **自绘 drawable → ImageVector 的替换策略**：
   - 先判断它是"语义图标"还是"品牌/插画"。语义图标换 Glass 族；品牌 logo 保留（永远不换）。
   - 找不到对应的 Glass 图标时：优先用最接近的语义图标，**不要**自己拼路径
     （线宽、圆角、光学尺寸都对不上）。
   - 确实没有的，保留原 `painterResource`，但尺寸要走同一个 `IconSizes`，
     并在代码里注明"无 Glass 对应物"。
   - `material-icons-extended` 必须删（体积大 + 线宽冲突）。

---

## 5. 覆盖度怎么算

迁移完成后，按类统计"已迁移 / 总数"，并**记录分母的排除项与理由**：

| 类别 | 分母 | 排除理由示例 |
|---|---|---|
| 页面 | 全部导航目的地 | 系统级页面（权限申请、文件选择）不计 |
| 容器 | `Scaffold` / 自建根布局 | — |
| 顶栏 | 有顶栏的页面数 | 全屏预览页无顶栏 |
| 导航 | 一级导航 + 二级返回 | — |
| 按钮 | 全部可点击按钮 | 系统分享面板内的不计 |
| 卡片 | 全部卡片/分组 | 自绘数据卡（图表）保留 |
| 设置行 | 全部 Preference | — |
| 弹层 | 全部 Dialog/Sheet/Dropdown | 系统窗口不计 |
| 输入 | 全部 TextField | — |
| 图标 | 全部图标引用 | 品牌 logo（应用图标）不计 |

验收时**"未验证"不能算"已完成"**（见 [13](13-project-adaptation-workflow.md) §7、[11](11-testing-and-acceptance.md)）。

---

## 6. GIF 助手的实际覆盖（1.10）

| 类别 | 实际 |
|---|---|
| 页面 | 8 个目的地：`Root`（3 个 tab）+ `Tool` + `Theme` + `About` + `Licenses` + `License`(+`ButtonColors` 仅 debug) |
| 容器 | 全部走 `Os4ListPage`（含 About，1.10 合并） |
| 顶栏 | 全部 `PageTopBar`（玻璃/非玻璃二选一） |
| 导航 | 竖屏 `GlassNavigationBar`、宽屏 `GlassNavigationRail`、二级页无一级导航（刻意偏离官方） |
| 卡片 | `GroupCard`（普通）与 `GlassCard`（About 页） |
| 弹层 | `GlassDialog`（删除确认）、`GlassBottomSheet`（作品操作）、`GlassDropdownPreference`（设置下拉） |
| 输入 | `TextField`（字幕输入） |
| 图标 | 全部 Glass 族（底栏 3 个 + 工具 10 个 + 行首若干） |
| 保留自定义 | `ProgressButton`（移植）、`PreviewStage`、`PrimaryActionBar`、色板、`BgEffectBackground`（移植） |
| 不引入 | material3（全项目零 import） |

---

## 7. 迁移顺序建议

1. 容器与顶栏（先把骨架统一，否则每页都要改一遍）。
2. 导航形态（涉及 Insets 与让位，见 [05](05-navigation-and-adaptive.md)）。
3. 卡片与设置行（量最大、风险最低）。
4. 弹层（涉及宿主与采样源，要配合 [06](06-glass-architecture.md)）。
5. 输入/进度/滑块（细节多，最后做）。
6. 图标（可以随时做，但建议统一在一轮内换完，避免混族）。

---

## 8. 检查清单

- [ ] 每个页面都用了统一容器（不再有第二种骨架）
- [ ] 顶栏在宽屏下关掉了大标题（`expandable = false`）
- [ ] 所有弹层都挂在正确的 Scaffold 宿主上
- [ ] 卡片内没有 `HorizontalDivider`
- [ ] 图标全部 Glass 族（除了官方也允许的返回键例外）
- [ ] 没有引入 `androidx.compose.material3`
- [ ] 分母已记录，排除项有理由
- [ ] 每个替换都能在 `miuix/docs/components/` 找到对应文档
