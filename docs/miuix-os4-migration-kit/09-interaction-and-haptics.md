# 09 · 交互、触觉反馈与无障碍

> 触觉反馈最容易做错的两件事：**重复加**（库已经加了）和**加错地方**（没有状态变化也震）。
> 本篇的归属表来自 GIF 助手对 MIUIX 全量源码的检索 + `dumpsys vibrator_manager` 实测。

---

## 1. 原则

1. **库自带的不要重复加**：MIUIX 的 `Switch` / `Slider` / `Checkbox` / `RadioButton` /
   `GlassDropdownPreference` 等组件内部已经调用了 `performHapticFeedback`。
2. **只对"状态真的变化"给反馈**：点已选中的项不该震。
3. **反馈强度/开关交给系统**：`LocalHapticFeedback` 会自动遵守系统触觉设置与强度。
4. **禁用态不给反馈**。
5. **不要自己造反馈类型**：用 `HapticFeedbackType.ToggleOn` / `ToggleOff` / `Confirm` /
   `ContextClick` / `GestureThresholdActivate` / `TextHandleMove`。

---

## 2. 库自带触觉的组件（不要重复加）

> 📌 全部为 `miuix-*/src/**/*.kt` 的 `performHapticFeedback` 调用点。
> 路径省略 `src/commonMain/kotlin/top/yukonga/miuix/kmp/`。

| 组件 | 位置 | 触发条件 | 类型 |
|---|---|---|---|
| `Switch`（点按/整行切换） | `basic/Switch.kt:123-125` | `toggleable` 的值变化 | `ToggleOn` / `ToggleOff` |
| `Switch`（拖拽拇指到极限） | `basic/Switch.kt:186` / `:190` | 拖到另一端边界 | `ToggleOff` / `ToggleOn` |
| `Switch`（拖拽松手未达阈值） | `basic/Switch.kt:206` / `:208` | 松手时按方向补一次 | `ToggleOff` / `ToggleOn` |
| `Checkbox` | `basic/Checkbox.kt:150` | 勾选变化 | — |
| `RadioButton` | `basic/RadioButton.kt:101` | 选中变化 | — |
| `RadioButtonPreference` | `preference/RadioButtonPreference.kt:131-132` | `if (selected) ToggleOff else ToggleOn` | `ToggleOn`/`ToggleOff` |
| `Slider`（边界） | `basic/Slider.kt:1054`（默认 `DefaultHapticEffect = Edge`，`:1367`） | 拖到 `start` / `endInclusive` | `GestureThresholdActivate` |
| `Slider`（分档） | `basic/Slider.kt:1075` / `:1098` | 跨档 / 跨关键点（`hapticEffect = Step` 时） | `TextHandleMove` |
| `NumberPicker` | `basic/NumberPicker.kt:135` | 选中项变化 | — |
| `PullToRefresh` | `basic/PullToRefresh.kt:734` | 触发刷新 | — |
| `GlassDropdownPreference`（展开） | `glass/GlassDropdownPreference.kt:138` | `if (expanded)` | `ContextClick` |
| `GlassDropdownPreference`（选中） | `glass/GlassDropdownPreference.kt:172` | 点选条目 | `Confirm` |
| `OverlayDropdownMenu` / `OverlayDropdownPopup` | `preference/.../OverlayDropdownMenu.kt:120`、`OverlayDropdownPopup.kt:81` | 展开/选中 | `ContextClick` / `Confirm` |
| `Modifier.scrollEndHaptic()` | `ui/utils/ScrollEndHaptic.kt:73` / `:83`（默认类型 `:125`） | 甩到上/下边界且该边界上次未被标记 | `TextHandleMove` |

**重复抑制机制**（不要拆掉）：

- `Switch` 用 `hasVibrated` / `hasVibratedOnce` 双向抑制（`Switch.kt:186-208`）。
- `Slider` 用 `edgeFeedbackTriggered` 抑制边界重复触发（`Slider.kt:1043-1058`）。
- `scrollEndHaptic` 用"边界标记"抑制同一端反复触发。

> 实测验证：分辨率滑块拖到最大只有**一条**记录，在最大值处再拖**无新记录**
> （`dumpsys vibrator_manager`，🔴 模拟器无马达，只能确认"API 被调用且系统接受"）。

### 2.1 确认**没有**触觉的库组件（需要就自己加）

`Scaffold`、`TopAppBar`、`Card`、`Button`、`Surface`、`IconButton`、`Icon`、`Text`、
`TextField`、`BasicComponent`、`ArrowPreference`、`SmallTitle`、
`GlassTopAppBar`、`GlassIconButton`、`GlassNavigationBar`、`GlassNavigationRail`、
`GlassDialog`、`GlassBottomSheet`、`GlassDialogButton`、`GlassFloatingActionButton`、
`GlassFloatingToolbar`、`InfiniteProgressIndicator`。

> ⚠️ 特别说明：`SwitchPreference` 点**整行文字区**是**没有**反馈的（只有开关本体与拖拽有）。
> 这是库的现状，不是 bug。如果你的产品要求"整行点击也震"，需要自己在行的 `onClick` 里加，
> 但要注意别和开关本体的反馈叠成两次。

---

## 3. 业务层该加什么：判定表

| 控件 | 需要业务加吗 | 说明 |
|---|---|---|
| `SwitchPreference`（整行点击） | ❌ | 库已有 |
| `SliderPreference` | ❌ | 库已有（边界/分档） |
| `CheckboxPreference` / `RadioButtonPreference` | ❌ | 库已有 |
| `GlassDropdownPreference` | ❌ | 库已有（展开 + 选中） |
| 底部导航切换 tab | ❌（建议不加） | 官方不给导航加触觉；加了会显得"廉价" |
| `ArrowPreference` 跳转 | ❌ | 只有页面转场，不需要震动 |
| 主操作按钮（生成/开始） | ⭕ 可选 | 若操作耗时 > 1s，可在**开始**时给一次 `Confirm`；不要每帧给 |
| 自绘选择控件（色板/评分） | ✅ | 补 `ToggleOn`（选中变化时） |
| 长按菜单 | ⭕ | 若系统无默认反馈，可补 `ContextClick` |
| 危险操作确认 | ⭕ | 可补 `Confirm`（在真正执行时，不是在弹窗出现时） |
| 拖拽排序开始/结束 | ✅ | `GestureThresholdActivate` / `TextHandleMove` |

### 3.1 正确的自定义反馈写法

```kotlin
val haptic = LocalHapticFeedback.current

Modifier.selectable(
    selected = selected,
    role = Role.RadioButton,
    onClick = {
        if (!selected) {                                    // ← 只在状态变化时
            haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
            onSelect(value)
        }
    },
)
```

🏠 参考实现：`.../screens/ToolScreen.kt:378-385`。

**反例**：

```kotlin
onClick = {
    haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)   // ❌ 每次点击都震
    onSelect(value)
}
```

---

## 4. 交互状态：OS4 组件必须有的四种

| 状态 | 视觉 | 语义 | 常见遗漏 |
|---|---|---|---|
| 默认 | 主题色 | — | — |
| 按下 | 库的 ripple/scale | — | 用 `Box.clickable` 自己画，丢了 ripple |
| 禁用 | 同档 `disabled*` 色 | `disabled()` | 只改透明度（对比度不可控） |
| 选中 | `primary` 或指示器 | `selected` / `stateDescription` | 只改颜色不给语义 |
| 加载 | 进度指示器 + 保持可点击（若是可取消任务） | `stateDescription` | 加载时禁用按钮（用户无法取消） |

### 4.1 加载态的一个好设计（GIF 助手）

生成中按钮**保持 enabled**，文案变成「取消生成 · 45%」，点击 = 取消。

```kotlin
enabled = if (busy) true else enabled
label = if (busy) "取消生成 · ${(progress * 100).toInt()}%" else text
```

理由：用户在等一个**可以中断**的任务，禁用按钮会让他无处可点。
（🏠 `.../ui/Widgets.kt:578-590`）

---

## 5. 无障碍（迁移时最容易被忽略）

### 5.1 必做清单

| 项 | 做法 |
|---|---|
| 图标按钮 | `contentDescription` 不要为 null（装饰性图标才 null） |
| 自绘可交互元素 | `Modifier.semantics { role = Role.Button / Role.RadioButton }` |
| 选中态 | `semantics { selected = … }` 或 `stateDescription = "已选中"` |
| 进度 | `semantics { progressBarRangeInfo = … }` |
| 分组标题 | 只做视觉标题，不要给 `heading()` 除非确实是标题结构 |
| 最小点击区 | ≥ 48dp（Compose 的 `minimumInteractiveComponentSize` 会自动补，但自绘 `Box` 不会） |
| 颜色不是唯一信息 | 选中态不能只靠颜色（加指示器/勾） |
| 字体缩放 | 用 `sp`；测试 1.3× |

### 5.2 自绘控件的语义模板

```kotlin
Box(
    Modifier
        .size(48.dp)                                  // 点击区
        .selectable(selected = selected, role = Role.RadioButton, onClick = …)
        .semantics {
            contentDescription = "蓝色"
            if (selected) stateDescription = "已选中"
        },
)
```

---

## 6. 系统行为兼容

| 系统设置 | 影响 | 要注意什么 |
|---|---|---|
| 触觉反馈开关（关闭） | `performHapticFeedback` 无效果 | 不要做"靠震动传递唯一信息"的交互 |
| 触觉强度 | 强弱由系统决定 | 不要假设"一定能感觉到" |
| 无马达设备（部分平板/模拟器） | 无效果 | 关键状态必须有视觉冗余 |
| 减弱动画（开发者选项） | 动画时长归零 | 转场/展开动画不能依赖时长做状态同步 |
| 字体大小 | 布局变化 | 见 [05](05-navigation-and-adaptive.md) §6.3 |
| 显示大小（density） | dp 不变、像素变 | 所有尺寸用 dp |
| 深色/浅色 | 颜色 | 见 [03](03-os4-theme-system.md) |
| 强制 RTL | 布局镜像 | 见 [05](05-navigation-and-adaptive.md) §7 |

---

## 7. 验证方法

### 7.1 触觉：`dumpsys vibrator_manager`

```bash
adb shell dumpsys vibrator_manager | grep -A2 "performHapticFeedback"
```

看 `reason: performHapticFeedback(constant=N)` 的原始值，按"源码分支 ↔ 实测事件"配对。
GIF 助手实测记录（🔴 模拟器，只证明"调用发生"）：

| 操作 | 实测 constant |
|---|---|
| 点开关（开/关） | 21 / 22 |
| 复选框 | 22 |
| 下拉展开 | 6 |
| 下拉选中 | 16 |
| 分辨率滑块拖到最大 | 23（一条） |
| 在最大值再拖 | **无新记录**（抑制生效） |
| 切底栏 tab | **无记录** |
| 点 `ArrowPreference` | **无记录** |
| 点 `SwitchRow` 整行文字区 | **无记录** |

### 7.2 手感

只能真机。验证清单：

- [ ] 开关切换的震动"短促、单次"
- [ ] 滑块到边界的反馈不连续触发
- [ ] 长按/拖拽有起始感
- [ ] 系统关闭触觉后完全静音
- [ ] 系统触觉强度调到最低仍有可感知反馈（或至少不影响功能）

### 7.3 无障碍

- [ ] TalkBack 打开后每个可交互元素都有可读标签
- [ ] 自绘色板能读出颜色名与选中态
- [ ] 焦点顺序合理（不受视觉层级影响）
- [ ] 字体 1.3× / 2.0× 下无截断

---

## 8. 参考

- 触觉全量检索结果（23 个文件、8 个组件族）：`docs/os4-round8-audit.md` §4
- 自绘色板语义修复：`docs/os4-round10-audit.md` §4.2 #3
- 官方 `RadioButton` 触发条件：`miuix-preference/.../RadioButtonPreference.kt:131`
- 官方 `Switch` 抑制逻辑：`miuix-ui/.../basic/Switch.kt:186-208`
- 官方 `scrollEndHaptic`：`miuix-ui/.../utils/ScrollEndHaptic.kt`
