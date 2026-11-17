package com.ayang.giftoolkit

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ayang.giftoolkit.core.GifFrame
import com.ayang.giftoolkit.data.SettingsStore
import com.ayang.giftoolkit.ui.Tool
import java.io.File

/** 主题模式；Monet 取色跟随系统。 */
enum class ThemeMode(val label: String) {
    System("跟随系统"),
    Light("浅色"),
    Dark("深色"),
}

/**
 * 预测性返回动画档位。
 *
 * [None] 不只是"不做动画"：它同时会把系统级预测性返回关掉（`enableOnBackInvokedCallback` 置 false），
 * 让返回变成和传统系统一致的行为。其余档位只改页面转场的视觉，系统手势由系统自己画。
 */
enum class BackAnimation(val label: String, val summary: String) {
    Miuix("Miuix", "左右滑入，下层后退"),
    Aosp("AOSP", "进入页缩放淡入，当前页轻微后退"),
    Scale("缩放", "按手势方向缩放并平移"),
    Classic("经典", "通栏平移，下层不动"),
    None("无", "关闭手势预览，页面切换仍用默认转场"),
}

/** 缩放档位下页面退出的方向。 */
enum class BackExitDirection(val label: String) {
    FollowGesture("跟随手势"),
    AlwaysRight("始终向右"),
    AlwaysLeft("始终向左"),
}

/**
 * 跨屏共享的状态，只放真正全局的东西。
 *
 * 外观三项（主题 / 动态取色 / 玻璃）走 [SettingsStore] 落盘：读的时候取内存里的快照，
 * 写的时候同时更新快照与磁盘，所以界面立即响应、重启后仍然保留。
 */
class AppState(private val store: SettingsStore) {

    /**
     * 是否有一层**模态**页面弹层（如作品操作面板）正开着。
     *
     * 底栏画在 NavDisplay 外面（录制层约束），层级永远在页面内容之上；
     * 而 GlassBottomSheet 由页面自己的 Scaffold 绘制 —— 所以弹层会被底栏压住。
     * 弹层开着时把一层导航让出去，既解决压盖，也符合"模态遮住导航"的常规行为。
     * 不落盘，纯运行时状态。
     */
    var modalOverlayOpen by mutableStateOf(false)

    private var themeModeState by mutableStateOf(store.themeMode)
    private var monetEnabledState by mutableStateOf(store.monetEnabled)
    private var glassEnabledState by mutableStateOf(store.glassEnabled)
    private var amoledEnabledState by mutableStateOf(store.amoledEnabled)
    private var backAnimationState by mutableStateOf(store.backAnimation)
    private var backExitDirectionState by mutableStateOf(store.backExitDirection)

    var themeMode: ThemeMode
        get() = themeModeState
        set(value) {
            if (value != themeModeState) {
                themeModeState = value
                store.themeMode = value
            }
        }

    var monetEnabled: Boolean
        get() = monetEnabledState
        set(value) {
            if (value != monetEnabledState) {
                monetEnabledState = value
                store.monetEnabled = value
            }
        }

    var amoledEnabled: Boolean
        get() = amoledEnabledState
        set(value) {
            if (value != amoledEnabledState) {
                amoledEnabledState = value
                store.amoledEnabled = value
            }
        }

    var backAnimation: BackAnimation
        get() = backAnimationState
        set(value) {
            if (value != backAnimationState) {
                backAnimationState = value
                store.backAnimation = value
            }
        }

    var backExitDirection: BackExitDirection
        get() = backExitDirectionState
        set(value) {
            if (value != backExitDirectionState) {
                backExitDirectionState = value
                store.backExitDirection = value
            }
        }

    /** 玻璃材质开关（顶栏 / 底栏是否模糊），见 [com.ayang.giftoolkit.ui.GlassSupport]。 */
    var glassEnabled: Boolean
        get() = glassEnabledState
        set(value) {
            if (value != glassEnabledState) {
                glassEnabledState = value
                store.glassEnabled = value
            }
        }

    var libraryToken by mutableIntStateOf(0)

    /** 最近一次导出，供结果页使用。 */
    var lastResultPath by mutableStateOf<String?>(null)
    var lastResultMime by mutableStateOf("image/gif")
}

/**
 * 单个工具页的表单状态。
 *
 * 每个工具页各自 remember 一份，工具之间互不污染 —— 旧版本把所有工具塞进一个
 * AppState，改过视频参数再进 GIF 编辑会残留，这版修掉了。
 */
class ToolForm(private val tool: Tool) {
    var uris by mutableStateOf<List<Uri>>(emptyList())

    var busy by mutableStateOf(false)
    var progress by mutableFloatStateOf(0f)
    var message by mutableStateOf("")
    var result by mutableStateOf<File?>(null)
    var resultMime by mutableStateOf("image/gif")
    var preview by mutableStateOf<List<GifFrame>>(emptyList())
    var stillPreview by mutableStateOf<Bitmap?>(null)

    /**
     * 用户点了「取消」。
     *
     * 不用 `Job.cancel()`：解码循环是阻塞调用，协程取消只在挂起点生效，
     * 视频解码那种 for 循环根本轮不到检查。这里改成协作式标记，
     * 由 [com.ayang.giftoolkit.task.runTask] 在进度回调里逐帧检查。
     */
    var cancelRequested by mutableStateOf(false)

    // 通用
    var width by mutableFloatStateOf(360f)
    var fps by mutableFloatStateOf(12f)
    var quality by mutableFloatStateOf(255f)

    // 视频
    var startSec by mutableFloatStateOf(0f)
    var durationSec by mutableFloatStateOf(3f)
    var stabilize by mutableStateOf(false)
    var colorKey by mutableStateOf(false)
    var keyColor by mutableStateOf(0xFF00B441.toInt())
    var keyTolerance by mutableFloatStateOf(30f)

    // 图片合成
    var delayMs by mutableFloatStateOf(120f)

    // 编辑
    var rotate by mutableFloatStateOf(0f)
    var mirror by mutableStateOf(false)
    var flip by mutableStateOf(false)
    var reverse by mutableStateOf(false)
    var cropCenter by mutableStateOf(false)
    var text by mutableStateOf("")
    var mosaic by mutableStateOf(false)
    var mosaicPos by mutableIntStateOf(0)

    // 拼图
    var puzzleCols by mutableIntStateOf(2)

    // 压缩
    var scale by mutableFloatStateOf(0.7f)
    var frameStep by mutableFloatStateOf(1f)

    // 播放
    var playSpeed by mutableFloatStateOf(1f)

    val canRun: Boolean
        get() = uris.isNotEmpty() && !busy && (tool.acceptsMultiple || uris.size == 1)
}
