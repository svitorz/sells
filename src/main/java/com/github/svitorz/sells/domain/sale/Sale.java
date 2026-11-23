package com.github.svitorz.sells.domain.sale;

import com.github.svitorz.sells.domain.product.Product;
import com.github.svitorz.sells.domain.shared.Decimals;
import com.github.svitorz.sells.domain.shared.DomainException;
import com.github.svitorz.sells.domain.shared.Texts;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class Sale {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final Long id;
    private LocalDateTime date;
    private BigDecimal subtotal;
    private SaleStatus status;
    private Long customerId;
    private final long userId;
    private final List<SaleItem> items;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime cancelledAt;
    private Long cancelledByUserId;
    private String cancelReason;
    private final Long version;

    private Sale(Long id, LocalDateTime date, BigDecimal subtotal, SaleStatus status, Long customerId,
                 long userId, List<SaleItem> items, LocalDateTime createdAt, LocalDateTime updatedAt,
                 LocalDateTime cancelledAt, Long cancelledByUserId, String cancelReason, Long version) {
        this.id = id;
        this.date = Objects.requireNonNull(date, "date");
        this.subtotal = Objects.requireNonNull(subtotal, "subtotal");
        this.status = Objects.requireNonNull(status, "status");
        this.customerId = customerId;
        this.userId = userId;
        this.items = new ArrayList<>(items);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.cancelledAt = cancelledAt;
        this.cancelledByUserId = cancelledByUserId;
        this.cancelReason = cancelReason;
        this.version = version;
    }

    /** UC08: a new draft, owned by the given user, with subtotal zero. */
    public static Sale startDraft(long userId, LocalDateTime now) {
        return new Sale(null, now, ZERO, SaleStatus.DRAFT, null, userId, List.of(),
                now, now, null, null, null, null);
    }

    public static Sale restore(Long id, LocalDateTime date, BigDecimal subtotal, SaleStatus status,
                               Long customerId, long userId, List<SaleItem> items, LocalDateTime createdAt,
                               LocalDateTime updatedAt, LocalDateTime cancelledAt, Long cancelledByUserId,
                               String cancelReason, Long version) {
        return new Sale(id, date, subtotal, status, customerId, userId, items, createdAt, updatedAt,
                cancelledAt, cancelledByUserId, cancelReason, version);
    }

    /**
     * UC09: adds the product, or sums the quantity if it is already in the sale.
     * The unit price is copied from the product only when the line is created.
     */
    public SaleItem addItem(Product product, BigDecimal quantity, LocalDateTime now) {
        requireDraft();
        if (product.getId() == null) {
            throw new IllegalArgumentException("product must be persisted");
        }
        if (product.isDeleted()) {
            throw new DomainException("O produto \"" + product.getName() + "\" foi excluído e não pode ser vendido.");
        }
        BigDecimal normalized = product.normalizeQuantity(quantity);
        Optional<SaleItem> existing = findItem(product.getId());
        SaleItem item;
        if (existing.isPresent()) {
            item = existing.get();
            item.changeQuantity(item.getQuantity().add(normalized));
        } else {
            item = SaleItem.create(product.getId(), product.getUnit(), normalized, product.getPrice());
            items.add(item);
        }
        touch(now);
        return item;
    }

    /** UC10 */
    public void changeItemQuantity(long productId, BigDecimal quantity, LocalDateTime now) {
        requireDraft();
        requireItem(productId).changeQuantity(quantity);
        touch(now);
    }

    /** UC10 */
    public void removeItem(long productId, LocalDateTime now) {
        requireDraft();
        items.remove(requireItem(productId));
        touch(now);
    }

    /** UC11: null removes the customer. */
    public void assignCustomer(Long customerId, LocalDateTime now) {
        requireDraft();
        this.customerId = customerId;
        touch(now);
    }

    /** UC13: stock is handled by the application service before calling this. */
    public void complete(LocalDateTime now) {
        requireDraft();
        if (items.isEmpty()) {
            throw new DomainException("A venda precisa ter pelo menos um item para ser finalizada.");
        }
        status = SaleStatus.COMPLETED;
        date = now;
        touch(now);
    }

    /** UC14: stock is handled by the application service before calling this. */
    public void cancel(long cancelledByUserId, String reason, LocalDateTime now) {
        if (status != SaleStatus.COMPLETED) {
            throw new DomainException("Somente vendas concluídas podem ser canceladas.");
        }
        String trimmed = Texts.required(reason, "Motivo do cancelamento", 2000);
        status = SaleStatus.CANCELLED;
        cancelledAt = now;
        this.cancelledByUserId = cancelledByUserId;
        cancelReason = trimmed;
        updatedAt = now;
    }

    public void requireDraft() {
        if (status != SaleStatus.DRAFT) {
            throw new DomainException("Esta venda não está mais em rascunho e não pode ser alterada.");
        }
    }

    public boolean isDraft() {
        return status == SaleStatus.DRAFT;
    }

    public Optional<SaleItem> findItem(long productId) {
        return items.stream().filter(item -> item.getProductId() == productId).findFirst();
    }

    private SaleItem requireItem(long productId) {
        return findItem(productId).orElseThrow(() -> new DomainException("O item não está nesta venda."));
    }

    private void touch(LocalDateTime now) {
        BigDecimal total = items.stream().map(SaleItem::total).reduce(ZERO, BigDecimal::add);
        if (total.compareTo(Decimals.MAX_MONEY) > 0) {
            throw new DomainException("O total da venda excede o valor máximo permitido.");
        }
        subtotal = total;
        updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public SaleStatus getStatus() {
        return status;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public long getUserId() {
        return userId;
    }

    public List<SaleItem> getItems() {
        return List.copyOf(items);
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public Long getCancelledByUserId() {
        return cancelledByUserId;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public Long getVersion() {
        return version;
    }
}
