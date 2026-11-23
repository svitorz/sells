package com.github.svitorz.sells.application.catalog;

import com.github.svitorz.sells.domain.product.ProductUnit;

import java.math.BigDecimal;
import java.util.Set;

public record UpdateProductCommand(String name, String barcode, BigDecimal price, ProductUnit unit,
                                   String description, Set<Long> categoryIds) {
}
