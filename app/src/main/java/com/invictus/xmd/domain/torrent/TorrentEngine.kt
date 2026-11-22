package com.invictus.xmd.domain.torrent

import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.TorrentStatus
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import com.invictus.xmd.domain.download.DownloadCancelledException
import com.invictus.xmd.domain.download.DownloadEngine
import com.invictus.xmd.domain.download.DownloadPart
import com.invictus.xmd.domain.download.ProgressFn
import com.invictus.xmd.domain.download.LogFn

/** What a finished (or in-progress, for naming purposes) torrent looks like. */
data class TorrentResult(
    val name: String,
    val numFiles: Int,
    val saveDir: File,
    val singleFilePath: String? = null
)

/**
 * One shared libtorrent session for the whole process -- a real BitTorrent
 * engine (DHT, peer wire protocol, piece selection, etc.) is heavyweight
 * enough that running more than one per app would just fight itself over
 * the network and disk. Every [TorrentEngine] instance (one per in-flight
 * torrent item, same pattern as DownloadEngine for HTTP items) shares this.
 */
object TorrentSession {
    @Volatile private var manager: SessionManager? = null

    val instance: SessionManager
        get() = manager ?: synchronized(this) {
            manager ?: SessionManager().also {
                it.start()
                manager = it
            }
        }

    fun fetchMetadata(magnetUri: String, timeoutSeconds: Int, cacheDir: File): ByteArray? {
        cacheDir.mkdirs()
        return runCatching {
            instance.fetchMagnet(magnetUri, timeoutSeconds, cacheDir)
        }.getOrNull()
    }
}

/**
 * Downloads a single torrent (from a magnet link or raw .torrent file bytes)
 * via libtorrent4j, reporting progress the same shape as [DownloadEngine]
 * so DownloadService can treat both engines interchangeably from the
 * QueueRepository's point of view (bytesDone/bytesTotal/speedBps).
 */
class TorrentEngine(
    private val progress: ProgressFn = { _, _, _ -> },
    private val log: LogFn = {}
) {
    private val cancelled = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)

    @Volatile private var handle: TorrentHandle? = null
    @Volatile private var currentTorrentInfo: TorrentInfo? = null

    fun getParts(): List<DownloadPart> {
        val h = handle ?: return emptyList()
        if (!runCatching { h.isValid }.getOrDefault(false)) return emptyList()
        val ti = currentTorrentInfo ?: return emptyList()
        val isPaused = paused.get()
        val isCancelled = cancelled.get()
        val status = runCatching { h.status(TorrentHandle.QUERY_PIECES) }.getOrNull()
            ?: runCatching { h.status() }.getOrNull()
        val isDownloading = !isPaused && !isCancelled &&
            (status?.state() == TorrentStatus.State.DOWNLOADING || (status?.downloadRate() ?: 0) > 0)

        if (ti.numFiles() > 1) {
            val fp = runCatching { h.fileProgress(TorrentHandle.PIECE_GRANULARITY) }.getOrNull()
                ?: runCatching { h.fileProgress() }.getOrNull()
            val numFiles = ti.numFiles()
            return (0 until numFiles).map { idx ->
                val fileName = runCatching { ti.files().fileName(idx) }.getOrNull() ?: "File ${idx + 1}"
                val fileSize = runCatching { ti.files().fileSize(idx) }.getOrNull() ?: 0L
                val done = if (fp != null && idx < fp.size) fp[idx].coerceIn(0L, fileSize) else 0L
                val isDone = fileSize > 0 && done >= fileSize
                DownloadPart(
                    id = idx + 1,
                    name = fileName,
                    bytesDone = done,
                    totalBytes = fileSize,
                    isRunning = !isDone && !isPaused && !isCancelled && isDownloading,
                    statusText = when {
                        isDone -> "Completed"
                        isPaused -> "Paused"
                        done > 0 -> "Receiving data"
                        isDownloading -> "Downloading"
                        else -> "Queued"
                    }
                )
            }
        } else {
            val numPieces = ti.numPieces()
            val pieceLen = ti.pieceLength()
            val piecesBitfield = status?.pieces()
            val totalSize = if (status != null && status.totalWanted() > 0) status.totalWanted() else ti.totalSize()
            val totalDone = status?.totalDone() ?: 0L

            val downloadQueue = runCatching { h.downloadQueue }.getOrNull()
            val activePieceIndices = downloadQueue?.map { it.pieceIndex() }?.toSet().orEmpty()

            val chunkCount = minOf(16, numPieces.coerceAtLeast(1))
            val piecesPerChunk = (numPieces + chunkCount - 1) / chunkCount

            return (0 until chunkCount).map { chunkIdx ->
                val startPiece = chunkIdx * piecesPerChunk
                val endPiece = minOf((chunkIdx + 1) * piecesPerChunk, numPieces)
                val chunkPiecesCount = (endPiece - startPiece).coerceAtLeast(1)

                var donePieces = 0
                if (piecesBitfield != null && piecesBitfield.size() > 0) {
                    for (p in startPiece until endPiece) {
                        if (p < piecesBitfield.size() && piecesBitfield.getBit(p)) {
                            donePieces++
                        }
                    }
                }

                val chunkTotalBytes = if (chunkIdx == chunkCount - 1) {
                    totalSize - (chunkIdx.toLong() * piecesPerChunk * pieceLen)
                } else {
                    chunkPiecesCount.toLong() * pieceLen
                }.coerceAtLeast(1L)

                val chunkStartByte = chunkIdx.toLong() * piecesPerChunk * pieceLen
                val pieceDoneBytes = (chunkTotalBytes * donePieces / chunkPiecesCount).coerceIn(0L, chunkTotalBytes)
                val distributedDoneBytes = (totalDone - chunkStartByte).coerceIn(0L, chunkTotalBytes)
                val chunkDoneBytes = if (donePieces > 0) pieceDoneBytes else maxOf(pieceDoneBytes, distributedDoneBytes)

                val isDone = (donePieces == chunkPiecesCount && chunkPiecesCount > 0) || (chunkDoneBytes >= chunkTotalBytes && chunkTotalBytes > 0)
                val hasActivePiece = (startPiece until endPiece).any { it in activePieceIndices }

                DownloadPart(
                    id = chunkIdx + 1,
                    name = "Piece group ${chunkIdx + 1}",
                    bytesDone = chunkDoneBytes,
                    totalBytes = chunkTotalBytes,
                    isRunning = !isDone && !isPaused && !isCancelled && isDownloading,
                    statusText = when {
                        isDone -> "Completed"
                        isPaused -> "Paused"
                        hasActivePiece || chunkDoneBytes > 0 -> "Receiving data"
                        isDownloading -> "Downloading"
                        else -> "Queued"
                    }
                )
            }
        }
    }

    fun pause() {
        paused.set(true)
        handle?.pause()
    }

    fun resume() {
        paused.set(false)
        handle?.resume()
    }

    fun cancel() {
        cancelled.set(true)
        paused.set(false)
        handle?.let { h ->
            runCatching { TorrentSession.instance.remove(h) }
        }
    }

    /**
     * Resolves a magnet link's metadata over DHT/peers (this is the slow,
     * sometimes-fails part -- a dead/unseeded magnet just times out here),
     * then downloads it into [saveDir]. Blocks the calling thread until the
     * torrent finishes, so call this from a background dispatcher.
     */
    fun downloadMagnet(magnetUri: String, saveDir: File, selectedFileIndices: String? = null): TorrentResult {
        cancelled.set(false)
        paused.set(false)
        saveDir.mkdirs()

        log("Looking for peers to fetch torrent metadata…")
        checkpoint()
        val data = TorrentSession.instance.fetchMagnet(magnetUri, METADATA_TIMEOUT_SECONDS, saveDir)
            ?: throw RuntimeException(
                "Couldn't find this torrent's metadata (no peers/seeds responded within " +
                    "${METADATA_TIMEOUT_SECONDS}s). The magnet link may be dead."
            )
        checkpoint()

        val ti = TorrentInfo.bdecode(data)
        return startAndPoll(ti, saveDir, selectedFileIndices)
    }

    /** Same as [downloadMagnet] but for a .torrent file already fetched as bytes. */
    fun downloadTorrentFile(torrentBytes: ByteArray, saveDir: File, selectedFileIndices: String? = null): TorrentResult {
        cancelled.set(false)
        paused.set(false)
        saveDir.mkdirs()
        val ti = TorrentInfo.bdecode(torrentBytes)
        return startAndPoll(ti, saveDir, selectedFileIndices)
    }

    private fun startAndPoll(ti: TorrentInfo, saveDir: File, selectedFileIndices: String? = null): TorrentResult {
        currentTorrentInfo = ti
        log("Downloading: ${ti.name()}")

        val selectedSet = selectedFileIndices?.split(",")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.toSet()

        TorrentSession.instance.download(ti, saveDir)

        val h = waitForHandle(ti) ?: throw RuntimeException("Torrent failed to start")
        handle = h

        if (selectedSet != null && selectedSet.isNotEmpty()) {
            val count = ti.numFiles()
            val priorities = Priority.array(Priority.IGNORE, count)
            for (idx in 0 until count) {
                if (idx in selectedSet) {
                    priorities[idx] = Priority.DEFAULT
                }
            }
            h.prioritizeFiles(priorities)
        }

        while (true) {
            checkpoint()
            while (paused.get()) {
                Thread.sleep(200)
                checkpoint()
            }
            val status = h.status()
            val total = if (status.totalWanted() > 0) status.totalWanted() else ti.totalSize()
            progress(status.totalDone(), total, status.downloadRate().toDouble())

            val finished = status.isFinished ||
                status.state() == TorrentStatus.State.FINISHED ||
                status.state() == TorrentStatus.State.SEEDING
            if (finished) {
                progress(total, total, 0.0)
                break
            }
            Thread.sleep(POLL_INTERVAL_MS)
        }

        log("Torrent finished: ${ti.name()}")

        val singleFilePath = if (selectedSet != null && selectedSet.size == 1) {
            val idx = selectedSet.first()
            val relPath = runCatching { ti.files().filePath(idx) }.getOrNull()
            if (relPath != null) File(saveDir, relPath).absolutePath else null
        } else if (ti.numFiles() == 1) {
            val relPath = runCatching { ti.files().filePath(0) }.getOrNull()
            if (relPath != null) File(saveDir, relPath).absolutePath else File(saveDir, ti.name()).absolutePath
        } else null

        return TorrentResult(
            name = ti.name(),
            numFiles = ti.numFiles(),
            saveDir = saveDir,
            singleFilePath = singleFilePath
        )
    }

    /** libtorrent hands back a TorrentHandle asynchronously after download() is called. */
    private fun waitForHandle(ti: TorrentInfo): TorrentHandle? {
        repeat(HANDLE_WAIT_ATTEMPTS) {
            val h = TorrentSession.instance.find(ti.infoHash())
            if (h != null) return h
            Thread.sleep(HANDLE_WAIT_INTERVAL_MS)
        }
        return TorrentSession.instance.find(ti.infoHash())
    }

    private fun checkpoint() {
        if (cancelled.get()) throw DownloadCancelledException()
    }

    companion object {
        private const val METADATA_TIMEOUT_SECONDS = 60
        private const val POLL_INTERVAL_MS = 700L
        private const val HANDLE_WAIT_ATTEMPTS = 50
        private const val HANDLE_WAIT_INTERVAL_MS = 200L
    }
}
