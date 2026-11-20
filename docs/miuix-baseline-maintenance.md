# Miuix 基线长期维护机制

> 目的：让这个项目在任意一台新电脑 clone 之后，仍然能独立开发、独立升级 Miuix 基线、独立发版。
> 本文是**机制与操作手册**；某一次升级的具体差异分析见
> `docs/miuix-baseline-<旧sha>-to-<新sha>-diff.md`，定制面清单见 `docs/upgrade-prep-customization-inventory.md`。
>
> 适用基线：Miuix `5ff41ba9`（2026-11-20）。本文所有命令都在
> `GifHelper/`（Git Bash / zsh / bash）与 `GifHelper/../miuix/` 两个目录下执行，路径以实际工作区为准。

---

## 0. 结论速览

1. **项目不是 Miuix 的 fork。** 定制代码 100% 在本仓库 `src/`，Miuix 只是以 composite build 引入的**只读源码依赖**。
   这条决定了升级方式：不是 `git merge` 上游，而是**把钉住的提交往前挪一格 + 修少量耦合点**。
2. **基线用 commit 钉，不用版本号。** Miuix 的 `0.9.5-beta01` 之类版本号只表示发布批次，
   同一版本号不存在多次发布；但 `main` 上的观感改动（默认值、模糊管线）会直接影响我们，
   所以判定依据必须是 **commit + 代码差异**。
3. **升级成本的大头不在代码，而在 5 个「吃上游默认值」的文件和 5 个「复制件」。**
   它们逐条登记在本文 §4.2，每次升级按表核对即可，不需要重新审计整个项目。
4. **本地可以随时删。** GitHub `origin` 保存了全部源码、文档与历史；`../miuix` 可由
   `scripts/setup-miuix.sh` 用脚本里钉住的 commit 一键重建；`local.properties` 与签名文件是唯一需要在仓库外另存的东西。
5. **真正无法自动化的只有一种是「真机玻璃观感」。** 本机模拟器是软件渲染（SwiftShader），
   `textureBlur` 不产出像素，玻璃相关的视觉回归必须在真机上做（见 §6.3）。

---

## 1. 当前仓库架构（事实，不是目标态）

| 角色 | 实际位置 | 地址 | 说明 |
| --- | --- | --- | --- |
| **upstream**（上游基线） | `GifHelper/../miuix` 独立 git 仓库 | `https://github.com/compose-miuix-ui/miuix.git` | 只读消费；**我们从不向它提交** |
| **origin**（自己的主仓库） | `GifHelper/` git 仓库 | `https://github.com/Ayang233/gif-toolkit.git` | 唯一需要备份、发布、长期保存的仓库 |
| **local**（开发环境） | 上面两个目录 + JDK 21 + Android SDK 37.2 | — | 可销毁、可重建 |

工作区里还存在 `KernelSU-AVD`、`_archive` 等目录：前者是无关参考项目，后者是本机的升级前 APK / 截图 / 脚本归档，
**都不属于本仓库、不参与构建、不随仓库迁移**。

### 1.1 依赖是怎么进来的

`settings.gradle.kts` 用 **composite build** 引入源码：

```kotlin
val miuixDir = providers.environmentVariable("MIUIX_DIR").orNull?.let { file(it) } ?: file("../miuix")
includeBuild(miuixDir)
```

- 为什么不用 Maven Central：`miuix-glass` / `miuix-glass-icons` **没有发布**到中央仓库（截至 0.9.5 开发线），
  而 OS4 玻璃化必须用它们，所以只能从源码构建。
- 为什么需要打补丁：Miuix 的 `build-logic` 在**配置阶段**执行 `git rev-list/rev-parse` 取版本号，
  而 composite build 下该子构建的工作目录是宿主工程（不是 git 仓库）→ `git` exit 128 → 同步直接失败。
  补丁给这两处调用补 `workingDir = rootDir`（4 行），由 `scripts/setup-miuix.sh` 自动打上。
- 补丁是**唯一一处对官方源码的本地修改**，不提交、不参与版本控制、重新 clone 后由脚本重现。
  → 所以不存在"长期维护一堆 patch"的问题。

### 1.2 基线写在哪（三处，必须同时改）

| 位置 | 内容 | 作用 |
| --- | --- | --- |
| `scripts/setup-miuix.sh` `PINNED_REF` | `5ff41ba9` | **权威来源**：重建环境时切到这个 commit |
| `AGENTS.md`「构建环境的两个坑」第 1 条 | 基线 commit + 上次基线 + 升级注意事项 | 给人/AI 的上下文 |
| `docs/miuix-baseline-<旧>-to-<新>-diff.md` | 每次升级的差异报告 | 审计与回溯依据 |

> 历史教训：本轮升级前，`AGENTS.md` 写 `3177a684`、脚本 pin `d45ca181`、本地实际 `3177a684`，
> 三处漂移了 14 个提交。**任何一次升级，先把这三处对齐再动手。**

---

## 2. 为什么不需要 fork / 不需要 merge

| 做法 | 是否采用 | 理由 |
| --- | --- | --- |
| Fork Miuix 后改库 | ❌ | 会把"官方更新"和"我们的修改"变成同一条历史，之后每次升级都要解冲突，成本随提交数增长 |
| 把玻璃模块复制进本仓库 | ❌ | 复制件无法获得上游 blur 管线优化与 bug 修复；本项目只复制了 5 个很小的辅助件（§4.2 C 表） |
| composite build + 钉 commit + 有限适配 | ✅ | 官方更新免费获得；我们的定制全部留在自己的树里；冲突面被压缩到固定的几张表 |

一句话：**官方代码不进我们的仓库，我们的定制不进官方源码。**

---

## 3. 基线升级标准流程（SOP）

> 建议每次升级完把 `docs/miuix-baseline-<旧>-to-<新>-diff.md` 落盘并在 CHANGELOG 记一行，
> 这样下一次升级只需要读"上一次报告 + 本次差异"，不需要重新理解整个项目。

### Step 1 — 建保护点（5 分钟）

```bash
# 在 GifHelper/ 下
git status --porcelain            # 必须为空，或先提交
git fetch origin --prune --tags
git rev-list --left-right --count origin/main...HEAD   # 期望 "0 0"
git tag -a pre-miuix-upgrade-<当前miuix短sha> -m "升级前保护点：Miuix <sha>，GifHelper <版本>"
cp build/outputs/apk/debug/GifHelper-debug.apk  <仓库外>/GifHelper-<版本>-baseline.apk   # 留一份能跑的旧包
```

判定：**工作区干净 + 本地与远端一致 + 旧 APK 有备份**，三条缺一不可。
本轮的实物记录见 `_archive/baseline-upgrade/UPGRADE-PROTECTION-POINT.md`（属于本机归档，不进仓库）。

### Step 2 — 取上游并确认目标提交（2 分钟）

```bash
cd ../miuix
git fetch origin --prune --tags
git log --oneline HEAD..origin/main         # 当前 pin 到最新 main 之间的提交
git rev-parse --short origin/main           # 目标提交
git diff --stat HEAD..origin/main           # 变更面（文件数/模块）
```

判定：**目标提交必须是 `origin/main` 的实际 head**，不接受"按版本号推断"。
如果上游刚有 in-flight 提交、看起来风险大，可以钉到最近的 tag（`git tag --contains <sha>`），
但那样下次升级要跨过的提交更多 —— 默认跟 main。

### Step 3 — 分类差异（半天）

把 `git diff` 分成四类并写进差异报告：

| 类别 | 含义 | 处理 |
| --- | --- | --- |
| **A 官方原生更新** | 与定制无关的新增/修复/重构 | 直接吸收 |
| **B 本项目定制** | `src/` 里的 OS4/Glass 适配 | 不用动，但要核对是否被 A 影响 |
| **C 冲突** | 官方改了**我们正在依赖的默认值/内部行为/接口** | **人工适配（升级的真正工作量）** |
| **D 无冲突** | 模块零 diff（如本轮的 `miuix-material` / `miuix-icons` / `miuix-nav`） | 无需动作 |

分类方法：先 `git diff --stat` 按模块分桶 → 再对 `miuix-glass/**`、`miuix-blur/**`、`miuix-ui/basic/**`
逐个看公开签名与默认值 → 最后拿本文 §4.2 的耦合表逐条对号。

### Step 4 — 切基线并编译（10 分钟）

```bash
# ../miuix 当前有本地补丁，直接 checkout 会保留补丁（文件未被上游改动时）
git -C ../miuix diff > /tmp/miuix-local-patch.patch     # 先留底
git -C ../miuix checkout <目标sha>
git -C ../miuix status --porcelain                       # 只应剩 build-logic/.../BuildConfig.kt
bash scripts/setup-miuix.sh                              # 幂等：确认 pin、必要时重打补丁

./gradlew :assembleDebug --console=plain
./gradlew :assembleRelease --console=plain               # R8 + lintVital 也要过
```

判定：**debug 与 release 都 `BUILD SUCCESSFUL`**。
编译错误通常只有两类：图标/常量改名、公开签名变化。按报错修，不要为了"过编译"改设计。

### Step 5 — 适配耦合点（本轮实际工作量：1 处）

只改 §4.2 表里被命中的条目，改完在差异报告里记"改了什么、为什么"。
本轮命中 1 条：`ui/PageChrome.kt` 的 `rememberBottomBarClearance()` 里 `28.dp → 20.dp`
（`GlassNavigationBar` 的 ≤16dp 档位变化）。其余命中项都是"需要看，不需要改"。

**不要顺手重构。** 升级的目标是"功能与观感不变"，重构留到独立任务。

### Step 6 — 全量验证（1 小时，模拟器）+ 真机（另计）

```bash
./gradlew :assembleDebug :assembleRelease
adb install -r build/outputs/apk/debug/GifHelper-debug.apk
adb shell am start -n com.ayang.giftoolkit/.MainActivity
adb logcat -c && adb logcat -d -v brief | grep -E "FATAL|SIGSEGV|libhwui"
```

验证矩阵（本项目当前**没有**单元测试/UI 测试，全部靠人工 + 截图对比）：

| 维度 | 配置 | 检查点 |
| --- | --- | --- |
| 配色 | 静态 / Monet × 明 / 暗（4 组） | 页面底色 vs 卡片底色不糊、禁用态可读 |
| 玻璃 | 开 / 关 | 底栏、顶栏、Rail、弹层、GlassCard；关掉后不能塌成通栏白条 |
| 版式 | 竖屏 / 横屏（宽屏 Rail 折叠态） | 内容不被侧栏压住、底部主操作条不贴边、末尾无空带 |
| 页面 | 首页 / 作品 / 设置 / 主题 / 关于 / 许可 / 工具页 | 顶栏折叠、滚动、下拉、Dialog/BottomSheet |
| 包体 | release | 能装能起；`DEBUG_UI` 分支确实被 R8 删掉 |

**自动化可行的部分**：截图对比（同一组路径跑两遍 APK，逐像素 diff）。
本轮的采集与 diff 脚本保存在 `_archive/baseline-upgrade/`，可以直接改造成 `scripts/visual-smoke.*`（见 §7）。
**自动化不可行的部分**：真机玻璃质感、滚动帧率。

### Step 7 — 回填文档（20 分钟）

- `AGENTS.md` 基线 commit + 新增的坑
- `docs/miuix-baseline-<旧>-to-<新>-diff.md`（差异报告）
- `CHANGELOG.md` 一行"Miuix 基线升到 `<sha>`"
- `README.md`（若版本号变了）
- 本文 §4.2 若新增/删除耦合项，同步改

### Step 8 — 提交与发布

```bash
git add -A
git commit -m "升级：Miuix 基线 <旧短sha> -> <新短sha>"
git push origin main
```

发版另按 §6 走。**验证没过不急发版**（`versionCode`/`versionName` 可以下一提交再改）。

### 回滚（任何一步失败）

```bash
git -C ../miuix checkout <旧sha>                 # 退回旧基线
git checkout <保护点 tag>                        # 或 git switch -c fix/xxx
git -C ../miuix diff > /dev/null                 # 确认补丁仍在
.git 之外：随时可以从 pre-miuix-upgrade-* tag 重建
```

---

## 4. 定制成果保护机制

### 4.1 三道防线

1. **约束（人/AI 都遵守）**：`AGENTS.md` 的 15 条硬约束。它们描述的是"为什么这样写"，
   所以升级后不能只看编译过没过，要按约束逐条确认节点关系、配色职责、内边距来源没被破坏。
2. **清单（结构化）**：每次升级产出一份差异报告 + 一份定制面清单（本轮：`docs/upgrade-prep-customization-inventory.md`）。
3. **回归（可对比）**：截图 diff。玻璃关闭时可做到逐像素级；玻璃开启时依赖真机。

### 4.2 基线耦合登记表（每次升级按此表核对，别重新审计全项目）

**U 表 — 依赖上游默认值 / 未公开行为（脆弱点）**

| # | 本项目位置 | 依赖的上游行为 | 上游若改，症状 | 本轮结论 |
| --- | --- | --- | --- | --- |
| U01 | `App.kt` 最外层 `Scaffold(Transparent)` | 该 Scaffold 是 `GlassDialog/GlassBottomSheet/GlassDropdownPreference` 的最外层宿主 | 弹层不渲染 / 退回到页面层 | 未变 |
| U02 | `PageChrome.kt` `rememberBottomBarClearance()` | `GlassNavigationBar` 内部 `floatingMargin`（未导出）：≤16dp→20dp、≥28dp→跟随、其余→12+inset | 内容末尾空带 / 被底栏压住 | **已变：28→20dp，已适配** |
| U03 | `GlassScope.kt` 顶栏玻璃档 | `GlassTopAppBar` 不传参时的默认：blur 半径 80dp、ramp 48/32dp、band 36dp、按钮 44dp | 顶栏观感、模糊带厚度 | 半径/尺寸未变；渐进模糊曲线 0.5→0.25（观感变化，真机确认） |
| U04 | `GlassScope.kt` 顶栏 band 画笔 | 传 `GlassTopAppBarDefaults.bandBrush()`，库靠其**私有类型**判断是否走渐变带 | 顶栏 background 变成一条硬色带 | 未变（仍是私有契约） |
| U05 | `App.kt` 玻璃底栏 / Rail | 不传参的默认：Height 54dp、指示器色、`NAV_HIDE_SCALE` 动画 | 底栏位置/隐藏动画 | 尺寸未变；≤16dp 档 margin 变化（见 U02）；新增横向安全区 padding（横屏需真机看） |
| U06 | `PageChrome.kt` `Os4ListPage` 的 `Scaffold` | 默认 `contentWindowInsets`（`systemBars ∪ displayCutout`） | 全项目顶部/横向留白一起变 | 未变 |
| U07 | `PageChrome.kt` `MiuixScrollBehavior()` | 顶栏折叠/回弹的 spring 与阈值 | 顶栏手感 | 未变 |
| U08 | `ui/Widgets.kt` `Card` / `GlassCard` | `CardDefaults` 的圆角 16dp、`InsideMargin=0`、内容色 | 所有卡片行内边距/圆角 | 未变 |
| U09 | `ui/Widgets.kt` `DropdownRow` | `GlassDropdownPreference` 默认 `renderInRootScaffold=true` + 锚点=行 bounds | 下拉位置/出现时机 | **锚点改为"值+箭头"bounds，需回归** |
| U10 | `screens/LibraryScreen.kt` `GlassBottomSheet` | 默认 `insideMargin(DpSize(24,0))`、`renderInRootScaffold=true`、内容自己让导航条 | 面板位置/被导航条压住 | 未变 |
| U11 | `screens/ToolScreen.kt` `BasicComponent` | `InsideMargin=16dp`、最小高 56dp | 参数行版式 | 未变 |
| U12 | `ui/AnimatedTheme.kt` | 主题装配：外层 `MiuixTheme(controller)` 提供 `LocalColorSchemeMode`，内层 `MiuixTheme(colors=…)` 做逐色过渡 | `MiuixTheme.isDynamicColor` 恒 false → 上游所有"动态取色感知默认值"（`dynamicGlassMaterial`、顶栏按钮 fill）不生效 | **已修**：本轮补了外层 `controller`（3 行），Monet 下玻璃材质 tint 已生效；颜色过渡不变 |
| U13 | `App.kt` 一级页 BackHandler | tab≠0 才拦截，官方 `MainScreenBackHandler` 同款 | 返回行为 | 未变 |

**C 表 — 复制进本仓库的官方/参考代码（不会自动获得上游修复，升级后需人工比对）**

| # | 本项目文件 | 来源 | 风险 |
| --- | --- | --- | --- |
| C01 | `ui/CombinedBackdrop.kt` | miuix example `component/liquid/CombinedBackdrop.kt` | **`implements Backdrop` 接口**：接口加成员就编译失败；语义变化则静默错 |
| C02 | `ui/bg/BgEffect.kt`、`ui/bg/BgEffectShader.kt` | miuix example 的 OS3 动态渐变背景 | 上游改了 shader/API 不会同步；`isRuntimeShaderSupported()` 口径变化会影响退化时机 |
| C03 | `ui/ProgressButton.kt` | InstallerX `MiuixAboutPage` 同源写法 | 与官方 `Button` 几何不同源（16dp/50dp vs 16dp/40dp），属已知债务 |
| C04 | `ui/PageChrome.kt` 的 `rememberPageBackdrop` / `pageContentPadding` / `pageScrollModifiers` | 官方 `utils/PageUtils.kt`、`utils/Overscroll` 的等价实现 | 官方后续优化不会流过来 |
| C05 | `ui/Adaptive.kt` 宽屏阈值 | 官方 `utils/AdaptiveUtils.kt` | 官方改阈值后，本项目横屏形态与官方分叉 |

> 维护建议：C 表**只增不减、每条都要有"来源文件 + 复制理由 + 上游对应提交"**。
> 若某天官方把这些能力公开成 API，优先删复制件改为调用。

### 4.3 明确禁止的做法

- ❌ 直接改 `../miuix` 里的组件源码（除了 `build-logic` 那 4 行补丁）——下次 checkout 就丢，且写不进任何文档。
- ❌ 用官方文件覆盖本项目文件"求新"——会丢掉 OS4/Glass 的定制（安装 8 处配色 / 录制层坐标 / 弹层采样源）。
- ❌ 因为"想让玻璃更好看"而改上游默认值前不记录——那等于把脆弱耦合又加一条。

---

## 5. 本地删除与重新 Clone

### 5.1 删除前必须保留的东西

| 东西 | 位置 | 是否在 GitHub | 备注 |
| --- | --- | --- | --- |
| 源代码 + 文档 + 全部历史 | `GifHelper/` | ✅ 全部（`origin/main`） | 114 个跟踪文件，.git 约 2.7MB |
| 基线 pin | `scripts/setup-miuix.sh` | ✅ | 恢复时自动重建 `../miuix` |
| 签名 keystore | **不在仓库** | ❌ | 目前 release 用 debug 签名；正式分发前必须生成并**在仓库外**备份 |
| `local.properties`（SDK 路径） | 本机 | ❌（被 .gitignore） | 重建时手写一行，或设 `ANDROID_HOME` |
| `../miuix` 源码 | 本机 | 不需要 | 由脚本 clone + checkout pin |
| 旧 APK / 截图 / 审计归档 | `_archive/` | ❌（本机归档） | 想留就复制到 GitHub Release 或网盘 |
| 版本 tag | 仓库内 | ✅（`v1.0`…`v1.10`） | `git tag` 查看；**注意有的 tag 只在本地**，要 `git push --tags` 才上云 |

### 5.2 新电脑从零恢复（约 15 分钟）

```bash
# 1) 环境：JDK 21、Android SDK Platform 37.2（含 build-tools 37.0.0）
git clone https://github.com/Ayang233/gif-toolkit.git GifHelper
cd GifHelper

# 2) 拉 Miuix 源码并钉到基线提交（脚本会打 composite build 必需的补丁）
bash scripts/setup-miuix.sh              # 生成 ../miuix 并 checkout PINNED_REF

# 3) SDK 路径
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties

# 4) 构建
./gradlew :assembleDebug
adb install -r build/outputs/apk/debug/GifHelper-debug.apk
adb shell am start -n com.ayang.giftoolkit/.MainActivity
```

验证恢复是否成功：

- `git -C ../miuix rev-parse --short HEAD` == `scripts/setup-miuix.sh` 里的 `PINNED_REF`
- `git -C ../miuix status --porcelain` 只有 `build-logic/.../BuildConfig.kt`
- `./gradlew :assembleDebug` 成功、能装能起

> ⚠️ 受限网络注意：`git clone github.com` 可能失败而 `api.github.com` 可用。
> 此时按 `AGENTS.md`「构建环境的两个坑」第 3 条的 REST API 方案推送/拉取；拉取也可以直接用 GitHub 的 codeload tarball。
> 另外 `MIUIX_DIR` 环境变量可以指定任意 miuix 源码目录，便于同时保留多个基线做对比。

### 5.3 「clone 下来能不能独立维护」——结论

能。前提是仓库里必须有：pin 脚本（有）、构建说明（有 README/AGENTS）、迁移与耦合文档（有）、
版本与发布约定（有 CHANGELOG/ROADMAP）。缺的只有两件**与仓库无关**的东西：签名 keystore、以及真机测试环境。

---

## 6. 版本维护与发布

### 6.1 版本号约定

- `versionName`：`1.<n>`，**每吸收一次上游基线或有一组对外可见改动就 +1**；纯内部重构/文档不动版本。
- `versionCode`：严格递增整数（当前 12 → 下一个 13）。
- 版本号写在 `build.gradle.kts` 的 `defaultConfig`，关于页/设置页从 `AppInfo` 读，不要在 UI 里硬写。

### 6.2 分支与 tag

推荐长期只有一条线（本项目规模不值得引入 develop/release 分支）：

```
main ──●──●──●──●──●──  ← 每个可发布状态打 tag v1.x
        \        \
         \        └─ baseline/<miuix短sha>（可选：升级试验分支，验证完 squash 回 main）
          └─ feature/<name>（可选：大改动）
```

- 日常直接提交 `main`；升级或大重构若预计失败，开 `baseline/…` 试验，成功后再合。
- 发版打 **附注 tag**：`git tag -a v1.11 -m "..."`，并推到远端 `git push origin v1.11`。
- tag 与 CHANGELOG 一一对应；`pre-miuix-upgrade-*` 这类保护点 tag 只留本地即可（或都推上去，成本很低）。

### 6.3 发布流程

```bash
# 1) 版本号 + CHANGELOG
#    build.gradle.kts: versionCode / versionName
#    CHANGELOG.md:    新版本小节（只写实际改动）

# 2) 构建与冒烟
./gradlew :assembleRelease
adb install -r build/outputs/apk/release/GifHelper-release.apk   # 装 release 验证 R8 后能起
adb logcat -c && adb shell am start -n com.ayang.giftoolkit/.MainActivity

# 3) 提交 + 打 tag + 推
git add -A && git commit -m "<版本>：<一句话>"
git tag -a v1.11 -m "1.11"
git push origin main --follow-tags

# 4) GitHub Release：上传 release APK，正文取 CHANGELOG 对应小节
```

**发布前必须人工确认**：真机玻璃观感（顶栏模糊带、底栏/侧栏、弹层）、滚动是否掉帧、
十个工具各跑一遍、release 包里 `DEBUG_UI` 分支确实不存在。

---

## 7. 自动化方案与成本（提议，本轮不落地）

按"投入产出比"排序。原则：**只自动化"重复且确定性高"的环节**，视觉与观感仍要人看。

| 优先级 | 能力 | 做法 | 成本 | 收益 |
| --- | --- | --- | --- | --- |
| **P0** | 上游基线监测 | `scripts/check-upstream.sh`：`git -C ../miuix fetch && git rev-list --count PINNED..origin/main`，有差异就打印提交标题 | 30 行 / 10 分钟 | 不再靠"想起来才看"；CHANGELOG 不再漏基线 |
| **P1** | 升级前后差异报告 | `scripts/diff-baseline.sh <旧> <新>`：`--stat` 按模块分桶 + 列出改动到的公开 `fun/class` | 60 行 / 30 分钟 | 把 §3 Step 3 的半手工劳动变成一条命令 |
| **P2** | 截图回归 | 本轮的 `capture-v2.ps1` + `diff-shots.py` 改成 `scripts/visual-smoke.ps1`：固定路径 × 4 配色 × 明暗，输出逐像素 diff 与外接图 | 已有雏形，约 2 小时整理 | 玻璃关闭时可判定"零视觉变化"；本轮实测 20 张图只有状态栏时钟不同 |
| **P3** | CI 构建验证 | GitHub Actions：`setup-java 21` + `setup-miuix.sh` + `assembleDebug/Release`；构建缓存 + `../miuix` 缓存 | 2~3 小时 | 每次 push 都能证明"还能构建"；也方便验证"新 clone 能否恢复" |
| **P4** | 真机自动化截图 | 设备云 / 自建设备农场 | 高 | 玻璃路径唯一能自动化的方式，但成本远超当前项目规模 |
| ❌ 不建议 | 自动 merge 上游 / 自动改适配代码 | — | — | 冲突面小但语义强（录制层坐标、默认值依赖），自动改风险高于收益 |
| ❌ 不建议 | 视觉 diff 进 CI 卡合并 | — | — | 模拟器渲染不稳定，噪声大 |

**建议顺序**：P0 → P2 → P3（P1 可选，因为只要 P0 提示"有新提交"，
差异报告本来就要人工读）。

---

## 8. 升级风险清单

| 风险 | 表现 | 触发条件 | 缓解 |
| --- | --- | --- | --- |
| 默认值漂移 | 顶栏高度/模糊带/底栏位置/卡片内边距全变 | 上游改我们"吃默认值"的组件 | U 表逐条核对；升级后固定路径截图 diff |
| blur 管线重写 | 玻璃"糊得不一样"（厚度、渐变、亮度） | `miuix-blur` 大改（本轮 7 个提交） | 只能真机对比；保留旧 APK 做 A/B |
| 内部契约变化 | 弹层不渲染、顶栏硬色带、TEXTURE 采样错位 | 私有 class/类型判断、Scaffold 宿主机制变化 | U01/U04/U09 逐条回归 |
| 工具链抬升 | 配置期直接失败 | 上游抬 `compileSdk`/AGP/Kotlin | 看上游 `gradle/libs.versions.toml`；同步调整（已有 37.2 先例） |
| R8 误删 | release 崩溃/白屏，debug 正常 | 新增反射/序列化类 | 每次升级都装 release 包跑一遍 |
| 模拟器假象 | 模拟器上玻璃不显示/显示异常 | SwiftShader 无 `textureBlur` 输出 | 以真机为准，别为模拟器改实现 |
| 版本号/发布事故 | 装了旧包、tag 打错 | 手改 `versionCode` 忘了改 | §6.3 流程 + 发布前检查清单 |
| 误删/误覆盖 | 定制被官方文件覆盖 | 用"整目录覆盖"方式合入 | 永远不用 `checkout --theirs` 之类粗暴合并；只按 U/C 表逐点适配 |

---

## 9. Git 命令速查（作用 + 执行时机）

| 命令 | 作用 | 时机 |
| --- | --- | --- |
| `git fetch origin --prune --tags` | 拉取远端引用与 tag，清理已删除分支 | 每次开工前 |
| `git rev-list --left-right --count origin/main...HEAD` | 显示 ahead/behind 数量 | 确认本地与远端一致 |
| `git tag -a pre-miuix-upgrade-<sha> -m "…"` | 打升级前保护点（附注 tag，带说明） | 升级动代码前 |
| `git -C ../miuix fetch origin --prune --tags` | 更新上游基线的本地引用 | 检查基线更新时 |
| `git -C ../miuix log --oneline HEAD..origin/main` | 列出上游新增提交 | 决定要不要升级 |
| `git -C ../miuix diff --stat HEAD..origin/main` | 上游变更面 | 评估升级范围 |
| `git -C ../miuix diff HEAD..origin/main -- <path>` | 单文件/单模块差异 | 排查冲突项 |
| `git -C ../miuix checkout <sha>` | 切换基线（本地补丁会保留在未冲突的文件上） | 升级/回滚 |
| `git -C ../miuix diff > patch.diff` | 导出本地补丁留底 | 切基线前 |
| `git switch -c baseline/<sha>` | 开升级试验分支 | 预计有风险时 |
| `git add -A && git commit -m "升级：Miuix 基线 A -> B"` | 落地升级提交 | 验证通过后 |
| `git push origin main` | 推送主仓库 | 验证通过后 |
| `git tag -a v1.x -m "…" && git push origin v1.x` | 发布 tag | 发版时 |
| `git push --tags` | 补推本地 tag（历史上有 tag 只在本地） | 首次迁移/换电脑前 |
| `git checkout -- <path>` | 撤销单文件改动 | 适配改错时 |
| `git reset --hard <tag>` | 回到保护点（**会丢未提交改动**） | 升级失败需要重来 |
| `git clone https://github.com/Ayang233/gif-toolkit.git` | 新环境重建 | 换电脑/误删后 |
| `bash scripts/setup-miuix.sh` | 重建 `../miuix` + 打补丁 + 钉基线 | clone 后第一件事 |

---

## 10. 与本轮升级的关系（结论）

- 本轮（2026-11-20）把基线从 `3177a684` 推进到 `5ff41ba9`，34 个提交、198 个文件；
  **编译零错误**，只有 1 处必须适配（U02 的 20dp）。
- 随后按"不许自造轮子、全部对齐最新基线"的要求做了一轮彻查与修正，产物是两份报告：
  `docs/audit-self-built-wheels.md`（自造轮子清单）与 `docs/audit-baseline-alignment.md`（样式/默认值对齐清单）。
  真正算"自造轮子"并被修掉的有 6 处：
  1. 屏幕圆角自己读 `RoundedCorner` → 改用库的 `rememberNavSystemCornerRadius()`；
  2. 预览舞台手写 `clip(RoundedCornerShape)` → 改用库的 `squircleSurface`；
  3. "抠图颜色"行手抄 `BasicComponent` 的行规格 → 改用 `BasicComponent(endActions=…)`；
  4. `NavDisplayEffects`/`GlassTopAppBar` 重复声明库默认值 → 删掉，吃默认值；
  5. `ProgressButton` / 进度指示器硬编码尺寸 → 改用 `ButtonDefaults` / `ProgressIndicatorDefaults`；
  6. 关于页/顶栏自己判断"软件渲染器"（把 `-gpu host` 也误判）→ 删除，能力判断交给库。
- 另外修掉一条基线对齐的 P0：`AnimatedTheme` 不再提供 `LocalColorSchemeMode`，
  导致 5ff41ba9 的 `dynamicGlassMaterial` 在 Monet 下全部失效（"弹层还是老样式"的材质原因）。
- 模拟器截图矩阵（4 配置 × 5 页面 + 横屏）：非关于页与升级前**逐像素一致**（差异仅状态栏时钟）；
  Monet 下因新材质 tint 与关于页因 `textureBlur` 生效而有预期的视觉变化。
- 长期机制的三件套已经就位：**pin 脚本 + 差异报告 + 耦合登记表**（本轮再加三份审计报告：
  `audit-self-built-wheels.md`、`audit-baseline-alignment.md`、`audit-miuix-example-parity.md`）。

### 10.1 「以 MIUIX 自身为基准」的回归方法（建议固化为流程）

后续每次想确认"我们是否还完全照用 MIUIX"，不要拿第三方 App 当参照，直接编译官方 example：

```bash
cd <miuix>            # 与 setup-miuix.sh 钉的同一个提交
./gradlew :example:android:assembleDebug
# 产物：example/android/build/outputs/apk/debug/Miuix-*.apk
adb install -r example/android/build/outputs/apk/debug/Miuix-*.apk
```

然后在模拟器上逐屏对照（Home / Settings / 弹层 / About）。本轮用它发现并修掉了 3 处偏离：
列表页缺 `VerticalScrollBar`、行首图标 26dp≠官方 28dp、`centerObstructionStart` 未接。
清单与判定见 `docs/audit-miuix-example-parity.md`。
