package com.ikeu.common.money;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class TaskFeeBreakdownTest {
    private static BigDecimal m(String s) { return new BigDecimal(s); }
    @Test void splitsServiceIncomeAndShoppingBudget() {
        var b = TaskFeeBreakdown.of(m("2.50"), m("1.00"), m("6.40"), m("9.90"));
        assertEquals(0,b.serviceFee().compareTo(m("3.50")));
        assertEquals(0,b.shoppingBudget().compareTo(m("6.40")));
        assertEquals(0,b.total().compareTo(b.serviceFee().add(b.shoppingBudget())));
    }
    @Test void allowsEitherComponentToBeZero() {
        assertEquals(0,TaskFeeBreakdown.of(m("2"),m("0"),m("0"),m("2")).shoppingBudget().signum());
        assertEquals(0,TaskFeeBreakdown.of(m("0"),m("0"),m("5"),m("5")).serviceFee().signum());
    }
    @Test void rejectsInvalidCurrencyAndMismatch() {
        assertThrows(IllegalArgumentException.class,()->TaskFeeBreakdown.of(m("-1"),m("0"),m("0"),m("1")));
        assertThrows(IllegalArgumentException.class,()->TaskFeeBreakdown.of(m("0.001"),m("0"),m("0"),m("0.001")));
        assertThrows(IllegalArgumentException.class,()->TaskFeeBreakdown.of(m("1"),m("1"),m("1"),m("4")));
        assertThrows(IllegalArgumentException.class,()->TaskFeeBreakdown.of(m("0"),m("0"),m("0"),m("0")));
        assertThrows(IllegalArgumentException.class,()->TaskFeeBreakdown.of(null,m("0"),m("0"),m("1")));
    }
}
