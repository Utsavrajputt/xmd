package com.invictus.xmd.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.invictus.xmd.R
import com.invictus.xmd.core.Shortcut
import com.invictus.xmd.core.FaviconLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Chrome-style speed-dial grid: one tile per shortcut plus a trailing
 * "+" tile to add a new one. Tap opens the URL; long-press on a real
 * shortcut tile offers edit/delete (handled by the fragment via
 * [onLongPress]).
 */
class ShortcutAdapter(
    private val onTap: (Shortcut) -> Unit,
    private val onLongPress: (Shortcut) -> Unit,
    private val onAddTap: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var shortcuts: List<Shortcut> = emptyList()

    // One scope for every favicon fetch this adapter kicks off; cancelled as
    // a whole when the RecyclerView detaches (fragment/view destroyed) so no
    // fetch outlives the screen that asked for it.
    private val scope = CoroutineScope(Dispatchers.Main)

    companion object {
        private const val VIEW_TYPE_SHORTCUT = 0
        private const val VIEW_TYPE_ADD = 1
    }

    fun submitList(items: List<Shortcut>) {
        shortcuts = items
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = shortcuts.size + 1

    override fun getItemViewType(position: Int): Int =
        if (position < shortcuts.size) VIEW_TYPE_SHORTCUT else VIEW_TYPE_ADD

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_SHORTCUT) {
            ShortcutViewHolder(inflater.inflate(R.layout.item_shortcut_tile, parent, false))
        } else {
            AddTileViewHolder(inflater.inflate(R.layout.item_shortcut_add_tile, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ShortcutViewHolder) {
            val shortcut = shortcuts[position]
            holder.title.text = shortcut.title
            holder.itemView.setOnClickListener { onTap(shortcut) }
            holder.itemView.setOnLongClickListener { onLongPress(shortcut); true }
            bindFavicon(holder, shortcut)
        } else if (holder is AddTileViewHolder) {
            holder.itemView.setOnClickListener { onAddTap() }
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        scope.cancel()
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is ShortcutViewHolder) {
            holder.faviconJob?.cancel()
            holder.faviconJob = null
        }
    }

    /**
     * Loads the tile's real favicon in the background (FaviconLoader has its
     * own cache, so repeat binds of the same host are cheap). Falls back to
     * -- i.e. simply never replaces -- the generic ic_link icon already set
     * in the layout XML if the fetch fails or the view gets recycled before
     * it completes.
     */
    private fun bindFavicon(holder: ShortcutViewHolder, shortcut: Shortcut) {
        holder.faviconJob?.cancel()
        // Reset to the generic icon immediately so a recycled row doesn't
        // briefly show the previous shortcut's favicon before this one loads.
        holder.favicon.setImageResource(R.drawable.ic_link)
        holder.favicon.setPadding(holder.faviconDefaultPadding, holder.faviconDefaultPadding, holder.faviconDefaultPadding, holder.faviconDefaultPadding)
        holder.favicon.imageTintList = android.content.res.ColorStateList.valueOf(
            resolveThemeColor(holder.favicon.context, com.google.android.material.R.attr.colorPrimary)
        )

        holder.faviconJob = scope.launch {
            val bitmap = kotlinx.coroutines.withContext(Dispatchers.IO) { FaviconLoader.load(shortcut.url) }
            if (bitmap != null && holder.bindingAdapterPosition != RecyclerView.NO_POSITION &&
                shortcuts.getOrNull(holder.bindingAdapterPosition)?.id == shortcut.id
            ) {
                holder.favicon.imageTintList = null
                holder.favicon.setPadding(0, 0, 0, 0)
                holder.favicon.setImageBitmap(bitmap)
            }
        }
    }

    /** Resolves a color from the current active theme (Theme.Xmd.*) instead
     *  of a static @color resource, so the favicon fallback tint follows
     *  the selected app theme. */
    private fun resolveThemeColor(context: android.content.Context, attrResId: Int): Int {
        val tv = android.util.TypedValue()
        context.theme.resolveAttribute(attrResId, tv, true)
        return tv.data
    }

    class ShortcutViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val favicon: ImageView = view.findViewById(R.id.tileFavicon)
        val title: TextView = view.findViewById(R.id.tileTitle)
        val faviconDefaultPadding: Int = favicon.paddingLeft
        var faviconJob: Job? = null
    }

    class AddTileViewHolder(view: View) : RecyclerView.ViewHolder(view)
}
