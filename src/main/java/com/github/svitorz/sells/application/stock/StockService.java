package com.github.svitorz.sells.application.stock;

import com.github.svitorz.sells.application.common.NotFoundException;
import com.github.svitorz.sells.application.port.ProductRepository;
import com.github.svitorz.sells.application.port.StockMovementRepository;
import com.github.svitorz.sells.application.port.TransactionRunner;
import com.github.svitorz.sells.application.port.UserRepository;
import com.github.svitorz.sells.application.session.AuthenticatedUser;
import com.github.svitorz.sells.application.session.SessionContext;
import com.github.svitorz.sells.domain.product.Product;
import com.github.svitorz.sells.domain.stock.StockMovement;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** UC17 and UC18. Every method requires an administrator. */
public final class StockService {

    private final ProductRepository products;
    private final StockMovementRepository movements;
    private final UserRepository users;
    private final SessionContext session;
    private final TransactionRunner tx;
    private final Clock clock;

    public StockService(ProductRepository products, StockMovementRepository movements, UserRepository users,
                        SessionContext session, TransactionRunner tx, Clock clock) {
        this.products = products;
        this.movements = movements;
        this.users = users;
        this.session = session;
        this.tx = tx;
        this.clock = clock;
    }

    public StockMovementView registerEntry(long productId, BigDecimal quantity, String notes) {
        AuthenticatedUser actor = session.requireAdmin();
        return tx.inTransaction(() -> {
            Product product = lockActive(productId);
            BigDecimal qty = product.getUnit().quantity(quantity);
            StockMovement movement = StockMovement.entry(productId, qty, actor.id(), notes, now());
            product.applyStockChange(qty, now());
            products.save(product);
            return viewOf(movements.saveAll(List.of(movement)).get(0));
        });
    }

    /** The movement is built before the stock changes, so a rejected adjustment (no reason) touches nothing. */
    public StockMovementView registerAdjustment(long productId, BigDecimal delta, String notes) {
        AuthenticatedUser actor = session.requireAdmin();
        return tx.inTransaction(() -> {
            Product product = lockActive(productId);
            BigDecimal qty = product.getUnit().delta(delta);
            StockMovement movement = StockMovement.adjustment(productId, qty, actor.id(), notes, now());
            product.applyStockChange(qty, now());
            products.save(product);
            return viewOf(movements.saveAll(List.of(movement)).get(0));
        });
    }

    /** Deleted products are accepted here, because their history stays readable. */
    public List<StockMovementView> listMovements(long productId) {
        session.requireAdmin();
        return tx.inTransaction(() -> {
            if (products.findAllByIds(List.of(productId)).isEmpty()) {
                throw new NotFoundException("Produto não encontrado.");
            }
            List<StockMovement> history = movements.findByProductId(productId);
            Map<Long, String> names = users.findNamesByIds(
                    history.stream().map(StockMovement::getUserId).distinct().toList());
            return history.stream()
                    .map(movement -> view(movement, names))
                    .toList();
        });
    }

    /** Entries and adjustments are not allowed on deleted products. */
    private Product lockActive(long productId) {
        List<Product> found = products.findAllByIdsForUpdate(List.of(productId));
        if (found.isEmpty() || found.get(0).isDeleted()) {
            throw new NotFoundException("Produto não encontrado.");
        }
        return found.get(0);
    }

    private StockMovementView viewOf(StockMovement movement) {
        return view(movement, users.findNamesByIds(List.of(movement.getUserId())));
    }

    private static StockMovementView view(StockMovement movement, Map<Long, String> names) {
        return new StockMovementView(movement.getId(), movement.getType(), movement.getQuantity(),
                movement.getCreatedAt(), names.get(movement.getUserId()), movement.getSaleId(), movement.getNotes());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
