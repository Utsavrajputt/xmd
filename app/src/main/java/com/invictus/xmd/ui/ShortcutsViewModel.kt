package com.invictus.xmd.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import com.invictus.xmd.core.Shortcut
import com.invictus.xmd.core.ShortcutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns everything the Compose speed-dial grid (see ShortcutsScreen.kt) needs:
 * the shortcut list, an in-progress reorder session, and which of the
 * add/edit/options dialogs (if any) is showing -- state that previously
 * lived as scattered fields/functions on BrowserFragment
 * (setupSpeedDial/showAddShortcutDialog/showEditShortcutDialog/
 * showShortcutOptionsDialog/wireIconPicker).
 *
 * This is the first ViewModel in the app -- every earlier Compose phase
 * (Settings/Bookmarks/Downloads) got away with the Fragment holding state
 * directly since Settings.x()/Repository observation was simple enough.
 * The speed-dial grid's reorder-session + multi-dialog state was the first
 * case that stopped being simple, hence introducing one here.
 *
 * [Uri] handling: the actual `ActivityResultLauncher<String>` (system photo
 * picker) has to stay registered on BrowserFragment -- that's an Android
 * platform requirement, a ViewModel can't own an ActivityResultLauncher.
 * The Fragment forwards the picked Uri here via [onIconPicked]; everything
 * downstream of that (preview, copying into internal storage, persisting)
 * is business logic and lives here.
 */
class ShortcutsViewModel(app: Application) : AndroidViewModel(app) {

    sealed interface Dialog {
        data object None : Dialog
        data object Add : Dialog
        data class Edit(val shortcut: Shortcut) : Dialog
        data class Options(val shortcut: Shortcut) : Dialog
    }

    // The grid's current order. Mirrors ShortcutRepository.shortcuts except
    // while a reorder session is active (see reorderMode below), during
    // which this is the local drag order and the repository is the source
    // of truth again only once "Done" commits it -- same guard the old
    // ShortcutAdapter.submitList()-vs-reorderMode check made.
    private val _displayOrder = MutableStateFlow<List<Shortcut>>(emptyList())
    val displayOrder: StateFlow<List<Shortcut>> = _displayOrder.asStateFlow()

    private val _reorderMode = MutableStateFlow(false)
    val reorderMode: StateFlow<Boolean> = _reorderMode.asStateFlow()

    private val _dialog = MutableStateFlow<Dialog>(Dialog.None)
    val dialog: StateFlow<Dialog> = _dialog.asStateFlow()

    // Set once the photo picker returns a Uri for the currently-open add/edit
    // dialog; consumed (and cleared) on save, or cleared on dismiss.
    private val _pendingIconUri = MutableStateFlow<Uri?>(null)
    val pendingIconUri: StateFlow<Uri?> = _pendingIconUri.asStateFlow()

    init {
        ShortcutRepository.init(app)
        viewModelScope.launch {
            ShortcutRepository.shortcuts.asFlow().collect { list ->
                if (!_reorderMode.value) _displayOrder.value = list
            }
        }
    }

    // ── Reorder session ──────────────────────────────────────────────────

    fun toggleReorderMode() {
        if (_reorderMode.value) {
            ShortcutRepository.reorder(_displayOrder.value.map { it.id })
            _reorderMode.value = false
        } else {
            _reorderMode.value = true
        }
    }

    /** Called continuously while a tile is being dragged over another. */
    fun moveItem(from: Int, to: Int) {
        val list = _displayOrder.value
        if (from !in list.indices || to !in list.indices) return
        _displayOrder.value = list.toMutableList().apply { add(to, removeAt(from)) }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────

    fun onAddTapped() {
        _pendingIconUri.value = null
        _dialog.value = Dialog.Add
    }

    fun onTileLongPressed(shortcut: Shortcut) {
        _dialog.value = Dialog.Options(shortcut)
    }

    fun onEditSelected(shortcut: Shortcut) {
        _pendingIconUri.value = null
        _dialog.value = Dialog.Edit(shortcut)
    }

    fun onDeleteSelected(shortcut: Shortcut) {
        ShortcutRepository.remove(shortcut)
        _dialog.value = Dialog.None
    }

    fun onDialogDismissed() {
        _dialog.value = Dialog.None
        _pendingIconUri.value = null
    }

    fun onIconPicked(uri: Uri) {
        _pendingIconUri.value = uri
    }

    fun onSaveAdd(title: String, url: String) {
        val normalized = normalizeToUrl(url)
        val icon = _pendingIconUri.value
        val context = getApplication<Application>()
        viewModelScope.launch {
            if (icon != null) {
                ShortcutRepository.addWithIcon(context, title, normalized, icon)
            } else {
                ShortcutRepository.add(title, normalized)
            }
        }
        _dialog.value = Dialog.None
        _pendingIconUri.value = null
    }

    fun onSaveEdit(shortcut: Shortcut, title: String, url: String) {
        val normalized = normalizeToUrl(url)
        val newTitle = title.ifBlank { runCatching { java.net.URI(normalized).host }.getOrNull() ?: normalized }
        val icon = _pendingIconUri.value
        val context = getApplication<Application>()
        viewModelScope.launch {
            if (icon != null) {
                val path = ShortcutRepository.copyIconToInternalStorage(context, icon, shortcut.id)
                ShortcutRepository.update(
                    shortcut.copy(title = newTitle, url = normalized, customIconPath = path ?: shortcut.customIconPath)
                )
            } else {
                ShortcutRepository.update(shortcut.copy(title = newTitle, url = normalized))
            }
        }
        _dialog.value = Dialog.None
        _pendingIconUri.value = null
    }

    /** Bare host/search text -> https URL; anything already URL-shaped is
     *  passed through. Duplicated from BrowserFragment.normalizeToUrl
     *  (address-bar-scoped, private) rather than shared -- this one is
     *  scoped to shortcut persistence and the two are free to diverge. */
    private fun normalizeToUrl(input: String): String {
        val looksLikeUrl = input.contains(".") && !input.contains(" ")
        return when {
            input.startsWith("http://") || input.startsWith("https://") -> input
            looksLikeUrl -> "https://$input"
            else -> "https://www.google.com/search?q=${Uri.encode(input)}"
        }
    }
}
