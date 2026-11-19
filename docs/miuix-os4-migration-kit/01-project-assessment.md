# 01 · 新项目扫描与兼容性评估

> 本篇只做**判断**，不改代码。目标是产出两份东西：**项目基线报告**和**可行性结论**。
> 填写模板见 [14-new-project-audit-template.md](14-new-project-audit-template.md)。

---

## 1. 扫描什么（9 类，缺一不可）

| # | 类别 | 必读文件 / 命令 | 要得到的事实 |
|---|---|---|---|
| 1 | 技术架构 | `build.gradle(.kts)`、`settings.gradle(.kts)`、`gradle.properties`、`gradle/libs.versions.toml` | 语言、Compose 类型、Kotlin/AGP/CMP 版本、min/compile/targetSdk、模块数 |
| 2 | 依赖 | 同上 + `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` | UI 相关依赖、是否存在 material3 / 其它设计体系 |
| 3 | 页面 | `src/**/ui/**`、`**/screens/**`、`**/pages/**`、导航图 | 页面清单、每页的容器与骨架 |
| 4 | 组件 | grep 组件名（见 §5） | 每类控件的实际用法与数量 |
| 5 | 导航 | `NavHost` / `NavController` / 自建路由 / Fragment 栈 | 导航框架、返回语义、转场实现 |
| 6 | 主题 | `Theme.kt`、`Color.kt`、`Type.kt`、`res/values/themes.xml`、`colors.xml` | 颜色来源、深浅色策略、动态取色、字体、圆角 |
| 7 | 业务组件 | 自绘 `@Composable` 清单 | 哪些必须保留、哪些能被官方组件替代 |
| 8 | 兼容性 | `minSdk`、`AndroidManifest`、`compileSdk` | 是否满足 MIUIX 的 SDK 门槛（见 §4） |
| 9 | 构建能力 | 能否 `assembleDebug`；`local.properties` / `ANDROID_HOME`；JDK 版本 | 是否有构建阻断 |

**纪律**：以**当前工作区源码**为准。旧审计报告、README、CHANGELOG 只能当线索 ——
GIF 助手的 `AGENTS.md` 就出现过三次文档漂移（`SegmentedControl` 从未存在、
`squircleClip` 注释说有代码没有、`rememberPageBackdrop` 注释写 `pageColor()` 实际是 `pageBaseColor()`）。

---

## 2. 兼容性判定决策树

按顺序回答，**任一"否"就停在这一步**，不要带病进入下一阶段。

```
Q1 是 Kotlin 吗？
├─ 否（纯 Java / 其它 JVM 语言）
│   └─ 能否引入 Kotlin？能 → 走 Q2；不能 → 停：MIUIX 是 Kotlin/Compose 库，无法接入
└─ 是 → Q2

Q2 有 Compose 吗？
├─ 否（XML / View）
│   ├─ 全量迁移 UI 到 Compose 成本可接受？
│   │   ├─ 可接受 → 走 Q3（需先做 Compose 迁移，见 §3.3）
│   │   └─ 不可接受 → 停：MIUIX 无 View 版组件；可考虑混合（ComposeView 逐屏替换），
│   │                  但玻璃层无法跨 View/Compose 边界采样，见 §3.4
└─ 是 → Q3

Q3 是 Jetpack Compose 还是 Compose Multiplatform？
├─ CMP（KMP）→ 注意：MIUIX 的 Android 变体可用；非 Android target 不可用
└─ Jetpack Compose → 直接用（MIUIX 是 KMP 库，Android 端等价）

Q4 minSdk 是多少？
├─ ≥ 33 → 玻璃全功能可用（L4+ 可做）
├─ 24~32 → 只做 OS4 基础（L1–L3）；Glass 会 fallback 无模糊
└─ < 24 → 停：MIUIX 各模块 minSdk = 24（miuix-blur = 33）

Q5 Kotlin / AGP / CMP 版本是否 ≥ MIUIX 要求？
├─ 是 → Q6
└─ 否 → 先升版本（这是前置条件，不是"顺手改改"）

Q6 当前导航框架能否容忍替换或共存？
├─ 已是 miuix-nav → 最省事
├─ androidx.navigation → 可共存；但拿不到 NavDisplayEffects 的圆角裁剪转场
└─ 自建路由 → 可共存；需自行实现返回语义（见 05 §2.3）

Q7 是否有 material3 / 其它设计体系？
├─ 有 → 必须先决策：共存会打架（GIF 助手硬约束 7：不引入 material3）
└─ 无 → 继续

Q8 是否存在影响 UI 的第三方库（图表、富文本、地图、相机预览）？
├─ 有 → 逐项判断：能否包在 MIUIX 容器里？是否需要保留自绘？
└─ 无 → 可进入阶段 1
```

---

## 3. 技术架构可行性矩阵

| 技术栈 | 能直接接入 MIUIX | 需要做什么 | 玻璃（L4+）可用性 | 结论 |
|---|---|---|---|---|
| **Jetpack Compose** | ✅ 能 | 加依赖、换 Theme、逐页替换组件 | ✅ 可用（API 33+） | **最佳路径**，GIF 助手即此类（CMP 变体） |
| **Compose Multiplatform（Android target）** | ✅ 能 | 同上；注意 `commonMain` 不要放 Android 专属 API | ✅ 可用 | 推荐（GIF 助手做法：实现全放 `androidMain`） |
| **CMP（Desktop / iOS / Web）** | ❌ 不能 | MIUIX 的 `miuix-glass` / `blur` 只有 Android + Skiko 变体；Glass 语义不同 | ❌ | 不要假设"CMP 就是同一套" |
| **XML / View（传统）** | ❌ 不能直接 | 逐屏 `ComposeView` 迁移；或整体重写 | ❌ 跨边界无法采样 | **分阶段迁移**，玻璃层放最后 |
| **混合（部分 Compose）** | ⚠️ 部分 | 已 Compose 的页面可接；View 页面不行 | ⚠️ 仅限 Compose 子树内 | 可行但边界要画清楚 |
| **Flutter** | ❌ 不能 | 无 MIUIX 组件；只能自绘模仿 | ❌ | **不建议为了 MIUIX 重写整个应用** |
| **React Native** | ❌ 不能 | 同上 | ❌ | 同上 |
| **Java-only（无 Kotlin）** | ❌ 不能 | 先引入 Kotlin + Compose | ❌ | 成本极高 |
| **WebView 壳** | ❌ 不能 | 无意义 | ❌ | 不适用 |

> 矩阵的判定依据：MIUIX 是 **Kotlin + Compose** 的 UI 库，所有组件都是 `@Composable`。
> Flutter / RN 的渲染树与 Compose 无关，**不可能直接调用**；XML/View 只能在 Compose 子树内
> 用 `AndroidView` 互操作，而玻璃采样依赖 Compose 的 `LayoutCoordinates`，
> 跨到 View 层就没有坐标链路（见 [06](06-glass-architecture.md) §3.4）。

---

## 4. 前置条件清单（不满足则先解决，别硬上）

| 条件 | 要求 | 检查 |
|---|---|---|
| 语言 | Kotlin | `build.gradle.kts` 的 `org.jetbrains.kotlin.*` |
| UI | Compose（Jetpack 或 CMP Android） | `buildFeatures.compose = true` 或 `org.jetbrains.compose` 插件 |
| minSdk | ≥ 24（OS4）/ ≥ 33（Glass 全功能） | `defaultConfig.minSdk` |
| compileSdk | ≥ MIUIX 的 `COMPILE_SDK.MINOR`（当前 37.2） | `BuildConfig.kt`；AGP 9 语法见 [02](02-version-and-dependencies.md) §6 |
| Kotlin | ≥ MIUIX 的 Kotlin 版本（当前 2.4.21） | `libs.versions.toml` |
| CMP（若用） | ≥ MIUIX 的 CMP（当前 1.12.2） | 同上 |
| JDK | 21 | `compileOptions` / `kotlin.jvmTarget` |
| 构建 | 能产出 debug APK | `./gradlew :app:assembleDebug` |

---

## 5. UI 组件识别：grep 清单

扫描时直接跑这几组（大小写不敏感），把命中数记进基线报告。命中数为 0 的类别可以排除。

```bash
# 页面容器与骨架
grep -rn "Scaffold(" --include=*.kt .
# 顶栏
grep -rn "TopAppBar(\|CenterAlignedTopAppBar\|LargeTopAppBar" --include=*.kt .
# 导航
grep -rn "NavigationBar(\|NavigationRail(\|BottomNavigation\|NavHost" --include=*.kt .
# 按钮
grep -rn "Button(\|OutlinedButton\|TextButton\|IconButton(" --include=*.kt .
# 卡片/表面
grep -rn "Card(\|Surface(\|ElevatedCard" --include=*.kt .
# 设置类
grep -rn "Switch(\|Checkbox(\|RadioButton(\|Slider(\|DropdownMenu" --include=*.kt .
# 弹层
grep -rn "Dialog(\|ModalBottomSheet\|AlertDialog\|Popup(" --include=*.kt .
# 输入
grep -rn "TextField(\|OutlinedTextField\|BasicTextField" --include=*.kt .
# 进度
grep -rn "ProgressIndicator\|LinearProgressIndicator\|CircularProgressIndicator" --include=*.kt .
# 主题与颜色
grep -rn "MaterialTheme\|colorScheme\|dynamicColor\|isSystemInDarkTheme" --include=*.kt .
# 危险：其它设计体系
grep -rn "androidx.compose.material3\." --include=*.kt .
```

每类都记三件事：**命中数**、**代表文件**、**是否有自绘替代**。

---

## 6. 页面识别：每页要记录的字段

对每个页面（或导航目的地）建一行：

| 字段 | 说明 |
|---|---|
| 路由/入口 | Route 类名或 NavHost route |
| 文件 | `路径:行号` |
| 容器 | Scaffold / Column / 自建 |
| 顶栏 | 有没有、是否大标题、是否滚动折叠 |
| 底栏/侧栏 | 有没有、是否共享 |
| 列表类型 | LazyColumn / LazyVerticalGrid / 普通 Column |
| 弹层 | Dialog / BottomSheet / Dropdown / Popup 各自位置 |
| 背景 | 纯色 / 渐变 / 图片 / 视频 |
| 是否一级页 | 决定导航与 backdrop 归属 |
| 是否适合 Glass | 见 [07](07-glass-component-guide.md) §8 |

---

## 7. 主题识别：必须在迁移前定下来的 6 件事

1. **颜色从哪来**：`MaterialTheme.colorScheme` / 自定义 `AppColors` / 散落的 `Color(0xFF...)`。
2. **深浅色策略**：跟随系统 / 手动 / 两者都有。
3. **动态取色**：是否用 Material You（`dynamicDarkColorScheme`）。MIUIX 对应 `ColorSchemeMode.Monet*`。
4. **字体**：是否有自定义 `Typography`；字号是否有语义命名（title/body/caption）。
5. **圆角**：是否集中在 shape 里；是否有硬编码 `RoundedCornerShape(8.dp)`。
6. **是否已有一套"品牌色"**：迁到 MIUIX 后品牌色往哪个角色映射（primary？secondary？还是保留为自定义色）。

> 这一节决定 [03](03-os4-theme-system.md) 的工作量。**颜色散写越多，迁移成本越高**，
> 因为 MIUIX 的 `ColorScheme` 有 50+ 个语义角色，散写颜色没有语义可映射。

---

## 8. 输出：项目基线报告

必须包含（模板见 [14](14-new-project-audit-template.md)）：

1. 技术架构表（版本、SDK、模块）。
2. 依赖清单 + 冲突判定。
3. 页面清单（§6 的字段）。
4. 组件清单（§5 的命中数与代表文件）。
5. 导航与返回语义现状。
6. 主题与颜色现状（§7 六问的答案）。
7. 自定义组件清单与保留理由。
8. 兼容性结论：**能接 / 需先迁移 / 不能接**，以及阻断条件。
9. 预估阶段：目标 L 级、需要几个阶段、最大风险点。

---

## 9. 常见误判（都是真实踩过的）

| 误判 | 实际 | 出处 |
|---|---|---|
| "能编译 = 玻璃可用" | 低版本 `glassPanel` 走 fallback，无模糊 | `GlassPanel.kt:46-58` |
| "模拟器上发灰 = 代码错了" | 软件渲染不产出模糊/AGSL 像素 | [12](12-known-issues-and-solutions.md) C24、C15 |
| "minSdk 24 就能做 Glass" | `miuix-blur` minSdk 33 | `miuix-blur/build.gradle.kts:32` |
| "example 里的 `rememberBlurBackdrop` 是库 API" | 它只在 `example/shared/.../utils/PageUtils.kt:294` | [06](06-glass-architecture.md) §2 |
| "MIUIX 有 `GlassSurface` 组件" | 实际是工厂 `glassSurface(...)` / `Modifier.glassPanel(...)` | [07](07-glass-component-guide.md) §1 |
| "`isRenderEffectSupported()` 能判断 GPU 能不能画玻璃" | 它只是 `SDK_INT >= 31`，且库里没有调用点 | `RenderEffect.android.kt:10-12` |
| "把导航放进 Row + weight 排一下" | `GlassNavigationRail` 会吃满整行，内容 0 宽 | [12](12-known-issues-and-solutions.md) C28 |
