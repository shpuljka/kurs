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

    @Test fun `expression spacing and minus sign`() =
        assertEquals("1 200 + 8% − 3 × 2", ru.expression("1200+8%-3×2").plain())
}
