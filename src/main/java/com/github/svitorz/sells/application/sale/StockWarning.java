package com.github.svitorz.sells.application.sale;

import com.github.svitorz.sells.domain.product.ProductUnit;

import java.math.BigDecimal;

/** Returned when a draft line would exceed the current stock (UC09 warning). */
public record StockWarning(String productName, BigDecimal available, ProductUnit unit) {
}
