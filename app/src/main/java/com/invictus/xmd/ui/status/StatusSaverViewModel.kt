package com.invictus.xmd.ui.status

import android.app.Application
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.invictus.xmd.R
import com.invictus.xmd.domain.status.SaveResult
import com.invictus.xmd.domain.status.StatusItem
import com.invictus.xmd.domain.status.StatusSaverRepository
import com.invictus.xmd.domain.status.StatusSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal enum class StatusTab { Recent, Saved }

internal data class StatusMessage(@StringRes val resId: Int, val count: Int = 0)

private class StatusSnapshot(
    val recent: List<StatusItem>,
    val saved: List<StatusItem>,
)

internal class StatusSaverViewModel(application: Application) : AndroidViewModel(application) {

    var tab by mutableStateOf(StatusTab.Recent)
        private set
    var source by mutableStateOf(StatusSource.WHATSAPP)
        private set
    var recent by mutableStateOf<List<StatusItem>>(emptyList())
        private set
    var saved by mutableStateOf<List<StatusItem>>(emptyList())
        private set
    var savedNames by mutableStateOf<Set<String>>(emptySet())
        private set
    var hasAccess by mutableStateOf(true)
        private set
    var loading by mutableStateOf(true)
        private set
    var selected by mutableStateOf<Set<String>>(emptySet())
        private set

    private val _messages = MutableSharedFlow<StatusMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<StatusMessage> = _messages.asSharedFlow()

    val currentItems: List<StatusItem>
        get() = if (tab == StatusTab.Recent) recent else saved

    fun refresh() {
        val context = getApplication<Application>()
        val requestedSource = source
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                if (!StatusSaverRepository.hasAccess(context)) {
                    null
                } else {
                    StatusSnapshot(
                        recent = StatusSaverRepository.listRecent(requestedSource),
                        saved = StatusSaverRepository.listSaved(),
                    )
                }
            }
            if (requestedSource != source) return@launch // source changed mid-load
            hasAccess = snapshot != null
            if (snapshot != null) {
                recent = snapshot.recent
                saved = snapshot.saved
                savedNames = snapshot.saved.mapTo(HashSet()) { it.name }
                val live = currentItems.mapTo(HashSet()) { it.path }
                selected = selected.filterTo(HashSet()) { it in live }
            }
            loading = false
        }
    }

    fun selectTab(newTab: StatusTab) {
        if (newTab == tab) return
        tab = newTab
        selected = emptySet()
    }

    fun selectSource(newSource: StatusSource) {
        if (newSource == source) return
        source = newSource
        selected = emptySet()
        recent = emptyList()
        loading = true
        refresh()
    }

    fun toggleSelect(path: String) {
        selected = if (path in selected) selected - path else selected + path
    }

    fun selectAll() {
        selected = currentItems.mapTo(HashSet()) { it.path }
    }

    fun clearSelection() {
        selected = emptySet()
    }

    fun save(items: List<StatusItem>) {
        if (items.isEmpty()) return
        val context = getApplication<Application>()
        val sourceAtSave = source
        viewModelScope.launch {
            val results = withContext(Dispatchers.IO) {
                items.map { StatusSaverRepository.save(context, sourceAtSave, File(it.path)) }
            }
            val savedCount = results.count { it is SaveResult.Saved }
            val failedCount = results.count { it is SaveResult.Failed }
            selected = emptySet()
            _messages.tryEmit(
                when {
                    savedCount > 0 -> StatusMessage(R.string.status_saved_n, savedCount)
                    failedCount > 0 -> StatusMessage(R.string.status_save_failed)
                    else -> StatusMessage(R.string.status_already_saved)
                },
            )
            refresh()
        }
    }

    fun delete(items: List<StatusItem>) {
        if (items.isEmpty()) return
        val context = getApplication<Application>()
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) {
                StatusSaverRepository.deleteSaved(context, items.map { it.path })
            }
            selected = emptySet()
            _messages.tryEmit(StatusMessage(R.string.status_deleted_n, deleted))
            refresh()
        }
    }
}
