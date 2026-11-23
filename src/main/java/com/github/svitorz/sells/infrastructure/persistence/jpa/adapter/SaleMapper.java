package com.github.svitorz.sells.infrastructure.persistence.jpa.adapter;

import com.github.svitorz.sells.domain.product.ProductUnit;
import com.github.svitorz.sells.domain.sale.Sale;
import com.github.svitorz.sells.domain.sale.SaleItem;
import com.github.svitorz.sells.infrastructure.persistence.jpa.entity.SaleEntity;
import com.github.svitorz.sells.infrastructure.persistence.jpa.entity.SaleItemEntity;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.List;

final class SaleMapper {

    private SaleMapper() {
    }

    /** units maps each product id to its unit: items need it, and the adapter loads it in one query. */
    static Sale toDomain(SaleEntity entity, Map<Long, ProductUnit> units) {
        List<SaleItem> items = entity.getItems().stream()
                .map(item -> SaleItem.restore(item.getId(), item.getProductId(), units.get(item.getProductId()),
                        item.getQuantity(), item.getUnitPrice()))
                .toList();
        return Sale.restore(entity.getId(), entity.getDate(), entity.getSubtotal(), entity.getStatus(),
                entity.getCustomerId(), entity.getUserId(), items, entity.getCreatedAt(), entity.getUpdatedAt(),
                entity.getCancelledAt(), entity.getCancelledByUserId(), entity.getCancelReason(),
                entity.getVersion());
    }

    /**
     * Items are synchronized by product id: rows are kept, changed or removed, never deleted and recreated.
     */
    static void copyToEntity(Sale sale, SaleEntity entity) {
        entity.setDate(sale.getDate());
        entity.setSubtotal(sale.getSubtotal());
        entity.setStatus(sale.getStatus());
        entity.setCustomerId(sale.getCustomerId());
        entity.setUserId(sale.getUserId());
        entity.setCreatedAt(sale.getCreatedAt());
        entity.setUpdatedAt(sale.getUpdatedAt());
        entity.setCancelledAt(sale.getCancelledAt());
        entity.setCancelledByUserId(sale.getCancelledByUserId());
        entity.setCancelReason(sale.getCancelReason());

        Map<Long, SaleItemEntity> byProduct = entity.getItems().stream()
                .collect(Collectors.toMap(SaleItemEntity::getProductId, Function.identity()));
        Set<Long> wanted = sale.getItems().stream().map(SaleItem::getProductId).collect(Collectors.toSet());
        entity.getItems().removeIf(item -> !wanted.contains(item.getProductId()));

        for (SaleItem item : sale.getItems()) {
            SaleItemEntity target = byProduct.get(item.getProductId());
            if (target == null) {
                target = new SaleItemEntity();
                target.setSale(entity);
                target.setProductId(item.getProductId());
                entity.getItems().add(target);
            }
            target.setQuantity(item.getQuantity());
            target.setUnitPrice(item.getUnitPrice());
        }
    }
}
