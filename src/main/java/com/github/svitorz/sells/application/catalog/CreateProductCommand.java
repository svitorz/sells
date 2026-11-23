package com.github.svitorz.sells.application.catalog;

import com.github.svitorz.sells.domain.product.ProductUnit;

import java.math.BigDecimal;
import java.util.Set;

public record CreateProductCommand(String name, String barcode, BigDecimal price, ProductUnit unit,
                                   String description, BigDecimal initialStock, Set<Long> categoryIds) {
}
