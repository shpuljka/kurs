package io.github.shpuljka.kurs.converter

import org.junit.Assert.assertEquals
import org.junit.Test

class KeypadStateTest {
    private val state = ConverterUiState(ConverterModel.initial("USD"), country = "US")

    @Test fun `a folded keypad unfolds`() = assertEquals(false, state.copy(keypadCollapsed = true).withKeypad().keypadCollapsed)

    @Test fun `an open keypad stays open`() = assertEquals(false, state.copy(keypadCollapsed = false).withKeypad().keypadCollapsed)

    // Before the saved state is read, a tap mustn't decide it: the restore would be overwritten.
    @Test fun `not restored yet stays unknown`() = assertEquals(null, state.withKeypad().keypadCollapsed)
}
