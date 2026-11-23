package com.github.svitorz.sells.infrastructure.persistence;

import com.github.svitorz.sells.application.sale.SaleSearch;
import com.github.svitorz.sells.domain.product.Product;
import com.github.svitorz.sells.domain.product.ProductUnit;
import com.github.svitorz.sells.domain.sale.Sale;
import com.github.svitorz.sells.domain.sale.SaleItem;
import com.github.svitorz.sells.domain.sale.SaleStatus;
import com.github.svitorz.sells.domain.user.Role;
import com.github.svitorz.sells.domain.user.User;
import com.github.svitorz.sells.support.ServiceIntegrationTest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SalePersistenceTest extends ServiceIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 15, 10, 0);

    private User user() {
        return users.save(User.create("Ana", "ana@example.com", "ana", "h", Role.USER, null));
    }

    private Product product(String name) {
        return products.save(Product.create(name, null, new BigDecimal("10"), ProductUnit.UN, null, Set.of(), NOW));
    }

    @Test
    void roundTripKeepsTwoItemsWithUnitAndTotals() {
        User ana = user();
        Product arroz = product("Arroz");
        Product feijao = product("Feijão");
        Sale sale = Sale.startDraft(ana.getId(), NOW);
        sale.addItem(arroz, new BigDecimal("2"), NOW);
        sale.addItem(feijao, new BigDecimal("1"), NOW);

        Sale saved = sales.save(sale);
        Sale loaded = sales.findById(saved.getId()).orElseThrow();

        assertEquals(2, loaded.getItems().size());
        assertEquals(0, new BigDecimal("30.00").compareTo(loaded.getSubtotal()));
        SaleItem first = loaded.getItems().get(0);
        assertEquals(arroz.getId().longValue(), first.getProductId());
        assertEquals(ProductUnit.UN, first.getUnit());
    }

    @Test
    void saveSynchronizesItemsByProductWithoutRecreatingThem() {
        User ana = user();
        Product arroz = product("Arroz");
        Product feijao = product("Feijão");
        Product cafe = product("Café");
        Sale sale = Sale.startDraft(ana.getId(), NOW);
        sale.addItem(arroz, new BigDecimal("2"), NOW);
        sale.addItem(feijao, new BigDecimal("1"), NOW);
        Sale saved = sales.save(sale);

        Sale editing = sales.findById(saved.getId()).orElseThrow();
        editing.changeItemQuantity(feijao.getId(), new BigDecimal("4"), NOW);
        editing.removeItem(arroz.getId(), NOW);
        editing.addItem(cafe, new BigDecimal("1"), NOW);
        sales.save(editing);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select product_id, quantity from sale_items where sale_id = ? order by product_id", saved.getId());
        assertEquals(2, rows.size());
        assertEquals(feijao.getId(), rows.get(0).get("product_id"));
        assertEquals(0, new BigDecimal("4").compareTo((BigDecimal) rows.get(0).get("quantity")));
        assertEquals(cafe.getId(), rows.get(1).get("product_id"));
        assertEquals(0, new BigDecimal("1").compareTo((BigDecimal) rows.get(1).get("quantity")));
    }

    @Test
    void deleteByIdRemovesItems() {
        User ana = user();
        Product arroz = product("Arroz");
        Sale sale = Sale.startDraft(ana.getId(), NOW);
        sale.addItem(arroz, new BigDecimal("1"), NOW);
        Sale saved = sales.save(sale);

        sales.deleteById(saved.getId());

        Integer remaining = jdbcTemplate.queryForObject(
                "select count(*) from sale_items where sale_id = ?", Integer.class, saved.getId());
        assertEquals(0, remaining);
        assertTrue(sales.findById(saved.getId()).isEmpty());
    }

    @Test
    void findLatestDraftReturnsTheHighestDraftOfTheUser() {
        User ana = user();
        sales.save(Sale.startDraft(ana.getId(), NOW));
        Sale latest = sales.save(Sale.startDraft(ana.getId(), NOW));

        assertEquals(latest.getId(), sales.findLatestDraftByUser(ana.getId()).orElseThrow().getId());
    }

    @Test
    void searchFiltersByStatusDateUserAndCustomer() {
        User ana = user();
        Product arroz = product("Arroz");
        Sale day1 = Sale.startDraft(ana.getId(), NOW);
        day1.addItem(arroz, new BigDecimal("1"), NOW);
        day1.complete(NOW);
        sales.save(day1);
        LocalDateTime nextDay = NOW.plusDays(1);
        Sale day2 = Sale.startDraft(ana.getId(), nextDay);
        day2.addItem(arroz, new BigDecimal("1"), nextDay);
        day2.complete(nextDay);
        sales.save(day2);
        sales.save(Sale.startDraft(ana.getId(), NOW));

        assertEquals(2, sales.search(new SaleSearch(null, null, null, null, null), 50).size());
        assertEquals(1, sales.search(new SaleSearch(LocalDate.of(2026, 1, 15), LocalDate.of(2026, 1, 15),
                null, null, null), 50).size());
        assertEquals(2, sales.search(new SaleSearch(LocalDate.of(2026, 1, 15), LocalDate.of(2026, 1, 16),
                null, null, null), 50).size());
        assertEquals(2, sales.search(new SaleSearch(null, null, null, null, ana.getId()), 50).size());
        assertTrue(sales.search(new SaleSearch(null, null, SaleStatus.CANCELLED, null, null), 50).isEmpty());
        assertFalse(sales.search(new SaleSearch(null, null, null, null, null), 50).stream()
                .anyMatch(s -> s.getStatus() == SaleStatus.DRAFT));
    }

    @Test
    void completedSaleKeepsItsItemWhenTheProductIsDeletedLater() {
        User ana = user();
        Product arroz = product("Arroz");
        Sale sale = Sale.startDraft(ana.getId(), NOW);
        sale.addItem(arroz, new BigDecimal("2"), NOW);
        sale.complete(NOW);
        Sale saved = sales.save(sale);
        Product deleted = products.findActiveById(arroz.getId()).orElseThrow();
        deleted.delete(NOW);
        products.save(deleted);

        Sale loaded = sales.findById(saved.getId()).orElseThrow();

        assertEquals(1, loaded.getItems().size());
        assertEquals(arroz.getId().longValue(), loaded.getItems().get(0).getProductId());
        assertEquals(ProductUnit.UN, loaded.getItems().get(0).getUnit());
    }
}
