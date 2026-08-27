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
import com.invictus.xmd.core.HistoryEntry
import com.invictus.xmd.core.HistoryRepository

/** Browser tab visited-page history: list, swipe-to-delete, Clear all, tap to reopen,
 *  and an in-memory search box that filters the currently loaded entries by title/URL. */
class HistoryFragment : Fragment() {

    interface Callbacks {
        /** Reopens the given URL in the Browser tab. */
        fun openInBrowser(url: String)
    }

    private lateinit var backButton: ImageButton
    private lateinit var clearAllLabel: TextView
    private lateinit var searchInput: EditText
    private lateinit var list: RecyclerView
    private lateinit var emptyLabel: TextView
    private lateinit var adapter: HistoryAdapter

    // Full, unfiltered set as last delivered by HistoryRepository -- the
    // source of truth the search box filters against. Kept separate from
    // whatever's currently bound to the adapter so a new DB emission and a
    // query edit can each re-derive the visible list independently.
    private var allEntries: List<HistoryEntry> = emptyList()
    private var currentQuery: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_history, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        backButton = view.findViewById(R.id.historyBackButton)
        clearAllLabel = view.findViewById(R.id.historyClearAll)
        searchInput = view.findViewById(R.id.historySearchInput)
        list = view.findViewById(R.id.historyList)
        emptyLabel = view.findViewById(R.id.historyEmptyLabel)

        adapter = HistoryAdapter(
            onTap = { entry ->
                (activity as? Callbacks)?.openInBrowser(entry.url)
                parentFragmentManager.popBackStack()
            },
            onDeleteTap = { entry -> HistoryRepository.remove(entry) }
        )
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter

        attachSwipeToDelete()
        setupSearch()

        backButton.setOnClickListener { parentFragmentManager.popBackStack() }
        clearAllLabel.setOnClickListener { confirmClearAll() }

        HistoryRepository.entries.observe(viewLifecycleOwner) { entries ->
            allEntries = entries
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

    /** Matches on title OR URL, case-insensitive substring -- same casual
     *  match style as the address-bar suggestions, not a fielded search. */
    private fun applyFilter() {
        val query = currentQuery.trim()
        val visible = if (query.isEmpty()) {
            allEntries
        } else {
            allEntries.filter { entry ->
                entry.title.contains(query, ignoreCase = true) || entry.url.contains(query, ignoreCase = true)
            }
        }
        adapter.submitList(visible)
        emptyLabel.text = if (query.isEmpty()) getString(R.string.history_empty) else getString(R.string.history_search_empty)
        emptyLabel.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun attachSwipeToDelete() {
        val callback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val entry: HistoryEntry = adapter.entryAt(viewHolder.bindingAdapterPosition) ?: return
                HistoryRepository.remove(entry)
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(list)
    }

    private fun confirmClearAll() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.history_clear_all)
            .setPositiveButton(R.string.history_clear_all) { _, _ ->
                HistoryRepository.clearAll()
                android.widget.Toast.makeText(requireContext(), R.string.history_cleared_toast, android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
