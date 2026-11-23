package com.github.svitorz.sells.application.sale;

import com.github.svitorz.sells.domain.product.ProductUnit;

import java.math.BigDecimal;

public record SaleItemView(long productId, String productName, String barcode, ProductUnit unit,
                           BigDecimal quantity, BigDecimal unitPrice, BigDecimal total) {
}
