package io.github.shpuljka.kurs.calc

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

class AmountFormatTest {
    private val ru = AmountFormat(Locale.forLanguageTag("ru-RU"))
    private val en = AmountFormat(Locale.US)

    /** Normalizes Java's group separators (NBSP / narrow NBSP) to a plain space. */
    private fun String.plain() = replace(' ', ' ').replace(' ', ' ')

    @Test fun `ru amount with decimals`() = assertEquals("11 975,04", ru.amount(BigDecimal("11975.0425"), 2).plain())

    @Test fun `en amount with decimals`() = assertEquals("11,975.04", en.amount(BigDecimal("11975.0425"), 2))

    @Test fun `trailing zero kept to currency digits`() = assertEquals("92,40", ru.amount(BigDecimal("92.4"), 2))

    @Test fun `whole amount has no decimals`() = assertEquals("100", ru.amount(BigDecimal("99.999"), 2))

    @Test fun `zero-digit currency`() = assertEquals("15 120", ru.amount(BigDecimal("15119.6"), 0).plain())

    @Test fun `typed keeps decimals verbatim`() = assertEquals("1 234,50", ru.typed("1234.50").plain())

    @Test fun `typed with trailing point`() = assertEquals("12,", ru.typed("12."))

    @Test fun `typed empty is zero`() = assertEquals("0", ru.typed(""))

    @Test fun `editable groups only the integer part`() =
        assertEquals("1 234,5678", ru.editable("1234.5678").text.plain())

    @Test fun `raw to display skips group separators`() {
        val f = ru.editable("1234")
        // "1 234": raw 1 (before "2") is display 2 (after the space).
        assertEquals(listOf(0, 2, 3, 4, 5), f.rawToDisplay.toList())
    }

    @Test fun `display to raw maps taps back`() {
        val f = ru.editable("12+3")
        // "12 + 3": a tap inside " + " lands before or after the operator.
        assertEquals(listOf(0, 1, 2, 2, 3, 3, 4), f.displayToRaw.toList())
    }

    @Test fun `editable keeps a leading decimal point`() = assertEquals(",5", ru.editable(".5").text)

    @Test fun `expression spacing and minus sign`() =
        assertEquals("1 200 + 8% − 3 × 2", ru.expression("1200+8%-3×2").plain())
}
