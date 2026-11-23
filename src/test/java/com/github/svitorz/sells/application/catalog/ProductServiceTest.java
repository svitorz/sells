package com.github.svitorz.sells.application.catalog;

import com.github.svitorz.sells.application.common.AccessDeniedException;
import com.github.svitorz.sells.application.common.NotFoundException;
import com.github.svitorz.sells.application.fakes.ApplicationTestFixture;
import com.github.svitorz.sells.domain.product.ProductUnit;
import com.github.svitorz.sells.domain.shared.DomainException;
import com.github.svitorz.sells.domain.stock.StockMovement;
import com.github.svitorz.sells.domain.stock.StockMovementType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductServiceTest {

    private final ApplicationTestFixture f = new ApplicationTestFixture();

    private static CreateProductCommand product(String name, String barcode, BigDecimal initialStock,
                                                Set<Long> categoryIds) {
        return new CreateProductCommand(name, barcode, new BigDecimal("10.00"), ProductUnit.UN, null,
                initialStock, categoryIds);
    }

    private static CreateProductCommand product(String name, BigDecimal initialStock) {
        return product(name, null, initialStock, Set.of());
    }

    @Test
    void initialStockBecomesStockAndOneEntryMovement() {
        f.loginAsAdmin();

        ProductView created = f.productService.create(product("Arroz", new BigDecimal("5")));

        assertEquals(0, new BigDecimal("5.000").compareTo(created.stock()));
        List<StockMovement> history = f.movements.findByProductId(created.id());
        assertEquals(1, history.size());
        assertEquals(StockMovementType.ENTRY, history.get(0).getType());
        assertEquals(0, new BigDecimal("5.000").compareTo(history.get(0).getQuantity()));
        assertEquals("Estoque inicial", history.get(0).getNotes());
    }

    @Test
    void zeroOrMissingInitialStockCreatesNoMovement() {
        f.loginAsAdmin();

        ProductView zero = f.productService.create(product("Arroz", BigDecimal.ZERO));
        ProductView none = f.productService.create(product("Feijão", null));

        assertTrue(f.movements.findByProductId(zero.id()).isEmpty());
        assertTrue(f.movements.findByProductId(none.id()).isEmpty());
    }

    @Test
    void negativeInitialStockIsRejectedAndNothingIsSaved() {
        f.loginAsAdmin();

        assertThrows(DomainException.class,
                () -> f.productService.create(product("Arroz", new BigDecimal("-1"))));

        assertTrue(f.productService.search(null, null).isEmpty());
    }

    @Test
    void duplicateActiveBarcodeIsRejectedUntilTheFirstIsDeleted() {
        f.loginAsAdmin();
        ProductView first = f.productService.create(product("Arroz", "7891000100103", null, Set.of()));

        DomainException duplicate = assertThrows(DomainException.class,
                () -> f.productService.create(product("Outro", "7891000100103", null, Set.of())));
        assertEquals("Já existe um produto ativo com este código de barras.", duplicate.getMessage());

        f.productService.delete(first.id());
        assertEquals("Novo", f.productService.create(product("Novo", "7891000100103", null, Set.of())).name());
    }

    @Test
    void unknownCategoryIsNotFound() {
        f.loginAsAdmin();

        assertThrows(NotFoundException.class,
                () -> f.productService.create(product("Arroz", null, null, Set.of(999L))));
    }

    @Test
    void searchMatchesNamePartCaseInsensitivelyBarcodeAndCategoryAndHidesDeleted() {
        f.loginAsAdmin();
        CategoryView graos = f.categoryService.create(new CategoryCommand("Grãos", null));
        ProductView arroz = f.productService.create(product("Arroz", "789100", null, Set.of(graos.id())));
        f.productService.create(product("Feijão", null, null, Set.of()));

        assertEquals(List.of("Arroz"), names(f.productService.search("arr", null)));
        assertEquals(List.of("Arroz"), names(f.productService.search("789100", null)));
        assertEquals(List.of("Arroz"), names(f.productService.search(null, graos.id())));

        f.productService.delete(arroz.id());
        assertTrue(f.productService.search("arr", null).isEmpty());
    }

    @Test
    void updateNeverChangesStock() {
        f.loginAsAdmin();
        ProductView arroz = f.productService.create(product("Arroz", new BigDecimal("5")));

        ProductView updated = f.productService.update(arroz.id(),
                new UpdateProductCommand("Arroz tipo 2", null, new BigDecimal("12.00"), ProductUnit.UN, null,
                        Set.of()));

        assertEquals("Arroz tipo 2", updated.name());
        assertEquals(0, new BigDecimal("5.000").compareTo(updated.stock()));
    }

    @Test
    void operatorCanSearchButCannotCreate() {
        f.loginAsAdmin();
        f.productService.create(product("Arroz", null));
        f.loginAsOperator();

        assertEquals(List.of("Arroz"), names(f.productService.search(null, null)));
        assertThrows(AccessDeniedException.class,
                () -> f.productService.create(product("Feijão", null)));
        assertFalse(f.productService.search("zzz", null).stream().findAny().isPresent());
    }

    private static List<String> names(List<ProductView> views) {
        return views.stream().map(ProductView::name).toList();
    }
}
