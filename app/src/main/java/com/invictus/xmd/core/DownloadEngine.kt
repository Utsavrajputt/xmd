package com.invictus.xmd.core

import org.json.JSONArray
import org.json.JSONObject
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.URI
import java.net.URLDecoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern

typealias ProgressFn = (done: Long, total: Long, speedBps: Double) -> Unit
typealias LogFn = (String) -> Unit

// 1 MiB blocks: far fewer read()/write() syscalls than the old 256 KiB,
// which matters most when the destination sits behind Android's FUSE
// layer (shared/public storage) where every syscall has extra overhead.
private const val STREAM_BLOCK_SIZE = 1024 * 1024
private const val MULTI_CONNECTION_MIN_BYTES = 4L * 1024 * 1024
private const val PROGRESS_THROTTLE_NANOS = 200_000_000L // ~5 UI updates/sec

class DownloadEngine(
    private val client: OkHttpClient,
    private val progress: ProgressFn = { _, _, _ -> },
    private val log: LogFn = {},
    private val connections: Int = 4,
    private val speedLimitBytesPerSec: Long = 0L
) {
    private val paused    = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)
    private val lastProgressEmitNanos = AtomicLong(0L)
    private val limiter   = RateLimiter(speedLimitBytesPerSec)

    // Every in-flight OkHttp Call (single-connection download, each segment of
    // a multi-connection download, and the range-support probe) registers
    // itself here while running. Cancel.() closes them all directly instead
    // of only flipping a flag -- a blocked InputStream.read() on a stalled
    // connection (e.g. server trickling a byte every 20s, well under the
    // read timeout) never returns to re-check that flag on its own, so the
    // old flag-only cancel() could leave a "stuck" download completely
    // unresponsive to Cancel/Cancel All. Forcing the socket closed makes the
    // blocked read throw immediately.
    private val activeCalls = java.util.concurrent.ConcurrentHashMap.newKeySet<Call>()

    // ── Multi-connection segment resume metadata ────────────────────────
    /**
     * One connection's byte range plus how much of it is already on disk.
     * `done` is only ever mutated by the single thread downloading this
     * segment, but it's read cross-thread when the whole segment list gets
     * serialized to the sidecar file -- @Volatile keeps that read from
     * seeing a stale value without needing a full lock.
     */
    private data class SegmentState(val start: Long, val end: Long, @Volatile var done: Long = 0L)

    private data class PersistedMultiState(val url: String, val total: Long, val segments: List<SegmentState>)

    private val metaLock = Any()
    private val lastMetaWriteNanos = AtomicLong(0L)

    private fun metaFile(destination: File) = File(destination.parentFile, destination.name + ".xmdparts")

    /**
     * Writes current per-segment progress next to the destination file so a
     * later downloadAuto() call for the same URL can resume each segment
     * from its own last byte instead of restarting the whole download --
     * see the comment on [downloadAuto] for why this exists. Uses a
     * write-to-temp-then-rename so a process death mid-write never leaves a
     * half-written (unparseable) sidecar behind.
     */
    private fun writeSegmentMeta(destination: File, url: String, total: Long, segments: List<SegmentState>) {
        synchronized(metaLock) {
            runCatching {
                val obj = JSONObject()
                obj.put("url", url)
                obj.put("total", total)
                val arr = JSONArray()
                segments.forEach { seg ->
                    arr.put(JSONObject().apply {
                        put("start", seg.start); put("end", seg.end); put("done", seg.done)
                    })
                }
                obj.put("segments", arr)
                val tmp = File(destination.parentFile, metaFile(destination).name + ".tmp")
                tmp.writeText(obj.toString())
                tmp.renameTo(metaFile(destination))
            }
        }
    }

    /** Throttled version of [writeSegmentMeta] for the hot per-chunk call site
     *  inside [downloadRange] -- same idea as [emitProgress]'s throttle, just
     *  for disk writes instead of UI callbacks. */
    private fun maybeWriteSegmentMeta(write: () -> Unit) {
        val now = System.nanoTime()
        val last = lastMetaWriteNanos.get()
        if (now - last < PROGRESS_THROTTLE_NANOS) return
        if (lastMetaWriteNanos.compareAndSet(last, now)) write()
    }

    /**
     * Reads back a previously-written sidecar, or null if there isn't one,
     * it doesn't parse, it was for a different URL's resolved link, or the
     * destination file's size no longer matches what it describes (any of
     * which mean it's stale/untrustworthy and a fresh start is safer).
     */
    private fun readPersistedMultiState(destination: File): PersistedMultiState? {
        val file = metaFile(destination)
        if (!file.isFile) return null
        return try {
            val obj = JSONObject(file.readText())
            val url = obj.getString("url")
            val total = obj.getLong("total")
            if (!destination.isFile || destination.length() != total) return null
            val arr = obj.getJSONArray("segments")
            val segs = (0 until arr.length()).map { i ->
                val s = arr.getJSONObject(i)
                SegmentState(s.getLong("start"), s.getLong("end"), s.getLong("done"))
            }
            if (segs.isEmpty() || segs.first().start != 0L || segs.last().end != total - 1L) return null
            PersistedMultiState(url, total, segs)
        } catch (e: Exception) {
            null
        }
    }

    private fun deleteSegmentMeta(destination: File) {
        runCatching { metaFile(destination).delete() }
    }

    // ── Rate limiter (unchanged) ──────────────────────────────────────────
    private class RateLimiter(private val bytesPerSecond: Long) {
        private val lock  = Any()
        private val startNanos = System.nanoTime()
        private var bytesConsumed = 0L

        fun acquire(bytes: Int) {
            if (bytesPerSecond <= 0) return
            var sleepNanos = 0L
            synchronized(lock) {
                bytesConsumed += bytes
                val elapsedNanos  = System.nanoTime() - startNanos
                val expectedNanos = (bytesConsumed.toDouble() / bytesPerSecond * 1_000_000_000L).toLong()
                if (expectedNanos > elapsedNanos) sleepNanos = expectedNanos - elapsedNanos
            }
            if (sleepNanos > 0) Thread.sleep(sleepNanos / 1_000_000, (sleepNanos % 1_000_000).toInt())
        }
    }

    // ── Sliding-window speed meter ────────────────────────────────────────
    /**
     * Thread-safe 3-second sliding window speed meter.
     *
     * Previous code used (totalBytesDownloaded / totalElapsedSeconds), which
     * produced an ever-decaying average dragged down by TCP slow-start at the
     * beginning of the download. This meter only considers bytes received in
     * the last [windowMs] ms, so it tracks the *current* speed the way the
     * system status-bar does — no drift, no slow-start penalty.
     *
     * In multi-connection mode a single shared instance is passed to all
     * segment workers so their bytes are summed into one accurate aggregate.
     */
    private class SpeedMeter(private val windowMs: Long = 3_000L) {
        private val lock    = Any()
        // ArrayDeque of (timestampNanos, bytes) pairs
        private val samples = ArrayDeque<Pair<Long, Long>>()

        fun record(bytes: Long) {
            if (bytes <= 0) return
            val now = System.nanoTime()
            synchronized(lock) {
                samples.addLast(now to bytes)
                val cutoff = now - windowMs * 1_000_000L
                while (samples.isNotEmpty() && samples.first().first < cutoff) {
                    samples.removeFirst()
                }
            }
        }

        /** Returns bytes/sec over the sliding window; 0.0 if fewer than 2 samples. */
        fun bps(): Double {
            synchronized(lock) {
                if (samples.size < 2) return 0.0
                val windowNanos = samples.last().first - samples.first().first
                if (windowNanos <= 0L) return 0.0
                // Sum bytes of every sample EXCEPT the first (it's the window anchor)
                val totalBytes = samples.drop(1).sumOf { it.second }
                return totalBytes * 1_000_000_000.0 / windowNanos
            }
        }
    }

    // ── Public control ────────────────────────────────────────────────────
    fun pause()  { paused.set(true) }
    fun resume() { paused.set(false) }
    fun cancel() {
        cancelled.set(true)
        paused.set(false)
        // Force-close every in-flight connection so a blocked read on a
        // stalled/trickling download is interrupted immediately.
        activeCalls.forEach { it.cancel() }
    }

    private fun checkpoint() {
        if (cancelled.get()) throw DownloadCancelledException()
        while (paused.get()) {
            Thread.sleep(100)
            if (cancelled.get()) throw DownloadCancelledException()
        }
    }

    private fun emitProgress(done: Long, total: Long, speedBps: Double, force: Boolean = false) {
        val now  = System.nanoTime()
        val last = lastProgressEmitNanos.get()
        if (!force && now - last < PROGRESS_THROTTLE_NANOS) return
        if (lastProgressEmitNanos.compareAndSet(last, now) || force) {
            progress(done, total, speedBps)
        }
    }

    // ── Companions (filename utils) ─────────────────────────────────────
    companion object {
        private val INVALID_CHARS       = charArrayOf('<', '>', ':', '"', '/', '\\', '|', '?', '*')
        private val CONTENT_RANGE_TOTAL = Pattern.compile("/(\\d+)$")
        private val CONTENT_DISPOSITION_FILENAME =
            Pattern.compile("filename\\*?=(?:UTF-8'')?\"?([^\";]+)\"?", Pattern.CASE_INSENSITIVE)

        private fun sanitize(name: String): String =
            name.map { if (it in INVALID_CHARS) '_' else it }.joinToString("").take(220)

        fun filenameFromUrl(url: String): String {
            val path = runCatching { URI(url).path }.getOrNull().orEmpty()
            val raw  = path.substringAfterLast('/').let {
                runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it)
            }
            return sanitize(raw.ifBlank { "download.bin" })
        }

        fun filenameFromLink(link: String): String {
            val fragment = runCatching { URI(link).fragment }.getOrNull()?.trim().orEmpty()
            if (fragment.isEmpty()) return ""
            return sanitize(fragment)
        }

        /** Parses a filename out of a raw Content-Disposition header value, e.g.
         *  `attachment; filename="Movie.mkv"` or the RFC 5987
         *  `attachment; filename*=UTF-8''Movie.mkv` form. Null if none found. */
        fun filenameFromContentDisposition(header: String?): String? {
            if (header.isNullOrBlank()) return null
            val matcher = CONTENT_DISPOSITION_FILENAME.matcher(header)
            if (!matcher.find()) return null
            val raw = matcher.group(1)?.trim().orEmpty()
            if (raw.isEmpty()) return null
            val decoded = runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
            return sanitize(decoded).ifBlank { null }
        }

        /**
         * Looks up the server's real filename before a download starts, for
         * links whose URL path is just an opaque id rather than the actual
         * filename -- e.g. pixeldrain.dev/api/file/<id>?download or a
         * hubcloud-generated link. filenameFromUrl() alone would name the
         * file after that id (what was happening before this existed); the
         * real name only ever shows up in the response's Content-Disposition
         * header. Tries a cheap HEAD first, then falls back to a 0-byte
         * ranged GET for servers that don't implement HEAD (many CDNs).
         * Returns null (never throws) if neither yields a usable name, so
         * callers can fall back to the URL-based naming as before.
         */
        fun probeRealFilename(client: OkHttpClient, url: String): String? {
            val headName = runCatching {
                client.newCall(Request.Builder().url(url).head().build()).execute().use { resp ->
                    if (resp.isSuccessful) filenameFromContentDisposition(resp.header("Content-Disposition")) else null
                }
            }.getOrNull()
            if (headName != null) return headName

            return runCatching {
                val rangeRequest = Request.Builder().url(url).header("Range", "bytes=0-0").build()
                client.newCall(rangeRequest).execute().use { resp ->
                    filenameFromContentDisposition(resp.header("Content-Disposition"))
                }
            }.getOrNull()
        }

        /**
         * Deletes a destination file together with its multi-connection
         * resume sidecar (the ".xmdparts" file written by [writeSegmentMeta]).
         * Call this whenever a download is abandoned for good -- a genuine
         * user Cancel -- so a future download reusing the same temp path
         * doesn't get confused by stale segment metadata. Deliberately NOT
         * called for a merely-FAILED (retries exhausted) item: leaving both
         * files in place is what lets a manual Retry resume instead of
         * starting the whole file over.
         */
        fun deletePartialFiles(destination: File) {
            destination.delete()
            File(destination.parentFile, destination.name + ".xmdparts").delete()
        }
    }

    // ── Entry point ───────────────────────────────────────────────────────
    /**
     * Always checks for resumable multi-connection progress FIRST, before
     * looking at the current [connections] setting at all -- a prior
     * attempt's sidecar (see [writeSegmentMeta]) fully determines the
     * segment layout on resume, so this correctly keeps resuming
     * multi-connection even if the user changed the connections-per-download
     * setting mid-download. Skipping this used to be the whole bug: any
     * exception from a fresh downloadMulti() -- including a plain network
     * drop with most of the file already on disk -- deleted the destination
     * outright and restarted single-connection from byte 0, which is exactly
     * what a network toggle mid-download would trigger.
     *
     * Deliberately does NOT require the sidecar's stored URL to match [url]:
     * a manual Retry on a share link (FuckingFast/pixeldrain/hubcloud/etc.)
     * always re-resolves to a fresh, differently-tokened CDN link (see
     * MainActivity.retrySingle), so gating resume on exact URL equality
     * meant resume silently never worked on the one path users actually hit
     * after a failure. [destination]'s path is already per-item deterministic
     * (same fileName+category each attempt) and readPersistedMultiState
     * already verifies the file's on-disk length still matches the
     * recorded total, so path+size is trusted as "the same download" and
     * the fresh [url] is simply used to fetch whatever ranges are left.
     */
    fun downloadAuto(url: String, destination: File) {
        cancelled.set(false)
        paused.set(false)

        val resumable = readPersistedMultiState(destination)
        if (resumable != null) {
            log("Resuming ${destination.name}: ${resumable.segments.sumOf { it.done }}/${resumable.total} bytes across ${resumable.segments.size} connections")
            downloadMulti(url, destination, resumable.total, resumable.segments)
            return
        }
        if (metaFile(destination).isFile) {
            // A sidecar exists but readPersistedMultiState still rejected it --
            // corrupt JSON, or destination's on-disk length no longer matches
            // the recorded total (e.g. something else touched the file).
            // destination's on-disk length in that case is a multi-connection
            // pre-allocation that doesn't describe real single-connection
            // progress, so falling through to the plain single-connection
            // resume below with that length would make the server think the
            // file is already complete. Safer to discard both and start clean.
            deleteSegmentMeta(destination)
            destination.delete()
        }

        val alreadyPartial = destination.isFile && destination.length() > 0
        if (connections > 1 && !alreadyPartial) {
            val probe = probeRangeSupport(url)
            // probeRangeSupport() swallows every exception (including a
            // cancellation-triggered IOException) internally, so re-check the
            // flag explicitly here in case Cancel landed while the probe
            // itself was in flight.
            checkpoint()
            if (probe.supportsRanges && probe.totalSize >= MULTI_CONNECTION_MIN_BYTES) {
                val segmentSize = probe.totalSize / connections
                val freshSegments = (0 until connections).map { i ->
                    val start = i * segmentSize
                    val end = if (i == connections - 1) probe.totalSize - 1 else (start + segmentSize - 1)
                    SegmentState(start, end)
                }
                try {
                    log("Downloading with $connections parallel connections")
                    downloadMulti(url, destination, probe.totalSize, freshSegments)
                    return
                } catch (e: DownloadCancelledException) {
                    throw e
                } catch (e: Exception) {
                    // Only safe to discard the sparse placeholder file and
                    // fall back to single-connection if NOTHING was actually
                    // written yet -- re-read the sidecar (downloadMulti keeps
                    // it current as segments progress) rather than assuming;
                    // if real bytes made it to disk, preserve them and let
                    // the caller's retry resume these exact segments instead.
                    val bytesSoFar = readPersistedMultiState(destination)
                        ?.takeIf { it.url == url }?.segments?.sumOf { it.done } ?: 0L
                    if (bytesSoFar > 0) throw e
                    log("Parallel download failed immediately (${e.message}), falling back to single connection")
                    deleteSegmentMeta(destination)
                    destination.delete()
                    cancelled.set(false)
                }
            }
        }
        download(url, destination)
    }

    // ── Range probe (unchanged) ───────────────────────────────────────────
    private data class RangeProbe(val totalSize: Long, val supportsRanges: Boolean)

    private fun probeRangeSupport(url: String): RangeProbe {
        val request = Request.Builder().url(url).header("Range", "bytes=0-0").build()
        val call = client.newCall(request)
        activeCalls.add(call)
        return try {
            call.execute().use { response ->
                when (response.code) {
                    206 -> {
                        val contentRange = response.header("Content-Range").orEmpty()
                        val matcher = CONTENT_RANGE_TOTAL.matcher(contentRange)
                        val total = if (matcher.find()) matcher.group(1)!!.toLong() else -1L
                        RangeProbe(total, total > 0)
                    }
                    200 -> {
                        val total = response.header("content-length")?.toLongOrNull() ?: -1L
                        RangeProbe(total, false)
                    }
                    else -> RangeProbe(-1L, false)
                }
            }
        } catch (e: Exception) {
            RangeProbe(-1L, false)
        } finally {
            activeCalls.remove(call)
        }
    }

    // ── Multi-connection download ──────────────────────────────────────────
    /**
     * `segments` fully describes the layout (and, on a resume, how much of
     * each is already downloaded) -- callers build this fresh from
     * [connections] for a brand-new download, or read it back from the
     * sidecar via [readPersistedMultiState] to resume one already in
     * progress. The completion check at the end sums each segment's real
     * `done` count rather than checking `destination.length()`, since the
     * file is pre-sized to `totalSize` up front (see setLength below) and
     * so its length alone can never actually catch a short/truncated
     * download -- that was a second, separate way a corrupted file could
     * previously get marked DONE.
     */
    private fun downloadMulti(url: String, destination: File, totalSize: Long, segments: List<SegmentState>) {
        destination.parentFile?.mkdirs()
        RandomAccessFile(destination, "rw").use { it.setLength(totalSize) }

        val alreadyDone = segments.sumOf { it.done }
        val doneCounter = AtomicLong(alreadyDone)
        // Seed the UI with real progress immediately on resume instead of a
        // visible jump back to 0% while the still-incomplete segments spin up.
        if (alreadyDone > 0) emitProgress(alreadyDone, totalSize, 0.0, force = true)

        writeSegmentMeta(destination, url, totalSize, segments)

        val pending = segments.filter { it.done < (it.end - it.start + 1) }
        if (pending.isEmpty()) {
            // Every segment already finished in a prior attempt (e.g. the
            // process died right after the last byte landed, before cleanup
            // ran) -- nothing left to download.
            deleteSegmentMeta(destination)
            emitProgress(totalSize, totalSize, 0.0, force = true)
            log("Downloaded ${destination.name}")
            return
        }

        // One shared SpeedMeter so all segment threads contribute to the
        // same sliding window — gives the true aggregate download speed.
        val speedMeter = SpeedMeter()
        val failure    = AtomicReference<Exception?>(null)
        val executor   = Executors.newFixedThreadPool(pending.size)

        try {
            val futures = pending.map { seg ->
                executor.submit {
                    try {
                        downloadRange(url, destination, seg, doneCounter, totalSize, speedMeter) {
                            maybeWriteSegmentMeta { writeSegmentMeta(destination, url, totalSize, segments) }
                        }
                    } catch (e: Exception) {
                        failure.compareAndSet(null, e)
                        cancel()
                    }
                }
            }
            futures.forEach { it.get() }
        } finally {
            executor.shutdownNow()
            // Always snapshot final per-segment state, success or failure --
            // this is what a subsequent downloadAuto() resumes from.
            writeSegmentMeta(destination, url, totalSize, segments)
        }

        failure.get()?.let { throw it }

        val finalDone = segments.sumOf { it.done }
        if (finalDone < totalSize) {
            throw RuntimeException("Parallel download incomplete: $finalDone/$totalSize bytes")
        }
        deleteSegmentMeta(destination)
        emitProgress(totalSize, totalSize, 0.0, force = true)
        log("Downloaded ${destination.name}")
    }

    private fun downloadRange(
        url: String,
        destination: File,
        seg: SegmentState,
        doneCounter: AtomicLong,
        totalSize: Long,
        speedMeter: SpeedMeter,      // ← shared across all segment workers
        persist: () -> Unit          // ← throttled sidecar write, called per chunk + once at the end
    ) {
        val resumeStart = seg.start + seg.done
        if (resumeStart > seg.end) return // already fully downloaded in a prior attempt
        val request = Request.Builder().url(url).header("Range", "bytes=$resumeStart-${seg.end}").build()
        val call = client.newCall(request)
        activeCalls.add(call)
        try {
            call.execute().use { response ->
                if (response.code != 206 && response.code != 200) {
                    throw RuntimeException("Segment ${seg.start}-${seg.end} failed (HTTP ${response.code})")
                }
                val body = response.body ?: throw RuntimeException("Empty segment body")
                RandomAccessFile(destination, "rw").use { raf ->
                    raf.seek(resumeStart)
                    body.byteStream().use { input ->
                        val buffer = ByteArray(STREAM_BLOCK_SIZE)
                        while (true) {
                            checkpoint()
                            val read = input.read(buffer)
                            if (read == -1) break
                            if (read == 0) continue
                            raf.write(buffer, 0, read)
                            seg.done += read
                            val done = doneCounter.addAndGet(read.toLong())
                            limiter.acquire(read)
                            speedMeter.record(read.toLong())          // ← sliding window
                            emitProgress(done, totalSize, speedMeter.bps())
                            persist()
                        }
                    }
                }
                // A clean EOF (read() == -1) here doesn't guarantee the server
                // actually sent the full requested range -- a proxy/CDN
                // cutting the response short mid-segment looks identical to a
                // normal finish from this side. Verify the byte count
                // explicitly instead of trusting -1 alone, otherwise a
                // truncated segment silently counts as "done" and the file
                // ships corrupted.
                val expected = seg.end - seg.start + 1
                if (seg.done < expected) {
                    throw IOException("Segment ${seg.start}-${seg.end} ended early (${seg.done}/$expected bytes)")
                }
            }
        } catch (e: IOException) {
            // A cancelled Call closes the socket, which surfaces here as a
            // plain IOException (e.g. "Canceled") rather than our own
            // DownloadCancelledException -- translate it back so callers see
            // a clean cancellation instead of a confusing network error.
            if (cancelled.get()) throw DownloadCancelledException()
            throw e
        } finally {
            activeCalls.remove(call)
        }
    }

    // ── Single-connection download (with resume) ───────────────────────────
    fun download(url: String, destination: File) {
        destination.parentFile?.mkdirs()
        cancelled.set(false)
        paused.set(false)

        val existingSize    = if (destination.isFile) destination.length() else 0L
        val requestBuilder  = Request.Builder().url(url)
        if (existingSize > 0) requestBuilder.header("Range", "bytes=$existingSize-")

        val call = client.newCall(requestBuilder.build())
        activeCalls.add(call)
        try {
            call.execute().use { response ->
                when (response.code) {
                    416 -> {
                        log("File already complete: ${destination.name}")
                        return
                    }
                    206 -> {
                        val contentRange = response.header("Content-Range").orEmpty()
                        val matcher = CONTENT_RANGE_TOTAL.matcher(contentRange)
                        val totalSize = if (matcher.find()) {
                            matcher.group(1)!!.toLong()
                        } else {
                            existingSize + (response.header("content-length")?.toLongOrNull() ?: 0L)
                        }
                        if (totalSize > 0 && existingSize >= totalSize) {
                            log("File already complete: ${destination.name}"); return
                        }
                        log("Resuming ${destination.name} from $existingSize bytes")
                        streamToFile(response, destination, existingSize, totalSize, append = true)
                    }
                    200 -> {
                        val totalSize = response.header("content-length")?.toLongOrNull() ?: 0L
                        if (existingSize > 0 && totalSize > 0 && existingSize >= totalSize) {
                            log("File already complete: ${destination.name}"); return
                        }
                        if (existingSize > 0) log("Server ignored resume; restarting ${destination.name}")
                        streamToFile(response, destination, 0L, totalSize, append = false)
                    }
                    else -> {
                        val host = runCatching { URI(url).host }.getOrNull()
                        if (host == "dl.fuckingfast.co" && response.code in setOf(401, 403, 404, 410)) {
                            throw RuntimeException(
                                "This direct link has expired or is unavailable. Paste the original " +
                                "share link to prepare a fresh download URL."
                            )
                        }
                        throw RuntimeException("Failed to download file (HTTP ${response.code})")
                    }
                }
            }
        } catch (e: IOException) {
            // Cancelling closes the socket, which surfaces here as a plain
            // IOException rather than DownloadCancelledException directly --
            // translate it so a Cancel tap reads as "Cancelled", not a
            // confusing network error.
            if (cancelled.get()) throw DownloadCancelledException()
            throw e
        } finally {
            activeCalls.remove(call)
        }
        log("Downloaded ${destination.name}")
    }

    private fun streamToFile(
        response: Response,
        destination: File,
        initial: Long,
        totalSize: Long,
        append: Boolean
    ) {
        val body       = response.body ?: throw RuntimeException("Empty response body")
        var done       = initial
        val speedMeter = SpeedMeter()           // ← per-download sliding window

        RandomAccessFile(destination, "rw").use { raf ->
            if (append) raf.seek(destination.length()) else { raf.setLength(0); raf.seek(0) }
            body.byteStream().use { input ->
                val buffer = ByteArray(STREAM_BLOCK_SIZE)
                while (true) {
                    checkpoint()
                    val read = input.read(buffer)
                    if (read == -1) break
                    if (read == 0) continue
                    raf.write(buffer, 0, read)
                    done += read
                    limiter.acquire(read)
                    speedMeter.record(read.toLong())              // ← sliding window
                    emitProgress(done, totalSize, speedMeter.bps())
                }
            }
        }

        emitProgress(done, totalSize, 0.0, force = true)

        val finalSize = destination.length()
        if (totalSize > 0 && finalSize < totalSize) {
            throw RuntimeException("Download incomplete: $finalSize/$totalSize bytes")
        }
    }
}
