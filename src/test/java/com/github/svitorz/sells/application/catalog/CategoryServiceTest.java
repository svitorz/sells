package com.github.svitorz.sells.application.catalog;

import com.github.svitorz.sells.application.common.AccessDeniedException;
import com.github.svitorz.sells.application.common.NotFoundException;
import com.github.svitorz.sells.application.fakes.ApplicationTestFixture;
import com.github.svitorz.sells.domain.product.ProductUnit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CategoryServiceTest {

    private final ApplicationTestFixture f = new ApplicationTestFixture();

    @Test
    void crudWorksForAdmin() {
        f.loginAsAdmin();

        CategoryView created = f.categoryService.create(new CategoryCommand("Bebidas", "Líquidos"));
        CategoryView updated = f.categoryService.update(created.id(), new CategoryCommand("Bebidas frias", null));

        assertEquals(List.of(updated), f.categoryService.list());

        f.categoryService.delete(created.id());
        assertEquals(List.of(), f.categoryService.list());
        assertThrows(NotFoundException.class, () -> f.categoryService.update(created.id(),
                new CategoryCommand("Qualquer", null)));
    }

    @Test
    void countProductsCountsOnlyActiveProducts() {
        f.loginAsAdmin();
        CategoryView bebidas = f.categoryService.create(new CategoryCommand("Bebidas", null));
        ProductView suco = f.productService.create(new CreateProductCommand("Suco", null, new BigDecimal("5"),
                ProductUnit.UN, null, null, Set.of(bebidas.id())));
        f.productService.create(new CreateProductCommand("Refri", null, new BigDecimal("6"),
                ProductUnit.UN, null, null, Set.of(bebidas.id())));
        f.productService.delete(suco.id());

        assertEquals(1, f.categoryService.countProducts(bebidas.id()));
    }

    @Test
    void operatorCannotCreateCategory() {
        f.loginAsOperator();

        assertThrows(AccessDeniedException.class,
                () -> f.categoryService.create(new CategoryCommand("Bebidas", null)));
    }
}
