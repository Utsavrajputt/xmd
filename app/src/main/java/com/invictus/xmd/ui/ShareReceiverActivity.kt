package com.invictus.xmd.ui

import android.content.Intent
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.invictus.xmd.BuildConfig
import com.invictus.xmd.R
import com.invictus.xmd.core.DownloadCategory
import com.invictus.xmd.core.ItemStatus
import com.invictus.xmd.core.LinkParser
import com.invictus.xmd.core.MediaPlatform
import com.invictus.xmd.core.QueueItem
import com.invictus.xmd.core.QueueRepository
import com.invictus.xmd.core.Settings
import com.invictus.xmd.core.YtDlpManager
import com.invictus.xmd.service.DownloadService

/**
 * Entry point for links handed to xmd from another app's "external
 * downloader" hook -- Morphe's Player > External downloads setting,
 * browsers without a download-manager chooser, etc. -- instead of
 * MainActivity.
 *
 * Why this exists instead of just handling ACTION_SEND in MainActivity
 * (which it used to): opening MainActivity brings xmd's whole UI to the
 * foreground, which briefly kicks the caller (YouTube inside Morphe, a
 * browser tab, ...) off-screen. This activity is themed fully transparent
 * (Theme.Xmd.Transparent, see themes.xml) so nothing behind it ever
 * disappears -- it just shows a small bottom sheet (quality picker for
 * YouTube, nothing at all for a plain direct-download link) and finishes
 * itself the moment a choice is made, exactly like YTDLnis/Seal do.
 *
 * Deliberately narrow in scope: only YouTube links (quality picker) and
 * plain generic-download links (queue + start immediately, no UI) are
 * handled invisibly. Share-links that need the Cloudflare-challenge
 * WebView still hand off to MainActivity -- that flow can't happen without
 * a visible screen, so there's no point pretending otherwise.
 */
class ShareReceiverActivity : AppCompatActivity() {

    // Colors/typography for the sheet are resolved against a wrapped
    // context using the user's actual theme + light/dark choice, same
    // values MainActivity applies via setTheme() -- but applied here to
    // the sheet's context only, not the activity's own (deliberately
    // invisible) window theme.
    private val themedContext by lazy {
        ContextThemeWrapper(this, Settings.appTheme().resolvedStyleRes(Settings.isDarkMode()))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent) {
        val url = extractUrl(intent)
        val isHttp = url != null && (url.startsWith("http://") || url.startsWith("https://"))
        val isMagnet = url != null && url.startsWith("magnet:", ignoreCase = true)
        if (url.isNullOrEmpty() || !(isHttp || isMagnet)) {
            Toast.makeText(this, "No link found to download", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        QueueRepository.setLinks(listOf(url))
        val item = QueueRepository.current().firstOrNull { it.sourceUrl == url } ?: run {
            finish()
            return
        }

        when {
            LinkParser.isYoutubeLink(url) -> showYoutubeQualitySheet(item)
            LinkParser.isGenericDownloadUrl(url) -> {
                QueueRepository.update(item.id) { it.copy(directUrl = url, status = ItemStatus.READY) }
                DownloadService.start(this)
                Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show()
                finish()
            }
            LinkParser.isShareLink(url) || LinkParser.isFitgirlPage(url) -> {
                // Needs the Cloudflare-challenge WebView -- no way to do
                // that invisibly, so this one case does still hand off to
                // the full app.
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .setAction(Intent.ACTION_SEND)
                        .putExtra(Intent.EXTRA_TEXT, url)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
                finish()
            }
            else -> {
                QueueRepository.update(item.id) { it.copy(status = ItemStatus.FAILED, error = "Not a valid URL: $url") }
                Toast.makeText(this, "Not a supported link", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun extractUrl(intent: Intent): String? = when (intent.action) {
        Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty().let { text ->
            if (text.startsWith("magnet:", ignoreCase = true)) text
            else Regex("""https?://\S+""").find(text)?.value
        }
        Intent.ACTION_VIEW -> intent.data?.toString()
        else -> null
    }?.trim()

    // ── YouTube quality bottom sheet ────────────────────────────────────

    private fun showYoutubeQualitySheet(item: QueueItem) {
        if (!BuildConfig.HAS_YOUTUBE_SUPPORT) {
            Toast.makeText(this, "YouTube needs the Full build of xmd", Toast.LENGTH_LONG).show()
            QueueRepository.update(item.id) { it.copy(status = ItemStatus.FAILED, error = "YouTube needs the Full build") }
            finish()
            return
        }
        if (!YtDlpManager.isInstalled(this)) {
            Toast.makeText(this, "Install yt-dlp from xmd's Settings first", Toast.LENGTH_LONG).show()
            QueueRepository.update(item.id) { it.copy(status = ItemStatus.FAILED, error = "yt-dlp not installed") }
            finish()
            return
        }

        val dialog = BottomSheetDialog(themedContext)
        val view = layoutInflater.cloneInContext(themedContext).inflate(R.layout.sheet_share_quality, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)

        view.findViewById<TextView>(R.id.sheetTitle).text = item.fileName ?: "Choose quality"
        view.findViewById<TextView>(R.id.sheetSubtitle).apply {
            text = item.sourceUrl
            visibility = View.VISIBLE
        }

        val group = view.findViewById<RadioGroup>(R.id.qualityGroup)
        val options = YtDlpManager.standardQualityOptions()
        options.forEach { option ->
            // Plain platform RadioButton with an AppCompat-lineage style
            // resource crashes off-theme here the same way it would in
            // MainActivity's picker (see resolveYoutube's comment) --
            // AppCompatRadioButton built + styled by hand instead.
            val row = AppCompatRadioButton(themedContext)
            row.id = View.generateViewId()
            row.text = option.label
            row.isClickable = true
            row.buttonDrawable = null
            row.setBackgroundResource(R.drawable.bg_radio_row_selector)
            row.setTextColor(ContextCompat.getColorStateList(themedContext, R.color.text_radio_row))
            row.textSize = 14f
            row.gravity = Gravity.CENTER_VERTICAL
            val density = resources.displayMetrics.density
            row.setPadding((16 * density).toInt(), (14 * density).toInt(), (16 * density).toInt(), (14 * density).toInt())
            val startIcon = if (option.isAudioOnly) R.drawable.ic_music_note else R.drawable.ic_video
            row.setCompoundDrawablesWithIntrinsicBounds(startIcon, 0, R.drawable.ic_check_selector, 0)
            row.compoundDrawablePadding = (12 * density).toInt()
            row.tag = option
            row.layoutParams = RadioGroup.LayoutParams(
                RadioGroup.LayoutParams.MATCH_PARENT,
                RadioGroup.LayoutParams.WRAP_CONTENT
            )
            group.addView(row)
        }
        // Same default as MainActivity's picker: one below the top of the
        // ladder (1440p) rather than either extreme.
        (group.getChildAt(1) as? RadioButton)?.isChecked = true

        var resolved = false

        view.findViewById<View>(R.id.btnDownload).setOnClickListener {
            val checked = group.findViewById<RadioButton>(group.checkedRadioButtonId)
            val chosen = checked?.tag as? YtDlpManager.QualityOption
            resolved = true
            if (chosen == null) {
                QueueRepository.update(item.id) { it.copy(status = ItemStatus.FAILED, error = "Cancelled") }
            } else {
                QueueRepository.update(item.id) {
                    it.copy(
                        status = ItemStatus.READY,
                        platform = MediaPlatform.YOUTUBE,
                        mediaFormatSelector = chosen.formatSelector,
                        mediaFormatLabel = chosen.label,
                        category = if (chosen.isAudioOnly) DownloadCategory.MUSIC else DownloadCategory.VIDEOS
                    )
                }
                DownloadService.start(this)
                Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }

        dialog.setOnDismissListener {
            if (!resolved) {
                QueueRepository.update(item.id) { it.copy(status = ItemStatus.FAILED, error = "Cancelled") }
            }
            // Whether downloaded, cancelled, or swiped away -- this
            // activity's only job was to get the sheet up, so it's done
            // either way. Finishing here (not immediately after dismiss())
            // is what lets the caller app stay in front the whole time;
            // there's nothing left for xmd to show.
            finish()
        }

        dialog.show()
    }
}
