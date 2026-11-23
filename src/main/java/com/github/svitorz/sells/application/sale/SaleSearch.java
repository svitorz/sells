package com.github.svitorz.sells.application.sale;

import com.github.svitorz.sells.domain.sale.SaleStatus;

import java.time.LocalDate;

public record SaleSearch(LocalDate from, LocalDate to, SaleStatus status, Long customerId, Long userId) {
}
