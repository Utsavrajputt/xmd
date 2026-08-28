package com.invictus.xmd.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.invictus.xmd.R
import com.invictus.xmd.core.Bookmark
import com.invictus.xmd.core.BookmarkRepository

/** Saved-pages screen for real bookmarks (star button in the Browser
 *  toolbar) -- list, swipe-to-delete, Clear all, tap to reopen, and an
 *  in-memory search box. Same shape as HistoryFragment. */
class BookmarkFragment : Fragment() {

    interface Callbacks {
        /** Reopens the given URL in the Browser tab. */
        fun openBookmarkInBrowser(url: String)
    }

    private lateinit var backButton: ImageButton
    private lateinit var clearAllLabel: TextView
    private lateinit var searchInput: EditText
    private lateinit var list: RecyclerView
    private lateinit var emptyLabel: TextView
    private lateinit var adapter: BookmarkListAdapter

    // Full, unfiltered set as last delivered by BookmarkRepository -- the
    // source of truth the search box filters against.
    private var allBookmarks: List<Bookmark> = emptyList()
    private var currentQuery: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_bookmarks, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        backButton = view.findViewById(R.id.bookmarksBackButton)
        clearAllLabel = view.findViewById(R.id.bookmarksClearAll)
        searchInput = view.findViewById(R.id.bookmarksSearchInput)
        list = view.findViewById(R.id.bookmarksList)
        emptyLabel = view.findViewById(R.id.bookmarksEmptyLabel)

        adapter = BookmarkListAdapter(
            onTap = { bookmark ->
                (activity as? Callbacks)?.openBookmarkInBrowser(bookmark.url)
                parentFragmentManager.popBackStack()
            },
            onDeleteTap = { bookmark -> BookmarkRepository.remove(bookmark) }
        )
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter

        attachSwipeToDelete()
        setupSearch()

        backButton.setOnClickListener { parentFragmentManager.popBackStack() }
        clearAllLabel.setOnClickListener { confirmClearAll() }

        BookmarkRepository.bookmarks.observe(viewLifecycleOwner) { bookmarks ->
            allBookmarks = bookmarks
            applyFilter()
        }
    }

    private fun setupSearch() {
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentQuery = s?.toString().orEmpty()
                applyFilter()
            }
        })
    }

    /** Matches on title OR URL, case-insensitive substring. */
    private fun applyFilter() {
        val query = currentQuery.trim()
        val visible = if (query.isEmpty()) {
            allBookmarks
        } else {
            allBookmarks.filter { entry ->
                entry.title.contains(query, ignoreCase = true) || entry.url.contains(query, ignoreCase = true)
            }
        }
        adapter.submitList(visible)
        emptyLabel.text = if (query.isEmpty()) getString(R.string.bookmarks_empty) else getString(R.string.bookmarks_search_empty)
        emptyLabel.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun attachSwipeToDelete() {
        val callback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val entry: Bookmark = adapter.entryAt(viewHolder.bindingAdapterPosition) ?: return
                BookmarkRepository.remove(entry)
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(list)
    }

    private fun confirmClearAll() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.bookmarks_clear_all)
            .setPositiveButton(R.string.bookmarks_clear_all) { _, _ ->
                BookmarkRepository.clearAll()
                android.widget.Toast.makeText(requireContext(), R.string.bookmarks_cleared_toast, android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
