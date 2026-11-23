package com.github.svitorz.sells.domain.stock;

import com.github.svitorz.sells.domain.shared.DomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StockMovementTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 15, 10, 0);

    @Test
    void saleStoresNegativeQuantity() {
        StockMovement movement = StockMovement.sale(10L, new BigDecimal("2"), 1L, 1L, NOW);

        assertEquals(0, new BigDecimal("-2").compareTo(movement.getQuantity()));
        assertEquals(1L, movement.getSaleId());
    }

    @Test
    void saleReturnStoresPositiveQuantity() {
        StockMovement movement = StockMovement.saleReturn(10L, new BigDecimal("2"), 1L, 1L, NOW);

        assertEquals(0, new BigDecimal("2").compareTo(movement.getQuantity()));
    }

    @Test
    void adjustmentWithoutNotesIsRejected() {
        assertThrows(DomainException.class,
                () -> StockMovement.adjustment(10L, new BigDecimal("1"), 1L, null, NOW));
    }

    @Test
    void entryWithZeroIsRejected() {
        assertThrows(DomainException.class,
                () -> StockMovement.entry(10L, BigDecimal.ZERO, 1L, null, NOW));
    }

    @Test
    void adjustmentWithZeroIsRejected() {
        assertThrows(DomainException.class,
                () -> StockMovement.adjustment(10L, BigDecimal.ZERO, 1L, "Contagem", NOW));
    }
}
