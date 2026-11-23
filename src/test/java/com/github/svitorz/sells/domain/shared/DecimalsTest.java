package com.github.svitorz.sells.domain.shared;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DecimalsTest {

    @Test
    void moneyIsReturnedWithTwoDecimals() {
        assertEquals(new BigDecimal("10.00"), Decimals.money(new BigDecimal("10"), "Preço"));
    }

    @Test
    void moneyRejectsNullNegativeTooManyDecimalsAndTooLarge() {
        assertThrows(DomainException.class, () -> Decimals.money(null, "Preço"));
        assertThrows(DomainException.class, () -> Decimals.money(new BigDecimal("-0.01"), "Preço"));
        assertThrows(DomainException.class, () -> Decimals.money(new BigDecimal("1.234"), "Preço"));
        assertThrows(DomainException.class, () -> Decimals.money(new BigDecimal("100000000"), "Preço"));
    }

    @Test
    void formatUsesCommaAndDropsTrailingZeros() {
        assertEquals("2,5", Decimals.format(new BigDecimal("2.500")));
        assertEquals("3", Decimals.format(new BigDecimal("3.000")));
    }
}
