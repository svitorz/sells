package com.github.svitorz.sells.infrastructure.persistence;

import com.github.svitorz.sells.domain.product.Product;
import com.github.svitorz.sells.domain.product.ProductUnit;
import com.github.svitorz.sells.domain.stock.StockMovement;
import com.github.svitorz.sells.domain.user.Role;
import com.github.svitorz.sells.domain.user.User;
import com.github.svitorz.sells.support.ServiceIntegrationTest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StockMovementPersistenceTest extends ServiceIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 15, 10, 0);

    @Test
    void saveAllReturnsIdsInTheSameOrder() {
        Product arroz = products.save(Product.create("Arroz", null, new BigDecimal("10"), ProductUnit.UN, null,
                Set.of(), NOW));
        User ana = users.save(User.create("Ana", "ana@example.com", "ana", "h", Role.USER, null));

        List<StockMovement> saved = movements.saveAll(List.of(
                StockMovement.entry(arroz.getId(), new BigDecimal("2"), ana.getId(), null, NOW),
                StockMovement.entry(arroz.getId(), new BigDecimal("3"), ana.getId(), "Reposição", NOW)));

        assertEquals(2, saved.size());
        assertNotNull(saved.get(0).getId());
        assertNotNull(saved.get(1).getId());
        assertEquals(0, new BigDecimal("2").compareTo(saved.get(0).getQuantity()));
        assertEquals(0, new BigDecimal("3").compareTo(saved.get(1).getQuantity()));
    }

    @Test
    void findByProductIdIsChronological() {
        Product arroz = products.save(Product.create("Arroz", null, new BigDecimal("10"), ProductUnit.UN, null,
                Set.of(), NOW));
        User ana = users.save(User.create("Ana", "ana@example.com", "ana", "h", Role.USER, null));
        movements.saveAll(List.of(
                StockMovement.entry(arroz.getId(), new BigDecimal("1"), ana.getId(), null, NOW.plusHours(2)),
                StockMovement.entry(arroz.getId(), new BigDecimal("2"), ana.getId(), null, NOW)));

        List<StockMovement> history = movements.findByProductId(arroz.getId());

        assertEquals(0, new BigDecimal("2").compareTo(history.get(0).getQuantity()));
        assertEquals(0, new BigDecimal("1").compareTo(history.get(1).getQuantity()));
    }
}
