package com.invictus.xmd.domain.browser

/** Small in-memory ring buffer of WebView console messages and failed requests,
 *  copied to the clipboard from the browser overflow menu ("Copy debug log"). */
object WebDebugLog {
    private const val MAX = 300
    private val lines = ArrayDeque<String>()

    fun add(line: String) = synchronized(lines) {
        if (lines.size >= MAX) lines.removeFirst()
        lines.addLast(line.take(400))
    }

    fun dump(): String = synchronized(lines) { lines.joinToString("\n") }
}
