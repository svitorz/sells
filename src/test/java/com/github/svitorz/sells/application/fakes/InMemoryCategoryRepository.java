package com.github.svitorz.sells.application.fakes;

import com.github.svitorz.sells.application.port.CategoryRepository;
import com.github.svitorz.sells.domain.product.Category;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemoryCategoryRepository implements CategoryRepository {

    private final Map<Long, Category> store = new LinkedHashMap<>();
    private final AtomicLong sequence = new AtomicLong(1);
    private final InMemoryProductRepository products;

    public InMemoryCategoryRepository(InMemoryProductRepository products) {
        this.products = products;
    }

    @Override
    public Optional<Category> findById(long id) {
        return Optional.ofNullable(store.get(id)).map(InMemoryCategoryRepository::copy);
    }

    @Override
    public List<Category> findAll() {
        return store.values().stream()
                .sorted(Comparator.comparing(Category::getName))
                .map(InMemoryCategoryRepository::copy)
                .toList();
    }

    @Override
    public List<Category> findAllByIds(Collection<Long> ids) {
        return store.values().stream()
                .filter(category -> ids.contains(category.getId()))
                .map(InMemoryCategoryRepository::copy)
                .toList();
    }

    @Override
    public long countActiveProducts(long categoryId) {
        return products.countActiveInCategory(categoryId);
    }

    @Override
    public Category save(Category category) {
        long id = category.getId() == null ? sequence.getAndIncrement() : category.getId();
        Category stored = Category.restore(id, category.getName(), category.getDescription());
        store.put(id, stored);
        return copy(stored);
    }

    @Override
    public void deleteById(long id) {
        store.remove(id);
    }

    private static Category copy(Category category) {
        return Category.restore(category.getId(), category.getName(), category.getDescription());
    }
}
