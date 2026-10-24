package io.github.shpuljka.kurs.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class CurrencyCatalogTest {
    private val ru = Locale.forLanguageTag("ru-RU")
    private val list = CurrencyCatalog.list(listOf("USD", "RUB", "TRY", "EUR", "XAU", "XDR", "XOF", "BTC", "ITL", "TRL"), ru)

    private fun search(q: String) = list.filter { it.matches(q) }.map { it.code }

    @Test fun `only current national currencies, sorted by code`() =
        assertEquals(listOf("EUR", "RUB", "TRY", "USD", "XOF"), list.map { it.code })

    @Test fun `names are localized and capitalized`() =
        assertEquals("Российский рубль", list.first { it.code == "RUB" }.name)

    @Test fun `empty query matches all`() = assertEquals(5, search("  ").size)

    @Test fun `search by code is case-insensitive`() = assertEquals(listOf("USD"), search("usd"))

    @Test fun `search by localized name`() = assertEquals(listOf("TRY"), search("лира"))

    @Test fun `search by english name`() = assertEquals(listOf("TRY"), search("lira"))

    @Test fun `search by issuer country`() = assertEquals(listOf("TRY"), search("Турция"))
}
