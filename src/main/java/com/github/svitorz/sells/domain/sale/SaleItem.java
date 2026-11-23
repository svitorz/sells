package com.github.svitorz.sells.domain.sale;

import com.github.svitorz.sells.domain.product.ProductUnit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class SaleItem {

    private final Long id;
    private final long productId;
    private final ProductUnit unit;
    private BigDecimal quantity;
    private final BigDecimal unitPrice;

    private SaleItem(Long id, long productId, ProductUnit unit, BigDecimal quantity, BigDecimal unitPrice) {
        this.id = id;
        this.productId = productId;
        this.unit = Objects.requireNonNull(unit, "unit");
        this.quantity = Objects.requireNonNull(quantity, "quantity");
        this.unitPrice = Objects.requireNonNull(unitPrice, "unitPrice");
    }

    static SaleItem create(long productId, ProductUnit unit, BigDecimal quantity, BigDecimal unitPrice) {
        return new SaleItem(null, productId, unit, unit.quantity(quantity), unitPrice);
    }

    public static SaleItem restore(Long id, long productId, ProductUnit unit, BigDecimal quantity,
                                   BigDecimal unitPrice) {
        return new SaleItem(id, productId, unit, quantity, unitPrice);
    }

    void changeQuantity(BigDecimal newQuantity) {
        this.quantity = unit.quantity(newQuantity);
    }

    /** quantity x unitPrice, rounded to cents with HALF_UP. */
    public BigDecimal total() {
        return quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
    }

    public Long getId() {
        return id;
    }

    public long getProductId() {
        return productId;
    }

    public ProductUnit getUnit() {
        return unit;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }
}
