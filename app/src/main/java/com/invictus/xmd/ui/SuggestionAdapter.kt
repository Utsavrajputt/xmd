package com.invictus.xmd.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.invictus.xmd.R

/**
 * Row = either a Google search phrase or a matching local history entry
 * (see BrowserFragment.scheduleSuggest, which merges both into one list --
 * search results first, then a handful of history matches, Chrome-style).
 * Tapping a SEARCH row loads [text] as a search/URL like manual address-bar
 * entry; tapping a HISTORY row loads [url] directly, skipping normalization
 * since it's already a real visited URL. The "+" button only makes sense
 * for a search phrase (bookmarking a raw query), so it's hidden on history
 * rows.
 */
class SuggestionAdapter(
    private val onTap: (Suggestion) -> Unit,
    private val onAddTap: (String) -> Unit
) : RecyclerView.Adapter<SuggestionAdapter.ViewHolder>() {

    sealed class Suggestion {
        abstract val text: String
        data class Search(override val text: String) : Suggestion()
        data class History(override val text: String, val url: String) : Suggestion()
    }

    private var suggestions: List<Suggestion> = emptyList()

    fun submitList(items: List<Suggestion>) {
        suggestions = items
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_suggestion, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = suggestions[position]
        holder.text.text = item.text
        holder.icon.setImageResource(
            if (item is Suggestion.History) R.drawable.ic_clock else R.drawable.ic_link
        )
        holder.itemView.setOnClickListener { onTap(item) }
        holder.addButton.visibility = if (item is Suggestion.Search) View.VISIBLE else View.GONE
        holder.addButton.setOnClickListener {
            if (item is Suggestion.Search) onAddTap(item.text)
        }
    }

    override fun getItemCount(): Int = suggestions.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.suggestionIcon)
        val text: TextView = view.findViewById(R.id.suggestionText)
        val addButton: ImageButton = view.findViewById(R.id.suggestionAddButton)
    }
}
