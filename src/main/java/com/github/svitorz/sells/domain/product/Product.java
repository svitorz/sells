package com.github.svitorz.sells.domain.product;

import com.github.svitorz.sells.domain.shared.Decimals;
import com.github.svitorz.sells.domain.shared.DomainException;
import com.github.svitorz.sells.domain.shared.Texts;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public final class Product {

    private static final Pattern WHITESPACE = Pattern.compile("\\s");

    private final Long id;
    private String name;
    private String barcode;
    private BigDecimal price;
    private ProductUnit unit;
    private String description;
    private BigDecimal stock;
    private Set<Long> categoryIds;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
    private final Long version;

    private Product(Long id, String name, String barcode, BigDecimal price, ProductUnit unit, String description,
                    BigDecimal stock, Set<Long> categoryIds, LocalDateTime createdAt, LocalDateTime updatedAt,
                    LocalDateTime deletedAt, Long version) {
        this.id = id;
        this.name = name;
        this.barcode = barcode;
        this.price = price;
        this.unit = unit;
        this.description = description;
        this.stock = stock;
        this.categoryIds = categoryIds;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
        this.version = version;
    }

    public static Product create(String name, String barcode, BigDecimal price, ProductUnit unit,
                                 String description, Set<Long> categoryIds, LocalDateTime now) {
        Objects.requireNonNull(now, "now");
        Product product = new Product(null, null, null, null, null, null,
                BigDecimal.ZERO.setScale(3), Set.of(), now, now, null, null);
        product.update(name, barcode, price, unit, description, categoryIds, now);
        return product;
    }

    public static Product restore(Long id, String name, String barcode, BigDecimal price, ProductUnit unit,
                                  String description, BigDecimal stock, Set<Long> categoryIds,
                                  LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime deletedAt,
                                  Long version) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(stock, "stock");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        return new Product(id, name, barcode, price, unit, description, stock,
                categoryIds == null ? Set.of() : Set.copyOf(categoryIds),
                createdAt, updatedAt, deletedAt, version);
    }

    public void update(String name, String barcode, BigDecimal price, ProductUnit unit,
                       String description, Set<Long> categoryIds, LocalDateTime now) {
        requireActive();
        String newName = Texts.required(name, "Nome", 255);
        String newBarcode = validateBarcode(Texts.optional(barcode, "Código de barras", 50));
        BigDecimal newPrice = Decimals.money(price, "Preço");
        if (unit == null) {
            throw new DomainException("O campo Unidade é obrigatório.");
        }
        if (unit == ProductUnit.UN && stock != null && Decimals.decimalPlaces(stock) > 0) {
            throw new DomainException("Não é possível mudar a unidade para UN enquanto o estoque tiver frações. "
                    + "Ajuste o estoque antes.");
        }
        String newDescription = Texts.optional(description, "Descrição", 2000);
        Set<Long> newCategoryIds = categoryIds == null ? Set.of() : Set.copyOf(categoryIds);

        this.name = newName;
        this.barcode = newBarcode;
        this.price = newPrice;
        this.unit = unit;
        this.description = newDescription;
        this.categoryIds = newCategoryIds;
        this.updatedAt = now;
    }

    /** Applies a stock difference. Does not check whether the product is deleted (see ADR-0006). */
    public void applyStockChange(BigDecimal delta, LocalDateTime now) {
        BigDecimal normalized = unit.delta(delta);
        BigDecimal newStock = stock.add(normalized);
        if (newStock.signum() < 0) {
            throw new DomainException("Estoque insuficiente para \"" + name + "\". Disponível: "
                    + Decimals.format(stock) + ".");
        }
        stock = newStock;
        updatedAt = now;
    }

    public BigDecimal normalizeQuantity(BigDecimal quantity) {
        return unit.quantity(quantity);
    }

    public void delete(LocalDateTime now) {
        if (deletedAt != null) {
            throw new DomainException("O produto já foi excluído.");
        }
        deletedAt = now;
        updatedAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    private void requireActive() {
        if (deletedAt != null) {
            throw new DomainException("Produto excluído não pode ser alterado.");
        }
    }

    private static String validateBarcode(String barcode) {
        if (barcode != null && WHITESPACE.matcher(barcode).find()) {
            throw new DomainException("O código de barras não pode conter espaços.");
        }
        return barcode;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getBarcode() {
        return barcode;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public ProductUnit getUnit() {
        return unit;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getStock() {
        return stock;
    }

    public Set<Long> getCategoryIds() {
        return Set.copyOf(categoryIds);
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public Long getVersion() {
        return version;
    }
}
