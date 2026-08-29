package com.invictus.xmd.ui

import android.os.Bundle
import android.os.Environment
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.invictus.xmd.R
import com.invictus.xmd.core.BookmarkRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Dedicated Settings screen -- replaces the old single-dialog Settings UI.
 * This Activity hosts a root category list ([SettingsRootFragment]); tapping
 * a category pushes its Fragment into [R.id.settingsFragmentContainer] via
 * addToBackStack, same manual FragmentManager pattern MainActivity already
 * uses for Home/Downloads/Browser/History (no Jetpack Navigation component
 * in this codebase, so we don't introduce one here either).
 *
 * The header (back button + title) is drawn once here rather than per
 * fragment; each pushed fragment updates [setHeaderTitle] instead of
 * carrying its own toolbar, matching the self-drawn-header convention
 * already used by HistoryFragment.
 */
class SettingsActivity : AppCompatActivity(),
    SettingsDownloadsFragment.Callbacks {

    private lateinit var headerTitle: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        headerTitle = findViewById(R.id.settingsHeaderTitle)
        findViewById<ImageButton>(R.id.settingsBackButton).setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settingsFragmentContainer, SettingsRootFragment(), TAG_ROOT)
                .commit()
        }

        // Keep the header title in sync with whichever fragment is on top,
        // including after a system back navigation pops the back stack.
        supportFragmentManager.addOnBackStackChangedListener { syncHeaderTitle() }

        onBackPressedDispatcher.addCallback(this) {
            if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack()
            } else {
                finish()
            }
        }
    }

    private fun syncHeaderTitle() {
        val top = supportFragmentManager.findFragmentById(R.id.settingsFragmentContainer)
        headerTitle.text = when (top) {
            is SettingsAppearanceFragment -> getString(R.string.settings_category_appearance)
            is SettingsConnectionsFragment -> getString(R.string.settings_category_connections)
            is SettingsDownloadsFragment -> getString(R.string.settings_category_downloads)
            is SettingsYoutubeFragment -> getString(R.string.settings_category_youtube)
            is AboutFragment -> getString(R.string.settings_category_about)
            else -> getString(R.string.settings_title)
        }
    }

    /** Called by [SettingsRootFragment] when a category row is tapped. */
    fun openCategory(fragment: Fragment, tag: String) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                android.R.anim.fade_in,
                android.R.anim.fade_out,
                android.R.anim.fade_in,
                android.R.anim.fade_out,
            )
            .replace(R.id.settingsFragmentContainer, fragment, tag)
            .addToBackStack(tag)
            .commit()
        // Title updates on the next frame via addOnBackStackChangedListener,
        // but set it immediately too so there's no stale-title flash before
        // that callback fires.
        headerTitle.text = when (fragment) {
            is SettingsAppearanceFragment -> getString(R.string.settings_category_appearance)
            is SettingsConnectionsFragment -> getString(R.string.settings_category_connections)
            is SettingsDownloadsFragment -> getString(R.string.settings_category_downloads)
            is SettingsYoutubeFragment -> getString(R.string.settings_category_youtube)
            is AboutFragment -> getString(R.string.settings_category_about)
            else -> getString(R.string.settings_title)
        }
    }

    // ── SettingsDownloadsFragment.Callbacks: website source-pack import ────
    // Moved verbatim from MainActivity.startWebImportFlow() / friends -- the
    // Import Websites action lives in the Downloads settings screen now, and
    // this logic is fully self-contained (BookmarkRepository only), so it's
    // relocated here rather than delegated back across Activities.

    override fun startWebImportFlow() {
        Toast.makeText(this, R.string.import_websites_scanning, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val files = withContext(Dispatchers.IO) { BookmarkRepository.findImportCandidates() }
            if (files.isEmpty()) {
                Toast.makeText(this@SettingsActivity, R.string.import_websites_not_found, Toast.LENGTH_LONG).show()
            } else {
                showImportCandidatesDialog(files)
            }
        }
    }

    private fun showImportCandidatesDialog(files: List<File>) {
        val storageRoot = Environment.getExternalStorageDirectory().path
        val labels = files.map { it.path.removePrefix(storageRoot).trimStart('/') }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.import_websites_title)
            .setItems(labels) { _, which -> runWebImport(files[which]) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun runWebImport(file: File) {
        lifecycleScope.launch {
            val result = BookmarkRepository.importWebsites(file)
            val message = if (result.imported > 0) {
                getString(R.string.import_websites_success, result.imported)
            } else {
                getString(R.string.import_websites_none_new)
            }
            Toast.makeText(this@SettingsActivity, message, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val TAG_ROOT = "settings_root"
    }
}
