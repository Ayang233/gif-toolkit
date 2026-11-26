package com.ayang.giftoolkit.ui

/**
 * 文件大小与时间的唯一格式化出处。
 *
 * 之前 `ToolScreen` / `LibraryScreen` / `TaskRunner` 各有一份一模一样的 `formatSize`，
 * 改一处就会只在某一个页面生效。现在只有这一份，页面按需 import。
 */
fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

/** `MM-dd HH:mm`：作品列表行里的修改时间。 */
fun formatTime(millis: Long): String {
    val fmt = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
    return fmt.format(java.util.Date(millis))
}
