package io.github.shpuljka.kurs.calc

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** A key of the converter keypad, spec.md §1.3. */
sealed interface Key {
    data class Digit(val char: Char) : Key
    data class Op(val symbol: Char) : Key
    data object Decimal : Key
    data object Percent : Key
    data object Backspace : Key
    data object Clear : Key
    data object Equals : Key
}

/** Expression text with a cursor ([start] == [end]) or a selection; offsets into [text]. */
data class Editing(val text: String, val start: Int = text.length, val end: Int = start) {
    val min: Int get() = minOf(start, end).coerceIn(0, text.length)
    val max: Int get() = maxOf(start, end).coerceIn(0, text.length)
    val hasSelection: Boolean get() = min != max

    companion object {
        fun selectAll(text: String) = Editing(text, 0, text.length)
    }
}

/**
 * Calculator for the active currency row.
 *
 * The expression is stored as a locale-independent string: digits, `.` as the decimal
 * point, operators `+ - × ÷` and `%` after a number. Formatting for display happens in the UI.
 */
object Calculator {
    const val OPERATORS = "+-×÷"
    private const val MAX_DIGITS = 12
    private val MC = MathContext.DECIMAL64
    private val HUNDRED = BigDecimal(100)
    private val TOKEN = Regex("""\d+(?:\.\d*)?%?|\.\d+%?|[+\-×÷]""")

    fun hasOperation(expr: String): Boolean = expr.any { it in OPERATORS || it == '%' }

    /** Applies a key with the cursor at the end of [expr]. */
    fun press(expr: String, key: Key): String = edit(Editing(expr), key).text

    /**
     * Applies a key at the cursor, spec.md §1.3. Digits and the decimal point replace a selection;
     * operators, `%` and `=` continue the whole expression (a freshly selected amount + `+` gives `100+`).
     * [Key.Equals] needs currency precision, see [collapse]; here it only drops the selection.
     */
    fun edit(e: Editing, key: Key): Editing {
        val text = e.text
        return when (key) {
            Key.Clear -> Editing("")
            Key.Equals -> Editing(text)
            Key.Backspace -> when {
                e.hasSelection -> Editing(text.removeRange(e.min, e.max), e.min)
                e.min == 0 -> e
                else -> Editing(text.removeRange(e.min - 1, e.min), e.min - 1)
            }
            is Key.Op -> insertOperator(text, if (e.hasSelection) e.max else e.min, key.symbol) ?: e
            Key.Percent -> insertPercent(text, if (e.hasSelection) e.max else e.min) ?: e
            Key.Decimal, is Key.Digit -> {
                val base = text.removeRange(e.min, e.max)
                val inserted = if (key is Key.Digit) insertDigit(base, e.min, key.char) else insertDecimal(base, e.min)
                inserted ?: e
            }
        }
    }

    private fun numberAround(text: String, pos: Int): Pair<String, String> =
        text.substring(0, pos).takeLastWhile { it !in OPERATORS } to text.substring(pos).takeWhile { it !in OPERATORS }

    private fun insertDigit(text: String, pos: Int, digit: Char): Editing? {
        val (before, after) = numberAround(text, pos)
        return when {
            '%' in before -> null
            (before + after).count { it.isDigit() } >= MAX_DIGITS -> null
            // No leading zeros: "0" then 5 becomes "5"; a 0 in front of other digits is ignored.
            before == "0" -> Editing(text.substring(0, pos - 1) + digit + text.substring(pos), pos)
            digit == '0' && before.isEmpty() && after.firstOrNull()?.isDigit() == true -> null
            else -> Editing(text.substring(0, pos) + digit + text.substring(pos), pos + 1)
        }
    }

    private fun insertDecimal(text: String, pos: Int): Editing? {
        val (before, after) = numberAround(text, pos)
        val insert = if (before.isEmpty()) "0." else "."
        return when {
            '.' in before || '.' in after || '%' in before -> null
            else -> Editing(text.substring(0, pos) + insert + text.substring(pos), pos + insert.length)
        }
    }

    /** Next to another operator it replaces that one; never at the start. */
    private fun insertOperator(text: String, pos: Int, op: Char): Editing? {
        val prev = text.getOrNull(pos - 1)
        val next = text.getOrNull(pos)
        return when {
            pos == 0 -> null
            prev != null && prev in OPERATORS ->
                if (pos == 1) null else Editing(text.substring(0, pos - 1) + op + text.substring(pos), pos)
            next != null && next in OPERATORS -> Editing(text.substring(0, pos) + op + text.substring(pos + 1), pos + 1)
            else -> Editing(text.substring(0, pos) + op + text.substring(pos), pos + 1)
        }
    }

    /** Only right after a digit and at the end of its number. */
    private fun insertPercent(text: String, pos: Int): Editing? {
        val prev = text.getOrNull(pos - 1)
        val next = text.getOrNull(pos)
        if (prev?.isDigit() != true || (next != null && next !in OPERATORS)) return null
        return Editing(text.substring(0, pos) + '%' + text.substring(pos), pos + 1)
    }

    /**
     * Evaluates [expr] with the usual precedence. `%` after `+`/`−` is a share of the running
     * sum (`120 + 8 %` = 129.6), after `×`/`÷` or at the start it is a plain fraction.
     * Returns null on division by zero.
     */
    fun evaluate(expr: String): BigDecimal? {
        var sum = BigDecimal.ZERO
        var sign = BigDecimal.ONE
        var term: BigDecimal? = null
        var op: Char? = null
        for (match in TOKEN.findAll(expr)) {
            val token = match.value
            if (token.length == 1 && token[0] in OPERATORS) {
                op = token[0]
                continue
            }
            val percent = token.endsWith('%')
            val n = BigDecimal(token.trimEnd('%').trimEnd('.'))
            val fraction = if (percent) n.divide(HUNDRED, MC) else n
            val current = term
            term = when {
                current == null -> fraction
                op == '×' -> current.multiply(fraction, MC)
                op == '÷' -> if (fraction.signum() == 0) return null else current.divide(fraction, MC)
                else -> {
                    sum = sum.add(sign.multiply(current), MC)
                    sign = if (op == '-') BigDecimal.ONE.negate() else BigDecimal.ONE
                    if (percent) sum.multiply(fraction, MC) else n
                }
            }
        }
        return sum.add(sign.multiply(term ?: BigDecimal.ZERO), MC)
    }

    /** The `=` key: replaces the expression with its result rounded to [fractionDigits]. */
    fun collapse(expr: String, fractionDigits: Int): String {
        val value = evaluate(expr) ?: return expr
        return value.setScale(fractionDigits, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }
}
