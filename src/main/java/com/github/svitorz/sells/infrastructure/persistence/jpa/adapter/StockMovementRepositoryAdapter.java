package com.github.svitorz.sells.infrastructure.persistence.jpa.adapter;

import com.github.svitorz.sells.application.port.StockMovementRepository;
import com.github.svitorz.sells.domain.stock.StockMovement;
import com.github.svitorz.sells.infrastructure.persistence.jpa.entity.StockMovementEntity;
import com.github.svitorz.sells.infrastructure.persistence.jpa.repository.StockMovementJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@Transactional
public class StockMovementRepositoryAdapter implements StockMovementRepository {

    private final StockMovementJpaRepository jpa;

    public StockMovementRepositoryAdapter(StockMovementJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<StockMovement> saveAll(List<StockMovement> movements) {
        List<StockMovementEntity> entities = movements.stream().map(movement -> {
            StockMovementEntity entity = new StockMovementEntity();
            StockMovementMapper.copyToEntity(movement, entity);
            return entity;
        }).toList();
        return jpa.saveAllAndFlush(entities).stream().map(StockMovementMapper::toDomain).toList();
    }

    @Override
    public List<StockMovement> findByProductId(long productId) {
        return jpa.findByProductIdOrderByCreatedAtAscIdAsc(productId).stream()
                .map(StockMovementMapper::toDomain)
                .toList();
    }
}
