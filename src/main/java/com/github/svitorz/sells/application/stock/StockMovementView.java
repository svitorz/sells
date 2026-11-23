package com.github.svitorz.sells.application.stock;

import com.github.svitorz.sells.domain.stock.StockMovementType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StockMovementView(long id, StockMovementType type, BigDecimal quantity, LocalDateTime createdAt,
                                String userName, Long saleId, String notes) {
}
