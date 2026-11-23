package com.github.svitorz.sells.support;

import com.github.svitorz.sells.application.catalog.CategoryService;
import com.github.svitorz.sells.application.catalog.ProductService;
import com.github.svitorz.sells.application.customer.CustomerService;
import com.github.svitorz.sells.application.port.CategoryRepository;
import com.github.svitorz.sells.application.port.CustomerRepository;
import com.github.svitorz.sells.application.port.ProductRepository;
import com.github.svitorz.sells.application.port.SaleRepository;
import com.github.svitorz.sells.application.port.StockMovementRepository;
import com.github.svitorz.sells.application.port.UserRepository;
import com.github.svitorz.sells.application.sale.SaleService;
import com.github.svitorz.sells.application.session.AuthenticatedUser;
import com.github.svitorz.sells.application.session.SessionContext;
import com.github.svitorz.sells.application.stock.StockService;
import com.github.svitorz.sells.application.user.NewUserCommand;
import com.github.svitorz.sells.application.user.UserService;
import com.github.svitorz.sells.domain.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

/** Base for tests that use the real database and the real services. */
public abstract class ServiceIntegrationTest extends IntegrationTest {

    @Autowired
    protected SessionContext session;

    @Autowired
    protected UserService userService;

    @Autowired
    protected CategoryService categoryService;

    @Autowired
    protected ProductService productService;

    @Autowired
    protected CustomerService customerService;

    @Autowired
    protected SaleService saleService;

    @Autowired
    protected StockService stockService;

    @Autowired
    protected UserRepository users;

    @Autowired
    protected CategoryRepository categories;

    @Autowired
    protected ProductRepository products;

    @Autowired
    protected CustomerRepository customers;

    @Autowired
    protected SaleRepository sales;

    @Autowired
    protected StockMovementRepository movements;

    @BeforeEach
    void resetSession() {
        session.end();
    }

    protected AuthenticatedUser loginAsAdmin() {
        if (userService.needsFirstAdmin()) {
            return userService.createFirstAdmin(
                    new NewUserCommand("Admin", "admin@example.com", "admin", "secret123", Role.ADMIN, null));
        }
        return userService.login("admin", "secret123");
    }
}
