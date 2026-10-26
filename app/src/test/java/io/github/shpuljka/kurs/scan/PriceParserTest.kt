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

    @Test fun `currency without fraction reads three digits as thousands`() {
        assertEquals("1299", best(line(W("1.299")), digits = 0))
        assertEquals("1299", best(line(W("1,299")), digits = 0))
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

    // --- review cases ----------------------------------------------------------

    @Test fun `currency code glued before the number`() {
        val c = PriceParser.candidates(listOf(line(W("EUR12,99"))), 2).single()
        assertEquals("12.99", c.amount.toPlainString())
        assertTrue(c.hasCurrency)
        assertEquals("12.9", best(line(W("CHF12.90"))))
    }

    @Test fun `code glued after letters is skipped whole`() = assertEquals(emptyList<String>(), amounts(line(W("SKU12,99"))))

    @Test fun `quantity times price`() {
        assertEquals(listOf("1.99"), amounts(line(W("2x1,99"))))
        assertEquals(listOf("1.99"), amounts(line(W("2"), W("x"), W("1,99"))))
        assertEquals(listOf("5"), amounts(line(W("3"), W("for"), W("5"))))
    }

    @Test fun `long grouped prices are kept`() {
        assertEquals("1234567.89", best(line(W("1.234.567,89"))))
        assertEquals("1299000", best(line(W("Rp"), W("1.299.000,00"))))
    }

    @Test fun `more than 12 digits do not fit the keypad`() = assertNull(best(line(W("123.456.789.012,50"))))

    @Test fun `currency without fraction rounds printed cents off`() {
        assertEquals("1300", best(line(W("1.299,50")), digits = 0))
        assertEquals("13", best(line(W("12,9")), digits = 0))
        assertEquals("1299", best(line(W("1.299")), digits = 0))
    }

    @Test fun `letter-prefixed dollar signs count as currency`() {
        assertTrue(PriceParser.candidates(listOf(line(W("R$"), W("4,99"))), 2).single().hasCurrency)
        assertTrue(PriceParser.candidates(listOf(line(W("US$4.99"))), 2).single().hasCurrency)
        assertTrue(PriceParser.candidates(listOf(line(W("Rs.1,299"))), 2).single().hasCurrency)
    }

    @Test fun `per unit written with words`() {
        assertTrue(PriceParser.candidates(listOf(line(W("1,29"), W("per"), W("100"), W("g"))), 2).single().perUnit)
        assertTrue(PriceParser.candidates(listOf(line(W("1,29"), W("€"), W("je"), W("100"), W("g"))), 2).single().perUnit)
    }

    @Test fun `price per 100g with a slash is kept`() {
        val c = PriceParser.candidates(listOf(line(W("0,89/100g"))), 2)
        assertEquals(listOf("0.89"), c.map { it.amount.toPlainString() })
        assertTrue(c.single().perUnit)
    }

    @Test fun `on a tie the number with cents wins`() = assertEquals("1.99", best(line(W("3"), W("1,99"))))

    @Test fun `on a full tie the right-most number wins`() = assertEquals("5", best(line(W("3"), W("5"))))

    // --- small print glued to the price (real Kazakh tags) --------------------

    @Test fun `a date's year glued after the cents is dropped`() = assertEquals("1155", best(line(W("1155,002026"))))

    @Test fun `a day digit before the glued year too`() = assertEquals("1155", best(line(W("1155,0072026"))))

    @Test fun `the glued year leaves the box on the price`() {
        val candidate = PriceParser.candidates(listOf(line(W("1155,002026"))), 2).single()
        assertEquals(line(W("1155,00")).words.single().last().box.right, candidate.box.right)
    }

    @Test fun `a mangled tail after the cents is dropped`() = assertEquals("1140", best(line(W("1140,0g2020"))))

    @Test fun `a unit after a decimal is still a weight`() = assertNull(best(line(W("1,5g"))))

    @Test fun `four digits after a separator still aren't a price`() = assertNull(best(line(W("1.2026"))))

    @Test fun `the time after a date isn't a price`() =
        assertEquals(listOf("1155"), amounts(line(W("1155,002026"), W("11.04"))))

    @Test fun `a price after a full date is kept`() =
        assertEquals("129.99", best(line(W("25.10.2026", h = 20f), W("129,99"))))

    @Test fun `no space thousands after a decimal`() =
        assertEquals(listOf("3265", "112"), amounts(line(W("3265.00"), W("112"))))

    // --- what wins ---------------------------------------------------------

    @Test fun `cents beat a bare number of the same size`() = assertEquals("965", best(line(W("965.00"), W("2020624"))))

    @Test fun `a huge lone digit loses to the price`() =
        assertEquals("735", best(line(W("1", h = 200f)), line(W("735,00", h = 80f), y = 300f)))

    @Test fun `a lone digit with a currency is a price`() = assertEquals("5", best(line(W("5", h = 80f), W("€", h = 80f)), line(W("2,99"))))

    // "40 мин" on a pack, far above a big "1" and just right of it.
    @Test fun `small digits far above a number aren't its cents`() =
        assertFalse("1.4" in amounts(line(W("40", gap = 140f)), line(W("1", h = 200f), y = 600f)))

    @Test fun `400rp is grams`() = assertNull(best(line(W("400rp"))))
}
