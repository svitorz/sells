package com.github.svitorz.sells.application.sale;

/** warning is null when the quantity fits the current stock. */
public record AddItemResult(SaleView sale, StockWarning warning) {
}
