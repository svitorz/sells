package com.github.svitorz.sells.domain.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Decimals {

    /** Largest value that fits NUMERIC(10,2). */
    public static final BigDecimal MAX_MONEY = new BigDecimal("99999999.99");
    /** Largest value that fits NUMERIC(10,3). */
    public static final BigDecimal MAX_QUANTITY = new BigDecimal("9999999.999");

    private Decimals() {
    }

    /** Validates a money value (>= 0, at most 2 decimals) and returns it with scale 2. */
    public static BigDecimal money(BigDecimal value, String label) {
        if (value == null) {
            throw new DomainException("O campo " + label + " é obrigatório.");
        }
        if (value.signum() < 0) {
            throw new DomainException("O campo " + label + " não pode ser negativo.");
        }
        if (decimalPlaces(value) > 2) {
            throw new DomainException("O campo " + label + " aceita no máximo 2 casas decimais.");
        }
        if (value.compareTo(MAX_MONEY) > 0) {
            throw new DomainException("O campo " + label + " excede o valor máximo permitido.");
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    /** Number of significant decimal places (2.500 has 1). Never negative. */
    public static int decimalPlaces(BigDecimal value) {
        return Math.max(0, value.stripTrailingZeros().scale());
    }

    /** Formats for messages: 2.500 becomes "2,5" and 3.000 becomes "3". */
    public static String format(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString().replace('.', ',');
    }
}
