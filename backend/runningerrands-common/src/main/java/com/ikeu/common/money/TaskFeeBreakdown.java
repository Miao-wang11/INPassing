package com.ikeu.common.money;

import java.math.BigDecimal;

/** Payment breakdown. The legacy reward column is a prepaid TOTAL, not income. */
public record TaskFeeBreakdown(BigDecimal serviceFee, BigDecimal shoppingBudget, BigDecimal total) {
    private static final BigDecimal MAX = new BigDecimal("99999999.99");
    public static TaskFeeBreakdown of(BigDecimal tip, BigDecimal deliveryFee,
                                       BigDecimal productCost, BigDecimal total) {
        check(tip, "tip"); check(deliveryFee, "deliveryFee");
        check(productCost, "productCost"); check(total, "total");
        BigDecimal serviceFee = tip.add(deliveryFee);
        BigDecimal calculatedTotal = serviceFee.add(productCost);
        if (calculatedTotal.signum() <= 0 || calculatedTotal.compareTo(MAX) > 0
                || total.compareTo(calculatedTotal) != 0) {
            throw new IllegalArgumentException("Prepaid total != service fee + shopping budget, or outside DECIMAL(10,2)");
        }
        return new TaskFeeBreakdown(serviceFee, productCost, total);
    }
    private static void check(BigDecimal value, String field) {
        if (value == null || value.signum() < 0 || value.stripTrailingZeros().scale() > 2
                || value.compareTo(MAX) > 0) {
            throw new IllegalArgumentException("Invalid money field: " + field);
        }
    }
}
