package com.github.svitorz.sells.application.fakes;

import com.github.svitorz.sells.application.port.ProductRepository;
import com.github.svitorz.sells.domain.product.Product;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/** Stores copies, like a database does. Version grows by one on each save, as JPA does. */
public final class InMemoryProductRepository implements ProductRepository {

    private final Map<Long, Product> store = new LinkedHashMap<>();
    private final AtomicLong sequence = new AtomicLong(1);

    @Override
    public Optional<Product> findActiveById(long id) {
        return Optional.ofNullable(store.get(id))
                .filter(product -> !product.isDeleted())
                .map(InMemoryProductRepository::copy);
    }

    @Override
    public Optional<Product> findActiveByBarcode(String barcode) {
        return store.values().stream()
                .filter(product -> !product.isDeleted())
                .filter(product -> barcode.equals(product.getBarcode()))
                .findFirst()
                .map(InMemoryProductRepository::copy);
    }

    @Override
    public List<Product> search(String term, Long categoryId, int limit) {
        String lowerTerm = term == null ? null : term.toLowerCase(Locale.ROOT);
        return store.values().stream()
                .filter(product -> !product.isDeleted())
                .filter(product -> term == null
                        || product.getName().toLowerCase(Locale.ROOT).contains(lowerTerm)
                        || term.equals(product.getBarcode()))
                .filter(product -> categoryId == null || product.getCategoryIds().contains(categoryId))
                .sorted(Comparator.comparing(Product::getName))
                .limit(limit)
                .map(InMemoryProductRepository::copy)
                .toList();
    }

    @Override
    public boolean existsActiveByBarcode(String barcode, Long excludingId) {
        return store.values().stream()
                .anyMatch(product -> !product.isDeleted()
                        && barcode.equals(product.getBarcode())
                        && !Objects.equals(product.getId(), excludingId));
    }

    @Override
    public List<Product> findAllByIds(Collection<Long> ids) {
        return store.values().stream()
                .filter(product -> ids.contains(product.getId()))
                .sorted(Comparator.comparing(Product::getId))
                .map(InMemoryProductRepository::copy)
                .toList();
    }

    @Override
    public List<Product> findAllByIdsForUpdate(Collection<Long> ids) {
        return findAllByIds(ids);
    }

    @Override
    public Product save(Product product) {
        long id = product.getId() == null ? sequence.getAndIncrement() : product.getId();
        Long version = product.getVersion() == null ? 0L : product.getVersion() + 1;
        Product stored = Product.restore(id, product.getName(), product.getBarcode(), product.getPrice(),
                product.getUnit(), product.getDescription(), product.getStock(), product.getCategoryIds(),
                product.getCreatedAt(), product.getUpdatedAt(), product.getDeletedAt(), version);
        store.put(id, stored);
        return copy(stored);
    }

    /** Number of active products in the category. Used by the category repository. */
    long countActiveInCategory(long categoryId) {
        return store.values().stream()
                .filter(product -> !product.isDeleted() && product.getCategoryIds().contains(categoryId))
                .count();
    }

    private static Product copy(Product product) {
        return Product.restore(product.getId(), product.getName(), product.getBarcode(), product.getPrice(),
                product.getUnit(), product.getDescription(), product.getStock(), product.getCategoryIds(),
                product.getCreatedAt(), product.getUpdatedAt(), product.getDeletedAt(), product.getVersion());
    }
}
