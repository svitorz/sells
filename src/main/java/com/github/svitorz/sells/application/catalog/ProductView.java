package com.github.svitorz.sells.application.catalog;

import com.github.svitorz.sells.domain.product.ProductUnit;

import java.math.BigDecimal;
import java.util.List;

public record ProductView(long id, String name, String barcode, BigDecimal price, ProductUnit unit,
                          String description, BigDecimal stock, List<CategoryView> categories) {
}
