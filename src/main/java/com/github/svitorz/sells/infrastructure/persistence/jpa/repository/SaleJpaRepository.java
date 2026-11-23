package com.github.svitorz.sells.infrastructure.persistence.jpa.repository;

import com.github.svitorz.sells.domain.sale.SaleStatus;
import com.github.svitorz.sells.infrastructure.persistence.jpa.entity.SaleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface SaleJpaRepository extends JpaRepository<SaleEntity, Long>, JpaSpecificationExecutor<SaleEntity> {

    Optional<SaleEntity> findFirstByUserIdAndStatusOrderByIdDesc(Long userId, SaleStatus status);
}
