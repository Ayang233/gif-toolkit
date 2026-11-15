# AGENTS.md

给接手这个仓库的人（或 AI 助手）看的上下文。README 讲「是什么、怎么构建」，这里讲「改的时候要注意什么」。

## 一句话

单模块 Kotlin Multiplatform 工程，**全部实现放在 `androidMain`**，`commonMain` 只用来声明依赖；
UI 全部基于 [Miuix](https://github.com/compose-miuix-ui/miuix)，**不引入 Material 组件**。

## 目录地图

```
src/androidMain/kotlin/com/ayang/giftoolkit/
├── App.kt                主题装配、NavDisplay 路由、玻璃底栏、退出确认
├── Route.kt              路由定义（Root / Tool / About / Licenses / License）
├── AppState.kt           全局状态 + 每个工具一份 ToolForm（表单状态）
├── data/SettingsStore.kt 外观设置持久层（SharedPreferences）
├── ui/
│   ├── ToolCatalog.kt    十个工具的元数据（id / 标题 / 说明 / 分组 / 图标 / 按钮文案）
│   ├── Widgets.kt        分组卡片、行、下拉、预览舞台、主操作条、pageColor/cardColor
│   ├── GlassScope.kt     页面玻璃作用域 + PageTopBar（玻璃/普通两态）
│   ├── GlassSupport.kt   玻璃开关与能力检测的**唯一决策点**
│   ├── ProgressButton.kt 自带进度填充的按钮（移植自 miuix 生态，Apache-2.0）
│   └── LicenseCatalog.kt 第三方组件清单
├── screens/              主页 / 工具页 / 作品页 / 设置 / 关于 / 开源许可
├── task/TaskRunner.kt    任务分发 + 进度 + 预览取帧
└── core/                 GifCodec / GifTasks / MediaTools / Stabilizer / ColorKey / MotionPhoto
```

## 常用命令

```bash
./scripts/setup-miuix.sh                 # 首次：准备 Miuix 源码并打补丁
./gradlew :assembleDebug                 # 调试包
./gradlew :assembleRelease               # 发布包（R8，约 3MB）
adb install -r build/outputs/apk/debug/GifHelper-debug.apk
adb shell am start -n com.ayang.giftoolkit/.MainActivity
```

环境：JDK 21、Android SDK Platform 37、Build-Tools 37.0.0。
没有 `local.properties` 时用 `ANDROID_HOME` 环境变量也能构建（已验证）。

## 硬约束（违反会出事，不是风格问题）

1. **玻璃容器必须是 `layerBackdrop` 录制层的兄弟节点，不能是子节点。**
   子是采样到自己 → `libhwui` 的 `RenderNode::prepareTreeImpl` 原生栈溢出 → 直接闪退。
   这是项目历史上真实发生过的崩溃。
2. **颜色一律走 `ui/Widgets.kt` 的 `pageColor()` / `cardColor()`。**
   Miuix 在 Monet 下会把 `surface` 与 `surfaceContainer` 压成同一个值，
   直接用主题角色会让页面和卡片糊成一片。
3. **玻璃只有一个决策点：`ui/GlassSupport.kt` 的 `LocalGlassEnabled`。**
   页面里不许再出现 `isRenderEffectSupported()` 之类的判断 —— 一度 `App.kt`、`GlassScope`、
   `Widgets.kt` 各判各的，就出过「顶栏玻璃、底栏实心」的割裂。
   **另外：Miuix 的 `isRenderEffectSupported()` 只是 `SDK_INT >= 31`**，
   它查不出某台 GPU 上模糊会不会翻车（真机上出现过「内容不糊开、只被压暗」），
   所以"关掉玻璃"这个开关必须一直留给用户。
4. **弹层（Popover / Dropdown / BottomSheet）不要采样背景。**
   `GlassDropdownPreference` 的 `backdrop` 传 `null`：弹层挂在录制层**外面**，
   采样坐标靠 `localPositionOf` 反算，偏一点就把背景抹成一块脏斑（实机可复现）。
   传 `null` 时库走不透明底色 + 描边 + 投影，才是参考实现那个观感。
5. **分段控件用自绘的 `SegmentedControl`，不要用 `TabRow` 去做"分段"。**
   库里其实有三个页签组件（`TabRow` / `TabRowWithContour` / `GlassTabRow`），
   它们都是**可横向滚动的页签**，不是「一整条凹槽 + 一个滑块」的分段控件；
   `TabRowWithContour` 还会额外画 5dp 轮廓 padding，看起来像一排小按钮。
   离散选项（2~4 个）优先用 `DropdownRow`（玻璃浮层下拉）。
   滑块取色也不能用 `colorScheme.background`（Monet 下不是白色），要取 `cardColor()`。
6. **卡片内不加 `HorizontalDivider`。** 靠 56dp 最小行高 + 16dp 内边距区分，这与 KernelSU / HyperOS 设置页一致。
7. **不要引入 `androidx.compose.material3`。** 会和 Miuix 的设计语言打架，且显著增大包体。

## 常见改动怎么下手

| 要做的事 | 改哪里 |
| --- | --- |
| 加一个工具 | `ToolCatalog` 加枚举项和图标 → `TaskRunner` 加分支 → `ToolScreen.ParamPanel` 加参数行 |
| 加一项设置 | `SettingsStore` 加字段 → `AppState` 加代理属性（读写联动） → `SettingsScreen` / `ThemeScreen` 加行 |
| 加一个页面 | `Route` 加 object → `App.kt` 的 NavDisplay 加 `entry<Route.X>` → `screens/` 新建文件 |
| 改配色层次 | 只动 `pageColor()` / `cardColor()`，不要在各处散写 `colorScheme.*` |
| 加离散选项（2~4 个） | `DropdownRow`（玻璃浮层下拉），不要做"点一下轮换到下一个" |
| 加弹层 | 选择面板用 `WindowBottomSheet`、确认用 `WindowDialog`；两者都是系统窗口，天然在录制层外 |

## 产物与版本约定

- 版本号在 `build.gradle.kts` 的 `versionCode` / `versionName`
- release 使用 **debug 签名**，只为方便装机试用；正式分发必须换 keystore
- debug 包约 38MB（未混淆），release 约 3MB
- 改完 UI 要跑一遍「静态 / Monet × 明 / 暗」四种组合，只看一种会漏掉配色塌陷

## 构建环境的两个坑

1. Miuix 的 `miuix-glass` / `miuix-glass-icons` 没有发布到 Maven Central，只能源码引入；
   且它的 `build-logic` 在配置阶段调 git，composite build 下必须补 `workingDir = rootDir`。
   `scripts/setup-miuix.sh` 会把这两件事都做掉。
2. 在受限网络下 `git push` 可能报 `CONNECT tunnel failed`，而 `api.github.com` 仍然可用；
   必要时可走 REST API（blobs → tree → commit → refs）推送，但远端与本地 commit SHA 会不同源。
