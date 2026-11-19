# 19 · 虚拟项目推演：一个全新 Jetpack Compose 项目从 L0 到 L6

> 本篇是知识库的**自检**。假设现在交给代码助手一个完全没有 MIUIX 的项目，
> 按本知识库逐步推演，检查是否缺步骤。**推演中发现的 5 个缺漏已在本轮补进手册**
> （补在哪里见每节的"缺漏 → 已补"）。

---

## 0. 虚拟项目定义

| 项 | 值 |
|---|---|
| 名称 | NoteNest（云笔记，纯本地单机示例） |
| UI | **Jetpack Compose**（不是 CMP），**Material3** |
| 导航 | `androidx.navigation:navigation-compose`，5 个 destination |
| 页面 | 笔记列表 / 笔记详情 / 编辑器 / 搜索 / 设置 / 关于（共 6） |
| 弹层 | 删除确认 Dialog、排序 BottomSheet、导出菜单 DropdownMenu |
| 状态 | `ViewModel` + `StateFlow` |
| 主题 | `MaterialTheme` + `dynamicDarkColorScheme`（Android 12+），支持系统深浅色 |
| Kotlin / AGP / Compose | 2.1.0 / 8.7.0 / BOM 2025.01.00 |
| compileSdk / minSdk / targetSdk | 35 / **26** / 35 |
| 模块 | 单模块 `:app` |
| 特殊 | 有 1 个自绘"字数进度环"、1 个自绘标签色板 |

目标：先做到 **L3（OS4 全项目）**，再评估是否做 Glass。

---

## 1. 阶段 0 · 项目扫描

**我查的文档**：[01](01-project-assessment.md) §1 的 9 类 + §5 的 grep 清单。

执行结果（节选）：

| 类别 | 结果 |
|---|---|
| 架构 | Kotlin 2.1.0 / AGP 8.7.0 / Compose BOM / 单模块 |
| 依赖 | material3、navigation-compose、lifecycle-viewmodel-compose、coil、material-icons-extended |
| 页面 | 6 个 destination（全部在 `ui/nav/AppNavHost.kt`） |
| 组件 | `Scaffold` 6、`TopAppBar` 5、`NavigationBar` 1、`Card` 9、`Button` 12、`TextField` 4、`AlertDialog` 3、`ModalBottomSheet` 1、`DropdownMenu` 1 |
| 导航 | androidx.navigation；无预测性返回处理；栈底返回直接 finish |
| 主题 | `MaterialTheme` + `dynamicDarkColorScheme`；颜色散写在 7 个文件（`Color(0x…)` 共 23 处） |
| 自定义 | 字数进度环（`Canvas`）、标签色板（`Row` + `Box.clickable`） |
| 兼容性 | minSdk 26、compileSdk 35、Kotlin 2.1.0 |
| 构建 | `assembleDebug` 通过 |

输出 `docs/adaptation/00-baseline.md`（用 [14](14-new-project-audit-template.md)）。

**这一步是否够用？** 够。grep 清单覆盖了 11 类组件，页面分母清晰。

---

## 2. 阶段 1 · 可行性判断

**我查的文档**：[01](01-project-assessment.md) §2 决策树、§4 前置条件；[02](02-version-and-dependencies.md) §1。

逐条：

| 检查 | 结果 | 结论 |
|---|---|---|
| Kotlin | ✅ 2.1.0 | 通过 |
| Compose（Jetpack） | ✅ | 通过（MIUIX 的 Android 变体等价可用） |
| minSdk ≥ 24 | ✅ 26 | 通过 |
| minSdk ≥ 33（Glass） | ❌ 26 | **Glass 暂不可用** → 目标先定 L3 |
| compileSdk ≥ 37.2 | ❌ 35 | **阻断**：AGP 配置期会失败，必须先升 |
| Kotlin ≥ 2.4.21 | ❌ 2.1.0 | **阻断**：版本低于 MIUIX |
| 无 material3 | ❌ 有 | 需退场（不是阻断，但要排期） |
| 导航可共存 | ✅ | androidx.navigation 可保留 |
| 构建可跑 | ✅ | 通过 |

**结论**：`需先完成 compileSdk 37.2 + Kotlin 2.4.21 升级、并清理 material3，再接入`。

**发现了什么缺漏？**

- ❗Kotlin 2.0+ 的 Compose 需要 `org.jetbrains.kotlin.plugin.compose` 插件。手册原来的"依赖冲突"表没写。
  → **已补**：[02](02-version-and-dependencies.md) §7 表格新增一行。

> 若结论是"不能接入"（例如 Flutter），按 [16](16-new-project-code-assistant-prompt.md) 末尾的
> "如果项目不适合迁移"处理，不要强行开工。

---

## 3. 阶段 2 · 基础接入（→ L1）

**我查的文档**：[02](02-version-and-dependencies.md) §3 决策表、§4.3 composite build；[03](03-os4-theme-system.md) §1。

动作：

1. 加依赖：`miuix-ui`、`miuix-preference`、`miuix-icons`（不做 Glass 先不加 `-glass`）。
2. `AppTheme` 改成 `MiuixTheme(controller = …)`（不需要主题动画，所以不用 `colors=` 重载）。
3. 建三个取色函数（[03](03-os4-theme-system.md) §3.5）：`page()` / `card()` / `stage()`。
4. 把 23 处 `Color(0x…)` 按 §2.1 的映射表逐个替换。
5. 排版换成 `MiuixTheme.textStyles.*`。
6. 设置页的 `Switch`/`Slider` 换 `SwitchPreference`/`SliderPreference`。

**卡点**：material3 还在，`Scaffold`/`TopAppBar` 同名冲突。

**发现了什么缺漏？**

- ❗手册说"不要引入 material3"，但**没有说一个已有的 material3 项目怎么退场**（共存规则、
  import 别名、清理顺序、隐形残留）。
  → **已补**：[03](03-os4-theme-system.md) §11「Material3 退场策略」。
- ❗`enableEdgeToEdge` / `themes.xml` / 状态栏图标反色 / `windowSoftInputMode` 这几行不属于任何组件，
  但漏了就会有白边、图标看不见、输入法遮表单。手册原来只在 GIF 基线里提过，**没有作为步骤写出来**。
  → **已补**：[05](05-navigation-and-adaptive.md) §9「窗口与系统栏配置」。

L1 门槛检查：构建 ✅、`MiuixTheme` 唯一 ✅、四配色能跑 ✅（material3 已退场到只剩 1 页）、
一个页面完整跑通 ✅、无散写颜色 ✅。

---

## 4. 阶段 3 · 全项目 OS4 迁移（→ L2 / L3）

**我查的文档**：[04](04-component-mapping.md) §2 映射表、§7 迁移顺序；[13](13-project-adaptation-workflow.md) §5。

顺序：

1. 选**试点页**：设置页（3 个分组、6 行、1 个下拉、无动画）。
2. 统一骨架：`MiuixScaffold(containerColor = page())` + `TopAppBar` + `LazyColumn`。
3. 逐页替换 6 个 destination。
4. 弹层：3 个 `AlertDialog` → `GlassDialog`？**不**——本阶段还没接 Glass，
   先用普通 `WindowDialog` 或 MIUIX 的对话框；到 L4 再换（[07](07-glass-component-guide.md) §8.2）。
5. 自绘组件：
   - 字数进度环 → 保留（`Canvas`），但颜色改 `MiuixTheme.colorScheme.primary`，加 `progressBarRangeInfo` 语义。
   - 标签色板 → 保留，补 `selectable(role = RadioButton)` + `contentDescription` + 固定槽位（[08](08-custom-component-migration.md) §5）。

**发现了什么缺漏？**

- ❗手册讲了很多"怎么改"，但**没有说"从哪一页开始"**。新项目最容易犯的错是从主页开始，
  而主页往往有导航 + 悬浮按钮 + 弹层，一处结构错就全错。
  → **已补**：[13](13-project-adaptation-workflow.md) §5.1「先做一个试点页」。
- ❗自绘 `drawable` 怎么换成 Glass 图标？手册只说了"用 Glass 族"，没说找不到对应图标时怎么办。
  → **已补**：[04](04-component-mapping.md) §4 第 5 条「自绘 drawable → ImageVector 的替换策略」。

L2 → L3：6 个页面全部迁移（含 About 页用 [10](10-about-and-special-effects.md) 的固定头部做法，
但不做动态渐变 —— 那是 Glass 层的可选项）。页面覆盖率 100%，组件覆盖率 100%，
排除项：系统分享面板（系统负责）。

---

## 5. 阶段 4 · 自适应与导航（并入 L3）

**我查的文档**：[05](05-navigation-and-adaptive.md) §1 阈值、§3 摆法、§4 五个式子、§9 窗口配置。

动作：

1. 加 `shouldShowSplitPane()` / `shouldExpandNavigationRail()`（抄 example 的阈值）。
2. 手机竖屏：`NavigationBar`（或 Glass 版，等 L4）；宽屏：`NavigationRail`。
   **注意**：不放进 `Row + weight`（[05](05-navigation-and-adaptive.md) §3.2）。
3. 内容让位：`LocalRailInset` 用**函数**下发，在 `PaddingValues.calculate*Padding` 里读。
4. 横向安全区：不在 Scaffold 内容槽的控件用 `rememberHorizontalSafeInsets()`。
5. 底部留白：`maxOf(innerPadding.bottom, clearance)`。
6. RTL / 大字 / 四尺寸截图。

**卡点**：本项目用 `androidx.navigation`，没有 `NavDisplayEffects`，转场圆角做不了。

**我查的文档**：[05](05-navigation-and-adaptive.md) §2.2 只说了 `miuix-nav` 的好处，
**没说 androidx.navigation 用户在保留导航层的前提下具体要做什么、会失去什么**。

- ❗**已补**：[05](05-navigation-and-adaptive.md) §2.4「从 androidx.navigation 迁移（不动导航层的最小改法）」，
  包括"弹层宿主套在 NavHost 外面"这个关键点。

决策：**保留 androidx.navigation**，接受转场无圆角裁剪，到 L4/L5 再评估换 `miuix-nav`。

---

## 6. 阶段 5 · Glass 接入（→ L4）

**前置**：minSdk 26 → 要做 Glass 必须提 minSdk 到 33，或做版本分支。
这是一个**产品决策**，代码助手不能自己决定。

推演两种走法：

### 走法 A：不提 minSdk（停在 L3）

- 不接 Glass，项目完成于 L3。
- 交付时明确写"Glass 不可用原因：minSdk 26 < 33（`miuix-blur` 门槛）"。

### 走法 B：提 minSdk 33（继续 L4~L6）

**我查的文档**：[02](02-version-and-dependencies.md) §3.1、[06](06-glass-architecture.md) 全文、
[07](07-glass-component-guide.md) §3。

1. 加 `miuix-glass` + `miuix-glass-icons`。
2. 建唯一决策点：`LocalGlassEnabled = prefs.glass && isGlassSupported()`。
3. **先画结构图**（[06](06-glass-architecture.md) §4.3 结构 C 起步）：

```text
MainActivity
└─ MiuixTheme
   └─ Scaffold(Transparent, insets=0)          ← 弹层宿主
      └─ Box(fillMaxSize)
         ├─ Box(fillMaxSize).layerBackdrop(rootBackdrop)   ★录制层
         │   └─ NavHost → 6 个页面
         │       └─ 页面 Scaffold
         │           ├─ topBar = GlassTopAppBar(backdrop = pageBackdrop)
         │           └─ content = Box.layerBackdrop(pageBackdrop) ★第二层
         └─ GlassNavigationBar(backdrop = rootBackdrop)     ← 兄弟
```

4. 顺序：决策点 → 整屏录制层 → 页面录制层 → 顶栏 → 底栏 → 页面局部 → 弹层。
5. 弹层（删除确认 / 排序 / 导出）改用 `GlassDialog` / `GlassBottomSheet` / `GlassDropdownPreference`，
   采样源用 `rootBackdrop`（录在 NavHost 外面，真实坐标）。

**卡点**：本项目没有分页容器（不是 Pager），所以没有"槽位坐标"问题 ——
但**如果以后加了分页**（例如笔记列表分 tab），规则仍然成立（[06](06-glass-architecture.md) §6.2）。

**发现了什么缺漏？没有**（06/07 的覆盖是够的）；但确认了一个**易误判点**：
`isRenderEffectSupported()` 不是"GPU 能不能画玻璃"的判据（[06](06-glass-architecture.md) §7.1），
必须同时留用户开关。

L4 门槛：单一决策点 ✅、两类录制层 ✅、四类宿主 ✅、`backdrop=null` 可用 ✅、进出 20 次无闪退 ✅。

---

## 7. 阶段 6 · 全项目 Glass（→ L5）

逐组件 5 问（[07](07-glass-component-guide.md) §6），逐页面判"该/不该"（§8.5）：

| 位置 | 结论 |
|---|---|
| 顶栏 | Glass ✅ |
| 底部导航 | Glass ✅（手机） |
| 侧栏 | Glass ✅（宽屏） |
| 设置页行 | 普通组件（不该 Glass） |
| 编辑器工具栏 | 保留自绘 + `Modifier.glassPanel`（浮在内容上，需要看见文字） |
| 笔记卡片列表 | 普通卡片（不该 Glass：长列表 + 文字对比度） |
| 删除确认 | `GlassDialog` ✅ |
| 排序 | `GlassBottomSheet` ✅ |
| 导出菜单 | `GlassDropdownPreference` / `GlassDropdownPopup` ✅ |
| 字数环 | 普通自绘（不该 Glass） |
| 标签色板 | 普通自绘（不该 Glass） |

结果：Glass 位置 6 个，普通位置 5 个，全部有理由。L5 门槛满足（模拟器级）。

---

## 8. 阶段 7 · 全量测试

**我查的文档**：[11](11-testing-and-acceptance.md) §4 矩阵。

- 构建 debug/release ✅
- 6 页面 × 进出 × 返回 ✅
- 四配色 × 四尺寸 截图 ✅
- 玻璃开/关 ✅
- 弹层 3 个全开一遍 ✅
- 触觉 `dumpsys` ✅
- 真机：**必须做**（玻璃观感、`textureBlur`、圆角安全区、手势条、触觉手感）
- RTL / 大字 1.3× ✅
- 进程死亡恢复 ✅

---

## 9. 阶段 8 · 验收与 L6

按 [13](13-project-adaptation-workflow.md) §10 出 7 项交付 + 评级。

推演结论：**走法 B 可达 L6**（前提是真机证据齐），**走法 A 停在 L3**。

---

## 10. 推演中发现的缺漏与修补（本轮已完成）

| # | 缺漏 | 为什么要紧 | 补在哪 |
|---|---|---|---|
| G1 | 已有 material3 的项目怎么退场（共存/别名/清理顺序/隐形残留） | 新项目 90% 都是 material3；没有退场策略就会长期混搭 | [03](03-os4-theme-system.md) §11 |
| G2 | 窗口与系统栏配置（`enableEdgeToEdge` / `themes.xml` DayNight / 图标反色 / 预测性返回 / `windowSoftInputMode` / configChanges） | 这些不属于组件，但漏了会白边、图标看不见、表单被键盘遮住 | [05](05-navigation-and-adaptive.md) §9 |
| G3 | androidx.navigation 用户的最小改法与"会失去什么" | 大多数项目不会换导航层；没有这节就不知道怎么在保留 NavHost 的情况下接 Glass 宿主 | [05](05-navigation-and-adaptive.md) §2.4 |
| G4 | 自绘 drawable → Glass 图标的替换策略（找不到对应图标怎么办） | 机械替换会导致线宽/光学尺寸不匹配 | [04](04-component-mapping.md) §4 第 5 条 |
| G5 | "从哪一页开始"的试点页策略 | 从主页开始会把结构错误扩散到全项目 | [13](13-project-adaptation-workflow.md) §5.1 |
| G6 | Kotlin 2.0+ 必须应用 compose 编译器插件 | 升级 Kotlin 后 `@Composable` 全红 | [02](02-version-and-dependencies.md) §7 |

修补后重新走一遍流程，**没有新的阻断性缺漏**。

---

## 11. 推演结论

1. **依赖判断**：够。`02` 的决策表 + `01` 的前置条件能覆盖"哪个模块要装、什么版本、SDK 够不够"。
2. **架构迁移**：够（补 G1/G2 后）。Material3 退场与窗口配置是原来最大的两个空白。
3. **组件映射**：够。`04` 的 30 行映射表 + 四种处置 + 图标规则。
4. **Glass 接线**：够。`06` 的 9 步 + 8 条红线 + 3 种宿主结构，`07` 的逐组件说明。
5. **测试/验收**：够。`11` 的三级证据 + 矩阵；`13` 的门与 L0~L6 定义能诚实表达"差真机"。
6. **最容易漏的三件事**（写在这里当提醒）：
   - 弹层宿主必须是最外层 Scaffold（不是页面的）。
   - 跨分页容器的弹层，采样源必须录在容器外。
   - `backdrop = null` 时功能入口必须还在。
7. **这套手册不能替代的一件事**：真机验收。任何"L6"都必须有真机截图与记录，
   否则最高只能写 L5（模拟器级）。

---

## 12. 给下一位代码助手的三句话

1. **先读 `06`，再动一行 Glass 代码。**
2. **先画结构图，再写组件。**
3. **没验证的，写"未验证"，不要写"完成"。**
