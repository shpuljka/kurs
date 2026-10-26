package io.github.shpuljka.kurs.scan

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ML Kit output for real shelf photos (a Kazakh supermarket, tenge), recorded on a device:
 * each glyph as [char, left, top, right, bottom]. The best candidate must be the price.
 */
class RealTagsTest {

    private fun load(name: String): List<OcrLine> {
        val lines = JSONObject(javaClass.getResource("/ocr/$name.json")!!.readText()).getJSONArray("lines")
        return (0 until lines.length()).map { i ->
            val words = lines.getJSONArray(i)
            OcrLine((0 until words.length()).map { j ->
                val glyphs = words.getJSONArray(j)
                (0 until glyphs.length()).map { k ->
                    val g = glyphs.getJSONArray(k)
                    Glyph(g.getString(0)[0], Box(g.getDouble(1).toFloat(), g.getDouble(2).toFloat(), g.getDouble(3).toFloat(), g.getDouble(4).toFloat()))
                }
            })
        }
    }

    private fun best(name: String) = PriceParser.candidates(load(name), fractionDigits = 2).first().amount.stripTrailingZeros().toPlainString()

    // A huge "1" and "40 мин" on the packs around the tag.
    @Test fun soup() = assertEquals("735", best("kz-soup"))

    // "400гр" comes back as "400rp": grams, not rupiah.
    @Test fun oats() = assertEquals("539", best("kz-oats"))

    @Test fun `two tags, either price`() = assertEquals("1385", best("kz-porridge"))

    @Test fun cola() = assertEquals("765", best("kz-cola"))

    // The date's small print is glued to the price, mangled: "1140,0g020282".
    @Test fun `chips, sour cream`() = assertEquals("1140", best("kz-chips-sour-cream"))

    @Test fun `chips, sour cream, another shot`() = assertEquals("1140", best("kz-chips-sour-cream-2"))

    // "1155,002026" and then the time "11.04", read with a dot.
    @Test fun `chips, chili`() = assertEquals("1155", best("kz-chips-chili"))

    @Test fun `chips, paprika`() = assertEquals("1155", best("kz-chips-paprika"))

    // Three tags; "3265.00 112" must not join into one number.
    @Test fun `frozen berries`() = assertEquals("2555", best("kz-frozen-berries"))

    // The date's rest "2020624" stands as a word of the same height next to "965.00".
    @Test fun gummies() = assertEquals("965", best("kz-gummies"))
}
