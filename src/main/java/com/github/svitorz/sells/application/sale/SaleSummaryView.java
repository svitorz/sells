package com.github.svitorz.sells.application.sale;

import com.github.svitorz.sells.domain.sale.SaleStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SaleSummaryView(long id, LocalDateTime date, SaleStatus status, String customerName,
                              String userName, int itemCount, BigDecimal subtotal) {
}
