package com.github.svitorz.sells.domain.sale;

import com.github.svitorz.sells.domain.product.Product;
import com.github.svitorz.sells.domain.product.ProductUnit;
import com.github.svitorz.sells.domain.shared.DomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SaleTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 15, 10, 0);
    private static final LocalDateTime LATER = NOW.plusHours(1);

    private static Product product(long id, String name, String price, ProductUnit unit) {
        return Product.restore(id, name, null, new BigDecimal(price), unit, null,
                new BigDecimal("100.000"), Set.of(), NOW, NOW, null, 0L);
    }

    private static Product deletedProduct(long id, String name) {
        return Product.restore(id, name, null, new BigDecimal("1.00"), ProductUnit.UN, null,
                new BigDecimal("1.000"), Set.of(), NOW, NOW, NOW, 0L);
    }

    private static Sale draft() {
        return Sale.startDraft(1L, NOW);
    }

    @Test
    void startDraftIsEmptyDraftWithZeroSubtotal() {
        Sale sale = draft();

        assertEquals(SaleStatus.DRAFT, sale.getStatus());
        assertEquals(new BigDecimal("0.00"), sale.getSubtotal());
        assertEquals(0, sale.getItems().size());
        assertNull(sale.getCustomerId());
    }

    @Test
    void addItemCopiesProductPrice() {
        Sale sale = draft();

        SaleItem item = sale.addItem(product(10L, "Arroz", "10.00", ProductUnit.KG), new BigDecimal("1.5"), NOW);

        assertEquals(new BigDecimal("10.00"), item.getUnitPrice());
    }

    @Test
    void addingSameProductTwiceSumsIntoOneLine() {
        Sale sale = draft();
        Product arroz = product(10L, "Arroz", "10.00", ProductUnit.UN);

        sale.addItem(arroz, new BigDecimal("1"), NOW);
        sale.addItem(arroz, new BigDecimal("2"), NOW);

        assertEquals(1, sale.getItems().size());
        assertEquals(0, new BigDecimal("3.000").compareTo(sale.getItems().get(0).getQuantity()));
    }

    @Test
    void addingSameProductAfterPriceChangeKeepsOriginalUnitPrice() {
        Sale sale = draft();
        sale.addItem(product(10L, "Arroz", "10.00", ProductUnit.UN), new BigDecimal("1"), NOW);

        SaleItem item = sale.addItem(product(10L, "Arroz", "12.00", ProductUnit.UN), new BigDecimal("1"), NOW);

        assertEquals(new BigDecimal("10.00"), item.getUnitPrice());
    }

    @Test
    void subtotalRoundsEachLineHalfUp() {
        Sale kilo = draft();
        kilo.addItem(product(1L, "Queijo", "9.99", ProductUnit.KG), new BigDecimal("0.125"), NOW);
        assertEquals(new BigDecimal("1.25"), kilo.getSubtotal());

        Sale ham = draft();
        ham.addItem(product(2L, "Presunto", "10.00", ProductUnit.KG), new BigDecimal("0.333"), NOW);
        assertEquals(new BigDecimal("3.33"), ham.getSubtotal());

        Sale mixed = draft();
        mixed.addItem(product(3L, "Pão", "2.50", ProductUnit.UN), new BigDecimal("3"), NOW);
        mixed.addItem(product(4L, "Presunto", "7.99", ProductUnit.KG), new BigDecimal("0.5"), NOW);
        assertEquals(new BigDecimal("11.50"), mixed.getSubtotal());
    }

    @Test
    void fractionalQuantityForUnitProductIsRejected() {
        assertThrows(DomainException.class,
                () -> draft().addItem(product(10L, "Arroz", "10.00", ProductUnit.UN), new BigDecimal("1.5"), NOW));
    }

    @Test
    void deletedProductIsRejectedWithExactMessage() {
        DomainException e = assertThrows(DomainException.class,
                () -> draft().addItem(deletedProduct(10L, "X"), new BigDecimal("1"), NOW));
        assertEquals("O produto \"X\" foi excluído e não pode ser vendido.", e.getMessage());
    }

    @Test
    void changeItemQuantityRecalculatesSubtotal() {
        Sale sale = draft();
        sale.addItem(product(10L, "Arroz", "10.00", ProductUnit.UN), new BigDecimal("1"), NOW);

        sale.changeItemQuantity(10L, new BigDecimal("3"), NOW);

        assertEquals(new BigDecimal("30.00"), sale.getSubtotal());
    }

    @Test
    void changeItemQuantityOfMissingItemIsRejected() {
        Sale sale = draft();

        DomainException e = assertThrows(DomainException.class,
                () -> sale.changeItemQuantity(99L, new BigDecimal("1"), NOW));
        assertEquals("O item não está nesta venda.", e.getMessage());
    }

    @Test
    void removeItemRecalculatesSubtotal() {
        Sale sale = draft();
        sale.addItem(product(10L, "Arroz", "10.00", ProductUnit.UN), new BigDecimal("1"), NOW);

        sale.removeItem(10L, NOW);

        assertEquals(new BigDecimal("0.00"), sale.getSubtotal());
        assertEquals(0, sale.getItems().size());
    }

    @Test
    void completeWithoutItemsIsRejected() {
        DomainException e = assertThrows(DomainException.class, () -> draft().complete(NOW));
        assertEquals("A venda precisa ter pelo menos um item para ser finalizada.", e.getMessage());
    }

    @Test
    void completeWithItemsSetsStatusAndDate() {
        Sale sale = draft();
        sale.addItem(product(10L, "Arroz", "10.00", ProductUnit.UN), new BigDecimal("1"), NOW);

        sale.complete(LATER);

        assertEquals(SaleStatus.COMPLETED, sale.getStatus());
        assertEquals(LATER, sale.getDate());
    }

    @Test
    void completedSaleCannotBeChanged() {
        Sale sale = draft();
        Product arroz = product(10L, "Arroz", "10.00", ProductUnit.UN);
        sale.addItem(arroz, new BigDecimal("1"), NOW);
        sale.complete(LATER);
        String message = "Esta venda não está mais em rascunho e não pode ser alterada.";

        assertEquals(message, assertThrows(DomainException.class,
                () -> sale.addItem(arroz, new BigDecimal("1"), NOW)).getMessage());
        assertEquals(message, assertThrows(DomainException.class,
                () -> sale.removeItem(10L, NOW)).getMessage());
        assertEquals(message, assertThrows(DomainException.class,
                () -> sale.changeItemQuantity(10L, new BigDecimal("2"), NOW)).getMessage());
        assertEquals(message, assertThrows(DomainException.class,
                () -> sale.assignCustomer(5L, NOW)).getMessage());
    }

    @Test
    void cancelOnDraftIsRejected() {
        DomainException e = assertThrows(DomainException.class, () -> draft().cancel(1L, "Erro", NOW));
        assertEquals("Somente vendas concluídas podem ser canceladas.", e.getMessage());
    }

    @Test
    void cancelWithBlankReasonIsRejected() {
        Sale sale = completedSale();

        assertThrows(DomainException.class, () -> sale.cancel(1L, "   ", LATER));
    }

    @Test
    void validCancelFillsCancellationFieldsAndSecondCancelIsRejected() {
        Sale sale = completedSale();

        sale.cancel(5L, "Erro de digitação", LATER);

        assertEquals(SaleStatus.CANCELLED, sale.getStatus());
        assertEquals(LATER, sale.getCancelledAt());
        assertEquals(5L, sale.getCancelledByUserId());
        assertEquals("Erro de digitação", sale.getCancelReason());
        assertThrows(DomainException.class, () -> sale.cancel(5L, "de novo", LATER));
    }

    private static Sale completedSale() {
        Sale sale = draft();
        sale.addItem(product(10L, "Arroz", "10.00", ProductUnit.UN), new BigDecimal("1"), NOW);
        sale.complete(LATER);
        return sale;
    }
}
