package com.ayang.giftoolkit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 内容延伸到状态栏/导航栏下方，否则系统栏区域由窗口背景绘制，深色模式会留一条白边
        enableEdgeToEdge()
        // 三键导航下系统会给导航栏加一层对比度底色（浅色下是白的），
        // 和页面底色（#F7F7F7）之间会出现一条接缝。关掉它，让页面底色一直铺到底。
        window.isNavigationBarContrastEnforced = false
        setContent {
            GifApp()
        }
    }
}
