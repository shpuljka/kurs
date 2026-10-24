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
    private val TOKEN = Regex("""\d+(?:\.\d*)?%?|[+\-×÷]""")

    fun hasOperation(expr: String): Boolean = expr.any { it in OPERATORS || it == '%' }

    /** Applies a key to [expr]. [Key.Equals] needs currency precision, see [collapse]. */
    fun press(expr: String, key: Key): String {
        val last = expr.lastOrNull()
        val number = expr.takeLastWhile { it !in OPERATORS }
        return when (key) {
            Key.Clear -> ""
            Key.Backspace -> expr.dropLast(1)
            Key.Equals -> expr
            is Key.Op -> when {
                expr.isEmpty() -> expr
                last != null && last in OPERATORS -> expr.dropLast(1) + key.symbol
                else -> expr + key.symbol
            }
            Key.Percent -> if (last?.isDigit() == true) "$expr%" else expr
            Key.Decimal -> when {
                '.' in number || '%' in number -> expr
                number.isEmpty() -> "${expr}0."
                else -> "$expr."
            }
            is Key.Digit -> when {
                '%' in number -> expr
                number.count { it.isDigit() } >= MAX_DIGITS -> expr
                number == "0" -> expr.dropLast(1) + key.char
                else -> expr + key.char
            }
        }
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
