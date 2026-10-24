package io.github.shpuljka.kurs.converter

import io.github.shpuljka.kurs.calc.Calculator
import io.github.shpuljka.kurs.calc.Key
import io.github.shpuljka.kurs.data.Currencies
import io.github.shpuljka.kurs.data.Rates
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * State of the main screen, spec.md §1: currency rows, which one is being typed into,
 * and that row's expression. Pure — no Android, easy to test.
 */
data class ConverterModel(
    val codes: List<String>,
    val active: Int = 0,
    val expr: String = START_AMOUNT,
) {
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

    fun press(key: Key): ConverterModel = copy(
        expr = if (key == Key.Equals) {
            Calculator.collapse(expr, Currencies.fractionDigits(codes[active]))
        } else {
            Calculator.press(expr, key)
        },
    )

    /** Makes [index] the input row, seeding it with its current value. */
    fun select(index: Int, rates: Rates?): ConverterModel {
        if (index == active) return this
        val value = values(rates)[index] ?: BigDecimal.ZERO
        return copy(active = index, expr = plain(value, codes[index]))
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

        /** Local currency + USD + EUR; if local is USD/EUR the next popular one fills in. */
        fun initial(localCode: String): ConverterModel =
            ConverterModel(listOf(localCode) + Currencies.POPULAR.filter { it != localCode }.take(2))

        /** Seed for a newly selected row. The keypad has no unary minus, so negatives become 0. */
        private fun plain(value: BigDecimal, code: String): String =
            if (value.signum() < 0) "0"
            else value.setScale(Currencies.fractionDigits(code), RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }
}
