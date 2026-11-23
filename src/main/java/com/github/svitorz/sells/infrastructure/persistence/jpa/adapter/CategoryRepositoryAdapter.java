package com.github.svitorz.sells.infrastructure.persistence.jpa.adapter;

import com.github.svitorz.sells.application.port.CategoryRepository;
import com.github.svitorz.sells.domain.product.Category;
import com.github.svitorz.sells.infrastructure.persistence.jpa.entity.CategoryEntity;
import com.github.svitorz.sells.infrastructure.persistence.jpa.repository.CategoryJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class CategoryRepositoryAdapter implements CategoryRepository {

    private final CategoryJpaRepository jpa;

    public CategoryRepositoryAdapter(CategoryJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Category> findById(long id) {
        return jpa.findById(id).map(CategoryMapper::toDomain);
    }

    @Override
    public List<Category> findAll() {
        return jpa.findAllByOrderByNameAsc().stream().map(CategoryMapper::toDomain).toList();
    }

    @Override
    public List<Category> findAllByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jpa.findAllById(ids).stream().map(CategoryMapper::toDomain).toList();
    }

    @Override
    public long countActiveProducts(long categoryId) {
        return jpa.countActiveProducts(categoryId);
    }

    @Override
    public Category save(Category category) {
        CategoryEntity entity;
        if (category.getId() == null) {
            entity = new CategoryEntity();
        } else {
            entity = jpa.findById(category.getId())
                    .orElseThrow(() -> new IllegalStateException("Category " + category.getId() + " not found"));
        }
        CategoryMapper.copyToEntity(category, entity);
        return CategoryMapper.toDomain(jpa.saveAndFlush(entity));
    }

    @Override
    public void deleteById(long id) {
        jpa.deleteById(id);
    }
}
