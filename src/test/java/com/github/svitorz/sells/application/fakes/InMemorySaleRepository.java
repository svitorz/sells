package com.github.svitorz.sells.application.fakes;

import com.github.svitorz.sells.application.port.SaleRepository;
import com.github.svitorz.sells.application.sale.SaleSearch;
import com.github.svitorz.sells.domain.sale.Sale;
import com.github.svitorz.sells.domain.sale.SaleItem;
import com.github.svitorz.sells.domain.sale.SaleStatus;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/** Stores copies, like a database does. Version grows by one on each save, as JPA does. */
public final class InMemorySaleRepository implements SaleRepository {

    private final Map<Long, Sale> store = new LinkedHashMap<>();
    private final AtomicLong sequence = new AtomicLong(1);

    @Override
    public Optional<Sale> findById(long id) {
        return Optional.ofNullable(store.get(id)).map(InMemorySaleRepository::copy);
    }

    @Override
    public Optional<Sale> findLatestDraftByUser(long userId) {
        return store.values().stream()
                .filter(sale -> sale.getStatus() == SaleStatus.DRAFT && sale.getUserId() == userId)
                .max(Comparator.comparing(Sale::getId))
                .map(InMemorySaleRepository::copy);
    }

    @Override
    public List<Sale> search(SaleSearch criteria, int limit) {
        return store.values().stream()
                .filter(sale -> matchesStatus(sale, criteria.status()))
                .filter(sale -> criteria.from() == null || !sale.getDate().toLocalDate().isBefore(criteria.from()))
                .filter(sale -> criteria.to() == null || !sale.getDate().toLocalDate().isAfter(criteria.to()))
                .filter(sale -> criteria.customerId() == null
                        || criteria.customerId().equals(sale.getCustomerId()))
                .filter(sale -> criteria.userId() == null || criteria.userId() == sale.getUserId())
                .sorted(Comparator.comparing(Sale::getDate).reversed()
                        .thenComparing(Sale::getId, Comparator.reverseOrder()))
                .limit(limit)
                .map(InMemorySaleRepository::copy)
                .toList();
    }

    @Override
    public Sale save(Sale sale) {
        long id = sale.getId() == null ? sequence.getAndIncrement() : sale.getId();
        Long version = sale.getVersion() == null ? 0L : sale.getVersion() + 1;
        Sale stored = Sale.restore(id, sale.getDate(), sale.getSubtotal(), sale.getStatus(), sale.getCustomerId(),
                sale.getUserId(), copyItems(sale.getItems()), sale.getCreatedAt(), sale.getUpdatedAt(),
                sale.getCancelledAt(), sale.getCancelledByUserId(), sale.getCancelReason(), version);
        store.put(id, stored);
        return copy(stored);
    }

    @Override
    public void deleteById(long id) {
        store.remove(id);
    }

    /** A null status means the history: COMPLETED or CANCELLED, never DRAFT. */
    private static boolean matchesStatus(Sale sale, SaleStatus status) {
        if (status == null) {
            return sale.getStatus() == SaleStatus.COMPLETED || sale.getStatus() == SaleStatus.CANCELLED;
        }
        return sale.getStatus() == status;
    }

    private static Sale copy(Sale sale) {
        return Sale.restore(sale.getId(), sale.getDate(), sale.getSubtotal(), sale.getStatus(),
                sale.getCustomerId(), sale.getUserId(), copyItems(sale.getItems()), sale.getCreatedAt(),
                sale.getUpdatedAt(), sale.getCancelledAt(), sale.getCancelledByUserId(), sale.getCancelReason(),
                sale.getVersion());
    }

    private static List<SaleItem> copyItems(List<SaleItem> items) {
        return items.stream()
                .map(item -> SaleItem.restore(item.getId(), item.getProductId(), item.getUnit(),
                        item.getQuantity(), item.getUnitPrice()))
                .toList();
    }
}
