package io.github.shpuljka.kurs.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class PriceParserTest {

    /** A word on the tag: [h] is the glyph height, [top] its top edge; glyphs are 0.6·h wide. */
    private data class W(val text: String, val h: Float = 40f, val top: Float = 0f, val gap: Float = 12f)

    /** Lays the words out left to right, as ML Kit would report them. */
    private fun line(vararg words: W, y: Float = 0f): OcrLine {
        var x = 0f
        return OcrLine(words.map { w ->
            x += w.gap
            w.text.map { c ->
                val width = w.h * 0.6f
                Glyph(c, Box(x, y + w.top, x + width, y + w.top + w.h)).also { x += width }
            }
        })
    }

    /** Raised cents glued to the price, like `129⁹⁹`: half height, tops aligned. */
    private fun withCents(main: String, cents: String, h: Float = 40f): OcrLine {
        val big = line(W(main, h)).words.single()
        var x = big.last().box.right
        val small = cents.map { c -> Glyph(c, Box(x, 0f, x + h * 0.3f, h * 0.5f)).also { x += h * 0.3f } }
        return OcrLine(listOf(big + small))
    }

    private fun amounts(vararg lines: OcrLine, digits: Int = 2) =
        PriceParser.candidates(lines.toList(), digits).map { it.amount.stripTrailingZeros().toPlainString() }

    private fun best(vararg lines: OcrLine, digits: Int = 2) = amounts(*lines, digits = digits).firstOrNull()

    // --- separators (spec §12.4) ---------------------------------------------

    @Test fun `comma and two digits is a fraction`() = assertEquals("12.99", best(line(W("12,99"))))

    @Test fun `point and two digits is a fraction`() = assertEquals("12.99", best(line(W("12.99"))))

    @Test fun `one digit after the separator is a fraction`() = assertEquals("4.5", best(line(W("4,5"))))

    @Test fun `three digits after a point are thousands`() = assertEquals("1299", best(line(W("1.299"))))

    @Test fun `three digits after a comma are thousands`() = assertEquals("1299", best(line(W("1,299"))))

    @Test fun `the last of two separators is decimal`() {
        assertEquals("1299.5", best(line(W("1.299,50"))))
        assertEquals("1299.5", best(line(W("1,299.50"))))
    }

    @Test fun `several thousands separators`() = assertEquals("1234567", best(line(W("1.234.567"))))

    @Test fun `swiss apostrophe`() = assertEquals("1299.9", best(line(W("1'299.90"))))

    @Test fun `space between thousands joins the number`() = assertEquals("1299", best(line(W("1"), W("299"))))

    @Test fun `space thousands with a fraction`() = assertEquals("12345.5", best(line(W("12"), W("345,50"))))

    @Test fun `two separate numbers are not joined`() =
        assertEquals(listOf("12", "34"), amounts(line(W("12"), W("34"))).sorted())

    @Test fun `currency without fraction takes every separator as thousands`() {
        assertEquals("1299", best(line(W("1.299")), digits = 0))
        assertEquals("1299", best(line(W("12,99")), digits = 0))
    }

    @Test fun `leading zero keeps three decimals`() = assertEquals("0.299", best(line(W("0.299"))))

    @Test fun `four digits after a separator is not a price`() = assertNull(best(line(W("1.2999"))))

    @Test fun `parse amount directly`() {
        assertEquals(BigDecimal("1299.50"), PriceParser.parseAmount("1 299,50", 2))
        assertNull(PriceParser.parseAmount("1..2", 2))
    }

    // --- cents and whole marks ------------------------------------------------

    @Test fun `raised small cents`() = assertEquals("129.99", best(withCents("129", "99")))

    @Test fun `raised cents after a grouped price`() {
        val main = line(W("1"), W("299"))
        val last = main.words.last()
        var x = last.last().box.right
        val cents = "90".map { c -> Glyph(c, Box(x, 0f, x + 12f, 20f)).also { x += 12f } }
        assertEquals("1299.9", best(OcrLine(main.words.dropLast(1) + listOf(last + cents))))
    }

    @Test fun `small digits on the baseline are not cents`() {
        // "129" then "99" at half height but sitting low: a separate small number, not raised.
        val big = line(W("129")).words.single()
        var x = big.last().box.right
        val low = "99".map { c -> Glyph(c, Box(x, 20f, x + 12f, 40f)).also { x += 12f } }
        assertEquals("129", best(OcrLine(listOf(big + low))))
    }

    @Test fun `dash after the separator is a whole amount`() {
        assertEquals("12", best(line(W("12.–"))))
        assertEquals("12", best(line(W("12,-"))))
    }

    // --- what is not a price --------------------------------------------------

    @Test fun `weights and volumes are dropped`() {
        assertEquals(emptyList<String>(), amounts(line(W("500"), W("g"))))
        assertEquals(emptyList<String>(), amounts(line(W("1,5"), W("л"))))
        assertEquals(emptyList<String>(), amounts(line(W("330ml"))))
        assertEquals(emptyList<String>(), amounts(line(W("2"), W("шт"))))
    }

    @Test fun `percent is dropped`() = assertEquals(emptyList<String>(), amounts(line(W("-30%"))))

    @Test fun `a currency that starts like a unit is kept`() = assertEquals("99", best(line(W("99"), W("грн"))))

    @Test fun `full dates are dropped`() = assertEquals(emptyList<String>(), amounts(line(W("25.10.2026"))))

    @Test fun `slash dates are dropped`() = assertEquals(emptyList<String>(), amounts(line(W("25/10"))))

    @Test fun `short dotted number stays a possible price`() = assertEquals("25.1", best(line(W("25.10"))))

    @Test fun `time is dropped`() = assertEquals(emptyList<String>(), amounts(line(W("12:30"))))

    @Test fun `barcodes and long codes are dropped`() {
        assertEquals(emptyList<String>(), amounts(line(W("4600123456789"))))
        assertEquals(emptyList<String>(), amounts(line(W("123456789"))))
    }

    @Test fun `digits glued after letters are a code`() = assertEquals(emptyList<String>(), amounts(line(W("SKU123"))))

    @Test fun `zero is not a price`() = assertEquals(emptyList<String>(), amounts(line(W("0"))))

    // --- ranking --------------------------------------------------------------

    @Test fun `biggest print wins`() {
        val old = line(W("1599", h = 20f), y = 0f)
        val new = line(W("1299", h = 60f), y = 40f)
        assertEquals("1299", best(old, new))
    }

    @Test fun `currency sign next to a number lifts it`() {
        val plain = line(W("250", h = 40f))
        val priced = line(W("199", h = 32f), W("€", h = 32f), y = 60f)
        assertEquals("199", best(plain, priced))
        assertTrue(PriceParser.candidates(listOf(priced), 2).single().hasCurrency)
    }

    @Test fun `currency before the number counts too`() =
        assertTrue(PriceParser.candidates(listOf(line(W("$"), W("4.99"))), 2).single().hasCurrency)

    @Test fun `price per kilo ranks below the price`() {
        val price = line(W("349", h = 40f), W("₽", h = 40f))
        val perKg = line(W("698", h = 40f), W("₽/кг", h = 40f), y = 60f)
        assertEquals(listOf("349", "698"), amounts(price, perKg))
        assertTrue(PriceParser.candidates(listOf(perKg), 2).single().perUnit)
    }

    @Test fun `price for 100 g is per unit`() {
        val c = PriceParser.candidates(listOf(line(W("за"), W("100"), W("г"), W("45,90"))), 2)
        assertEquals(listOf("45.9"), c.map { it.amount.stripTrailingZeros().toPlainString() })
        assertTrue(c.single().perUnit)
    }

    @Test fun `plain price is not per unit`() =
        assertFalse(PriceParser.candidates(listOf(line(W("12,99"), W("€"))), 2).single().perUnit)

    @Test fun `box covers the whole price including raised cents`() {
        val c = PriceParser.candidates(listOf(withCents("129", "99")), 2).single()
        assertEquals(12f, c.box.left, 0.01f)
        assertEquals(12f + 3 * 24f + 2 * 12f, c.box.right, 0.01f)
        assertEquals(40f, c.size, 0.01f)
    }

    @Test fun `a realistic supermarket tag`() {
        val tag = listOf(
            line(W("Молоко", h = 18f), W("3,2%", h = 18f), W("930", h = 18f), W("мл", h = 18f)),
            withCents("89", "99", h = 80f),
            line(W("Цена", h = 14f), W("за", h = 14f), W("1", h = 14f), W("л:", h = 14f), W("96,76", h = 14f), W("₽", h = 14f)),
            line(W("4607001771234", h = 12f), W("25.10.2026", h = 12f)),
        )
        assertEquals(listOf("89.99", "96.76"), amounts(*tag.toTypedArray()))
    }

    // --- what ML Kit really returns (recorded from the sample tags) -----------

    @Test fun `raised cents on a line of their own are glued to the price`() {
        // "89" and "99" come back as separate lines; the cents are half as tall and top-aligned.
        val main = OcrLine(listOf(listOf(Glyph('8', Box(130f, 254f, 300f, 595f)), Glyph('9', Box(330f, 249f, 525f, 588f)))))
        val cents = OcrLine(listOf(listOf(Glyph('9', Box(546f, 263f, 636f, 400f)), Glyph('9', Box(640f, 256f, 727f, 393f)))))
        assertEquals(listOf("89.99"), amounts(main, cents))
    }

    @Test fun `separate small number below is not cents`() {
        val main = OcrLine(listOf(listOf(Glyph('8', Box(130f, 254f, 300f, 595f)), Glyph('9', Box(330f, 249f, 525f, 588f)))))
        val low = OcrLine(listOf(listOf(Glyph('9', Box(546f, 450f, 636f, 590f)), Glyph('9', Box(640f, 450f, 727f, 590f)))))
        assertEquals(listOf("89", "99"), amounts(main, low))
    }

    @Test fun `no cents are glued for a currency without fraction`() {
        val main = OcrLine(listOf(listOf(Glyph('8', Box(130f, 254f, 300f, 595f)), Glyph('9', Box(330f, 249f, 525f, 588f)))))
        val cents = OcrLine(listOf(listOf(Glyph('9', Box(546f, 263f, 636f, 400f)), Glyph('9', Box(640f, 256f, 727f, 393f)))))
        assertEquals(listOf("89", "99"), amounts(main, cents, digits = 0))
    }

    @Test fun `cyrillic read as latin look-alikes`() {
        // "930 мл", "100г", "Цена за 1 л: 96,76 руб.", "1 299 тг" as the Latin model returns them.
        assertEquals(emptyList<String>(), amounts(line(W("930"), W("Mn"))))
        assertEquals(emptyList<String>(), amounts(line(W("100r"))))
        val perLitre = PriceParser.candidates(listOf(line(W("JeHa"), W("3a"), W("1n:"), W("96,76"), W("py6."))), 2)
        assertEquals(listOf("96.76"), perLitre.map { it.amount.toPlainString() })
        assertTrue(perLitre.single().hasCurrency)
        assertTrue(perLitre.single().perUnit)
        assertTrue(PriceParser.candidates(listOf(line(W("1"), W("299"), W("Tr"))), 0).single().hasCurrency)
    }
}
