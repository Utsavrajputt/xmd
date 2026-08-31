package com.invictus.xmd.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox
import com.invictus.xmd.R
import java.util.Locale

data class TorrentFileEntry(
    val index: Int,
    val path: String,
    val sizeBytes: Long,
    var isSelected: Boolean = true
)

class TorrentFileAdapter(
    private val onSelectionChanged: (selectedCount: Int, selectedBytes: Long) -> Unit
) : RecyclerView.Adapter<TorrentFileAdapter.FileViewHolder>() {

    private val files = mutableListOf<TorrentFileEntry>()

    fun setFiles(newFiles: List<TorrentFileEntry>) {
        files.clear()
        files.addAll(newFiles)
        notifyDataSetChanged()
        notifySelectionChanged()
    }

    fun selectAll(select: Boolean) {
        files.forEach { it.isSelected = select }
        notifyDataSetChanged()
        notifySelectionChanged()
    }

    fun areAllSelected(): Boolean = files.isNotEmpty() && files.all { it.isSelected }

    fun getSelectedCount(): Int = files.count { it.isSelected }

    fun getTotalCount(): Int = files.size

    fun getSelectedIndices(): List<Int> = files.filter { it.isSelected }.map { it.index }

    private fun notifySelectionChanged() {
        val count = files.count { it.isSelected }
        val bytes = files.filter { it.isSelected }.sumOf { it.sizeBytes }
        onSelectionChanged(count, bytes)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_torrent_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val item = files[position]
        holder.nameText.text = item.path.substringAfterLast('/')
        holder.sizeText.text = formatBytes(item.sizeBytes)

        holder.checkbox.setOnCheckedChangeListener(null)
        holder.checkbox.isChecked = item.isSelected

        holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
            item.isSelected = isChecked
            notifySelectionChanged()
        }

        holder.itemView.setOnClickListener {
            item.isSelected = !item.isSelected
            holder.checkbox.isChecked = item.isSelected
            notifySelectionChanged()
        }
    }

    override fun getItemCount(): Int = files.size

    class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val checkbox: MaterialCheckBox = itemView.findViewById(R.id.torrentFileCheckbox)
        val nameText: TextView = itemView.findViewById(R.id.torrentFileNameText)
        val sizeText: TextView = itemView.findViewById(R.id.torrentFileSizeText)
    }

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
            val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
            return String.format(Locale.US, "%.1f %s", value, units[digitGroups])
        }
    }
}
