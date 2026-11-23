package com.github.svitorz.sells.application.stock;

import com.github.svitorz.sells.application.catalog.ProductView;
import com.github.svitorz.sells.application.common.AccessDeniedException;
import com.github.svitorz.sells.application.common.NotFoundException;
import com.github.svitorz.sells.application.fakes.ApplicationTestFixture;
import com.github.svitorz.sells.application.sale.SaleView;
import com.github.svitorz.sells.domain.product.ProductUnit;
import com.github.svitorz.sells.domain.shared.DomainException;
import com.github.svitorz.sells.domain.stock.StockMovementType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StockServiceTest {

    private final ApplicationTestFixture f = new ApplicationTestFixture();

    private static BigDecimal dec(String value) {
        return new BigDecimal(value);
    }

    private BigDecimal stockOf(long productId) {
        return f.products.findAllByIds(List.of(productId)).get(0).getStock();
    }

    @Test
    void registerEntryIncreasesStockAndRecordsEntry() {
        f.loginAsAdmin();
        ProductView arroz = f.createProduct("Arroz", null, "10", ProductUnit.UN, null);

        StockMovementView movement = f.stockService.registerEntry(arroz.id(), dec("5"), "Compra");

        assertEquals(0, dec("5.000").compareTo(stockOf(arroz.id())));
        assertEquals(StockMovementType.ENTRY, movement.type());
        assertEquals("Compra", movement.notes());
    }

    @Test
    void registerEntryRejectsZeroAndDeletedProducts() {
        f.loginAsAdmin();
        ProductView arroz = f.createProduct("Arroz", null, "10", ProductUnit.UN, null);

        DomainException zero = assertThrows(DomainException.class,
                () -> f.stockService.registerEntry(arroz.id(), BigDecimal.ZERO, null));
        assertEquals("A quantidade deve ser maior que zero.", zero.getMessage());

        f.productService.delete(arroz.id());
        assertThrows(NotFoundException.class, () -> f.stockService.registerEntry(arroz.id(), dec("1"), null));
    }

    @Test
    void negativeAdjustmentDecreasesStockButNeedsAReasonAndCannotGoBelowZero() {
        f.loginAsAdmin();
        ProductView arroz = f.createProduct("Arroz", null, "10", ProductUnit.UN, "5");

        f.stockService.registerAdjustment(arroz.id(), dec("-2"), "Quebra");
        assertEquals(0, dec("3.000").compareTo(stockOf(arroz.id())));

        DomainException noReason = assertThrows(DomainException.class,
                () -> f.stockService.registerAdjustment(arroz.id(), dec("-1"), null));
        assertEquals("O campo Motivo do ajuste é obrigatório.", noReason.getMessage());
        assertEquals(0, dec("3.000").compareTo(stockOf(arroz.id())));

        DomainException negative = assertThrows(DomainException.class,
                () -> f.stockService.registerAdjustment(arroz.id(), dec("-10"), "Erro"));
        assertEquals("Estoque insuficiente para \"Arroz\". Disponível: 3.", negative.getMessage());
    }

    @Test
    void unitProductRejectsFractionalEntry() {
        f.loginAsAdmin();
        ProductView arroz = f.createProduct("Arroz", null, "10", ProductUnit.UN, null);

        assertThrows(DomainException.class, () -> f.stockService.registerEntry(arroz.id(), dec("1.5"), null));
    }

    @Test
    void listMovementsIsChronologicalWithUserNamesAndIncludesSales() {
        f.loginAsAdmin();
        ProductView arroz = f.createProduct("Arroz", null, "10", ProductUnit.UN, "10");
        f.clock.advance(Duration.ofMinutes(1));
        SaleView sale = f.saleService.startDraft();
        f.saleService.addItem(sale.id(), arroz.id(), dec("3"));
        f.clock.advance(Duration.ofMinutes(1));
        f.saleService.complete(sale.id());
        f.clock.advance(Duration.ofMinutes(1));
        f.stockService.registerEntry(arroz.id(), dec("2"), "Reposição");

        List<StockMovementView> history = f.stockService.listMovements(arroz.id());

        assertEquals(List.of(StockMovementType.ENTRY, StockMovementType.SALE, StockMovementType.ENTRY),
                history.stream().map(StockMovementView::type).toList());
        assertEquals("Administrador", history.get(0).userName());
        assertEquals(sale.id(), history.get(1).saleId());
    }

    @Test
    void operatorGetsAccessDeniedOnAllStockMethods() {
        ProductView arroz = f.createProduct("Arroz", null, "10", ProductUnit.UN, "5");
        f.loginAsOperator();

        assertThrows(AccessDeniedException.class, () -> f.stockService.registerEntry(arroz.id(), dec("1"), null));
        assertThrows(AccessDeniedException.class,
                () -> f.stockService.registerAdjustment(arroz.id(), dec("1"), "Teste"));
        assertThrows(AccessDeniedException.class, () -> f.stockService.listMovements(arroz.id()));
    }
}
