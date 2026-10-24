package io.github.shpuljka.kurs.calc

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale

/** Locale-aware display of amounts and calculator input, spec.md §5. */
class AmountFormat(locale: Locale) {
    private val separator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
    private val integers = NumberFormat.getIntegerInstance(locale)
    private val numbers = NumberFormat.getNumberInstance(locale).apply { roundingMode = RoundingMode.HALF_UP }
    private val token = Regex("""\d+(?:\.\d*)?%?|[+\-×÷]""")

    /** Converted amount: whole values without decimals, otherwise exactly [fractionDigits]. */
    fun amount(value: BigDecimal, fractionDigits: Int): String {
        val rounded = value.setScale(fractionDigits, RoundingMode.HALF_UP)
        val digits = if (rounded.stripTrailingZeros().scale() <= 0) 0 else fractionDigits
        numbers.minimumFractionDigits = digits
        numbers.maximumFractionDigits = digits
        // Same minus sign as in the typed expression.
        return numbers.format(rounded).replace('-', '−')
    }

    /** A number as the user typed it: grouped integer part, decimals kept verbatim. */
    fun typed(number: String): String {
        if (number.isEmpty()) return "0"
        val percent = if (number.endsWith('%')) "%" else ""
        val body = number.removeSuffix("%")
        val whole = body.substringBefore('.')
        val grouped = integers.format(if (whole.isEmpty()) BigInteger.ZERO else BigInteger(whole))
        val fraction = if ('.' in body) separator + body.substringAfter('.') else ""
        return grouped + fraction + percent
    }

    /** The whole expression, e.g. `1 200 + 8%`. */
    fun expression(expr: String): String = editable(expr).text

    /**
     * [expr] for display, with offset maps so the UI can draw the cursor and turn a tap into a
     * position in [expr]. Integer parts are grouped by thousands, the decimal point localized,
     * operators spaced.
     */
    fun editable(expr: String): Formatted {
        val out = StringBuilder()
        val rawToDisplay = IntArray(expr.length + 1)
        var i = 0
        var inFraction = false
        while (i < expr.length) {
            val ch = expr[i]
            when {
                ch.isDigit() && !inFraction -> {
                    val end = (i until expr.length).firstOrNull { !expr[it].isDigit() } ?: expr.length
                    for (k in i until end) {
                        if (k > i && (end - k) % 3 == 0) out.append(grouping)
                        rawToDisplay[k] = out.length
                        out.append(expr[k])
                    }
                    i = end
                    continue
                }
                ch == '-' && i == 0 -> {
                    // Sign of a negative result, not a binary minus.
                    rawToDisplay[i] = out.length
                    out.append('−')
                }
                ch in Calculator.OPERATORS -> {
                    rawToDisplay[i] = out.length
                    out.append(' ').append(if (ch == '-') '−' else ch).append(' ')
                    inFraction = false
                }
                ch == '.' -> {
                    rawToDisplay[i] = out.length
                    out.append(separator)
                    inFraction = true
                }
                else -> {
                    rawToDisplay[i] = out.length
                    out.append(ch)
                }
            }
            i++
        }
        rawToDisplay[expr.length] = out.length
        // Each display offset goes to the nearest raw offset (ties to the earlier one).
        val displayToRaw = IntArray(out.length + 1)
        var raw = 0
        for (d in 0..out.length) {
            while (raw < expr.length && rawToDisplay[raw + 1] - d < d - rawToDisplay[raw]) raw++
            displayToRaw[d] = raw
        }
        return Formatted(out.toString(), rawToDisplay, displayToRaw)
    }

    private val grouping = DecimalFormatSymbols.getInstance(locale).groupingSeparator
}

/** Display text plus offset maps between it and the raw expression. */
class Formatted(val text: String, val rawToDisplay: IntArray, val displayToRaw: IntArray)
