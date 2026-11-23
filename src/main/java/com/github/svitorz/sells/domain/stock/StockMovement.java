package com.github.svitorz.sells.domain.stock;

import com.github.svitorz.sells.domain.shared.DomainException;
import com.github.svitorz.sells.domain.shared.Texts;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

public final class StockMovement {

    private final Long id;
    private final long productId;
    private final StockMovementType type;
    private final BigDecimal quantity;
    private final Long saleId;
    private final long userId;
    private final LocalDateTime createdAt;
    private final String notes;

    private StockMovement(Long id, long productId, StockMovementType type, BigDecimal quantity, Long saleId,
                          long userId, LocalDateTime createdAt, String notes) {
        this.id = id;
        this.productId = productId;
        this.type = Objects.requireNonNull(type, "type");
        this.quantity = Objects.requireNonNull(quantity, "quantity");
        this.saleId = saleId;
        this.userId = userId;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.notes = notes;
    }

    public static StockMovement entry(long productId, BigDecimal quantity, long userId, String notes,
                                      LocalDateTime now) {
        Objects.requireNonNull(quantity, "quantity");
        if (quantity.signum() <= 0) {
            throw new DomainException("A quantidade de entrada deve ser maior que zero.");
        }
        return new StockMovement(null, productId, StockMovementType.ENTRY, quantity, null, userId, now,
                Texts.optional(notes, "Observações", 2000));
    }

    public static StockMovement adjustment(long productId, BigDecimal delta, long userId, String notes,
                                           LocalDateTime now) {
        Objects.requireNonNull(delta, "delta");
        if (delta.signum() == 0) {
            throw new DomainException("A quantidade deve ser diferente de zero.");
        }
        return new StockMovement(null, productId, StockMovementType.ADJUSTMENT, delta, null, userId, now,
                Texts.required(notes, "Motivo do ajuste", 2000));
    }

    public static StockMovement sale(long productId, BigDecimal quantity, long saleId, long userId,
                                     LocalDateTime now) {
        Objects.requireNonNull(quantity, "quantity");
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        return new StockMovement(null, productId, StockMovementType.SALE, quantity.negate(), saleId, userId,
                now, null);
    }

    public static StockMovement saleReturn(long productId, BigDecimal quantity, long saleId, long userId,
                                           LocalDateTime now) {
        Objects.requireNonNull(quantity, "quantity");
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        return new StockMovement(null, productId, StockMovementType.RETURN, quantity, saleId, userId, now, null);
    }

    public static StockMovement restore(Long id, long productId, StockMovementType type, BigDecimal quantity,
                                        Long saleId, long userId, LocalDateTime createdAt, String notes) {
        Objects.requireNonNull(id, "id");
        return new StockMovement(id, productId, type, quantity, saleId, userId, createdAt, notes);
    }

    public Long getId() {
        return id;
    }

    public long getProductId() {
        return productId;
    }

    public StockMovementType getType() {
        return type;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public Long getSaleId() {
        return saleId;
    }

    public long getUserId() {
        return userId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getNotes() {
        return notes;
    }
}
