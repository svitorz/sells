package com.github.svitorz.sells.application.fakes;

import com.github.svitorz.sells.application.catalog.CategoryService;
import com.github.svitorz.sells.application.catalog.CreateProductCommand;
import com.github.svitorz.sells.application.catalog.ProductService;
import com.github.svitorz.sells.application.catalog.ProductView;
import com.github.svitorz.sells.application.customer.CustomerCommand;
import com.github.svitorz.sells.application.customer.CustomerService;
import com.github.svitorz.sells.application.customer.CustomerView;
import com.github.svitorz.sells.application.sale.SaleService;
import com.github.svitorz.sells.application.session.AuthenticatedUser;
import com.github.svitorz.sells.application.session.SessionContext;
import com.github.svitorz.sells.application.stock.StockService;
import com.github.svitorz.sells.application.user.NewUserCommand;
import com.github.svitorz.sells.application.user.UserService;
import com.github.svitorz.sells.domain.product.ProductUnit;
import com.github.svitorz.sells.domain.user.Role;

import java.math.BigDecimal;
import java.util.Optional;

/** Wires every fake and every service of the application layer, for tests. */
public final class ApplicationTestFixture {

    public final InMemoryUserRepository users = new InMemoryUserRepository();
    public final InMemoryProductRepository products = new InMemoryProductRepository();
    public final InMemoryCategoryRepository categories = new InMemoryCategoryRepository(products);
    public final InMemoryCustomerRepository customers = new InMemoryCustomerRepository();
    public final InMemorySaleRepository sales = new InMemorySaleRepository();
    public final InMemoryStockMovementRepository movements = new InMemoryStockMovementRepository();
    public final FakePasswordHasher hasher = new FakePasswordHasher();
    public final DirectTransactionRunner tx = new DirectTransactionRunner();
    public final MutableClock clock = new MutableClock();
    public final SessionContext session = new SessionContext();

    public final UserService userService = new UserService(users, hasher, session, tx);
    public final CategoryService categoryService = new CategoryService(categories, session, tx);
    public final ProductService productService = new ProductService(products, categories, movements, session, tx,
            clock);
    public final CustomerService customerService = new CustomerService(customers, session, tx, clock);
    public final SaleService saleService = new SaleService(sales, products, customers, users, movements, session,
            tx, clock);
    public final StockService stockService = new StockService(products, movements, users, session, tx, clock);

    /** Logs in as admin for one call, then restores the session that was active before. */
    public ProductView createProduct(String name, String barcode, String price, ProductUnit unit,
                                     String initialStock) {
        Optional<AuthenticatedUser> previous = session.current();
        loginAsAdmin();
        try {
            return productService.create(new CreateProductCommand(name, barcode, new BigDecimal(price), unit, null,
                    initialStock == null ? null : new BigDecimal(initialStock), null));
        } finally {
            restoreSession(previous);
        }
    }

    /** Logs in as admin for one call, then restores the session that was active before. */
    public CustomerView createCustomer(String name) {
        Optional<AuthenticatedUser> previous = session.current();
        loginAsAdmin();
        try {
            return customerService.create(new CustomerCommand(name, "11999990000", null, null, null, null));
        } finally {
            restoreSession(previous);
        }
    }

    private void restoreSession(Optional<AuthenticatedUser> previous) {
        if (previous.isPresent()) {
            session.start(previous.get());
        } else {
            session.end();
        }
    }

    /** Creates the admin "admin" if needed, then logs in as it. */
    public AuthenticatedUser loginAsAdmin() {
        if (users.findByUsername("admin").isEmpty()) {
            return userService.createFirstAdmin(
                    new NewUserCommand("Administrador", "admin@example.com", "admin", "secret123", Role.ADMIN, null));
        }
        return userService.login("admin", "secret123");
    }

    /** Creates the operator "caixa" if needed (through an admin), then logs in as it. */
    public AuthenticatedUser loginAsOperator() {
        if (users.findByUsername("caixa").isEmpty()) {
            loginAsAdmin();
            userService.createUser(
                    new NewUserCommand("Caixa", "caixa@example.com", "caixa", "secret123", Role.USER, null));
        }
        return userService.login("caixa", "secret123");
    }
}
