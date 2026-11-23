package com.github.svitorz.sells.infrastructure.persistence.jpa.repository;

import com.github.svitorz.sells.infrastructure.persistence.jpa.entity.StockMovementEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockMovementJpaRepository extends JpaRepository<StockMovementEntity, Long> {

    List<StockMovementEntity> findByProductIdOrderByCreatedAtAscIdAsc(Long productId);
}
