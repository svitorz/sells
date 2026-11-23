package com.github.svitorz.sells.infrastructure.persistence.jpa.repository;

import com.github.svitorz.sells.infrastructure.persistence.jpa.entity.ProductEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<ProductEntity, Long>,
        JpaSpecificationExecutor<ProductEntity> {

    Optional<ProductEntity> findByIdAndDeletedAtIsNull(Long id);

    Optional<ProductEntity> findByBarcodeAndDeletedAtIsNull(String barcode);

    boolean existsByBarcodeAndDeletedAtIsNull(String barcode);

    boolean existsByBarcodeAndDeletedAtIsNullAndIdNot(String barcode, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProductEntity p where p.id in :ids order by p.id")
    List<ProductEntity> findAllByIdInForUpdate(@Param("ids") Collection<Long> ids);

    /**
     * Only id and unit, as scalars: a sale read must not put product entities in the persistence
     * context, or a later SELECT ... FOR UPDATE would return the stale state of those entities.
     */
    @Query("select p.id, p.unit from ProductEntity p where p.id in :ids")
    List<Object[]> findIdAndUnitByIdIn(@Param("ids") Collection<Long> ids);
}
