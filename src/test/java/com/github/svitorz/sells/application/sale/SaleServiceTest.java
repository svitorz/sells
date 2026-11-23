package com.github.svitorz.sells.application.sale;

import com.github.svitorz.sells.application.common.AccessDeniedException;
import com.github.svitorz.sells.application.common.NotFoundException;
import com.github.svitorz.sells.application.catalog.ProductView;
import com.github.svitorz.sells.application.customer.CustomerView;
import com.github.svitorz.sells.application.fakes.ApplicationTestFixture;
import com.github.svitorz.sells.application.user.NewUserCommand;
import com.github.svitorz.sells.domain.product.ProductUnit;
import com.github.svitorz.sells.domain.sale.SaleStatus;
import com.github.svitorz.sells.domain.shared.DomainException;
import com.github.svitorz.sells.domain.stock.StockMovement;
import com.github.svitorz.sells.domain.stock.StockMovementType;
import com.github.svitorz.sells.domain.user.Role;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaleServiceTest {

    private final ApplicationTestFixture f = new ApplicationTestFixture();

    private static BigDecimal dec(String value) {
        return new BigDecimal(value);
    }

    private BigDecimal stockOf(long productId) {
        return f.products.findAllByIds(List.of(productId)).get(0).getStock();
    }

    private List<StockMovement> movementsOf(long productId, StockMovementType type) {
        return f.movements.findByProductId(productId).stream().filter(m -> m.getType() == type).toList();
    }

    @Test
    void findOpenDraftReturnsTheLatestDraftOfTheUser() {
        f.loginAsAdmin();
        assertTrue(f.saleService.findOpenDraft().isEmpty());

        f.saleService.startDraft();
        SaleView second = f.saleService.startDraft();

        assertEquals(second.id(), f.saleService.findOpenDraft().orElseThrow().id());
    }

    @Test
    void addItemUpdatesSubtotalAndBarcodeLookupWorks() {
        f.loginAsAdmin();
        ProductView arroz = f.createProduct("Arroz", "7891", "10", ProductUnit.UN, "10");
        SaleView sale = f.saleService.startDraft();

        AddItemResult byId = f.saleService.addItem(sale.id(), arroz.id(), dec("2"));
        assertEquals(0, dec("20.00").compareTo(byId.sale().subtotal()));

        AddItemResult byBarcode = f.saleService.addItemByBarcode(sale.id(), "7891", dec("1"));
        assertEquals(0, dec("30.00").compareTo(byBarcode.sale().subtotal()));
    }

    @Test
    void unknownBarcodeIsNotFound() {
        f.loginAsAdmin();
        SaleView sale = f.saleService.startDraft();

        NotFoundException e = assertThrows(NotFoundException.class,
                () -> f.saleService.addItemByBarcode(sale.id(), "999", dec("1")));
        assertEquals("Nenhum produto encontrado com o código 999.", e.getMessage());
    }

    @Test
    void warningAppearsOnlyWhenTheLineExceedsStock() {
        f.loginAsAdmin();
        ProductView arroz = f.createProduct("Arroz", null, "10", ProductUnit.UN, "5");

        AddItemResult over = f.saleService.addItem(f.saleService.startDraft().id(), arroz.id(), dec("8"));
        AddItemResult within = f.saleService.addItem(f.saleService.startDraft().id(), arroz.id(), dec("2"));

        assertNotNull(over.warning());
        assertEquals(0, dec("5.000").compareTo(over.warning().available()));
        assertNull(within.warning());
    }

    @Test
    void operatorCannotChangeAnotherUsersDraftButAdminCan() {
        ProductView arroz = f.createProduct("Arroz", null, "10", ProductUnit.UN, "10");
        f.loginAsAdmin();
        f.userService.createUser(new NewUserCommand("Bia", "bia@example.com", "bia", "secret123", Role.USER, null));
        f.loginAsOperator();
        SaleView mine = f.saleService.startDraft();

        f.userService.login("bia", "secret123");
        AccessDeniedException e = assertThrows(AccessDeniedException.class,
                () -> f.saleService.addItem(mine.id(), arroz.id(), dec("1")));
        assertEquals("Este rascunho pertence a outro usuário.", e.getMessage());

        f.loginAsAdmin();
        assertEquals(1, f.saleService.addItem(mine.id(), arroz.id(), dec("1")).sale().items().size());
    }

    @Test
    void assignCustomerChecksExistenceAndNullRemovesIt() {
        f.loginAsAdmin();
        SaleView sale = f.saleService.startDraft();

        assertThrows(NotFoundException.class, () -> f.saleService.assignCustomer(sale.id(), 999L));

        CustomerView maria = f.createCustomer("Maria");
        assertEquals(maria.id(), f.saleService.assignCustomer(sale.id(), maria.id()).customerId());
        assertNull(f.saleService.assignCustomer(sale.id(), null).customerId());
    }

    @Test
    void completeReducesStockWritesSaleMovementsAndKeepsSubtotal() {
        f.loginAsAdmin();
        ProductView arroz = f.createProduct("Arroz", null, "10", ProductUnit.UN, "10");
        SaleView sale = f.saleService.startDraft();
        f.saleService.addItem(sale.id(), arroz.id(), dec("3"));

        SaleView done = f.saleService.complete(sale.id());

        assertEquals(SaleStatus.COMPLETED, done.status());
        assertEquals(0, dec("30.00").compareTo(done.subtotal()));
        assertEquals(0, dec("7.000").compareTo(stockOf(arroz.id())));
        List<StockMovement> sold = movementsOf(arroz.id(), StockMovementType.SALE);
        assertEquals(1, sold.size());
        assertEquals(0, dec("-3").compareTo(sold.get(0).getQuantity()));
        assertEquals(sale.id(), sold.get(0).getSaleId());
    }

    @Test
    void completeWithInsufficientStockChangesNothing() {
        f.loginAsAdmin();
        ProductView arroz = f.createProduct("Arroz", null, "10", ProductUnit.UN, "1");
        SaleView sale = f.saleService.startDraft();
        f.saleService.addItem(sale.id(), arroz.id(), dec("2"));

        DomainException e = assertThrows(DomainException.class, () -> f.saleService.complete(sale.id()));

        assertEquals("Estoque insuficiente para \"Arroz\". Disponível: 1.", e.getMessage());
        assertEquals(0, dec("1.000").compareTo(stockOf(arroz.id())));
        assertEquals(SaleStatus.DRAFT, f.sales.findById(sale.id()).orElseThrow().getStatus());
        assertTrue(movementsOf(arroz.id(), StockMovementType.SALE).isEmpty());
    }

    @Test
    void completeIsRejectedWhenAProductWasDeletedAfterBeingAdded() {
        f.loginAsAdmin();
        ProductView arroz = f.createProduct("Arroz", null, "10", ProductUnit.UN, "10");
        SaleView sale = f.saleService.startDraft();
        f.saleService.addItem(sale.id(), arroz.id(), dec("2"));
        f.productService.delete(arroz.id());

        DomainException e = assertThrows(DomainException.class, () -> f.saleService.complete(sale.id()));

        assertEquals("O produto \"Arroz\" foi excluído. Remova-o da venda para finalizar.", e.getMessage());
        assertEquals(SaleStatus.DRAFT, f.sales.findById(sale.id()).orElseThrow().getStatus());
        assertEquals(0, dec("10.000").compareTo(stockOf(arroz.id())));
    }

    @Test
    void completeOfEmptyDraftIsRejected() {
        f.loginAsAdmin();
        SaleView sale = f.saleService.startDraft();

        DomainException e = assertThrows(DomainException.class, () -> f.saleService.complete(sale.id()));
        assertEquals("A venda precisa ter pelo menos um item para ser finalizada.", e.getMessage());
    }

    @Test
    void deleteDraftRemovesItButCompletedSaleCannotBeDeleted() {
        f.loginAsAdmin();
        ProductView arroz = f.createProduct("Arroz", null, "10", ProductUnit.UN, "10");
        SaleView draft = f.saleService.startDraft();
        f.saleService.deleteDraft(draft.id());
        assertThrows(NotFoundException.class, () -> f.saleService.get(draft.id()));

        SaleView sale = f.saleService.startDraft();
        f.saleService.addItem(sale.id(), arroz.id(), dec("1"));
        f.saleService.complete(sale.id());
        DomainException e = assertThrows(DomainException.class, () -> f.saleService.deleteDraft(sale.id()));
        assertEquals("Esta venda não está mais em rascunho e não pode ser alterada.", e.getMessage());
    }

    @Test
    void operatorCannotCancelAndAdminCancelReturnsStock() {
        f.loginAsAdmin();
        ProductView arroz = f.createProduct("Arroz", null, "10", ProductUnit.UN, "10");
        SaleView sale = f.saleService.startDraft();
        f.saleService.addItem(sale.id(), arroz.id(), dec("3"));
        f.saleService.complete(sale.id());

        f.loginAsOperator();
        assertThrows(AccessDeniedException.class, () -> f.saleService.cancel(sale.id(), "Erro"));

        f.loginAsAdmin();
        SaleView cancelled = f.saleService.cancel(sale.id(), "Erro de digitação");

        assertEquals(SaleStatus.CANCELLED, cancelled.status());
        assertEquals(0, dec("10.000").compareTo(stockOf(arroz.id())));
        List<StockMovement> returned = movementsOf(arroz.id(), StockMovementType.RETURN);
        assertEquals(1, returned.size());
        assertEquals(0, dec("3").compareTo(returned.get(0).getQuantity()));
    }

    @Test
    void cancelRejectsDraftsAndBlankReasonWithoutTouchingStock() {
        f.loginAsAdmin();
        ProductView arroz = f.createProduct("Arroz", null, "10", ProductUnit.UN, "10");
        SaleView draft = f.saleService.startDraft();
        f.saleService.addItem(draft.id(), arroz.id(), dec("2"));

        DomainException onDraft = assertThrows(DomainException.class,
                () -> f.saleService.cancel(draft.id(), "Erro"));
        assertEquals("Somente vendas concluídas podem ser canceladas.", onDraft.getMessage());

        SaleView sale = f.saleService.startDraft();
        f.saleService.addItem(sale.id(), arroz.id(), dec("1"));
        f.saleService.complete(sale.id());
        assertThrows(DomainException.class, () -> f.saleService.cancel(sale.id(), "   "));

        assertEquals(0, dec("9.000").compareTo(stockOf(arroz.id())));
    }

    @Test
    void cancelReturnsStockEvenWhenTheProductWasDeletedLater() {
        f.loginAsAdmin();
        ProductView arroz = f.createProduct("Arroz", null, "10", ProductUnit.UN, "10");
        SaleView sale = f.saleService.startDraft();
        f.saleService.addItem(sale.id(), arroz.id(), dec("3"));
        f.saleService.complete(sale.id());
        f.productService.delete(arroz.id());

        f.saleService.cancel(sale.id(), "Devolvido");

        assertEquals(0, dec("10.000").compareTo(stockOf(arroz.id())));
    }

    @Test
    void searchHidesDraftsFiltersByStatusDateUserAndCustomer() {
        f.loginAsAdmin();
        ProductView arroz = f.createProduct("Arroz", null, "10", ProductUnit.UN, "100");
        CustomerView maria = f.createCustomer("Maria");
        SaleView draft = f.saleService.startDraft();
        SaleView day1 = f.saleService.startDraft();
        f.saleService.addItem(day1.id(), arroz.id(), dec("2"));
        f.saleService.assignCustomer(day1.id(), maria.id());
        f.saleService.complete(day1.id());
        f.clock.advance(Duration.ofDays(1));
        SaleView day2 = f.saleService.startDraft();
        f.saleService.addItem(day2.id(), arroz.id(), dec("1"));
        f.saleService.complete(day2.id());
        f.saleService.cancel(day2.id(), "Teste");

        List<Long> all = ids(f.saleService.search(null));
        assertEquals(List.of(day2.id(), day1.id()), all);
        assertFalse(all.contains(draft.id()));

        assertEquals(List.of(day2.id()),
                ids(f.saleService.search(new SaleSearch(null, null, SaleStatus.CANCELLED, null, null))));
        assertEquals(List.of(day1.id()),
                ids(f.saleService.search(new SaleSearch(LocalDate.of(2026, 1, 15), LocalDate.of(2026, 1, 15),
                        null, null, null))));
        assertEquals(List.of(day2.id(), day1.id()),
                ids(f.saleService.search(new SaleSearch(LocalDate.of(2026, 1, 15), LocalDate.of(2026, 1, 16),
                        null, null, null))));
        assertEquals(List.of(day1.id()),
                ids(f.saleService.search(new SaleSearch(null, null, null, maria.id(), null))));
        assertEquals(all, ids(f.saleService.search(new SaleSearch(null, null, null, null, f.loginAsAdmin().id()))));
        assertTrue(f.saleService.search(new SaleSearch(null, null, null, null, 999L)).isEmpty());
    }

    @Test
    void searchRejectsInvertedPeriod() {
        f.loginAsAdmin();

        DomainException e = assertThrows(DomainException.class, () -> f.saleService.search(
                new SaleSearch(LocalDate.of(2026, 1, 16), LocalDate.of(2026, 1, 15), null, null, null)));
        assertEquals("A data inicial deve ser anterior ou igual à data final.", e.getMessage());
    }

    @Test
    void getFillsNamesAndCancellationFields() {
        f.loginAsAdmin();
        ProductView arroz = f.createProduct("Arroz", null, "10", ProductUnit.UN, "10");
        CustomerView maria = f.createCustomer("Maria");
        SaleView sale = f.saleService.startDraft();
        f.saleService.addItem(sale.id(), arroz.id(), dec("1"));
        f.saleService.assignCustomer(sale.id(), maria.id());
        f.saleService.complete(sale.id());

        SaleView before = f.saleService.get(sale.id());
        assertEquals("Maria", before.customerName());
        assertEquals("Administrador", before.userName());
        assertEquals("Arroz", before.items().get(0).productName());
        assertNull(before.cancelledByName());

        f.saleService.cancel(sale.id(), "Erro");
        SaleView after = f.saleService.get(sale.id());
        assertEquals("Administrador", after.cancelledByName());
        assertEquals("Erro", after.cancelReason());
    }

    private static List<Long> ids(List<SaleSummaryView> views) {
        return views.stream().map(SaleSummaryView::id).toList();
    }
}
