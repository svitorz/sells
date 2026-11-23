package com.github.svitorz.sells.infrastructure.persistence.jpa.adapter;

import com.github.svitorz.sells.domain.stock.StockMovement;
import com.github.svitorz.sells.infrastructure.persistence.jpa.entity.StockMovementEntity;

final class StockMovementMapper {

    private StockMovementMapper() {
    }

    static StockMovement toDomain(StockMovementEntity entity) {
        return StockMovement.restore(entity.getId(), entity.getProductId(), entity.getType(), entity.getQuantity(),
                entity.getSaleId(), entity.getUserId(), entity.getCreatedAt(), entity.getNotes());
    }

    static void copyToEntity(StockMovement movement, StockMovementEntity entity) {
        entity.setProductId(movement.getProductId());
        entity.setType(movement.getType());
        entity.setQuantity(movement.getQuantity());
        entity.setSaleId(movement.getSaleId());
        entity.setUserId(movement.getUserId());
        entity.setCreatedAt(movement.getCreatedAt());
        entity.setNotes(movement.getNotes());
    }
}
