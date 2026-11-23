package com.github.svitorz.sells.application.catalog;

import com.github.svitorz.sells.application.common.NotFoundException;
import com.github.svitorz.sells.application.port.CategoryRepository;
import com.github.svitorz.sells.application.port.TransactionRunner;
import com.github.svitorz.sells.application.session.SessionContext;
import com.github.svitorz.sells.domain.product.Category;

import java.util.List;

public final class CategoryService {

    private final CategoryRepository categories;
    private final SessionContext session;
    private final TransactionRunner tx;

    public CategoryService(CategoryRepository categories, SessionContext session, TransactionRunner tx) {
        this.categories = categories;
        this.session = session;
        this.tx = tx;
    }

    public List<CategoryView> list() {
        session.require();
        return tx.inTransaction(() -> categories.findAll().stream().map(CategoryService::toView).toList());
    }

    public CategoryView create(CategoryCommand cmd) {
        session.requireAdmin();
        return tx.inTransaction(() -> toView(
                categories.save(Category.create(cmd.name(), cmd.description()))));
    }

    public CategoryView update(long id, CategoryCommand cmd) {
        session.requireAdmin();
        return tx.inTransaction(() -> {
            Category category = find(id);
            category.update(cmd.name(), cmd.description());
            return toView(categories.save(category));
        });
    }

    /** Used by the UI to warn before deleting. Counts only active products. */
    public long countProducts(long id) {
        session.requireAdmin();
        return tx.inTransaction(() -> categories.countActiveProducts(id));
    }

    /** Physical deletion (ADR-0006). */
    public void delete(long id) {
        session.requireAdmin();
        tx.inTransaction(() -> {
            find(id);
            categories.deleteById(id);
        });
    }

    private Category find(long id) {
        return categories.findById(id).orElseThrow(() -> new NotFoundException("Categoria não encontrada."));
    }

    static CategoryView toView(Category category) {
        return new CategoryView(category.getId(), category.getName(), category.getDescription());
    }
}
