# 02 · MIUIX 版本与依赖选择

> 结论先行：**不要照抄任何项目的版本号。** 每次新项目适配，都要重新确认"当时"的版本、
> 发布渠道和兼容要求。本篇给出**确认方法 + 决策表 + 检查表**，而不是一组写死的坐标。

---

## 1. 版本事实（本库证据基线，仅供对照）

以下取自本机 MIUIX 源码 `miuix/`，HEAD = `3177a684622a774276c49f52489a43d0d0ed123d`（2026-11-18）。
**这是"我们验证过的那一版"，不是"推荐给新项目的版本"。**

| 项 | 值 | 来源 |
|---|---|---|
| library 版本 | `0.9.5`（本地无 `v0.9.5` tag，属开发线） | `miuix/build-logic/src/main/kotlin/BuildConfig.kt:5` |
| group | `top.yukonga.miuix.kmp` | 同上 `:6` |
| Kotlin | `2.4.21` | `miuix/gradle/libs.versions.toml:3` |
| AGP | `9.5.1` | `miuix/gradle/libs.versions.toml:2` |
| Compose Multiplatform | `1.12.2` | `miuix/gradle/libs.versions.toml:10` |
| compileSdk | `37` + `minorApiLevel 2` | `BuildConfig.kt:11-12` |
| default minSdk | `24` | `BuildConfig.kt:14` |
| buildTools | `37.0.0` | `BuildConfig.kt:15` |
| JDK | `21` | `BuildConfig.kt:16` |
| 版本号生成 | 无参数 → `0.9.5-<hash>-SNAPSHOT`；`-Prelease` → `0.9.5`；`-Pbeta` → `0.9.5-betaN` | `miuix/build-logic/src/main/kotlin/module.publication.gradle.kts:12-33` |

上表是 `3177a684`（2026-11-18）的历史快照，**当前基线不是这一版**——
现用基线见 `AGENTS.md` 的「构建环境的两个坑」第 1 条。

参考工程 GIF 助手（1.12）的实际组合 📌：

| 项 | 值 | 来源 |
|---|---|---|
| AGP / Kotlin / CMP | `9.5.1` / `2.4.21` / `1.13.0-rc01` | `build.gradle.kts:4-7` |
| compileSdk | `release(37) { minorApiLevel = 2 }` | `:56-60` |
| minSdk / targetSdk | `33` / `36` | `:65-66` |
| JDK | 21（`JvmTarget.JVM_21`） | `:14` |
| 导航 | `miuix-nav` 的 `NavDisplay`（**不用 androidx.navigation**） | `.../App.kt:72-75` |

> 为什么 GIF 助手 minSdk 是 33 而不是 24：`miuix-blur` 的 minSdk 就是 33，
> 而 OS4 玻璃（`drawBackdrop` / texture blur）依赖 API 33 的 `RuntimeShader`。
> 见 §3.1。

---

## 2. 模块地图：谁依赖谁

📌 全部来自各 `miuix-*/build.gradle.kts` 的 `api(...)` / `implementation(...)`。

```
miuix-core            （平台工具、MiuixIcons 根对象）
   ▲ api
miuix-ui ── api ──> miuix-squircle ── api ──> miuix-material
   ▲ api                       ▲ api
   │                           │
miuix-preference          miuix-blur ── api ──> miuix-material
   │                           ▲ api
   │                           │
   └────────────> miuix-glass ─┘── api ──> miuix-ui
                       ▲
miuix-glass-icons ── api ──> miuix-core
miuix-icons      ── api ──> miuix-core
miuix-nav ── implementation ──> miuix-squircle, navigationevent, kotlinx-serialization
miuix-window （仅 desktop，无 Android target）
```

可直接读出的重要事实：

- `miuix-ui` 已经 `api(miuix-squircle)`，所以**再单独声明 `miuix-squircle` 是冗余的**。
  GIF 助手就多写了一条（`build.gradle.kts:35`），而且源码里没有一处 `squircle*` 调用
  （全项目 grep 零命中），属于"注释说有、代码没有"的文档漂移 🏠。
- `miuix-glass` 已经 `api(miuix-blur)`、`api(miuix-material)`、`api(miuix-ui)`，
  所以**只引 `miuix-glass` 就能拿到 `GlassDefaults`、`Backdrop`、`MiuixTheme`**。
  GIF 助手仍显式声明 `miuix-material`（`:31`），属"显式优于隐式"的工程选择，不是必须。
- `miuix-blur` 的 minSdk = **33**，其它库模块 = **24**。这是整套体系里唯一的 SDK 硬门槛。
- `miuix-nav` 是**自包含导航 runtime**，不依赖 androidx.navigation3；用它可以拿到 `NavDisplay`、
  `NavDisplayEffects`（圆角裁剪/dim）和预测性返回。用 androidx.navigation 也能配 MIUIX 的组件，
  但拿不到 `NavDisplayEffects` 那套转场（见 [05](05-navigation-and-adaptive.md) §2.2）。

---

## 3. 依赖决策表

> 原则：**按需引入，不追求"全家桶"。** 下表的"必要性"是相对"要做 OS4 + Glass 的 Compose 应用"而言。

| 模块 | 作用 | 必要性 | minSdk | 什么时候可以省 |
|---|---|---|---|---|
| `miuix-core` | `platform()`、圆角、对话框属性、`MiuixIcons` 根 | 传递依赖，不用手写 | 24 | 不用手写 |
| `miuix-ui` | 基础组件：Scaffold / TopAppBar / Card / Button / TextField / Theme / Slider / Switch | **必选** | 24 | 不能省 |
| `miuix-preference` | 设置页行组件：`SwitchPreference`、`SliderPreference`、`ArrowPreference`、`RadioButtonPreference`、各种 Dropdown | **有设置页就必选** | 24 | 纯展示类应用可省 |
| `miuix-icons` | `extended` 图标族（156 个） | 可选 | 24 | 只用 Glass 族时可省；但要注意 Glass 族覆盖面较窄 |
| `miuix-glass-icons` | `MiuixIcons.Glass.*`（512 个，含 `Light` 细线档） | **做 Glass 就必选** | 24 | 不做 Glass 可省 |
| `miuix-material` | `GlassDefaults` / `GlassStyle` / `GlassMaterial` / `isRenderEffectSupported` / `isRuntimeShaderSupported` / `RuntimeShader` | **做 Glass 就必选**（`miuix-glass` 已传递） | 24 | 显式声明只是可读性更好 |
| `miuix-blur` | `Backdrop` / `rememberLayerBackdrop` / `layerBackdrop` / `drawBackdrop` / `textureBlur` | **做 Glass 就必选**（`miuix-glass` 已传递） | **33** | 不做 Glass 可省；省了就退回 API 24 |
| `miuix-glass` | 全部 Glass 组件（见 [07](07-glass-component-guide.md)） | **做 Glass 就必选** | 24（依赖 blur 33） | 只做 OS4 可省 |
| `miuix-squircle` | 连续曲率圆角 `squircleClip` / `squircleBackground` | 可选 | 24 | 已由 `miuix-ui` 传递；显式声明无收益 |
| `miuix-nav` | `NavDisplay`、`NavBackStack`、`NavTransitions`、预测性返回、`NavDisplayEffects` | **推荐** | 24 | 已有 androidx.navigation 且不想换时可省 |
| `miuix-window` | Desktop 窗口/标题栏（Windows DWM、macOS traffic light） | Android 不需要 | 无 Android target | Android 项目必省 |

### 3.1 关于 minSdk 33 这个门槛

📌 事实：
- `isRenderEffectSupported()` 的 Android 实现**只是 `SDK_INT >= 31`**
  （`miuix-material/src/androidMain/.../RenderEffect.android.kt:10-12`），而且**库里没有任何调用点**。
- 真正的前置判断是 `isRuntimeShaderSupported()` = `SDK_INT >= 33`
  （`RuntimeShader.android.kt:16-18`），`Modifier.drawBackdrop` 在构造 element 前就会判它
  （`miuix-blur/.../DrawBackdropModifier.kt:120`）。
- `miuix-glass` 的 Android minSdk 已从 33 降到 24（提交 `19b5b831`），
  低版本上 `glassPanel` 走 `fallback`（`GlassPanel.kt:46-58`），**不产出模糊**，只剩描边/底色。

因此新项目有三种选择：

| 选择 | minSdk | 结果 | 建议 |
|---|---|---|---|
| A | **33** | 玻璃全功能 | 主路径。GIF 助手就是这个 |
| B | 24~32 | 能编译，`glassPanel` 走 fallback 无模糊，`textureBlur` 无效 | 只做 OS4 时可用；要做 Glass 不建议 |
| C | 33 + 低版本单独 flavor | 分渠道 | 成本高，非必要不做 |

> 如果新项目必须支持 < 33：**先完成 OS4 基础适配（L0→L3），把 Glass 作为 L4+ 的可选层**，
> 并把 `LocalGlassEnabled` 的默认值设为 `false`。不要用自绘渐变去"模仿模糊"，
> 那是第二套视觉，后续必然与真机不一致（GIF 助手第九轮踩过，第十轮整段删除）。

---

## 4. 三种引入方式

### 4.1 Maven Central（首选）

📌 11 个库模块全部应用了 `module.publication`，配置了 `sonatype` 仓库与
`publishToMavenCentralUsingCentralApi`（`miuix/build-logic/src/main/kotlin/module.publication.gradle.kts:55-70`、`:103`）。

但要注意 📌：仓库 CI（`.github/workflows/publish.yml:48-49`）实际只执行
`publishAllPublicationsToGithubRepository`，**没有调用 Central 发布任务**。
所以"某个模块/某个版本是否已在 Central"必须**现场核实**，不能按源码配置推断。

核实命令（任选）：

```bash
# 直接查 Central 元数据（替换 VERSION）
curl -sI https://repo1.maven.org/maven2/top/yukonga/miuix/kmp/miuix-glass/VERSION/miuix-glass-VERSION.pom
curl -s  https://repo1.maven.org/maven2/top/yukonga/miuix/kmp/miuix-glass/maven-metadata.xml | head -40
```

### 4.2 GitHub Packages

需要凭据；坐标与 Central 相同。适合内部/CI 环境。

### 4.3 composite build（源码引入）

GIF 助手就是这么做的（`settings.gradle.kts:56-63` 的 `includeBuild(miuixDir)`），
原因是当时 `miuix-glass` / `miuix-glass-icons` 没有发布到 Central。
源码引入的好处是能读实现、能临时打补丁；代价是构建慢、要维护一份补丁。

📌 **必踩的坑**：MIUIX 的 `build-logic` 在**配置阶段**调用 git 取版本号，
而 composite build 的子构建工作目录是宿主工程（不是 git 仓库）→ `exit 128`。
修法是给 `miuix/build-logic/src/main/kotlin/BuildConfig.kt` 的 `getGitVersionCode()` 与
`getGitHashShort()` 各加一行 `workingDir = rootDir`（2 处，`1 file changed, 4 insertions(+)`）。

最小可用 `settings.gradle.kts` 片段：

```kotlin
// 与 GIF 助手同构；请按新项目目录调整
val miuixDir = providers.environmentVariable("MIUIX_DIR").orNull
    ?.let { file(it) } ?: file("../miuix")

if (!miuixDir.exists()) {
    error("找不到 MIUIX 源码目录：${miuixDir.absolutePath}；请先 git clone 或设置 MIUIX_DIR")
}
includeBuild(miuixDir)
```

并把源码版本**钉在一个提交**上（GIF 助手用的是 `3177a684`，写入脚本变量），
不要跟 `main` 浮动 —— 上游 `miuix-glass` 一天内可能连着改默认值
（例如 HEAD 当天把 `GlassFloatingActionButtonDefaults.Size` 从 `60.dp` 改成 `56.dp`）。

---

## 5. 版本策略：稳定 / 预发布 / 主干怎么区分

📌 事实：MIUIX 在 README 里直接声明 **"This library is experimental. APIs may change without notice."**
（`miuix/README.md:5`）。源码里**没有**给 Glass 组件加 `@Experimental*` opt-in 注解
（唯一的 opt-in 是 `ExperimentalScrollBarApi`）。所以"稳定"只能靠**版本线与时间**判断，不能靠注解。

判定表：

| 形态 | 如何识别 | 能否用于生产 | 风险 |
|---|---|---|---|
| 正式版 | `maven-metadata.xml` 里的 release 版本；有对应 git tag | 可以 | 低 |
| Beta / RC | 版本号带 `-betaNN` / `-rcNN`；`-Pbeta` 构建产物 | 谨慎，建议先跑完 [11](11-testing-and-acceptance.md) | 中：默认值可能再改 |
| 主干未发布 | `BuildConfig.LIBRARY_VERSION` 有值但无 tag，或用了 `-SNAPSHOT` | 不建议 | 高：API 随时变 |
| 本地临时补丁 | `git status` 有改动 / 有 `git apply` 的 patch | 只能自用 | 高：升级即冲突 |

本库基线 `3177a684` 属于**"主干未发布"**（本地 tag 最新是 `v0.9.4`，且仓库是 shallow clone）。
这就是为什么本库反复强调"不要把 GIF 助手的版本固化成默认版本"。

### 5.1 记录基线的方式（每个项目都要做）

在项目里留一份 `docs/miuix-baseline.md`，至少写四行：

```text
MIUIX commit:  <git rev-parse HEAD>
MIUIX version: <BuildConfig.LIBRARY_VERSION>
引入方式:      composite build / Maven Central <version>
本地补丁:      无 / <patch 文件名>（用途一句话）
```

---

## 6. 版本变更检查表（升级前逐项过）

按 GIF 助手真实踩坑顺序排列。每条都注明"不改会怎样"。

| # | 检查项 | 怎么查 | 不改的后果 |
|---|---|---|---|
| 1 | **compileSdk 要求** | `BuildConfig.kt` 的 `COMPILE_SDK` / `COMPILE_SDK_MINOR` | AGP 配置期直接失败：`requires ... compile against version 37.2 or later`（真实报错，见 [12](12-known-issues-and-solutions.md) C41） |
| 2 | **`GlassTopAppBar` 默认值** | diff `GlassTopAppBar.kt` 的 `bandOverhang`、`buttonSize`、`BandBrush` | 观感变了但代码没动，无法归因 |
| 3 | **`GlassNavigationBar` / `Rail` 默认值** | diff 两个文件的 `Height`、`Margin`、`ExpandedWidth`、`CollapsedHeight` | 内容让位算错（GIF 助手镜像了它的 floatingMargin 公式，见 [05](05-navigation-and-adaptive.md) §4.3） |
| 4 | **`GlassDefaults.Style`** | `miuix-material/.../GlassDefaults.kt:35` | 玻璃厚度/高光整批变化 |
| 5 | **`isRuntimeShaderSupported` 判定** | 是否仍为 SDK≥33 | 低版本设备行为变化 |
| 6 | **玻璃图标改名** | `git log --diff-filter=R --oneline -20 -- miuix-glass-icons` | 编译失败（`Glass.Folder` → `FolderOutline`，15 组改名，见 [17](17-source-reference-index.md) §4） |
| 7 | **`renderInRootScaffold` 默认值** | `GlassDialog.kt:55`、`GlassBottomSheet.kt:59`、`GlassDropdownPreference.kt:73` | 弹层宿主位置变化 → 采样坐标错 |
| 8 | **新增组件** | `git log --oneline -30 -- miuix-glass` | 可能已有官方组件替代你的自绘实现（例如 `GlassSegmentedTabRow` 出现前大家只能自绘） |
| 9 | **`build-logic` 是否仍调 git** | `grep -n "workingDir" miuix/build-logic/.../BuildConfig.kt` | composite build 直接 `exit 128` |
| 10 | **发布渠道** | 见 §4.1 的 curl | 依赖解析失败 |

---

## 7. 依赖冲突与常见构建问题

| 现象 | 根因 | 处置 |
|---|---|---|
| `Duplicate class ... androidx.compose.material3.*` | 同时引入 material3 与 MIUIX | 移除 material3（GIF 助手硬约束 7：全项目 grep `androidx.compose.material` 零命中） |
| Compose 版本不一致告警 | CMP 版本与 MIUIX 编译时用的 CMP 不同 | 让 CMP 版本 ≥ MIUIX 的 `libs.versions.toml` 值 |
| `NoClassDefFoundError: android.graphics.RuntimeShader` | 设备 < 33 且直接调了 AGSL 路径 | 所有 AGSL 调用前判 `isRuntimeShaderSupported()` |
| JVM target 不一致 | MIUIX 用 JDK 21 | 项目也设 `JvmTarget.JVM_21` |
| `@Composable` 报错 / 找不到 compose 编译器 | Kotlin 2.0+ 必须单独应用 `org.jetbrains.kotlin.plugin.compose`（版本与 Kotlin 一致） | 加插件；GIF 助手：`build.gradle.kts:6` |
| `local.properties` 缺失时构建失败 | 只读了 `local.properties` | 同时支持 `ANDROID_HOME`（GIF 助手已验证 ✅） |
| KMP 工程 `commonMain` 编译失败 | 把 Android 专属 API 写进 common | 全部实现放 `androidMain`（GIF 助手做法），`commonMain` 只声明依赖 |

---

## 8. GIF 助手的完整依赖清单（作为参考，不是模板）

📌 来源 `build.gradle.kts:17-48`。

```kotlin
commonMain.dependencies {
    implementation("org.jetbrains.compose.runtime:runtime:1.12.2")
    implementation("org.jetbrains.compose.foundation:foundation:1.12.2")
    implementation("org.jetbrains.compose.ui:ui:1.12.2")
    implementation("androidx.navigationevent:navigationevent-compose:1.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.13.0")

    implementation("top.yukonga.miuix.kmp:miuix-ui")
    implementation("top.yukonga.miuix.kmp:miuix-preference")
    implementation("top.yukonga.miuix.kmp:miuix-icons")
    implementation("top.yukonga.miuix.kmp:miuix-material")
    implementation("top.yukonga.miuix.kmp:miuix-squircle")   // 冗余：miuix-ui 已 api 它
    implementation("top.yukonga.miuix.kmp:miuix-glass")
    implementation("top.yukonga.miuix.kmp:miuix-glass-icons")
    implementation("top.yukonga.miuix.kmp:miuix-nav")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1") // 反射隐藏 API
}
androidMain.dependencies {
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
```

其中 `hiddenapibypass` 只为反射 `ApplicationInfo.setEnableOnBackInvokedCallback`
（预测性返回开关，见 `.../GifApplication.kt:27-48`）——
**新项目如果没有"让用户切换返回动画"这种设置，就不需要它**。
正确做法是 `AndroidManifest.xml` 里 `android:enableOnBackInvokedCallback="true"` 即可。

---

## 9. 参考

- 官方接入文档：`miuix/docs/guide/getting-started.md`
- 官方 blur 门槛：`miuix/docs/guide/blur.md:41-46`
- 官方 glass 说明（含 minSdk 24 + blur 33 的 fallback 关系）：`miuix/docs/guide/glass.md:6-8`
- 官方版本声明：`miuix/README.md:5`
- 发布配置：`miuix/build-logic/src/main/kotlin/module.publication.gradle.kts`
- CI：`miuix/.github/workflows/publish.yml:48-49`
- 本库案例：`12-known-issues-and-solutions.md` C41（compileSdk）、C42（composite build 补丁）
