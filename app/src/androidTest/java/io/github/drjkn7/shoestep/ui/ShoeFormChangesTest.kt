package io.github.drjkn7.shoestep.ui

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.drjkn7.shoestep.data.SaveResult
import io.github.drjkn7.shoestep.data.ShoeInput
import io.github.drjkn7.shoestep.data.model.ShoeType
import io.github.drjkn7.shoestep.ui.shoe.FormStatus
import io.github.drjkn7.shoestep.ui.shoe.ShoeFormViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T45 underneath the screen: what counts as a change, and that it is still known after Android
 * closes the app in the background (the same SavedStateHandle handed to a new form).
 */
@RunWith(AndroidJUnit4::class)
class ShoeFormChangesTest {

    private val container = testContainer()

    @After fun tearDown() = container.database.close()

    private fun form(state: SavedStateHandle, shoeId: String? = null) =
        ShoeFormViewModel(container.shoeRepository, container.clock, state, shoeId, defaultCurrencyCode = "EUR")

    @Test fun newFormCountsTypingButNotTheDefaultCurrency() {
        val state = SavedStateHandle()
        val vm = form(state)
        assertFalse(vm.hasChanges)

        vm.applyDefaultCurrency("GBP") // from Settings, a moment after opening
        assertFalse(vm.hasChanges)

        vm.update(vm.form.copy(name = "Trail"))
        assertTrue(vm.hasChanges)
        vm.update(vm.form.copy(name = ""))
        assertFalse("typing and deleting again is no change", vm.hasChanges)

        vm.update(vm.form.copy(type = ShoeType.HIKING))
        assertTrue("still changed after the app is restored", form(state).hasChanges)
    }

    @Test fun editFormComparesWithTheSavedPair() {
        val id = runBlocking {
            (container.shoeRepository.add(ShoeInput("Daily", "", "", ShoeType.WALKING, "", "EUR", null, 2)) as SaveResult.Saved).shoeId
        }
        val state = SavedStateHandle()
        val vm = form(state, id)
        waitUntil { vm.status == FormStatus.Editing }
        assertFalse(vm.hasChanges)
        assertEquals("Daily", vm.savedName)

        vm.update(vm.form.copy(name = "Daily 2"))
        assertTrue(vm.hasChanges)
        val restored = form(state, id)
        assertTrue(restored.hasChanges)
        assertEquals("Daily", restored.savedName)
    }

    private fun waitUntil(condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < end) { "timed out" }
            Thread.sleep(20)
        }
    }
}
