# 11 · 测试、验收与回归

> **核心纪律：代码接线正确、运行时能力具备、像素表现正确，是三个不同的验收维度。**
> 把三者混为一谈，就会得出"模拟器上发灰 = 代码写错"或"编译通过 = 适配完成"这两种错误结论。

---

## 1. 三级证据模型

| 级别 | 能证明什么 | 手段 | 不能证明什么 |
|---|---|---|---|
| **L1 代码接线** | 节点关系、录制层位置、参数传递正确 | 源码审计 + 结构图 + grep | 运行时是否生效 |
| **L2 运行时能力** | 设备能否画玻璃 | `isRuntimeShaderSupported()`（SDK≥33）、`isRenderEffectSupported()`（SDK≥31，且**粒度极粗**） | 像素是否正确 |
| **L3 像素表现** | 玻璃厚薄、模糊、边缘、色差 | 真机截图 + 逐点取色 / 录屏逐帧 | — |

结论：

- L1 只能靠**人读代码**（本知识库的 [06](06-glass-architecture.md) 红线就是检查表）。
- L2 只能排除"完全不可能"的设备，不能保证效果。
- **L3 必须真机**。

> 因此"验收完成"的定义是 **L1 + L2 + L3 都有证据**，缺一不算。
> GIF 助手 1.10 的现状：L1 ✅、L2 ✅（模拟器 API 36）、L3 **部分 🟡/🔴**（真机玻璃观感与 `textureBlur` 未验）。

---

## 2. 各维度能用什么环境验

| 验收项 | 源码审计 | 模拟器（`-gpu host`） | 模拟器（`swiftshader_indirect`） | 真机 |
|---|---|---|---|---|
| 节点关系 / 是否会闪退 | ✅ | ✅ | ✅ | ✅ |
| 布局 / 让位 / Insets | ✅ | ✅ | ✅ | ✅ |
| 颜色层次（四套配色） | 🟡 | ✅ | ✅ | ✅ |
| 玻璃模糊是否存在 | ❌ | 🟡 | ❌ | ✅ |
| 玻璃厚薄/高光/折射 | ❌ | ❌ | ❌ | ✅ |
| `textureBlur` + `DstIn` | ❌ | 🟡 | ❌（不产出像素） | ✅ |
| AGSL 渐进模糊 | ❌ | 🟡 | ❌（错位副本） | ✅ |
| 触觉"有没有被调用" | ✅ | ✅（`dumpsys`） | ✅ | ✅ |
| 触觉手感/强度 | ❌ | ❌（无马达） | ❌ | ✅ |
| 性能/掉帧 | ❌ | 🟡 | ❌ | ✅ |
| 屏幕圆角安全区 | ❌ | ❌（圆角=0） | ❌ | ✅ |
| 手势导航条遮挡 | ❌ | ❌（无手势条） | ❌ | ✅ |
| 大字体 / RTL | ✅ | ✅ | ✅ | ✅ |
| 进程死亡恢复 | ❌ | ✅ | ✅ | ✅ |

---

## 3. 工具与命令

### 3.1 截图（别用 `>` 重定向）

```powershell
# Windows PowerShell：直接重定向会把 PNG 写坏（按文本编码）
cmd /c "adb exec-out screencap -p > shot.png"

# 或先落设备再 pull
adb shell screencap -p /sdcard/shot.png ; adb pull /sdcard/shot.png .
```

### 3.2 尺寸与姿态

```bash
adb shell wm size                      # 物理分辨率
adb shell wm density 320               # 改密度（改完记得恢复）
adb shell settings put system user_rotation 1     # 横屏
adb shell settings put system accelerometer_rotation 0
```

常用组合（1200dp 级别的验证）：

| 目标 | 命令 |
|---|---|
| 1200 × 540dp 横屏 | `wm size 2400x1080` + `wm density 320` + 横屏 |
| 1200dp 竖屏 | `wm size 2400x1080` + `wm density 144` + 竖屏 |
| 914dp 手机横屏 | `wm density 420` + 横屏（1080×2400 屏） |

> 测完恢复：`adb shell wm size reset` / `wm density reset`。

### 3.3 像素测量

GIF 助手的做法：截图后用 Pillow 逐点/逐行扫描。

```python
from PIL import Image
im = Image.open("shot.png").convert("RGB")
# 沿一条竖线扫，找色带边界
for y in range(2000, 2400):
    print(y, im.getpixel((40, y)))
```

要量什么：

- 页面底色 vs 卡片色的实际 RGB（验证层次）。
- 侧栏面板右边界 / 卡片左边界（验证让位，÷2 = dp）。
- 顶栏区域是否有"深色带"（验证采样空）。
- 弹层区域内是否有硬边（沿横向扫亮度突变）。

### 3.4 录屏逐帧

```bash
adb shell settings put global animator_duration_scale 10   # 放慢 10 倍
adb shell screenrecord --fps 60 /sdcard/nav.mp4
```

用来验证"底栏是否跟着页面被盖住"这类**转场行为**（GIF 助手 R8 用 60fps 逐帧看到
底栏文字 x 从 359 变到 170，位移 −189px，与新页左边缘 x≈318 互为印证）。

### 3.5 坐标探针（临时）

```kotlin
Modifier.onGloballyPositioned { coords ->
    println("panelWin=${coords.positionInWindow()} layer=${coords.size}")
}
```

只在排查坐标问题时临时加，**验完删除**（GIF 助手 R7 这么定位到 `offset.x = -1080`）。

### 3.6 触觉

```bash
adb shell dumpsys vibrator_manager | grep -A2 performHapticFeedback
```

### 3.7 闪退

```bash
adb logcat -c && adb shell am start -n <pkg>/.MainActivity
adb logcat | grep -E "Fatal signal|FATAL EXCEPTION|StackOverflowError|prepareTreeImpl"
```

### 3.8 release 包内容检查（防调试代码泄漏）

```bash
# 解出所有 dex，按 UTF-8 逐段搜字符串
unzip -o app-release.apk 'classes*.dex' -d out/
grep -a "调试页里的一句话" out/classes*.dex
```

GIF 助手实测：release 里三条调试字符串命中 0 / 0 / 0，debug 对照命中 1 / 1 / 1（证明搜索有效）。

---

## 4. 验收矩阵（按组合跑，不要只跑一种）

### 4.1 主题 × 明暗

| 组合 | 必看 |
|---|---|
| 静态浅色 | 页面/卡片层次、说明文字对比度 |
| 静态深色 | 是否有亮色带（`pageColor` 误用）、卡片是否可见 |
| Monet 浅色 | 卡片是否与页面糊成一片、选中项是否主色 |
| Monet 深色 | 同上 + 滑块/开关可见性 |
| AMOLED（深色） | 页面纯黑、卡片近黑仍有层次 |
| 玻璃关闭（任一配色） | 所有入口是否仍然可见 |

### 4.2 尺寸 × 姿态

| 尺寸 | 必看 |
|---|---|
| 竖屏手机（360~420dp） | 底栏、内容底部留白、大字 |
| 横屏手机（~900dp） | Rail 折叠态（顶部胶囊）、内容满宽、无让位 |
| 1200dp 横屏 | Rail 展开、让位 262dp + 安全区、录制层满宽 |
| 1200dp 竖屏 | 同上（注意此时横向安全区为 0，容易漏） |
| 分屏 / 自由窗口 | `containerSize` 变化后布局正确 |
| 字体 1.3× / 2.0× | 无截断、无重叠 |

### 4.3 交互

| 项 | 必看 |
|---|---|
| 一级页切 tab | 指示器与页面同步、无闪 |
| 进出二级页 | 底栏被盖住/揭开（不是"闪没"） |
| 返回键 / 手势返回 | tab≠0 回 tab0；tab=0 直接退出（无确认框） |
| 弹层开合 | 位置正确、可关闭、关闭后状态清理 |
| 下拉 | 弹层有背景模糊、无硬边 |
| 长列表滚动 | 顶栏 band 跟随手指、滚动到边界的触觉 |
| 生成中取消 | 按钮文案变化、可取消 |

### 4.4 页面清单

对每个页面建一行（模板见 [15](15-new-project-migration-template.md)）：

| 页面 | 容器 | 顶栏 | 底栏/Rail | 弹层 | 玻璃 | 主题×4 | 尺寸×4 | 真机 |
|---|---|---|---|---|---|---|---|---|
| … | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |

---

## 5. 状态类测试的特殊困难

| 场景 | 困难 | 做法 |
|---|---|---|
| 进度中间态 | 任务太快（< 1s），按钮来不及进"取消生成 · x%" | 用一个耗时 > 3s 的素材；或临时把任务放慢 |
| 弹层动画 | 截图抓不到中间帧 | 录屏 + `animator_duration_scale=10` |
| 预测性返回 | 需要手势，模拟器触控不可靠 | 真机；或 `adb shell input swipe` + 录屏 |
| 进程死亡恢复 | 需要 `am kill` | `adb shell am kill <pkg>` 后从最近任务恢复 |
| 大圆角安全区 | 模拟器圆角为 0 | 真机（圆角 ≥ 60dp 的机型） |
| 手势导航条遮挡 | 模拟器无手势条 | 真机 + 手势导航 |

---

## 6. 自动化程度建议

| 项 | 能否自动化 | 说明 |
|---|---|---|
| 构建 | ✅ | CI `assembleDebug` + `assembleRelease` |
| Lint / 编译告警 | ✅ | 但不要因为"没告警"就认为 Glass 对 |
| 截图对比 | 🟡 | 版面可对比，玻璃像素在不同 GPU 上不可比 |
| 单元测试 | 🟡 | 阈值函数（`shouldShowSplitPane`）、颜色映射、`steps` 计算可单测 |
| UI 测试 | 🟡 | Compose UI test 能验"元素存在/可点"，验不了玻璃 |
| 真机 | ❌ 必须人工 | 玻璃观感、手感、圆角安全区 |

> GIF 助手**没有测试源集**（`src/**/test`、`androidTest` 都不存在），
> 全部验证靠构建 + 模拟器截图 + 像素测量。新项目至少应给纯函数加单测。

### 6.1 值得单测的三类纯函数

```kotlin
// 1. 断点
fun isWide(widthDp: Float, heightDp: Float) =
    widthDp >= 840f || (widthDp >= 600f && heightDp / widthDp < 1.2f)

// 2. 让位
fun railInset(expanded: Boolean, progress: Float) =
    if (expanded) (250f + 12f) * progress else 0f

// 3. 圆角安全区
fun safeBottomGap(corner: Float, sideGap: Float, navBottom: Float, min: Float = 12f): Float {
    if (corner <= sideGap) return min
    val disc = 2f * corner * sideGap - sideGap * sideGap
    if (disc <= 0f) return min
    return (corner - sqrt(disc) - navBottom).coerceAtLeast(min)
}
```

---

## 7. 回归清单（每次改 UI 都跑）

```text
[ ] ./gradlew assembleDebug 通过
[ ] ./gradlew assembleRelease 通过（R8 不报 missing class）
[ ] 四种配色截图
[ ] 四种宽度截图（竖屏/横屏900/1200横/1200竖）
[ ] 玻璃开/关各一遍
[ ] 进出一级页与二级页各 3 次（看底栏行为）
[ ] 打开所有弹层（Dialog / Sheet / Dropdown）
[ ] 滚到列表顶/底（看 band 与触觉）
[ ] 返回键：tab 0 直接退出
[ ] logcat 无 Fatal signal / StackOverflowError / prepareTreeImpl
[ ] release dex 里没有调试字符串
```

---

## 8. 性能验收（Glass 特有）

| 指标 | 怎么量 | 参考 |
|---|---|---|
| 掉帧 | `adb shell dumpsys gfxinfo <pkg> framestats` | 滚动时掉帧率 |
| 录制层数量 | 代码审计 | 同屏 ≤ 2 层整屏录制 |
| 分页预组合 | `beyondViewportPageCount` | 每页一个录制层时取 1（不要照抄官方的 4） |
| 首帧 | 冷启动录屏 | 首帧不应有"卡片压住头部" |
| 发热 | 真机长滚 | 🔴 GIF 助手未测 |

---

## 9. 回滚验证

每次迁移都准备一份回滚方案，并**验证它可用**：

| 层级 | 回滚方式 | 验证 |
|---|---|---|
| 单页 | 保留旧页面文件，gate 一个开关 | 开关切换后旧页面能跑 |
| 主题 | 保留旧 `Theme` 分支 | 切回旧主题不崩 |
| 玻璃 | `LocalGlassEnabled = false` 全局关 | 关掉后所有页面可用、无空白 |
| 依赖 | 记下升级前的版本/commit | 回退后能构建 |

> GIF 助手的回滚方式：`scripts/setup-miuix.sh` 可重建 MIUIX 源码与补丁；
> `LocalGlassEnabled` 一个开关可关掉全部玻璃。

---

## 10. 交付物

一次完整适配的验收应产出：

1. 修改清单（文件 + 一句话）。
2. 页面/组件覆盖表（含分母与排除理由）。
3. 四种配色 × 四种宽度的截图。
4. 真机验收记录（机型、Android 版本、GPU、结论、截图）。
5. 未完成项与已知问题。
6. 回滚方式。
7. 成熟度评级（[13](13-project-adaptation-workflow.md) §7）。
