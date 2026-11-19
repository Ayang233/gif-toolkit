# 08 · 自定义组件迁移

> 自定义组件是迁移中最危险的部分：它们**最容易破坏 Glass 的层级与坐标**，
> 也最容易成为"第二套设计语言"。
> 本篇给出一套判定流程 + GOP 助手里四个真实自定义组件的完整拆解。

---

## 1. 判定流程（6 步，逐条回答）

```
1. 官方有没有对应组件？
   ├─ 有 → 用它（读 04）
   └─ 没有 → 2

2. 业务能不能接受"换成官方组件的交互"？
   ├─ 能 → 换（通常体验更一致）
   └─ 不能 → 3

3. 这个自定义组件是否真的必要？（有没有独特交互/数据可视化）
   ├─ 不必要 → 用官方组件 + 少量样式
   └─ 必要 → 4

4. 它继承了 MIUIX Theme 吗？
   ├─ 否 → 先把颜色/字号/圆角改成 MiuixTheme.* 再继续
   └─ 是 → 5

5. 它遵循 OS4 的交互状态吗？（按下/禁用/选中/加载）
   ├─ 否 → 补状态（见 09）
   └─ 是 → 6

6. 它破坏 Glass 层级了吗？
   ├─ 是（自建录制层把消费者包进去 / 采样源跨容器）→ 改结构（读 06 的红线）
   └─ 否 → 保留
```

---

## 2. `ProgressButton`：按钮内嵌进度

### 2.1 它解决什么问题

普通"按钮 + 独立进度条"的组合有两个问题：

1. 同一任务出现**两条进度**（内容区一条、底部操作条一条）；
2. 进度条与按钮分离，视线要来回跳。

`ProgressButton` 把进度**画进按钮轨道内部**，填充从左往右铺，按钮本身就是进度条。

### 2.2 公开 API（GIF 助手移植版）

```kotlin
@Composable
@NonRestartableComposable
fun ProgressButton(
    progress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cornerRadius: Dp = 16.dp,
    minHeight: Dp = 50.dp,
    colors: ProgressButtonColors = ProgressButtonDefaults.progressButtonColors(),
    insideMargin: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    interactionSource: MutableInteractionSource? = null,
    indication: Indication? = LocalIndication.current,
    content: @Composable RowScope.() -> Unit,
)

@Immutable
data class ProgressButtonColors(
    val trackColor: Color,
    val progressColor: Color,
    val contentColor: Color,
    val disabledTrackColor: Color,
    val disabledProgressColor: Color,
    val disabledContentColor: Color,
)
```

🏠 `GifHelper/.../ui/ProgressButton.kt:48-120`（Apache-2.0，移植自 MIUIX 生态）。

### 2.3 实现要点（三处值得抄）

**(1) 用官方 `Surface` 承载，圆角与裁剪交给它**

```kotlin
Surface(
    onClick = { if (enabled) onClick() else Unit },
    enabled = enabled,
    shape = RoundedCornerShape(cornerRadius),
    color = trackColor,
    contentColor = contentColor,
    interactionSource = interactionSource,
    indication = indication,
    modifier = modifier
        .heightIn(min = minHeight)
        .semantics { role = Role.Button },   // 语义：它是按钮
) { … }
```

📌 好处：按下态、涟漪、禁用语义都由库处理；自己用 `Box.clickable` 会丢这些。

**(2) 进度用 `drawWithContent` 画，天然被 shape 裁圆角**

```kotlin
Modifier.drawWithContent {
    drawContent()
    val w = size.width * progress.coerceIn(0f, 1f)
    if (w > 0f) drawRect(color = progressColor, size = Size(w, size.height))
}
```

🏠 `ProgressButton.kt:82-90`。

**(3) 禁用态与进度态各用一套颜色，且必须同档配对**

```kotlin
// 进度中：轨道浅、填充主色、文字色在 45% 处切换
ProgressButtonDefaults.progressButtonColors(
    trackColor = MiuixTheme.colorScheme.secondaryVariant,
    progressColor = MiuixTheme.colorScheme.primary,
    contentColor = if (progress < 0.45f) MiuixTheme.colorScheme.onSecondaryContainer
                   else MiuixTheme.colorScheme.onPrimary,
)
// 禁用：同档配对（方案 A）
ProgressButtonDefaults.progressButtonColors(
    trackColor = MiuixTheme.colorScheme.primary,
    progressColor = MiuixTheme.colorScheme.primary,
    contentColor = MiuixTheme.colorScheme.onPrimary,
    disabledTrackColor = MiuixTheme.colorScheme.disabledPrimaryButton,
    disabledProgressColor = MiuixTheme.colorScheme.disabledPrimaryButton,
    disabledContentColor = MiuixTheme.colorScheme.disabledOnPrimaryButton,
)
```

🏠 `GifHelper/.../ui/Widgets.kt:551-576`。

### 2.4 已知风险

| 风险 | 说明 |
|---|---|
| 45% 换色阈值是"拍出来的常数" | 当填充右边界正好切在文字上时，单色文字无法同时适配两块底色。R5 建议改成"按文字包围盒判断"或改双色描边 🔴 未实施 |
| 进度中间态难验证 | 小任务 < 1s，按钮来不及进"取消生成 · x%" 🔴 见 [11](11-testing-and-acceptance.md) §5 |
| 移植件不是库 API | 新项目可以照抄**思路**（Surface + drawWithContent），代码要自己写 |

### 2.5 迁移建议

- 如果你的项目没有"长任务 + 进度"场景，**不要**引入这个组件（直接用 `Button`）。
- 如果有：优先考虑官方 `LinearProgressIndicator` 放按钮上方（更简单、可访问性更好），
  再考虑内嵌进度。

---

## 3. `PrimaryActionBar`：底部主操作区

### 3.1 它解决什么问题

二级页底部有一个通栏主按钮。三个坑：

1. 列表内容会从按钮周围的缝隙里透出来（滑块圆弧、色块边缘）；
2. 加渐变遮罩会在按钮上方切出一条可见色阶；
3. 侧栏展开/横屏挖孔时按钮被压住。

### 3.2 正确写法（四个决定）

```kotlin
Column(
    modifier = Modifier
        .fillMaxWidth()
        .background(pageBaseColor())          // ① 与页面同色，视觉上"没有背景"
        .navigationBarsPadding()              // ② 让开系统导航栏
        .padding(end = sideGap + safeEnd, top = 20.dp)
        .padding(bottom = bottomGap)          // ③ 圆角安全区
        .railStartInset(sideGap + safeStart)  // ④ 侧栏让位 + 起始安全区
) { ActionButton(…) }
```

🏠 `GifHelper/.../ui/Widgets.kt:497-518`。

| 决定 | 为什么 |
|---|---|
| ① 纯色 `pageBaseColor()` | 渐变会切出色阶；完全不铺会透出内容；同色纯色 = 视觉无背景 + 挡住穿透 |
| ② `navigationBarsPadding()` | 它不在 Scaffold 内容槽，拿不到 `innerPadding` |
| ③ `safeBottomGap(corner, sideGap)` | 大圆角机型贴边会被切角（公式见 [05](05-navigation-and-adaptive.md) §4.5） |
| ④ `railStartInset(base)` | `base` 必须真的用上（曾经被忽略 → 窄屏下按钮贴屏幕边缘） |

底部下限：官方 `GlassPage.bottomBar` 用 `maxOf(24.dp, safeBottom + 8.dp, paddingBottom + 8.dp)`，
所以这里取 `maxOf(safeBottomGap(...), 24.dp)`。

### 3.3 已知风险

- 底部导航条留白在真机上是否足够 🔴 未验证（模拟器没有手势导航条遮挡样本）。
- 底栏高度按 54dp 最小值算；**大字体（≥1.6）或标题换行时底栏会变高**，可能压住列表末尾 🔴 推断，未实测。

---

## 4. `PreviewStage`：媒体预览区

### 4.1 问题

横屏窗口只有 ~411dp 高，而 16:9 铺满宽度需要 **620dp**。结果：

- 预览把参数区整块顶出屏幕；
- 空态提示语居中在视口之外，看起来"预览区是空的"。

### 4.2 解法：高度上限按**窗口高度**算，不是按约束

```kotlin
val windowHeight = LocalWindowInfo.current.containerSize.height
BoxWithConstraints(modifier.fillMaxWidth()) {
    val cap = with(density) {
        val h = windowHeight.toDp()
        h * if (h < 500.dp) 0.32f else 0.42f     // 矮窗口取更小比例
    }
    val stageHeight = (maxWidth / aspect).coerceAtMost(cap)
    Box(Modifier.fillMaxWidth().height(stageHeight).clip(RoundedCornerShape(12.dp))
        .background(pageColor())) { … }
}
```

🏠 `GifHelper/.../ui/Widgets.kt:390-450`。

三个要点：

1. 在 `LazyColumn` 的 item 里，`maxHeight` 是**无穷大**，按约束算会得到"宽度推出来的离谱高度"。
2. 矮窗口（< 500dp）用 32%：42% 会把预览推到按钮底下。
3. 舞台底色用"次级表面"（`pageColor()`），不能用卡片色（会和卡片糊成一片，透明素材完全没边界）。

### 4.3 可迁移性

**业务特定**。比例（4:3、16:9）、封顶比例（32%/42%）都要按项目调。
可复用的是"**按窗口高度封顶**"这个思路，以及"舞台用次级表面"这条视觉规则。

---

## 5. 自绘选择控件（色板）：语义比外观重要

GIF 助手的"抠图颜色"是五个色点 + 标题，自绘了 `Box.selectable`。迁移时补了三件事：

| 问题 | 修法 |
|---|---|
| TalkBack 读空标签 | 每个色点加 `contentDescription`（"绿"/"蓝"/"白"/"黑"/"红"） |
| 无选中语义 | `selectable(role = Role.RadioButton, selected = …)` + `stateDescription` |
| 选中时尺寸 24→28dp 让整行跳动 | 固定 28dp 槽位（圆点仍 24dp），行高补到 56dp |

🏠 `GifHelper/.../screens/ToolScreen.kt:353-402`。

触觉：只在**选中项真的变化**时发 `HapticFeedbackType.ToggleOn`（依据官方
`RadioButtonPreference.kt:131` / `RadioButton.kt:101` 的触发条件）。详见 [09](09-interaction-and-haptics.md)。

> 为什么不用官方 `RadioButtonPreference`：版式是"标题 + 一排色点"，官方是"标题 + 单个控件"。
> 这种情况属于 §1 的第 3 步"必要"分支，但必须补齐语义。

---

## 6. `GlassCard`：玻璃卡（唯一推荐的玻璃卡片写法）

```kotlin
@Composable
fun GlassCard(backdrop: Backdrop?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val glassy = backdrop != null
    Card(
        modifier = modifier
            .then(
                if (glassy) Modifier.textureBlur(
                    backdrop = backdrop,
                    shape = RoundedCornerShape(CardDefaults.CornerRadius),
                    blurRadius = 60f,                       // 薄玻璃，比顶栏/底栏小一档
                    colors = BlurDefaults.blurColors(
                        blendColors = listOf(BlendColorEntry(surface.copy(alpha = 0.6f))),
                    ),
                ) else Modifier
            ),
        colors = CardDefaults.defaultColors(color = if (glassy) Color.Transparent else cardColor()),
    ) { content() }
}
```

🏠 `GifHelper/.../ui/Widgets.kt:187-219`（写法来自 InstallerX `MiuixAboutPage.kt:612-638` 的转引）。

要点：

- **卡片本体必须 `Color.Transparent`**，否则材质被卡片底色盖住。
- `backdrop == null` 必须退回**不透明卡**：`textureBlur` 没有采样源时会把卡片画成透明板，
  入口会整块消失（那是功能回退，不只是观感）。
- 卡片玻璃**只能用在背景本身是渐变/图片的页面**（About）。普通设置页用不透明卡：
  文字对比度更稳，性能更好。

---

## 7. 自定义组件的通用红线

| 红线 | 说明 | 事故 |
|---|---|---|
| 不要自建录制层把玻璃消费者包进去 | 采样到自己 → `libhwui` 栈溢出 | [12](12-known-issues-and-solutions.md) C10 |
| 不要用"卡片局部录制层"给弹层当采样源 | 弹层伸出卡片就没有像素 | C12 |
| 不要在组件内部读逐帧动画值 | 每次变化都重组 | [06](06-glass-architecture.md) R8 |
| 不要把库默认值抄成常量 | 上游一改就对不上 | C45 |
| 不要自绘渐变冒充模糊 | 与真机不一致，返工 | C24 |
| 不要用 `Box.clickable` 代替 `Surface`/`Button` | 丢按下态、涟漪、禁用语义 | §2.3 |
| 不要在组件里写死颜色 | 深浅色/Monet 必塌 | [03](03-os4-theme-system.md) §8 |

---

## 8. 迁移模板：把任意自定义组件改成 OS4

```kotlin
// 迁移前
@Composable
fun OldWidget(...) {
    Box(
        Modifier
            .background(Color(0xFF2B2B2B))          // ① 写死颜色
            .clip(RoundedCornerShape(8.dp))         // ② 写死圆角
            .clickable { onClick() }                // ③ 丢语义/涟漪
    ) {
        Text("…", color = Color.White, fontSize = 13.sp)   // ④ 写死字号
    }
}

// 迁移后
@Composable
fun NewWidget(...) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),                          // 或 CardDefaults.CornerRadius / 库默认
        color = cardColor(),                                        // ① 语义色
        modifier = Modifier.semantics { role = Role.Button },        // ③ 语义
    ) {
        Text(
            text = "…",
            style = MiuixTheme.textStyles.body1,                    // ④ 语义字号
            color = MiuixTheme.colorScheme.onSurfaceContainer,      // ① 语义色
        )
    }
}
```

五步改写：

1. 颜色 → `MiuixTheme.colorScheme.*` 的语义角色（[03](03-os4-theme-system.md) §2.1）。
2. 圆角 → `CardDefaults.CornerRadius` / 库默认 / 设计值（不要 `8.dp` 这种散值）。
3. 容器 → `Surface` / `Card` / `Button`，拿回按下态与语义。
4. 文字 → `MiuixTheme.textStyles.*`。
5. 如果有玻璃 → 按 [06](06-glass-architecture.md) 接线，**不要在组件内部建录制层**。

---

## 9. 检查清单

- [ ] 每个保留的自定义组件都能回答 §1 的 6 个问题
- [ ] 组件内没有 `Color(0x…)` / `fontSize = …sp`（除刻意的设计值）
- [ ] 组件内没有自建 `layerBackdrop`
- [ ] 组件有 `role` / `contentDescription` / `stateDescription`（若可交互）
- [ ] 组件有禁用态、按下态、加载态
- [ ] 组件的尺寸来自 Theme 或统一常量
- [ ] 组件在玻璃关闭时仍可用
