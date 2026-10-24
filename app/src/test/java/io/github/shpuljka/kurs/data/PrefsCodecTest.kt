package io.github.shpuljka.kurs.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PrefsCodecTest {
    @Test fun `codes round trip`() = assertEquals(listOf("RUB", "USD"), PrefsCodec.codes(PrefsCodec.codes(listOf("RUB", "USD"))))

    @Test fun `missing or garbage codes are dropped`() {
        assertEquals(emptyList<String>(), PrefsCodec.codes(null))
        assertEquals(listOf("RUB"), PrefsCodec.codes("RUB,,usd,EURO"))
    }

    @Test fun `pairs round trip`() {
        val pairs = listOf(CurrencyPair("USD", "RUB"), CurrencyPair("EUR", "RUB"))
        assertEquals(pairs, PrefsCodec.pairs(PrefsCodec.pairs(pairs)))
    }

    @Test fun `broken pairs are dropped`() = assertEquals(listOf(CurrencyPair("USD", "RUB")), PrefsCodec.pairs("USD:RUB,EUR,GBP:x"))

    @Test fun `empty pairs`() = assertEquals(emptyList<CurrencyPair>(), PrefsCodec.pairs(""))

    @Test fun `default digest pairs go into the local currency`() =
        assertEquals(
            listOf(CurrencyPair("USD", "RUB"), CurrencyPair("EUR", "RUB")),
            DigestSettings.defaultPairs(listOf("RUB", "USD", "EUR", "GBP")),
        )
}
