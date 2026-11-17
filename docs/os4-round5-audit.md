# MIUIX OS4 交互与视觉细节复查（第 5 轮）

本轮只处理四件事：开始生成按钮与生成进度（**只调研**）、主题设置两个下拉的 Glass、
进出二级页时底栏的收缩展开、一级页返回的退出确认。

---

## 0. 基线与取证条件

| 项 | 值 |
| --- | --- |
| 官方仓库基线 | `compose-miuix-ui/miuix` `origin/main` = **`3a980f7c`**（2026-11-17 00:13） |
| 上一轮基线 | `ac0af467`，本轮相隔 1 个提交：`fix: Optimize scroll performance in UI components`（`PullToRefresh.kt` / `ScrollBar.kt` / `Slider.kt` + `docs/guide/blur|material.md`） |
| 本地 miuix | 已切到 `3a980f7c`；`build-logic/src/main/kotlin/BuildConfig.kt` 的 composite build 补丁（两处 `workingDir = rootDir`）在切换后仍然有效，未丢失 |
| 官方示例 APK | `miuix/example/android/build/outputs/apk/debug/Miuix-v1.0.9(1292)-debug.apk`（由 `3a980f7c` 现场编译） |
| GIF 助手 APK | 同一基线上 `./gradlew :assembleDebug` 成功 |
| 设备 | AVD `android16`，1080×2400 / 420dpi，Android 16，`-gpu host` |
| 截图 | `_archive/screenshots/os4-round5/`（工作区归档，不进应用仓库） |

**模拟器可信度声明**（本文所有 APK 结论都按这条读）：

- **可信**：组件结构、布局与尺寸、参数驱动的颜色（采样像素值）、弹层宿主与采样范围、
  交互行为（返回、导航栈、tab 复位）、AGSL 是否可用 —— 顶部 `progressive blur` 在滚动时
  能正常出图，说明本机 RuntimeShader 路径是通的，没有静默回退。
- **不可信 / 需真机复核**：玻璃材质的厚度、高光、边缘折射、模糊半径的观感；
  个别 GPU 上 AGSL 编译失败后静默回退到 `bandBrush` 的表现；动效与触感的实际手感。
  本文不会把模拟器上的"看起来糊了"当成真机结论。

---

## 1. 对照记录

### 1.1 二级页「开始生成」按钮（**只调研，本轮不改代码**）

| 排查项 | 内容 |
| --- | --- |
| GIF 助手当前实现 | `ToolScreen.kt:114` 的 `bottomBar` → `PrimaryActionBar`（`Widgets.kt:357`）→ `ProgressButton`（`ui/ProgressButton.kt`，移植自 InstallerX Revived，Apache-2.0）。禁用态取 `ProgressButtonDefaults` 默认值：`trackColor = disabledSecondaryVariant`、`contentColor = disabledOnPrimaryButton` |
| MIUIX 官方实现 | `ButtonDefaults.buttonColors()`：`disabledColor = disabledSecondaryVariant`，文字跟随次级档；`ButtonDefaults.buttonColorsPrimary()`：`disabledColor = disabledPrimaryButton`、`disabledContentColor = disabledOnPrimaryButton`（`miuix-ui/.../basic/Button.kt:180-238`）。静态浅色取值：`disabledSecondaryVariant #F2F2F2` / `disabledOnSecondaryVariant #B2B2B2` / `disabledPrimaryButton #C2D9FF` / `disabledOnPrimaryButton #FFFFFF`（`theme/Colors.kt:340-365`） |
| 官方 APK 验证 | 示例首页 Button 区块两行：`Cancel`（#F2F2F2 + 深灰字）、`Submit`（#3482FF + 白字）；第二行两个 `Disabled` —— 次级档是 **#F2F2F2 底 + #B2B2B2 灰字**，主档是 **#C2D9FF 淡蓝底 + 白字**（`02_buttons_zoom.png`）。即官方禁用态是"整对换色"，不是"只把底变灰" |
| 第三方参考 | InstallerX Revived 的 `ProgressButton` 把 `disabledSecondaryVariant`（次级档的底）与 `disabledOnPrimaryButton`（主档的字）拼在一起 —— 这是本项目的直接来源 |
| 当前差异 | 我方是 `#F2F2F2` 底 + `#FFFFFF` 字，对比度约 1.04:1，等于"白字压白底"（`gif_06_tool_disabled.png`）；官方两个档位都没有这种组合 |
| 问题原因 | 不是"灰色选错了"，而是**轨道色与文字色来自两套不同的 family**，跨档拼装 |
| 处理方案 | 见 §3.1 的三条建议（本轮按要求不动代码） |
| 验收方式 | 明/暗 × 静态/Monet 四组截图 + 像素采样对比度；禁用→可用的切换动画不能跳 |

### 1.2 生成进度条（**只调研，本轮不改代码**）

| 排查项 | 内容 |
| --- | --- |
| GIF 助手当前实现 | 没有独立进度条：轨道就是按钮本身，`ProgressButton` 用 `drawWithContent` 画 `width * progress` 的矩形（被 `Surface` 的 shape 裁成圆角），另有 16dp `InfiniteProgressIndicator`；文案 `取消生成 · NN%`，文字色在 **45%** 处从 `onSecondaryContainer` 切到 `onPrimary`（`Widgets.kt:406-469`） |
| MIUIX 官方实现 | 三个组件（`basic/ProgressIndicator.kt`）：`LinearProgressIndicator`（默认高 6dp、圆头、`progress = null` 为不定态）、`CircularProgressIndicator`（30dp / 4dp 描边）、`InfiniteProgressIndicator`（20dp / 2dp）。**官方没有任何"按钮内嵌进度"的组件**，示例里进度只出现在独立区块和 `OverscrollLoadMorePage` 的加载更多 |
| 官方 APK 验证 | `10b_progress_zoom.png`：线性条为 6dp 胶囊，轨道浅灰、填充主色；不定态是一段短条在轨道内滑；圆形组 30dp、圆头描边，末端有一个灰色 `InfiniteProgressIndicator` |
| 第三方参考 | 按钮内进度来自 InstallerX Revived（安装器场景：按钮即"安装/取消"开关） |
| 当前差异 | ① 模式不同（按钮内 vs 独立指示器）；② 45% 换色阈值只在"填充刚好盖住文字"时成立 —— 实测 64% 时填充边缘正好切在 `64` 上，右半个字是白字压在浅灰轨道上，读不出来（`_archive/.../v2_23_busy.png`，见截图索引） |
| 问题原因 | 阈值是拍出来的常数，不是按"文字包围盒 vs 填充边界"算的；单色文字无法同时适配两块底色 |
| 处理方案 | 见 §3.2（本轮按要求不动代码） |
| 验收方式 | 生成中逐档截图（10%/30%/45%/60%/85%），确认整行文字任一时刻都可读；取消仍能中断 |

### 1.3 主题设置两个下拉的 Glass / 模糊不一致（**已修复**）

| 排查项 | 内容 |
| --- | --- |
| GIF 助手当前实现 | 两个菜单是**同一个组件**：`DropdownRow`（`Widgets.kt`）→ `GlassDropdownPreference`，`visuals` 都由 `GlassPopupDefaults.visuals(GlassDefaults.style(isDark))` 生成。唯一差别是 `backdrop` 来自 `GroupCard` 的**卡片局部录制层**（`Modifier.layerBackdrop(cardBackdrop)` 挂在 `Card` 上） |
| MIUIX 官方实现 | `GlassDropdownPreference` 的 `backdrop` 注释写明"必须是排除弹层宿主的背景层"；官方 `GlassPage.kt:441-452` 直接把**整页 `backdrop`** 传进去（那行的行卡片只有一行）；`BlurSection.kt:123-125` 的 `preferenceBackdrop` 挂在**包住整列行 + 280dp 演示卡的 Column** 上，不是单张卡片 |
| 官方 APK 验证 | 示例设置页 `Color Mode` 下拉：面板整体均匀雾化，没有任何边界痕迹（`04_dropdown_color.png` 同批） |
| 第三方参考 | 未使用（官方已覆盖同类场景：单行卡片 + 5 项弹层） |
| 当前差异 | 「主题模式」那张卡有 3 行（~170dp 高），弹层基本落在卡片内 → 看起来正常；「预测性返回动画」那张卡只有 1 行（~56dp），5 项弹层大半伸出卡片下边界 → 伸出去的部分没有像素可采（`cmp_popup2_backanim.png` 能看出一条横向硬边，边下是发灰的平块） |
| 问题原因 | **卡片局部录制层范围不足**。不是材质、不是 `style`、不是宿主层级，也不是坐标偏移 —— 库用 `layerCoordinates.localPositionOf(...)` 反算（`miuix-blur/.../LayerBackdrop.kt:81`），跨容器是精确的。之前一次"脏斑"的真实成因是录制层没垫底色，`rememberPageBackdrop` 的 `drawRect(pageColor())` 已经补上 |
| 处理方案 | 改传**页面正文录制层**，与官方 `GlassPage` 一致：`GlassScope` 新增 `LocalPageBackdrop`，由 `ContentBackdropBox` 下发；`DropdownRow` 消费它；`GroupCard` 不再自建录制层（省一次 GPU 录制）。弹层由 Scaffold 的 popup 层绘制，是录制层的**兄弟**节点，不会采样到自己（AGENTS.md 第 1 条仍成立） |
| 验收方式 | 静态/Monet × 明/暗/AMOLED/关玻璃，两个菜单在同一页面各截一次；弹层底部不能出现横向硬边；无脏斑、无闪退 |

### 1.4 进出二级页时底栏收缩展开（**已修复，A 方案**）

| 排查项 | 内容 |
| --- | --- |
| GIF 助手当前实现 | `App.kt` 里底栏常挂，靠 `GlassNavigationBar(visible = stack.size <= 1)` 开关 |
| MIUIX 官方实现 | `visible` 不是"隐藏"：`GlassNavigationBar.kt:200` 注释写明 "the bar shrinks, fades and blurs itself away together"；实现是 `showScale` 缩到 `GlassMotion.NAV_HIDE_SCALE` + 淡出（`:256-266`） |
| 官方 APK 验证 | 官方示例的底栏画在 Main 那一层页面里，压栈到 About / License 后**整页被替换，底栏根本不在了**（`06_about_page.png`，页面底部是内容本身，没有任何底栏残影）；示例里也没有任何"二级页保留底栏"的场景 |
| 第三方参考 | 未使用 |
| 当前差异 | 动画本身是官方组件的既定行为，**触发条件是我们自己加的** `visible` 开关 —— 官方从不给二级页传 `visible = false` |
| 问题原因 | 我们的底栏在 `NavDisplay` 之外（录制层约束决定，见 AGENTS.md 第 1 条），没法像官方那样随页面一起被替换，于是用 `visible` 代偿，代价就是这段缩放 |
| 处理方案 | 二级页**不挂载**（`if (!wide && stack.size <= 1)`），删掉 `visible` 参数。不引入淡入淡出等新动画 |
| 验收方式 | 进入/返回时底栏只"在场 / 不在场"，无缩放；`reserveNavBar = false` 下二级页末尾不留空带、底部按钮位置不变（`v2_11_tool_bottom.png`）；横屏 rail 不受影响 |

### 1.5 一级页返回时的退出确认（**已修复**）

| 排查项 | 内容 |
| --- | --- |
| GIF 助手当前实现 | `BackHandler(enabled = stack.size <= 1)`：不在 tab 0 就先回 tab 0，否则弹 `WindowDialog`「退出 GIF 助手？」 |
| MIUIX 官方实现 | `AppContent.kt:910` `MainScreenBackHandler`：`isBackEnabled = navigator.current() is Route.Main && backStackSize() == 1 && selectedPage != 0` —— **tab 0 时把手势/按键整个交还系统**，不拦、不弹窗 |
| 官方 APK 验证 | 连续三次返回：About → 设置 tab → 工具 tab → **直接回桌面**（`adb dumpsys` 确认前台变成 launcher），全程无确认框 |
| 第三方参考 | 未使用（官方行为已足够明确） |
| 当前差异 | 多了一层自加的返回拦截 |
| 问题原因 | 项目自行添加的 `BackHandler` 分支，与官方/系统返回语义冲突：拦截后系统拿不到返回事件，预测性返回的"预览退出"也就不会发生 |
| 处理方案 | `BackHandler(enabled = stack.size <= 1 && tab != 0)`，删掉 `confirmExit` 与 `WindowDialog`；tab 0 交还系统 → `Activity.finish()` |
| 验收方式 | 系统手势返回、系统返回键各自在 tab 0 退出到桌面；tab 1/2 返回回到 tab 0；二级页返回一级；预测性返回动画正常；删除/覆盖等业务确认框不受影响 |

---

## 2. 本轮改动清单

| 文件 | 改动 |
| --- | --- |
| `ui/GlassScope.kt` | 新增 `LocalPageBackdrop`（页面正文录制层）；`ContentBackdropBox` 向下提供它 |
| `ui/Widgets.kt` | `GroupCard` 去掉卡片局部 `layerBackdrop` 与 `LocalCardBackdrop`；`DropdownRow` 改用 `LocalPageBackdrop` |
| `App.kt` | 底栏改为条件挂载（`!wide && stack.size <= 1`），去掉 `visible`；`BackHandler` 的 enabled 增加 `tab != 0`；删除退出确认状态与 `WindowDialog` |

未改动：`ProgressButton.kt`、`PrimaryActionBar` 及其配色（第一项按要求只调研）。

---

## 3. 第一项：调研结论与建议（本轮未实施）

### 3.1 禁用态按钮

结论：现在的灰底白字**不符合** MIUIX OS4 的习惯，但问题不在"灰"这个颜色，而在**配色跨档拼装**。

1. **首选**：按主按钮档成对取值 —— `disabledPrimaryButton`（静态浅色 #C2D9FF）+ `disabledOnPrimaryButton`（白）。
   这与官方示例 `Submit / Disabled` 完全一致，是我们这个"主 CTA"的正确档位。
2. **备选（推荐同时评估）**：官方在"条件不满足"时并没有对应的 CTA 示例；OS4 里更常见的是
   **按钮不出现或不出现在可操作位置**。若产品上能接受，未导入素材时不渲染主操作条，
   比"渲染一个几乎不可见的按钮"更接近系统习惯（但会改变页面高度节奏，需一起看）。
3. **不建议**：自行把禁用态改成"深灰底 + 浅灰字"之类。那会成为第三套禁用样式，
   与 `Button` / `TextButton` 的既有语义分叉。

无论选 1 还是 2，都要一并解决"禁用→可用"切换时的底色跳变（现在的 `ProgressButton` 是瞬切）。

### 3.2 生成进度

1. 官方没有"按钮内进度"，现在的实现属第三方模式，可以保留，但**换色的 45% 阈值要改**：
   应按"填充右边界是否越过文字包围盒"决定颜色，或者给文字加一层跟随底色的描边/双色，
   否则 45%~60% 区间必然出现半行读不清（本轮实测 64% 仍然能拍到）。
2. 若要"更像 OS4"，可以把 `LinearProgressIndicator`（6dp 胶囊）放在按钮上方，
   按钮只承担"取消"语义；进度用官方组件、取消用官方按钮，两者都不必自绘。
3. 无论哪条，都要保留现在"生成中点击 = 取消"的语义（已写进 `PrimaryActionBar` 注释与文案）。

---

## 4. 保留问题

| 问题 | 原因 | 建议 |
| --- | --- | --- |
| 宽屏侧栏玻璃：rail 左侧约半块区域不发糊 | rail 叠在内容之上，而内容录制层从 `x = railInset` 开始录制（`App.kt` 的 `padding(start = railInset).layerBackdrop(...)`），rail 左半段落在录制层之外 —— 与本题 1.3 的"采样越界"同源 | 把录制层扩到整宽（rail 会让出左侧，采样多为空白/背景，代价可接受），或给 rail 下方单独垫一层录制层；本轮未改，避免影响已验收的宽屏布局 |
| 玻璃的真机观感 | 模拟器 GPU 与真机不同 | 发布前在真机上看一次顶栏 progressive blur 与底栏玻璃 |
| 生成中文字换色阈值、禁用态配色 | 属第一项，本轮只调研 | 见 §3 |

---

## 5. 截图索引（`_archive/screenshots/os4-round5/`）

| 文件 | 内容 |
| --- | --- |
| `02_buttons_zoom.png` | 官方示例 Button 区块：正常/禁用 × 次级/主 四态 |
| `10b_progress_zoom.png` | 官方线性（含不定态）与圆形进度指示器 |
| `06_about_page.png` | 官方二级页（About）：底部没有底栏 |
| `gif_04_dropdown_theme.png` | 修复前：「主题模式」弹层（落在卡片内，正常） |
| `gif_05_dropdown_backanim.png` | 修复前：「预测性返回动画」弹层（伸出卡片，下半截发灰） |
| `cmp_popup1_theme.png` / `cmp_popup2_backanim.png` | 上面两张的放大对照，硬边清晰可见 |
| `gif_06_tool_disabled.png` | 修复前（也是当前）的「开始生成」禁用态：白字压 #F2F2F2 |
| `gif_07_exit_dialog.png` | 移除前的「退出 GIF 助手？」确认框 |
| `after_cmp_popup2.png` | 修复后的同一弹层：整块均匀雾化，硬边消失 |
| `after_02_backanim.png` | 修复后整屏（静态浅色） |
| `after_04_before_push.png` / `after_05_mid_push.png` | 一级页有底栏 → 点击后 120ms 的转场帧：底栏已不在场，没有任何缩小残影 |
| `after_11_tool_bottom.png` | 二级页底部：主操作条贴底，无 100dp 空带 |
| `after_23_busy.png` | 生成中 64%：填充边缘切过文字，右半个百分数压在浅色轨道上（第一项的证据） |
| `after_30_landscape_home.png` | 横屏：左侧 rail、无底栏；rail 左半段玻璃未糊（既有问题，见 §4） |
