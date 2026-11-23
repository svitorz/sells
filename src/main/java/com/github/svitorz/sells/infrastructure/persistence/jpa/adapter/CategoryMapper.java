package com.github.svitorz.sells.infrastructure.persistence.jpa.adapter;

import com.github.svitorz.sells.domain.product.Category;
import com.github.svitorz.sells.infrastructure.persistence.jpa.entity.CategoryEntity;

final class CategoryMapper {

    private CategoryMapper() {
    }

    static Category toDomain(CategoryEntity entity) {
        return Category.restore(entity.getId(), entity.getName(), entity.getDescription());
    }

    static void copyToEntity(Category category, CategoryEntity entity) {
        entity.setName(category.getName());
        entity.setDescription(category.getDescription());
    }
}
