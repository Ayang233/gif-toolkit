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
        setContent {
            GifApp()
        }
    }
}
