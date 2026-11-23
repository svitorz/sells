package com.github.svitorz.sells.application.catalog;

import com.github.svitorz.sells.application.common.NotFoundException;
import com.github.svitorz.sells.application.port.CategoryRepository;
import com.github.svitorz.sells.application.port.ProductRepository;
import com.github.svitorz.sells.application.port.StockMovementRepository;
import com.github.svitorz.sells.application.port.TransactionRunner;
import com.github.svitorz.sells.application.session.AuthenticatedUser;
import com.github.svitorz.sells.application.session.SessionContext;
import com.github.svitorz.sells.domain.product.Category;
import com.github.svitorz.sells.domain.product.Product;
import com.github.svitorz.sells.domain.shared.DomainException;
import com.github.svitorz.sells.domain.stock.StockMovement;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class ProductService {

    private static final int SEARCH_LIMIT = 200;

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final StockMovementRepository movements;
    private final SessionContext session;
    private final TransactionRunner tx;
    private final Clock clock;

    public ProductService(ProductRepository products, CategoryRepository categories,
                          StockMovementRepository movements, SessionContext session,
                          TransactionRunner tx, Clock clock) {
        this.products = products;
        this.categories = categories;
        this.movements = movements;
        this.session = session;
        this.tx = tx;
        this.clock = clock;
    }

    public List<ProductView> search(String term, Long categoryId) {
        session.require();
        String cleanTerm = blankToNull(term);
        return tx.inTransaction(() -> toViews(products.search(cleanTerm, categoryId, SEARCH_LIMIT)));
    }

    public Optional<ProductView> findByBarcode(String barcode) {
        session.require();
        String clean = blankToNull(barcode);
        if (clean == null) {
            return Optional.empty();
        }
        return tx.inTransaction(() -> products.findActiveByBarcode(clean)
                .map(product -> toViews(List.of(product)).get(0)));
    }

    public ProductView get(long id) {
        session.require();
        return tx.inTransaction(() -> toViews(List.of(findActive(id))).get(0));
    }

    /**
     * Creates the product with stock zero, then records the initial stock as an ENTRY movement.
     * Everything runs in one transaction: a rejected initial stock saves nothing.
     */
    public ProductView create(CreateProductCommand cmd) {
        AuthenticatedUser actor = session.requireAdmin();
        return tx.inTransaction(() -> {
            LocalDateTime now = now();
            Set<Long> categoryIds = cmd.categoryIds() == null ? Set.of() : cmd.categoryIds();
            Product product = Product.create(cmd.name(), cmd.barcode(), cmd.price(), cmd.unit(),
                    cmd.description(), categoryIds, now);
            BigDecimal initialQuantity = cmd.initialStock() == null ? null : cmd.unit().stock(cmd.initialStock());

            checkCategories(categoryIds);
            checkBarcodeUnique(product.getBarcode(), null);

            Product saved = products.save(product);
            if (initialQuantity != null && initialQuantity.signum() > 0) {
                saved.applyStockChange(initialQuantity, now);
                saved = products.save(saved);
                movements.saveAll(List.of(StockMovement.entry(saved.getId(), initialQuantity,
                        actor.id(), "Estoque inicial", now)));
            }
            return toViews(List.of(saved)).get(0);
        });
    }

    /** Never changes the stock. */
    public ProductView update(long id, UpdateProductCommand cmd) {
        session.requireAdmin();
        return tx.inTransaction(() -> {
            Product product = findActive(id);
            Set<Long> categoryIds = cmd.categoryIds() == null ? Set.of() : cmd.categoryIds();
            checkCategories(categoryIds);
            product.update(cmd.name(), cmd.barcode(), cmd.price(), cmd.unit(), cmd.description(),
                    categoryIds, now());
            checkBarcodeUnique(product.getBarcode(), id);
            return toViews(List.of(products.save(product))).get(0);
        });
    }

    public void delete(long id) {
        session.requireAdmin();
        tx.inTransaction(() -> {
            Product product = findActive(id);
            product.delete(now());
            products.save(product);
        });
    }

    private Product findActive(long id) {
        return products.findActiveById(id).orElseThrow(() -> new NotFoundException("Produto não encontrado."));
    }

    private void checkCategories(Set<Long> categoryIds) {
        if (!categoryIds.isEmpty() && categories.findAllByIds(categoryIds).size() != categoryIds.size()) {
            throw new NotFoundException("Categoria não encontrada.");
        }
    }

    private void checkBarcodeUnique(String barcode, Long excludingId) {
        if (barcode != null && products.existsActiveByBarcode(barcode, excludingId)) {
            throw new DomainException("Já existe um produto ativo com este código de barras.");
        }
    }

    /** Builds the views with a single category lookup for the whole list. */
    private List<ProductView> toViews(List<Product> list) {
        Set<Long> allCategoryIds = new HashSet<>();
        list.forEach(product -> allCategoryIds.addAll(product.getCategoryIds()));
        Map<Long, CategoryView> byId = new HashMap<>();
        for (Category category : categories.findAllByIds(allCategoryIds)) {
            byId.put(category.getId(), new CategoryView(category.getId(), category.getName(),
                    category.getDescription()));
        }
        return list.stream()
                .map(product -> new ProductView(product.getId(), product.getName(), product.getBarcode(),
                        product.getPrice(), product.getUnit(), product.getDescription(), product.getStock(),
                        product.getCategoryIds().stream()
                                .map(byId::get)
                                .filter(Objects::nonNull)
                                .sorted(Comparator.comparing(CategoryView::name))
                                .toList()))
                .toList();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
