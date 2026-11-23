package com.github.svitorz.sells.infrastructure.persistence.jpa.adapter;

import com.github.svitorz.sells.application.common.ConcurrentUpdateException;
import com.github.svitorz.sells.application.port.ProductRepository;
import com.github.svitorz.sells.domain.product.Product;
import com.github.svitorz.sells.infrastructure.persistence.jpa.entity.CategoryEntity;
import com.github.svitorz.sells.infrastructure.persistence.jpa.entity.ProductEntity;
import com.github.svitorz.sells.infrastructure.persistence.jpa.repository.CategoryJpaRepository;
import com.github.svitorz.sells.infrastructure.persistence.jpa.repository.ProductJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@Repository
@Transactional
public class ProductRepositoryAdapter implements ProductRepository {

    private static final String CONFLICT_MESSAGE =
            "Este registro foi alterado por outra operação. Feche e abra a tela novamente.";

    private final ProductJpaRepository jpa;
    private final CategoryJpaRepository categories;

    public ProductRepositoryAdapter(ProductJpaRepository jpa, CategoryJpaRepository categories) {
        this.jpa = jpa;
        this.categories = categories;
    }

    @Override
    public Optional<Product> findActiveById(long id) {
        return jpa.findByIdAndDeletedAtIsNull(id).map(ProductMapper::toDomain);
    }

    @Override
    public Optional<Product> findActiveByBarcode(String barcode) {
        return jpa.findByBarcodeAndDeletedAtIsNull(barcode).map(ProductMapper::toDomain);
    }

    @Override
    public List<Product> search(String term, Long categoryId, int limit) {
        Specification<ProductEntity> spec = (root, query, cb) -> cb.isNull(root.get("deletedAt"));
        if (term != null && !term.isBlank()) {
            String trimmed = term.trim();
            String like = "%" + trimmed.toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), like),
                    cb.equal(root.get("barcode"), trimmed)));
        }
        if (categoryId != null) {
            spec = spec.and((root, query, cb) -> {
                query.distinct(true);
                return cb.equal(root.join("categories").get("id"), categoryId);
            });
        }
        return jpa.findAll(spec, PageRequest.of(0, limit, Sort.by("name")))
                .getContent().stream().map(ProductMapper::toDomain).toList();
    }

    @Override
    public boolean existsActiveByBarcode(String barcode, Long excludingId) {
        return excludingId == null
                ? jpa.existsByBarcodeAndDeletedAtIsNull(barcode)
                : jpa.existsByBarcodeAndDeletedAtIsNullAndIdNot(barcode, excludingId);
    }

    @Override
    public List<Product> findAllByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jpa.findAllById(ids).stream().map(ProductMapper::toDomain).toList();
    }

    @Override
    public List<Product> findAllByIdsForUpdate(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jpa.findAllByIdInForUpdate(ids).stream().map(ProductMapper::toDomain).toList();
    }

    @Override
    public Product save(Product product) {
        ProductEntity entity;
        if (product.getId() == null) {
            entity = new ProductEntity();
        } else {
            entity = jpa.findById(product.getId())
                    .orElseThrow(() -> new IllegalStateException("Product " + product.getId() + " not found"));
            if (!Objects.equals(entity.getVersion(), product.getVersion())) {
                throw new ConcurrentUpdateException(CONFLICT_MESSAGE);
            }
        }
        ProductMapper.copyToEntity(product, entity);
        List<CategoryEntity> found = categories.findAllById(product.getCategoryIds());
        entity.setCategories(new HashSet<>(found));
        return ProductMapper.toDomain(jpa.saveAndFlush(entity));
    }
}
