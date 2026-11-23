package com.github.svitorz.sells.infrastructure.persistence;

import com.github.svitorz.sells.application.common.ConcurrentUpdateException;
import com.github.svitorz.sells.domain.product.Category;
import com.github.svitorz.sells.domain.product.Product;
import com.github.svitorz.sells.domain.product.ProductUnit;
import com.github.svitorz.sells.support.ServiceIntegrationTest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductPersistenceTest extends ServiceIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 15, 10, 0);

    @Test
    void roundTripKeepsFieldsAndBothCategories() {
        Category bebidas = categories.save(Category.create("Bebidas", null));
        Category frios = categories.save(Category.create("Frios", "Geladeira"));
        Product saved = products.save(Product.create("Suco", "7891000100103", new BigDecimal("5.50"),
                ProductUnit.UN, "Laranja", Set.of(bebidas.getId(), frios.getId()), NOW));

        Product loaded = products.findActiveById(saved.getId()).orElseThrow();

        assertEquals("Suco", loaded.getName());
        assertEquals("7891000100103", loaded.getBarcode());
        assertEquals(0, new BigDecimal("5.50").compareTo(loaded.getPrice()));
        assertEquals(ProductUnit.UN, loaded.getUnit());
        assertEquals("Laranja", loaded.getDescription());
        assertEquals(Set.of(bebidas.getId(), frios.getId()), loaded.getCategoryIds());
        assertEquals(NOW, loaded.getCreatedAt());
    }

    @Test
    void productWithoutDescriptionIsSaved() {
        Product saved = products.save(Product.create("Pão", null, new BigDecimal("1"), ProductUnit.UN, null,
                Set.of(), NOW));

        assertNull(products.findActiveById(saved.getId()).orElseThrow().getDescription());
    }

    @Test
    void searchMatchesNameIgnoringCaseAndBarcodeExactly() {
        products.save(Product.create("Suco de Laranja", "111", new BigDecimal("5"), ProductUnit.UN, null,
                Set.of(), NOW));

        assertEquals(1, products.search("SUCO", null, 50).size());
        assertEquals("Suco de Laranja", products.search("111", null, 50).get(0).getName());
    }

    @Test
    void searchByCategoryDoesNotDuplicateAProductWithTwoCategories() {
        Category bebidas = categories.save(Category.create("Bebidas", null));
        Category frios = categories.save(Category.create("Frios", null));
        products.save(Product.create("Suco", null, new BigDecimal("5"), ProductUnit.UN, null,
                Set.of(bebidas.getId(), frios.getId()), NOW));

        assertEquals(1, products.search(null, bebidas.getId(), 50).size());
    }

    @Test
    void deletedProductLeavesSearchButStaysInFindAllByIds() {
        Product saved = products.save(Product.create("Arroz", null, new BigDecimal("10"), ProductUnit.UN, null,
                Set.of(), NOW));
        Product deleted = products.findActiveById(saved.getId()).orElseThrow();
        deleted.delete(NOW);
        products.save(deleted);

        assertTrue(products.search(null, null, 50).isEmpty());
        assertTrue(products.findActiveById(saved.getId()).isEmpty());
        List<Product> found = products.findAllByIds(List.of(saved.getId()));
        assertEquals(1, found.size());
        assertTrue(found.get(0).isDeleted());
    }

    @Test
    void savingAnOutdatedCopyIsAConcurrentUpdate() {
        Product original = products.save(Product.create("Arroz", null, new BigDecimal("10"), ProductUnit.UN, null,
                Set.of(), NOW));
        Product first = products.findActiveById(original.getId()).orElseThrow();
        Product second = products.findActiveById(original.getId()).orElseThrow();

        first.update("Arroz tipo 1", null, new BigDecimal("11"), ProductUnit.UN, null, Set.of(), NOW);
        products.save(first);
        second.update("Arroz tipo 2", null, new BigDecimal("12"), ProductUnit.UN, null, Set.of(), NOW);

        assertThrows(ConcurrentUpdateException.class, () -> products.save(second));
    }

    @Test
    void findAllByIdsForUpdateReturnsRowsOrderedById() {
        Product a = products.save(Product.create("A", null, new BigDecimal("1"), ProductUnit.UN, null, Set.of(), NOW));
        Product b = products.save(Product.create("B", null, new BigDecimal("1"), ProductUnit.UN, null, Set.of(), NOW));
        Product c = products.save(Product.create("C", null, new BigDecimal("1"), ProductUnit.UN, null, Set.of(), NOW));

        List<Long> ids = products.findAllByIdsForUpdate(List.of(c.getId(), a.getId(), b.getId())).stream()
                .map(Product::getId)
                .toList();

        assertEquals(List.of(a.getId(), b.getId(), c.getId()), ids);
    }
}
