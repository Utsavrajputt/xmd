package com.invictus.xmd.ui

import androidx.lifecycle.SavedStateHandle
import com.invictus.xmd.ui.browser.SavedPagesDestination
import com.invictus.xmd.ui.components.MainDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityViewModelTest {

    @Test
    fun stateRestoresFromSavedStateHandle() {
        val handle = SavedStateHandle()
        val original = MainActivityViewModel(handle)

        original.initializeMainDestination(MainDestination.Browser)
        original.setHeaderSearchActive(true)
        original.setHeaderSearchQuery("release notes")
        original.setSavedPagesDestination(SavedPagesDestination.History)

        val restored = MainActivityViewModel(handle)

        assertEquals(MainDestination.Browser, restored.mainDestination)
        assertTrue(restored.headerSearchActive)
        assertEquals("release notes", restored.headerSearchQuery)
        assertEquals(SavedPagesDestination.History, restored.savedPagesDestination)
    }

    @Test
    fun configuredDefaultDoesNotReplaceRestoredDestination() {
        val handle = SavedStateHandle(mapOf("main_destination" to MainDestination.Home.name))
        val viewModel = MainActivityViewModel(handle)

        viewModel.initializeMainDestination(MainDestination.Downloads)

        assertEquals(MainDestination.Home, viewModel.mainDestination)
        assertFalse(viewModel.headerSearchActive)
    }
}
