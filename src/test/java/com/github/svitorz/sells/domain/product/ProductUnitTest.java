package com.github.svitorz.sells.domain.product;

import com.github.svitorz.sells.domain.shared.DomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductUnitTest {

    @Test
    void unitQuantityIsScaledAndMustBeWhole() {
        assertEquals(new BigDecimal("2.000"), ProductUnit.UN.quantity(new BigDecimal("2")));
        assertThrows(DomainException.class, () -> ProductUnit.UN.quantity(new BigDecimal("1.5")));
    }

    @Test
    void kilogramQuantityAcceptsThreeDecimals() {
        assertEquals(new BigDecimal("0.375"), ProductUnit.KG.quantity(new BigDecimal("0.375")));
        assertThrows(DomainException.class, () -> ProductUnit.KG.quantity(new BigDecimal("0.3755")));
    }

    @Test
    void quantityMustBePositive() {
        assertThrows(DomainException.class, () -> ProductUnit.UN.quantity(BigDecimal.ZERO));
        assertThrows(DomainException.class, () -> ProductUnit.UN.quantity(new BigDecimal("-1")));
    }

    @Test
    void deltaAcceptsNegativeButNotZero() {
        assertEquals(new BigDecimal("-2.000"), ProductUnit.UN.delta(new BigDecimal("-2")));
        assertThrows(DomainException.class, () -> ProductUnit.UN.delta(BigDecimal.ZERO));
    }

    @Test
    void stockAcceptsZeroButNotNegative() {
        assertEquals(new BigDecimal("0.000"), ProductUnit.KG.stock(BigDecimal.ZERO));
        assertThrows(DomainException.class, () -> ProductUnit.KG.stock(new BigDecimal("-1")));
    }
}
