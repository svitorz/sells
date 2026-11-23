package com.github.svitorz.sells.infrastructure.persistence.jpa.repository;

import com.github.svitorz.sells.infrastructure.persistence.jpa.entity.CategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CategoryJpaRepository extends JpaRepository<CategoryEntity, Long> {

    List<CategoryEntity> findAllByOrderByNameAsc();

    @Query(value = "select count(*) from product_category pc join products p on p.id = pc.product_id "
            + "where pc.category_id = :categoryId and p.deleted_at is null", nativeQuery = true)
    long countActiveProducts(@Param("categoryId") long categoryId);
}
