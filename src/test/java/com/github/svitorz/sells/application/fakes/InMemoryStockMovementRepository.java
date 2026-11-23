package com.github.svitorz.sells.application.fakes;

import com.github.svitorz.sells.application.port.StockMovementRepository;
import com.github.svitorz.sells.domain.stock.StockMovement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemoryStockMovementRepository implements StockMovementRepository {

    private final List<StockMovement> store = new ArrayList<>();
    private final AtomicLong sequence = new AtomicLong(1);

    @Override
    public List<StockMovement> saveAll(List<StockMovement> movements) {
        List<StockMovement> saved = new ArrayList<>();
        for (StockMovement movement : movements) {
            StockMovement stored = StockMovement.restore(sequence.getAndIncrement(), movement.getProductId(),
                    movement.getType(), movement.getQuantity(), movement.getSaleId(), movement.getUserId(),
                    movement.getCreatedAt(), movement.getNotes());
            store.add(stored);
            saved.add(stored);
        }
        return saved;
    }

    @Override
    public List<StockMovement> findByProductId(long productId) {
        return store.stream()
                .filter(movement -> movement.getProductId() == productId)
                .sorted(Comparator.comparing(StockMovement::getCreatedAt)
                        .thenComparing(StockMovement::getId))
                .toList();
    }
}
