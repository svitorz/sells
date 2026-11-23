package com.github.svitorz.sells.application.port;

import com.github.svitorz.sells.domain.product.Category;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CategoryRepository {

    Optional<Category> findById(long id);

    /** Ordered by name. */
    List<Category> findAll();

    List<Category> findAllByIds(Collection<Long> ids);

    /** Products with deleted_at null. */
    long countActiveProducts(long categoryId);

    Category save(Category category);

    void deleteById(long id);
}
