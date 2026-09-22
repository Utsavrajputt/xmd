package com.invictus.xmd.repository

import com.invictus.xmd.database.entities.QueueItem
import com.invictus.xmd.domain.download.DownloadCategory
import com.invictus.xmd.domain.download.ItemStatus
import com.invictus.xmd.utils.storage.OnDuplicateStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class QueueRepositoryEnqueueTest {

    @Test
    fun simultaneousEnqueuesReserveDifferentNames() {
        withTempDirectory { directory ->
            val start = CountDownLatch(1)
            val pool = Executors.newFixedThreadPool(2)
            val items = List(2) { queueItem(directory, "video.mp4") }

            try {
                val futures = items.map { item ->
                    pool.submit<QueueRepository.EnqueueResult> {
                        start.await()
                        QueueRepository.enqueueResolvingDuplicate(item, null)
                    }
                }
                start.countDown()

                val names = futures.map { future ->
                    (future.get() as QueueRepository.EnqueueResult.Success).item.fileName
                }.toSet()

                assertEquals(setOf("video.mp4", "video_1.mp4"), names)
            } finally {
                items.forEach { QueueRepository.removeItem(it.id) }
                pool.shutdownNow()
            }
        }
    }

    @Test
    fun overrideRejectsItemThatMayStillWriteDestination() {
        withTempDirectory { directory ->
            val existing = queueItem(directory, "video.mp4", ItemStatus.DOWNLOADING)
            val replacement = queueItem(directory, "video.mp4")

            try {
                QueueRepository.enqueueResolvingDuplicate(existing, null)
                val result = QueueRepository.enqueueResolvingDuplicate(
                    replacement,
                    OnDuplicateStrategy.OverrideDownload,
                )

                assertTrue(result is QueueRepository.EnqueueResult.ActiveConflict)
                assertTrue(QueueRepository.current().any { it.id == existing.id })
                assertFalse(QueueRepository.current().any { it.id == replacement.id })
            } finally {
                QueueRepository.removeItem(existing.id)
                QueueRepository.removeItem(replacement.id)
            }
        }
    }

    @Test
    fun overrideDeletesCompletedTargetAndReplacesQueueItem() {
        withTempDirectory { directory ->
            val target = File(directory, "video.mp4").apply { writeText("old") }
            val existing = queueItem(directory, target.name, ItemStatus.DONE)
            val replacement = queueItem(directory, target.name)

            try {
                QueueRepository.enqueueResolvingDuplicate(existing, null)
                val result = QueueRepository.enqueueResolvingDuplicate(
                    replacement,
                    OnDuplicateStrategy.OverrideDownload,
                )

                assertTrue(result is QueueRepository.EnqueueResult.Success)
                assertFalse(target.exists())
                assertFalse(QueueRepository.current().any { it.id == existing.id })
                assertTrue(QueueRepository.current().any { it.id == replacement.id })
            } finally {
                QueueRepository.removeItem(existing.id)
                QueueRepository.removeItem(replacement.id)
            }
        }
    }

    @Test
    fun failedOverrideDeletionLeavesQueueUnchanged() {
        withTempDirectory { directory ->
            val target = File(directory, "occupied").apply {
                mkdirs()
                File(this, "child").writeText("keeps directory non-empty")
            }
            val existing = queueItem(directory, target.name, ItemStatus.DONE)
            val replacement = queueItem(directory, target.name)

            try {
                QueueRepository.enqueueResolvingDuplicate(existing, null)
                val result = QueueRepository.enqueueResolvingDuplicate(
                    replacement,
                    OnDuplicateStrategy.OverrideDownload,
                )

                assertTrue(result is QueueRepository.EnqueueResult.DeleteFailed)
                assertTrue(QueueRepository.current().any { it.id == existing.id })
                assertFalse(QueueRepository.current().any { it.id == replacement.id })
            } finally {
                QueueRepository.removeItem(existing.id)
                QueueRepository.removeItem(replacement.id)
            }
        }
    }

    @Test
    fun lateYoutubeProgressDoesNotRevivePausedItem() {
        withTempDirectory { directory ->
            val item = queueItem(directory, "video.mp4", ItemStatus.DOWNLOADING)

            try {
                QueueRepository.enqueueResolvingDuplicate(item, null)
                QueueRepository.reportYoutubeProgress(item.id, 42, "Downloading")
                QueueRepository.markPaused(item.id, resetMediaProgress = true)

                QueueRepository.reportYoutubeProgress(item.id, 43, "Downloading")

                val paused = QueueRepository.current().first { it.id == item.id }
                assertEquals(ItemStatus.PAUSED, paused.status)
                assertEquals(-1, paused.progressPercent)
            } finally {
                QueueRepository.removeItem(item.id)
            }
        }
    }

    @Test
    fun lateCompletionDoesNotOverrideCancelledItem() {
        withTempDirectory { directory ->
            val item = queueItem(directory, "video.mp4", ItemStatus.DOWNLOADING)

            try {
                QueueRepository.enqueueResolvingDuplicate(item, null)
                QueueRepository.markFailed(item.id, "Cancelled")

                QueueRepository.markDone(item.id, File(directory, "video.mp4").absolutePath)

                val cancelled = QueueRepository.current().first { it.id == item.id }
                assertEquals(ItemStatus.FAILED, cancelled.status)
                assertEquals("Cancelled", cancelled.error)
            } finally {
                QueueRepository.removeItem(item.id)
            }
        }
    }

    private fun queueItem(
        directory: File,
        fileName: String,
        status: ItemStatus = ItemStatus.READY,
    ) = QueueItem(
        id = UUID.randomUUID().toString(),
        sourceUrl = "https://example.com/$fileName",
        directUrl = "https://example.com/$fileName",
        fileName = fileName,
        customSaveDirPath = directory.absolutePath,
        category = DownloadCategory.OTHERS,
        status = status,
    )

    private fun withTempDirectory(block: (File) -> Unit) {
        val directory = File(
            System.getProperty("java.io.tmpdir"),
            "xmd_enqueue_${UUID.randomUUID()}",
        ).apply { mkdirs() }
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
