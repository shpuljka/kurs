package io.github.shpuljka.kurs.scan

import java.math.BigDecimal
import java.math.RoundingMode

/** Axis-aligned box in image (or view) pixels. */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2
    val centerY: Float get() = (top + bottom) / 2

    operator fun contains(p: Pair<Float, Float>): Boolean = p.first in left..right && p.second in top..bottom

    fun union(other: Box) = Box(minOf(left, other.left), minOf(top, other.top), maxOf(right, other.right), maxOf(bottom, other.bottom))
}

/** One recognized character (an ML Kit symbol) and where it is. */
data class Glyph(val char: Char, val box: Box)

/** One recognized line of text: words in reading order, each a run of glyphs. */
data class OcrLine(val words: List<List<Glyph>>)

/** A number on the tag that may be the price, spec.md §12.4. */
data class PriceCandidate(
    val amount: BigDecimal,
    val box: Box,
    /** Height of the main digits; the biggest print is usually the price. */
    val size: Float,
    /** A currency sign or code stands right next to it. */
    val hasCurrency: Boolean,
    /** A price per kg / per 100 g and the like. */
    val perUnit: Boolean,
    /** The digits when printed as a bare integer (no separators or cents): may pair with raised cents on another line. */
    val bareDigits: String? = null,
) {
    val score: Float get() = size * (if (hasCurrency) CURRENCY_BOOST else 1f) * (if (perUnit) PER_UNIT_PENALTY else 1f)

    private companion object {
        const val CURRENCY_BOOST = 1.5f
        const val PER_UNIT_PENALTY = 0.5f
    }
}

/**
 * Finds price candidates in recognized text, spec.md §12.4: separators, raised cents (`129⁹⁹`),
 * `12.–`, and drops weights, percentages, dates, times and long codes. Pure — no Android.
 */
object PriceParser {

    /** Candidates, best first. [fractionDigits] are those of the currency the price goes into. */
    fun candidates(lines: List<OcrLine>, fractionDigits: Int): List<PriceCandidate> {
        val found = lines.flatMap { line(it, fractionDigits) }
        return (if (fractionDigits > 0) joinRaisedCents(found) else found).sortedWith(BEST_FIRST)
    }

    /** Biggest weighted print first; on a tie the one with a currency, then with cents, then the right-most ("2 x 1,99"). */
    private val BEST_FIRST = compareByDescending<PriceCandidate> { it.score }
        .thenByDescending { it.hasCurrency }
        .thenByDescending { it.amount.stripTrailingZeros().scale() > 0 }
        .thenByDescending { it.box.left }

    /** Raised cents often come back as a line of their own: glue a small 2-digit number to the price on its left. */
    private fun joinRaisedCents(found: List<PriceCandidate>): List<PriceCandidate> {
        val used = mutableSetOf<PriceCandidate>()
        val joined = found.map { price ->
            val h = price.size
            val cents = found.firstOrNull { c ->
                c !== price && c !in used && price.bareDigits != null && c.bareDigits?.length == 2 &&
                    c.box.left - price.box.right in -h * 0.1f..h * 0.6f &&
                    isRaised(c.box, price.box, h)
            } ?: return@map price
            used += cents
            price.copy(
                amount = price.amount + BigDecimal(cents.bareDigits).movePointLeft(2),
                box = price.box.union(cents.box),
                hasCurrency = price.hasCurrency || cents.hasCurrency,
                perUnit = price.perUnit || cents.perUnit,
                bareDigits = null,
            )
        }
        return joined.filter { it !in used }
    }

    private class Slot(val glyph: Glyph, val wordStart: Boolean) {
        val c get() = glyph.char
    }

    private fun line(line: OcrLine, fractionDigits: Int): List<PriceCandidate> {
        val slots = line.words.filter { it.isNotEmpty() }.flatMap { word -> word.mapIndexed { i, g -> Slot(g, i == 0) } }
        val text = slots.joinToString("") { (if (it.wordStart) " " else "") + it.c }
        // Index of each slot in [text], to cut the context around a number.
        val at = IntArray(slots.size).also { pos ->
            var p = 0
            slots.forEachIndexed { i, s -> if (s.wordStart) p++; pos[i] = p; p++ }
        }
        val found = mutableListOf<PriceCandidate>()
        var i = 0
        while (i < slots.size) {
            if (!slots[i].c.isDigit()) {
                i++
                continue
            }
            if (i > 0 && !slots[i].wordStart && slots[i - 1].c.isLetterOrDigit()) {
                // Glued after letters: a currency prefix ("EUR12,99", "R$4,99") still makes a price, a code ("SKU123") doesn't.
                val wordStart = (i downTo 0).first { slots[it].wordStart }
                val prefix = slots.subList(wordStart, i).joinToString("") { "${it.c}" }.lowercase()
                if (!CURRENCY_PREFIX.matches(prefix)) {
                    // Skip the whole number so its tail ("SKU12,99" → "99") isn't read on its own.
                    while (i < slots.size && !slots[i].wordStart && (slots[i].c.isDigit() || slots[i].c in SEPARATORS)) i++
                    continue
                }
            }
            val run = number(slots, i)
            i = run.end
            if (run.rejected) continue
            val before = text.substring(0, at[run.start]).lowercase()
            val after = if (run.end < slots.size) text.substring(at[run.end]).lowercase() else ""
            val amount = parseAmount(run.main, fractionDigits, run.cents, run.whole) ?: continue
            // The keypad takes at most 12 digits per number.
            if (amount.signum() == 0 || amount.toPlainString().count(Char::isDigit) > MAX_DIGITS) continue
            if (UNIT_AFTER.containsMatchIn(after) || QUANTITY_AFTER.containsMatchIn(after)) continue
            // Letters glued after the digits ("3a", "5x") make a word, not a price — unless it's a currency ("99p").
            val glued = run.end < slots.size && !slots[run.end].wordStart && slots[run.end].c.isLetter()
            if (glued && !CURRENCY_AFTER.containsMatchIn(after)) continue
            val perUnit = PER_UNIT_AFTER.containsMatchIn(after) || PER_UNIT_BEFORE.containsMatchIn(before)
            // "0,89/100g" is a price per unit, not a date.
            val dateLike = TIME_OR_DATE_BEFORE.containsMatchIn(before) || (!perUnit && TIME_OR_DATE_AFTER.containsMatchIn(after))
            if (isDate(run.main) || dateLike) continue
            found += PriceCandidate(
                amount = amount,
                box = run.box,
                size = run.size,
                hasCurrency = CURRENCY_AFTER.containsMatchIn(after) || CURRENCY_BEFORE.containsMatchIn(before),
                perUnit = perUnit,
                bareDigits = run.main.takeIf { run.cents == null && !run.whole && it.all(Char::isDigit) },
            )
        }
        return found
    }

    private class Run(
        val start: Int,
        val end: Int,
        /** Digits and separators as printed, spaces between thousands kept as ' '. */
        val main: String,
        /** Raised small digits after the main ones: the cents. */
        val cents: String?,
        /** Ends with `.–` / `,-`: a whole amount. */
        val whole: Boolean,
        val box: Box,
        val size: Float,
        val rejected: Boolean,
    )

    /** Reads one number starting at slot [start]. */
    private fun number(slots: List<Slot>, start: Int): Run {
        val main = StringBuilder()
        val digitBoxes = mutableListOf<Box>()
        var box = slots[start].glyph.box
        var i = start
        var group = 0 // digits in the current group
        var cents: String? = null
        var whole = false

        fun mainHeight() = digitBoxes.map { it.height }.sorted().let { it[it.size / 2] }
        fun take(k: Int) { box = box.union(slots[k].glyph.box) }

        while (i < slots.size) {
            val s = slots[i]
            when {
                s.c.isDigit() && (i == start || !s.wordStart) -> {
                    // A smaller digit ends the number: raised cents are read below, anything else is dropped.
                    if (digitBoxes.isNotEmpty() && !similar(s.glyph.box.height, mainHeight())) break
                    main.append(s.c); digitBoxes += s.glyph.box; take(i); group++; i++
                }
                // Thousands separated by a space: "1 299" arrives as two words.
                s.c.isDigit() && s.wordStart && group in 1..3 && digitsAhead(slots, i) == 3 &&
                    similar(s.glyph.box.height, mainHeight()) && !isRaised(s.glyph.box, digitBoxes.first(), mainHeight()) -> {
                    main.append(' ').append(s.c); digitBoxes += s.glyph.box; take(i); group = 1; i++
                }
                s.c in SEPARATORS && !s.wordStart && i + 1 < slots.size && slots[i + 1].c.isDigit() && !slots[i + 1].wordStart &&
                    !isRaised(slots[i + 1].glyph.box, digitBoxes.first(), mainHeight()) -> {
                    main.append(s.c); take(i); group = 0; i++
                }
                s.c in ".," && i + 1 < slots.size && slots[i + 1].c in DASHES && !slots[i + 1].wordStart -> {
                    whole = true; take(i); take(i + 1); i += 2
                    break
                }
                else -> break
            }
        }
        // Raised cents right after the main digits, in the same word or just after it.
        if (!whole && i < slots.size && digitBoxes.isNotEmpty()) {
            val h = mainHeight()
            val first = slots[i]
            val near = !first.wordStart || first.glyph.box.left - box.right < h * 0.6f
            if (first.c.isDigit() && near && isRaised(first.glyph.box, digitBoxes.first(), h)) {
                val raised = StringBuilder()
                var k = i
                while (k < slots.size && slots[k].c.isDigit() && (k == i || !slots[k].wordStart) && raised.length < 2) {
                    raised.append(slots[k].c); take(k); k++
                }
                if (raised.length == 2) { cents = raised.toString(); i = k }
            }
        }
        val digits = main.count { it.isDigit() } + (cents?.length ?: 0)
        // Swallow leftover digits of the word (a lone raised digit) so they aren't read as another number.
        var end = i
        while (end < slots.size && slots[end].c.isDigit() && !slots[end].wordStart) end++
        // A long bare run is a barcode or article number; grouped amounts ("1.299.000,00") may be longer.
        val bare = main.all(Char::isDigit) && cents == null
        return Run(start, end, main.toString(), cents, whole, box, mainHeight(), rejected = bare && digits > MAX_CODE_DIGITS)
    }

    /** Same-size digits in the word starting at [from]; raised cents after them don't count. */
    private fun digitsAhead(slots: List<Slot>, from: Int): Int {
        val h = slots[from].glyph.box.height
        var k = from
        while (k < slots.size && slots[k].c.isDigit() && (k == from || !slots[k].wordStart) && similar(slots[k].glyph.box.height, h)) k++
        return k - from
    }

    /** A digit printed smaller and raised: its top near the main digits' top, well above their bottom. */
    private fun isRaised(b: Box, mainFirst: Box, mainHeight: Float): Boolean =
        b.height < mainHeight * 0.75f && b.bottom < mainFirst.bottom - mainHeight * 0.25f && b.top < mainFirst.top + mainHeight * 0.35f

    private fun similar(a: Float, b: Float) = a > b * 0.75f && a < b * 1.33f

    /**
     * Reads [raw] (digits with `.`, `,`, `'` or space separators), spec.md §12.4:
     * 3 digits after a separator mean thousands, 1–2 mean a fraction; with several, the last one
     * may be decimal; currencies without a fraction take every separator as thousands.
     */
    fun parseAmount(raw: String, fractionDigits: Int, cents: String? = null, whole: Boolean = false): BigDecimal? {
        val groups = raw.split(Regex("[.,'’ ]"))
        if (groups.any { it.isEmpty() || !it.all(Char::isDigit) }) return null
        val seps = raw.filter { !it.isDigit() }
        val last = seps.lastOrNull()
        val tail = groups.last()
        val decimal = cents == null && !whole && last != null && last in ".," &&
            (tail.length in 1..2 || (fractionDigits > 0 && tail.length == 3 && groups.size == 2 && groups[0] == "0"))
        if (!decimal && last != null && last in ".," && tail.length != 3 && !whole && cents == null) return null
        val integer = if (decimal) groups.dropLast(1).joinToString("") else groups.joinToString("")
        val fraction = when {
            cents != null -> cents
            decimal -> tail
            else -> ""
        }
        val value = BigDecimal(if (fraction.isEmpty()) integer else "$integer.$fraction")
        // A currency without cents rounds them off instead of reading "12,90" as 1290.
        return if (fractionDigits == 0) value.setScale(0, RoundingMode.HALF_UP) else value
    }

    private fun isDate(raw: String) = FULL_DATE.matches(raw)

    private const val SEPARATORS = ".,'’"
    private const val DASHES = "-–—"
    private const val MAX_CODE_DIGITS = 8
    private const val MAX_DIGITS = 12

    // The bundled model reads Latin only, so Cyrillic comes back as look-alikes: "мл" → "mn", "г" → "r",
    // "шт" → "wt", "руб." → "py6.", "тг" → "tr", "за" → "3a". Both spellings are listed.
    private const val UNITS = "kg|g|gr|mg|l|ml|cl|dl|cm|mm|m|oz|lb|lbs|pcs|pc|шт|кг|г|гр|мг|л|мл|см|мм|м|r|n|mn|wt|ut|%"
    private const val CURRENCY = "[€$£¥₸₽₺₹₩₴₪₫฿₱₦₼₾]|eur|usd|gbp|rub|kzt|chf|aed|try|pln|czk|huf|sek|nok|dkk|uah|" +
        "руб|py[6бõo]|р\\.?|p\\.?|тг|tr|t|тенге|грн|zł|kč|kr|ft|lei|fr\\.?|[a-z]{1,2}\\$|rs\\.?|rm|s/\\.?|rp"

    // Letters allowed right before the digits: a currency ("EUR12,99") or a quantity ("2x1,99").
    private val CURRENCY_PREFIX = Regex("($CURRENCY)\\s?|\\d+[x×]")
    private val UNIT_AFTER = Regex("^\\s?($UNITS)(?![\\p{L}])")
    private val CURRENCY_AFTER = Regex("^\\s?($CURRENCY)(?![\\p{L}])")
    private val CURRENCY_BEFORE = Regex("(?<![\\p{L}])($CURRENCY)\\s?$")
    private val PER_UNIT_AFTER = Regex("^\\s?(($CURRENCY)\\s?)?(/|per|pro|je|par|por|за|3a)\\s?(\\d+\\s?)?($UNITS)(?![\\p{L}])")
    // "2 x 1,99", "3 for 5": a quantity, not a price.
    private val QUANTITY_AFTER = Regex("^\\s?(x|×|for|für|pour)\\s?\\d")
    private val PER_UNIT_BEFORE = Regex("(за|3a|per|pro|je|par|por|/)\\s?(\\d+\\s?)?($UNITS)(?![\\p{L}])")
    private val FULL_DATE = Regex("\\d{1,2}[./]\\d{1,2}[./]\\d{2,4}")
    // "12:30", "25/10": the neighbour across ':' or '/' is a digit.
    private val TIME_OR_DATE_BEFORE = Regex("\\d[:/]$")
    private val TIME_OR_DATE_AFTER = Regex("^[:/]\\d")
}
