package io.github.shpuljka.kurs.converter

import io.github.shpuljka.kurs.calc.Key
import io.github.shpuljka.kurs.data.Rates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class ConverterModelTest {
    private val rates = Rates(
        LocalDate.of(2026, 10, 23),
        mapOf("USD" to "1", "RUB" to "80", "EUR" to "0.8", "GBP" to "0.75", "JPY" to "150")
            .mapValues { BigDecimal(it.value) },
    )

    private fun assertValue(expected: String, actual: BigDecimal?) =
        assertTrue("expected $expected, was $actual", actual != null && BigDecimal(expected).compareTo(actual) == 0)

    @Test fun `initial rows are local plus USD and EUR`() =
        assertEquals(listOf("RUB", "USD", "EUR"), ConverterModel.initial("RUB").codes)

    @Test fun `local USD is followed by EUR and GBP`() =
        assertEquals(listOf("USD", "EUR", "GBP"), ConverterModel.initial("USD").codes)

    @Test fun `local EUR is followed by USD and GBP`() =
        assertEquals(listOf("EUR", "USD", "GBP"), ConverterModel.initial("EUR").codes)

    @Test fun `starts with 100 in the first row`() {
        val m = ConverterModel.initial("RUB")
        assertEquals(0, m.active)
        assertEquals("100", m.expr)
    }

    @Test fun `other rows are converted from the active one`() {
        val v = ConverterModel(listOf("RUB", "USD", "EUR"), active = 1, expr = "120+8%").values(rates)
        assertValue("10368", v[0])
        assertValue("129.6", v[1])
        assertValue("103.68", v[2])
    }

    @Test fun `no rates leaves other rows empty`() {
        val v = ConverterModel(listOf("RUB", "USD")).values(null)
        assertValue("100", v[0])
        assertNull(v[1])
    }

    @Test fun `division by zero leaves every row empty`() =
        assertTrue(ConverterModel(listOf("RUB", "USD"), expr = "5÷0").values(rates).all { it == null })

    @Test fun `typing goes into the expression`() =
        assertEquals("100+", ConverterModel(listOf("RUB", "USD")).press(Key.Op('+')).expr)

    @Test fun `equals rounds to the active currency`() =
        assertEquals("130", ConverterModel(listOf("JPY", "USD"), expr = "120+8%").press(Key.Equals).expr)

    @Test fun `selecting a row seeds it with its rounded value`() {
        val m = ConverterModel(listOf("RUB", "EUR"), expr = "1000").select(1, rates)
        assertEquals(1, m.active)
        assertEquals("10", m.expr)
    }

    @Test fun `selected row's amount is fully selected, typing replaces it`() {
        val m = ConverterModel(listOf("RUB", "EUR"), expr = "1000").select(1, rates)
        assertEquals(0 to 2, m.selStart to m.selEnd)
        assertEquals("7", m.press(Key.Digit('7')).expr)
    }

    @Test fun `start amount is selected so the first digit replaces it`() =
        assertEquals("5", ConverterModel.initial("RUB").press(Key.Digit('5')).expr)

    @Test fun `after typing the cursor sits at the end`() {
        val m = ConverterModel.initial("RUB").press(Key.Digit('5')).press(Key.Digit('0'))
        assertEquals("50", m.expr)
        assertEquals(2 to 2, m.selStart to m.selEnd)
    }

    @Test fun `cursor placed in the middle edits there`() {
        val m = ConverterModel(listOf("RUB", "USD"), expr = "150").moveCursor(1).press(Key.Digit('2'))
        assertEquals("1250", m.expr)
        assertEquals(2, m.selStart)
    }

    @Test fun `cursor position is clamped`() = assertEquals(3, ConverterModel(listOf("RUB", "USD"), expr = "150").moveCursor(99).selStart)

    @Test fun `select all after placing the cursor`() {
        val m = ConverterModel(listOf("RUB", "USD"), expr = "150").moveCursor(1).selectAll()
        assertEquals(0 to 3, m.selStart to m.selEnd)
    }

    @Test fun `selecting a negative value seeds zero`() =
        assertEquals("0", ConverterModel(listOf("RUB", "USD"), expr = "5-10").select(1, rates).expr)

    @Test fun `selecting the active row changes nothing`() {
        val m = ConverterModel(listOf("RUB", "USD"), expr = "1+")
        assertSame(m, m.select(0, rates))
    }

    @Test fun `add appends a new currency`() =
        assertEquals(listOf("RUB", "USD", "GBP"), ConverterModel(listOf("RUB", "USD")).add("GBP").codes)

    @Test fun `add ignores duplicates`() =
        assertEquals(listOf("RUB", "USD"), ConverterModel(listOf("RUB", "USD")).add("USD").codes)

    @Test fun `at most 10 rows`() {
        val full = ConverterModel(List(10) { "C$it" })
        assertEquals(false, full.canAdd)
        assertEquals(10, full.add("GBP").codes.size)
    }

    @Test fun `at least 2 rows`() {
        val m = ConverterModel(listOf("RUB", "USD"))
        assertEquals(false, m.canRemove)
        assertEquals(m, m.remove(1, rates))
    }

    @Test fun `removing a row above the active keeps the active row`() {
        val m = ConverterModel(listOf("RUB", "USD", "EUR"), active = 2, expr = "5").remove(0, rates)
        assertEquals(listOf("USD", "EUR"), m.codes)
        assertEquals(1, m.active)
        assertEquals("5", m.expr)
    }

    @Test fun `removing the active row moves input to the first row and keeps the amount`() {
        val m = ConverterModel(listOf("RUB", "USD", "EUR"), active = 1, expr = "10").remove(1, rates)
        assertEquals(listOf("RUB", "EUR"), m.codes)
        assertEquals(0, m.active)
        assertEquals("800", m.expr)
    }

    @Test fun `travelling inserts the new local currency on top`() {
        val m = ConverterModel(listOf("RUB", "USD", "EUR"), active = 1, expr = "50").withLocal("TRY")
        assertEquals(listOf("TRY", "RUB", "USD", "EUR"), m.codes)
        assertEquals("USD", m.codes[m.active])
        assertEquals("50", m.expr)
    }

    @Test fun `travelling moves an existing currency to the top`() =
        assertEquals(listOf("EUR", "RUB", "USD"), ConverterModel(listOf("RUB", "USD", "EUR")).withLocal("EUR").codes)

    @Test fun `same local currency changes nothing`() {
        val m = ConverterModel(listOf("RUB", "USD"))
        assertSame(m, m.withLocal("RUB"))
    }

    @Test fun `travelling with 10 rows drops the last one`() {
        val m = ConverterModel(List(10) { "C$it" }).withLocal("TRY")
        assertEquals(10, m.codes.size)
        assertEquals(listOf("TRY", "C0"), m.codes.take(2))
        assertEquals("C8", m.codes.last())
    }

    @Test fun `if the active row is dropped, input restarts on the first row`() {
        val m = ConverterModel(List(10) { "C$it" }, active = 9, expr = "7").withLocal("TRY")
        assertEquals(0, m.active)
        assertEquals("100", m.expr)
    }

    @Test fun `replace changes the currency of a row`() =
        assertEquals(listOf("RUB", "GBP"), ConverterModel(listOf("RUB", "USD")).replace(1, "GBP").codes)

    @Test fun `replace with a currency already shown swaps rows`() =
        assertEquals(listOf("EUR", "USD", "RUB"), ConverterModel(listOf("RUB", "USD", "EUR")).replace(0, "EUR").codes)

    // --- scanned price (spec §12.5) -----------------------------------------

    private val rows = ConverterModel(listOf("KZT", "USD", "EUR", "RUB"), active = 1, expr = "100")

    @Test fun `scan goes to the local currency row`() = assertEquals(0, rows.scanTarget("KZT"))

    @Test fun `scan goes to the active row without a local row`() {
        assertEquals(1, rows.scanTarget("TRY"))
        assertEquals(1, rows.scanTarget(null))
    }

    @Test fun `preview shows the first two other rows`() {
        assertEquals(listOf(1, 2), rows.scanPreview(0))
        assertEquals(listOf(0, 2), rows.scanPreview(1))
        assertEquals(listOf(1), ConverterModel(listOf("KZT", "USD")).scanPreview(0))
    }

    @Test fun `scanned price replaces the amount and selects it`() {
        val m = rows.insertScanned(0, BigDecimal("1299.50"))
        assertEquals(0, m.active)
        assertEquals("1299.5", m.expr)
        assertEquals(0, m.selStart)
        assertEquals(6, m.selEnd)
    }

    @Test fun `scanned price is appended after an open operation`() {
        val m = ConverterModel(listOf("KZT", "USD"), active = 0, expr = "120+").insertScanned(0, BigDecimal("80"))
        assertEquals("120+80", m.expr)
        assertEquals(6, m.selStart)
        assertEquals(6, m.selEnd)
    }

    @Test fun `open operation in another row is not continued`() {
        val m = ConverterModel(listOf("KZT", "USD"), active = 1, expr = "5×").insertScanned(0, BigDecimal("80"))
        assertEquals(0, m.active)
        assertEquals("80", m.expr)
    }

    @Test fun `finished expression is replaced`() =
        assertEquals("80", ConverterModel(listOf("KZT", "USD"), expr = "120+8%").insertScanned(0, BigDecimal("80")).expr)

    @Test fun `scanned price is rounded to the row currency`() {
        assertEquals("0.3", rows.insertScanned(1, BigDecimal("0.299")).expr)
        assertEquals("1300", ConverterModel(listOf("JPY", "USD")).insertScanned(0, BigDecimal("1299.5")).expr)
    }
}
