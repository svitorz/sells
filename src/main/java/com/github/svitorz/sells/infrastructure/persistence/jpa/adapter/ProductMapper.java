package com.github.svitorz.sells.infrastructure.persistence.jpa.adapter;

import com.github.svitorz.sells.domain.product.Product;
import com.github.svitorz.sells.infrastructure.persistence.jpa.entity.CategoryEntity;
import com.github.svitorz.sells.infrastructure.persistence.jpa.entity.ProductEntity;

import java.util.Set;
import java.util.stream.Collectors;

/** Categories are copied by the adapter, because they need the category repository. */
final class ProductMapper {

    private ProductMapper() {
    }

    static Product toDomain(ProductEntity entity) {
        Set<Long> categoryIds = entity.getCategories().stream()
                .map(CategoryEntity::getId)
                .collect(Collectors.toSet());
        return Product.restore(entity.getId(), entity.getName(), entity.getBarcode(), entity.getPrice(),
                entity.getUnit(), entity.getDescription(), entity.getStock(), categoryIds,
                entity.getCreatedAt(), entity.getUpdatedAt(), entity.getDeletedAt(), entity.getVersion());
    }

    static void copyToEntity(Product product, ProductEntity entity) {
        entity.setName(product.getName());
        entity.setBarcode(product.getBarcode());
        entity.setPrice(product.getPrice());
        entity.setUnit(product.getUnit());
        entity.setDescription(product.getDescription());
        entity.setStock(product.getStock());
        entity.setCreatedAt(product.getCreatedAt());
        entity.setUpdatedAt(product.getUpdatedAt());
        entity.setDeletedAt(product.getDeletedAt());
    }
}
