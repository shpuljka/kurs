package io.github.shpuljka.kurs.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class CalculatorTest {

    private fun assertEval(expected: String, expr: String) {
        val actual = Calculator.evaluate(expr)
        assertTrue("$expr = $actual, expected $expected", actual != null && BigDecimal(expected).compareTo(actual) == 0)
    }

    private fun type(vararg keys: Key, from: String = ""): String =
        keys.fold(from) { expr, key -> Calculator.press(expr, key) }

    private fun d(c: Char) = Key.Digit(c)

    // --- evaluation -------------------------------------------------------

    @Test fun `empty expression is zero`() = assertEval("0", "")

    @Test fun `plain number`() = assertEval("120", "120")

    @Test fun `operator precedence`() = assertEval("134.6", "5+120×1.08")

    @Test fun `left to right for same precedence`() = assertEval("5", "20÷2-5")

    @Test fun `percent after plus is share of running sum - US tax case`() = assertEval("129.6", "120+8%")

    @Test fun `percent after minus`() = assertEval("90", "100-10%")

    @Test fun `percent counts whole preceding sum`() = assertEval("220", "100×2+10%")

    @Test fun `percent after multiply is plain fraction`() = assertEval("9.6", "120×8%")

    @Test fun `percent after divide is plain fraction`() = assertEval("200", "10÷5%")

    @Test fun `leading percent is fraction`() = assertEval("0.08", "8%")

    @Test fun `no binary float errors`() = assertEval("0.3", "0.1+0.2")

    @Test fun `trailing operator is ignored`() = assertEval("120", "120+")

    @Test fun `trailing decimal point is ignored`() = assertEval("12", "12.")

    @Test fun `division by zero has no result`() = assertNull(Calculator.evaluate("5÷0"))

    @Test fun `hasOperation detects operators and percent`() {
        assertFalse(Calculator.hasOperation("120.5"))
        assertTrue(Calculator.hasOperation("120+"))
        assertTrue(Calculator.hasOperation("8%"))
    }

    // --- input rules ------------------------------------------------------

    @Test fun `digits append`() = assertEquals("120", type(d('1'), d('2'), d('0')))

    @Test fun `leading zero is replaced`() = assertEquals("5", type(d('0'), d('5')))

    @Test fun `repeated zeros collapse`() = assertEquals("0", type(d('0'), d('0')))

    @Test fun `decimal on empty starts with zero`() = assertEquals("0.", type(Key.Decimal))

    @Test fun `second decimal in a number is ignored`() =
        assertEquals("1.5", type(d('1'), Key.Decimal, d('5'), Key.Decimal))

    @Test fun `decimal allowed again in next number`() = assertEquals("1.5+0.", type(Key.Decimal, from = "1.5+"))

    @Test fun `operator on empty is ignored`() = assertEquals("", type(Key.Op('+')))

    @Test fun `repeated operator replaces previous`() = assertEquals("120×", type(Key.Op('+'), Key.Op('×'), from = "120"))

    @Test fun `percent only after a digit`() {
        assertEquals("", type(Key.Percent))
        assertEquals("120+", type(Key.Percent, from = "120+"))
        assertEquals("120+8%", type(Key.Percent, from = "120+8"))
        assertEquals("120+8%", type(Key.Percent, from = "120+8%"))
    }

    @Test fun `no digits after percent`() = assertEquals("8%", type(d('1'), Key.Decimal, from = "8%"))

    @Test fun `number limited to 12 digits`() = assertEquals("123456789012", type(d('3'), from = "123456789012"))

    @Test fun `decimals count toward limit but point does not`() =
        assertEquals("12345678901.2", type(d('3'), from = "12345678901.2"))

    @Test fun `backspace removes last char`() = assertEquals("120+", type(Key.Backspace, from = "120+8"))

    @Test fun `backspace on empty stays empty`() = assertEquals("", type(Key.Backspace))

    @Test fun `clear empties`() = assertEquals("", type(Key.Clear, from = "120+8%"))

    // --- editing at the cursor / over a selection --------------------------

    private fun edit(text: String, start: Int, end: Int = start, vararg keys: Key): Editing =
        keys.fold(Editing(text, start, end)) { e, key -> Calculator.edit(e, key) }

    @Test fun `typing over a full selection replaces the amount`() =
        assertEquals(Editing("5", 1), edit("1234.5", 0, 6, d('5')))

    @Test fun `decimal over a full selection starts a new number`() =
        assertEquals(Editing("0.", 2), edit("100", 0, 3, Key.Decimal))

    @Test fun `operator over a full selection continues the expression`() =
        assertEquals(Editing("100+", 4), edit("100", 0, 3, Key.Op('+')))

    @Test fun `percent over a full selection continues the expression`() =
        assertEquals(Editing("100%", 4), edit("100", 0, 3, Key.Percent))

    @Test fun `backspace deletes the selection`() = assertEquals(Editing("", 0), edit("100", 0, 3, Key.Backspace))

    @Test fun `digit in the middle of a number`() = assertEquals(Editing("1534", 2), edit("134", 1, 1, d('5')))

    @Test fun `backspace in the middle`() = assertEquals(Editing("14", 1), edit("134", 2, 2, Key.Backspace))

    @Test fun `backspace at the start does nothing`() = assertEquals(Editing("134", 0), edit("134", 0, 0, Key.Backspace))

    @Test fun `decimal point rejected if the number already has one after the cursor`() =
        assertEquals(Editing("1.5", 1), edit("1.5", 1, 1, Key.Decimal))

    @Test fun `operator inserted between digits`() = assertEquals(Editing("12+34", 3), edit("1234", 2, 2, Key.Op('+')))

    @Test fun `operator next to an operator after the cursor replaces it`() =
        assertEquals(Editing("12×34", 3), edit("12+34", 2, 2, Key.Op('×')))

    @Test fun `operator at the very start is ignored`() = assertEquals(Editing("12", 0), edit("12", 0, 0, Key.Op('+')))

    @Test fun `zero in front of digits is ignored`() = assertEquals(Editing("5", 0), edit("5", 0, 0, d('0')))

    @Test fun `percent only at the end of a number`() {
        assertEquals(Editing("12", 1), edit("12", 1, 1, Key.Percent))
        assertEquals(Editing("12%+3", 3), edit("12+3", 2, 2, Key.Percent))
    }

    @Test fun `digit limit counts the whole number around the cursor`() =
        assertEquals(Editing("123456789012", 3), edit("123456789012", 3, 3, d('7')))

    @Test fun `leading decimal point after editing still evaluates`() = assertEval("0.5", ".5")

    // --- collapse (=) -----------------------------------------------------

    @Test fun `collapse rounds to currency digits`() = assertEquals("129.6", Calculator.collapse("120+8%", fractionDigits = 2))

    @Test fun `collapse with zero digits`() = assertEquals("130", Calculator.collapse("120+8%", fractionDigits = 0))

    @Test fun `collapse keeps expression when no result`() = assertEquals("5÷0", Calculator.collapse("5÷0", fractionDigits = 2))

    @Test fun `collapse of whole number has no fraction`() = assertEquals("200", Calculator.collapse("100×2", fractionDigits = 2))
}
