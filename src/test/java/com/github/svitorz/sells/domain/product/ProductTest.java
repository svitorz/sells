package com.github.svitorz.sells.domain.product;

import com.github.svitorz.sells.domain.shared.DomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 15, 10, 0);

    private static Product create(String barcode, String price) {
        return Product.create("Arroz", barcode, new BigDecimal(price), ProductUnit.KG, null, Set.of(), NOW);
    }

    private static Product restoreWithStock(String name, ProductUnit unit, String stock, LocalDateTime deletedAt) {
        return Product.restore(1L, name, null, new BigDecimal("10.00"), unit, null, new BigDecimal(stock),
                Set.of(), NOW, NOW, deletedAt, 0L);
    }

    @Test
    void createStartsWithZeroStockAndEqualDates() {
        Product product = create(null, "10");

        assertEquals(new BigDecimal("0.000"), product.getStock());
        assertEquals(NOW, product.getCreatedAt());
        assertEquals(NOW, product.getUpdatedAt());
    }

    @Test
    void blankBarcodeBecomesNull() {
        assertNull(create("   ", "10").getBarcode());
    }

    @Test
    void barcodeWithSpaceIsRejected() {
        DomainException e = assertThrows(DomainException.class, () -> create("789 100", "10"));
        assertEquals("O código de barras não pode conter espaços.", e.getMessage());
    }

    @Test
    void negativePriceIsRejected() {
        assertThrows(DomainException.class, () -> create(null, "-1"));
    }

    @Test
    void applyStockChangeAddsAndSubtracts() {
        Product product = create(null, "10");

        product.applyStockChange(new BigDecimal("5"), NOW);
        assertEquals(new BigDecimal("5.000"), product.getStock());

        product.applyStockChange(new BigDecimal("-2"), NOW);
        assertEquals(new BigDecimal("3.000"), product.getStock());
    }

    @Test
    void insufficientStockHasExactMessage() {
        Product product = restoreWithStock("Arroz", ProductUnit.UN, "2", null);

        DomainException e = assertThrows(DomainException.class,
                () -> product.applyStockChange(new BigDecimal("-3"), NOW));
        assertEquals("Estoque insuficiente para \"Arroz\". Disponível: 2.", e.getMessage());
    }

    @Test
    void fractionalDeltaIsRejectedForUnitProducts() {
        Product product = restoreWithStock("Arroz", ProductUnit.UN, "2", null);

        assertThrows(DomainException.class, () -> product.applyStockChange(new BigDecimal("0.5"), NOW));
    }

    @Test
    void changingKilogramToUnitWithFractionalStockIsRejected() {
        Product product = restoreWithStock("Arroz", ProductUnit.KG, "1.5", null);

        DomainException e = assertThrows(DomainException.class,
                () -> product.update("Arroz", null, new BigDecimal("10"), ProductUnit.UN, null, Set.of(), NOW));
        assertEquals("Não é possível mudar a unidade para UN enquanto o estoque tiver frações. "
                + "Ajuste o estoque antes.", e.getMessage());
    }

    @Test
    void deleteMarksProductAsDeleted() {
        Product product = create(null, "10");

        product.delete(NOW);

        assertTrue(product.isDeleted());
        assertEquals(NOW, product.getDeletedAt());
    }

    @Test
    void deleteTwiceIsRejected() {
        Product product = create(null, "10");
        product.delete(NOW);

        DomainException e = assertThrows(DomainException.class, () -> product.delete(NOW));
        assertEquals("O produto já foi excluído.", e.getMessage());
    }

    @Test
    void updateAfterDeleteIsRejected() {
        Product product = create(null, "10");
        product.delete(NOW);

        DomainException e = assertThrows(DomainException.class,
                () -> product.update("Arroz", null, new BigDecimal("10"), ProductUnit.KG, null, Set.of(), NOW));
        assertEquals("Produto excluído não pode ser alterado.", e.getMessage());
    }
}
