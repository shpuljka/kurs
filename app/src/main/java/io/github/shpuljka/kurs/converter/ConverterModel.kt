package io.github.shpuljka.kurs.converter

import io.github.shpuljka.kurs.calc.Calculator
import io.github.shpuljka.kurs.calc.Editing
import io.github.shpuljka.kurs.calc.Key
import io.github.shpuljka.kurs.data.Currencies
import io.github.shpuljka.kurs.data.Rates
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * State of the main screen, spec.md §1: currency rows, which one is being typed into,
 * and that row's expression with its cursor/selection. Pure — no Android, easy to test.
 * A freshly shown amount is fully selected, so typing replaces it (spec §1.2).
 */
data class ConverterModel(
    val codes: List<String>,
    val active: Int = 0,
    val expr: String = START_AMOUNT,
    val selStart: Int = 0,
    val selEnd: Int = expr.length,
) {
    val editing: Editing get() = Editing(expr, selStart, selEnd)

    val canAdd: Boolean get() = codes.size < MAX_ROWS
    val canRemove: Boolean get() = codes.size > MIN_ROWS

    /** Value of each row; null where it can't be computed (no rate, division by zero). */
    fun values(rates: Rates?): List<BigDecimal?> {
        val source = Calculator.evaluate(expr)
        return codes.mapIndexed { i, code ->
            when {
                i == active -> source
                source == null || rates == null -> null
                else -> rates.convert(source, codes[active], code)
            }
        }
    }

    fun press(key: Key): ConverterModel {
        val result = if (key == Key.Equals) {
            Editing(Calculator.collapse(expr, Currencies.fractionDigits(codes[active])))
        } else {
            Calculator.edit(editing, key)
        }
        return copy(expr = result.text, selStart = result.min, selEnd = result.max)
    }

    /** Places the cursor at [position] in [expr] (a tap in the active row). */
    fun moveCursor(position: Int): ConverterModel {
        val p = position.coerceIn(0, expr.length)
        return copy(selStart = p, selEnd = p)
    }

    fun selectAll(): ConverterModel = copy(selStart = 0, selEnd = expr.length)

    /** Makes [index] the input row, seeding it with its current value, fully selected. */
    fun select(index: Int, rates: Rates?): ConverterModel {
        if (index == active) return this
        val value = values(rates)[index] ?: BigDecimal.ZERO
        val seed = plain(value, codes[index])
        return copy(active = index, expr = seed, selStart = 0, selEnd = seed.length)
    }

    /** Row a scanned price goes into, spec.md §12.5: the local currency's, else the active one. */
    fun scanTarget(local: String?): Int = codes.indexOf(local).takeIf { it >= 0 } ?: active

    /** Rows previewed under a scanned price: the first two other than [target]. */
    fun scanPreview(target: Int): List<Int> = codes.indices.filter { it != target }.take(SCAN_PREVIEW_ROWS)

    /**
     * Puts a scanned [amount] into row [target] and makes it active, spec.md §12.5: after an open
     * operation (`120+`) it is appended, otherwise it replaces the amount and is selected like any fresh amount.
     */
    fun insertScanned(target: Int, amount: BigDecimal): ConverterModel {
        if (target !in codes.indices) return this
        // Rounded like the scanner panel shows it: "0.299" for USD goes in as 0.3.
        val price = amount.setScale(Currencies.fractionDigits(codes[target]), RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        val open = target == active && expr.isNotEmpty() && expr.last() in Calculator.OPERATORS
        return if (open) {
            val text = expr + price
            copy(expr = text, selStart = text.length, selEnd = text.length)
        } else {
            copy(active = target, expr = price, selStart = 0, selEnd = price.length)
        }
    }

    fun add(code: String): ConverterModel =
        if (!canAdd || code in codes) this else copy(codes = codes + code)

    fun remove(index: Int, rates: Rates?): ConverterModel {
        if (!canRemove || index !in codes.indices) return this
        return when {
            index == active -> {
                // Keep the amount: move input to the first remaining row with its converted value.
                val remaining = codes.filterIndexed { i, _ -> i != index }
                val values = values(rates).filterIndexed { i, _ -> i != index }
                ConverterModel(remaining, 0, plain(values[0] ?: BigDecimal.ZERO, remaining[0]))
            }
            index < active -> copy(codes = codes.filterIndexed { i, _ -> i != index }, active = active - 1)
            else -> copy(codes = codes.filterIndexed { i, _ -> i != index })
        }
    }

    fun replace(index: Int, code: String): ConverterModel = when {
        index !in codes.indices || codes[index] == code -> this
        // Picking a currency already on screen swaps the two rows.
        code in codes -> copy(codes = codes.toMutableList().apply { this[indexOf(code)] = this[index]; this[index] = code })
        else -> copy(codes = codes.toMutableList().apply { this[index] = code })
    }

    /**
     * Spec §3, travelling: the new country's currency becomes the first row and the rest shift down.
     * The typed-into currency stays active. With all 10 rows taken, the last row makes room.
     */
    fun withLocal(code: String): ConverterModel {
        if (codes.firstOrNull() == code) return this
        val activeCode = codes[active]
        val reordered = (listOf(code) + codes.filter { it != code }).take(MAX_ROWS)
        val newActive = reordered.indexOf(activeCode)
        return if (newActive >= 0) {
            copy(codes = reordered, active = newActive)
        } else {
            ConverterModel(reordered, 0, START_AMOUNT)
        }
    }

    companion object {
        const val MAX_ROWS = 10
        const val MIN_ROWS = 2
        const val START_AMOUNT = "100"
        const val SCAN_PREVIEW_ROWS = 2

        /** Local currency + USD + EUR; if local is USD/EUR the next popular one fills in. */
        fun initial(localCode: String): ConverterModel =
            ConverterModel(listOf(localCode) + Currencies.POPULAR.filter { it != localCode }.take(2))

        /** Seed for a newly selected row. The keypad has no unary minus, so negatives become 0. */
        private fun plain(value: BigDecimal, code: String): String =
            if (value.signum() < 0) "0"
            else value.setScale(Currencies.fractionDigits(code), RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }
}
