package com.github.svitorz.sells.application.sale;

import com.github.svitorz.sells.domain.sale.SaleStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record SaleView(long id, LocalDateTime date, SaleStatus status, Long customerId, String customerName,
                       long userId, String userName, List<SaleItemView> items, BigDecimal subtotal,
                       LocalDateTime cancelledAt, String cancelledByName, String cancelReason) {
}
