package com.github.svitorz.sells.application;

import com.github.svitorz.sells.application.catalog.CreateProductCommand;
import com.github.svitorz.sells.application.catalog.ProductView;
import com.github.svitorz.sells.application.sale.SaleView;
import com.github.svitorz.sells.domain.product.ProductUnit;
import com.github.svitorz.sells.domain.sale.SaleStatus;
import com.github.svitorz.sells.domain.shared.DomainException;
import com.github.svitorz.sells.support.ServiceIntegrationTest;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Uses only the services, as the UI will. Runs against the real database. */
class SaleFlowIntegrationTest extends ServiceIntegrationTest {

    private ProductView product(String stock) {
        loginAsAdmin();
        return productService.create(new CreateProductCommand("Arroz", null, new BigDecimal("10.00"),
                ProductUnit.UN, null, stock == null ? null : new BigDecimal(stock), Set.of()));
    }

    private BigDecimal stockOf(long productId) {
        return jdbcTemplate.queryForObject("select stock from products where id = ?", BigDecimal.class, productId);
    }

    private int countMovements(String type, long productId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from stock_movements where type = ? and product_id = ?", Integer.class,
                type, productId);
        return count;
    }

    @Test
    void completingASaleReducesStockAndWritesOneSaleMovement() {
        ProductView arroz = product("10");
        SaleView sale = saleService.startDraft();
        saleService.addItem(sale.id(), arroz.id(), new BigDecimal("3"));

        saleService.complete(sale.id());

        assertEquals(0, new BigDecimal("7.000").compareTo(stockOf(arroz.id())));
        assertEquals(1, countMovements("SALE", arroz.id()));
        BigDecimal quantity = jdbcTemplate.queryForObject(
                "select quantity from stock_movements where type = 'SALE' and product_id = ?", BigDecimal.class,
                arroz.id());
        assertEquals(0, new BigDecimal("-3.000").compareTo(quantity));
    }

    @Test
    void aFailedCompletionChangesNothing() {
        ProductView arroz = product("1");
        SaleView sale = saleService.startDraft();
        saleService.addItem(sale.id(), arroz.id(), new BigDecimal("2"));

        assertThrows(DomainException.class, () -> saleService.complete(sale.id()));

        assertEquals(0, new BigDecimal("1.000").compareTo(stockOf(arroz.id())));
        assertEquals(SaleStatus.DRAFT, saleService.get(sale.id()).status());
        assertEquals(0, countMovements("SALE", arroz.id()));
    }

    @Test
    void cancellingReturnsStockAndWritesAReturnMovement() {
        ProductView arroz = product("10");
        SaleView sale = saleService.startDraft();
        saleService.addItem(sale.id(), arroz.id(), new BigDecimal("3"));
        saleService.complete(sale.id());

        saleService.cancel(sale.id(), "Erro de digitação");

        assertEquals(0, new BigDecimal("10.000").compareTo(stockOf(arroz.id())));
        assertEquals(1, countMovements("RETURN", arroz.id()));
    }

    @RepeatedTest(5)
    void twoSimultaneousSalesOfTheLastUnitOnlyOneSucceeds() throws Exception {
        ProductView arroz = product("1");
        SaleView first = saleService.startDraft();
        saleService.addItem(first.id(), arroz.id(), new BigDecimal("1"));
        SaleView second = saleService.startDraft();
        saleService.addItem(second.id(), arroz.id(), new BigDecimal("1"));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<SaleView>> results = List.of(first.id(), second.id()).stream()
                    .map(saleId -> pool.submit(() -> {
                        start.await();
                        return saleService.complete(saleId);
                    }))
                    .toList();
            start.countDown();

            int succeeded = 0;
            int rejected = 0;
            for (Future<SaleView> result : results) {
                try {
                    result.get(30, TimeUnit.SECONDS);
                    succeeded++;
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof DomainException rule
                            && rule.getMessage().startsWith("Estoque insuficiente")) {
                        rejected++;
                    } else {
                        throw e;
                    }
                }
            }

            assertEquals(1, succeeded);
            assertEquals(1, rejected);
            assertEquals(0, new BigDecimal("0.000").compareTo(stockOf(arroz.id())));
            assertEquals(1, countMovements("SALE", arroz.id()));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void creatingAProductWithInitialStockWritesAnEntryMovement() {
        ProductView arroz = product("5");

        assertEquals(0, new BigDecimal("5.000").compareTo(stockOf(arroz.id())));
        assertEquals(1, countMovements("ENTRY", arroz.id()));
        assertTrue(stockOf(arroz.id()).signum() > 0);
    }
}
