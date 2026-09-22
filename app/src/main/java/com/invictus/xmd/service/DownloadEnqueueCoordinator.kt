package com.invictus.xmd.service

import android.content.Context
import com.invictus.xmd.database.entities.QueueItem
import com.invictus.xmd.repository.QueueRepository
import com.invictus.xmd.utils.storage.OnDuplicateStrategy

object DownloadEnqueueCoordinator {

    fun enqueueAndStart(
        context: Context,
        item: QueueItem,
        duplicateStrategy: OnDuplicateStrategy?,
    ): QueueRepository.EnqueueResult {
        val result = QueueRepository.enqueueResolvingDuplicate(item, duplicateStrategy)
        if (result is QueueRepository.EnqueueResult.Success) {
            DownloadService.start(context)
        }
        return result
    }
}