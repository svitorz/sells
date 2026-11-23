package com.github.svitorz.sells.application.port;

import com.github.svitorz.sells.domain.stock.StockMovement;

import java.util.List;

public interface StockMovementRepository {

    /** Returns the saved movements, with ids, in the same order. */
    List<StockMovement> saveAll(List<StockMovement> movements);

    /** Ordered by createdAt asc, id asc. */
    List<StockMovement> findByProductId(long productId);
}
