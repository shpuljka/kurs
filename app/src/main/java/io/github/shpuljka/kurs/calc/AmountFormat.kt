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
        return numbers.format(rounded)
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
    fun expression(expr: String): String = token.findAll(expr).joinToString("") {
        when (val t = it.value) {
            "-" -> " − "
            "+", "×", "÷" -> " $t "
            else -> typed(t)
        }
    }
}
