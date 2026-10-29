package com.invictus.xmd.domain.download

/**
 * How a download is split across threads.
 *  - [CLASSIC]: fixed split into exactly `maxThreads` equal segments.
 *  - [SMART]: thread count follows the file size (capped at `maxThreads`) and the file is cut
 *    into more chunks than threads, so faster connections take over chunks slow ones haven't started.
 */
enum class MultiThreadMode {
    CLASSIC,
    SMART;

    companion object {
        fun fromName(name: String?): MultiThreadMode =
            entries.firstOrNull { it.name == name } ?: CLASSIC
    }
}
