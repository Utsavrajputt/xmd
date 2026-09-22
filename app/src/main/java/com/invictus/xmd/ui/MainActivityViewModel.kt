package com.invictus.xmd.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.invictus.xmd.ui.browser.SavedPagesDestination
import com.invictus.xmd.ui.components.MainDestination

internal class MainActivityViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    var mainDestination by mutableStateOf(
        savedStateHandle.get<String>(KEY_MAIN_DESTINATION)
            ?.let { name -> MainDestination.entries.firstOrNull { it.name == name } }
            ?: MainDestination.Downloads,
    )
        private set

    var headerSearchActive by mutableStateOf(savedStateHandle[KEY_SEARCH_ACTIVE] ?: false)
        private set

    var headerSearchQuery by mutableStateOf(savedStateHandle[KEY_SEARCH_QUERY] ?: "")
        private set

    var savedPagesDestination by mutableStateOf(
        savedStateHandle.get<String>(KEY_SAVED_PAGES_DESTINATION)
            ?.let { name -> SavedPagesDestination.entries.firstOrNull { it.name == name } },
    )
        private set

    fun initializeMainDestination(defaultDestination: MainDestination) {
        if (!savedStateHandle.contains(KEY_MAIN_DESTINATION)) {
            updateMainDestination(defaultDestination)
        }
    }

    fun updateMainDestination(destination: MainDestination) {
        mainDestination = destination
        savedStateHandle[KEY_MAIN_DESTINATION] = destination.name
    }

    fun updateHeaderSearchActive(active: Boolean) {
        headerSearchActive = active
        savedStateHandle[KEY_SEARCH_ACTIVE] = active
    }

    fun updateHeaderSearchQuery(query: String) {
        headerSearchQuery = query
        savedStateHandle[KEY_SEARCH_QUERY] = query
    }

    fun updateSavedPagesDestination(destination: SavedPagesDestination?) {
        savedPagesDestination = destination
        savedStateHandle[KEY_SAVED_PAGES_DESTINATION] = destination?.name
    }

    private companion object {
        const val KEY_MAIN_DESTINATION = "main_destination"
        const val KEY_SEARCH_ACTIVE = "header_search_active"
        const val KEY_SEARCH_QUERY = "header_search_query"
        const val KEY_SAVED_PAGES_DESTINATION = "saved_pages_destination"
    }
}
