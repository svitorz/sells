package com.github.svitorz.sells.application.sale;

import com.github.svitorz.sells.application.common.AccessDeniedException;
import com.github.svitorz.sells.application.common.NotFoundException;
import com.github.svitorz.sells.application.port.CustomerRepository;
import com.github.svitorz.sells.application.port.ProductRepository;
import com.github.svitorz.sells.application.port.SaleRepository;
import com.github.svitorz.sells.application.port.StockMovementRepository;
import com.github.svitorz.sells.application.port.TransactionRunner;
import com.github.svitorz.sells.application.port.UserRepository;
import com.github.svitorz.sells.application.session.AuthenticatedUser;
import com.github.svitorz.sells.application.session.SessionContext;
import com.github.svitorz.sells.domain.product.Product;
import com.github.svitorz.sells.domain.sale.Sale;
import com.github.svitorz.sells.domain.sale.SaleItem;
import com.github.svitorz.sells.domain.shared.Decimals;
import com.github.svitorz.sells.domain.shared.DomainException;
import com.github.svitorz.sells.domain.stock.StockMovement;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class SaleService {

    private static final int SEARCH_LIMIT = 500;

    private final SaleRepository sales;
    private final ProductRepository products;
    private final CustomerRepository customers;
    private final UserRepository users;
    private final StockMovementRepository movements;
    private final SessionContext session;
    private final TransactionRunner tx;
    private final Clock clock;

    public SaleService(SaleRepository sales, ProductRepository products, CustomerRepository customers,
                       UserRepository users, StockMovementRepository movements, SessionContext session,
                       TransactionRunner tx, Clock clock) {
        this.sales = sales;
        this.products = products;
        this.customers = customers;
        this.users = users;
        this.movements = movements;
        this.session = session;
        this.tx = tx;
        this.clock = clock;
    }

    public Optional<SaleView> findOpenDraft() {
        AuthenticatedUser actor = session.require();
        return tx.inTransaction(() -> sales.findLatestDraftByUser(actor.id()).map(this::toView));
    }

    public SaleView startDraft() {
        AuthenticatedUser actor = session.require();
        return tx.inTransaction(() -> toView(sales.save(Sale.startDraft(actor.id(), now()))));
    }

    public AddItemResult addItem(long saleId, long productId, BigDecimal quantity) {
        AuthenticatedUser actor = session.require();
        return tx.inTransaction(() -> addItemTo(actor, saleId, productId, quantity));
    }

    public AddItemResult addItemByBarcode(long saleId, String barcode, BigDecimal quantity) {
        AuthenticatedUser actor = session.require();
        String code = barcode == null ? "" : barcode.trim();
        return tx.inTransaction(() -> {
            Optional<Product> found = code.isEmpty() ? Optional.empty() : products.findActiveByBarcode(code);
            Product product = found.orElseThrow(
                    () -> new NotFoundException("Nenhum produto encontrado com o código " + code + "."));
            return addItemTo(actor, saleId, product.getId(), quantity);
        });
    }

    public SaleView changeItemQuantity(long saleId, long productId, BigDecimal quantity) {
        AuthenticatedUser actor = session.require();
        return tx.inTransaction(() -> {
            Sale sale = loadDraftForActor(saleId, actor);
            sale.changeItemQuantity(productId, quantity, now());
            return toView(sales.save(sale));
        });
    }

    public SaleView removeItem(long saleId, long productId) {
        AuthenticatedUser actor = session.require();
        return tx.inTransaction(() -> {
            Sale sale = loadDraftForActor(saleId, actor);
            sale.removeItem(productId, now());
            return toView(sales.save(sale));
        });
    }

    /** A null customerId removes the customer from the draft. */
    public SaleView assignCustomer(long saleId, Long customerId) {
        AuthenticatedUser actor = session.require();
        return tx.inTransaction(() -> {
            Sale sale = loadDraftForActor(saleId, actor);
            if (customerId != null && customers.findActiveById(customerId).isEmpty()) {
                throw new NotFoundException("Cliente não encontrado.");
            }
            sale.assignCustomer(customerId, now());
            return toView(sales.save(sale));
        });
    }

    public void deleteDraft(long saleId) {
        AuthenticatedUser actor = session.require();
        tx.inTransaction(() -> {
            loadDraftForActor(saleId, actor);
            sales.deleteById(saleId);
        });
    }

    /**
     * UC13. Checks every line before changing anything. Stock, sale and movements
     * are saved together, inside one transaction.
     */
    public SaleView complete(long saleId) {
        AuthenticatedUser actor = session.require();
        return tx.inTransaction(() -> {
            Sale sale = loadDraftForActor(saleId, actor);
            Map<Long, Product> locked = lockProducts(productIds(sale));

            for (SaleItem item : sale.getItems()) {
                Product product = lockedProduct(locked, item.getProductId());
                if (product.isDeleted()) {
                    throw new DomainException("O produto \"" + product.getName()
                            + "\" foi excluído. Remova-o da venda para finalizar.");
                }
                if (product.getStock().compareTo(item.getQuantity()) < 0) {
                    throw new DomainException("Estoque insuficiente para \"" + product.getName()
                            + "\". Disponível: " + Decimals.format(product.getStock()) + ".");
                }
            }

            sale.complete(now());
            List<StockMovement> lines = new ArrayList<>();
            for (SaleItem item : sale.getItems()) {
                Product product = lockedProduct(locked, item.getProductId());
                product.applyStockChange(item.getQuantity().negate(), now());
                products.save(product);
                lines.add(StockMovement.sale(product.getId(), item.getQuantity(), sale.getId(),
                        actor.id(), now()));
            }
            Sale saved = sales.save(sale);
            movements.saveAll(lines);
            return toView(saved);
        });
    }

    /** UC14. Validates the cancellation before touching stock. Stock comes back even if a product was deleted. */
    public SaleView cancel(long saleId, String reason) {
        AuthenticatedUser actor = session.requireAdmin();
        return tx.inTransaction(() -> {
            Sale sale = sales.findById(saleId).orElseThrow(() -> new NotFoundException("Venda não encontrada."));
            sale.cancel(actor.id(), reason, now());
            Map<Long, Product> locked = lockProducts(productIds(sale));

            List<StockMovement> lines = new ArrayList<>();
            for (SaleItem item : sale.getItems()) {
                Product product = lockedProduct(locked, item.getProductId());
                product.applyStockChange(item.getQuantity(), now());
                products.save(product);
                lines.add(StockMovement.saleReturn(product.getId(), item.getQuantity(), sale.getId(),
                        actor.id(), now()));
            }
            Sale saved = sales.save(sale);
            movements.saveAll(lines);
            return toView(saved);
        });
    }

    public SaleView get(long saleId) {
        session.require();
        return tx.inTransaction(() -> toView(
                sales.findById(saleId).orElseThrow(() -> new NotFoundException("Venda não encontrada."))));
    }

    /** UC15. Drafts never appear in the history. */
    public List<SaleSummaryView> search(SaleSearch criteria) {
        session.require();
        SaleSearch effective = criteria == null ? new SaleSearch(null, null, null, null, null) : criteria;
        if (effective.from() != null && effective.to() != null && effective.from().isAfter(effective.to())) {
            throw new DomainException("A data inicial deve ser anterior ou igual à data final.");
        }
        return tx.inTransaction(() -> {
            List<Sale> found = sales.search(effective, SEARCH_LIMIT);
            Map<Long, String> customerNames = customers.findNamesByIds(
                    found.stream().map(Sale::getCustomerId).filter(Objects::nonNull).distinct().toList());
            Map<Long, String> userNames = users.findNamesByIds(
                    found.stream().map(Sale::getUserId).distinct().toList());
            return found.stream()
                    .map(sale -> new SaleSummaryView(sale.getId(), sale.getDate(), sale.getStatus(),
                            sale.getCustomerId() == null ? null : customerNames.get(sale.getCustomerId()),
                            userNames.get(sale.getUserId()), sale.getItems().size(), sale.getSubtotal()))
                    .toList();
        });
    }

    private AddItemResult addItemTo(AuthenticatedUser actor, long saleId, long productId, BigDecimal quantity) {
        Sale sale = loadDraftForActor(saleId, actor);
        Product product = products.findActiveById(productId)
                .orElseThrow(() -> new NotFoundException("Produto não encontrado."));
        SaleItem item = sale.addItem(product, quantity, now());
        Sale saved = sales.save(sale);
        StockWarning warning = item.getQuantity().compareTo(product.getStock()) > 0
                ? new StockWarning(product.getName(), product.getStock(), product.getUnit())
                : null;
        return new AddItemResult(toView(saved), warning);
    }

    private Sale loadDraftForActor(long saleId, AuthenticatedUser actor) {
        Sale sale = sales.findById(saleId).orElseThrow(() -> new NotFoundException("Venda não encontrada."));
        sale.requireDraft();
        if (sale.getUserId() != actor.id() && !actor.isAdmin()) {
            throw new AccessDeniedException("Este rascunho pertence a outro usuário.");
        }
        return sale;
    }

    /** Distinct product ids of the sale, in ascending order: the lock order. */
    private static List<Long> productIds(Sale sale) {
        return sale.getItems().stream().map(SaleItem::getProductId).distinct().sorted().toList();
    }

    private Map<Long, Product> lockProducts(List<Long> ids) {
        Map<Long, Product> locked = new HashMap<>();
        for (Product product : products.findAllByIdsForUpdate(ids)) {
            locked.put(product.getId(), product);
        }
        return locked;
    }

    private static Product lockedProduct(Map<Long, Product> locked, long productId) {
        Product product = locked.get(productId);
        if (product == null) {
            throw new NotFoundException("Produto não encontrado.");
        }
        return product;
    }

    private SaleView toView(Sale sale) {
        Map<Long, Product> productsById = new HashMap<>();
        for (Product product : products.findAllByIds(productIds(sale))) {
            productsById.put(product.getId(), product);
        }
        String customerName = null;
        if (sale.getCustomerId() != null) {
            customerName = customers.findNamesByIds(List.of(sale.getCustomerId())).get(sale.getCustomerId());
        }
        List<Long> userIds = new ArrayList<>(List.of(sale.getUserId()));
        if (sale.getCancelledByUserId() != null) {
            userIds.add(sale.getCancelledByUserId());
        }
        Map<Long, String> userNames = users.findNamesByIds(userIds);

        List<SaleItemView> items = sale.getItems().stream()
                .map(item -> {
                    Product product = productsById.get(item.getProductId());
                    return new SaleItemView(item.getProductId(), product.getName(), product.getBarcode(),
                            item.getUnit(), item.getQuantity(), item.getUnitPrice(), item.total());
                })
                .toList();

        return new SaleView(sale.getId(), sale.getDate(), sale.getStatus(), sale.getCustomerId(), customerName,
                sale.getUserId(), userNames.get(sale.getUserId()), items, sale.getSubtotal(),
                sale.getCancelledAt(),
                sale.getCancelledByUserId() == null ? null : userNames.get(sale.getCancelledByUserId()),
                sale.getCancelReason());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
