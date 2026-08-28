package com.invictus.xmd.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.invictus.xmd.R
import com.invictus.xmd.core.Bookmark

/** Row list for the Bookmarks screen -- same tap/delete shape as HistoryAdapter. */
class BookmarkListAdapter(
    private val onTap: (Bookmark) -> Unit,
    private val onDeleteTap: (Bookmark) -> Unit
) : RecyclerView.Adapter<BookmarkListAdapter.ViewHolder>() {

    private var items: List<Bookmark> = emptyList()

    fun submitList(list: List<Bookmark>) {
        items = list
        notifyDataSetChanged()
    }

    /** Used by swipe-to-delete: which entry backs the row at [position]. */
    fun entryAt(position: Int): Bookmark? = items.getOrNull(position)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_bookmark_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = items[position]
        holder.title.text = entry.title
        holder.url.text = entry.url
        holder.itemView.setOnClickListener { onTap(entry) }
        holder.deleteButton.setOnClickListener { onDeleteTap(entry) }
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.bookmarkItemTitle)
        val url: TextView = view.findViewById(R.id.bookmarkItemUrl)
        val deleteButton: ImageButton = view.findViewById(R.id.bookmarkItemDelete)
    }
}
